package cl.ariztia.bebederos.ui.screens.supervisor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.ariztia.bebederos.data.model.*
import cl.ariztia.bebederos.ui.components.*
import cl.ariztia.bebederos.ui.navigation.OperatorBottomBar
import cl.ariztia.bebederos.ui.navigation.Routes
import cl.ariztia.bebederos.ui.navigation.SupervisorBottomBar
import cl.ariztia.bebederos.ui.theme.*
import cl.ariztia.bebederos.viewmodel.AppViewModel
import cl.ariztia.bebederos.viewmodel.CatalogUiState
import cl.ariztia.bebederos.viewmodel.FlushingEditPreparationUiState
import cl.ariztia.bebederos.viewmodel.RemoteFlushingListUiState
import cl.ariztia.bebederos.viewmodel.RemotePhotoUiState
import cl.ariztia.bebederos.viewmodel.catalogErrorMessage
import cl.ariztia.bebederos.viewmodel.catalogOrNull
import cl.ariztia.bebederos.viewmodel.flushingErrorMessage
import cl.ariztia.bebederos.viewmodel.photoErrorMessage
import cl.ariztia.bebederos.viewmodel.records

/** Dashboard global del supervisor con conteos derivados exclusivamente del catálogo remoto. */
@Composable
fun HomeSupervisorScreen(viewModel: AppViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.currentUser.collectAsState()
    val catalogState by viewModel.catalog.collectAsState()
    val ambientWeatherState by viewModel.ambientWeather.collectAsState()
    val remoteCatalog = catalogState.catalogOrNull
    val relatedFarms = remoteCatalog?.farms.orEmpty()
    val relatedSheds = remoteCatalog?.sheds.orEmpty()
    val relatedLines = remoteCatalog?.lines.orEmpty()
    val statusCounts = relatedLines
        .groupingBy { it.status }
        .eachCount()

    Scaffold(
        bottomBar = {
            SupervisorBottomBar(
                Routes.HomeSupervisor,
                onNavigate
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "Supervisor",
                    color = BrandRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    user?.name ?: "Supervisor",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Catálogo remoto · Todas las granjas",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (catalogState is CatalogUiState.Content) RemoteDataBanner()

            if (catalogState is CatalogUiState.Content) Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        SummaryTile(
                            relatedFarms.size.toString(),
                            "Granjas",
                            BrandRed,
                            BrandRedLight
                        )
                    }

                    Box(Modifier.weight(1f)) {
                        SummaryTile(
                            relatedSheds.size.toString(),
                            "Galpones",
                            Color(0xFF6A1B9A),
                            Color(0xFFEDE7F6)
                        )
                    }

                    Box(Modifier.weight(1f)) {
                        SummaryTile(
                            relatedLines.size.toString(),
                            "Líneas",
                            Normal,
                            NormalBg
                        )
                    }
                }

                SectionCard {
                    Text(
                        "Resumen de líneas",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            SummaryTile(
                                (statusCounts[TemperatureStatus.NORMAL] ?: 0).toString(),
                                "Normales",
                                Normal,
                                NormalBg
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            SummaryTile(
                                (statusCounts[TemperatureStatus.WARNING] ?: 0).toString(),
                                "Advertencia",
                                Warning,
                                WarningBg
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            SummaryTile(
                                (statusCounts[TemperatureStatus.CRITICAL] ?: 0).toString(),
                                "Críticas",
                                Critical,
                                CriticalBg
                            )
                        }
                    }
                }

                AmbientWeatherCard(
                    state = ambientWeatherState,
                    onRetry = viewModel::refreshAmbientWeather
                )

                SectionCard {
                    Text("Alertas térmicas", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Consulta las alertas activas informadas por Spring Boot para todas las granjas.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                SectionCard {
                    Text("Seguimiento remoto de flushing", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Consulta los registros autorizados por Spring Boot y almacenados en PostgreSQL.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onNavigate(Routes.FlushingTrack) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver seguimiento")
                    }
                }
            } else {
                when (val state = catalogState) {
                    CatalogUiState.Idle,
                    CatalogUiState.Loading -> RemoteStateMessage(
                        "Cargando catálogo remoto…",
                        loading = true
                    )
                    CatalogUiState.Empty -> RemoteStateMessage(
                        "El backend no devolvió granjas disponibles."
                    )
                    is CatalogUiState.Error -> RemoteStateMessage(
                        catalogErrorMessage(state.error),
                        isError = true,
                        onRetry = viewModel::retryCatalog
                    )
                    is CatalogUiState.Content -> Unit
                }
        }
    }
}
}

/** Entrada a la jerarquía de granjas; los conteos se derivan de relaciones validadas. */
@Composable
fun FarmsScreen(
    viewModel: AppViewModel,
    onNavigate: (String) -> Unit
) {
    val catalogState by viewModel.catalog.collectAsState()
    val remoteCatalog = catalogState.catalogOrNull
    val farms = remoteCatalog?.farms.orEmpty()

    Scaffold(
        topBar = {
            AppTopBar("Granjas")
        },
        bottomBar = {
            SupervisorBottomBar(
                Routes.Farms,
                onNavigate
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
        ) {
            if (catalogState is CatalogUiState.Content) RemoteDataBanner()

            if (catalogState is CatalogUiState.Content) LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                items(
                    farms.size
                ) { i ->
                    val farm =
                        farms[i]

                    val lines = remoteCatalog?.linesForFarm(farm.id).orEmpty()

                    val counts =
                        lines
                            .groupingBy {
                                it.status
                            }
                            .eachCount()

                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectRemoteFarm(
                                    farm.id
                                )

                                onNavigate(
                                    Routes.Sheds
                                )
                            },
                        shape =
                            RoundedCornerShape(
                                18.dp
                            ),
                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        Color.White
                                ),
                        border =
                            BorderStroke(
                                1.dp,
                                Border
                            )
                    ) {
                        Column(
                            Modifier.padding(
                                16.dp
                            )
                        ) {
                            Row(
                                verticalAlignment =
                                    Alignment
                                        .CenterVertically
                            ) {
                                Icon(
                                    Icons.Default
                                        .Agriculture,
                                    null,
                                    tint = BrandRed
                                )

                                Spacer(
                                    Modifier.width(
                                        10.dp
                                    )
                                )

                                Column(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    Text(
                                        farm.name,
                                        fontWeight =
                                            FontWeight
                                                .SemiBold
                                    )

                                    Text(
                                        "${remoteCatalog?.shedsForFarm(farm.id).orEmpty().size} galpones",
                                        fontSize =
                                            11.sp,
                                        color =
                                            TextSecondary
                                    )
                                }

                                Icon(
                                    Icons.Default
                                        .ChevronRight,
                                    null,
                                    tint =
                                        TextSecondary
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Row(
                                horizontalArrangement =
                                    Arrangement
                                        .spacedBy(
                                            8.dp
                                        )
                            ) {
                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.NORMAL
                                                ] ?: 0
                                                ).toString(),
                                        "Normales",
                                        Normal,
                                        NormalBg
                                    )
                                }

                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.WARNING
                                                ] ?: 0
                                                ).toString(),
                                        "Advertencia",
                                        Warning,
                                        WarningBg
                                    )
                                }

                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.CRITICAL
                                                ] ?: 0
                                                ).toString(),
                                        "Críticas",
                                        Critical,
                                        CriticalBg
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when (val state = catalogState) {
                    CatalogUiState.Idle,
                    CatalogUiState.Loading -> RemoteStateMessage(
                        "Cargando granjas remotas…",
                        loading = true
                    )
                    CatalogUiState.Empty -> RemoteStateMessage(
                        "No hay granjas disponibles."
                    )
                    is CatalogUiState.Error -> RemoteStateMessage(
                        catalogErrorMessage(state.error),
                        isError = true,
                        onRetry = viewModel::retryCatalog
                    )
                    is CatalogUiState.Content -> Unit
                }
            }
        }
    }
}

/** Galpones de la granja seleccionada, sin mezclar relaciones de otras granjas. */
@Composable
fun ShedsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val catalogState by viewModel.catalog.collectAsState()
    val remoteCatalog = catalogState.catalogOrNull
    val farm = viewModel.selectedRemoteFarm()
    val sheds = farm?.let { remoteCatalog?.shedsForFarm(it.id) }.orEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                "Galpones",
                farm?.name ?: "Granja no disponible",
                onBack
            )
        },
        bottomBar = {
            SupervisorBottomBar(
                Routes.Farms,
                onNavigate
            )
        }
    ) { inner ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
        ) {
            if (catalogState is CatalogUiState.Content) RemoteDataBanner()

            if (catalogState is CatalogUiState.Content && farm != null && sheds.isNotEmpty()) LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                items(sheds.size) { i ->

                    val shed =
                        sheds[i]

                    val lines = remoteCatalog?.linesForShed(shed.id).orEmpty()

                    val counts =
                        lines
                            .groupingBy {
                                it.status
                            }
                            .eachCount()

                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectRemoteShed(
                                    shed.id
                                )

                                onNavigate(
                                    Routes.Lines
                                )
                            },
                        shape =
                            RoundedCornerShape(
                                18.dp
                            ),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color.White
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                Border
                            )
                    ) {
                        Column(
                            Modifier.padding(
                                16.dp
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement
                                        .SpaceBetween
                            ) {
                                Text(
                                    shed.name,
                                    fontWeight =
                                        FontWeight
                                            .SemiBold
                                )

                                Text(
                                    "${lines.size} líneas",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {
                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.NORMAL
                                                ] ?: 0
                                                ).toString(),
                                        "Normal",
                                        Normal,
                                        NormalBg
                                    )
                                }

                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.WARNING
                                                ] ?: 0
                                                ).toString(),
                                        "Advert.",
                                        Warning,
                                        WarningBg
                                    )
                                }

                                Box(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    SummaryTile(
                                        (
                                                counts[
                                                    TemperatureStatus.CRITICAL
                                                ] ?: 0
                                                ).toString(),
                                        "Crítico",
                                        Critical,
                                        CriticalBg
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when (val state = catalogState) {
                    CatalogUiState.Idle,
                    CatalogUiState.Loading -> RemoteStateMessage(
                        "Cargando galpones remotos…",
                        loading = true
                    )
                    CatalogUiState.Empty -> RemoteStateMessage("No hay granjas disponibles.")
                    is CatalogUiState.Error -> RemoteStateMessage(
                        catalogErrorMessage(state.error),
                        isError = true,
                        onRetry = viewModel::retryCatalog
                    )
                    is CatalogUiState.Content -> RemoteStateMessage(
                        if (farm == null) {
                            "Selecciona una granja para consultar sus galpones."
                        } else {
                            "La granja seleccionada no tiene galpones."
                        }
                    )
                }
            }
        }
    }
}

/**
 * Historial de flushing de la línea seleccionada para cualquiera de los dos roles.
 *
 * La lista procede solo de `GET /lineas/{id}/flushings`. La barra inferior depende del rol,
 * mientras [onBack] conserva la jerarquía cuando existe un padre.
 */
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    supervisorMode: Boolean,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val flushingList by viewModel.remoteFlushings.collectAsState()
    val deleteState by viewModel.flushingDelete.collectAsState()
    val saveState by viewModel.flushingSave.collectAsState()
    val editPreparation by viewModel.flushingEditPreparation.collectAsState()
    val photoStates by viewModel.remotePhotos.collectAsState()
    val line = viewModel.selectedRemoteLine()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeletionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(line?.id) {
        line?.let { viewModel.loadRemoteFlushingsForLine(it.id) }
    }

    LaunchedEffect(deleteState.deletedRecordId) {
        deleteState.deletedRecordId?.let { deletedId ->
            pendingDeletionId = null
            val warning = deleteState.temperatureRefreshWarning
            viewModel.consumeFlushingDeleteSuccess(deletedId)
            snackbarHostState.showSnackbar(
                warning ?: "Flushing eliminado correctamente."
            )
        }
    }

    LaunchedEffect(saveState.updatedRecordId) {
        saveState.updatedRecordId?.let { updatedId ->
            val message = saveState.photoUploadWarning
                ?: saveState.temperatureRefreshWarning
                ?: "Flushing actualizado correctamente."
            viewModel.consumeFlushingUpdateSuccess(updatedId)
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(editPreparation) {
        when (val state = editPreparation) {
            is FlushingEditPreparationUiState.Ready -> {
                viewModel.consumeFlushingEditPreparation(state.recordId)
                onNavigate(Routes.FlushingEdit)
            }
            is FlushingEditPreparationUiState.Error -> {
                snackbarHostState.showSnackbar(flushingErrorMessage(state.error))
                viewModel.clearFlushingEditPreparation()
            }
            FlushingEditPreparationUiState.Idle,
            FlushingEditPreparationUiState.Loading -> Unit
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                "Historial de flushing",
                line?.let { "${it.name} · ${it.shedName}" } ?: "Línea no disponible",
                onBack = onBack
            )
        },
        bottomBar = {
            if (supervisorMode) {
                SupervisorBottomBar(Routes.History, onNavigate)
            } else {
                OperatorBottomBar(Routes.History, onNavigate)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard {
                    Text("Ubicación seleccionada", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(line?.farmName ?: "Granja no disponible", fontSize = 13.sp)
                    Text(
                        line?.let { "${it.shedName} · ${it.name}" }
                            ?: "Selecciona una línea del catálogo remoto.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            item { RemoteFlushingBanner() }

            if (editPreparation is FlushingEditPreparationUiState.Loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            when (val state = flushingList) {
                RemoteFlushingListUiState.Idle,
                RemoteFlushingListUiState.Loading -> item {
                    Column(
                        Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando historial de flushing…", color = TextSecondary)
                    }
                }
                is RemoteFlushingListUiState.Error -> item {
                    RemoteStateMessage(
                        flushingErrorMessage(state.error),
                        isError = true,
                        onRetry = { line?.let { viewModel.loadRemoteFlushingsForLine(it.id) } }
                    )
                }
                is RemoteFlushingListUiState.Ready -> {
                    if (state.records.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sin flushing registrados", color = TextSecondary)
                            }
                        }
                    } else {
                        items(state.records, key = { it.id }) { flushing ->
                            val canManage = !supervisorMode &&
                                    viewModel.canCurrentUserManageFlushing(flushing)
                            FlushingCard(
                                f = flushing,
                                showRegistrationDetails = true,
                                photoState = photoStates[flushing.id]
                                    ?: RemotePhotoUiState.Idle,
                                onLoadPhoto = {
                                    viewModel.loadRemotePhoto(flushing.id)
                                },
                                onRetryPhoto = {
                                    viewModel.retryRemotePhotoUpload(flushing.id)
                                },
                                onDeletePhoto = if (canManage) {
                                    { viewModel.deleteRemotePhoto(flushing.id) }
                                } else {
                                    null
                                },
                                onEdit = if (canManage) {
                                    { viewModel.prepareFlushingEdit(flushing.id) }
                                } else {
                                    null
                                },
                                onDelete = if (canManage) {
                                    {
                                        viewModel.clearFlushingDeleteState()
                                        pendingDeletionId = flushing.id
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeletionId?.let { recordId ->
        val record = flushingList.records.firstOrNull { it.id == recordId }
        if (record != null) {
            val isDeleting = deleteState.deletingRecordId == recordId
            AlertDialog(
                onDismissRequest = {
                    if (!isDeleting) {
                        pendingDeletionId = null
                        viewModel.clearFlushingDeleteState()
                    }
                },
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                title = { Text("Eliminar flushing") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Se eliminará permanentemente el registro del ${remoteFlushingTimestamp(record)}."
                        )
                        Text("Motivo: ${record.reason}")
                        Text(
                            "La eliminación puede cambiar la temperatura efectiva mostrada para esta línea.",
                            color = TextSecondary
                        )
                        deleteState.errorMessage?.let {
                            Text(it, color = Critical, fontSize = 12.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDeletionId = null
                            viewModel.clearFlushingDeleteState()
                        },
                        enabled = !isDeleting
                    ) {
                        Text("Cancelar")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteFlushing(recordId) },
                        enabled = !isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Critical)
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Eliminar")
                        }
                    }
                }
            )
        } else if (deleteState.deletedRecordId == null && !deleteState.isDeleting) {
            LaunchedEffect(recordId) {
                pendingDeletionId = null
                viewModel.clearFlushingDeleteState()
                snackbarHostState.showSnackbar("El flushing ya no está disponible.")
            }
        }
    }
}

/** Seguimiento supervisor: distingue carga, error, lista vacía y registros disponibles. */
@Composable
fun FlushingTrackScreen(
    viewModel: AppViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val flushingList by viewModel.remoteFlushings.collectAsState()
    val photoStates by viewModel.remotePhotos.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAllRemoteFlushings()
    }

    Scaffold(
        topBar = {
            AppTopBar("Seguimiento de flushing", onBack = onBack)
        },
        bottomBar = {
            SupervisorBottomBar(
                Routes.HomeSupervisor,
                onNavigate
            )
        }
    ) { inner ->

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RemoteFlushingBanner()
            }

            when (val state = flushingList) {
                RemoteFlushingListUiState.Idle,
                RemoteFlushingListUiState.Loading -> item {
                    Column(
                        Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando registros de flushing…", color = TextSecondary)
                    }
                }
                is RemoteFlushingListUiState.Error -> item {
                    RemoteStateMessage(
                        flushingErrorMessage(state.error),
                        isError = true,
                        onRetry = viewModel::loadAllRemoteFlushings
                    )
                }
                is RemoteFlushingListUiState.Ready -> {
                    if (state.records.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay registros de flushing guardados.",
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(state.records, key = { it.id }) { flushing ->
                            FlushingCard(
                                f = flushing,
                                showRegistrationDetails = true,
                                photoState = photoStates[flushing.id]
                                    ?: RemotePhotoUiState.Idle,
                                onLoadPhoto = {
                                    viewModel.loadRemotePhoto(flushing.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * Tarjeta reutilizada por dashboard, historial y seguimiento.
 *
 * Presenta únicamente los campos canónicos devueltos por Spring Boot.
 */
@Composable
private fun FlushingCard(
    f: RemoteFlushing,
    showRegistrationDetails: Boolean = false,
    photoState: RemotePhotoUiState = RemotePhotoUiState.Idle,
    onLoadPhoto: (() -> Unit)? = null,
    onRetryPhoto: (() -> Unit)? = null,
    onDeletePhoto: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            Border
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    if (f.completed) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    null,
                    tint =
                        if (f.completed) {
                            Normal
                        } else {
                            Critical
                        }
                )

                Spacer(
                    Modifier.width(8.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        "${f.lineName} · ${f.shedName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Text(
                        f.farmName,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    if (f.completed) {
                        "Completado"
                    } else {
                        "Pendiente"
                    },
                    color =
                        if (f.completed) {
                            Normal
                        } else {
                            Critical
                        },
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                "Operario: ${f.operatorName}",
                fontSize = 12.sp
            )

            Text(
                remoteFlushingTimestamp(f),
                fontSize = 12.sp,
                color = TextSecondary
            )

            Text(
                "Motivo: ${f.reason}",
                fontSize = 12.sp,
                color = TextSecondary
            )

            if (!f.observation.isNullOrBlank()) {
                Text(
                    "Observación: ${f.observation}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else if (showRegistrationDetails) {
                Text(
                    "Observación: Sin observaciones",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (showRegistrationDetails) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Temperaturas del flushing",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                RemoteHistoricalTemperatureRow(
                    label = "Antes del flushing",
                    temperature = f.beforeTemperatureC
                )
                RemoteHistoricalTemperatureRow(
                    label = "Después del flushing",
                    temperature = f.afterTemperatureC
                )
            } else {
                Spacer(Modifier.height(6.dp))
                CompactFlushingTemperatureSummary(f)
            }

            if (showRegistrationDetails) {
                RemotePhotoSection(
                    flushingId = f.id,
                    state = photoState,
                    onLoad = onLoadPhoto,
                    onRetryUpload = onRetryPhoto,
                    onDelete = onDeletePhoto,
                    canReplace = onEdit != null
                )
                Text(
                    "Versión ${f.version}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            if (onEdit != null || onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onEdit?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Editar")
                        }
                    }
                    onDelete?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Critical)
                            Spacer(Modifier.width(6.dp))
                            Text("Eliminar", color = Critical)
                        }
                    }
                }
            }
        }
    }
}

/** Muestra una única descarga por ID y diferencia ausencia, espera, error y contenido remoto. */
@Composable
private fun RemotePhotoSection(
    flushingId: Long,
    state: RemotePhotoUiState,
    onLoad: (() -> Unit)?,
    onRetryUpload: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    canReplace: Boolean
) {
    var confirmDelete by remember(flushingId) { mutableStateOf(false) }

    LaunchedEffect(flushingId) {
        onLoad?.invoke()
    }

    Spacer(Modifier.height(8.dp))
    Text("Evidencia fotográfica", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    when (state) {
        RemotePhotoUiState.Idle,
        RemotePhotoUiState.LoadingMetadata -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Consultando fotografía…", fontSize = 11.sp, color = TextSecondary)
        }
        RemotePhotoUiState.NoPhoto -> Text(
            "Este flushing no tiene fotografía.",
            fontSize = 11.sp,
            color = TextSecondary
        )
        is RemotePhotoUiState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Descargando fotografía…", fontSize = 11.sp, color = TextSecondary)
        }
        is RemotePhotoUiState.Available -> {
            RemotePhotoContent(
                state = state,
                canReplace = canReplace,
                canDelete = onDelete != null,
                onRequestDelete = { confirmDelete = true }
            )
        }
        is RemotePhotoUiState.Uploading -> {
            FlushingPhoto(
                uri = state.candidate.contentUri,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
            Text("Subiendo fotografía…", fontSize = 11.sp, color = TextSecondary)
        }
        is RemotePhotoUiState.UploadError -> {
            FlushingPhoto(
                uri = state.candidate.contentUri,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
            Text(
                "Pendiente de subir: ${photoErrorMessage(state.error)}",
                fontSize = 11.sp,
                color = Warning
            )
            onRetryUpload?.let {
                TextButton(onClick = it) { Text("Reintentar solo fotografía") }
            }
        }
        is RemotePhotoUiState.LoadError -> {
            Text(photoErrorMessage(state.error), fontSize = 11.sp, color = Critical)
            TextButton(onClick = { onLoad?.invoke() }) { Text("Reintentar descarga") }
        }
        is RemotePhotoUiState.Deleting -> {
            RemoteFlushingPhoto(
                bytes = state.content.bytes,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Eliminando fotografía…", fontSize = 11.sp, color = TextSecondary)
        }
        is RemotePhotoUiState.DeleteError -> {
            RemoteFlushingPhoto(
                bytes = state.content.bytes,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
            Text(photoErrorMessage(state.error), fontSize = 11.sp, color = Critical)
            onDelete?.let {
                TextButton(onClick = { confirmDelete = true }) { Text("Reintentar eliminación") }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar fotografía") },
            text = { Text("Se eliminará la evidencia remota, pero se conservará el flushing.") },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Critical)
                ) { Text("Eliminar") }
            }
        )
    }
}

@Composable
private fun RemotePhotoContent(
    state: RemotePhotoUiState.Available,
    canReplace: Boolean,
    canDelete: Boolean,
    onRequestDelete: () -> Unit
) {
    RemoteFlushingPhoto(
        bytes = state.content.bytes,
        modifier = Modifier.fillMaxWidth().height(160.dp)
    )
    Text(
        "${state.content.metadata.originalName} · ${state.content.metadata.sizeBytes / 1024} KB",
        fontSize = 11.sp,
        color = TextSecondary
    )
    if (canReplace) {
        Text(
            "Para reemplazarla, usa Editar y toma una nueva fotografía.",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
    if (canDelete) {
        TextButton(onClick = onRequestDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Critical)
            Spacer(Modifier.width(6.dp))
            Text("Eliminar fotografía", color = Critical)
        }
    }
}

@Composable
private fun CompactFlushingTemperatureSummary(flushing: RemoteFlushing) {
    val before = remoteDecimalText(flushing.beforeTemperatureC)
    val after = remoteDecimalText(flushing.afterTemperatureC)
    val text = "Antes: $before °C → Después: $after °C"

    Text(
        text,
        fontSize = 11.sp,
        color = TextSecondary
    )
}

@Composable
private fun RemoteHistoricalTemperatureRow(label: String, temperature: java.math.BigDecimal) {
    val value = temperature.toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label: ${remoteDecimalText(temperature)} °C",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        StatusBadge(temperatureStatusFor(value))
    }
}
/** Alertas demo globales; la acción de detalle solo existe para una ubicación completa válida. */
@Composable
fun AlertsSupervisorScreen(
    viewModel: AppViewModel,
    onNavigate: (String) -> Unit
) {
    val offline by viewModel.offline.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar("Alertas demostrativas")
        },
        bottomBar = {
            SupervisorBottomBar(
                Routes.AlertsSupervisor,
                onNavigate
            )
        }
    ) { inner ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
        ) {

            LocalDataBanner(offline, viewModel.lines.firstOrNull()?.measurementDate)

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(viewModel.alerts.size) { i ->

                    val alert = viewModel.alerts[i]

                    val location = viewModel.resolveAlertLocation(alert)
                    val (color, bg) = statusColors(alert.status)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = bg
                        ),
                        border = BorderStroke(
                            1.dp,
                            color
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp)
                        ) {

                            Row {

                                Column(
                                    Modifier.weight(1f)
                                ) {

                                    Text(
                                        location?.let { "${it.line.name} · ${it.shed.name}" }
                                            ?: "Ubicación no disponible",
                                        color = color,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        location?.let { "${it.farm.name} · ${demoAlertTimestamp(alert.date, alert.time)}" }
                                            ?: "Referencia: granja ${alert.farmId} · galpón ${alert.shedId} · línea ${alert.lineId}",
                                        fontSize = 11.sp,
                                        color = if (location == null) Critical else TextSecondary
                                    )
                                }

                                Text(
                                    "${alert.temperature}°C",
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }

                            if (location != null) {
                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (viewModel.selectAlertLocation(alert)) {
                                            onNavigate(Routes.LineDetail)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Ver detalle")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
