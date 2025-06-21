package com.dasc.pecustrack.ui.view

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.activity.result.registerForActivityResult
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.dasc.pecustrack.databinding.ActivitySelectDeviceBinding
import com.dasc.pecustrack.ui.adapter.BluetoothDeviceAdapter
import com.dasc.pecustrack.ui.viewmodel.BluetoothViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue
import androidx.core.view.isGone
import androidx.core.view.isVisible

@AndroidEntryPoint
class DeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectDeviceBinding
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private lateinit var deviceAdapter: BluetoothDeviceAdapter

    // Permisos necesarios para Bluetooth en diferentes versiones de Android
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH, // Para API < 31
            Manifest.permission.BLUETOOTH_ADMIN, // Para API < 31
            Manifest.permission.ACCESS_FINE_LOCATION // Requerido para escaneo en algunas versiones
        )
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                }
            }
            if (allGranted) {
                checkBluetoothAndStartScan()
            } else {
                // Mostrar un diálogo explicando por qué se necesitan los permisos
                // y ofrecer ir a la configuración de la app.
                showPermissionDeniedDialog()
                binding.textViewEstadoScan.text = "Permisos necesarios denegados."
                binding.progressBarScan.visibility = View.GONE
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startScanDevices() // Bluetooth activado, iniciar escaneo
            } else {
                Toast.makeText(this, "Bluetooth no fue activado.", Toast.LENGTH_SHORT).show()
                binding.textViewEstadoScan.text = "Bluetooth no activado."
                binding.progressBarScan.visibility = View.GONE
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Si quieres un botón de atrás

        setupRecyclerView()

        binding.buttonEscanear.setOnClickListener {
            // Limpiar la lista anterior antes de un nuevo escaneo
            deviceAdapter.submitList(emptyList())
            bluetoothViewModel.scannedDevices.value?.let { // Limpiar la lista en el ViewModel también
                if (it.isNotEmpty()) {
                    // No puedes modificar directamente _scannedDevices desde aquí,
                    // El ViewModel debería tener un método para limpiar o reiniciar.
                    // Por ahora, el ViewModel se actualiza con nuevos dispositivos.
                }
            }
            checkPermissionsAndStartScan()
        }

        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        // Para manejar el botón de atrás de la Toolbar
        finish() // O navega hacia arriba si tienes una jerarquía más compleja
        return true
    }

    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter { device ->
            // Acción al hacer clic en un dispositivo: intentar conectar
            bluetoothViewModel.connectToDevice(device)
            // Aquí puedes mostrar un diálogo de "Conectando..."
            // y observar connectionStatus para cerrarlo o mostrar errores.
            Toast.makeText(this, "Conectando a ${getDeviceNameSafe(device)}...", Toast.LENGTH_SHORT).show()
            // Podrías querer deshabilitar más clics mientras se conecta.
        }
        binding.recyclerViewDispositivos.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(this@DeviceActivity)
            addItemDecoration(DividerItemDecoration(this@DeviceActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun observeViewModel() {
        bluetoothViewModel.scannedDevices.observe(this) { devices ->
            if (devices.isNullOrEmpty() && binding.progressBarScan.isGone) {
                binding.textViewEstadoScan.visibility = View.VISIBLE
                binding.textViewEstadoScan.text = "No se encontraron dispositivos. Intenta escanear."
            } else if (devices.isNullOrEmpty() && binding.progressBarScan.isVisible) {
                binding.textViewEstadoScan.visibility = View.VISIBLE
                binding.textViewEstadoScan.text = "Escaneando..."
            }
            else {
                binding.textViewEstadoScan.visibility = View.GONE
            }
            deviceAdapter.submitList(devices)
            binding.recyclerViewDispositivos.visibility = if (devices.isNullOrEmpty()) View.GONE else View.VISIBLE
            binding.progressBarScan.visibility = View.GONE // Ocultar ProgressBar al recibir dispositivos
            // binding.buttonEscanear.isEnabled = true // Habilitar botón de escaneo nuevamente

        }

        bluetoothViewModel.connectionStatus.observe(this) { status ->
            Toast.makeText(this, "Estado: $status", Toast.LENGTH_LONG).show()
            // Actualizar UI según el estado (ej. ProgressBar, texto)
            if (status.contains("Conectado a")) {
                // Aquí podrías cerrar la activity y devolver el dispositivo conectado
                // o navegar a otra pantalla.
                // Por ejemplo, devolver el dispositivo conectado a la actividad que llamó
                val deviceName = status.substringAfter("Conectado a ")
                Toast.makeText(this, "Conexión exitosa a $deviceName", Toast.LENGTH_LONG).show()

                // Opcional: Devolver resultado a la actividad que inició esta
                // val resultIntent = Intent()
                // val connectedDevice = bluetoothViewModel.connectedDevice.value // Necesitas exponer el dispositivo completo
                // resultIntent.putExtra("CONNECTED_DEVICE_ADDRESS", connectedDevice?.address)
                // setResult(Activity.RESULT_OK, resultIntent)
                // finish()

            } else if (status.startsWith("Escaneando")) {
                binding.progressBarScan.visibility = View.VISIBLE
                binding.textViewEstadoScan.text = status
                binding.textViewEstadoScan.visibility = View.VISIBLE
                binding.buttonEscanear.isEnabled = false
            } else if (status.contains("Error") || status.contains("finalizado") || status.contains("Desconectado")) {
                binding.progressBarScan.visibility = View.GONE
                binding.textViewEstadoScan.text = status
                binding.textViewEstadoScan.visibility = if (deviceAdapter.itemCount == 0) View.VISIBLE else View.GONE
                binding.buttonEscanear.isEnabled = true
            }
        }

        // Observar si ya hay un dispositivo conectado al iniciar la Activity
        bluetoothViewModel.connectedDevice.observe(this) { connectedDevice ->
            if (connectedDevice != null) {
                // Si ya hay un dispositivo conectado, quizás quieras mostrarlo
                // o incluso cerrar esta activity si el objetivo era solo conectar uno.
                // Por ahora, solo un Toast para notificar.
                Toast.makeText(this, "Actualmente conectado a ${getDeviceNameSafe(connectedDevice)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndStartScan() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            checkBluetoothAndStartScan()
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkBluetoothAndStartScan() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no está soportado en este dispositivo.", Toast.LENGTH_LONG).show()
            binding.textViewEstadoScan.text = "Bluetooth no soportado."
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startScanDevices()
        }
    }

    private fun startScanDevices() {
        // Mostrar ProgressBar y actualizar texto
        binding.progressBarScan.visibility = View.VISIBLE
        binding.textViewEstadoScan.text = "Escaneando dispositivos..."
        binding.textViewEstadoScan.visibility = View.VISIBLE
        binding.recyclerViewDispositivos.visibility = View.GONE // Opcional: ocultar mientras escanea
        binding.buttonEscanear.isEnabled = false

        bluetoothViewModel.startScan() // Llama al método del ViewModel
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos Requeridos")
            .setMessage("Esta aplicación necesita permisos de Bluetooth y ubicación (en algunas versiones de Android) para escanear y conectarse a dispositivos Bluetooth. Por favor, otórguelos en la configuración de la aplicación.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                // Abrir la configuración de la app
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                // Puedes mostrar un mensaje indicando que la funcionalidad estará limitada.
            }
            .show()
    }

    // Función de utilidad para obtener el nombre del dispositivo de forma segura
    private fun getDeviceNameSafe(device: BluetoothDevice?): String {
        device?.let {
            return if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                it.name ?: it.address
            } else {
                it.address // O "Nombre no disponible"
            }
        }
        return "Dispositivo desconocido"
    }

    override fun onDestroy() {
        super.onDestroy()
        // No es necesario llamar a bluetoothViewModel.disconnect() aquí
        // a menos que quieras desconectar explícitamente al salir de esta pantalla.
        // El servicio puede seguir conectado en segundo plano si así está diseñado.
    }
}