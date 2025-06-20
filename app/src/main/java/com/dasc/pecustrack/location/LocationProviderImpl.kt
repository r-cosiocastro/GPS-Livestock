package com.dasc.pecustrack.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Hazlo Singleton si quieres una única instancia gestionando la ubicación
class LocationProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ILocationProvider {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var externalLocationCallback: ((Location) -> Unit)? = null

    // Usaremos un StateFlow para la última ubicación conocida, así los observadores siempre obtienen el último valor
    private val _lastKnownLocationFlow = MutableStateFlow<Location?>(null)
    override val lastKnownLocation: StateFlow<Location?> = _lastKnownLocationFlow

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = TimeUnit.SECONDS.toMillis(10)       // Intervalo deseado para actualizaciones activas
        fastestInterval = TimeUnit.SECONDS.toMillis(5) // Intervalo más rápido si la ubicación está disponible antes
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        // maxWaitTime = TimeUnit.MINUTES.toMillis(2) // Opcional: para actualizaciones en lote
    }

    @SuppressLint("MissingPermission") // Asegúrate de manejar los permisos ANTES de llamar a esto
    override fun startLocationUpdates(locationCallbackForConsumer: (Location) -> Unit) {
        if (PermissionUtils.hasLocationPermissions(context)) { // Utilidad para verificar permisos
            Log.d("LocationProvider", "Iniciando actualizaciones de ubicación.")
            this.externalLocationCallback = locationCallbackForConsumer

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        Log.d("LocationProvider", "Nueva ubicación recibida: $location")
                        _lastKnownLocationFlow.value = location // Actualiza el StateFlow
                        externalLocationCallback?.invoke(location)
                    }
                }

                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    super.onLocationAvailability(locationAvailability)
                    Log.d("LocationProvider", "Disponibilidad de ubicación: ${locationAvailability.isLocationAvailable}")
                    // Puedes manejar la no disponibilidad aquí si es necesario
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                Log.e("LocationProvider", "Error de seguridad al solicitar actualizaciones: ${e.message}")
                // Aquí podrías notificar al consumidor sobre el error de permisos
            }
        } else {
            Log.w("LocationProvider", "No se tienen permisos de ubicación para iniciar actualizaciones.")
            // Notificar al consumidor o lanzar una excepción personalizada si es necesario
            // throw LocationPermissionMissingException("Location permissions are required.")
        }
    }

    override fun stopLocationUpdates() {
        Log.d("LocationProvider", "Deteniendo actualizaciones de ubicación.")
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
            externalLocationCallback = null // Limpia el callback externo también
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocationSuspending(): Location? {
        if (!PermissionUtils.hasLocationPermissions(context)) {
            Log.w("LocationProvider", "No se tienen permisos para obtener la última ubicación.")
            return null
        }
        return try {
            val location = fusedLocationClient.lastLocation.await()
            _lastKnownLocationFlow.value = location // Actualiza el StateFlow también
            location
        } catch (e: Exception) {
            Log.e("LocationProvider", "Error al obtener la última ubicación conocida: ${e.message}")
            null
        }
    }

    // Pequeña utilidad para permisos (puedes moverla a un archivo separado)
    object PermissionUtils {
        fun hasLocationPermissions(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

// Opcional: Excepción personalizada
class LocationPermissionMissingException(message: String) : Exception(message)