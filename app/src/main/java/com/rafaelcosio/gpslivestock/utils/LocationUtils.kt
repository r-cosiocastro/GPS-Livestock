package com.rafaelcosio.gpslivestock.utils

import android.location.Location
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

object LocationUtils {
    /*
    fun calcularDistancia(origen: LatLng, destino: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(origen.latitude, origen.longitude, destino.latitude, destino.longitude, results)
        return results[0]
    }
     */


    fun calcularDistancia(origen: Location, destino: Location): Float {
        return origen.distanceTo(destino)
    }

    fun dispositivoEstaDentroDelPoligono(
        ubicacion: LatLng,
        puntosPoligono: List<LatLng>,
        geodesic: Boolean = true // Usualmente true para cálculos más precisos
    ): Boolean {
        if (puntosPoligono.size < 3) return false // Un polígono necesita al menos 3 puntos
        return PolyUtil.containsLocation(ubicacion, puntosPoligono, geodesic)
    }

    fun dispositivoEstaDentroDeCualquierPoligono(
        ubicacionDispositivo: LatLng,
        listaPoligonos: List<Poligono>
    ): Boolean {
        if (listaPoligonos.isEmpty()) return false
        return listaPoligonos.any { poligono ->
            if (poligono.puntos.size < 3) false
            else PolyUtil.containsLocation(ubicacionDispositivo, poligono.puntos, true)
        }
    }

}