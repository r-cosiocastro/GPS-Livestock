package com.rafaelcosio.gpslivestock.ui.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rafaelcosio.gpslivestock.utils.AppPreferences // <-- Importar AppPreferences

class SplashView : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { true } // Mantener el splash hasta que la lógica decida
        checkPermissions()
    }

    private val requiredPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()


    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) { 
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Todos los permisos ya están concedidos
            Log.d("SplashView", "All permissions already granted on check")
            decideNextActivity() // <-- Llamar a la nueva función
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                }
            }
            if (allGranted) {
                Log.d("SplashView", "All permissions granted from launcher")
                decideNextActivity() // <-- Llamar a la nueva función
            } else {
                showPermissionDeniedDialog()
            }
        }

    private fun decideNextActivity() {
        // Verificar si el usuario ya ha iniciado sesión
        val userEmail = AppPreferences.getUserEmail(this)
        val targetActivity = if (!userEmail.isNullOrBlank()) {
            Log.d("SplashView", "User session found. Navigating to MapsView.")
            MapsView::class.java
        } else {
            Log.d("SplashView", "No user session. Navigating to LoginView.")
            LoginView::class.java
        }
        val intent = Intent(this, targetActivity)
        startActivity(intent)
        finish() // Finalizar SplashView para que no quede en la pila
    }

    private fun showPermissionDeniedDialog() {
        if (isFinishing || isDestroyed) { 
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Permisos Requeridos")
            .setMessage("Esta aplicación necesita permisos de Bluetooth y ubicación para escanear y conectarse a dispositivos Bluetooth. Por favor, otórguelos en la configuración de la aplicación o cierre la aplicación.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cerrar aplicación") { dialog, _ ->
                dialog.dismiss()
                finishAffinity() // Cierra la app completamente si los permisos son denegados
            }
            .setCancelable(false) // Para asegurar que el usuario tome una acción
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Lógica de onResume si se necesita para re-chequear permisos después de volver de Configuración
        // Por ahora, si el usuario concedió permisos en Configuración, 
        // la próxima vez que se inicie la app (o si esta actividad sigue viva y se llama a checkPermissions de alguna forma),
        // se detectarán.
    }
}