package com.dasc.pecustrack.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dasc.pecustrack.bluetooth.BluetoothService
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionEventInfo(
    val deviceName: String?,
    val errorMessage: String? = null,
    val device: BluetoothDevice? = null // Para ACTION_DEVICE_CONNECTED_INFO si aún lo necesitas
)

@Singleton
class BluetoothStateManager @Inject constructor() {
    private val _scanResults = MutableLiveData<List<BluetoothDevice>>()
    val scanResults: LiveData<List<BluetoothDevice>> = _scanResults

    // Si el escaneo falla o se detiene
    private val _scanFailed = MutableLiveData<Int>() // Podrías usar un Int para el código de error o un objeto de evento más rico
    val scanFailed: LiveData<Int> = _scanFailed

    private val _scanStopped = MutableLiveData<Unit>() // Evento simple para indicar que el escaneo se detuvo
    val scanStopped: LiveData<Unit> = _scanStopped

    // Para ACTION_RECONNECT_ATTEMPTING
    private val _reconnectAttempting = MutableLiveData<ConnectionEventInfo>()
    val reconnectAttempting: LiveData<ConnectionEventInfo> = _reconnectAttempting

    // Para ACTION_CONNECTION_SUCCESSFUL
    private val _connectionSuccessful = MutableLiveData<ConnectionEventInfo>()
    val connectionSuccessful: LiveData<ConnectionEventInfo> = _connectionSuccessful

    // Para ACTION_CONNECTION_FAILED
    private val _connectionFailed = MutableLiveData<ConnectionEventInfo>()
    val connectionFailed: LiveData<ConnectionEventInfo> = _connectionFailed

    // Para ACTION_DEVICE_DISCONNECTED
    private val _deviceDisconnected = MutableLiveData<ConnectionEventInfo>()
    val deviceDisconnected: LiveData<ConnectionEventInfo> = _deviceDisconnected

    // Para ACTION_DEVICE_CONNECTED_INFO (si MapsActivity o DeviceActivity necesitan el objeto BluetoothDevice además del nombre)
    // Si solo necesitan el nombre, connectionSuccessful podría ser suficiente.
    private val _deviceConnectedInfo = MutableLiveData<ConnectionEventInfo>()
    val deviceConnectedInfo: LiveData<ConnectionEventInfo> = _deviceConnectedInfo

    fun postScanResults(devices: List<BluetoothDevice>) {
        _scanResults.postValue(devices)
    }

    fun postScanFailed(errorCode: Int) {
        _scanFailed.postValue(errorCode)
    }

    fun postScanStopped() {
        _scanStopped.postValue(Unit)
    }

    // Funciones para que el BluetoothService actualice los estados
    fun postReconnectAttempting(deviceName: String) {
        _reconnectAttempting.postValue(ConnectionEventInfo(deviceName))
    }

    fun postConnectionSuccessful(deviceName: String, device: BluetoothDevice? = null) {
        _connectionSuccessful.postValue(ConnectionEventInfo(deviceName, device = device))
        // Si ACTION_DEVICE_CONNECTED_INFO se vuelve redundante, considera fusionar
        if (device != null) {
            _deviceConnectedInfo.postValue(ConnectionEventInfo(deviceName, device = device))
        }
    }

    fun postConnectionFailed(deviceName: String?, errorMessage: String?) {
        _connectionFailed.postValue(ConnectionEventInfo(deviceName, errorMessage))
    }

    fun postDeviceDisconnected(deviceName: String?) {
        _deviceDisconnected.postValue(ConnectionEventInfo(deviceName))
    }

    fun postDeviceConnectedInfo(device: BluetoothDevice, displayName: String) { // Específicamente para mantener la estructura actual
        _deviceConnectedInfo.postValue(ConnectionEventInfo(displayName, device = device))
    }
}