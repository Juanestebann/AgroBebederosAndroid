package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.RemotePhoto
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.PhotoApi
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Credentials
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PhotoRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PhotoRepository
    private lateinit var tempDirectory: File
    private lateinit var credentials: InMemoryCredentials

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        tempDirectory = Files.createTempDirectory("photo-repository-test").toFile()
        credentials = InMemoryCredentials().apply {
            save("operario@example.cl", "clave-segura")
        }
        val client = RetrofitProvider.createOkHttpClient(credentials)
        val retrofit = RetrofitProvider.createRetrofit(server.url("/").toString(), client)
        repository = PhotoRepository(retrofit.create(PhotoApi::class.java))
    }

    @After
    fun cleanUp() {
        server.shutdown()
        tempDirectory.deleteRecursively()
    }

    @Test
    fun preparesJpegAndPngFromTheirRealSignatures() {
        val jpeg = photoFile("captura.jpg", JPEG_BYTES)
        val png = photoFile("captura.png", PNG_BYTES)

        val preparedJpeg = assertIs<PhotoResult.Success<PreparedPhoto>>(
            repository.prepare(jpeg.absolutePath)
        ).data
        val preparedPng = assertIs<PhotoResult.Success<PreparedPhoto>>(
            repository.prepare(png.absolutePath)
        ).data

        assertEquals("image/jpeg", preparedJpeg.contentType)
        assertEquals("image/png", preparedPng.contentType)
        assertEquals(jpeg.length(), preparedJpeg.sizeBytes)
        assertEquals(png.length(), preparedPng.sizeBytes)
    }

    @Test
    fun rejectsMissingEmptyOversizedAndInvalidFilesBeforeNetwork() {
        val empty = photoFile("vacia.jpg", byteArrayOf())
        val invalid = photoFile("texto.jpg", "no es imagen".encodeToByteArray())
        val oversized = File(tempDirectory, "grande.jpg")
        RandomAccessFile(oversized, "rw").use {
            it.write(JPEG_BYTES)
            it.setLength(MAX_REMOTE_PHOTO_BYTES + 1L)
        }

        assertPrepareFailure(PhotoError.FILE_NOT_FOUND, File(tempDirectory, "ausente.jpg"))
        assertPrepareFailure(PhotoError.EMPTY_FILE, empty)
        assertPrepareFailure(PhotoError.TOO_LARGE, oversized)
        assertPrepareFailure(PhotoError.UNSUPPORTED_FORMAT, invalid)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun uploadUsesArchivoPartStreamsJpegAndRequires201() = runBlocking {
        val file = photoFile("evidencia.jpg", JPEG_BYTES)
        val prepared = successfulPreparation(file)
        server.enqueue(jsonResponse(photoJson("image/jpeg", file.length()), 201))

        val result = repository.upload(FLUSHING_ID, prepared)
        val request = server.takeRequest()
        val multipart = request.body.readUtf8()

        assertIs<PhotoResult.Success<RemotePhoto>>(result)
        assertEquals("POST", request.method)
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto", request.path)
        assertEquals(Credentials.basic("operario@example.cl", "clave-segura"), request.getHeader("Authorization"))
        assertTrue(multipart.contains("name=\"archivo\""))
        assertTrue(multipart.contains("filename=\"evidencia.jpg\""))
        assertTrue(multipart.contains("Content-Type: image/jpeg"))
    }

    @Test
    fun uploadAlsoAcceptsPngAndRejectsWrongSuccessCode() = runBlocking {
        val file = photoFile("evidencia.png", PNG_BYTES)
        val prepared = successfulPreparation(file)
        server.enqueue(jsonResponse(photoJson("image/png", file.length()), 201))
        server.enqueue(jsonResponse(photoJson("image/png", file.length()), 200))

        assertIs<PhotoResult.Success<RemotePhoto>>(repository.upload(FLUSHING_ID, prepared))
        assertFailure(PhotoError.UNEXPECTED, repository.upload(FLUSHING_ID, prepared))
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto", server.takeRequest().path)
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto", server.takeRequest().path)
    }

    @Test
    fun partialFailureRetryCallsOnlyPhotoEndpointWithSameRemoteId() = runBlocking {
        val file = photoFile("evidencia.jpg", JPEG_BYTES)
        val prepared = successfulPreparation(file)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(jsonResponse(photoJson("image/jpeg", file.length()), 201))

        assertFailure(PhotoError.UNEXPECTED, repository.upload(FLUSHING_ID, prepared))
        assertIs<PhotoResult.Success<RemotePhoto>>(repository.upload(FLUSHING_ID, prepared))

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto", first.path)
        assertEquals(first.path, second.path)
        assertTrue(first.path?.contains("/lineas/") == false)
        assertTrue(second.path?.contains("/lineas/") == false)
    }

    @Test
    fun metadataReturnsNullWhenPhotoIsAbsent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = repository.metadata(FLUSHING_ID)

        assertNull(assertIs<PhotoResult.Success<RemotePhoto?>>(result).data)
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto/metadata", server.takeRequest().path)
    }

    @Test
    fun metadataAndAuthenticatedDownloadReturnValidatedBytes() = runBlocking {
        server.enqueue(jsonResponse(photoJson("image/jpeg", JPEG_BYTES.size.toLong())))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(JPEG_BYTES))
        )

        val metadata = assertIs<PhotoResult.Success<RemotePhoto?>>(
            repository.metadata(FLUSHING_ID)
        ).data!!
        val content = assertIs<PhotoResult.Success<RemotePhotoContent>>(
            repository.download(FLUSHING_ID, metadata)
        ).data
        server.takeRequest()
        val downloadRequest = server.takeRequest()

        assertContentEquals(JPEG_BYTES, content.bytes)
        assertEquals("GET", downloadRequest.method)
        assertEquals("/api/v1/flushings/$FLUSHING_ID/foto", downloadRequest.path)
        assertEquals(Credentials.basic("operario@example.cl", "clave-segura"), downloadRequest.getHeader("Authorization"))
    }

    @Test
    fun downloadRejectsOversizedMetadataWithoutLoadingBody() = runBlocking {
        val metadata = remotePhoto(size = MAX_REMOTE_PHOTO_BYTES + 1L)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(JPEG_BYTES))
        )

        assertFailure(PhotoError.UNEXPECTED, repository.download(FLUSHING_ID, metadata))
    }

    @Test
    fun deleteRequires204AndMapsAccessErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(403))

        assertIs<PhotoResult.Success<Unit>>(repository.delete(FLUSHING_ID))
        assertFailure(PhotoError.FORBIDDEN, repository.delete(FLUSHING_ID))
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun networkFailureIsReportedWithoutThrowing() = runBlocking {
        val failing = PhotoRepository(NetworkFailureApi)

        assertFailure(PhotoError.NETWORK, failing.metadata(FLUSHING_ID))
    }

    @Test
    fun cacheSupportsReplacementInvalidationAndLogoutClear() {
        val cache = RemotePhotoCache()
        val first = RemotePhotoContent(remotePhoto(id = 1L), JPEG_BYTES)
        val replacement = RemotePhotoContent(remotePhoto(id = 2L), PNG_BYTES)

        cache.put(first)
        cache.put(replacement)
        assertEquals(1, cache.size())
        assertEquals(2L, cache.get(FLUSHING_ID)?.metadata?.id)

        cache.invalidate(FLUSHING_ID)
        assertNull(cache.get(FLUSHING_ID))
        cache.put(first)
        cache.clear()
        assertEquals(0, cache.size())
    }

    private fun assertPrepareFailure(error: PhotoError, file: File) {
        val result = assertIs<PhotoResult.Failure>(repository.prepare(file.absolutePath))
        assertEquals(error, result.error)
    }

    private fun successfulPreparation(file: File): PreparedPhoto =
        assertIs<PhotoResult.Success<PreparedPhoto>>(repository.prepare(file.absolutePath)).data

    private fun assertFailure(expected: PhotoError, result: PhotoResult<*>) {
        assertEquals(expected, assertIs<PhotoResult.Failure>(result).error)
    }

    private fun photoFile(name: String, bytes: ByteArray) = File(tempDirectory, name).apply {
        writeBytes(bytes)
    }

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun photoJson(contentType: String, size: Long) = """
        {
          "id": 91,
          "flushingId": $FLUSHING_ID,
          "nombreOriginal": "evidencia.${if (contentType == "image/png") "png" else "jpg"}",
          "tipoContenido": "$contentType",
          "tamanoBytes": $size,
          "creadaEn": "2026-11-07T20:05:00-03:00"
        }
    """.trimIndent()

    private fun remotePhoto(id: Long = 91L, size: Long = JPEG_BYTES.size.toLong()) = RemotePhoto(
        id = id,
        flushingId = FLUSHING_ID,
        originalName = "evidencia.jpg",
        contentType = "image/jpeg",
        sizeBytes = size,
        createdAt = OffsetDateTime.parse("2026-11-07T20:05:00-03:00")
    )

    private object NetworkFailureApi : PhotoApi {
        override suspend fun upload(
            flushingId: Long,
            archivo: MultipartBody.Part
        ): Response<cl.ariztia.bebederos.data.remote.dto.EvidenciaResponseDto> =
            throw IOException("Sin red")

        override suspend fun metadata(
            flushingId: Long
        ): Response<cl.ariztia.bebederos.data.remote.dto.EvidenciaResponseDto> =
            throw IOException("Sin red")

        override suspend fun download(flushingId: Long): Response<ResponseBody> =
            throw IOException("Sin red")

        override suspend fun delete(flushingId: Long): Response<Unit> =
            throw IOException("Sin red")
    }

    private companion object {
        const val FLUSHING_ID = 4_000_000_005L
        val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
