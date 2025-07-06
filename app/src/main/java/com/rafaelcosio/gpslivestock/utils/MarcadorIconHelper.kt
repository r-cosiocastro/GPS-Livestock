package com.rafaelcosio.gpslivestock.utils

import android.content.Context
import android.graphics.Canvas
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.rafaelcosio.gpslivestock.R
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

object MarcadorIconHelper {
    const val TIPO_ANIMAL_VACA = 0
    const val TIPO_ANIMAL_CABALLO = 1
    const val TIPO_ANIMAL_OVEJA = 2
    const val TIPO_ANIMAL_CABRA = 3
    const val TIPO_ANIMAL_CERDO = 4
    private val COLOR_NORMAL = "#9cb3ff".toColorInt()
    private val COLOR_FUERA_AREA = "#e86a66".toColorInt()
    private val COLOR_INACTIVO = "#bdbdbd".toColorInt()

    private val BADGE_FUERA_AREA = R.drawable.ic_alert_badge
    private val BADGE_INACTIVO = R.drawable.ic_disconnected_badge
    private val BADGE_FUERA_AREA_E_INACTIVO = R.drawable.ic_disconnected_inactive_badge
    private val BADGE_ACTIVO = R.drawable.ic_connected

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
        rastreador: Rastreador,
        escalaIconoPrincipal: Float = 0.8f,
        escalaInsignia: Float = 0.5f
    ): BitmapDescriptor? {
        val claveCache = generarClaveCache(
            rastreador.tipoAnimal,
            rastreador.activo,
            rastreador.dentroDelArea
        )

        if (cache.containsKey(claveCache)) {
            return cache[claveCache]
        }

        val iconoBaseResId = when (rastreador.tipoAnimal) {
            TIPO_ANIMAL_VACA -> R.drawable.cow
            TIPO_ANIMAL_CABALLO -> R.drawable.horse
            TIPO_ANIMAL_OVEJA -> R.drawable.sheep
            TIPO_ANIMAL_CABRA -> R.drawable.goat
            TIPO_ANIMAL_CERDO -> R.drawable.pig
            else -> R.drawable.cow
        }

        var colorTinte = COLOR_NORMAL
        var iconoInsigniaResId: Int? = null

        when {
            !rastreador.activo && !rastreador.dentroDelArea -> {
                colorTinte = COLOR_INACTIVO
                iconoInsigniaResId = BADGE_FUERA_AREA_E_INACTIVO
            }
            !rastreador.activo && rastreador.dentroDelArea -> {
                colorTinte = COLOR_INACTIVO
                iconoInsigniaResId = BADGE_INACTIVO
            }
            rastreador.activo && !rastreador.dentroDelArea -> {
                colorTinte = COLOR_FUERA_AREA
                iconoInsigniaResId = BADGE_FUERA_AREA
            }
            rastreador.activo && rastreador.dentroDelArea -> {
                colorTinte = COLOR_NORMAL
                iconoInsigniaResId = BADGE_ACTIVO
            }
        }

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
        @ColorInt tintColor: Int? = null,
        @DrawableRes badgeIconResId: Int? = null,
        badgeScale: Float = 0.4f,
        badgeMarginFactor: Float = 0.05f
    ): BitmapDescriptor? {
        val vectorDrawable =
            ContextCompat.getDrawable(context, vectorResId)?.mutate() ?: return null
        tintColor?.let {
        }
        val intrinsicWidth = vectorDrawable.intrinsicWidth
        val intrinsicHeight = vectorDrawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return null
        }

        val scaledWidth = (intrinsicWidth * scale).toInt()
        val scaledHeight = (intrinsicHeight * scale).toInt()

        vectorDrawable.setBounds(0, 0, scaledWidth, scaledHeight)
        val bitmap = createBitmap(scaledWidth, scaledHeight)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        badgeIconResId?.let { badgeResId ->
            val badgeDrawable =
                ContextCompat.getDrawable(context, badgeResId)?.mutate() ?: return@let

            val badgeIntrinsicWidth = badgeDrawable.intrinsicWidth
            val badgeIntrinsicHeight = badgeDrawable.intrinsicHeight

            if (badgeIntrinsicWidth > 0 && badgeIntrinsicHeight > 0) {
                val badgeFinalWidth = (scaledWidth * badgeScale).toInt()
                val badgeFinalHeight = (scaledHeight * badgeScale).toInt()

                val margin = (scaledWidth * badgeMarginFactor).toInt()
                val badgeLeft = scaledWidth - badgeFinalWidth - margin
                val badgeTop = margin

                badgeDrawable.setBounds(0, 0, badgeFinalWidth, badgeFinalHeight)
                canvas.save()
                canvas.translate(badgeLeft.toFloat(), badgeTop.toFloat())
                badgeDrawable.draw(canvas)
                canvas.restore()
            }
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun limpiarCache() {
        cache.clear()
    }
}