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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.dasc.pecustrack.data.model.Rastreador
import com.dasc.pecustrack.data.repository.RastreadorRepository
import com.dasc.pecustrack.ui.adapter.BleDevice
import com.dasc.pecustrack.utils.AppPreferences
import com.dasc.pecustrack.utils.NotificationHelper
import com.dasc.pecustrack.utils.NotificationHelper.DATA_UPDATE_CHANNEL_ID
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

    companion object {
        const val ACTION_CONNECT_BLE = "com.dasc.pecustrack.ACTION_CONNECT_BLE"
        const val ACTION_DISCONNECT_BLE = "com.dasc.pecustrack.ACTION_DISCONNECT_BLE"
        const val ACTION_REQUEST_CURRENT_STATUS =
            "com.dasc.pecustrack.ACTION_REQUEST_CURRENT_STATUS"


        const val ACTION_START_SCAN = "com.dasc.pecustrack.ACTION_START_SCAN"
        const val ACTION_STOP_SCAN = "com.dasc.pecustrack.ACTION_STOP_SCAN"
        const val EXTRA_DEVICE_ADDRESS = "com.dasc.pecustrack.EXTRA_DEVICE_ADDRESS"

        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1

        private val SERVICE_UUID_STRING = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID_STRING = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // UUID del Client Characteristic Configuration Descriptor

    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Componentes para BLE
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val connectedBleDevices =
        mutableMapOf<String, BluetoothGatt>() // Para manejar múltiples conexiones BLE si es necesario

    private val binder = LocalBinder()
    private var connectedGatt: BluetoothGatt? = null
    private var currentDeviceAddress: String? = null
    private var currentDeviceName: String? = null // Almacenar el nombre resuelto aquí


    private var isAttemptingAutoReconnect: Boolean = false
    private var lastAttemptedDeviceNameForToast: String? = null
    private var lastAttemptedDeviceAddressForAutoReconnect: String? =
        null // Para saber qué dispositivo estamos reconectando

    private val discoveredDevicesList = mutableListOf<BleDevice>()
    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_PERIOD: Long = 15000 // 15 segundos

    // Inyectar el repositorio
    @Inject
    lateinit var rastreadorRepository: RastreadorRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper // Asumiendo que también lo inyectas o lo tienes como object

    // CoroutineScope para operaciones del repositorio desde el servicio
    private val serviceJob = SupervisorJob()
    private val serviceScope =
        CoroutineScope(Dispatchers.IO + serviceJob) // Usa Dispatchers.IO para DB ops

    private fun startBleScanWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothService_SCAN", "Permiso BLUETOOTH_SCAN no concedido (API 31+). No se puede escanear.")
                bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR) // O un código de error personalizado
                return
            }
        } else { // Para API < 31, ACCESS_FINE_LOCATION es el crítico para resultados, BLUETOOTH/BLUETOOTH_ADMIN para iniciar.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ) { // BLUETOOTH_ADMIN para iniciar discovery
                Log.e("BluetoothService_SCAN", "Permisos de ubicación o Bluetooth Admin no concedidos (API < 31). No se puede escanear.")
                bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                return
            }
        }
        // Aquí llamas a la función que tiene la anotación si es necesario o directamente el código
        actuallyStartBleScan()
    }

    private fun actuallyStartBleScan() {
        if (!bluetoothAdapter.isEnabled || bluetoothLeScanner == null) {
            Log.w("BluetoothService_SCAN", "Bluetooth no habilitado o scanner nulo. No se puede escanear.")
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
            return
        }
        if (isScanning) {
            Log.d("BluetoothService_SCAN", "El escaneo ya está en progreso.")
            return
        }
        discoveredDevicesList.clear()
        bluetoothStateManager.postScanResults(emptyList()) // Notifica UI
        bluetoothStateManager.postScanStarted() // Nuevo evento para indicar que el escaneo comenzó

        val scanFilters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_UUID_STRING.toString())).build()) // Ajusta
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        Log.i("BluetoothService_SCAN", "Iniciando escaneo BLE...")
        try {
            // La llamada real que necesita el permiso BLUETOOTH_SCAN (implícitamente por el contexto)
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, bleScanCallback)
            isScanning = true
            scanHandler.postDelayed({
                if (isScanning) {
                    stopBleScanWithPermissionCheck() // Detener con verificación de permisos
                }
            }, SCAN_PERIOD)
        } catch (e: SecurityException) {
            Log.e("BluetoothService_SCAN", "SecurityException al iniciar escaneo: ${e.message}", e)
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
            isScanning = false
        }  catch (e: IllegalStateException) {
            Log.e("BluetoothService_SCAN", "IllegalStateException al iniciar escaneo (Bluetooth podría estar apagado): ${e.message}", e)
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) // O similar
            isScanning = false
        }
    }

    private fun stopBleScanWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothService_SCAN", "Permiso BLUETOOTH_SCAN no concedido (API 31+). No se puede detener explícitamente el escaneo, pero se detendrá por timeout o al cerrar.")
                // No se puede llamar a stopScan sin el permiso, pero el escaneo se detendrá.
                // Podrías no hacer nada aquí si el permiso no está, ya que el timeout lo manejará
                // o al cerrar la app. O simplemente loggear.
                // Si isScanning es true, el timeout lo manejará.
                // Si la UI lo pide explícitamente pero no hay permiso, es una situación extraña.
                if (isScanning) { // Si realmente estaba escaneando, notificar que se detuvo (aunque sea por no poder llamar a la API)
                    isScanning = false // Asumir que se detendrá o ya se detuvo
                    bluetoothStateManager.postScanStopped()
                }
                return
            }
        }
        // Para API < 31, no hay un permiso específico para stopScan que no sea BLUETOOTH_ADMIN que se usó para iniciar.
        actuallyStopBleScan()
    }

    private fun actuallyStopBleScan() {
        if (isScanning && bluetoothLeScanner != null) {
            Log.i("BluetoothService_SCAN", "Deteniendo escaneo BLE.")
            try {
                bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (e: SecurityException) {
                Log.e("BluetoothService_SCAN", "SecurityException al detener escaneo: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e("BluetoothService_SCAN", "IllegalStateException al detener escaneo (Bluetooth podría estar apagado): ${e.message}", e)
            }
            isScanning = false
            bluetoothStateManager.postScanStopped()
        }
        scanHandler.removeCallbacksAndMessages(null)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            var deviceName: String? = null

            // --- LÓGICA PARA OBTENER NOMBRE DEL DISPOSITIVO (¡DENTRO DEL SERVICIO!) ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        Log.w(
                            "BluetoothService_SCAN",
                            "onScanResult: SecEx para device.name API31+",
                            e
                        )
                    }
                } else {
                    Log.w(
                        "BluetoothService_SCAN",
                        "onScanResult: Sin permiso BLUETOOTH_CONNECT para device.name API31+"
                    )
                }
            } else { // API < 31
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) { // BLUETOOTH para device.name en APIs antiguas
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        Log.w(
                            "BluetoothService_SCAN",
                            "onScanResult: SecEx para device.name API<31",
                            e
                        )
                    }
                } else {
                    Log.w(
                        "BluetoothService_SCAN",
                        "onScanResult: Sin permiso BLUETOOTH para device.name API<31"
                    )
                }
            }
            // --- FIN DE LÓGICA PARA OBTENER NOMBRE ---

            val existingDeviceIndex =
                discoveredDevicesList.indexOfFirst { it.address == device.address }
            if (existingDeviceIndex == -1) {
                if (deviceName != null || result.scanRecord?.deviceName != null) { // Filtrar dispositivos sin nombre si se desea
                    Log.d(
                        "BluetoothService_SCAN",
                        "Nuevo dispositivo: ${deviceName ?: result.scanRecord?.deviceName} (${device.address}) RSSI: ${result.rssi}"
                    )
                    discoveredDevicesList.add(
                        BleDevice(
                            device,
                            deviceName ?: result.scanRecord?.deviceName,
                            device.address
                        )
                    )
                }
            } else { // Dispositivo ya existe, quizás actualizar RSSI o si el nombre se resolvió ahora
                val existing = discoveredDevicesList[existingDeviceIndex]
                if (existing.resolvedName == null && (deviceName != null || result.scanRecord?.deviceName != null)) {
                    discoveredDevicesList[existingDeviceIndex] = BleDevice(
                        device,
                        deviceName ?: result.scanRecord?.deviceName,
                        device.address /*, result.rssi*/
                    )
                } else {
                    // Opcional: solo actualizar si el RSSI cambió significativamente, etc.
                    // Para evitar muchos updates, podrías no hacer nada aquí o solo actualizar RSSI en el objeto existente.
                }
            }
            bluetoothStateManager.postScanResults(ArrayList(discoveredDevicesList)) // Enviar copia
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            var changed = false
            results.forEach { result ->
                val device = result.device
                var deviceName: String? = null
                // --- LÓGICA PARA OBTENER NOMBRE (similar a onScanResult) ---
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            deviceName = device.name
                        } catch (e: SecurityException) { /* log */
                        }
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            deviceName = device.name
                        } catch (e: SecurityException) { /* log */
                        }
                    }
                }
                // --- FIN ---
                if (!discoveredDevicesList.any { it.address == device.address }) {
                    if (deviceName != null || result.scanRecord?.deviceName != null) {
                        discoveredDevicesList.add(
                            BleDevice(
                                device,
                                deviceName ?: result.scanRecord?.deviceName,
                                device.address
                            )
                        )
                        changed = true
                    }
                }
            }
            if (changed) bluetoothStateManager.postScanResults(ArrayList(discoveredDevicesList))
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

    private fun isServiceConnectedToDevice(): Boolean {
        // Implementa una lógica para saber si ya hay una conexión GATT activa y válida
        return bluetoothGatt != null && connectedBleDevices.isNotEmpty() // Simplificado
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
        val action = intent?.action
        Log.d("BluetoothService_LIFECYCLE", "onStartCommand, Action: $action")

        if (intent?.action == null && !isServiceConnectedToDevice()) { // Si el servicio se inicia sin acción y no hay conexión activa
            val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
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
                if (nameForInitialToast != null) {
                    sendReconnectAttemptingBroadcast(nameForInitialToast)
                }
                attemptAutoReconnectToDevice(lastDeviceAddress) // autoConnect = true o false según tu elección
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w("BluetoothService_CMD", "Bluetooth no está habilitado. Ignorando comando $action.")
            // Podrías enviar un evento al StateManager indicando "Bluetooth_OFF"
            // bluetoothStateManager.postBluetoothDisabled()
            stopSelf() // Detener el servicio si BT está apagado y no puede hacer nada
            return START_NOT_STICKY
        }
        // Re-obtener el scanner si no se inicializó en onCreate (BT estaba apagado)
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.e("BluetoothService_CMD", "Falló al obtener BluetoothLeScanner incluso después de que BT está ON.")
                stopSelf()
                return START_NOT_STICKY
            }
        }


        when (action) {
            ACTION_START_SCAN -> {
                // El permiso BLUETOOTH_SCAN es verificado por la Activity antes de enviar este intent
                // La anotación está en la función startBleScan interna.
                startBleScanWithPermissionCheck()
            }
            ACTION_STOP_SCAN -> {
                // El permiso BLUETOOTH_SCAN es verificado por la Activity antes de enviar este intent
                // La anotación está en la función stopBleScan interna.
                stopBleScanWithPermissionCheck()
            }
            ACTION_CONNECT_BLE -> {
                // El permiso BLUETOOTH_CONNECT es verificado por la Activity antes de enviar este intent
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    val device = bluetoothAdapter.getRemoteDevice(address)
                    // La anotación está en la función connectToDevice interna
                    connectToDeviceWithPermissionCheck(device)
                } else {
                    Log.w("BluetoothService_CMD", "Dirección del dispositivo no proporcionada para conectar.")
                }
            }
            ACTION_DISCONNECT_BLE -> {
                // El permiso BLUETOOTH_CONNECT es verificado por la Activity
                // La anotación está en la función disconnectDevice interna
                disconnectDeviceWithPermissionCheck()
            }
        }
        return START_REDELIVER_INTENT // O START_STICKY según tu necesidad
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

    private fun clearLastConnectedDeviceAddress() {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit {
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS)
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME)
        }
        Log.i("BluetoothService_BLE", "Dirección del último dispositivo conectado eliminada.")
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

    private fun sendReconnectAttemptingBroadcast(deviceName: String?) {
        bluetoothStateManager.postAttemptingConnection(deviceName.toString())
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
        bluetoothStateManager.postConnectionSuccessful(displayName, device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBleScan() {
        if (isScanning) {
            Log.i("BluetoothService_SCAN", "Deteniendo escaneo BLE.")
            bluetoothLeScanner?.stopScan(bleScanCallback)
            isScanning = false
            // Anteriormente: sendBroadcast(Intent(ACTION_SCAN_STOPPED))
            bluetoothStateManager.postScanStopped()
        }
        scanHandler.removeCallbacksAndMessages(null) // Limpia cualquier callback pendiente para detener el escaneo
    }

    private fun connectToDeviceWithPermissionCheck(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothService_CONN", "Permiso BLUETOOTH_CONNECT no concedido (API 31+). No se puede conectar.")
                bluetoothStateManager.postConnectionFailed(currentDeviceName ?: device.address, "Permiso requerido")
                return
            }
        } else { // Para API < 31, BLUETOOTH_ADMIN o BLUETOOTH es suficiente.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) { // BLUETOOTH_ADMIN para conectar
                Log.e("BluetoothService_CONN", "Permiso BLUETOOTH_ADMIN no concedido (API < 31). No se puede conectar.")
                bluetoothStateManager.postConnectionFailed(currentDeviceName ?: device.address, "Permiso requerido (admin)")
                return
            }
        }
        actuallyConnectToDevice(device)
    }

    private fun actuallyConnectToDevice(device: BluetoothDevice) {
        if (!bluetoothAdapter.isEnabled) {
            Log.w("BluetoothService_CONN", "BT no habilitado, no se puede conectar a ${device.address}")
            bluetoothStateManager.postConnectionFailed(currentDeviceName ?: device.address, "Bluetooth apagado")
            return
        }
        Log.d("BluetoothService_CONN", "Intentando conectar a: ${device.address}")
        stopBleScanWithPermissionCheck() // Detener escaneo antes de conectar

        currentDeviceAddress = device.address
        // Intentar obtener el nombre aquí también, si no lo tenemos del escaneo
        if (currentDeviceName == null) {
            currentDeviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        device.name ?: device.address
                    } catch (e: SecurityException){
                        device.address
                    }
                } else {
                    device.address
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        device.name ?: device.address
                    } catch (e: SecurityException){
                        device.address
                    }
                } else {
                    device.address
                }
            }
        }

        bluetoothStateManager.postAttemptingConnection(currentDeviceName ?: device.address) // Nuevo evento

        try {
            // La llamada real que requiere BLUETOOTH_CONNECT en API 31+
            connectedGatt = device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e("BluetoothService_CONN", "SecurityException al conectar gatt: ${e.message}", e)
            bluetoothStateManager.postConnectionFailed(currentDeviceName ?: device.address, e.message)
        }
    }

    private fun disconnectDeviceWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothService_CONN", "Permiso BLUETOOTH_CONNECT no concedido (API 31+). No se puede desconectar explícitamente.")
                // Si GATT existe, podría cerrarse sin la llamada explícita de disconnect si la app se cierra.
                // Es mejor tener el permiso.
                if (connectedGatt != null) {
                    // No podemos llamar a disconnect, pero podemos cerrar y notificar.
                    try { connectedGatt?.close() } catch (e: SecurityException) { /* log */ }
                    connectedGatt = null
                    bluetoothStateManager.postDeviceDisconnected(currentDeviceName ?: currentDeviceAddress)
                    currentDeviceName = null
                    currentDeviceAddress = null
                }
                return
            }
        }
        // No hay permiso específico para disconnect en API < 31 que no sea BLUETOOTH_ADMIN que se usó para conectar.
        actuallyDisconnectDevice()
    }

    private fun actuallyDisconnectDevice() {
        if (connectedGatt != null) {
            Log.d("BluetoothService_CONN", "Desconectando de ${currentDeviceAddress}")
            try {
                connectedGatt?.disconnect() // Esta llamada necesita BLUETOOTH_CONNECT en API 31+
                // El cierre real y la notificación se harán en gattCallback.onConnectionStateChange
            } catch (e: SecurityException) {
                Log.e("BluetoothService_CONN", "SecurityException al desconectar gatt: ${e.message}", e)
                // Si falla la desconexión por permisos, al menos cerrar y notificar.
                try { connectedGatt?.close() } catch (se: SecurityException) { /* log */ }
                bluetoothStateManager.postDeviceDisconnected(currentDeviceName ?: currentDeviceAddress)
                connectedGatt = null
                currentDeviceName = null
                currentDeviceAddress = null
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceNameForNotification = currentDeviceName ?: deviceAddress // Usar el nombre que intentamos resolver

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BluetoothService_GATT", "Conectado a GATT server $deviceNameForNotification ($deviceAddress).")
                    connectedGatt = gatt // Asegurar que tenemos la instancia correcta
                    currentDeviceAddress = deviceAddress // Confirmar
                    // Guardar el nombre resuelto si BLUETOOTH_CONNECT está disponible
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            try { currentDeviceName = gatt.device.name ?: deviceNameForNotification } catch (e: SecurityException) {/*log*/}
                        }
                    } else {
                        if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                            try { currentDeviceName = gatt.device.name ?: deviceNameForNotification } catch (e: SecurityException) {/*log*/}
                        }
                    }
                    bluetoothStateManager.postConnectionSuccessful(currentDeviceName ?: deviceAddress, gatt.device)
                    // Iniciar descubrimiento de servicios si es necesario
                    gatt.discoverServices()
                    startForegroundWithNotification("Conectado a ${currentDeviceName ?: deviceAddress}")
                    handleDeviceNameLogicAndBroadcasts(gatt, "onConnectionStateChange")

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("BluetoothService_GATT", "Desconectado de GATT server $deviceNameForNotification ($deviceAddress).")
                    try { gatt.close() } catch (e: SecurityException) { Log.e("BluetoothService_GATT", "SecEx al cerrar GATT en desconexión", e) }
                    connectedGatt = null
                    bluetoothStateManager.postDeviceDisconnected(deviceNameForNotification)
                    currentDeviceName = null
                    currentDeviceAddress = null
                    stopForeground(STOP_FOREGROUND_REMOVE) // Detener el primer plano
                }
            } else { // Error en la conexión
                Log.w("BluetoothService_GATT", "Error GATT: $status al cambiar estado para $deviceNameForNotification ($deviceAddress)")
                try { gatt.close() } catch (e: SecurityException) { Log.e("BluetoothService_GATT", "SecEx al cerrar GATT en error", e) }
                connectedGatt = null
                bluetoothStateManager.postConnectionFailed(deviceNameForNotification, "Error GATT: $status")
                currentDeviceName = null
                currentDeviceAddress = null
                stopForeground(STOP_FOREGROUND_REMOVE)
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

                val service = gatt.getService(SERVICE_UUID_STRING)
                if (service == null) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Servicio $SERVICE_UUID_STRING no encontrado en $displayName"
                    )
                    // Considera desconectar o informar error
                    return
                }

                val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID_STRING)
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
            Log.d("BluetoothService_DATA", "Datos recibidos de ${gatt.device.address} en ${characteristic.uuid}: ${value.toHexString()}")
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

                        var rastreador =
                            rastreadorRepository.getDispositivoByIdOnce(idDispositivo)

                        if (rastreador == null) {
                            // Si no existe un dispositivo con ese ID y MAC, lo creamos.
                            // Esto asume que el ID que manda el ESP32 es único para ese tipo de sensor
                            // y la MAC identifica al ESP32 que lo tiene.
                            rastreador = Rastreador(
                                id = idDispositivo,
                                nombre = "Dispositivo $idDispositivo",
                                descripcion = "Sin descripción",
                                latitud = latitud,
                                longitud = longitud,
                                ultimaConexion = System.currentTimeMillis(),
                                tipoAnimal = 0,
                                activo = true
                            )
                            rastreadorRepository.insertDispositivo(rastreador)
                            Log.i(
                                "BluetoothService_BLE",
                                "Nuevo Dispositivo creado y guardado: $rastreador"
                            )
                            // Notificar a la UI sobre nuevo dispositivo
                            sendNewDeviceDataNotification(
                                rastreador,
                                "Nuevo dispositivo detectado"
                            )

                        } else {
                            // Actualizar el dispositivo existente con la nueva ubicación
                            rastreador = rastreador.copy(
                                latitud = latitud,
                                longitud = longitud,
                                ultimaConexion = System.currentTimeMillis(),
                                activo = true
                            )

                            rastreadorRepository.updateDispositivo(rastreador)
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
        private fun sendNewDeviceDataNotification(rastreador: Rastreador, message: String) {
            val notificationTitle =
                "PecusTrack: ${rastreador.nombre ?: "Dispositivo ${rastreador.id}"}"
            val notificationText = "$message (${rastreador.latitud}, ${rastreador.longitud})"
            val notificationID = System.currentTimeMillis().toInt()
            val notification = NotificationHelper.createBasicNotification(
                this@BluetoothService,
                DATA_UPDATE_CHANNEL_ID,
                notificationTitle,
                notificationText
            )
            notificationHelper.showNotification(
                this@BluetoothService,
                notificationID,
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

        gatt.writeCharacteristic(characteristic)?.let {
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
            val existingDevice = rastreadorRepository.getDispositivoByIdOnce(deviceId)
            if (existingDevice != null) {
                val updatedDevice = existingDevice.copy(
                    latitud = lat,
                    longitud = lon,
                    ultimaConexion = timestamp,
                    activo = true
                )
                rastreadorRepository.updateDispositivo(updatedDevice)
                Log.i("BluetoothService", "Dispositivo $deviceId actualizado en repositorio.")
            } else {
                val newDevice = Rastreador(
                    id = deviceId,
                    nombre = "Dispositivo: $deviceId",
                    latitud = lat,
                    longitud = lon,
                    tipoAnimal = 1, // Asignar un tipo por defecto
                    ultimaConexion = timestamp,
                    activo = true,
                    descripcion = "Sin descripción",
                )
                rastreadorRepository.insertDispositivo(newDevice)
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

    private fun startForegroundWithNotification(contentText: String) {
        val notification = NotificationHelper.createBluetoothServiceNotification(
            this,
            contentText
        )
        startForeground(NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.d("BluetoothService_LIFECYCLE", "Servicio Destruido")
        stopBleScanWithPermissionCheck()
        if (connectedGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // No podemos llamar a disconnect, solo close
                } else {
                    connectedGatt?.disconnect() // Intenta desconectar si es posible
                }
                connectedGatt?.close()
            } catch (e: SecurityException) {
                Log.e("BluetoothService_LIFECYCLE", "SecurityException al limpiar GATT en onDestroy", e)
            }
            connectedGatt = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}