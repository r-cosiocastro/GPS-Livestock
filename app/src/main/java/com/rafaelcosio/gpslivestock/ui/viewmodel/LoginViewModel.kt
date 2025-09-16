package com.rafaelcosio.gpslivestock.ui.viewmodel

import android.os.Build // <-- Importar Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaelcosio.gpslivestock.data.database.dao.UserDao
import com.rafaelcosio.gpslivestock.data.model.User
import com.rafaelcosio.gpslivestock.utils.PasswordUtils // <-- Importar PasswordUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define los posibles estados de la UI para el login
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _loginUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginUiState: StateFlow<LoginUiState> = _loginUiState

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState.Loading
            if (email.isBlank() || pass.isBlank()) {
                _loginUiState.value = LoginUiState.Error("Email y contraseña no pueden estar vacíos.")
                return@launch
            }

            try {
                val user = userDao.getUserByEmail(email)
                if (user != null) {
                    // Verificar la versión del SDK antes de usar PasswordUtils
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (PasswordUtils.verifyPassword(pass, user.salt, user.passwordHash)) {
                            _loginUiState.value = LoginUiState.Success(user)
                        } else {
                            _loginUiState.value = LoginUiState.Error("Email o contraseña incorrectos.")
                        }
                    } else {
                        // Manejar el caso para versiones de Android < Oreo (API 26)
                        // PasswordUtils.hashPassword y verifyPassword usan algoritmos de API 26+
                        // Para producción, necesitarías una estrategia de hash diferente o
                        // limitar tu minSdk a 26.
                        _loginUiState.value = LoginUiState.Error("Versión de Android no compatible para login seguro.")
                        // Alternativamente, podrías tener una lógica de hash más simple (y menos segura) como fallback,
                        // pero no es recomendable si la seguridad es una prioridad.
                    }
                } else {
                    _loginUiState.value = LoginUiState.Error("Usuario no encontrado.")
                }
            } catch (e: Exception) {
                // Loguear la excepción para depuración
                // Log.e("LoginViewModel", "Error durante el login", e)
                _loginUiState.value = LoginUiState.Error("Error durante el login: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    fun resetLoginState() {
        _loginUiState.value = LoginUiState.Idle
    }
}