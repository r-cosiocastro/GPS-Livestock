package com.rafaelcosio.gpslivestock.data.model

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Modelo de usuario que integra Firebase Auth con datos locales
 */
data class FirebaseUserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val userType: UserType = UserType.REGULAR_USER,
    val ranchName: String? = null,
    val isEmailVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {

    companion object {
        /**
         * Convierte un FirebaseUser a FirebaseUserProfile
         */
        fun fromFirebaseUser(firebaseUser: FirebaseUser): FirebaseUserProfile {
            return FirebaseUserProfile(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName,
                isEmailVerified = firebaseUser.isEmailVerified,
                lastLoginAt = System.currentTimeMillis()
            )
        }

        /**
         * Convierte un DocumentSnapshot de Firestore a FirebaseUserProfile
         */
        fun fromFirestoreDocument(document: DocumentSnapshot): FirebaseUserProfile? {
            return try {
                FirebaseUserProfile(
                    uid = document.id,
                    email = document.getString("email") ?: "",
                    displayName = document.getString("displayName"),
                    userType = UserType.valueOf(document.getString("userType") ?: "REGULAR_USER"),
                    ranchName = document.getString("ranchName"),
                    isEmailVerified = document.getBoolean("isEmailVerified") ?: false,
                    createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
                    lastLoginAt = document.getLong("lastLoginAt") ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convierte FirebaseUserProfile a Map para guardar en Firestore
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "email" to email,
            "displayName" to displayName,
            "userType" to userType.name,
            "ranchName" to ranchName,
            "isEmailVerified" to isEmailVerified,
            "createdAt" to createdAt,
            "lastLoginAt" to lastLoginAt
        )
    }

    /**
     * Convierte FirebaseUserProfile a User local para Room
     */
    fun toLocalUser(): User {
        return User(
            email = email,
            passwordHash = "", // No almacenamos la contraseña con Firebase
            salt = "", // No necesario con Firebase
            userType = userType,
            displayName = displayName,
            ranchName = ranchName
        )
    }
}
