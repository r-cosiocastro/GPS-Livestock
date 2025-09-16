package com.rafaelcosio.gpslivestock.domain.usecase

import com.rafaelcosio.gpslivestock.data.model.FirebaseUserProfile
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthUseCase @Inject constructor(
    private val authRepository: FirebaseAuthRepository
) {

    /**
     * Flujo del estado de autenticación
     */
    val authStateFlow: Flow<com.google.firebase.auth.FirebaseUser?> = authRepository.authStateFlow

    /**
     * Usuario actual
     */
    val currentUser get() = authRepository.currentUser

    /**
     * Registra un nuevo usuario
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String? = null,
        userType: UserType = UserType.REGULAR_USER,
        ranchName: String? = null
    ): Result<FirebaseUserProfile> {
        return authRepository.signUpWithEmailAndPassword(
            email = email,
            password = password,
            displayName = displayName,
            userType = userType,
            ranchName = ranchName
        )
    }

    /**
     * Inicia sesión
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUserProfile> {
        return authRepository.signInWithEmailAndPassword(email, password)
    }

    /**
     * Cierra sesión
     */
    suspend fun signOut(): Result<Unit> {
        return authRepository.signOut()
    }

    /**
     * Obtiene el perfil del usuario
     */
    suspend fun getUserProfile(uid: String): FirebaseUserProfile? {
        return authRepository.getUserProfile(uid)
    }

    /**
     * Actualiza el perfil del usuario
     */
    suspend fun updateUserProfile(userProfile: FirebaseUserProfile): Result<Unit> {
        return authRepository.updateUserProfile(userProfile)
    }

    /**
     * Envía email de verificación
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return authRepository.sendEmailVerification()
    }

    /**
     * Envía email para restablecer contraseña
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return authRepository.sendPasswordResetEmail(email)
    }

    /**
     * Verifica si el email está verificado
     */
    suspend fun isEmailVerified(): Boolean {
        return authRepository.isEmailVerified()
    }

    /**
     * Elimina la cuenta del usuario
     */
    suspend fun deleteAccount(): Result<Unit> {
        return authRepository.deleteAccount()
    }

    /**
     * Valida formato de email
     */
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Valida fortaleza de contraseña
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 6 // Firebase requiere mínimo 6 caracteres
    }

    /**
     * Valida que las contraseñas coincidan
     */
    fun passwordsMatch(password: String, confirmPassword: String): Boolean {
        return password == confirmPassword
    }
}
