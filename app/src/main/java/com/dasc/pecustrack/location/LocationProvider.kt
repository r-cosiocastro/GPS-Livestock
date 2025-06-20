package com.dasc.pecustrack.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface ILocationProvider {
    val lastKnownLocation: Flow<Location?> // Para exponer la última ubicación conocida como un Flow
    fun startLocationUpdates(locationCallbackForConsumer: (Location) -> Unit)
    fun stopLocationUpdates()
    suspend fun getLastKnownLocationSuspending(): Location? // Alternativa suspend para obtener una sola vez
}