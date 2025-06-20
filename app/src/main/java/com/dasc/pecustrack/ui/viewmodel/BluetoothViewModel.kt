package com.dasc.pecustrack.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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

    // Para comunicación desde el Servicio (usando LocalBroadcastManager o un ResultReceiver)
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothService.ACTION_DEVICE_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothService.EXTRA_DEVICE)
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
                BluetoothService.ACTION_DEVICE_CONNECTED_INFO -> { // Nuevo action del servicio
                    val device: BluetoothDevice?  = intent.parcelable(BluetoothService.EXTRA_DEVICE)
                    _connectedDevice.value = device // Guardar el dispositivo conectado
                    // Aquí podrías querer actualizar el connectionStatus con el nombre del dispositivo si está disponible
                    if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        _connectionStatus.value = "Conectado a ${device?.address}"
                    } else {
                        _connectionStatus.value = "Conectado a ${device?.name ?: device?.address}"
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothService.ACTION_DEVICE_FOUND)
            addAction(BluetoothService.ACTION_STATUS_CHANGED)
            addAction(BluetoothService.ACTION_DEVICE_CONNECTED_INFO) // Escuchar este nuevo action
        }
        localBroadcastManager.registerReceiver(broadcastReceiver, filter)
    }

    fun startScan() {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_START_SCAN
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun connectToDevice(device: BluetoothDevice) {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_CONNECT
            putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, device.address)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun disconnect() {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    override fun onCleared() {
        super.onCleared()
        localBroadcastManager.unregisterReceiver(broadcastReceiver)
    }
}