package com.dasc.pecustrack.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

object MarcadorIconHelper {

    // Constantes para los tipos de animal (mejora la legibilidad)
    const val TIPO_ANIMAL_VACA = 0
    const val TIPO_ANIMAL_CABALLO = 1
    const val TIPO_ANIMAL_OVEJA = 2
    const val TIPO_ANIMAL_CABRA = 3
    const val TIPO_ANIMAL_CERDO = 4

    // Colores base
    private val COLOR_NORMAL = "#9cb3ff".toColorInt() // Azul
    private val COLOR_FUERA_AREA = "#e86a66".toColorInt() // Naranja claro
    private val COLOR_INACTIVO = "#bdbdbd".toColorInt() // Gris claro

    // IDs de recursos para los iconos de estado (badges)
    private val BADGE_FUERA_AREA = R.drawable.ic_alert_badge // Reemplaza con tu ID
    private val BADGE_INACTIVO = R.drawable.ic_disconnected_badge // Reemplaza con tu ID

    private val cache = mutableMapOf<String, BitmapDescriptor>()

    private fun generarClaveCache(
        tipoAnimal: Int,
        activo: Boolean,
        dentroDelArea: Boolean
    ): String {
        return "$tipoAnimal-$activo-$dentroDelArea"
    }

    fun obtenerIconoMarcador(
        context: Context,
        dispositivo: Dispositivo,
        escalaIconoPrincipal: Float = 0.8f, // Escala para el ícono principal
        escalaInsignia: Float = 0.5f     // Escala para la insignia
    ): BitmapDescriptor? {
        val claveCache = generarClaveCache(
            dispositivo.tipoAnimal,
            dispositivo.activo,
            dispositivo.dentroDelArea
        )

        if (cache.containsKey(claveCache)) {
            return cache[claveCache]
        }

        val iconoBaseResId = when (dispositivo.tipoAnimal) {
            TIPO_ANIMAL_VACA -> R.drawable.cow
            TIPO_ANIMAL_CABALLO -> R.drawable.horse
            TIPO_ANIMAL_OVEJA -> R.drawable.sheep
            TIPO_ANIMAL_CABRA -> R.drawable.goat
            TIPO_ANIMAL_CERDO -> R.drawable.pig
            else -> R.drawable.cow // Un ícono por defecto si el tipo es desconocido
        }

        var colorTinte = COLOR_NORMAL
        var iconoInsigniaResId: Int? = null

        if (!dispositivo.activo) {
            colorTinte = COLOR_INACTIVO
            iconoInsigniaResId = BADGE_INACTIVO
        } else if (!dispositivo.dentroDelArea) {
            colorTinte = COLOR_FUERA_AREA
            iconoInsigniaResId = BADGE_FUERA_AREA
        }
        // Si está activo Y dentro del área, se usa COLOR_NORMAL y no hay iconoInsigniaResId

        return bitmapDescriptorFromVector(
            context = context,
            vectorResId = iconoBaseResId,
            scale = escalaIconoPrincipal,
            tintColor = colorTinte,
            badgeIconResId = iconoInsigniaResId,
            badgeScale = escalaInsignia
        )
    }



    fun bitmapDescriptorFromVector(
        context: Context,
        @DrawableRes vectorResId: Int,
        scale: Float = 0.5f,
        @ColorInt tintColor: Int? = null, // Parámetro para el tinte (opcional)
        @DrawableRes badgeIconResId: Int? = null, // Parámetro para el icono de insignia (opcional)
        badgeScale: Float = 0.4f, // Escala para el icono de insignia (respecto al tamaño del icono principal)
        badgeMarginFactor: Float = 0.05f // Margen para el icono de insignia (factor del tamaño del icono principal)
    ): BitmapDescriptor? {
        // 1. Cargar el drawable vectorial principal
        val vectorDrawable =
            ContextCompat.getDrawable(context, vectorResId)?.mutate() ?: return null

        // 2. Aplicar tinte si se especifica
        tintColor?.let {
            //DrawableCompat.setTint(vectorDrawable, it)
            //DrawableCompat.setTintMode(vectorDrawable, PorterDuff.Mode.MULTIPLY) // Asegura que el tinte se aplique correctamente
        }

        // 3. Calcular dimensiones escaladas del icono principal
        val intrinsicWidth = vectorDrawable.intrinsicWidth
        val intrinsicHeight = vectorDrawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            // El drawable no tiene dimensiones intrínsecas válidas (puede pasar con algunos <shape>)
            return null
        }

        val scaledWidth = (intrinsicWidth * scale).toInt()
        val scaledHeight = (intrinsicHeight * scale).toInt()

        vectorDrawable.setBounds(0, 0, scaledWidth, scaledHeight)

        // 4. Crear el Bitmap principal
        val bitmap = createBitmap(scaledWidth, scaledHeight)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)

        // 5. Superponer el icono de insignia si se especifica
        badgeIconResId?.let { badgeResId ->
            val badgeDrawable =
                ContextCompat.getDrawable(context, badgeResId)?.mutate() ?: return@let

            val badgeIntrinsicWidth = badgeDrawable.intrinsicWidth
            val badgeIntrinsicHeight = badgeDrawable.intrinsicHeight

            if (badgeIntrinsicWidth > 0 && badgeIntrinsicHeight > 0) {
                // Calcular tamaño y posición de la insignia
                val badgeFinalWidth = (scaledWidth * badgeScale).toInt()
                val badgeFinalHeight = (scaledHeight * badgeScale).toInt() // O ((badgeIntrinsicHeight.toFloat() / badgeIntrinsicWidth) * badgeFinalWidth).toInt() para mantener aspect ratio

                val margin = (scaledWidth * badgeMarginFactor).toInt()
                val badgeLeft = scaledWidth - badgeFinalWidth - margin
                val badgeTop = margin

                badgeDrawable.setBounds(0, 0, badgeFinalWidth, badgeFinalHeight) // Para dibujar correctamente en su propio canvas si fuera necesario, pero lo dibujaremos directo

                // Crear un Paint para la insignia si se necesita (ej. para transparencia)
                // val paint = Paint().apply { isAntiAlias = true }

                // Guardar el estado del canvas principal y aplicar transformaciones para la insignia
                canvas.save()
                canvas.translate(badgeLeft.toFloat(), badgeTop.toFloat())
                badgeDrawable.draw(canvas) // Dibujar la insignia sobre el canvas del bitmap principal
                canvas.restore()
            }
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun limpiarCache() {
        cache.clear()
    }
}