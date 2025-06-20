package com.dasc.pecustrack.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.utils.NotificationHelper
import com.dasc.pecustrack.utils.parcelable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {

    companion object {
        const val ACTION_START_SCAN = "com.dasc.pecustrack.ACTION_START_SCAN"
        const val ACTION_CONNECT = "com.dasc.pecustrack.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.dasc.pecustrack.ACTION_DISCONNECT"
        // ...
        const val ACTION_DEVICE_FOUND = "com.dasc.pecustrack.ACTION_DEVICE_FOUND"
        const val ACTION_STATUS_CHANGED = "com.dasc.pecustrack.ACTION_STATUS_CHANGED"
        const val ACTION_DATA_RECEIVED = "com.dasc.pecustrack.ACTION_DATA_RECEIVED"
        const val ACTION_DEVICE_CONNECTED_INFO = "com.dasc.pecustrack.ACTION_DEVICE_CONNECTED_INFO"

        const val EXTRA_DEVICE = "com.dasc.pecustrack.EXTRA_DEVICE"
        const val EXTRA_DEVICE_ADDRESS = "com.dasc.pecustrack.EXTRA_DEVICE_ADDRESS"
        const val EXTRA_STATUS = "com.dasc.pecustrack.EXTRA_STATUS"
        const val EXTRA_DATA_ID = "com.dasc.pecustrack.EXTRA_DATA_ID"
        const val EXTRA_DATA_LAT = "com.dasc.pecustrack.EXTRA_DATA_LAT"
        const val EXTRA_DATA_LON = "com.dasc.pecustrack.EXTRA_DATA_LON"

        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    // Inyectar el repositorio
    @Inject
    lateinit var dispositivoRepository: DispositivoRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper // Asumiendo que también lo inyectas o lo tienes como object


    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    // CoroutineScope para operaciones del repositorio desde el servicio
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Usa Dispatchers.IO para DB ops


    // Para escaneo BLE
    // private lateinit var bleScanner: BluetoothLeScanner
    // private val scanCallback = object : ScanCallback() { ... }

    // Para escaneo clásico
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.parcelable(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.w("BluetoothService", "Permiso BLUETOOTH_CONNECT denegado, no se puede obtener nombre.")
                            // Considera enviar solo la dirección si no tienes permiso para el nombre
                        }
                        // Solo enviar si tiene nombre (o manejarlo de otra forma)
                        // if (it.name != null) { // Algunos dispositivos pueden no tener nombre inicialmente
                        sendDeviceFoundBroadcast(it)
                        Log.d("BluetoothService", "Dispositivo encontrado: ${it.name ?: it.address}")
                        // }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("BluetoothService", "Descubrimiento de dispositivos finalizado.")
                    sendStatusBroadcast("Escaneo finalizado") // Informar a la UI
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        createNotificationChannel()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryReceiver, filter)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationText = "Servicio Bluetooth en ejecución..."
        val notification = NotificationHelper.createBluetoothServiceNotification(this, notificationText)
        startForeground(NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_START_SCAN -> {
                Log.d("BluetoothService", "Comando recibido: ACTION_START_SCAN")
                startScan()
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                Log.d("BluetoothService", "Comando recibido: ACTION_CONNECT a $address")
                address?.let {
                    val device = bluetoothAdapter.getRemoteDevice(it)
                    connectToDevice(device)
                }
            }
            ACTION_DISCONNECT -> {
                Log.d("BluetoothService", "Comando recibido: ACTION_DISCONNECT")
                disconnectAndStop()
            }
        }
        return START_STICKY // O START_NOT_STICKY si prefieres
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // No puedes escanear sin permiso en Android 12+
            sendStatusBroadcast("Error: Permiso de escaneo denegado")
            return
        }
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery() // Para Bluetooth Clásico
        sendStatusBroadcast("Escaneando dispositivos...")
        // Para BLE:
        // bleScanner.startScan(scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery()
        }
        connectThread = ConnectThread(device)
        connectThread?.start()
        sendStatusBroadcast("Conectando a ${device.name ?: device.address}...")
    }

    private fun disconnect() {
        connectedThread?.cancel()
        connectThread?.cancel() // En caso de que estuviera intentando conectar
        sendStatusBroadcast("Desconectado")
        // Considera stopSelf() si el servicio ya no necesita estar activo
    }


    // --- Hilos para conexión y comunicación (Bluetooth Clásico) ---
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) @RequiresPermission(
            Manifest.permission.BLUETOOTH_CONNECT
        ) {
            try {
                // MY_UUID es el UUID que tu dispositivo servidor Bluetooth usa
                val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID para SPP
                if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    sendStatusBroadcast("Error: Permiso de conexión denegado")
                    return@lazy null
                }
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: SecurityException) {
                Log.e("BluetoothService", "Socket's create() failed", e)
                sendStatusBroadcast("Error: Fallo al crear socket")
                null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun run() {
            if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.cancelDiscovery() // Asegúrate de que el descubrimiento esté cancelado
            }
            try {
                mmSocket?.connect() // Llamada bloqueante
                // Conexión exitosa, ahora maneja la comunicación
                mmSocket?.let { socket ->
                    manageConnectedSocket(socket)
                }
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not connect the client socket", e)
                sendStatusBroadcast("Error: No se pudo conectar")
                try {
                    mmSocket?.close()
                } catch (closeException: IOException) {
                    Log.e("BluetoothService", "Could not close the client socket", closeException)
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not close the client socket", e)
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        bluetoothSocket = socket // Guardar referencia
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BluetoothService", "Permiso BLUETOOTH_CONNECT denegado para obtener nombre del dispositivo conectado.")
            sendStatusBroadcast("Conectado a ${socket.remoteDevice.address}")
            sendConnectedDeviceToViewModel(socket.remoteDevice) // Enviar info del dispositivo conectado al ViewModel
            return
        }
        val deviceName = socket.remoteDevice.name ?: socket.remoteDevice.address
        sendStatusBroadcast("Conectado a $deviceName")
        sendConnectedDeviceToViewModel(socket.remoteDevice) // Enviar info del dispositivo conectado al ViewModel
        Log.i("BluetoothService", "Conexión establecida con $deviceName")
    }


    // Hilo para leer datos (ConnectedThread)
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            Log.d("ConnectedThread", "Hilo iniciado para leer datos.")
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (mmSocket.isConnected) { // Usar mmSocket.isConnected para controlar el bucle
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer)
                    val receivedString = String(mmBuffer, 0, numBytes)
                    Log.d("ConnectedThread", "Datos recibidos: $receivedString")

                    // Parsear el receivedString para obtener ID, lat, lon
                    val parts = receivedString.trim().split(',')
                    if (parts.size >= 3) { // Asumiendo ID,LAT,LON y opcionalmente más datos
                        val id = parts[0].toIntOrNull()
                        val lat = parts[1].toDoubleOrNull()
                        val lon = parts[2].toDoubleOrNull()
                        // val timestamp = System.currentTimeMillis() // O si el ESP32 envía un timestamp

                        if (id != null && lat != null && lon != null) {
                            Log.d("ConnectedThread", "Datos parseados: ID=$id, Lat=$lat, Lon=$lon. Actualizando repositorio.")
                            // Actualizar el repositorio directamente
                            serviceScope.launch { // Usar el CoroutineScope del servicio
                                updateDeviceInRepository(id, lat, lon, System.currentTimeMillis())
                            }
                            // YA NO ES NECESARIO ENVIAR ACTION_DATA_RECEIVED al ViewModel si este no lo usa
                            // sendDataReceivedBroadcast(id, lat, lon, timestamp)
                        } else {
                            Log.w("ConnectedThread", "Error al parsear datos recibidos: $receivedString")
                        }
                    } else {
                        Log.w("ConnectedThread", "Formato de datos inesperado: $receivedString")
                    }
                } catch (e: IOException) {
                    Log.e("ConnectedThread", "Input stream fue desconectado o error de lectura.", e)
                    sendStatusBroadcast("Desconectado (Error de lectura)")
                    this@BluetoothService.connectionLost() // Notificar al servicio principal
                    break // Salir del bucle
                }
            }
            Log.d("ConnectedThread", "Hilo finalizado.")
        }

        fun cancel() {
            try {
                mmSocket.close()
                Log.d("ConnectedThread", "Socket cerrado.")
            } catch (e: IOException) {
                Log.e("ConnectedThread", "No se pudo cerrar el socket de conexión", e)
            }
        }
    }
    // --- Fin Hilos ---

    // Método para actualizar el repositorio (ahora dentro del servicio)
    private suspend fun updateDeviceInRepository(deviceId: Int, lat: Double, lon: Double, timestamp: Long) {
        try {
            val existingDevice = dispositivoRepository.getDispositivoByIdOnce(deviceId)
            if (existingDevice != null) {
                val updatedDevice = existingDevice.copy(
                        latitud = lat,
                        longitud = lon,
                        ultimaConexion = timestamp,
                        activo = true)
                dispositivoRepository.updateDispositivo(updatedDevice)
                Log.i("BluetoothService", "Dispositivo $deviceId actualizado en repositorio.")
            } else {
                val newDevice = Dispositivo(
                    id = deviceId,
                    nombre = "Dispositivo: $deviceId",
                    latitud = lat,
                    longitud = lon,
                    ultimaConexion = timestamp,
                    activo = true,
                    descripcion = "Sin descripción",
                )
                dispositivoRepository.insertDispositivo(newDevice)
                Log.i("BluetoothService", "Nuevo dispositivo $deviceId insertado en repositorio.")
            }
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error actualizando dispositivo en repositorio: ${e.message}", e)
        }
    }
    private fun sendConnectedDeviceToViewModel(device: BluetoothDevice) {
        val intent = Intent(ACTION_DEVICE_CONNECTED_INFO).apply {
            putExtra(EXTRA_DEVICE, device)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun sendDeviceFoundBroadcast(device: BluetoothDevice) {
        val intent = Intent(ACTION_DEVICE_FOUND).apply {
            putExtra(EXTRA_DEVICE, device)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendStatusBroadcast(status: String) {
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_STATUS, status)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDataReceivedBroadcast(id: String, lat: Double, lon: Double) {
        val intent = Intent(ACTION_DATA_RECEIVED).apply {
            putExtra(EXTRA_DATA_ID, id)
            putExtra(EXTRA_DATA_LAT, lat)
            putExtra(EXTRA_DATA_LON, lon)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Bluetooth Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    // Para actualizar la notificación del servicio en primer plano:
    private fun updateServiceNotification(statusText: String) {
        val notification = NotificationHelper.createBluetoothServiceNotification(this, statusText)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, notification)
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null // No estamos usando vinculación directa en este ejemplo, sino broadcasts
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoveryReceiver)
        disconnect() // Asegúrate de limpiar recursos
        Log.d("BluetoothService", "Servicio destruido.")
    }

    private fun disconnectAndStop() {
        connectedThread?.cancel()
        connectThread?.cancel() // En caso de que estuviera intentando conectar
        bluetoothSocket?.close() // Cierra el socket principal si está abierto
        bluetoothSocket = null
        sendStatusBroadcast("Desconectado")
        Log.i("BluetoothService", "Conexión Bluetooth cerrada y recursos liberados.")
        // Considera stopSelf() aquí si el servicio ya no debe seguir corriendo
        // stopSelf()
    }
    private fun connectionLost() {
        // Lógica para manejar la pérdida de conexión
        sendStatusBroadcast("Conexión perdida")
        // Podrías intentar reconectar o simplemente limpiar
        disconnectAndStop() // Limpiar recursos
    }
}