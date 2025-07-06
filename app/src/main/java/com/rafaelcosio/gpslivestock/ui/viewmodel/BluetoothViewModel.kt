package com.rafaelcosio.gpslivestock.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.rafaelcosio.gpslivestock.bluetooth.BluetoothService
import com.rafaelcosio.gpslivestock.bluetooth.BluetoothStateManager
import com.rafaelcosio.gpslivestock.bluetooth.ConnectionStatusInfo
import com.rafaelcosio.gpslivestock.ui.adapter.BleDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    application: Application,
    private val bluetoothStateManager: BluetoothStateManager
) : AndroidViewModel(application) {
    private val _scannedBleDeviceItems = MutableLiveData<List<BleDevice>>(emptyList())
    val scannedBleDeviceItems: LiveData<List<BleDevice>> = _scannedBleDeviceItems

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _scanError = MutableLiveData<String?>() // Mensaje de error de escaneo
    val scanError: LiveData<String?> = _scanError
    private val _connectionStatusText = MutableLiveData<String>("Desconectado")
    val connectionStatusText: LiveData<String> = _connectionStatusText

    private val _connectedDeviceName = MutableLiveData<String?>()
    val connectedDeviceName: LiveData<String?> = _connectedDeviceName

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected
    private val scanStartedObserver = Observer<Unit> {
        Log.d("BluetoothViewModel_OBS", "StateManager - Scan Started")
        _isScanning.value = true
        _scanError.value = null // Limpiar errores previos
        _scannedBleDeviceItems.value = emptyList() // Limpiar resultados anteriores al iniciar nuevo escaneo
    }

    private val scanResultsObserver = Observer<List<BleDevice>> { devicesInfoList ->
        Log.d("BluetoothViewModel_OBS", "StateManager - ScanResults: ${devicesInfoList.size} DiscoveredDeviceInfo recibidos")
        val deviceItems = devicesInfoList.map { deviceInfo ->
            BleDevice(
                device = deviceInfo.device,
                address = deviceInfo.address,
                resolvedName = deviceInfo.resolvedName ?: "Desconocido (${deviceInfo.address.takeLast(5)})",
            )
        }
        _scannedBleDeviceItems.value = deviceItems
    }

    private val scanFailedObserver = Observer<Int> { errorCode ->
        Log.e("BluetoothViewModel_OBS", "StateManager - ScanFailed: $errorCode")
        _scanError.value = "Error de escaneo: $errorCode" // O un mensaje más amigable
        _isScanning.value = false
    }

    private val scanStoppedObserver = Observer<Unit> {
        Log.d("BluetoothViewModel_OBS", "StateManager - Scan Stopped")
        _isScanning.value = false
    }

    private val attemptingConnectionObserver = Observer<String> { deviceName ->
        Log.d("BluetoothViewModel_OBS", "StateManager - Attempting Connection to: $deviceName")
        _connectionStatusText.value = "Conectando a $deviceName..."
        _isConnected.value = false
    }

    private val connectionSuccessfulObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.i("BluetoothViewModel_OBS", "StateManager - Connection Successful to: ${statusInfo.deviceDisplayName}")
        _connectionStatusText.value = "Conectado a ${statusInfo.deviceDisplayName}"
        _connectedDeviceName.value = statusInfo.deviceDisplayName
        _isConnected.value = true
    }

    private val connectionFailedObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.e("BluetoothViewModel_OBS", "StateManager - Connection Failed to: ${statusInfo.deviceDisplayName}, Error: ${statusInfo.errorMessage}")
        _connectionStatusText.value = "Falló conexión con ${statusInfo.deviceDisplayName ?: "dispositivo"}: ${statusInfo.errorMessage}"
        _connectedDeviceName.value = null
        _isConnected.value = false
    }

    private val deviceDisconnectedObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.i("BluetoothViewModel_OBS", "StateManager - Device Disconnected: ${statusInfo.deviceDisplayName}")
        _connectionStatusText.value = "Desconectado de ${statusInfo.deviceDisplayName ?: "dispositivo"}"
        _connectedDeviceName.value = null
        _isConnected.value = false
    }


    init {
        bluetoothStateManager.scanStarted.observeForever(scanStartedObserver)
        bluetoothStateManager.scanResults.observeForever(scanResultsObserver)
        bluetoothStateManager.scanFailed.observeForever(scanFailedObserver)
        bluetoothStateManager.scanStopped.observeForever(scanStoppedObserver)
        bluetoothStateManager.attemptingConnection.observeForever(attemptingConnectionObserver)
        bluetoothStateManager.connectionSuccessful.observeForever(connectionSuccessfulObserver)
        bluetoothStateManager.connectionFailed.observeForever(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.observeForever(deviceDisconnectedObserver)
        Log.d("BluetoothViewModel_LIFECYCLE", "ViewModel Creado y Observadores Registrados")
    }

    override fun onCleared() {
        bluetoothStateManager.scanStarted.removeObserver(scanStartedObserver)
        bluetoothStateManager.scanResults.removeObserver(scanResultsObserver)
        bluetoothStateManager.scanFailed.removeObserver(scanFailedObserver)
        bluetoothStateManager.scanStopped.removeObserver(scanStoppedObserver)
        bluetoothStateManager.attemptingConnection.removeObserver(attemptingConnectionObserver)
        bluetoothStateManager.connectionSuccessful.removeObserver(connectionSuccessfulObserver)
        bluetoothStateManager.connectionFailed.removeObserver(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.removeObserver(deviceDisconnectedObserver)
        Log.d("BluetoothViewModel_LIFECYCLE", "ViewModel Limpiado y Observadores Eliminados")
        super.onCleared()
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

    fun startScan() {
        Log.d("BluetoothViewModel_ACTION", "Solicitando inicio de escaneo al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_START_SCAN
        }
        getApplication<Application>().startService(intent)
    }

    fun stopScan() {
        Log.d("BluetoothViewModel_ACTION", "Solicitando detención de escaneo al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_STOP_SCAN
        }
        getApplication<Application>().startService(intent)
    }

    fun connectToDevice(device: BluetoothDevice) {
        Log.d("BluetoothViewModel_ACTION", "Solicitando conexión a ${device.address} al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_CONNECT_BLE
            putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, device.address)
        }
        getApplication<Application>().startService(intent)
    }

    fun disconnectDevice() {
        Log.d("BluetoothViewModel_ACTION", "Solicitando desconexión al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_DISCONNECT_BLE
        }
        getApplication<Application>().startService(intent)
    }

    fun clearScanError() {
        _scanError.value = null
    }
}