package cl.ariztia.bebederos.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class InMemoryCredentialsTest {

    @Test
    fun credentialsExistOnlyAfterSavingThem() {
        val credentials = InMemoryCredentials()

        assertFalse(credentials.hasCredentials())
        assertNull(credentials.snapshot())

        credentials.save("persona@example.cl", "clave-prueba")

        assertTrue(credentials.hasCredentials())
        assertEquals("persona@example.cl", credentials.snapshot()?.email)
        assertEquals("clave-prueba", credentials.snapshot()?.password)
    }

    @Test
    fun clearRemovesTheActiveCredentials() {
        val credentials = InMemoryCredentials()
        credentials.save("persona@example.cl", "clave-prueba")

        credentials.clear()

        assertFalse(credentials.hasCredentials())
        assertNull(credentials.snapshot())
    }

    @Test
    fun aNewSuccessfulLoginReplacesThePreviousCredentials() {
        val credentials = InMemoryCredentials()
        credentials.save("anterior@example.cl", "clave-anterior")

        credentials.save("actual@example.cl", "clave-actual")

        assertEquals("actual@example.cl", credentials.snapshot()?.email)
        assertEquals("clave-actual", credentials.snapshot()?.password)
    }
}
