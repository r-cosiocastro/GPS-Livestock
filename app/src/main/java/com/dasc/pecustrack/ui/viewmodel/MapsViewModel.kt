package com.dasc.pecustrack.ui.viewmodel

import android.Manifest
import android.app.Application
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dasc.pecustrack.data.model.Dispositivo
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val _dispositivos = MutableLiveData<List<Dispositivo>>()
    val dispositivos: LiveData<List<Dispositivo>> get() = _dispositivos

    private val _ubicacionActual = MutableLiveData<Location>()
    val ubicacionActual: LiveData<Location> get() = _ubicacionActual

    private val _dispositivoSeleccionado = MutableLiveData<Dispositivo?>()
    val dispositivoSeleccionado: LiveData<Dispositivo?> get() = _dispositivoSeleccionado

    val markerDispositivoMap = mutableMapOf<Marker, Dispositivo>()

    private lateinit var locationCallback: LocationCallback

    fun cargarDispositivosEjemplo() {
        val lista = listOf(
            Dispositivo(1, "Vaquita mú", "Vaca con manchas negras", 24.102455, -110.316152),
            Dispositivo(2, "Vaquita lechera", "Da mucha leche", 24.1051774, -110.3698646),
            Dispositivo(3, "Vaquita del aramburo", "Casi nos la quita el Chedraui™️", 24.1108454, -110.3129548)
        )
        _dispositivos.value = lista
    }

    fun seleccionarDispositivo(dispositivo: Dispositivo) {
        _dispositivoSeleccionado.value = dispositivo
    }

    fun deseleccionarDispositivo() {
        _dispositivoSeleccionado.value = null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun iniciarActualizacionUbicacion() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    _ubicacionActual.postValue(it)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    fun detenerActualizacionUbicacion() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun calcularDistancia(origen: Location, destino: Location): Float {
        return origen.distanceTo(destino)
    }

    fun actualizarUbicacionDispositivo(dispositivoActualizado: Dispositivo) {
        val entry = markerDispositivoMap.entries.find { it.value.id == dispositivoActualizado.id }
        entry?.let { (marker, _) ->
            marker.position = LatLng(dispositivoActualizado.latitud, dispositivoActualizado.longitud)
            markerDispositivoMap[marker] = dispositivoActualizado
            if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                _dispositivoSeleccionado.value = dispositivoActualizado
            }
        }
    }

    fun obtenerBoundsParaMapa(): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _ubicacionActual.value.let { listPuntos.add(LatLng(_ubicacionActual.value!!.latitude, _ubicacionActual.value!!.longitude)) }
        _dispositivos.value?.let { dispositivos ->
            dispositivos.forEach { d ->
                listPuntos.add(LatLng(d.latitud, d.longitud))
            }
        }
        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun obtenerBoundsParaDispositivo(dispositivo: Dispositivo): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _ubicacionActual.value.let { listPuntos.add(LatLng(_ubicacionActual.value!!.latitude, _ubicacionActual.value!!.longitude)) }
        listPuntos.add(LatLng(dispositivo.latitud, dispositivo.longitud))
        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

}