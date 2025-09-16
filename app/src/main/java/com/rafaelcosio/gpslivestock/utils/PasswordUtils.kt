package com.rafaelcosio.gpslivestock.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtils {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000 // Número de iteraciones (ajustar según sea necesario)
    private const val KEY_LENGTH = 256    // Longitud de la clave en bits
    private const val SALT_SIZE = 16      // Tamaño de la sal en bytes

    /**
     * Genera un hash de contraseña y una sal.
     * @param password La contraseña en texto plano.
     * @return Un Pair que contiene la sal (hex string) y el hash de la contraseña (hex string).
     */
    @RequiresApi(Build.VERSION_CODES.O) // PBEKeySpec para SHA256 requiere API 26
    fun hashPassword(password: String): Pair<String, String> {
        val salt = generateSalt()
        val passwordChars = password.toCharArray()
        val keySpec = PBEKeySpec(passwordChars, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(keySpec).encoded
        return Pair(bytesToHexString(salt), bytesToHexString(hash))
    }

    /**
     * Verifica una contraseña en texto plano contra una sal y un hash almacenados.
     * @param password La contraseña en texto plano a verificar.
     * @param saltHex La sal almacenada (hex string).
     * @param storedHashHex El hash de la contraseña almacenado (hex string).
     * @return True si la contraseña coincide, false en caso contrario.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun verifyPassword(password: String, saltHex: String, storedHashHex: String): Boolean {
        val salt = hexStringToBytes(saltHex)
        val storedHash = hexStringToBytes(storedHashHex)
        val passwordChars = password.toCharArray()

        val keySpec = PBEKeySpec(passwordChars, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val calculatedHash = factory.generateSecret(keySpec).encoded

        return calculatedHash.contentEquals(storedHash)
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)
        return salt
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun hexStringToBytes(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                    Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}