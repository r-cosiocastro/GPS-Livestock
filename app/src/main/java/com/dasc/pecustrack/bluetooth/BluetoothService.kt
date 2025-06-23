package com.dasc.pecustrack.bluetooth

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.utils.AppPreferences
import com.dasc.pecustrack.utils.NotificationHelper
import com.dasc.pecustrack.utils.parcelable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {

    @Inject
    lateinit var bluetoothStateManager: BluetoothStateManager

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Componentes para BLE
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bleScanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val connectedBleDevices =
        mutableMapOf<String, BluetoothGatt>() // Para manejar múltiples conexiones BLE si es necesario


    companion object {
        const val ACTION_START_BLE_SCAN = "com.dasc.pecustrack.ACTION_START_BLE_SCAN"
        const val ACTION_STOP_BLE_SCAN = "com.dasc.pecustrack.ACTION_STOP_BLE_SCAN"
        const val ACTION_CONNECT_BLE = "com.dasc.pecustrack.ACTION_CONNECT_BLE"
        const val ACTION_DISCONNECT_BLE = "com.dasc.pecustrack.ACTION_DISCONNECT_BLE"
        const val ACTION_BLE_DATA_AVAILABLE = "com.dasc.pecustrack.ACTION_BLE_DATA_AVAILABLE"

        const val EXTRA_BLE_CHARACTERISTIC_UUID =
            "com.dasc.pecustrack.EXTRA_BLE_CHARACTERISTIC_UUID"
        const val EXTRA_BLE_DATA_VALUE = "com.dasc.pecustrack.EXTRA_BLE_DATA_VALUE"
        const val EXTRA_BLE_DATA_STRING = "com.dasc.pecustrack.EXTRA_BLE_DATA_STRING"

        const val ACTION_RECONNECT_ATTEMPTING = "com.dasc.pecustrack.ACTION_RECONNECT_ATTEMPTING"
        const val ACTION_CONNECTION_SUCCESSFUL = "com.dasc.pecustrack.ACTION_CONNECTION_SUCCESSFUL"
        const val ACTION_CONNECTION_FAILED = "com.dasc.pecustrack.ACTION_CONNECTION_FAILED"
        const val EXTRA_DEVICE_NAME = "com.dasc.pecustrack.EXTRA_DEVICE_NAME"
        const val ACTION_REQUEST_CURRENT_STATUS =
            "com.dasc.pecustrack.ACTION_REQUEST_CURRENT_STATUS"


        const val ACTION_START_SCAN = "com.dasc.pecustrack.ACTION_START_SCAN"
        const val ACTION_STOP_SCAN = "com.dasc.pecustrack.ACTION_STOP_SCAN"
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

        const val EXTRA_ERROR_MESSAGE = "com.dasc.pecustrack.EXTRA_ERROR_MESSAGE"
        const val ACTION_DEVICE_DISCONNECTED = "com.dasc.pecustrack.ACTION_DEVICE_DISCONNECTED"

        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1

        private const val SERVICE_UUID_STRING = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
        private const val CHARACTERISTIC_UUID_STRING = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // UUID del Client Characteristic Configuration Descriptor

    }

    private var isAttemptingAutoReconnect: Boolean = false
    private var lastAttemptedDeviceNameForToast: String? = null
    private var lastAttemptedDeviceAddressForAutoReconnect: String? =
        null // Para saber qué dispositivo estamos reconectando

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 segundos
    private var isScanning = false

    // Inyectar el repositorio
    @Inject
    lateinit var dispositivoRepository: DispositivoRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper // Asumiendo que también lo inyectas o lo tienes como object

    // CoroutineScope para operaciones del repositorio desde el servicio
    private val serviceJob = SupervisorJob()
    private val serviceScope =
        CoroutineScope(Dispatchers.IO + serviceJob) // Usa Dispatchers.IO para DB ops


    // Para escaneo BLE
    private lateinit var bleScanner: BluetoothLeScanner
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            // Anteriormente, podrías haber enviado un broadcast aquí por cada dispositivo
            // Ahora, actualizaremos una lista y la enviaremos, o actualizaremos StateManager de otra forma.

            // Lógica para añadir a una lista temporal y luego postearla:
            if (!discoveredDevices.map { it.address }.contains(device.address)) {
                Log.d("BluetoothService_SCAN", "Dispositivo encontrado: ${device.name ?: "Desconocido"} (${device.address})")
                discoveredDevices.add(device)
                // Actualizar StateManager con la lista completa (o una copia)
                bluetoothStateManager.postScanResults(ArrayList(discoveredDevices)) // Envía una copia
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            Log.d("BluetoothService_SCAN", "Resultados de escaneo por lotes recibidos: ${results.size} dispositivos")
            results.forEach { result ->
                val device = result.device
                if (!discoveredDevices.map { it.address }.contains(device.address)) {
                    discoveredDevices.add(device)
                }
            }
            bluetoothStateManager.postScanResults(ArrayList(discoveredDevices)) // Envía una copia
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BluetoothService_SCAN", "Fallo en el escaneo BLE: $errorCode")
            isScanning = false
            // Anteriormente: sendBroadcast(Intent(ACTION_SCAN_FAILED).putExtra(EXTRA_SCAN_ERROR_CODE, errorCode))
            bluetoothStateManager.postScanFailed(errorCode)
            bluetoothStateManager.postScanStopped() // También indica que se detuvo
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun initBleScanCallback() {
        bleScanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) // Necesario para device.name
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val device = result.device
                Log.d(
                    "BluetoothService_BLE",
                    "Dispositivo BLE encontrado: ${device.name ?: device.address} RSSI: ${result.rssi}"
                )
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                for (result in results) {
                    val device = result.device
                    Log.d(
                        "BluetoothService_BLE",
                        "Dispositivo BLE (batch) encontrado: ${device.name ?: device.address}"
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BluetoothService_BLE", "Error en el escaneo BLE: $errorCode")
            }
        }
    }

    // Función para enviar el estado actual
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCurrentConnectionStatus() {
        val currentDevice = bluetoothGatt?.device // Obtener el dispositivo del GATT activo
        if (currentDevice != null && bluetoothGatt?.connect() == true /*verificar si está realmente conectado, esto puede no ser suficiente*/) {
            // Para una verificación más robusta del estado conectado:
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val connectedDevicesList = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            if (connectedDevicesList.any { it.address == currentDevice.address }) {
                sendDeviceConnectedInfoBroadcast(
                    currentDevice,
                    currentDevice.name ?: currentDevice.address
                )
            } else {
                sendDeviceDisconnectedBroadcast() // Envía un disconnect genérico si no hay nada
            }
        } else {
            sendDeviceDisconnectedBroadcast() // Envía un disconnect genérico si no hay nada
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled) { // Solo inicializar si el BT está ON
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        }
        Log.d("BluetoothService_LIFECYCLE", "Servicio Creado")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BluetoothService", "onStartCommand: Servicio iniciado con intent: $intent")
        val notificationText = "Servicio Bluetooth en ejecución..."
        val notification =
            NotificationHelper.createBluetoothServiceNotification(this, notificationText)
        startForeground(NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, notification)

        if (intent?.action == null && !isServiceConnectedToDevice()) { // Si el servicio se inicia sin acción y no hay conexión activa
            val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
            val lastDeviceAddress =
                sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS, null)


            if (lastDeviceAddress != null) {
                var nameForInitialToast =
                    sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, null)
                if (nameForInitialToast == null) { // Si no hay nombre guardado, intenta obtenerlo del sistema (puede ser null)
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
                            nameForInitialToast =
                                device.name ?: lastDeviceAddress // Fallback a la MAC
                        } catch (e: IllegalArgumentException) {
                            nameForInitialToast = lastDeviceAddress // Fallback a la MAC
                        }
                    } else {
                        nameForInitialToast =
                            lastDeviceAddress // Fallback a la MAC si no hay permiso
                    }
                }

                isAttemptingAutoReconnect = true
                lastAttemptedDeviceAddressForAutoReconnect = lastDeviceAddress
                sendReconnectAttemptingBroadcast(nameForInitialToast)
                attemptAutoReconnectToDevice(lastDeviceAddress)
            }
        }

        when (intent?.action) {

            ACTION_START_BLE_SCAN -> {
                Log.d("BluetoothService", "Comando recibido: ACTION_START_BLE_SCAN")
                startBleScan()
            }

            ACTION_STOP_BLE_SCAN -> {
                Log.d("BluetoothService", "Comando recibido: ACTION_STOP_SCAN")
                stopBleScan()
            }

            ACTION_CONNECT_BLE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                Log.d("BluetoothService", "Comando recibido: ACTION_CONNECT_BLE a $address")
                isAttemptingAutoReconnect = false // Conexión manual
                address?.let {
                    val device = bluetoothAdapter.getRemoteDevice(it)
                    connectToBleDevice(device.address) // Tu función para conectar (autoConnect=false para conexiones nuevas)
                }
            }

            ACTION_DISCONNECT_BLE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                Log.d("BluetoothService", "Comando recibido: ACTION_DISCONNECT_BLE de $address")
                address?.let {
                    disconnectFromBleDevice(it)
                }
            }

            ACTION_REQUEST_CURRENT_STATUS -> {
                Log.d("BluetoothService_DEBUG", "Recibida ACTION_REQUEST_CURRENT_STATUS.")
                if (bluetoothGatt != null && isServiceConnectedToDevice()) { // isServiceConnectedToDevice() es una función que debes tener para verificar el estado real
                    val device = bluetoothGatt!!.device // ¡NO USAR '!!' EN PRODUCCIÓN, verificar nulidad!

                    // ****** ESTA ES LA PARTE CRÍTICA ******
                    // NO HAGAS ESTO DIRECTAMENTE:
                    // val problematicDisplayName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    // device.name ?: device.address
                    // } else {
                    // device.address
                    // }
                    // sendDeviceConnectedInfoBroadcast(device, problematicDisplayName)

                    // EN SU LUGAR, USA LA LÓGICA CORRECTA:
                    val savedName = getSavedDeviceName(device.address)
                    val actualHardwareName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) device.name else null

                    var displayNameForStatusUpdate: String
                    if (savedName != null) {
                        displayNameForStatusUpdate = savedName
                        if (actualHardwareName != null && actualHardwareName != savedName) {
                            displayNameForStatusUpdate = actualHardwareName // Prefiere el hardware si es diferente y no nulo
                        }
                    } else {
                        displayNameForStatusUpdate = actualHardwareName ?: device.address
                    }
                    Log.d("BluetoothService_DEBUG", "ACTION_REQUEST_CURRENT_STATUS: Enviando info con displayName = '$displayNameForStatusUpdate'")
                    sendDeviceConnectedInfoBroadcast(device, displayNameForStatusUpdate)

                } else {
                    sendDeviceDisconnectedBroadcast() // O un estado que indique "no conectado"
                }

            }
        }
        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun attemptAutoReconnectToDevice(deviceAddress: String) {
        if (!bluetoothAdapter.isEnabled) {
            sendConnectionFailedBroadcast(
                lastAttemptedDeviceNameForToast,
                "Bluetooth no habilitado."
            )
            isAttemptingAutoReconnect = false
            return
        }
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            Log.i(
                "BluetoothService_BLE",
                "Intentando reconexión automática a: ${device.name ?: device.address}"
            )
            // Usar autoConnect = true para reconexiones
            bluetoothGatt = device.connectGatt(this, true, gattCallback)
        } catch (e: IllegalArgumentException) {
            Log.e(
                "BluetoothService_BLE",
                "Dirección MAC guardada inválida para reconexión: $deviceAddress",
                e
            )
            sendConnectionFailedBroadcast(
                lastAttemptedDeviceNameForToast,
                "Dirección Bluetooth inválida."
            )
            clearLastConnectedDeviceAddress()
            isAttemptingAutoReconnect = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) // o manejar internamente la verificación de permisos para device.name
    private fun determineDisplayName(device: BluetoothDevice): String {
        val deviceAddress = device.address
        // La obtención del nombre del hardware necesita permiso BLUETOOTH_CONNECT
        // Asegúrate de que este permiso se verifica antes de acceder a device.name o que se maneja la SecurityException
        var actualDeviceNameFromHardware: String? = null
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            actualDeviceNameFromHardware = device.name // Puede ser null
        } else {
            Log.w("BluetoothService_NAME", "determineDisplayName: Permiso BLUETOOTH_CONNECT denegado. No se puede acceder a device.name.")
        }

        val savedName = getSavedDeviceName(deviceAddress)
        var displayName: String

        Log.d("BluetoothService_NAME", "determineDisplayName: HardwareName='${actualDeviceNameFromHardware}', SavedName='${savedName}' for $deviceAddress")

        if (savedName != null) {
            displayName = savedName
            if (actualDeviceNameFromHardware != null && actualDeviceNameFromHardware != savedName) {
                displayName = actualDeviceNameFromHardware // Prefiere el hardware si es más nuevo/diferente
            }
        } else {
            displayName = actualDeviceNameFromHardware ?: deviceAddress
        }
        return displayName
    }

    // Modifica handleDeviceNameLogicAndBroadcasts para usar determineDisplayName
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDeviceNameLogicAndBroadcasts(gatt: BluetoothGatt, connectionContext: String) {
        val device = gatt.device
        val deviceAddress = device.address
        // val actualDeviceNameFromHardware = device.name // Ya no se obtiene directamente aquí
        // val savedName = getSavedDeviceName(deviceAddress) // Ya no se obtiene directamente aquí

        // Determinar el nombre a usar para la UI y potencialmente para guardar
        val displayNameForUI = determineDisplayName(device)

        // Lógica de guardado (si el nombre del hardware es nuevo y diferente del guardado, o si no había guardado)
        var actualDeviceNameFromHardwareForSave: String? = null
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            actualDeviceNameFromHardwareForSave = device.name
        }

        val currentSavedName = getSavedDeviceName(deviceAddress) // Obtener para comparar

        if (actualDeviceNameFromHardwareForSave != null && actualDeviceNameFromHardwareForSave != currentSavedName) {
            saveDeviceName(deviceAddress, actualDeviceNameFromHardwareForSave)
            Log.d("BluetoothService_NAME", "$connectionContext: HardwareName ('$actualDeviceNameFromHardwareForSave') es nuevo/diferente. Guardado.")
        } else if (currentSavedName == null && actualDeviceNameFromHardwareForSave != null) {
            saveDeviceName(deviceAddress, actualDeviceNameFromHardwareForSave)
            Log.d("BluetoothService_NAME", "$connectionContext: No había SavedName, HardwareName ('$actualDeviceNameFromHardwareForSave') guardado.")
        }
        // No sobrescribir un nombre bueno con null

        Log.d("BluetoothService_DEBUG", "$connectionContext: Usando displayNameForUI = '$displayNameForUI'")

        sendConnectionSuccessfulBroadcast(gatt, displayNameForUI)
        sendDeviceConnectedInfoBroadcast(device, displayNameForUI)

        if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
            // ... (lógica de resetear flags de reconexión) ...
            if (connectionContext == "onDescriptorWrite" || (connectionContext == "onServicesDiscovered" /* && no usas onDescriptorWrite como punto final */)) {
                isAttemptingAutoReconnect = false
                lastAttemptedDeviceAddressForAutoReconnect = null
            }
        }
    }

    private fun getSavedDeviceName(deviceAddress: String): String? {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, null)
    }

    // Función para guardar el nombre en SharedPreferences
    private fun saveDeviceName(deviceAddress: String, name: String?) {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit {
            putString(
                AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS,
                deviceAddress
            ) // Guardar/actualizar siempre la MAC
            putString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, name)
        }
        Log.i("BluetoothService_NAME", "Nombre guardado para $deviceAddress: '$name'")
    }

    private fun isServiceConnectedToDevice(): Boolean {
        // Implementa una lógica para saber si ya hay una conexión GATT activa y válida
        return bluetoothGatt != null && connectedBleDevices.isNotEmpty() // Simplificado
    }

    private fun clearLastConnectedDeviceAddress() {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit {
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS)
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME)
        }
        Log.i("BluetoothService_BLE", "Dirección del último dispositivo conectado eliminada.")
    }

    private fun sendReconnectAttemptingBroadcast(deviceNameForToast: String) {
        // val intent = Intent(ACTION_RECONNECT_ATTEMPTING).putExtra(EXTRA_DEVICE_NAME, deviceNameForToast)
        // LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        bluetoothStateManager.postReconnectAttempting(deviceNameForToast)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendConnectionSuccessfulBroadcast(
        gatt: BluetoothGatt,
        displayName: String
    ) { // displayName ya está resuelto
        Log.d(
            "BluetoothService_DEBUG",
            "sendConnectionSuccessfulBroadcast: Usando displayName = '$displayName'"
        )
        // val intent = Intent(ACTION_CONNECTION_SUCCESSFUL).putExtra(EXTRA_DEVICE_NAME, displayName)
        // LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        bluetoothStateManager.postConnectionSuccessful(displayName, gatt.device)
    }

    private fun sendConnectionFailedBroadcast(deviceName: String?, errorMessage: String?) {
        // val intent = Intent(ACTION_CONNECTION_FAILED)
//        deviceName?.let { intent.putExtra(EXTRA_DEVICE_NAME, it) }
//        errorMessage?.let { intent.putExtra(EXTRA_ERROR_MESSAGE, it) } // Define EXTRA_ERROR_MESSAGE
//        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        val nameForMsg = deviceName ?: "Dispositivo"
        val errorMsg = errorMessage ?: "Error desconocido"
        bluetoothStateManager.postConnectionFailed(nameForMsg, errorMsg)
    }

    private fun sendDeviceDisconnectedBroadcast(deviceName: String? = null) {
        val nameToShow = deviceName ?: "Dispositivo" // O manejar null de otra forma
        Log.d("BluetoothService_DEBUG", "sendDeviceDisconnectedBroadcast: Para '$nameToShow'")
//        val intent = Intent(ACTION_DEVICE_DISCONNECTED)
//        deviceName?.let { intent.putExtra(EXTRA_DEVICE_NAME, it) }
//        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        bluetoothStateManager.postDeviceDisconnected(nameToShow)
    }

    // Si mantienes la lógica separada para ACTION_DEVICE_CONNECTED_INFO
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendDeviceConnectedInfoBroadcast(device: BluetoothDevice, displayName: String) {
        Log.d("BluetoothService_DEBUG", "sendDeviceConnectedInfoBroadcast: Usando displayName = '$displayName', device.address = '${device.address}'")
        // val intent = Intent(ACTION_DEVICE_CONNECTED_INFO).apply {
        //     putExtra(EXTRA_DEVICE, device) // El objeto BluetoothDevice original
        //     putExtra(EXTRA_DEVICE_NAME, displayName) // El nombre resuelto que la UI debe usar
        // }
        // LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        bluetoothStateManager.postDeviceConnectedInfo(device, displayName)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]) // Ajusta según necesidad
    private fun startBleScan() {
        if (!bluetoothAdapter.isEnabled) {
            Log.w("BluetoothService_SCAN", "Bluetooth no está habilitado. No se puede escanear.")
            // Podrías enviar un evento de "Bluetooth no habilitado"
            return
        }

        if (isScanning) {
            Log.d("BluetoothService_SCAN", "El escaneo ya está en progreso.")
            return
        }

        // Limpia la lista de dispositivos descubiertos antes de un nuevo escaneo
        discoveredDevices.clear()
        bluetoothStateManager.postScanResults(emptyList()) // Notifica que la lista está vacía al inicio

        val scanFilters = listOf(ScanFilter.Builder().build()) // Ajusta tus filtros
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.i("BluetoothService_SCAN", "Iniciando escaneo BLE...")
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        isScanning = true

        // Programar la detención del escaneo después de un tiempo
        scanHandler.postDelayed({
            if (isScanning) {
                stopBleScan()
            }
        }, SCAN_PERIOD)
    }

    // En tu función stopBleScan()
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBleScan() {
        if (isScanning) {
            Log.i("BluetoothService_SCAN", "Deteniendo escaneo BLE.")
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            // Anteriormente: sendBroadcast(Intent(ACTION_SCAN_STOPPED))
            bluetoothStateManager.postScanStopped()
        }
        scanHandler.removeCallbacksAndMessages(null) // Limpia cualquier callback pendiente para detener el escaneo
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun connectToBleDevice(deviceAddress: String) {
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(
                "BluetoothService_BLE",
                "Dispositivo BLE no encontrado con la dirección: $deviceAddress"
            )
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("BluetoothService_BLE", "Permiso BLUETOOTH_CONNECT denegado, no se puede conectar a GATT.")
            return
        }

        // Detener escaneo BLE antes de conectar para ahorrar recursos
        stopBleScan()

        Log.d("BluetoothService_BLE", "Intentando conectar al dispositivo GATT: ${device.address}")

        // Cierra conexiones previas a este mismo dispositivo si existen
        connectedBleDevices[deviceAddress]?.close()

        // El parámetro autoConnect a false significa que la conexión no persistirá si se pierde.
        // Si lo pones a true, el sistema intentará reconectar automáticamente cuando el dispositivo esté disponible.
        bluetoothGatt = device.connectGatt(this, true, gattCallback)
        // También puedes usar:
        // bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectFromBleDevice(deviceAddress: String) {
        val gatt = connectedBleDevices[deviceAddress]
        if (gatt == null) {
            Log.w("BluetoothService_BLE", "No hay conexión GATT para desconectar de $deviceAddress")
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "BluetoothService_BLE",
                "Permiso BLUETOOTH_CONNECT denegado, no se puede desconectar GATT."
            )
            return
        }
        Log.d("BluetoothService_BLE", "Desconectando de GATT ${gatt.device.address}")
        gatt.disconnect()
        // La desconexión real se confirma en onConnectionStateChange, donde debes llamar a gatt.close()
    }

    // Callback para eventos GATT
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BluetoothService_BLE", "Conectado al GATT server de $deviceAddress.")
                    // Aquí es donde MapsActivity ya no debería mostrar "Reconectando..."
                    // El descubrimiento de servicios determinará el éxito final
                    connectedBleDevices[deviceAddress] = gatt
                    bluetoothGatt = gatt // Asignar el gatt globalmente
                    // Iniciar descubrimiento de servicios
                    gatt.discoverServices()


                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val disconnectedDeviceName =
                        gatt.device.name ?: deviceAddress // Mejor obtenerlo aquí si está disponible
                    Log.i(
                        "BluetoothService_BLE",
                        "Desconectado del GATT server de $disconnectedDeviceName."
                    )
                    closeGattConnection(deviceAddress)
                    if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                        // Si la reconexión activa falló, notifica con el nombre que estabas intentando reconectar (de SharedPreferences)
                        val prefsName =
                            getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
                                .getString(
                                    AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME,
                                    deviceAddress
                                )
                        sendConnectionFailedBroadcast(prefsName, "Fallo en la reconexión.")
                    } else {
                        sendDeviceDisconnectedBroadcast(disconnectedDeviceName)
                    }
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
            } else { // Error en la conexión
                Log.w("BluetoothService_BLE", "Error GATT en conexión con $deviceAddress: $status")
                val errorDeviceName = gatt.device.name ?: deviceAddress
                closeGattConnection(deviceAddress)
                if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                    val prefsName =
                        getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
                            .getString(
                                AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME,
                                deviceAddress
                            )
                    sendConnectionFailedBroadcast(prefsName, "Fallo en conexión GATT ($status).")
                } else {
                    sendConnectionFailedBroadcast(
                        errorDeviceName,
                        "Fallo en conexión GATT ($status)."
                    )
                }
                isAttemptingAutoReconnect = false
                lastAttemptedDeviceAddressForAutoReconnect = null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun closeGattConnection(string: String) {
            val gatt = connectedBleDevices.remove(string)
            if (gatt != null) {
                Log.d("BluetoothService_BLE", "Cerrando conexión GATT para $string")
                gatt.close()
                if (bluetoothGatt == gatt) {
                    bluetoothGatt = null
                }
            } else {
                Log.w("BluetoothService_BLE", "No se encontró conexión GATT para cerrar: $string")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val device = gatt.device
            val currentDeviceName = device.name
            Log.e("BluetoothService_BLE", "onServicesDiscovered llamado para: $currentDeviceName")
            val displayName = currentDeviceName ?: device.address
            val deviceAddress = device.address
            val tempInitialName = getSavedDeviceName(deviceAddress) ?: device.name ?: deviceAddress
            val displayNameForLog = currentDeviceName ?: deviceAddress
            Log.d(
                "BluetoothService_DEBUG",
                "onServicesDiscovered: device.name = '$currentDeviceName', device.address = '$deviceAddress'"
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothService_BLE", "Servicios descubiertos para $displayNameForLog")
                // broadcastGattServices(gatt.device.address, gatt.services)
                // Aquí puedes leer/escribir características o suscribirte a notificaciones
                // Ejemplo: readCharacteristic(gatt, YOUR_SERVICE_UUID, YOUR_CHARACTERISTIC_UUID)
                // Ejemplo: enableNotifications(gatt, YOUR_SERVICE_UUID, YOUR_NOTIFY_CHARACTERISTIC_UUID)

                val serviceUuid = UUID.fromString(SERVICE_UUID_STRING)
                val characteristicUuid = UUID.fromString(CHARACTERISTIC_UUID_STRING)

                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Servicio $SERVICE_UUID_STRING no encontrado en $displayName"
                    )
                    // Considera desconectar o informar error
                    return
                }

                val characteristic = service.getCharacteristic(characteristicUuid)
                if (characteristic == null) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Característica $CHARACTERISTIC_UUID_STRING no encontrada en $displayName"
                    )
                    // Considera desconectar o informar error
                    return
                }

                // 1. Habilitar notificaciones localmente en el cliente GATT
                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    Log.i(
                        "BluetoothService_BLE",
                        "Notificaciones habilitadas localmente para ${characteristic.uuid}"
                    )

                    // 2. Escribir en el descriptor CCCD para que el servidor envíe notificaciones
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        val writeSuccess: Boolean
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val result = gatt.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                            Log.d(
                                "BluetoothService_BLE",
                                "Escribiendo en descriptor CCCD (API 33+), resultado: $result"
                            )
                            // El resultado de la escritura se confirma en onDescriptorWrite
                        } else {
                            // Para versiones anteriores
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            writeSuccess = gatt.writeDescriptor(descriptor)
                            if (writeSuccess) {
                                Log.d(
                                    "BluetoothService_BLE",
                                    "Escritura en descriptor CCCD (API <33) iniciada..."
                                )
                            } else {
                                Log.e(
                                    "BluetoothService_BLE",
                                    "Fallo al iniciar escritura en descriptor CCCD (API <33)"
                                )
                                // Si la escritura falla aquí, entonces la conexión NO está completa.
                                val bestName = getSavedDeviceName(deviceAddress) ?: device.name
                                ?: deviceAddress
                                sendConnectionFailedBroadcast(
                                    bestName,
                                    "Fallo al habilitar notificaciones (escritura desc)."
                                )
                                closeGattConnection(deviceAddress)
                                if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                                    isAttemptingAutoReconnect = false
                                    lastAttemptedDeviceAddressForAutoReconnect = null
                                }
                                return // Salir aquí porque la configuración falló
                            }
                        }
                    } else {
                        Log.e(
                            "BluetoothService_BLE",
                            "Descriptor CCCD no encontrado para ${characteristic.uuid}"
                        )
                    }
                } else {
                    Log.e(
                        "BluetoothService_BLE",
                        "Fallo al habilitar notificaciones localmente para ${characteristic.uuid}"
                    )
                }
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "onServicesDiscovered recibió: $status para $tempInitialName"
                )
                sendConnectionFailedBroadcast(
                    tempInitialName,
                    "Fallo al descubrir servicios ($status)."
                )
                closeGattConnection(deviceAddress)
                if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (gatt != null && status == BluetoothGatt.GATT_SUCCESS) {
                if (CCCD_UUID == descriptor?.uuid) { // Asegúrate que este es tu descriptor esperado
                    Log.i(
                        "BluetoothService_BLE",
                        "Notificaciones habilitadas. Conexión completamente establecida."
                    )
                    handleDeviceNameLogicAndBroadcasts(gatt, "onDescriptorWrite")
                } else {
                    // Escribiste a otro descriptor exitosamente, maneja si es necesario
                    Log.i(
                        "BluetoothService_BLE",
                        "Descriptor ${descriptor?.uuid} escrito exitosamente (no CCCD)."
                    )
                }
            } else if (gatt != null) {
                val device = gatt.device
                val bestName = getSavedDeviceName(device.address) ?: device.name ?: device.address
                Log.w(
                    "BluetoothService_BLE",
                    "Error al escribir descriptor para $bestName: $status"
                )
                sendConnectionFailedBroadcast(
                    bestName,
                    "Fallo al configurar notificaciones (desc write $status)."
                )
                if (isAttemptingAutoReconnect && device.address == lastAttemptedDeviceAddressForAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
                closeGattConnection(device.address)
            } else { // gatt es null, lo cual es muy raro aquí si status no es SUCCESS
                Log.w(
                    "BluetoothService_BLE",
                    "Error al escribir descriptor: GATT es null, status $status"
                )
                // No podemos obtener un nombre de dispositivo fácilmente aquí.
                // Si tienes lastAttemptedDeviceAddressForAutoReconnect, podrías usarlo.
                val deviceAddressForError =
                    lastAttemptedDeviceAddressForAutoReconnect ?: "Dispositivo desconocido"
                // Tratar de obtener el nombre guardado para esa dirección si existe
                val nameForError = if (lastAttemptedDeviceAddressForAutoReconnect != null) {
                    getSavedDeviceName(lastAttemptedDeviceAddressForAutoReconnect!!)
                } else {
                    null
                } ?: deviceAddressForError
                sendConnectionFailedBroadcast(
                    nameForError,
                    "Error crítico al configurar notificaciones (gatt null)."
                )
                if (isAttemptingAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
                // No se puede llamar a closeGattConnection(gatt.device.address) porque gatt es null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray // El nuevo formato para Android 13+
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            //Log.d("BluetoothService_BLE", "onCharacteristicChanged (API 33+) from ${characteristic.uuid}")
            handleCharacteristicChanged(gatt.device.address, characteristic.uuid.toString(), value)
        }

        // Para APIs < 33, se usa este:
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            // Solo llama a este si la versión es menor a Tiramsu para evitar doble procesamiento
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d(
                    "BluetoothService_BLE",
                    "onCharacteristicChanged (API <33) from ${characteristic.uuid}"
                )
                // El valor está en characteristic.value
                handleCharacteristicChanged(
                    gatt.device.address,
                    characteristic.uuid.toString(),
                    characteristic.value
                )
            }
        }

        private fun handleCharacteristicChanged(
            deviceAddress: String,
            charUuid: String,
            value: ByteArray
        ) {
            val dataString =
                String(value, Charsets.UTF_8).trim() // trim() para quitar espacios extra
            //Log.i("BluetoothService_BLE", "Dato GPS simulado recibido de $deviceAddress / $charUuid: '$dataString'")

            // Parsear el string "id,lat,lon"
            val parts =
                dataString.split(',').map { it.trim() } // Dividir por coma y quitar espacios
            if (parts.size == 3) {
                try {
                    val idDispositivo = parts[0].toInt()
                    val latitud = parts[1].toDouble()
                    val longitud = parts[2].toDouble()

                    //Log.d("BluetoothService_BLE", "Parseado: ID=$idDispositivo, Lat=$latitud, Lon=$longitud")

                    // Crear o actualizar tu modelo Dispositivo
                    // Necesitarás obtener el nombre del dispositivo si lo tienes, o usar la dirección MAC
                    // Aquí estoy asumiendo que quieres actualizar un dispositivo existente en tu BD
                    // basado en su ID único que viene del ESP32 y su dirección MAC (que es la del ESP32 conectado)

                    serviceScope.launch {
                        // Primero, intenta obtener el dispositivo por su ID único que envías (e.g., "2", "4")
                        // O quizás mejor, el dispositivo conectado actualmente tiene una dirección MAC (deviceAddress)
                        // y los datos que recibes (idDispositivo) son un IDENTIFICADOR INTERNO de ese dispositivo físico.

                        // Opción 1: Actualizar el dispositivo físico conectado con los nuevos datos de ID, Lat, Lon.
                        // Esto es si CADA ESP32 tiene un ID interno que envían.
                        // Aquí, 'deviceAddress' es la MAC del ESP32 conectado.
                        // 'idDispositivo' es el ID lógico que el ESP32 reporta (2, 4, 7, etc.)
                        // Esto se complica si un solo ESP32 reporta datos para MÚLTIPLES IDs lógicos.

                        // Por ahora, vamos a ASUMIR que el ID que envías ("2", "4") es el ID
                        // de un Dispositivo que YA EXISTE en tu base de datos y quieres actualizar su ubicación.
                        // Y que 'deviceAddress' es la MAC del ESP32 que está enviando estos datos.

                        // Si tu modelo Dispositivo tiene una PK autogenerada por Room y el 'id'
                        // que envías es un campo separado, la lógica de guardado cambiaría.
                        // ASUMIREMOS que el 'id' que viene del ESP32 ES la PrimaryKey en tu tabla Dispositivo.

                        var dispositivo =
                            dispositivoRepository.getDispositivoByIdOnce(idDispositivo)

                        if (dispositivo == null) {
                            // Si no existe un dispositivo con ese ID y MAC, lo creamos.
                            // Esto asume que el ID que manda el ESP32 es único para ese tipo de sensor
                            // y la MAC identifica al ESP32 que lo tiene.
                            dispositivo = Dispositivo(
                                id = idDispositivo,
                                nombre = "Dispositivo $idDispositivo",
                                descripcion = "Sin descripción",
                                latitud = latitud,
                                longitud = longitud,
                                ultimaConexion = System.currentTimeMillis(),
                                tipoAnimal = 0,
                                activo = true
                            )
                            dispositivoRepository.insertDispositivo(dispositivo)
                            Log.i(
                                "BluetoothService_BLE",
                                "Nuevo Dispositivo creado y guardado: $dispositivo"
                            )
                            // Notificar a la UI sobre nuevo dispositivo
                            sendNewDeviceDataNotification(
                                dispositivo,
                                "Nuevo dispositivo detectado"
                            )

                        } else {
                            // Actualizar el dispositivo existente con la nueva ubicación
                            dispositivo = dispositivo.copy(
                                latitud = latitud,
                                longitud = longitud,
                                ultimaConexion = System.currentTimeMillis(),
                                activo = true
                            )

                            dispositivoRepository.updateDispositivo(dispositivo)
                            //Log.i("BluetoothService_BLE", "Dispositivo actualizado: $dispositivo")
                            // Notificar a la UI sobre actualización
                            // sendNewDeviceDataNotification(dispositivo, "Ubicación actualizada")
                        }
                    }

                } catch (e: NumberFormatException) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Error al parsear datos de ubicación: '$dataString'",
                        e
                    )
                }
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "Formato de datos de ubicación inesperado: '$dataString'"
                )
            }
        }

        // Necesitarás añadir esto al servicio para enviar notificaciones al usuario
        // (notificaciones del sistema Android, no notificaciones BLE)
        private fun sendNewDeviceDataNotification(dispositivo: Dispositivo, message: String) {
            val notificationTitle =
                "PecusTrack: ${dispositivo.nombre ?: "Dispositivo ${dispositivo.id}"}"
            val notificationText = "$message (${dispositivo.latitud}, ${dispositivo.longitud})"
            val notification = NotificationHelper.createBasicNotification(
                this@BluetoothService,
                notificationTitle,
                notificationText,
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_ID
            )
            notificationHelper.showNotification(
                this@BluetoothService,
                NOTIFICATION_ID,
                notification
            )
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val deviceName = gatt.device.name ?: gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(
                    "BluetoothService_BLE",
                    "Característica leída de $deviceName ${characteristic.uuid}: ${value.toHexString()}"
                )
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "Fallo al leer característica de $deviceName ${characteristic.uuid}, status: $status"
                )
            }
        }

        // Para APIs < 33, se usa:
        // override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        //    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        //        val value = characteristic.value
        //        // ... manejar como arriba
        //    }
        // }

    }

    // Helper para convertir ByteArray a HexString para logging
    fun ByteArray.toHexString(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBleCharacteristic(deviceAddress: String, serviceUuid: UUID, characteristicUuid: UUID) {
        val gatt = connectedBleDevices[deviceAddress]
        if (gatt == null) {
            Log.w("BluetoothService_BLE", "No hay conexión GATT para leer de $deviceAddress")
            return
        }
        val service = gatt.getService(serviceUuid)
        if (service == null) {
            Log.w("BluetoothService_BLE", "Servicio $serviceUuid no encontrado en $deviceAddress")
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(
                "BluetoothService_BLE",
                "Característica $characteristicUuid no encontrada en servicio $serviceUuid de $deviceAddress"
            )
            return
        }
        if (!gatt.readCharacteristic(characteristic)) {
            Log.w(
                "BluetoothService_BLE",
                "Fallo al iniciar lectura de característica ${characteristic.uuid} de $deviceAddress"
            )
        } else {
            Log.d(
                "BluetoothService_BLE",
                "Iniciando lectura de característica ${characteristic.uuid} de $deviceAddress"
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeBleCharacteristic(
        deviceAddress: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray
    ) {
        val gatt = connectedBleDevices[deviceAddress]
        // ... (verificaciones similares a readBleCharacteristic) ...
        val characteristic = gatt?.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w("BluetoothService_BLE", "Característica para escribir no encontrada.")
            return
        }

        characteristic.value = data
        // El tipo de escritura depende de lo que el periférico soporte (con o sin respuesta)
        characteristic.writeType =
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // O WRITE_TYPE_NO_RESPONSE

        gatt?.writeCharacteristic(characteristic)?.let {
            if (!it) {
                Log.w(
                    "BluetoothService_BLE",
                    "Fallo al iniciar escritura de característica ${characteristic.uuid} en $deviceAddress"
                )
            } else {
                Log.d(
                    "BluetoothService_BLE",
                    "Iniciando escritura de característica ${characteristic.uuid} en $deviceAddress"
                )
            }
        }
    }


    // UUID para el Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun setBleCharacteristicNotification(
        deviceAddress: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        enable: Boolean
    ) {
        val gatt = connectedBleDevices[deviceAddress]
        // ... (verificaciones similares a readBleCharacteristic) ...
        val characteristic = gatt?.getService(serviceUuid)?.getCharacteristic(characteristicUuid)

        if (gatt == null || characteristic == null) {
            Log.w(
                "BluetoothService_BLE",
                "GATT o Característica no encontrada para notificaciones."
            )
            return
        }

        // Habilitar notificaciones/indicaciones localmente
        if (!gatt.setCharacteristicNotification(characteristic, enable)) {
            Log.e(
                "BluetoothService_BLE",
                "Fallo al habilitar/deshabilitar setCharacteristicNotification para ${characteristic.uuid}"
            )
            return
        }

        // Escribir en el CCCD del periférico para habilitar/deshabilitar notificaciones/indicaciones
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e("BluetoothService_BLE", "CCCD no encontrado para ${characteristic.uuid}")
            return
        }

        val payload = when {
            enable && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            enable && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "BluetoothService_BLE",
                    "La característica ${characteristic.uuid} no soporta ni notificación ni indicación."
                )
                return
            }
        }
        descriptor.value = payload
        if (!gatt.writeDescriptor(descriptor)) {
            Log.e("BluetoothService_BLE", "Fallo al escribir en CCCD para ${characteristic.uuid}")
        } else {
            Log.d(
                "BluetoothService_BLE",
                "${if (enable) "Habilitando" else "Deshabilitando"} notificaciones para ${characteristic.uuid}"
            )
        }
    }

    // Actualizar el repositorio (ahora dentro del servicio)
    private suspend fun updateDeviceInRepository(
        deviceId: Int,
        lat: Double,
        lon: Double,
        timestamp: Long
    ) {
        try {
            val existingDevice = dispositivoRepository.getDispositivoByIdOnce(deviceId)
            if (existingDevice != null) {
                val updatedDevice = existingDevice.copy(
                    latitud = lat,
                    longitud = lon,
                    ultimaConexion = timestamp,
                    activo = true
                )
                dispositivoRepository.updateDispositivo(updatedDevice)
                Log.i("BluetoothService", "Dispositivo $deviceId actualizado en repositorio.")
            } else {
                val newDevice = Dispositivo(
                    id = deviceId,
                    nombre = "Dispositivo: $deviceId",
                    latitud = lat,
                    longitud = lon,
                    tipoAnimal = 1, // Asignar un tipo por defecto
                    ultimaConexion = timestamp,
                    activo = true,
                    descripcion = "Sin descripción",
                )
                dispositivoRepository.insertDispositivo(newDevice)
                Log.i("BluetoothService", "Nuevo dispositivo $deviceId insertado en repositorio.")
            }
        } catch (e: Exception) {
            Log.e(
                "BluetoothService",
                "Error actualizando dispositivo en repositorio: ${e.message}",
                e
            )
        }
    }

    // Para actualizar la notificación del servicio en primer plano:
    private fun updateServiceNotification(statusText: String) {
        val notification = NotificationHelper.createBluetoothServiceNotification(this, statusText)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID,
            notification
        )
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null // No estamos usando vinculación directa en este ejemplo, sino broadcasts
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onDestroy() {
        super.onDestroy()
        Log.d("BluetoothService", "Servicio destruido. Iniciando limpieza de recursos...")

        // 1. Detener Escaneos
        // Detener escaneo clásico
        if (bluetoothAdapter.isDiscovering) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter.cancelDiscovery()
                Log.d("BluetoothService", "Descubrimiento clásico detenido.")
            }
        }
        stopBleScan()

        Log.d("BluetoothService", "Limpiando conexiones GATT (BLE)...")
        connectedBleDevices.values.forEach { gatt ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("BluetoothService", "Desconectando GATT de ${gatt.device.address}")
                gatt.disconnect()
            }
        }
        connectedBleDevices.clear()

        serviceJob.cancel()
        Log.d("BluetoothService", "ServiceJob cancelado. Limpieza de onDestroy completada.")
    }
}