package cl.ariztia.bebederos.data.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Roles disponibles en la sesión demostrativa local. */
enum class UserRole { OPERARIO, SUPERVISOR }

/** Clasificación térmica compartida por modelos, tarjetas, badges y gráficos. */
enum class TemperatureStatus { NORMAL, WARNING, CRITICAL }

/** Distingue una lectura del conjunto demo de una medición manual persistida. */
enum class TemperatureSource { DEMO, MANUAL_AFTER_FLUSHING }

/** Desde este valor una temperatura deja de ser normal. */
const val WARNING_TEMPERATURE_C = 23.0

/** Desde este valor una temperatura pasa a estado crítico. */
const val CRITICAL_TEMPERATURE_C = 27.0

/** Rango aceptado para mediciones manuales de temperatura del agua. */
const val MIN_WATER_TEMPERATURE_C = 0.0
const val MAX_WATER_TEMPERATURE_C = 50.0

/** Etiqueta en español que debe mostrarse en la interfaz en lugar del nombre del enum. */
val TemperatureStatus.displayLabel: String
    get() = when (this) {
        TemperatureStatus.NORMAL -> "Normal"
        TemperatureStatus.WARNING -> "Advertencia"
        TemperatureStatus.CRITICAL -> "Crítico"
    }

/** Usuario local de demostración; no representa una cuenta de un backend. */
data class DemoUser(
    val email: String,
    val password: String,
    val name: String,
    val role: UserRole,
    val farm: String
)

/** Usuario autenticado por el backend. La contraseña nunca forma parte de la sesión. */
data class SessionUser(
    val id: Long,
    val email: String,
    val name: String,
    val role: UserRole,
    val farmId: Long?,
    val farm: String?
)

/** Granja obtenida del backend; su identificador nunca se convierte a `Int`. */
data class RemoteFarm(
    val id: Long,
    val name: String
)

/** Galpón remoto con la relación de granja informada por Spring Boot. */
data class RemoteShed(
    val id: Long,
    val farmId: Long,
    val name: String,
    val farmName: String
)

/**
 * Línea remota cuya temperatura, estado, origen y fecha son autoritativos del backend.
 */
data class RemoteWaterLine(
    val id: Long,
    val farmId: Long,
    val farmName: String,
    val shedId: Long,
    val shedName: String,
    val name: String,
    val temperatureC: BigDecimal,
    val status: TemperatureStatus,
    val temperatureOrigin: String,
    val registeredAt: OffsetDateTime
)

/** Alerta térmica activa entregada por Spring Boot para el alcance de la sesión. */
data class RemoteAlert(
    val id: Long,
    val lineId: Long,
    val lineName: String,
    val shedId: Long,
    val shedName: String,
    val farmId: Long,
    val farmName: String,
    val temperatureC: BigDecimal,
    val status: TemperatureStatus,
    val generatedAt: OffsetDateTime
)

/** Jerarquía remota completa cargada para el alcance del usuario autenticado. */
data class RemoteCatalog(
    val farms: List<RemoteFarm>,
    val sheds: List<RemoteShed>,
    val lines: List<RemoteWaterLine>
) {
    fun farm(id: Long): RemoteFarm? = farms.firstOrNull { it.id == id }
    fun shed(id: Long): RemoteShed? = sheds.firstOrNull { it.id == id }
    fun line(id: Long): RemoteWaterLine? = lines.firstOrNull { it.id == id }
    fun shedsForFarm(farmId: Long): List<RemoteShed> = sheds.filter { it.farmId == farmId }
    fun linesForShed(shedId: Long): List<RemoteWaterLine> = lines.filter { it.shedId == shedId }
    fun linesForFarm(farmId: Long): List<RemoteWaterLine> = lines.filter { it.farmId == farmId }

    /** Sustituye una línea solo si el detalle conserva la jerarquía ya cargada. */
    fun withUpdatedLine(updated: RemoteWaterLine): RemoteCatalog? {
        val index = lines.indexOfFirst { it.id == updated.id }
        if (index < 0) return null
        val previous = lines[index]
        if (previous.farmId != updated.farmId || previous.shedId != updated.shedId) return null

        return copy(lines = lines.toMutableList().apply { set(index, updated) })
    }
}

/** Registro canónico de flushing recibido desde PostgreSQL mediante Spring Boot. */
data class RemoteFlushing(
    val id: Long,
    val lineId: Long,
    val lineName: String,
    val shedId: Long,
    val shedName: String,
    val farmId: Long,
    val farmName: String,
    val operatorId: Long,
    val operatorName: String,
    val registeredAt: OffsetDateTime,
    val reason: String,
    val observation: String?,
    val completed: Boolean,
    val beforeTemperatureC: BigDecimal,
    val afterTemperatureC: BigDecimal,
    val clientOperationId: UUID,
    val version: Long
)

/** Metadatos canónicos de una fotografía asociada a un flushing remoto. */
data class RemotePhoto(
    val id: Long,
    val flushingId: Long,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: OffsetDateTime
)

/** Granja del catálogo demostrativo y referencias declaradas a sus galpones. */
data class Farm(
    val id: Int,
    val name: String,
    val shedIds: List<Int>
)

/** Galpón asociado a una granja mediante [farmId]. */
data class Shed(
    val id: Int,
    val farmId: Int,
    val name: String,
    val lineIds: List<Int>
)

/**
 * Línea mostrada en consultas y dashboards.
 *
 * [temperatureSource] permite saber si la temperatura proviene del catálogo demo o de la
 * medición manual posterior del flushing más reciente. [status] siempre usa los umbrales
 * centrales de [temperatureStatusFor].
 */
data class WaterLine(
    val id: Int,
    val farmId: Int,
    val shedId: Int,
    val name: String,
    val temperature: Double,
    val measurementDate: String,
    val updatedAt: String,
    val temperatureSource: TemperatureSource = TemperatureSource.DEMO
) {
    val status: TemperatureStatus
        get() = temperatureStatusFor(temperature)
}

/** Punto histórico demostrativo de temperatura; no se obtiene de sensores en tiempo real. */
data class TemperatureRecord(
    val lineId: Int,
    val date: String,
    val time: String,
    val value: Double
) {
    val status: TemperatureStatus
        get() = temperatureStatusFor(value)
}

/** Alerta demostrativa cuya ubicación se valida antes de ofrecer navegación al detalle. */
data class Alert(
    val id: Int,
    val farmId: Int,
    val shedId: Int,
    val lineId: Int,
    val temperature: Double,
    val date: String,
    val time: String
) {
    val status: TemperatureStatus
        get() = temperatureStatusFor(temperature)
}

/**
 * Representación de dominio de un flushing almacenado localmente.
 *
 * [beforeTemperatureC] y [afterTemperatureC] son mediciones manuales incorporadas en la versión
 * 3 de Room. [temperatureAtRegistrationC] conserva únicamente el contexto automático heredado de
 * la versión 2: no equivale a una medición manual posterior ni actualiza la línea. Los tres campos
 * son anulables para poder leer registros creados por versiones anteriores sin inventar valores.
 * [photoUri] guarda una referencia al archivo local, no los bytes de la fotografía.
 */
data class FlushingRecord(
    val id: Long = 0,
    val farmId: Int,
    val shedId: Int,
    val lineId: Int,
    val operatorName: String,
    val date: String,
    val time: String,
    val reason: String,
    val observation: String,
    val photoUri: String? = null,
    val completed: Boolean = true,
    val temperatureAtRegistrationC: Double? = null,
    val beforeTemperatureC: Double? = null,
    val afterTemperatureC: Double? = null
) {
    val temperatureStatusAtRegistration: TemperatureStatus?
        get() = temperatureAtRegistrationC?.let(::temperatureStatusFor)

    val beforeTemperatureStatus: TemperatureStatus?
        get() = beforeTemperatureC?.let(::temperatureStatusFor)

    val afterTemperatureStatus: TemperatureStatus?
        get() = afterTemperatureC?.let(::temperatureStatusFor)
}

/**
 * Clasifica [value] con los únicos umbrales térmicos de la aplicación.
 *
 * @return normal bajo 23 °C, advertencia desde 23 °C y crítico desde 27 °C.
 */
fun temperatureStatusFor(value: Double): TemperatureStatus = when {
    value >= CRITICAL_TEMPERATURE_C -> TemperatureStatus.CRITICAL
    value >= WARNING_TEMPERATURE_C -> TemperatureStatus.WARNING
    else -> TemperatureStatus.NORMAL
}
