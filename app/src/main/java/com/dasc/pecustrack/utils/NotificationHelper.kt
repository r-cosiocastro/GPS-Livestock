package com.dasc.pecustrack.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.dasc.pecustrack.R

object NotificationHelper {

    // --- Constantes para Canales y IDs de Notificación ---
    // (Puedes mover las constantes de BluetoothService aquí si son exclusivamente para notificaciones)
    const val BLUETOOTH_SERVICE_CHANNEL_ID = "BluetoothServiceChannel"
    const val BLUETOOTH_SERVICE_NOTIFICATION_ID = 1


    const val DATA_UPDATE_CHANNEL_ID = "DataUpdateChannel"
    const val DATA_UPDATE_NOTIFICATION_ID = 2
    // ... otros canales e IDs que puedas necesitar


    /**
     * Crea un canal de notificación. Debe llamarse antes de mostrar cualquier notificación
     * en este canal en Android 8.0 (API 26) y superior.
     *
     * @param context El contexto de la aplicación.
     * @param channelId El ID único del canal.
     * @param channelName El nombre del canal visible para el usuario.
     * @param channelDescription La descripción del canal visible para el usuario.
     * @param importance La importancia del canal (ej. NotificationManager.IMPORTANCE_DEFAULT).
     */
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        channelDescription: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
            // Puedes configurar más propiedades del canal aquí (luces, vibración, etc.)
            // enableLights(true)
            // lightColor = Color.RED
            // enableVibration(true)
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d("NotificationHelper", "Canal de notificación creado: $channelId")
    }

    /**
     * Crea y devuelve una notificación básica.
     *
     * @param context El contexto de la aplicación.
     * @param channelId El ID del canal para esta notificación.
     * @param title El título de la notificación.
     * @param contentText El texto principal de la notificación.
     * @param smallIconResId El ID del recurso para el ícono pequeño de la notificación.
     * @param pendingIntent El PendingIntent a ejecutar cuando se toque la notificación (opcional).
     * @param autoCancel Si la notificación debe eliminarse automáticamente al tocarla.
     * @param onGoing Si la notificación es para una tarea en curso (ej. servicio en primer plano).
     * @return El objeto Notification construido.
     */
    fun createBasicNotification(
        context: Context,
        channelId: String,
        title: String,
        contentText: String,
        smallIconResId: Int = R.drawable.ic_notification, // Reemplaza con tu ícono por defecto
        pendingIntent: PendingIntent? = null,
        autoCancel: Boolean = true,
        onGoing: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconResId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Para compatibilidad con < API 26
            .setAutoCancel(autoCancel)
            .setOngoing(onGoing)

        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        return builder.build()
    }

    /**
     * Muestra una notificación.
     *
     * @param context El contexto de la aplicación.
     * @param notificationId El ID único para esta notificación (para actualizarla o cancelarla).
     * @param notification El objeto Notification a mostrar.
     */
    fun showNotification(context: Context, notificationId: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
        Log.d("NotificationHelper", "Mostrando notificación: $notificationId")
    }

    /**
     * Cancela/elimina una notificación específica.
     *
     * @param context El contexto de la aplicación.
     * @param notificationId El ID de la notificación a cancelar.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        Log.d("NotificationHelper", "Cancelando notificación: $notificationId")
    }


    // --- Métodos específicos para diferentes tipos de notificaciones (Ejemplos) ---

    /**
     * Crea la notificación específica para el servicio Bluetooth en primer plano.
     */
    fun createBluetoothServiceNotification(context: Context, statusText: String): Notification {
        // Ejemplo de PendingIntent para abrir tu actividad de Bluetooth
        // val intent = Intent(context, YourBluetoothActivity::class.java).apply {
        //     flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // }
        // val pendingIntent = PendingIntent.getActivity(
        //     context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // )

        return createBasicNotification(
            context = context,
            channelId = BLUETOOTH_SERVICE_CHANNEL_ID,
            title = context.getString(R.string.bluetooth_service_conectado), // Usa recursos de strings
            contentText = statusText,
            smallIconResId = R.drawable.ic_bluetooth_connected, // Ícono específico
            // pendingIntent = pendingIntent, // Opcional
            autoCancel = false, // Las notificaciones de servicio en primer plano no suelen ser auto-cancelables
            onGoing = true // Esencial para servicios en primer plano
        )
    }

}