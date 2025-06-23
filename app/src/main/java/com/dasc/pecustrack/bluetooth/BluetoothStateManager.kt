package com.dasc.pecustrack.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dasc.pecustrack.ui.adapter.BleDevice
import javax.inject.Inject
import javax.inject.Singleton

// Data class para eventos de conexión, similar a tu ConnectionEventInfo pero más simple si solo necesitas el nombre
data class ConnectionStatusInfo(
    val deviceDisplayName: String?, // Nombre ya resuelto o dirección como fallback
    val errorMessage: String? = null,
    val device: BluetoothDevice? = null // Opcional, si la UI necesita el objeto completo
)

@Singleton
class BluetoothStateManager @Inject constructor() {
    // --- Escaneo ---
    private val _scanStarted = MutableLiveData<Unit>()
    val scanStarted: LiveData<Unit> = _scanStarted

    private val _scanResults = MutableLiveData<List<BleDevice>>() // Usa tu DiscoveredDeviceInfo
    val scanResults: LiveData<List<BleDevice>> = _scanResults

    private val _scanFailed = MutableLiveData<Int>()
    val scanFailed: LiveData<Int> = _scanFailed

    private val _scanStopped = MutableLiveData<Unit>()
    val scanStopped: LiveData<Unit> = _scanStopped

    // --- Conexión ---
    private val _attemptingConnection = MutableLiveData<String>() // Solo el nombre del dispositivo
    val attemptingConnection: LiveData<String> = _attemptingConnection

    // Usaremos ConnectionStatusInfo para los siguientes
    private val _connectionSuccessful = MutableLiveData<ConnectionStatusInfo>()
    val connectionSuccessful: LiveData<ConnectionStatusInfo> = _connectionSuccessful

    private val _connectionFailed = MutableLiveData<ConnectionStatusInfo>()
    val connectionFailed: LiveData<ConnectionStatusInfo> = _connectionFailed

    private val _deviceDisconnected = MutableLiveData<ConnectionStatusInfo>()
    val deviceDisconnected: LiveData<ConnectionStatusInfo> = _deviceDisconnected


    // Funciones para que BluetoothService actualice (sin lógica de permisos aquí)
    fun postScanStarted() { _scanStarted.postValue(Unit) }
    fun postScanResults(devicesInfo: List<BleDevice>) { _scanResults.postValue(devicesInfo) }
    fun postScanFailed(errorCode: Int) { _scanFailed.postValue(errorCode) }
    fun postScanStopped() { _scanStopped.postValue(Unit) }

    fun postAttemptingConnection(deviceName: String) { _attemptingConnection.postValue(deviceName) }
    fun postConnectionSuccessful(displayName: String, device: BluetoothDevice) {
        _connectionSuccessful.postValue(ConnectionStatusInfo(displayName, device = device))
    }
    fun postConnectionFailed(displayName: String?, errorMessage: String?) {
        _connectionFailed.postValue(ConnectionStatusInfo(displayName, errorMessage))
    }
    fun postDeviceDisconnected(displayName: String?) {
        _deviceDisconnected.postValue(ConnectionStatusInfo(displayName))
    }
}