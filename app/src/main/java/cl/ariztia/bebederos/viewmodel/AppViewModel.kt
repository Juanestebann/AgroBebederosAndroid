package cl.ariztia.bebederos.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import cl.ariztia.bebederos.data.local.AppDatabase
import cl.ariztia.bebederos.data.local.SettingsStore
import cl.ariztia.bebederos.data.local.toEntity
import cl.ariztia.bebederos.data.local.toModel
import cl.ariztia.bebederos.data.model.*
import cl.ariztia.bebederos.data.remote.ExternalWeatherProvider
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.AlertApi
import cl.ariztia.bebederos.data.remote.api.AuthApi
import cl.ariztia.bebederos.data.remote.api.CatalogApi
import cl.ariztia.bebederos.data.remote.api.FlushingApi
import cl.ariztia.bebederos.data.remote.api.PhotoApi
import cl.ariztia.bebederos.data.repository.AuthRepository
import cl.ariztia.bebederos.data.repository.AuthResult
import cl.ariztia.bebederos.data.repository.AmbientWeatherRepository
import cl.ariztia.bebederos.data.repository.AmbientWeatherResult
import cl.ariztia.bebederos.data.repository.AlertError
import cl.ariztia.bebederos.data.repository.AlertRepository
import cl.ariztia.bebederos.data.repository.AlertResult
import cl.ariztia.bebederos.data.repository.CatalogError
import cl.ariztia.bebederos.data.repository.CatalogRepository
import cl.ariztia.bebederos.data.repository.CatalogResult
import cl.ariztia.bebederos.data.repository.DemoRepository
import cl.ariztia.bebederos.data.repository.CreateFlushingCommand
import cl.ariztia.bebederos.data.repository.FlushingError
import cl.ariztia.bebederos.data.repository.FlushingRepository
import cl.ariztia.bebederos.data.repository.FlushingResult
import cl.ariztia.bebederos.data.repository.PhotoError
import cl.ariztia.bebederos.data.repository.PhotoRepository
import cl.ariztia.bebederos.data.repository.PhotoResult
import cl.ariztia.bebederos.data.repository.RemotePhotoCache
import cl.ariztia.bebederos.data.repository.RemotePhotoContent
import cl.ariztia.bebederos.data.repository.ResolvedLocation
import cl.ariztia.bebederos.data.repository.UpdateFlushingCommand
import cl.ariztia.bebederos.data.repository.effectiveWaterLines
import cl.ariztia.bebederos.util.CriticalAlertNotificationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.io.File

/**
 * Estado observable de un intento de guardado.
 *
 * [savedRecordId] solo aparece cuando Spring Boot confirmó `201 Created`.
 */
data class FlushingSaveUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val reasonError: String? = null,
    val observationError: String? = null,
    val beforeTemperatureError: String? = null,
    val afterTemperatureError: String? = null,
    val savedRecordId: Long? = null,
    val updatedRecordId: Long? = null,
    val temperatureRefreshWarning: String? = null,
    val photoUploadWarning: String? = null
)

/** Estado de una eliminación confirmada desde el historial del operario. */
data class FlushingDeleteUiState(
    val deletingRecordId: Long? = null,
    val deletedRecordId: Long? = null,
    val errorMessage: String? = null,
    val temperatureRefreshWarning: String? = null
) {
    val isDeleting: Boolean
        get() = deletingRecordId != null
}

/** Diferencia explícitamente la carga inicial, una lista disponible y un fallo de Room. */
sealed interface FlushingListUiState {
    data object Loading : FlushingListUiState
    data class Ready(val records: List<FlushingRecord>) : FlushingListUiState
    data object Error : FlushingListUiState
}

/** Una lista vacía sigue siendo un resultado cargado correctamente, no un estado de carga. */
internal fun flushingListUiState(
    records: List<FlushingRecord>
): FlushingListUiState = FlushingListUiState.Ready(records)

val FlushingListUiState.records: List<FlushingRecord>
    get() = (this as? FlushingListUiState.Ready)?.records.orEmpty()

/** Resultado interno que agrupa todas las validaciones del formulario. */
internal data class FlushingFormValidation(
    val reasonError: String? = null,
    val observationError: String? = null,
    val beforeTemperature: WaterTemperatureInput = WaterTemperatureInput(),
    val afterTemperature: WaterTemperatureInput = WaterTemperatureInput()
) {
    val isValid: Boolean
        get() = reasonError == null && observationError == null &&
                beforeTemperature.isValid && afterTemperature.isValid
}

/** Valida textos y ambas mediciones antes de permitir cualquier escritura persistente. */
internal fun validateFlushingForm(
    reason: String,
    observation: String,
    beforeTemperatureInput: String,
    afterTemperatureInput: String
): FlushingFormValidation = FlushingFormValidation(
    reasonError = when {
        reason.isBlank() -> "Selecciona un motivo para el flushing."
        reason.length > MAX_FLUSHING_REASON_LENGTH ->
            "El motivo no puede superar $MAX_FLUSHING_REASON_LENGTH caracteres."
        else -> null
    },
    observationError = if (observation.length > MAX_FLUSHING_OBSERVATION_LENGTH) {
        "La observación no puede superar $MAX_FLUSHING_OBSERVATION_LENGTH caracteres."
    } else {
        null
    },
    beforeTemperature = validateWaterTemperatureInput(beforeTemperatureInput, "antes"),
    afterTemperature = validateWaterTemperatureInput(afterTemperatureInput, "después")
)

const val MAX_FLUSHING_OBSERVATION_LENGTH = 500
internal const val MAX_FLUSHING_REASON_LENGTH = 80
/** Selección jerárquica coherente usada durante una sesión autenticada. */
internal data class SelectedLocation(
    val farm: Farm,
    val shed: Shed,
    val line: WaterLine
)

/**
 * Mantiene la selección temporal granja–galpón–línea y aplica el alcance de cada rol.
 *
 * No se persiste: [reset] y [startSession] impiden heredar la ubicación de una sesión anterior.
 */
internal class SelectionState(private val repository: DemoRepository) {
    var current: SelectedLocation = initialLocation()
        private set
    private var activeRole: UserRole? = null
    private var operatorFarmId: Int? = null

    fun reset() {
        activeRole = null
        operatorFarmId = null
        current = initialLocation()
    }

    /** Inicializa una ubicación válida para el supervisor o para la granja real del operario. */
    fun startSession(user: DemoUser): Boolean {
        reset()

        val location = when (user.role) {
            UserRole.SUPERVISOR -> initialLocation()
            UserRole.OPERARIO -> repository.farms
                .firstOrNull { it.name.equals(user.farm.trim(), ignoreCase = true) }
                ?.let(::firstLocationForFarm)
        } ?: return false

        activeRole = user.role
        operatorFarmId = location.farm.id.takeIf { user.role == UserRole.OPERARIO }
        current = location
        return validatedCurrent() != null
    }

    fun selectFarm(id: Int) {
        if (!canAccessFarm(id)) return
        val farm = repository.farm(id) ?: return
        firstLocationForFarm(farm)?.let { current = it }
    }

    fun selectShed(id: Int) {
        val shed = repository.shed(id) ?: return
        if (!canAccessFarm(shed.farmId)) return
        val farm = repository.farm(shed.farmId) ?: return
        val line = repository.linesForShed(shed.id)
            .firstOrNull { it.farmId == farm.id }
            ?: return

        current = SelectedLocation(farm, shed, line)
    }

    fun selectLine(id: Int) {
        val location = locationForLine(repository.line(id)) ?: return
        if (canAccessFarm(location.farm.id)) current = location
    }

    /** Revalida permisos y las tres relaciones antes de utilizar la selección para guardar. */
    fun validatedCurrent(): SelectedLocation? {
        if (!canAccessFarm(current.farm.id)) return null
        return locationForLine(current.line)
            ?.takeIf { it == current }
    }

    private fun initialLocation(): SelectedLocation = repository.lines
        .firstNotNullOfOrNull(::locationForLine)
        ?: error("No existe una selección inicial válida")

    private fun firstLocationForFarm(farm: Farm): SelectedLocation? =
        repository.linesForFarm(farm.id)
            .firstNotNullOfOrNull { line ->
                locationForLine(line)?.takeIf { it.farm.id == farm.id }
            }

    private fun canAccessFarm(farmId: Int): Boolean = when (activeRole) {
        UserRole.SUPERVISOR -> true
        UserRole.OPERARIO -> operatorFarmId == farmId
        null -> false
    }

    private fun locationForLine(line: WaterLine?): SelectedLocation? {
        line ?: return null
        val location = repository.resolveLocation(
            line.farmId,
            line.shedId,
            line.id
        ) ?: return null
        return SelectedLocation(location.farm, location.shed, location.line)
    }
}

/**
 * Construye el registro persistible con ubicación, responsable y hora resueltos automáticamente.
 * Las temperaturas recibidas ya fueron validadas como mediciones manuales obligatorias.
 */
internal fun createFlushingRecord(
    location: SelectedLocation,
    operatorName: String,
    reason: String,
    observation: String,
    photoUri: String?,
    timestamp: FlushingTimestamp,
    beforeTemperatureC: Double,
    afterTemperatureC: Double
) = FlushingRecord(
    farmId = location.farm.id,
    shedId = location.shed.id,
    lineId = location.line.id,
    operatorName = operatorName,
    date = timestamp.date,
    time = timestamp.time,
    reason = reason,
    observation = observation,
    photoUri = photoUri,
    completed = true,
    temperatureAtRegistrationC = null,
    beforeTemperatureC = beforeTemperatureC,
    afterTemperatureC = afterTemperatureC
)

/**
 * Autoriza mutaciones con la identidad local disponible en el prototipo.
 *
 * La coincidencia por nombre no sustituye un identificador de autor estable en producción, pero
 * conserva el modelo existente sin inventar una identidad ni ampliar el esquema Room.
 */
internal fun canManageFlushing(
    user: DemoUser?,
    record: FlushingRecord,
    repository: DemoRepository
): Boolean = canManageFlushing(
    role = user?.role,
    farmName = user?.farm,
    userName = user?.name,
    record = record,
    repository = repository
)

/** Adaptación temporal para el CRUD local, que permanecerá oculto hasta integrar catálogos. */
internal fun canManageSessionFlushing(
    user: SessionUser?,
    record: FlushingRecord,
    repository: DemoRepository
): Boolean = canManageFlushing(
    role = user?.role,
    farmName = user?.farm,
    userName = user?.name,
    record = record,
    repository = repository
)

private fun canManageFlushing(
    role: UserRole?,
    farmName: String?,
    userName: String?,
    record: FlushingRecord,
    repository: DemoRepository
): Boolean {
    if (role != UserRole.OPERARIO || farmName.isNullOrBlank() || userName == null) return false
    val assignedFarm = repository.farms.firstOrNull {
        it.name.equals(farmName.trim(), ignoreCase = true)
    } ?: return false

    return record.farmId == assignedFarm.id &&
            record.operatorName == userName &&
            repository.resolveLocation(record) != null
}

/** Conserva toda la trazabilidad y aplica únicamente los campos expresamente editables. */
internal fun FlushingRecord.withEditableFields(
    reason: String,
    observation: String,
    photoUri: String?,
    beforeTemperatureC: Double,
    afterTemperatureC: Double
): FlushingRecord = copy(
    reason = reason.trim(),
    observation = observation.trim(),
    photoUri = photoUri,
    beforeTemperatureC = beforeTemperatureC,
    afterTemperatureC = afterTemperatureC
)

/** Una base ya existente no se vuelve a poblar aunque su tabla haya sido vaciada intencionalmente. */
internal fun shouldSeedDemoFlushings(
    seedAlreadyCompleted: Boolean,
    databasePreviouslyExisted: Boolean,
    storedRowCount: Int
): Boolean = !seedAlreadyCompleted && !databasePreviouslyExisted && storedRowCount == 0

/** Acepta exclusivamente URIs del directorio privado expuesto por el FileProvider de esta app. */
internal fun isOwnedFlushingPhotoUri(uriValue: String, packageName: String): Boolean {
    val uri = runCatching { URI(uriValue) }.getOrNull() ?: return false
    val firstPathSegment = uri.path
        ?.split('/')
        ?.firstOrNull { it.isNotBlank() }

    return uri.scheme == "content" &&
            uri.authority == "$packageName.fileprovider" &&
            firstPathSegment == "flushing_images"
}

/** Restringe el borrado posterior a una subida al directorio privado de capturas de esta app. */
internal fun isOwnedFlushingPhotoFile(filePath: String, filesDir: File): Boolean = runCatching {
    val photoDirectory = File(filesDir, "flushing_images").canonicalFile
    val candidate = File(filePath).canonicalFile
    candidate.parentFile == photoDirectory
}.getOrDefault(false)

/**
 * Coordina sesión, selección jerárquica, preferencias y persistencia para las pantallas Compose.
 *
 * Expone [StateFlow] inmutables: la UI los observa con `collectAsState()` y se recompone cuando
 * cambia su valor. Las escrituras suspendidas se ejecutan en [viewModelScope], que se cancela al
 * destruir el ViewModel. Los catálogos y flushing visibles son remotos; Room permanece separado
 * como almacenamiento legado.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DemoRepository()
    private val authRepository = AuthRepository(
        authApi = RetrofitProvider.create(AuthApi::class.java),
        credentials = RetrofitProvider.credentials
    )
    private val catalogRepository = CatalogRepository(
        catalogApi = RetrofitProvider.create(CatalogApi::class.java)
    )
    private val alertRepository = AlertRepository(
        api = RetrofitProvider.create(AlertApi::class.java)
    )
    private val flushingRepository = FlushingRepository(
        api = RetrofitProvider.create(FlushingApi::class.java)
    )
    private val photoRepository = PhotoRepository(
        api = RetrofitProvider.create(PhotoApi::class.java)
    )
    private val ambientWeatherRepository = AmbientWeatherRepository(
        api = ExternalWeatherProvider.api
    )
    private val remotePhotoCache = RemotePhotoCache()
    private val photoOperationGate = PhotoOperationGate()
    private val settingsStore = SettingsStore(application)
    private val criticalAlertNotificationCoordinator =
        CriticalAlertNotificationCoordinator(settingsStore)
    private val databasePreviouslyExisted = application.getDatabasePath(DATABASE_NAME).exists()
    private val database = Room.databaseBuilder(application, AppDatabase::class.java, DATABASE_NAME)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    private val flushingDao = database.flushingDao()
    private val flushingTimeProvider = FlushingTimeProvider()
    private val flushingSubmissionGate = FlushingSubmissionGate()
    private val flushingOperationId = FlushingOperationId()
    private val loginAttemptController = LoginAttemptController()
    private val temperatureHistoryFilter = TemperatureHistoryFilter()
    private var loginJob: Job? = null
    private var ambientWeatherJob: Job? = null
    private var catalogJob: Job? = null
    private var lineDetailJob: Job? = null
    private var alertJob: Job? = null
    private var notificationAlertJob: Job? = null
    private var flushingListJob: Job? = null
    private var flushingEditJob: Job? = null
    private var flushingMutationJob: Job? = null
    private val photoJobs = mutableMapOf<Long, Job>()
    private var sessionGeneration = 0L

    val farms = repository.farms
    val sheds = repository.sheds
    val lines = repository.lines
    val alerts = repository.alerts

    // MutableStateFlow queda encapsulado; las pantallas reciben únicamente su vista de solo lectura.
    private val _currentUser = MutableStateFlow<SessionUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _ambientWeather =
        MutableStateFlow<AmbientWeatherUiState>(AmbientWeatherUiState.Loading)
    val ambientWeather = _ambientWeather.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogUiState>(CatalogUiState.Idle)
    val catalog = _catalog.asStateFlow()

    private val _lineDetail = MutableStateFlow<LineDetailUiState>(LineDetailUiState.Idle)
    val lineDetail = _lineDetail.asStateFlow()

    private val _remoteAlerts = MutableStateFlow<RemoteAlertUiState>(RemoteAlertUiState.Idle)
    val remoteAlerts = _remoteAlerts.asStateFlow()

    private val _notificationAlertNavigation =
        MutableStateFlow<NotificationAlertNavigationState>(NotificationAlertNavigationState.Idle)
    val notificationAlertNavigation = _notificationAlertNavigation.asStateFlow()

    private var remoteSelection = RemoteCatalogSelection()

    private val selectionState = SelectionState(repository)

    private val _login = MutableStateFlow(LoginUiState())
    val login = _login.asStateFlow()

    private val _flushingSave = MutableStateFlow(FlushingSaveUiState())
    val flushingSave = _flushingSave.asStateFlow()

    private val _remoteFlushings =
        MutableStateFlow<RemoteFlushingListUiState>(RemoteFlushingListUiState.Idle)
    val remoteFlushings = _remoteFlushings.asStateFlow()

    private val _editingFlushing = MutableStateFlow<RemoteFlushing?>(null)
    val editingFlushing = _editingFlushing.asStateFlow()

    private val _flushingEditPreparation =
        MutableStateFlow<FlushingEditPreparationUiState>(FlushingEditPreparationUiState.Idle)
    val flushingEditPreparation = _flushingEditPreparation.asStateFlow()

    private val _flushingDelete = MutableStateFlow(FlushingDeleteUiState())
    val flushingDelete = _flushingDelete.asStateFlow()

    private val _remotePhotos =
        MutableStateFlow<Map<Long, RemotePhotoUiState>>(emptyMap())
    val remotePhotos = _remotePhotos.asStateFlow()

    private var activeRemoteFlushingLineId: Long? = null
    private var remoteFlushingQueryIsGlobal = false

    // stateIn convierte los Flow de DataStore en estado con un valor inicial apto para Compose.
    val offline = settingsStore.offline.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val criticalNotifications = settingsStore.notifications.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true
    )

    /** `null` significa que DataStore todavía no terminó su primera lectura. */
    val notificationPermissionRequested: StateFlow<Boolean?> =
        settingsStore.notificationPermissionRequested
            .map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Fuente reactiva de flushing. Cada emisión de Room se transforma de entidad a modelo y
     * conserva estados diferenciados de carga, contenido vacío y error.
     */
    val flushingList: StateFlow<FlushingListUiState> = flushingDao.observeAll()
        .map { entities ->
            flushingListUiState(entities.map { it.toModel() })
        }
        .catch { emit(FlushingListUiState.Error) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FlushingListUiState.Loading
        )

    /**
     * Líneas reconstruidas reactivamente con la última temperatura manual posterior válida.
     */
    val effectiveLines: StateFlow<List<WaterLine>> = flushingList
        .map { state -> effectiveWaterLines(repository.lines, state.records) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.lines
        )

    private val demoSeedingJob = viewModelScope.launch {
        seedDemoFlushingsIfNeeded()
    }

    init {
        refreshAmbientWeather()
    }

    fun updateEmail(value: String) {
        if (_login.value.isLoading) return
        _login.update {
            it.copy(
                email = value,
                emailError = null,
                credentialsError = null
            )
        }
    }

    fun updatePassword(value: String) {
        if (_login.value.isLoading) return
        _login.update {
            it.copy(
                password = value,
                passwordError = null,
                credentialsError = null
            )
        }
    }

    /** Valida el formulario y autentica contra Spring Boot sin bloquear el hilo de Compose. */
    fun login() {
        val state = _login.value
        val validation = validateLoginInput(state.email, state.password)

        if (!validation.isValid) {
            _login.update {
                it.copy(
                    emailError = validation.emailError,
                    passwordError = validation.passwordError,
                    credentialsError = null
                )
            }
            return
        }

        val loadingState = loginAttemptController.start(state) ?: return
        _login.value = loadingState
        val loginGeneration = sessionGeneration
        val password = state.password

        loginJob = viewModelScope.launch {
            try {
                when (val result = authRepository.login(validation.email, password)) {
                    is AuthResult.Success -> {
                        if (loginGeneration != sessionGeneration) {
                            authRepository.logout()
                            return@launch
                        }

                        sessionGeneration++
                        remotePhotoCache.clear()
                        _remotePhotos.value = emptyMap()
                        selectionState.reset()
                        _currentUser.value = result.user
                        _login.value = LoginUiState()
                        loadRemoteCatalog(result.user, sessionGeneration)
                        loadRemoteAlerts(sessionGeneration)
                    }

                    AuthResult.InvalidCredentials -> showLoginError(
                        loginGeneration,
                        "Correo o contraseña incorrectos."
                    )

                    AuthResult.NetworkError -> showLoginError(
                        loginGeneration,
                        "No se pudo conectar con el servidor. Revisa la conexión e inténtalo nuevamente."
                    )

                    AuthResult.UnexpectedResponse -> showLoginError(
                        loginGeneration,
                        "El servidor devolvió una respuesta inesperada. Inténtalo nuevamente."
                    )
                }
            } finally {
                loginAttemptController.finish()
                loginJob = null
                if (loginGeneration == sessionGeneration && _login.value.isLoading) {
                    _login.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**
     * Cierra la sesión y limpia únicamente estado temporal: selección, formulario y bloqueo.
     * Room, DataStore y fotografías permanecen intactos.
     */
    fun logout() {
        sessionGeneration++
        loginJob?.cancel()
        loginJob = null
        catalogJob?.cancel()
        catalogJob = null
        lineDetailJob?.cancel()
        lineDetailJob = null
        alertJob?.cancel()
        alertJob = null
        notificationAlertJob?.cancel()
        notificationAlertJob = null
        flushingListJob?.cancel()
        flushingListJob = null
        flushingEditJob?.cancel()
        flushingEditJob = null
        flushingMutationJob?.cancel()
        flushingMutationJob = null
        photoJobs.values.forEach(Job::cancel)
        photoJobs.clear()
        photoOperationGate.clear()
        remotePhotoCache.clear()
        authRepository.logout()
        _currentUser.value = null
        _login.value = LoginUiState()
        _catalog.value = CatalogUiState.Idle
        _lineDetail.value = LineDetailUiState.Idle
        _remoteAlerts.value = RemoteAlertUiState.Idle
        _notificationAlertNavigation.value = NotificationAlertNavigationState.Idle
        remoteSelection = RemoteCatalogSelection()
        selectionState.reset()
        flushingSubmissionGate.unlock()
        flushingOperationId.clear()
        _flushingSave.value = FlushingSaveUiState()
        _remoteFlushings.value = RemoteFlushingListUiState.Idle
        _editingFlushing.value = null
        _flushingEditPreparation.value = FlushingEditPreparationUiState.Idle
        _flushingDelete.value = FlushingDeleteUiState()
        _remotePhotos.value = emptyMap()
        activeRemoteFlushingLineId = null
        remoteFlushingQueryIsGlobal = false
    }

    private fun showLoginError(generation: Long, message: String) {
        if (generation != sessionGeneration) return
        _login.update {
            it.copy(
                password = "",
                credentialsError = message,
                isLoading = false
            )
        }
    }

    fun retryCatalog() {
        val user = _currentUser.value ?: return
        loadRemoteCatalog(user, sessionGeneration)
    }

    /** Consulta la API externa sin compartir la sesión HTTP Basic del backend propio. */
    fun refreshAmbientWeather() {
        if (ambientWeatherJob?.isActive == true) return
        _ambientWeather.value = AmbientWeatherUiState.Loading
        ambientWeatherJob = viewModelScope.launch {
            try {
                _ambientWeather.value = when (val result = ambientWeatherRepository.current()) {
                    is AmbientWeatherResult.Success ->
                        AmbientWeatherUiState.Ready(result.weather)
                    is AmbientWeatherResult.Failure ->
                        AmbientWeatherUiState.Error(result.error)
                }
            } finally {
                ambientWeatherJob = null
            }
        }
    }

    fun selectRemoteFarm(id: Long) {
        val remoteCatalog = _catalog.value.catalogOrNull ?: return
        val farm = remoteCatalog.farm(id) ?: return
        val shed = remoteCatalog.shedsForFarm(farm.id).firstOrNull()
        val line = shed?.let { remoteCatalog.linesForShed(it.id).firstOrNull() }
        remoteSelection = RemoteCatalogSelection(farm.id, shed?.id, line?.id)
        _lineDetail.value = LineDetailUiState.Idle
    }

    fun selectRemoteShed(id: Long) {
        val remoteCatalog = _catalog.value.catalogOrNull ?: return
        val shed = remoteCatalog.shed(id) ?: return
        val selectedFarmId = remoteSelection.farmId ?: return
        if (shed.farmId != selectedFarmId) return
        val line = remoteCatalog.linesForShed(shed.id).firstOrNull()
        remoteSelection = RemoteCatalogSelection(selectedFarmId, shed.id, line?.id)
        _lineDetail.value = LineDetailUiState.Idle
    }

    fun selectRemoteLine(id: Long) {
        val remoteCatalog = _catalog.value.catalogOrNull ?: return
        val line = remoteCatalog.line(id) ?: return
        remoteSelection = RemoteCatalogSelection(line.farmId, line.shedId, line.id)
        loadRemoteLineDetail(line.id, sessionGeneration)
    }

    fun selectedRemoteFarm(): RemoteFarm? =
        remoteSelection.farmId?.let { _catalog.value.catalogOrNull?.farm(it) }

    fun selectedRemoteShed(): RemoteShed? =
        remoteSelection.shedId?.let { _catalog.value.catalogOrNull?.shed(it) }

    fun selectedRemoteLine(): RemoteWaterLine? =
        remoteSelection.lineId?.let { _catalog.value.catalogOrNull?.line(it) }

    fun remoteShedsForFarm(farmId: Long): List<RemoteShed> =
        _catalog.value.catalogOrNull?.shedsForFarm(farmId).orEmpty()

    fun remoteLinesForFarm(farmId: Long): List<RemoteWaterLine> =
        _catalog.value.catalogOrNull?.linesForFarm(farmId).orEmpty()

    fun remoteLinesForShed(shedId: Long): List<RemoteWaterLine> =
        _catalog.value.catalogOrNull?.linesForShed(shedId).orEmpty()

    /** Selecciona la ubicación remota validada de una alerta antes de abrir su detalle. */
    fun selectRemoteAlert(alert: RemoteAlert): Boolean {
        val user = _currentUser.value ?: return false
        val remoteCatalog = _catalog.value.catalogOrNull ?: return false
        if (!canAccessRemoteAlert(user, alert, remoteCatalog)) return false
        selectRemoteLine(alert.lineId)
        return true
    }

    /** Conserva el ID si falta login y lo resuelve contra el endpoint protegido cuando sea posible. */
    fun openRemoteAlertFromNotification(alertId: Long) {
        notificationAlertJob?.cancel()
        notificationAlertJob = null
        if (alertId <= 0L) {
            _notificationAlertNavigation.value = NotificationAlertNavigationState.Unavailable(
                alertId,
                "La notificación no contiene una alerta válida."
            )
            return
        }

        _notificationAlertNavigation.value = when {
            _currentUser.value == null ->
                NotificationAlertNavigationState.WaitingForSession(alertId)
            _catalog.value.catalogOrNull == null ->
                NotificationAlertNavigationState.WaitingForCatalog(alertId)
            else -> NotificationAlertNavigationState.WaitingForCatalog(alertId)
        }
        resolvePendingNotificationAlert(sessionGeneration)
    }

    fun consumeNotificationAlertNavigation() {
        _notificationAlertNavigation.value = NotificationAlertNavigationState.Idle
    }

    fun markNotificationAlertUnavailable(alertId: Long) {
        _notificationAlertNavigation.value = NotificationAlertNavigationState.Unavailable(
            alertId,
            "La alerta ya no está disponible para esta sesión."
        )
    }

    fun retryRemoteAlerts() {
        if (_currentUser.value != null) loadRemoteAlerts(sessionGeneration)
    }

    private fun loadRemoteAlerts(generation: Long) {
        alertJob?.cancel()
        _remoteAlerts.value = RemoteAlertUiState.Loading
        alertJob = viewModelScope.launch {
            when (val result = alertRepository.all()) {
                is AlertResult.Success -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    _remoteAlerts.value = RemoteAlertUiState.Ready(result.data)
                }
                is AlertResult.Failure -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    if (result.error == AlertError.UNAUTHORIZED) {
                        expireRemoteSession()
                    } else {
                        _remoteAlerts.value = RemoteAlertUiState.Error(result.error)
                    }
                }
            }
        }
    }

    private fun resolvePendingNotificationAlert(generation: Long) {
        val alertId = when (val state = _notificationAlertNavigation.value) {
            is NotificationAlertNavigationState.WaitingForSession -> state.alertId
            is NotificationAlertNavigationState.WaitingForCatalog -> state.alertId
            else -> return
        }
        if (_currentUser.value == null) {
            _notificationAlertNavigation.value =
                NotificationAlertNavigationState.WaitingForSession(alertId)
            return
        }
        if (_catalog.value.catalogOrNull == null) {
            _notificationAlertNavigation.value =
                NotificationAlertNavigationState.WaitingForCatalog(alertId)
            return
        }

        notificationAlertJob?.cancel()
        _notificationAlertNavigation.value = NotificationAlertNavigationState.Loading(alertId)
        notificationAlertJob = viewModelScope.launch {
            when (val result = alertRepository.byId(alertId)) {
                is AlertResult.Success -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    val user = _currentUser.value
                    val catalog = _catalog.value.catalogOrNull
                    _notificationAlertNavigation.value = if (
                        user != null &&
                        catalog != null &&
                        canAccessRemoteAlert(user, result.data, catalog)
                    ) {
                        NotificationAlertNavigationState.Ready(result.data)
                    } else {
                        NotificationAlertNavigationState.Unavailable(
                            alertId,
                            "La alerta no pertenece al alcance de esta sesión."
                        )
                    }
                }
                is AlertResult.Failure -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    if (result.error == AlertError.UNAUTHORIZED) {
                        expireRemoteSession()
                    } else {
                        _notificationAlertNavigation.value =
                            NotificationAlertNavigationState.Unavailable(
                                alertId,
                                notificationAlertErrorMessage(result.error)
                            )
                    }
                }
            }
        }
    }

    private fun loadRemoteCatalog(user: SessionUser, generation: Long) {
        catalogJob?.cancel()
        lineDetailJob?.cancel()
        _catalog.value = CatalogUiState.Loading
        _lineDetail.value = LineDetailUiState.Idle
        remoteSelection = RemoteCatalogSelection()

        catalogJob = viewModelScope.launch {
            val result = catalogRepository.loadCatalog(user)
            if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch

            when (result) {
                is CatalogResult.Success -> {
                    val remoteCatalog = result.data
                    if (remoteCatalog.farms.isEmpty()) {
                        _catalog.value = CatalogUiState.Empty
                        failPendingNotificationAlert(
                            "No hay un catálogo disponible para validar la alerta."
                        )
                    } else {
                        _catalog.value = CatalogUiState.Content(remoteCatalog)
                        val initialFarm = when (user.role) {
                            UserRole.OPERARIO -> user.farmId?.let(remoteCatalog::farm)
                            UserRole.SUPERVISOR -> remoteCatalog.farms.firstOrNull()
                        }
                        initialFarm?.let { selectRemoteFarm(it.id) }
                        resolvePendingNotificationAlert(generation)
                    }
                }

                is CatalogResult.Failure -> handleCatalogFailure(result.error)
            }
        }
    }

    private fun loadRemoteLineDetail(lineId: Long, generation: Long) {
        lineDetailJob?.cancel()
        _lineDetail.value = LineDetailUiState.Loading

        lineDetailJob = viewModelScope.launch {
            val result = catalogRepository.line(lineId)
            if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
            if (remoteSelection.lineId != lineId) return@launch

            when (result) {
                is CatalogResult.Success -> {
                    val listedLine = _catalog.value.catalogOrNull?.line(lineId)
                    val detail = result.data
                    _lineDetail.value = if (
                        listedLine != null &&
                        listedLine.farmId == detail.farmId &&
                        listedLine.shedId == detail.shedId
                    ) {
                        LineDetailUiState.Content(detail)
                    } else {
                        LineDetailUiState.Error(CatalogError.UNEXPECTED)
                    }
                }

                is CatalogResult.Failure -> {
                    if (result.error == CatalogError.UNAUTHORIZED) {
                        expireRemoteSession()
                    } else {
                        _lineDetail.value = LineDetailUiState.Error(result.error)
                    }
                }
            }
        }
    }

    private fun handleCatalogFailure(error: CatalogError) {
        if (error == CatalogError.UNAUTHORIZED) {
            expireRemoteSession()
        } else {
            _catalog.value = CatalogUiState.Error(error)
            failPendingNotificationAlert(
                "No fue posible validar la alerta porque el catálogo no está disponible."
            )
        }
    }

    private fun failPendingNotificationAlert(message: String) {
        val alertId = when (val state = _notificationAlertNavigation.value) {
            is NotificationAlertNavigationState.WaitingForSession -> state.alertId
            is NotificationAlertNavigationState.WaitingForCatalog -> state.alertId
            is NotificationAlertNavigationState.Loading -> state.alertId
            else -> return
        }
        _notificationAlertNavigation.value =
            NotificationAlertNavigationState.Unavailable(alertId, message)
    }

    private fun expireRemoteSession() {
        logout()
        _login.value = LoginUiState(
            credentialsError = "La sesión dejó de ser válida. Inicia sesión nuevamente."
        )
    }

    fun setOffline(value: Boolean) =
        viewModelScope.launch {
            settingsStore.setOffline(value)
        }

    fun setCriticalNotifications(value: Boolean) =
        viewModelScope.launch {
            settingsStore.setNotifications(value)
        }

    suspend fun markNotificationPermissionRequested() {
        settingsStore.markNotificationPermissionRequested()
    }

    /** Publica todas las críticas visibles y persiste solo las que Android aceptó. */
    suspend fun publishVisibleCriticalNotifications(
        notificationsAvailable: Boolean,
        publish: (RemoteAlert) -> Boolean
    ): List<Long> {
        val user = _currentUser.value ?: return emptyList()
        return criticalAlertNotificationCoordinator.publishPending(
            userId = user.id,
            alerts = _remoteAlerts.value.alerts,
            notificationsEnabled = criticalNotifications.value,
            notificationsAvailable = notificationsAvailable,
            publish = publish
        )
    }

    fun selectFarm(id: Int) {
        selectionState.selectFarm(id)
    }

    fun selectShed(id: Int) {
        selectionState.selectShed(id)
    }

    fun selectLine(id: Int) {
        selectionState.selectLine(id)
    }

    fun selectedFarm(): Farm =
        selectionState.current.farm

    fun selectedShed(): Shed =
        selectionState.current.shed

    fun selectedLine(): WaterLine =
        effectiveLine(selectionState.current.line)

    fun shedsForSelectedFarm(): List<Shed> =
        repository.shedsForFarm(selectionState.current.farm.id)

    fun linesForSelectedShed(): List<WaterLine> =
        linesForShed(selectionState.current.shed.id)

    fun shedsForFarm(farmId: Int): List<Shed> =
        repository.shedsForFarm(farmId)

    fun shedCountForFarm(farm: Farm): Int =
        repository.shedCountForFarm(farm)

    fun linesForShed(shedId: Int): List<WaterLine> =
        effectiveVersionsOf(repository.linesForShed(shedId))

    fun linesForFarm(farmId: Int): List<WaterLine> =
        effectiveVersionsOf(repository.linesForFarm(farmId))

    fun farm(id: Int): Farm? =
        repository.farm(id)

    fun shed(id: Int): Shed? =
        repository.shed(id)

    fun line(id: Int): WaterLine? =
        repository.line(id)?.let(::effectiveLine)

    private fun effectiveVersionsOf(demoLines: List<WaterLine>): List<WaterLine> =
        demoLines.map(::effectiveLine)

    private fun effectiveLine(demoLine: WaterLine): WaterLine =
        effectiveLines.value.firstOrNull {
            it.farmId == demoLine.farmId &&
                    it.shedId == demoLine.shedId &&
                    it.id == demoLine.id
        } ?: demoLine

    fun resolveAlertLocation(alert: Alert): ResolvedLocation? =
        repository.resolveLocation(alert)

    fun resolveFlushingLocation(flushing: FlushingRecord): ResolvedLocation? =
        repository.resolveLocation(flushing)

    fun flushingsForLine(
        records: List<FlushingRecord>,
        line: WaterLine
    ): List<FlushingRecord> = repository.flushingsForLine(records, line)

    fun selectAlertLocation(alert: Alert): Boolean {
        val location = repository.resolveLocation(alert) ?: return false
        selectionState.selectLine(location.line.id)
        return selectionState.validatedCurrent()?.line?.id == location.line.id
    }

    fun historyForSelectedLine(): List<TemperatureRecord> =
        repository.historyForLine(selectionState.current.line.id)

    internal fun historyForSelectedLine(
        period: TemperatureHistoryPeriod
    ): List<TemperatureRecord> =
        temperatureHistoryFilter.filter(historyForSelectedLine(), period)

    internal fun currentFlushingTimestamp(): FlushingTimestamp =
        flushingTimeProvider.now()

    /** Abre un formulario remoto nuevo y genera una única clave idempotente para sus reintentos. */
    fun prepareFlushingRegistration() {
        flushingSubmissionGate.unlock()
        flushingOperationId.prepareNew()
        _flushingSave.value = FlushingSaveUiState()
        _editingFlushing.value = null
        _flushingEditPreparation.value = FlushingEditPreparationUiState.Idle
        _flushingDelete.value = FlushingDeleteUiState()
    }

    fun loadRemoteFlushingsForLine(lineId: Long) {
        val line = _catalog.value.catalogOrNull?.line(lineId) ?: return
        flushingListJob?.cancel()
        activeRemoteFlushingLineId = line.id
        remoteFlushingQueryIsGlobal = false
        _remoteFlushings.value = RemoteFlushingListUiState.Loading
        val generation = sessionGeneration

        flushingListJob = viewModelScope.launch {
            when (val result = flushingRepository.forLine(line.id)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    _remoteFlushings.value = RemoteFlushingListUiState.Ready(result.data)
                }
                is FlushingResult.Failure -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    publishFlushingListFailure(result.error)
                }
            }
        }
    }

    fun loadAllRemoteFlushings() {
        val user = _currentUser.value ?: return
        flushingListJob?.cancel()
        activeRemoteFlushingLineId = null
        remoteFlushingQueryIsGlobal = true
        _remoteFlushings.value = RemoteFlushingListUiState.Loading
        val generation = sessionGeneration

        flushingListJob = viewModelScope.launch {
            when (val result = flushingRepository.all(user)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    _remoteFlushings.value = RemoteFlushingListUiState.Ready(result.data)
                }
                is FlushingResult.Failure -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    publishFlushingListFailure(result.error)
                }
            }
        }
    }

    /** Relee el detalle canónico antes de permitir editar y conserva su versión optimista. */
    fun prepareFlushingEdit(recordId: Long) {
        val listed = _remoteFlushings.value.records.firstOrNull { it.id == recordId }
        if (listed == null || !canManageRemoteFlushing(_currentUser.value, listed)) {
            _flushingEditPreparation.value =
                FlushingEditPreparationUiState.Error(FlushingError.FORBIDDEN)
            return
        }

        flushingEditJob?.cancel()
        flushingSubmissionGate.unlock()
        _flushingSave.value = FlushingSaveUiState()
        _flushingDelete.value = FlushingDeleteUiState()
        _editingFlushing.value = null
        _flushingEditPreparation.value = FlushingEditPreparationUiState.Loading
        val generation = sessionGeneration

        flushingEditJob = viewModelScope.launch {
            when (val result = flushingRepository.byId(recordId)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    val record = result.data
                    if (!hasSameRemoteFlushingIdentity(listed, record)) {
                        _flushingEditPreparation.value =
                            FlushingEditPreparationUiState.Error(FlushingError.UNEXPECTED)
                    } else if (!canManageRemoteFlushing(_currentUser.value, record)) {
                        _flushingEditPreparation.value =
                            FlushingEditPreparationUiState.Error(FlushingError.FORBIDDEN)
                    } else {
                        upsertRemoteFlushing(record)
                        _editingFlushing.value = record
                        _flushingEditPreparation.value =
                            FlushingEditPreparationUiState.Ready(record.id)
                    }
                }
                is FlushingResult.Failure -> {
                    if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                    if (result.error == FlushingError.UNAUTHORIZED) {
                        expireRemoteSession()
                    } else {
                        _flushingEditPreparation.value =
                            FlushingEditPreparationUiState.Error(result.error)
                    }
                }
            }
        }
    }

    fun consumeFlushingEditPreparation(recordId: Long) {
        if ((_flushingEditPreparation.value as? FlushingEditPreparationUiState.Ready)
                ?.recordId == recordId
        ) {
            _flushingEditPreparation.value = FlushingEditPreparationUiState.Idle
        }
    }

    fun clearFlushingEditPreparation() {
        if (_flushingEditPreparation.value !is FlushingEditPreparationUiState.Loading) {
            _flushingEditPreparation.value = FlushingEditPreparationUiState.Idle
        }
    }

    fun cancelFlushingEdit() {
        if (_flushingSave.value.isSaving) return
        _editingFlushing.value = null
        _flushingEditPreparation.value = FlushingEditPreparationUiState.Idle
        _flushingSave.value = FlushingSaveUiState()
        flushingSubmissionGate.unlock()
    }

    fun clearFlushingValidationErrors() {
        _flushingSave.update {
            it.copy(
                errorMessage = null,
                reasonError = null,
                observationError = null,
                beforeTemperatureError = null,
                afterTemperatureError = null
            )
        }
    }

    fun consumeFlushingSuccess(recordId: Long) {
        _flushingSave.update {
            if (it.savedRecordId == recordId) FlushingSaveUiState() else it
        }
        flushingOperationId.clear()
        flushingSubmissionGate.unlock()
    }

    fun consumeFlushingUpdateSuccess(recordId: Long) {
        _flushingSave.update {
            if (it.updatedRecordId == recordId) FlushingSaveUiState() else it
        }
        if (_editingFlushing.value?.id == recordId) _editingFlushing.value = null
        flushingSubmissionGate.unlock()
    }

    fun clearFlushingDeleteState() {
        if (!_flushingDelete.value.isDeleting) {
            _flushingDelete.value = FlushingDeleteUiState()
            flushingSubmissionGate.unlock()
        }
    }

    fun consumeFlushingDeleteSuccess(recordId: Long) {
        if (_flushingDelete.value.deletedRecordId == recordId) {
            _flushingDelete.value = FlushingDeleteUiState()
        }
        flushingSubmissionGate.unlock()
    }

    /** Control visual por autoría estable; el backend realiza la autorización definitiva. */
    fun canCurrentUserManageFlushing(record: RemoteFlushing): Boolean =
        canManageRemoteFlushing(_currentUser.value, record)

    /**
     * Valida y guarda un flushing sin admitir envíos simultáneos.
     *
     * El UUID se conserva cuando falla una petición y solo se reemplaza al preparar otro formulario.
     * La foto local no se incluye: tendrá su endpoint remoto en una etapa posterior.
     */
    fun registerFlushing(
        reason: String,
        observation: String,
        beforeTemperatureInput: String,
        afterTemperatureInput: String,
        photoPath: String? = null,
        photoUri: String? = null
    ) {
        val validation = validateFlushingForm(
            reason,
            observation,
            beforeTemperatureInput,
            afterTemperatureInput
        )
        if (!validation.isValid) {
            _flushingSave.value = FlushingSaveUiState(
                reasonError = validation.reasonError,
                observationError = validation.observationError,
                beforeTemperatureError = validation.beforeTemperature.error,
                afterTemperatureError = validation.afterTemperature.error
            )
            return
        }

        if (!flushingSubmissionGate.tryLock()) return

        val user = _currentUser.value
        if (user?.role != UserRole.OPERARIO) {
            flushingSubmissionGate.unlock()
            _flushingSave.value = FlushingSaveUiState(
                errorMessage = "Solo un operario autenticado puede registrar flushing."
            )
            return
        }

        val line = selectedRemoteLine()
        if (line == null || line.farmId != user.farmId) {
            flushingSubmissionGate.unlock()
            _flushingSave.value = FlushingSaveUiState(
                errorMessage = "La granja, el galpón y la línea seleccionados no son válidos. Vuelve atrás y selecciónalos nuevamente."
            )
            return
        }

        val command = CreateFlushingCommand(
            clientOperationId = flushingOperationId.currentOrPrepare(),
            reason = reason.trim(),
            observation = observation.trim().ifBlank { null },
            beforeTemperatureC = validation.beforeTemperature.decimalValue!!,
            afterTemperatureC = validation.afterTemperature.decimalValue!!
        )

        _flushingSave.value = FlushingSaveUiState(isSaving = true)
        val registrationSession = sessionGeneration
        val photoCandidate = localPhotoCandidate(photoPath, photoUri)

        flushingMutationJob = viewModelScope.launch {
            when (val result = flushingRepository.create(line.id, command)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(registrationSession, sessionGeneration)) return@launch
                    val created = result.data
                    if (!canManageRemoteFlushing(user, created) ||
                        created.shedId != line.shedId || created.farmId != line.farmId
                    ) {
                        failFlushingWrite(
                            registrationSession,
                            "El backend devolvió un flushing con autoría o ubicación inesperada."
                        )
                        return@launch
                    }
                    upsertRemoteFlushing(created)
                    val photoWarning = photoCandidate?.let {
                        uploadPhotoForSavedFlushing(created.id, it, registrationSession)
                    }
                    if (!isCurrentSessionResult(registrationSession, sessionGeneration)) return@launch
                    val refreshed = refreshRemoteLineAfterFlushing(line.id, registrationSession)
                    if (!isCurrentSessionResult(registrationSession, sessionGeneration)) return@launch
                    loadRemoteAlerts(registrationSession)
                    _flushingSave.value = FlushingSaveUiState(
                        savedRecordId = created.id,
                        temperatureRefreshWarning = if (refreshed) null else TEMPERATURE_REFRESH_WARNING,
                        photoUploadWarning = photoWarning
                    )
                }
                is FlushingResult.Failure -> failFlushingWrite(
                    registrationSession,
                    when (result.error) {
                        FlushingError.CONFLICT ->
                            "El identificador de esta operación entró en conflicto. No se creó otro registro."
                        else -> flushingErrorMessage(result.error)
                    },
                    result.error
                )
            }
        }
    }

    /**
     * Actualiza solo los campos del contrato y envía la versión recibida desde el servidor.
     */
    fun updateFlushing(
        reason: String,
        observation: String,
        beforeTemperatureInput: String,
        afterTemperatureInput: String,
        photoPath: String? = null,
        photoUri: String? = null
    ) {
        val validation = validateFlushingForm(
            reason,
            observation,
            beforeTemperatureInput,
            afterTemperatureInput
        )
        if (!validation.isValid) {
            _flushingSave.value = FlushingSaveUiState(
                reasonError = validation.reasonError,
                observationError = validation.observationError,
                beforeTemperatureError = validation.beforeTemperature.error,
                afterTemperatureError = validation.afterTemperature.error
            )
            return
        }

        val editing = _editingFlushing.value
        if (editing == null || !canManageRemoteFlushing(_currentUser.value, editing)) {
            _flushingSave.value = FlushingSaveUiState(
                errorMessage = "No se encontró el flushing que deseas editar. Vuelve al historial e inténtalo nuevamente."
            )
            return
        }

        if (!flushingSubmissionGate.tryLock()) return
        val mutationSession = sessionGeneration
        val photoCandidate = localPhotoCandidate(photoPath, photoUri)
        _flushingSave.value = FlushingSaveUiState(isSaving = true)

        flushingMutationJob = viewModelScope.launch {
            val command = UpdateFlushingCommand(
                reason = reason.trim(),
                observation = observation.trim().ifBlank { null },
                beforeTemperatureC = validation.beforeTemperature.decimalValue!!,
                afterTemperatureC = validation.afterTemperature.decimalValue!!,
                version = editing.version
            )
            when (val result = flushingRepository.update(editing.id, command)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(mutationSession, sessionGeneration)) return@launch
                    val updated = result.data
                    if (!hasSameRemoteFlushingIdentity(editing, updated)) {
                        failFlushingWrite(
                            mutationSession,
                            "El backend modificó campos inmutables del flushing."
                        )
                        return@launch
                    }
                    upsertRemoteFlushing(updated)
                    _editingFlushing.value = updated
                    val photoWarning = photoCandidate?.let {
                        uploadPhotoForSavedFlushing(updated.id, it, mutationSession)
                    }
                    if (!isCurrentSessionResult(mutationSession, sessionGeneration)) return@launch
                    val refreshed = refreshRemoteLineAfterFlushing(updated.lineId, mutationSession)
                    if (!isCurrentSessionResult(mutationSession, sessionGeneration)) return@launch
                    loadRemoteAlerts(mutationSession)
                    _flushingSave.value = FlushingSaveUiState(
                        updatedRecordId = updated.id,
                        temperatureRefreshWarning = if (refreshed) null else TEMPERATURE_REFRESH_WARNING,
                        photoUploadWarning = photoWarning
                    )
                }
                is FlushingResult.Failure -> {
                    if (result.error == FlushingError.CONFLICT) {
                        reloadRemoteFlushingAfterConflict(editing, mutationSession)
                    } else {
                        failFlushingWrite(
                            mutationSession,
                            flushingErrorMessage(result.error),
                            result.error
                        )
                    }
                }
            }
        }
    }

    /** Elimina solo después de recibir el 204 documentado por el backend. */
    fun deleteFlushing(recordId: Long) {
        if (recordId <= 0) {
            _flushingDelete.value = FlushingDeleteUiState(
                errorMessage = "No se pudo identificar el flushing que deseas eliminar."
            )
            return
        }
        val record = _remoteFlushings.value.records.firstOrNull { it.id == recordId }
            ?: _editingFlushing.value?.takeIf { it.id == recordId }
        if (record == null || !canManageRemoteFlushing(_currentUser.value, record)) {
            _flushingDelete.value = FlushingDeleteUiState(
                errorMessage = "No tienes autorización para eliminar este flushing."
            )
            return
        }
        if (!flushingSubmissionGate.tryLock()) return

        val mutationSession = sessionGeneration
        _flushingDelete.value = FlushingDeleteUiState(deletingRecordId = recordId)

        flushingMutationJob = viewModelScope.launch {
            when (val result = flushingRepository.delete(recordId)) {
                is FlushingResult.Success -> {
                    if (!isCurrentSessionResult(mutationSession, sessionGeneration)) return@launch
                    removeRemoteFlushing(recordId)
                    remotePhotoCache.invalidate(recordId)
                    _remotePhotos.update { it - recordId }
                    if (_editingFlushing.value?.id == recordId) _editingFlushing.value = null
                    val refreshed = refreshRemoteLineAfterFlushing(record.lineId, mutationSession)
                    if (!isCurrentSessionResult(mutationSession, sessionGeneration)) return@launch
                    loadRemoteAlerts(mutationSession)
                    _flushingDelete.value = FlushingDeleteUiState(
                        deletedRecordId = recordId,
                        temperatureRefreshWarning = if (refreshed) null else TEMPERATURE_REFRESH_WARNING
                    )
                }
                is FlushingResult.Failure -> failFlushingDelete(
                    mutationSession,
                    flushingErrorMessage(result.error),
                    result.error
                )
            }
        }
    }

    /** Consulta metadata una sola vez y descarga solo cuando no existe una entrada en caché. */
    fun loadRemotePhoto(flushingId: Long) {
        if (flushingId <= 0 || _currentUser.value == null) return

        remotePhotoCache.get(flushingId)?.let { cached ->
            _remotePhotos.update { it + (flushingId to RemotePhotoUiState.Available(cached)) }
            return
        }
        when (_remotePhotos.value[flushingId]) {
            null,
            RemotePhotoUiState.Idle,
            is RemotePhotoUiState.LoadError -> Unit
            else -> return
        }
        if (photoJobs[flushingId]?.isCompleted == false) return

        val generation = sessionGeneration
        _remotePhotos.update {
            it + (flushingId to RemotePhotoUiState.LoadingMetadata)
        }
        photoJobs[flushingId] = viewModelScope.launch {
            try {
                when (val metadataResult = photoRepository.metadata(flushingId)) {
                    is PhotoResult.Success -> {
                        if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                        val metadata = metadataResult.data
                        if (metadata == null) {
                            remotePhotoCache.invalidate(flushingId)
                            setRemotePhotoState(flushingId, RemotePhotoUiState.NoPhoto)
                            return@launch
                        }

                        setRemotePhotoState(
                            flushingId,
                            RemotePhotoUiState.Downloading(metadata)
                        )
                        when (val download = photoRepository.download(flushingId, metadata)) {
                            is PhotoResult.Success -> {
                                if (!isCurrentSessionResult(generation, sessionGeneration)) {
                                    return@launch
                                }
                                remotePhotoCache.put(download.data)
                                setRemotePhotoState(
                                    flushingId,
                                    RemotePhotoUiState.Available(download.data)
                                )
                            }
                            is PhotoResult.Failure -> handlePhotoLoadFailure(
                                flushingId,
                                download.error,
                                generation
                            )
                        }
                    }
                    is PhotoResult.Failure -> handlePhotoLoadFailure(
                        flushingId,
                        metadataResult.error,
                        generation
                    )
                }
            } finally {
                photoJobs.remove(flushingId)
            }
        }
    }

    /** Reintenta exclusivamente el multipart; nunca vuelve a crear o actualizar el flushing. */
    fun retryRemotePhotoUpload(flushingId: Long) {
        val pending = _remotePhotos.value[flushingId] as? RemotePhotoUiState.UploadError ?: return
        if (_currentUser.value?.role != UserRole.OPERARIO) return
        if (!photoOperationGate.tryLock(flushingId)) return

        val generation = sessionGeneration
        photoJobs[flushingId] = viewModelScope.launch {
            try {
                val warning = uploadPhotoForSavedFlushing(
                    flushingId,
                    pending.candidate,
                    generation
                )
                if (isCurrentSessionResult(generation, sessionGeneration)) {
                    _flushingSave.update { save ->
                        if (save.savedRecordId == flushingId || save.updatedRecordId == flushingId) {
                            save.copy(photoUploadWarning = warning)
                        } else {
                            save
                        }
                    }
                }
            } finally {
                photoOperationGate.unlock(flushingId)
                photoJobs.remove(flushingId)
            }
        }
    }

    /** Elimina únicamente la evidencia; el flushing remoto permanece intacto. */
    fun deleteRemotePhoto(flushingId: Long) {
        val current = _remotePhotos.value[flushingId]
        val content = when (current) {
            is RemotePhotoUiState.Available -> current.content
            is RemotePhotoUiState.DeleteError -> current.content
            else -> return
        }
        val record = remoteFlushing(flushingId) ?: return
        if (!canManageRemoteFlushing(_currentUser.value, record)) return
        if (!photoOperationGate.tryLock(flushingId)) return

        val generation = sessionGeneration
        setRemotePhotoState(flushingId, RemotePhotoUiState.Deleting(content))
        photoJobs[flushingId] = viewModelScope.launch {
            try {
                when (val result = photoRepository.delete(flushingId)) {
                    is PhotoResult.Success -> {
                        if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                        remotePhotoCache.invalidate(flushingId)
                        setRemotePhotoState(flushingId, RemotePhotoUiState.NoPhoto)
                    }
                    is PhotoResult.Failure -> {
                        if (!isCurrentSessionResult(generation, sessionGeneration)) return@launch
                        if (result.error == PhotoError.UNAUTHORIZED) {
                            expireRemoteSession()
                        } else {
                            setRemotePhotoState(
                                flushingId,
                                RemotePhotoUiState.DeleteError(content, result.error)
                            )
                        }
                    }
                }
            } finally {
                photoOperationGate.unlock(flushingId)
                photoJobs.remove(flushingId)
            }
        }
    }

    private fun localPhotoCandidate(
        photoPath: String?,
        photoUri: String?
    ): LocalPhotoCandidate? = if (!photoPath.isNullOrBlank() && !photoUri.isNullOrBlank()) {
        LocalPhotoCandidate(photoPath, photoUri)
    } else {
        null
    }

    /** Devuelve una advertencia solo cuando el CRUD quedó guardado pero la foto no. */
    private suspend fun uploadPhotoForSavedFlushing(
        flushingId: Long,
        candidate: LocalPhotoCandidate,
        generation: Long
    ): String? {
        if (!isCurrentSessionResult(generation, sessionGeneration)) return null
        setRemotePhotoState(flushingId, RemotePhotoUiState.Uploading(candidate))

        val preparedResult = withContext(Dispatchers.IO) {
            photoRepository.prepare(candidate.filePath)
        }
        if (!isCurrentSessionResult(generation, sessionGeneration)) return null
        if (preparedResult is PhotoResult.Failure) {
            setRemotePhotoState(
                flushingId,
                RemotePhotoUiState.UploadError(candidate, preparedResult.error)
            )
            return photoUploadPendingMessage(preparedResult.error)
        }

        val prepared = (preparedResult as PhotoResult.Success).data
        return when (val result = photoRepository.upload(flushingId, prepared)) {
            is PhotoResult.Success -> {
                if (!isCurrentSessionResult(generation, sessionGeneration)) return null
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { prepared.file.readBytes() }.getOrNull()
                }
                if (bytes != null) {
                    val content = RemotePhotoContent(result.data, bytes)
                    remotePhotoCache.put(content)
                    setRemotePhotoState(flushingId, RemotePhotoUiState.Available(content))
                } else {
                    remotePhotoCache.invalidate(flushingId)
                    setRemotePhotoState(flushingId, RemotePhotoUiState.Idle)
                }
                removeUploadedStagingFile(candidate)
                if (bytes == null) {
                    "La fotografía se confirmó, pero la vista previa deberá descargarse nuevamente."
                } else {
                    null
                }
            }
            is PhotoResult.Failure -> {
                if (!isCurrentSessionResult(generation, sessionGeneration)) return null
                if (result.error == PhotoError.UNAUTHORIZED) {
                    expireRemoteSession()
                    null
                } else {
                    remotePhotoCache.invalidate(flushingId)
                    setRemotePhotoState(
                        flushingId,
                        RemotePhotoUiState.UploadError(candidate, result.error)
                    )
                    photoUploadPendingMessage(result.error)
                }
            }
        }
    }

    private suspend fun removeUploadedStagingFile(candidate: LocalPhotoCandidate) {
        val application = getApplication<Application>()
        if (!isOwnedFlushingPhotoFile(candidate.filePath, application.filesDir)) return
        withContext(Dispatchers.IO) {
            runCatching { File(candidate.filePath).delete() }
        }
    }

    private fun photoUploadPendingMessage(error: PhotoError): String =
        "El flushing quedó guardado, pero la fotografía está pendiente: ${photoErrorMessage(error)}"

    private fun handlePhotoLoadFailure(
        flushingId: Long,
        error: PhotoError,
        generation: Long
    ) {
        if (!isCurrentSessionResult(generation, sessionGeneration)) return
        when (error) {
            PhotoError.UNAUTHORIZED -> expireRemoteSession()
            PhotoError.NOT_FOUND -> {
                remotePhotoCache.invalidate(flushingId)
                setRemotePhotoState(flushingId, RemotePhotoUiState.NoPhoto)
            }
            else -> setRemotePhotoState(flushingId, RemotePhotoUiState.LoadError(error))
        }
    }

    private fun setRemotePhotoState(flushingId: Long, state: RemotePhotoUiState) {
        _remotePhotos.update { it + (flushingId to state) }
    }

    private fun remoteFlushing(flushingId: Long): RemoteFlushing? =
        _remoteFlushings.value.records.firstOrNull { it.id == flushingId }
            ?: _editingFlushing.value?.takeIf { it.id == flushingId }

    private fun failFlushingWrite(
        session: Long,
        message: String,
        error: FlushingError? = null
    ) {
        if (session != sessionGeneration) return
        if (error == FlushingError.UNAUTHORIZED) {
            expireRemoteSession()
            return
        }
        flushingSubmissionGate.unlock()
        _flushingSave.value = FlushingSaveUiState(errorMessage = message)
    }

    private fun failFlushingDelete(
        session: Long,
        message: String,
        error: FlushingError? = null
    ) {
        if (session != sessionGeneration) return
        if (error == FlushingError.UNAUTHORIZED) {
            expireRemoteSession()
            return
        }
        flushingSubmissionGate.unlock()
        _flushingDelete.value = FlushingDeleteUiState(errorMessage = message)
    }

    private fun publishFlushingListFailure(error: FlushingError) {
        if (error == FlushingError.UNAUTHORIZED) {
            expireRemoteSession()
        } else {
            _remoteFlushings.value = RemoteFlushingListUiState.Error(error)
        }
    }

    private suspend fun reloadRemoteFlushingAfterConflict(
        original: RemoteFlushing,
        generation: Long
    ) {
        when (val latest = flushingRepository.byId(original.id)) {
            is FlushingResult.Success -> {
                if (!isCurrentSessionResult(generation, sessionGeneration)) return
                if (!hasSameRemoteFlushingIdentity(original, latest.data)) {
                    failFlushingWrite(
                        generation,
                        "El backend devolvió campos inmutables diferentes al resolver el conflicto."
                    )
                    return
                }
                if (!canManageRemoteFlushing(_currentUser.value, latest.data)) {
                    failFlushingWrite(
                        generation,
                        "El flushing actualizado ya no pertenece a esta sesión."
                    )
                    return
                }
                _editingFlushing.value = latest.data
                upsertRemoteFlushing(latest.data)
                flushingSubmissionGate.unlock()
                _flushingSave.value = FlushingSaveUiState(
                    errorMessage = flushingErrorMessage(FlushingError.CONFLICT)
                )
            }
            is FlushingResult.Failure -> failFlushingWrite(
                generation,
                "Hubo un conflicto y no fue posible recargar la versión más reciente.",
                latest.error
            )
        }
    }

    private suspend fun refreshRemoteLineAfterFlushing(
        lineId: Long,
        generation: Long
    ): Boolean {
        return when (val result = catalogRepository.line(lineId)) {
            is CatalogResult.Success -> {
                if (!isCurrentSessionResult(generation, sessionGeneration)) return false
                val content = _catalog.value as? CatalogUiState.Content ?: return false
                val refreshedCatalog = content.catalog.withUpdatedLine(result.data) ?: return false
                _catalog.value = CatalogUiState.Content(refreshedCatalog)
                if (remoteSelection.lineId == lineId) {
                    _lineDetail.value = LineDetailUiState.Content(result.data)
                }
                true
            }
            is CatalogResult.Failure -> {
                if (result.error == CatalogError.UNAUTHORIZED &&
                    isCurrentSessionResult(generation, sessionGeneration)
                ) {
                    expireRemoteSession()
                }
                false
            }
        }
    }

    private fun upsertRemoteFlushing(record: RemoteFlushing) {
        val state = _remoteFlushings.value as? RemoteFlushingListUiState.Ready ?: return
        val belongsToQuery = remoteFlushingQueryIsGlobal || activeRemoteFlushingLineId == record.lineId
        if (!belongsToQuery) return
        val updated = (state.records.filterNot { it.id == record.id } + record)
            .sortedWith(compareByDescending<RemoteFlushing> { it.registeredAt }.thenByDescending { it.id })
        _remoteFlushings.value = RemoteFlushingListUiState.Ready(updated)
    }

    private fun removeRemoteFlushing(recordId: Long) {
        val state = _remoteFlushings.value as? RemoteFlushingListUiState.Ready ?: return
        _remoteFlushings.value = RemoteFlushingListUiState.Ready(
            state.records.filterNot { it.id == recordId }
        )
    }

    private suspend fun removeOwnedPhotoIfUnreferenced(photoUri: String?) {
        photoUri ?: return
        val application = getApplication<Application>()
        if (!isOwnedFlushingPhotoUri(photoUri, application.packageName)) return

        val referenceCount = try {
            flushingDao.countPhotoReferences(photoUri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        if (referenceCount != 0) return

        withContext(Dispatchers.IO) {
            runCatching {
                application.contentResolver.delete(Uri.parse(photoUri), null, null)
            }
        }
    }

    private suspend fun seedDemoFlushingsIfNeeded() {
        try {
            DEMO_SEED_MUTEX.withLock {
                val seedAlreadyCompleted = settingsStore.wereDemoFlushingsSeeded()
                if (seedAlreadyCompleted) return@withLock

                val storedRowCount = flushingDao.count()
                if (
                    shouldSeedDemoFlushings(
                        seedAlreadyCompleted = false,
                        databasePreviouslyExisted = databasePreviouslyExisted,
                        storedRowCount = storedRowCount
                    )
                ) {
                    flushingDao.insertAll(repository.initialFlushings.map { it.toEntity() })
                }

                // También marca instalaciones existentes para que un vaciado futuro sea definitivo.
                settingsStore.markDemoFlushingsSeeded()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // La aplicación puede continuar; al no marcar, un próximo arranque podrá reintentar.
        }
    }

    private companion object {
        const val DATABASE_NAME = "bebederos.db"
        const val TEMPERATURE_REFRESH_WARNING =
            "El CRUD se confirmó, pero no fue posible refrescar la temperatura efectiva."
        val DEMO_SEED_MUTEX = Mutex()
    }
}
