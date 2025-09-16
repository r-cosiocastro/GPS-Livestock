package com.rafaelcosio.gpslivestock.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.util.copy
import com.rafaelcosio.gpslivestock.data.model.FirebaseUserProfile
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase
) : ViewModel() {

    // Estados de UI
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUserProfile?>(null)
    val currentUser: StateFlow<FirebaseUserProfile?> = _currentUser.asStateFlow()

    init {
        // Observar cambios en el estado de autenticación
        viewModelScope.launch {
            authUseCase.authStateFlow.collect { firebaseUser ->
                if (firebaseUser != null) {
                    // Cargar perfil completo del usuario
                    val userProfile = authUseCase.getUserProfile(firebaseUser.uid)
                        ?: FirebaseUserProfile.fromFirebaseUser(firebaseUser)
                    _currentUser.value = userProfile
                } else {
                    _currentUser.value = null
                }
            }
        }
    }

    /**
     * Registra un nuevo usuario
     */
    fun signUp(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String? = null,
        userType: UserType = UserType.REGULAR_USER,
        ranchName: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Validaciones
            if (!authUseCase.isValidEmail(email)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Formato de email inválido"
                )
                return@launch
            }

            if (!authUseCase.isValidPassword(password)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "La contraseña debe tener al menos 6 caracteres"
                )
                return@launch
            }

            if (!authUseCase.passwordsMatch(password, confirmPassword)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Las contraseñas no coinciden"
                )
                return@launch
            }

            // Intentar registro
            val result = authUseCase.signUp(
                email = email,
                password = password,
                displayName = displayName,
                userType = userType,
                ranchName = ranchName
            )

            result.fold(
                onSuccess = { userProfile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignUpSuccess = true,
                        successMessage = "Cuenta creada exitosamente. Revisa tu email para verificar tu cuenta."
                    )
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = getErrorMessage(exception)
                    )
                }
            )
        }
    }

    /**
     * Inicia sesión
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Validaciones básicas
            if (!authUseCase.isValidEmail(email)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Formato de email inválido"
                )
                return@launch
            }

            if (password.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "La contraseña no puede estar vacía"
                )
                return@launch
            }

            // Intentar inicio de sesión
            val result = authUseCase.signIn(email, password)

            result.fold(
                onSuccess = { userProfile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignInSuccess = true
                    )
                    Log.d("AuthViewModel", "Inicio de sesión exitoso: " + userProfile.displayName)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = getErrorMessage(exception)
                    )
                }
            )
        }
    }

    /**
     * Cierra sesión
     */
    fun signOut() {
        viewModelScope.launch {
            authUseCase.signOut()
            _uiState.value = AuthUiState() // Reset UI state
        }
    }

    /**
     * Envía email de verificación
     */
    fun sendEmailVerification() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authUseCase.sendEmailVerification()
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Email de verificación enviado"
                    )
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = getErrorMessage(exception)
                    )
                }
            )
        }
    }

    /**
     * Envía email para restablecer contraseña
     */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            if (!authUseCase.isValidEmail(email)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Formato de email inválido"
                )
                return@launch
            }

            val result = authUseCase.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Email de restablecimiento enviado"
                    )
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = getErrorMessage(exception)
                    )
                }
            )
        }
    }

    /**
     * Limpia los mensajes de error y éxito
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Resetea los estados de éxito
     */
    fun resetSuccessStates() {
        _uiState.value = _uiState.value.copy(
            isSignInSuccess = false,
            isSignUpSuccess = false
        )
    }

    /**
     * Convierte excepciones de Firebase a mensajes legibles
     */
    private fun getErrorMessage(exception: Throwable): String {
        return when (exception.message) {
            "The email address is already in use by another account." ->
                "Este email ya está registrado"
            "The password is invalid or the user does not have a password." ->
                "Contraseña incorrecta"
            "There is no user record corresponding to this identifier. The user may have been deleted." ->
                "No existe una cuenta con este email"
            "The email address is badly formatted." ->
                "Formato de email inválido"
            "The given password is invalid. [ Password should be at least 6 characters ]" ->
                "La contraseña debe tener al menos 6 caracteres"
            "A network error (such as timeout, interrupted connection or unreachable host) has occurred." ->
                "Error de conexión. Verifica tu internet"
            else -> exception.message ?: "Error desconocido"
        }
    }
}

/**
 * Estado de la UI para autenticación
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignInSuccess: Boolean = false,
    val isSignUpSuccess: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
