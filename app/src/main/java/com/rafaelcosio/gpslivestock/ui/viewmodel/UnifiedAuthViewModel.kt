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
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class LoginSuccess(val user: User) : AuthUiState()
    object RegisterSuccess : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class UnifiedAuthViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading

            if (email.isBlank() || password.isBlank()) {
                _authUiState.value = AuthUiState.Error("Email y contraseña no pueden estar vacíos.")
                return@launch
            }

            try {
                val user = userDao.getUserByEmail(email)
                if (user != null) {
                    if (PasswordUtils.verifyPassword(password, user.salt, user.passwordHash)) {
                        _authUiState.value = AuthUiState.LoginSuccess(user)
                    } else {
                        _authUiState.value = AuthUiState.Error("Email o contraseña incorrectos.")
                    }
                } else {
                    _authUiState.value = AuthUiState.Error("Usuario no encontrado.")
                }
            } catch (e: Exception) {
                _authUiState.value = AuthUiState.Error("Error durante el login: ${e.localizedMessage ?: "Error desconocido"}")
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
            _authUiState.value = AuthUiState.Loading

            // Validaciones básicas
            if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                _authUiState.value = AuthUiState.Error("Email y contraseñas no pueden estar vacíos.")
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _authUiState.value = AuthUiState.Error("Formato de email inválido.")
                return@launch
            }
            if (password.length < 6) {
                _authUiState.value = AuthUiState.Error("La contraseña debe tener al menos 6 caracteres.")
                return@launch
            }
            if (password != confirmPassword) {
                _authUiState.value = AuthUiState.Error("Las contraseñas no coinciden.")
                return@launch
            }
            if (userType == UserType.RANCHER && ranchName.isNullOrBlank()) {
                _authUiState.value = AuthUiState.Error("El nombre del rancho es obligatorio para Ganaderos.")
                return@launch
            }

            try {
                // Verificar si el email ya existe
                val existingUser = userDao.getUserByEmail(email)
                if (existingUser != null) {
                    _authUiState.value = AuthUiState.Error("Este email ya está registrado.")
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
                _authUiState.value = AuthUiState.RegisterSuccess

            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                _authUiState.value = AuthUiState.Error("Este email ya está registrado.")
            } catch (e: Exception) {
                _authUiState.value = AuthUiState.Error("Error durante el registro: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    fun resetState() {
        _authUiState.value = AuthUiState.Idle
    }
}
