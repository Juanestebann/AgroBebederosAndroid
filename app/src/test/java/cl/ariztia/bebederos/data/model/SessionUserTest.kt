package cl.ariztia.bebederos.data.model

import kotlin.test.assertFalse
import org.junit.Test

class SessionUserTest {

    @Test
    fun sessionModelHasNoPasswordField() {
        val fieldNames = SessionUser::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse(fieldNames.any { it.contains("password") || it.contains("contrasena") })
    }
}
