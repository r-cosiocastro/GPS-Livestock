package com.dasc.pecustrack.ui.view

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
import com.dasc.pecustrack.databinding.ActivityBluetoothDevicesBinding
import com.dasc.pecustrack.ui.adapter.BleDeviceListAdapter
import com.dasc.pecustrack.ui.viewmodel.BluetoothViewModel
import com.dasc.pecustrack.utils.LoadingDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BluetoothDevicesView : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothDevicesBinding
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private lateinit var bleDeviceListAdapter: BleDeviceListAdapter
    private lateinit var loadingDialog: LoadingDialog
    private var currentDeviceIndex: Int = 0

    // --- MANEJO DE PERMISOS ---
    @RequiresApi(Build.VERSION_CODES.S)
    private val requiredPermissionsApi31 = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val requiredPermissionsApiBelow31 = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION // Necesario para resultados de escaneo BLE
    )

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }
            if (allGranted) {
                Log.d("DeviceActivity_PERM", "Todos los permisos necesarios concedidos.")
                // Ahora es seguro iniciar el escaneo u otra operación
                // Podrías tener una variable para recordar qué acción se quería hacer
                // o llamar directamente a la acción si es apropiado.
                // Por ejemplo, si esto fue para escanear:
                // bluetoothViewModel.startScan() // <-- ¡AHORA ES SEGURO!
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
            true // Todos los permisos ya están concedidos
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            false // Permisos solicitados, esperar resultado
        }
    }

    // --- FIN DE MANEJO DE PERMISOS ---


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Si quieres un botón de atrás

        loadingDialog = LoadingDialog(this@BluetoothDevicesView)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        // Para manejar el botón de atrás de la Toolbar
        finish() // O navega hacia arriba si tienes una jerarquía más compleja
        return true
    }

    private fun setupClickListeners() {
        binding.buttonEscanear.setOnClickListener {
            if (bluetoothViewModel.isScanning.value == true) {
                // No se necesita permiso específico para detener el escaneo desde la UI
                // ya que el servicio maneja la lógica interna de stopScan
                bluetoothViewModel.stopScan()
            } else {
                // ANTES de llamar a startScan, asegurar permisos de escaneo
                if (checkAndRequestPermissions()) {
                    bluetoothViewModel.startScan() // <-- ¡AHORA ES SEGURO!
                }
                // Si checkAndRequestPermissions() devuelve false, la solicitud ya se lanzó.
            }
        }

        binding.buttonDesconectar.setOnClickListener { // Si tienes un botón de desconectar
            if (checkAndRequestPermissions()) { // BLUETOOTH_CONNECT es necesario para desconectar
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
                bluetoothViewModel.clearScanError() // Limpiar después de mostrar
            }
        }
        bluetoothViewModel.connectionStatusText.observe(this) { status ->
            // Actualizar un TextView con el estado de la conexión, etc.
            binding.textViewEstadoScan.text = status // Asumiendo que tienes este TextView
        }
        bluetoothViewModel.isConnected.observe(this) { connected ->
            // Habilitar/deshabilitar UI basada en el estado de conexión
            binding.buttonDesconectar.isEnabled = connected
            binding.buttonDesconectar.text = if (connected) "Desconectar" else "Desconectado"
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupRecyclerView() {
        bleDeviceListAdapter = BleDeviceListAdapter { deviceItem ->
            // Acción al hacer clic en un dispositivo de la lista
            Log.d("DeviceActivity_UI", "Dispositivo clickeado: ${deviceItem.resolvedName} (${deviceItem.address})")
            currentDeviceIndex = bleDeviceListAdapter.currentList.indexOfFirst { it.address == deviceItem.address }
            if (bluetoothViewModel.isScanning.value == true) {
                bluetoothViewModel.stopScan() // Detener escaneo antes de intentar conectar (opcional)
            }
            // ANTES de llamar a connectToDevice, asegurar permisos de conexión
            if (checkAndRequestPermissions()) { // Reutilizar para verificar/solicitar permisos
                bluetoothViewModel.connectToDevice(deviceItem.device) // <-- ¡AHORA ES SEGURO!
            } else {
                Toast.makeText(this, "Se requieren permisos para conectar.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerViewDispositivos.apply {
            adapter = bleDeviceListAdapter
            layoutManager = LinearLayoutManager(this@BluetoothDevicesView)
            // Opcional: añadir ItemDecoration si quieres divisores
            // addItemDecoration(DividerItemDecoration(this@DeviceActivity, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No es necesario llamar a bluetoothViewModel.disconnect() aquí
        // a menos que quieras desconectar explícitamente al salir de esta pantalla.
        // El servicio puede seguir conectado en segundo plano si así está diseñado.
    }
}