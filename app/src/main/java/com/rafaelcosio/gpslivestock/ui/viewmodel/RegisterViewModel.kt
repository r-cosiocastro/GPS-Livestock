package com.rafaelcosio.gpslivestock.ui.viewmodel

import android.os.Build
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

// Define los posibles estados de la UI para el registro
sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Loading : RegisterUiState()
    object Success : RegisterUiState() // Simplemente indica éxito, sin datos extra por ahora
    data class Error(val message: String) : RegisterUiState()
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _registerUiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState

    fun registerUser(
        email: String,
        pass: String,
        confirmPass: String,
        displayName: String?,
        userType: UserType,
        ranchName: String?
    ) {
        viewModelScope.launch {
            _registerUiState.value = RegisterUiState.Loading

            // Validaciones básicas
            if (email.isBlank() || pass.isBlank() || confirmPass.isBlank()) {
                _registerUiState.value = RegisterUiState.Error("Email y contraseñas no pueden estar vacíos.")
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _registerUiState.value = RegisterUiState.Error("Formato de email inválido.")
                return@launch
            }
            if (pass.length < 6) { // Ejemplo de política de contraseña
                _registerUiState.value = RegisterUiState.Error("La contraseña debe tener al menos 6 caracteres.")
                return@launch
            }
            if (pass != confirmPass) {
                _registerUiState.value = RegisterUiState.Error("Las contraseñas no coinciden.")
                return@launch
            }
            if (userType == UserType.RANCHER && ranchName.isNullOrBlank()) {
                _registerUiState.value = RegisterUiState.Error("El nombre del rancho es obligatorio para Ganaderos.")
                return@launch
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                _registerUiState.value = RegisterUiState.Error("Versión de Android no compatible para registro seguro.")
                return@launch
            }

            try {
                // Verificar si el email ya existe
                val existingUser = userDao.getUserByEmail(email)
                if (existingUser != null) {
                    _registerUiState.value = RegisterUiState.Error("Este email ya está registrado.")
                    return@launch
                }

                val (salt, hashedPassword) = PasswordUtils.hashPassword(pass)

                val newUser = User(
                    email = email,
                    passwordHash = hashedPassword,
                    salt = salt,
                    userType = userType,
                    displayName = displayName?.takeIf { it.isNotBlank() },
                    ranchName = if (userType == UserType.RANCHER) ranchName else null
                )

                userDao.insertUser(newUser)
                _registerUiState.value = RegisterUiState.Success

            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Esto podría ocurrir si hay una violación de restricción única en el email a pesar del chequeo previo.
                // Es una doble verificación.
                 _registerUiState.value = RegisterUiState.Error("Este email ya está registrado (constraint).")
            }
            catch (e: Exception) {
                // Log.e("RegisterViewModel", "Error durante el registro", e)
                _registerUiState.value = RegisterUiState.Error("Error durante el registro: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    fun resetRegisterState() {
        _registerUiState.value = RegisterUiState.Idle
    }
}