package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.RemotePhoto
import cl.ariztia.bebederos.data.remote.api.PhotoApi
import cl.ariztia.bebederos.data.remote.mapper.toRemotePhoto
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Response

const val MAX_REMOTE_PHOTO_BYTES = 5L * 1024L * 1024L

enum class PhotoError {
    FILE_NOT_FOUND,
    EMPTY_FILE,
    TOO_LARGE,
    UNSUPPORTED_FORMAT,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    NETWORK,
    UNEXPECTED
}

sealed interface PhotoResult<out T> {
    data class Success<T>(val data: T) : PhotoResult<T>
    data class Failure(val error: PhotoError) : PhotoResult<Nothing>
}

/** Archivo local ya validado. OkHttp lo transmite como stream, no como un arreglo en memoria. */
data class PreparedPhoto(
    val file: File,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long
)

/** Bytes remotos limitados a 5 MiB junto con sus metadatos autoritativos. */
data class RemotePhotoContent(
    val metadata: RemotePhoto,
    val bytes: ByteArray
)

/** Caché efímera por sesión; nunca reemplaza al backend como fuente autoritativa. */
class RemotePhotoCache {
    private val entries = mutableMapOf<Long, RemotePhotoContent>()

    @Synchronized
    fun get(flushingId: Long): RemotePhotoContent? = entries[flushingId]

    @Synchronized
    fun put(content: RemotePhotoContent) {
        entries[content.metadata.flushingId] = content
    }

    @Synchronized
    fun invalidate(flushingId: Long) {
        entries.remove(flushingId)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}

/** Acceso convencional a los cuatro endpoints fotográficos documentados. */
class PhotoRepository(
    private val api: PhotoApi
) {
    fun prepare(filePath: String): PhotoResult<PreparedPhoto> {
        val file = File(filePath)
        if (!file.isFile) return PhotoResult.Failure(PhotoError.FILE_NOT_FOUND)

        val size = file.length()
        if (size == 0L) return PhotoResult.Failure(PhotoError.EMPTY_FILE)
        if (size > MAX_REMOTE_PHOTO_BYTES) return PhotoResult.Failure(PhotoError.TOO_LARGE)

        val contentType = try {
            detectContentType(file)
        } catch (_: IOException) {
            return PhotoResult.Failure(PhotoError.FILE_NOT_FOUND)
        } ?: return PhotoResult.Failure(PhotoError.UNSUPPORTED_FORMAT)

        return PhotoResult.Success(
            PreparedPhoto(
                file = file,
                originalName = file.name.ifBlank {
                    if (contentType == JPEG_CONTENT_TYPE) "fotografia.jpg" else "fotografia.png"
                },
                contentType = contentType,
                sizeBytes = size
            )
        )
    }

    suspend fun upload(
        flushingId: Long,
        photo: PreparedPhoto
    ): PhotoResult<RemotePhoto> = execute {
        require(flushingId > 0) { "El flushing remoto no es válido." }
        val body = photo.file.asRequestBody(photo.contentType.toMediaType())
        val part = MultipartBody.Part.createFormData("archivo", photo.originalName, body)
        val metadata = responseBody(api.upload(flushingId, part), expectedCode = 201)
            .toRemotePhoto()
        require(metadata.flushingId == flushingId) {
            "El backend asoció la fotografía a otro flushing."
        }
        require(metadata.contentType == photo.contentType) {
            "El backend devolvió otro tipo de contenido."
        }
        require(metadata.sizeBytes == photo.sizeBytes) {
            "El backend devolvió otro tamaño de fotografía."
        }
        metadata
    }

    /** Un 404 significa correctamente que el flushing no tiene fotografía. */
    suspend fun metadata(flushingId: Long): PhotoResult<RemotePhoto?> = try {
        val response = api.metadata(flushingId)
        when (response.code()) {
            200 -> {
                val metadata = response.body()?.toRemotePhoto()
                    ?: throw IllegalStateException("Respuesta HTTP sin cuerpo.")
                require(metadata.flushingId == flushingId) {
                    "El backend devolvió metadatos de otro flushing."
                }
                PhotoResult.Success(metadata)
            }
            404 -> PhotoResult.Success(null)
            else -> PhotoResult.Failure(errorForStatus(response.code()))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        PhotoResult.Failure(PhotoError.NETWORK)
    } catch (_: Exception) {
        PhotoResult.Failure(PhotoError.UNEXPECTED)
    }

    suspend fun download(
        flushingId: Long,
        metadata: RemotePhoto
    ): PhotoResult<RemotePhotoContent> = execute {
        require(metadata.flushingId == flushingId) { "Los metadatos no corresponden al flushing." }
        val response = api.download(flushingId)
        if (response.code() != 200) throw UnexpectedHttpCode(response.code())
        val body = response.body() ?: throw IllegalStateException("Respuesta HTTP sin cuerpo.")
        validateDownloadHeaders(body, metadata)
        val bytes = withContext(Dispatchers.IO) {
            body.use(::readLimitedBytes)
        }
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_REMOTE_PHOTO_BYTES) {
            "La descarga fotográfica tiene un tamaño inválido."
        }
        require(bytes.size.toLong() == metadata.sizeBytes) {
            "La descarga no coincide con el tamaño informado."
        }
        require(contentTypeForHeader(bytes) == metadata.contentType) {
            "La firma descargada no coincide con los metadatos."
        }
        RemotePhotoContent(metadata, bytes)
    }

    suspend fun delete(flushingId: Long): PhotoResult<Unit> = execute {
        val response = api.delete(flushingId)
        if (response.code() != 204) throw UnexpectedHttpCode(response.code())
    }

    private fun validateDownloadHeaders(body: ResponseBody, metadata: RemotePhoto) {
        require(metadata.contentType == JPEG_CONTENT_TYPE || metadata.contentType == PNG_CONTENT_TYPE)
        require(metadata.sizeBytes in 1..MAX_REMOTE_PHOTO_BYTES)
        val responseType = body.contentType()?.toString()?.substringBefore(';')
        require(responseType == metadata.contentType) { "El MIME descargado no coincide." }
        val responseLength = body.contentLength()
        require(responseLength == -1L || responseLength in 1..MAX_REMOTE_PHOTO_BYTES)
        require(responseLength == -1L || responseLength == metadata.sizeBytes)
    }

    /** Lee por bloques y corta al superar el límite, incluso si el servidor omite Content-Length. */
    private fun readLimitedBytes(body: ResponseBody): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (output.size().toLong() + read > MAX_REMOTE_PHOTO_BYTES) {
                    throw IllegalArgumentException("La descarga supera el máximo de 5 MB.")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun <T> responseBody(response: Response<T>, expectedCode: Int): T {
        if (response.code() != expectedCode) throw UnexpectedHttpCode(response.code())
        return response.body() ?: throw IllegalStateException("Respuesta HTTP sin cuerpo.")
    }

    private suspend fun <T> execute(block: suspend () -> T): PhotoResult<T> = try {
        PhotoResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: UnexpectedHttpCode) {
        PhotoResult.Failure(errorForStatus(error.code))
    } catch (error: HttpException) {
        PhotoResult.Failure(errorForStatus(error.code()))
    } catch (_: IOException) {
        PhotoResult.Failure(PhotoError.NETWORK)
    } catch (_: Exception) {
        PhotoResult.Failure(PhotoError.UNEXPECTED)
    }

    private fun errorForStatus(status: Int): PhotoError = when (status) {
        400 -> PhotoError.BAD_REQUEST
        401 -> PhotoError.UNAUTHORIZED
        403 -> PhotoError.FORBIDDEN
        404 -> PhotoError.NOT_FOUND
        else -> PhotoError.UNEXPECTED
    }

    private fun detectContentType(file: File): String? {
        val header = ByteArray(8)
        val read = FileInputStream(file).use { it.read(header) }
        return contentTypeForHeader(header, read)
    }

    private fun contentTypeForHeader(bytes: ByteArray): String? =
        contentTypeForHeader(bytes, bytes.size)

    private fun contentTypeForHeader(bytes: ByteArray, read: Int): String? = when {
        read >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> JPEG_CONTENT_TYPE
        read >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte() -> PNG_CONTENT_TYPE
        else -> null
    }

    private class UnexpectedHttpCode(val code: Int) : RuntimeException()

    private companion object {
        const val JPEG_CONTENT_TYPE = "image/jpeg"
        const val PNG_CONTENT_TYPE = "image/png"
    }
}
