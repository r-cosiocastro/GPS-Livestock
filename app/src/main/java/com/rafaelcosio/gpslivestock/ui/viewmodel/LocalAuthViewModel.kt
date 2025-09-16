package com.rafaelcosio.gpslivestock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaelcosio.gpslivestock.data.database.dao.UserDao
import com.rafaelcosio.gpslivestock.data.model.User
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.utils.PasswordUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Estados unificados para la autenticación
sealed class LocalAuthUiState {
    object Idle : LocalAuthUiState()
    object Loading : LocalAuthUiState()
    data class LoginSuccess(val user: User) : LocalAuthUiState()
    object RegisterSuccess : LocalAuthUiState()
    data class Error(val message: String) : LocalAuthUiState()
}

@HiltViewModel
class UnifiedAuthViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _authUiState = MutableStateFlow<LocalAuthUiState>(LocalAuthUiState.Idle)
    val authUiState: StateFlow<LocalAuthUiState> = _authUiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authUiState.value = LocalAuthUiState.Loading

            if (email.isBlank() || password.isBlank()) {
                _authUiState.value = LocalAuthUiState.Error("Email y contraseña no pueden estar vacíos.")
                return@launch
            }

            try {
                val user = userDao.getUserByEmail(email)
                if (user != null) {
                    if (PasswordUtils.verifyPassword(password, user.salt, user.passwordHash)) {
                        _authUiState.value = LocalAuthUiState.LoginSuccess(user)
                    } else {
                        _authUiState.value = LocalAuthUiState.Error("Email o contraseña incorrectos.")
                    }
                } else {
                    _authUiState.value = LocalAuthUiState.Error("Usuario no encontrado.")
                }
            } catch (e: Exception) {
                _authUiState.value = LocalAuthUiState.Error("Error durante el login: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    fun register(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String?,
        userType: UserType,
        ranchName: String?
    ) {
        viewModelScope.launch {
            _authUiState.value = LocalAuthUiState.Loading

            // Validaciones básicas
            if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                _authUiState.value = LocalAuthUiState.Error("Email y contraseñas no pueden estar vacíos.")
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _authUiState.value = LocalAuthUiState.Error("Formato de email inválido.")
                return@launch
            }
            if (password.length < 6) {
                _authUiState.value = LocalAuthUiState.Error("La contraseña debe tener al menos 6 caracteres.")
                return@launch
            }
            if (password != confirmPassword) {
                _authUiState.value = LocalAuthUiState.Error("Las contraseñas no coinciden.")
                return@launch
            }
            if (userType == UserType.RANCHER && ranchName.isNullOrBlank()) {
                _authUiState.value = LocalAuthUiState.Error("El nombre del rancho es obligatorio para Ganaderos.")
                return@launch
            }

            try {
                // Verificar si el email ya existe
                val existingUser = userDao.getUserByEmail(email)
                if (existingUser != null) {
                    _authUiState.value = LocalAuthUiState.Error("Este email ya está registrado.")
                    return@launch
                }

                val (salt, hashedPassword) = PasswordUtils.hashPassword(password)

                val newUser = User(
                    email = email,
                    passwordHash = hashedPassword,
                    salt = salt,
                    userType = userType,
                    displayName = displayName?.takeIf { it.isNotBlank() },
                    ranchName = if (userType == UserType.RANCHER) ranchName else null
                )

                userDao.insertUser(newUser)
                _authUiState.value = LocalAuthUiState.RegisterSuccess

            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                _authUiState.value = LocalAuthUiState.Error("Este email ya está registrado.")
            } catch (e: Exception) {
                _authUiState.value = LocalAuthUiState.Error("Error durante el registro: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    fun resetState() {
        _authUiState.value = LocalAuthUiState.Idle
    }
}
