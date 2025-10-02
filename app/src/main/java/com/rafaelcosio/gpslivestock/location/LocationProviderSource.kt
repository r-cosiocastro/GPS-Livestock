package com.rafaelcosio.gpslivestock.location

import com.google.android.gms.maps.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class LocationProviderSource @Inject constructor(
    private val locationProvider: ILocationProvider
) : LocationSource {

    private var listener: LocationSource.OnLocationChangedListener? = null
    private var locationJob: Job? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        this.listener = listener
        locationJob?.cancel()
        locationJob = locationProvider.lastKnownLocation
            .onEach { location ->
                location?.let {
                    listener.onLocationChanged(it)
                }
            }
            .launchIn(CoroutineScope(Job()))
    }

    override fun deactivate() {
        locationJob?.cancel()
        listener = null
    }
}