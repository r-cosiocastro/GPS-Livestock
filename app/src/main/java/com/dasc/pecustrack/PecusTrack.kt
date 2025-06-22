package com.dasc.pecustrack

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import com.dasc.pecustrack.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PecusTrack : Application(){
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(
            applicationContext,
            NotificationHelper.BLUETOOTH_SERVICE_CHANNEL_ID,
            getString(R.string.bluetooth_service_channel_name),
            getString(R.string.bluetooth_service_channel_description), // "Notificaciones del servicio Bluetooth"
        )
        NotificationHelper.createNotificationChannel(
            applicationContext,
            NotificationHelper.DATA_UPDATE_CHANNEL_ID,
            getString(R.string.data_update_channel_name),
            getString(R.string.data_update_channel_description) // "Notificaciones sobre nuevos datos de dispositivos"
        )
    }
}