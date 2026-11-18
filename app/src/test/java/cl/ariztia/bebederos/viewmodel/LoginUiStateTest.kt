package cl.ariztia.bebederos.viewmodel

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class LoginUiStateTest {

    @Test
    fun validCredentialsPreserveLocalValidationAndNormalizeEmail() {
        val validation = validateLoginInput("  persona@example.cl  ", "clave-prueba")

        assertTrue(validation.isValid)
        assertEquals("persona@example.cl", validation.email)
        assertNull(validation.emailError)
        assertNull(validation.passwordError)
    }

    @Test
    fun invalidFieldsAreRejectedBeforeCallingTheBackend() {
        val validation = validateLoginInput("correo-invalido", "")

        assertFalse(validation.isValid)
        assertNotNull(validation.emailError)
        assertNotNull(validation.passwordError)
    }

    @Test
    fun loadingStatePreventsASecondSimultaneousSubmission() {
        val controller = LoginAttemptController()
        val initial = LoginUiState(
            email = "persona@example.cl",
            password = "clave-prueba"
        )

        val loading = controller.start(initial)
        val duplicate = controller.start(initial)

        assertNotNull(loading)
        assertTrue(loading.isLoading)
        assertNull(duplicate)

        controller.finish()
        assertNotNull(controller.start(initial))
    }
}
