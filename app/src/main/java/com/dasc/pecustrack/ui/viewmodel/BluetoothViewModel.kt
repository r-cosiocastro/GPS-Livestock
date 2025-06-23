package com.dasc.pecustrack.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.dasc.pecustrack.bluetooth.BluetoothService
import com.dasc.pecustrack.bluetooth.BluetoothStateManager
import com.dasc.pecustrack.bluetooth.ConnectionEventInfo
import com.dasc.pecustrack.ui.adapter.DiscoveredDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    application: Application,
    private val bluetoothStateManager: BluetoothStateManager
) : AndroidViewModel(application) {

    private val _scannedDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    val scannedDevices: LiveData<List<BluetoothDevice>> = _scannedDevices

    private val _scannedDiscoveredDeviceItems = MutableLiveData<List<DiscoveredDeviceInfo>>(emptyList())
    val scannedDiscoveredDeviceItems: LiveData<List<DiscoveredDeviceInfo>> = _scannedDiscoveredDeviceItems

    private val _isScanning = MutableLiveData<Boolean>(false) // Para indicar el estado del escaneo
    val isScanning: LiveData<Boolean> = _isScanning

    private val _scanError = MutableLiveData<String?>() // Para mensajes de error de escaneo
    val scanError: LiveData<String?> = _scanError

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private val _connectionStatus = MutableLiveData<String>() // ej. "Conectando...", "Conectado"
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _currentDeviceName = MutableLiveData<String?>()
    val currentDeviceName: LiveData<String?> = _currentDeviceName

    private val _isConnecting = MutableLiveData<Boolean>()
    val isConnecting: LiveData<Boolean> = _isConnecting

    private val scanResultsObserver = Observer<List<BluetoothDevice>> { devices ->
        Log.d("BluetoothViewModel_SCAN", "StateManager - ScanResults: ${devices.size} dispositivos recibidos para transformar a BleDeviceItem")

        val deviceItems = devices.map { device ->
            var deviceName: String? = null // Inicializar como null

            // Lógica para obtener el nombre del dispositivo según la versión del SDK y los permisos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31) o superior
                if (ActivityCompat.checkSelfPermission(
                        getApplication(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        Log.e("BluetoothViewModel_SCAN", "SecurityException al obtener device.name en API 31+. Permiso BLUETOOTH_CONNECT podría faltar en el manifest o no estar concedido en tiempo de ejecución.", e)
                        // deviceName permanece null
                    }
                } else {
                    Log.w("BluetoothViewModel_SCAN", "Permiso BLUETOOTH_CONNECT no concedido en API 31+ para device.name.")
                    // deviceName permanece null
                }
            } else { // Versiones anteriores a Android 12 (API < 31), donde BLUETOOTH_CONNECT no existe.
                // Aquí, el acceso a device.name está generalmente permitido si tienes BLUETOOTH.
                // Sin embargo, todavía puede lanzar SecurityException si BLUETOOTH no está en el manifest,
                // o devolver null si el dispositivo no tiene nombre o no es visible.
                if (ActivityCompat.checkSelfPermission(
                        getApplication(),
                        Manifest.permission.BLUETOOTH // Para API < 31, este es el permiso relevante para obtener el nombre.
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        // Esto sería muy inusual si el permiso BLUETOOTH está concedido y en el manifest
                        Log.e("BluetoothViewModel_SCAN", "SecurityException al obtener device.name en API < 31, incluso con permiso BLUETOOTH.", e)
                        // deviceName permanece null
                    }
                } else {
                    Log.w("BluetoothViewModel_SCAN", "Permiso BLUETOOTH no concedido en API < 31 para device.name (o no está en el Manifest).")
                    // deviceName permanece null
                }
            }

            DiscoveredDeviceInfo(
                id = device.address,
                displayName = deviceName ?: "Desconocido (${device.address.takeLast(5)})", // Nombre o fallback
                address = device.address,
                bluetoothDevice = device
            )
        }
        _scannedDiscoveredDeviceItems.value = deviceItems
    }

    private val scanFailedObserver = Observer<Int> { errorCode ->
        Log.e("BluetoothViewModel_SCAN", "StateManager - ScanFailed: $errorCode")
        _scanError.value = "Error de escaneo: $errorCode" // O un mensaje más amigable
        _isScanning.value = false
    }

    private val scanStoppedObserver = Observer<Unit> {
        Log.d("BluetoothViewModel_SCAN", "StateManager - ScanStopped")
        _isScanning.value = false
        // No necesariamente limpiar _scannedDevices aquí, podrían quererse ver los últimos resultados
    }

    private val deviceConnectedInfoObserver = Observer<ConnectionEventInfo> { eventInfo ->
        Log.d("BluetoothViewModel_DEBUG", "StateManager - DeviceConnectedInfo: deviceName='${eventInfo.deviceName}', device='${eventInfo.device?.address}'")
        _connectedDevice.value = eventInfo.device
        _currentDeviceName.value = eventInfo.deviceName

        if (eventInfo.device != null) {
            _connectionStatus.value = "Conectado a ${eventInfo.deviceName ?: eventInfo.device.address}"
        } else {
            _connectionStatus.value = "Desconectado" // O un estado más apropiado
            _currentDeviceName.value = null // Limpiar si el dispositivo es null
        }
    }

    private val connectionFailedObserver = Observer<ConnectionEventInfo> { eventInfo ->
        Log.d("BluetoothViewModel_DEBUG", "StateManager - ConnectionFailed: deviceName='${eventInfo.deviceName}'")
        _connectionStatus.value = "Conexión fallida a ${eventInfo.deviceName ?: "dispositivo"}"
        // _connectedDevice.value = null // Podrías limpiar el dispositivo aquí si la conexión falla
        // _currentDeviceName.value = null
    }

    private val deviceDisconnectedObserver = Observer<ConnectionEventInfo> { eventInfo ->
        Log.d("BluetoothViewModel_DEBUG", "StateManager - DeviceDisconnected: deviceName='${eventInfo.deviceName}'")
        _connectionStatus.value = "${eventInfo.deviceName ?: "Dispositivo"} desconectado"
        _connectedDevice.value = null
        _currentDeviceName.value = null
    }


    init {
        requestCurrentConnectionStatus()
        startObservingBluetoothState()
    }

    fun requestCurrentConnectionStatus() {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action =
                BluetoothService.ACTION_REQUEST_CURRENT_STATUS
        }
        ContextCompat.startForegroundService(
            getApplication(),
            intent
        )
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startScan() {
        // Podrías actualizar _isScanning aquí inmediatamente para una respuesta UI más rápida,
        // pero el estado real lo determinará el servicio
        _isScanning.value = true
        _scanError.value = null // Limpiar errores anteriores
        _scannedDevices.value = emptyList() // Opcional: limpiar la lista al iniciar nuevo escaneo
        Log.d("BluetoothViewModel_SCAN", "Solicitando inicio de escaneo al servicio.")
        sendCommandToService(Intent(getApplication(), BluetoothService::class.java).setAction(BluetoothService.ACTION_START_SCAN))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopScan() {
        Log.d("BluetoothViewModel_SCAN", "Solicitando detención de escaneo al servicio.")
        sendCommandToService(Intent(getApplication(), BluetoothService::class.java).setAction(BluetoothService.ACTION_STOP_SCAN))
        _isScanning.value = false // Actualizar el estado de escaneo
    }

    fun connectToDevice(device: BluetoothDevice) {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_CONNECT_BLE
            putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, device.address)
        }

        ContextCompat.startForegroundService(getApplication(), intent)
        _isConnecting.value = true
    }

    fun disconnect() {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_DISCONNECT_BLE
            connectedDevice.value?.address?.let {
                putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, it)
            }
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }


    private fun startObservingBluetoothState() {
        // Observa los eventos relevantes del BluetoothStateManager
        // Estos observadores se activarán cuando el LiveData en StateManager cambie
        bluetoothStateManager.deviceConnectedInfo.observeForever(deviceConnectedInfoObserver)
        bluetoothStateManager.connectionFailed.observeForever(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.observeForever(deviceDisconnectedObserver)
        bluetoothStateManager.scanResults.observeForever(scanResultsObserver)
        bluetoothStateManager.scanFailed.observeForever(scanFailedObserver)
        bluetoothStateManager.scanStopped.observeForever(scanStoppedObserver)
        // Observa otros eventos si DeviceActivity necesita reaccionar a ellos (ej. reconnectAttempting, connectionSuccessful)
        // bluetoothStateManager.connectionSuccessful.observeForever { eventInfo -> ... }

        // Solicitar estado actual al servicio (si es necesario al iniciar el ViewModel)
        // Esto es si quieres que el ViewModel sepa el estado tan pronto como se crea,
        // incluso si el servicio ya estaba conectado.
        // sendCommandToService(Intent(application, BluetoothService::class.java).setAction(BluetoothService.ACTION_REQUEST_CURRENT_STATUS))
    }

    override fun onCleared() {
        super.onCleared()
        // Limpia los observadores para evitar memory leaks
        bluetoothStateManager.deviceConnectedInfo.removeObserver(deviceConnectedInfoObserver)
        bluetoothStateManager.connectionFailed.removeObserver(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.removeObserver(deviceDisconnectedObserver)
        bluetoothStateManager.scanResults.removeObserver(scanResultsObserver)
        bluetoothStateManager.scanFailed.removeObserver(scanFailedObserver)
        bluetoothStateManager.scanStopped.removeObserver(scanStoppedObserver)
        // Quita otros observadores si los añadiste
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCommandToService(intent: Intent) {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Log.d("BluetoothViewModel", "Enviando comando al servicio Bluetooth.")
            getApplication<Application>().startService(intent)
        } else {
            Log.e("BluetoothViewModel", "Permiso BLUETOOTH_CONNECT no concedido.")
            _scanError.value = "Permiso de Bluetooth no concedido"
        }
    }
}