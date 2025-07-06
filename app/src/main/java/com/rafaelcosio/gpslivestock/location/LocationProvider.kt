package com.rafaelcosio.gpslivestock.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface ILocationProvider {
    val lastKnownLocation: Flow<Location?>
    fun startLocationUpdates(locationCallbackForConsumer: (Location) -> Unit)
    fun stopLocationUpdates()
    suspend fun getLastKnownLocationSuspending(): Location?
}