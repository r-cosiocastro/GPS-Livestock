package com.rafaelcosio.gpslivestock.utils

import android.content.Context
import android.content.SharedPreferences
import com.rafaelcosio.gpslivestock.data.model.User
import com.rafaelcosio.gpslivestock.data.model.UserType

object AppPreferences {
    const val PREFS_NAME = "PecusTrackPrefs"

    // BLE Preferences
    const val KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS = "last_connected_ble_address"
    const val KEY_LAST_CONNECTED_BLE_DEVICE_NAME = "last_connected_ble_device_name"

    // User Session Preferences
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_TYPE = "user_type"
    private const val KEY_USER_DISPLAY_NAME = "user_display_name"
    private const val KEY_USER_RANCH_NAME = "user_ranch_name"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserSession(context: Context, user: User) {
        val editor = getPreferences(context).edit()
        editor.putInt(KEY_USER_ID, user.id)
        editor.putString(KEY_USER_EMAIL, user.email)
        editor.putString(KEY_USER_TYPE, user.userType.name) // Guardamos el nombre del enum
        user.displayName?.let { editor.putString(KEY_USER_DISPLAY_NAME, it) }
        user.ranchName?.let { editor.putString(KEY_USER_RANCH_NAME, it) }
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserId(context: Context): Int {
        return getPreferences(context).getInt(KEY_USER_ID, -1) // Devuelve -1 si no se encuentra
    }

    fun getUserEmail(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_EMAIL, null)
    }

    fun getUserType(context: Context): UserType? {
        val userTypeName = getPreferences(context).getString(KEY_USER_TYPE, null)
        return userTypeName?.let { UserType.valueOf(it) }
    }

    fun getUserDisplayName(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_DISPLAY_NAME, null)
    }

    fun getUserRanchName(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_RANCH_NAME, null)
    }

    fun clearUserSession(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_USER_ID)
        editor.remove(KEY_USER_EMAIL)
        editor.remove(KEY_USER_TYPE)
        editor.remove(KEY_USER_DISPLAY_NAME)
        editor.remove(KEY_USER_RANCH_NAME)
        editor.remove(KEY_IS_LOGGED_IN)
        editor.apply()
    }

    // Puedes mantener tus métodos existentes para BLE si son necesarios
    fun getLastConnectedBleDeviceAddress(context: Context): String? {
        return getPreferences(context).getString(KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS, null)
    }

    fun saveLastConnectedBleDevice(context: Context, address: String, name: String?) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS, address)
        name?.let { editor.putString(KEY_LAST_CONNECTED_BLE_DEVICE_NAME, it) }
        editor.apply()
    }

    fun getLastConnectedBleDeviceName(context: Context): String? {
        return getPreferences(context).getString(KEY_LAST_CONNECTED_BLE_DEVICE_NAME, null)
    }
}