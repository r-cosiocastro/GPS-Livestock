package com.rafaelcosio.gpslivestock.ui.view

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

// BaseActivity.kt
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Habilitar edge-to-edge para todas las Activities que hereden de esta.
        WindowCompat.setDecorFitsSystemWindows(window, false) // CORRECTO para Views

        // 2. Configurar colores de barra transparentes por defecto (pueden ser anulados)
        window.statusBarColor = Color.TRANSPARENT      // CORRECTO para Views
        window.navigationBarColor = Color.TRANSPARENT // CORRECTO para Views

        // 3. Configurar apariencia de iconos de barra (puede ser anulado)
        val controller = WindowInsetsControllerCompat(window, window.decorView.rootView) // CORRECTO para Views
        controller.isAppearanceLightStatusBars = true // Asume fondo claro por defecto
        controller.isAppearanceLightNavigationBars = true // Asume fondo claro por defecto
    }

    // Opcional: Método para que las subclases apliquen insets de forma común
    // si tienen un ID de vista común para el padding.
    protected fun applySystemBarInsets(targetView: View) { // CORRECTO para Views
        ViewCompat.setOnApplyWindowInsetsListener(targetView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding( // CORRECTO para Views
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    // Opcional: Método para que las subclases ajusten la apariencia de la barra de estado
    protected fun setLightStatusBar(isLight: Boolean) { // CORRECTO para Views
        val controller = WindowInsetsControllerCompat(window, window.decorView.rootView)
        controller.isAppearanceLightStatusBars = isLight
    }

    protected fun setLightNavigationBar(isLight: Boolean) { // CORRECTO para Views
        val controller = WindowInsetsControllerCompat(window, window.decorView.rootView)
        controller.isAppearanceLightNavigationBars = isLight
    }
}

