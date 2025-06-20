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
            getString(R.string.bluetooth_service_channel_name), // "Servicio Bluetooth"
            getString(R.string.bluetooth_service_channel_description), // "Notificaciones del servicio Bluetooth"
            NotificationManager.IMPORTANCE_LOW // O DEFAULT, para foreground services a veces LOW es mejor
        )
        NotificationHelper.createNotificationChannel(
            applicationContext,
            NotificationHelper.DATA_UPDATE_CHANNEL_ID,
            getString(R.string.data_update_channel_name), // "Actualizaciones de Dispositivos"
            getString(R.string.data_update_channel_description) // "Notificaciones sobre nuevos datos de dispositivos"
        )
        // Crea otros canales que necesites
    }
}