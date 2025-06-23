package com.dasc.pecustrack.ui.adapter

import android.bluetooth.BluetoothDevice

data class DiscoveredDeviceInfo(
    val id: String,
    val displayName: String,
    val address: String,
    val bluetoothDevice: BluetoothDevice
)
