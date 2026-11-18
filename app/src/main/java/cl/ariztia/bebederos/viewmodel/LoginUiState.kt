package cl.ariztia.bebederos.viewmodel

/** Estado temporal de campos, carga y errores del inicio de sesión. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val credentialsError: String? = null,
    val isLoading: Boolean = false
)

/** Errores producidos localmente antes de llamar al backend. */
internal data class LoginValidation(
    val email: String,
    val emailError: String? = null,
    val passwordError: String? = null
) {
    val isValid: Boolean
        get() = emailError == null && passwordError == null
}

/** Conserva la validación inmediata del formulario para no enviar solicitudes inválidas. */
internal fun validateLoginInput(email: String, password: String): LoginValidation {
    val normalizedEmail = email.trim()
    val emailError = when {
        normalizedEmail.isBlank() -> "Ingresa tu correo electrónico."
        normalizedEmail.length > MAX_LOGIN_EMAIL_LENGTH ->
            "El correo electrónico es demasiado largo."
        !LOGIN_EMAIL_PATTERN.matches(normalizedEmail) ->
            "Ingresa un correo electrónico válido."
        else -> null
    }
    val passwordError = when {
        password.isBlank() -> "Ingresa tu contraseña."
        password.length > MAX_LOGIN_PASSWORD_LENGTH -> "La contraseña es demasiado larga."
        else -> null
    }

    return LoginValidation(normalizedEmail, emailError, passwordError)
}

/** Impide iniciar otro login mientras el primero continúa en ejecución. */
internal class LoginAttemptController {
    private var running = false

    @Synchronized
    fun start(state: LoginUiState): LoginUiState? {
        if (running) return null
        running = true
        return state.copy(
            isLoading = true,
            emailError = null,
            passwordError = null,
            credentialsError = null
        )
    }

    @Synchronized
    fun finish() {
        running = false
    }
}

private const val MAX_LOGIN_EMAIL_LENGTH = 254
private const val MAX_LOGIN_PASSWORD_LENGTH = 128
private val LOGIN_EMAIL_PATTERN =
    Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")
