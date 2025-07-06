package com.rafaelcosio.gpslivestock.ui.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.rafaelcosio.gpslivestock.databinding.ActivityBluetoothDevicesBinding
import com.rafaelcosio.gpslivestock.ui.adapter.BleDeviceListAdapter
import com.rafaelcosio.gpslivestock.ui.viewmodel.BluetoothViewModel
import com.rafaelcosio.gpslivestock.utils.LoadingDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BluetoothDevicesView : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothDevicesBinding
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private lateinit var bleDeviceListAdapter: BleDeviceListAdapter
    private lateinit var loadingDialog: LoadingDialog
    private var currentDeviceIndex: Int = 0
    @RequiresApi(Build.VERSION_CODES.S)
    private val requiredPermissionsApi31 = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val requiredPermissionsApiBelow31 = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }
            if (allGranted) {
                Log.d("DeviceActivity_PERM", "Todos los permisos necesarios concedidos.")
            } else {
                Log.w("DeviceActivity_PERM", "No todos los permisos fueron concedidos.")
                Toast.makeText(this, "Se requieren permisos de Bluetooth para escanear/conectar.", Toast.LENGTH_LONG).show()
            }
        }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissionsApi31
        } else {
            requiredPermissionsApiBelow31
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsToRequest.isEmpty()) {
            true
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            false
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadingDialog = LoadingDialog(this@BluetoothDevicesView)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupClickListeners() {
        binding.buttonEscanear.setOnClickListener {
            if (bluetoothViewModel.isScanning.value == true) {
                bluetoothViewModel.stopScan()
            } else {
                if (checkAndRequestPermissions()) {
                    bluetoothViewModel.startScan()
                }
            }
        }

        binding.buttonDesconectar.setOnClickListener {
            if (checkAndRequestPermissions()) {
                bluetoothViewModel.disconnectDevice()
            }
        }
    }

    private fun setupObservers() {
        bluetoothViewModel.scannedBleDeviceItems.observe(this) { items ->
            Log.d("DeviceActivity_OBS", "Actualizando UI con ${items.size} dispositivos.")
            bleDeviceListAdapter.submitList(items)
        }
        bluetoothViewModel.isScanning.observe(this) { scanning ->
            binding.buttonEscanear.text = if (scanning) "Detener Escaneo" else "Escanear Dispositivos"
        }
        bluetoothViewModel.scanError.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                bluetoothViewModel.clearScanError()
            }
        }
        bluetoothViewModel.connectionStatusText.observe(this) { status ->
            binding.textViewEstadoScan.text = status
        }
        bluetoothViewModel.isConnected.observe(this) { connected ->
            binding.buttonDesconectar.isEnabled = connected
            binding.buttonDesconectar.text = if (connected) "Desconectar" else "Desconectado"
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupRecyclerView() {
        bleDeviceListAdapter = BleDeviceListAdapter { deviceItem ->
            Log.d("DeviceActivity_UI", "Dispositivo clickeado: ${deviceItem.resolvedName} (${deviceItem.address})")
            currentDeviceIndex = bleDeviceListAdapter.currentList.indexOfFirst { it.address == deviceItem.address }
            if (bluetoothViewModel.isScanning.value == true) {
                bluetoothViewModel.stopScan()
            }
            if (checkAndRequestPermissions()) {
                bluetoothViewModel.connectToDevice(deviceItem.device)
            } else {
                Toast.makeText(this, "Se requieren permisos para conectar.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerViewDispositivos.apply {
            adapter = bleDeviceListAdapter
            layoutManager = LinearLayoutManager(this@BluetoothDevicesView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}