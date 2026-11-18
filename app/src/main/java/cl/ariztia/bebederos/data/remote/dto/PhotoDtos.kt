package cl.ariztia.bebederos.data.remote.dto

data class EvidenciaResponseDto(
    val id: Long,
    val flushingId: Long,
    val nombreOriginal: String,
    val tipoContenido: String,
    val tamanoBytes: Long,
    val creadaEn: String
)
