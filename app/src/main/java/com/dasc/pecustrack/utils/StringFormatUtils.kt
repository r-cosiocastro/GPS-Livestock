package com.dasc.pecustrack.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng

object StringFormatUtils {
    fun formatearTiempoConexion(timestamp: Long?): String {
        if (timestamp == null) return "Sin datos"

        val ahora = System.currentTimeMillis()
        val diferencia = ahora - timestamp

        val minutos = diferencia / 60_000
        val horas = diferencia / 3_600_000

        return when {
            minutos < 1 -> "Hace menos de 1 minuto"
            minutos in 1..59 -> "Hace $minutos minuto${if (minutos == 1L) "" else "s"}"
            horas == 1L -> "Hace 1 hora"
            horas in 2..23 -> "Hace $horas horas"
            else -> "Hace más de 24 horas"
        }
    }

    fun formatearDistancia(distanciaMetros: Float): String {
        return if (distanciaMetros >= 1000) {
            String.format("%.2f km", distanciaMetros / 1000)
        } else {
            String.format("%.0f m", distanciaMetros)
        }
    }
}