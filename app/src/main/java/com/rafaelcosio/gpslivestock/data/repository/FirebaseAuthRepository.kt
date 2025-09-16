package com.rafaelcosio.gpslivestock.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.rafaelcosio.gpslivestock.data.model.FirebaseUserProfile
import com.rafaelcosio.gpslivestock.data.model.UserType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val TAG = "FirebaseAuthRepository"
    }

    /**

     * Obtiene el usuario actual de Firebase Auth
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Flow que observa el estado de autenticación
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(authStateListener)

        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    /**
     * Registra un nuevo usuario con email y contraseña
     */
    /**
     * Verifica conectividad de Firebase antes de realizar operaciones
     */
    private suspend fun waitForFirebaseConnection(): Boolean {
        return try {
            withTimeout(5_000L) { // 5 segundos para verificar conectividad
                // Intentar una operación simple para verificar conectividad
                firestore.collection("_connection_test").limit(1).get().await()
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase no está conectado: ${e.message}")
            false
        }
    }

    suspend fun signUpWithEmailAndPassword(
        email: String,
        password: String,
        displayName: String? = null,
        userType: UserType = UserType.REGULAR_USER,
        ranchName: String? = null
    ): Result<FirebaseUserProfile> {
        return try {
            // Crear usuario en Firebase Auth
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Error al crear usuario"))

            // Crear perfil de usuario
            val userProfile = FirebaseUserProfile(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                userType = userType,
                ranchName = ranchName,
                isEmailVerified = firebaseUser.isEmailVerified
            )

            // Guardar datos adicionales en Firestore
            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .set(userProfile.toFirestoreMap())
                .await()

            // Enviar email de verificación
            firebaseUser.sendEmailVerification().await()

            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia sesión con email y contraseña
     */
    suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): Result<FirebaseUserProfile> {
        Log.d("FirebaseAuthRepository", "Iniciando sesión con email: $email")
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Error al iniciar sesión"))
            Log.d("FirebaseAuthRepository", "Usuario autenticado: ${firebaseUser.uid}")

            // Obtener datos adicionales de Firestore
            val userProfile = getUserProfile(firebaseUser.uid)
                ?: FirebaseUserProfile.fromFirebaseUser(firebaseUser)

            // Actualizar último inicio de sesión
            updateLastLoginTime(firebaseUser.uid)

            Result.success(userProfile)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepository", "Error al iniciar sesión: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cierra la sesión del usuario actual
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene el perfil completo del usuario desde Firestore
     */
    suspend fun getUserProfile(uid: String): FirebaseUserProfile? {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .await()

            if (document.exists()) {
                FirebaseUserProfile.fromFirestoreDocument(document)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Actualiza el perfil del usuario en Firestore
     */
    suspend fun updateUserProfile(userProfile: FirebaseUserProfile): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userProfile.uid)
                .set(userProfile.toFirestoreMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envía email de verificación al usuario actual
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envía email para restablecer contraseña
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualiza la hora del último inicio de sesión
     */
    private suspend fun updateLastLoginTime(uid: String) {
        try {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update("lastLoginAt", System.currentTimeMillis())
                .await()
        } catch (e: Exception) {
            // Log error but don't fail the operation
        }
    }

    /**
     * Verifica si el email del usuario está verificado
     */
    suspend fun isEmailVerified(): Boolean {
        val user = currentUser
        return if (user != null) {
            // Recargar datos del usuario para obtener el estado más reciente
            user.reload().await()
            user.isEmailVerified
        } else {
            false
        }
    }

    /**
     * Elimina la cuenta del usuario actual
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))
            val uid = user.uid

            // Eliminar datos de Firestore
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .delete()
                .await()

            // Eliminar cuenta de Firebase Auth
            user.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
