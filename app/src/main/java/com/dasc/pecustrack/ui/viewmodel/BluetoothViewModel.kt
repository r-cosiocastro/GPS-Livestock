package com.dasc.pecustrack.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dasc.pecustrack.services.BluetoothService
import com.dasc.pecustrack.utils.parcelable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.io.path.name

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    application: Application,
    private val localBroadcastManager: LocalBroadcastManager
) : AndroidViewModel(application) {

    private val _scannedDevices = MutableLiveData<List<BluetoothDevice>>()
    val scannedDevices: LiveData<List<BluetoothDevice>> = _scannedDevices

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private val _connectionStatus = MutableLiveData<String>() // ej. "Conectando...", "Conectado"
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _currentDeviceName = MutableLiveData<String>()
    val currentDeviceName: LiveData<String> = _currentDeviceName

    private val _isConnecting = MutableLiveData<Boolean>()
    val isConnecting: LiveData<Boolean> = _isConnecting

    // Para comunicación desde el Servicio (usando LocalBroadcastManager o un ResultReceiver)
    private val broadcastReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothService.ACTION_DEVICE_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothService.EXTRA_DEVICE)
                    Log.d("BluetoothViewModel", "Dispositivo encontrado: ${device?.name}")
                    device?.let {
                        val currentList = _scannedDevices.value ?: emptyList()
                        if (!currentList.contains(it)) {
                            _scannedDevices.value = currentList + it
                        }
                    }
                }

                BluetoothService.ACTION_STATUS_CHANGED -> {
                    _connectionStatus.value = intent.getStringExtra(BluetoothService.EXTRA_STATUS)
                }

                BluetoothService.ACTION_DEVICE_CONNECTED_INFO -> {
                    val deviceFromIntent: BluetoothDevice? = intent.parcelable(BluetoothService.EXTRA_DEVICE)
                    // ****** OBTENER EL DISPLAYNAME DEL EXTRA ENVIADO POR EL SERVICIO ******
                    var displayNameFromService = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME)

                    Log.d("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: deviceFromIntent.address = ${deviceFromIntent?.address}")
                    Log.d("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: displayNameFromService (EXTRA) = $displayNameFromService")

                    if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        Log.d("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: deviceFromIntent.name (del objeto Parcelable, puede ser null) = ${deviceFromIntent?.name}")
                    } else {
                        Log.d("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: SIN permiso para device.name en ViewModel (aunque no deberíamos necesitarlo aquí)")
                    }

                    _connectedDevice.value = deviceFromIntent // Está bien guardar el objeto dispositivo

                    if (deviceFromIntent != null) {
                        // Usar el nombre resuelto por el servicio.
                        // Si por alguna razón extraña no viene, entonces hacer fallback.
                        val finalDisplayName = displayNameFromService ?: if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            Log.w("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: displayNameFromService era NULL, haciendo fallback a device.name o address.")
                            deviceFromIntent.name ?: deviceFromIntent.address
                        } else {
                            Log.w("BluetoothViewModel_DEBUG", "ACTION_DEVICE_CONNECTED_INFO: displayNameFromService era NULL y SIN permiso, haciendo fallback a address.")
                            deviceFromIntent.address
                        }
                        _connectionStatus.value = "Conectado a $finalDisplayName"
                        Log.d("BluetoothViewModel_DEBUG", "VM: ACTION_DEVICE_CONNECTED_INFO - finalDisplayName ANTES de asignar a _currentDeviceName: '$finalDisplayName'")
                        _currentDeviceName.value = finalDisplayName!!
                        Log.d("BluetoothViewModel_DEBUG", "VM: ACTION_DEVICE_CONNECTED_INFO - _currentDeviceName DESPUÉS de asignar: '${_currentDeviceName.value}'")
                        Log.d("BluetoothViewModel_DEBUG", "VM: ACTION_DEVICE_CONNECTED_INFO - Status actualizado a: ${_connectionStatus.value}")

                    } else {
                        _connectionStatus.value = "Desconectado"
                        Log.d("BluetoothViewModel_DEBUG", "VM: ACTION_DEVICE_CONNECTED_INFO - deviceFromIntent es null, status: Desconectado")
                    }
                    _isConnecting.value = false
                }

                BluetoothService.ACTION_DEVICE_DISCONNECTED -> {
                    _connectedDevice.value = null
                    _connectionStatus.value = "Desconectado"
                    _currentDeviceName.value = ""
                }

                BluetoothService.ACTION_CONNECTION_FAILED -> {
                    _connectedDevice.value = null
                    _connectionStatus.value = "Error de conexión"
                    _currentDeviceName.value = ""
                }

                BluetoothService.ACTION_CONNECTION_SUCCESSFUL -> {
                    val deviceNameFromExtra = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME)
                    Log.d("BluetoothViewModel_DEBUG", "ACTION_CONNECTION_SUCCESSFUL: deviceNameFromExtra = '$deviceNameFromExtra'")
                    _connectionStatus.value = "Conectado a $deviceNameFromExtra" // Asume que deviceNameFromExtra ya tiene el fallback
                }

                else -> {
                    Log.e("BluetoothViewModel", "Acción desconocida recibida: ${intent?.action}")
                }

            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothService.ACTION_DEVICE_FOUND)
            addAction(BluetoothService.ACTION_STATUS_CHANGED)
            addAction(BluetoothService.ACTION_DEVICE_CONNECTED_INFO)
            addAction(BluetoothService.ACTION_DEVICE_DISCONNECTED)
            addAction(BluetoothService.ACTION_CONNECTION_FAILED)
            // addAction(BluetoothService.ACTION_CONNECTION_SUCCESSFUL) considerar quitarlo
        }
        localBroadcastManager.registerReceiver(broadcastReceiver, filter)
        requestCurrentConnectionStatus()
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
        Log.d("BluetoothViewModel", "Comenzando escaneo de dispositivos Bluetooth (viewmodel)")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_START_BLE_SCAN
            Log.d("BluetoothViewModel", "Intent para iniciar escaneo: $this")
        }
        ContextCompat.startForegroundService(getApplication(), intent)
        Log.d("BluetoothViewModel", "Intent enviado para iniciar escaneo")
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

    override fun onCleared() {
        super.onCleared()
        localBroadcastManager.unregisterReceiver(broadcastReceiver)
    }
}