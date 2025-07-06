package com.rafaelcosio.gpslivestock.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val _lastKnownLocationFlow = MutableStateFlow<Location?>(null)
    override val lastKnownLocation: StateFlow<Location?> = _lastKnownLocationFlow

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = TimeUnit.SECONDS.toMillis(10)
        fastestInterval = TimeUnit.SECONDS.toMillis(5)
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(locationCallbackForConsumer: (Location) -> Unit) {
        if (PermissionUtils.hasLocationPermissions(context)) {
            Log.d("LocationProvider", "Iniciando actualizaciones de ubicación.")
            this.externalLocationCallback = locationCallbackForConsumer

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        Log.d("LocationProvider", "Nueva ubicación recibida: $location")
                        _lastKnownLocationFlow.value = location
                        externalLocationCallback?.invoke(location)
                    }
                }

                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    super.onLocationAvailability(locationAvailability)
                    Log.d("LocationProvider", "Disponibilidad de ubicación: ${locationAvailability.isLocationAvailable}")
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
            }
        } else {
            Log.w("LocationProvider", "No se tienen permisos de ubicación para iniciar actualizaciones.")
        }
    }

    override fun stopLocationUpdates() {
        Log.d("LocationProvider", "Deteniendo actualizaciones de ubicación.")
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
            externalLocationCallback = null
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
            _lastKnownLocationFlow.value = location
            location
        } catch (e: Exception) {
            Log.e("LocationProvider", "Error al obtener la última ubicación conocida: ${e.message}")
            null
        }
    }
    object PermissionUtils {
        fun hasLocationPermissions(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
class LocationPermissionMissingException(message: String) : Exception(message)