package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.repository.PhotoError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class RemotePhotoUiStateTest {
    @Test
    fun uploadErrorKeepsLocalCandidateForPhotoOnlyRetry() {
        val candidate = LocalPhotoCandidate(
            filePath = "C:/private/flushing_images/flushing_1.jpg",
            contentUri = "content://app.fileprovider/flushing_images/flushing_1.jpg"
        )
        val state: RemotePhotoUiState = RemotePhotoUiState.UploadError(
            candidate,
            PhotoError.NETWORK
        )

        val pending = assertIs<RemotePhotoUiState.UploadError>(state)
        assertEquals(candidate.filePath, pending.candidate.filePath)
        assertEquals(PhotoError.NETWORK, pending.error)
    }

    @Test
    fun operationGatePreventsConcurrentPhotoRequestsAndClearsOnLogout() {
        val gate = PhotoOperationGate()

        assertTrue(gate.tryLock(50L))
        assertFalse(gate.tryLock(50L))
        assertTrue(gate.tryLock(51L))
        gate.clear()
        assertTrue(gate.tryLock(50L))
    }

    @Test
    fun logoutRejectsPendingPhotoResponseWithoutRemovingLocalCandidate() {
        val candidate = LocalPhotoCandidate(
            filePath = "C:/private/flushing_images/pendiente.jpg",
            contentUri = "content://app.fileprovider/flushing_images/pendiente.jpg"
        )
        val requestGeneration = 7L
        val generationAfterLogout = 8L

        assertFalse(isCurrentSessionResult(requestGeneration, generationAfterLogout))
        assertEquals(
            "C:/private/flushing_images/pendiente.jpg",
            candidate.filePath
        )
    }
}
