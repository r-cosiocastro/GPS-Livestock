package com.dasc.pecustrack.ui.view

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.dasc.pecustrack.R
import com.dasc.pecustrack.databinding.ActivitySelectDeviceBinding
import com.dasc.pecustrack.ui.adapter.BluetoothDeviceAdapter
import com.dasc.pecustrack.ui.viewmodel.BluetoothViewModel
import com.dasc.pecustrack.utils.LoadingDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectDeviceBinding
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var loadingDialog: LoadingDialog

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
        loadingDialog = LoadingDialog(this@DeviceActivity)

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
            checkBluetoothAndStartScan()
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
            // Toast.makeText(this, "Conectando a ${getDeviceNameSafe(device)}...", Toast.LENGTH_SHORT).show()
            // Podrías querer deshabilitar más clics mientras se conecta.
        }
        binding.recyclerViewDispositivos.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(this@DeviceActivity)
            addItemDecoration(DividerItemDecoration(this@DeviceActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun observeViewModel() {
        bluetoothViewModel.isConnecting.observe(this) {
            if (it) {
                loadingDialog.showLoadingDialog("Conectando...", R.raw.loading)
            } else {
                loadingDialog.dismiss()
            }
        }

        bluetoothViewModel.connectedDevice.observe(this) { device ->
            if (device != null) {
                binding.buttonDesconectar.visibility = View.VISIBLE // Mostrar botón para desconectar
            } else {
                // No hay dispositivo conectado
                binding.buttonDesconectar.visibility = View.GONE
            }
        }

        bluetoothViewModel.currentDeviceName.observe(this) { deviceName ->
            // Actualizar el texto de conexión si cambia el nombre del dispositivo conectado
            if (deviceName != null) {
                binding.textViewConectadoA.text = getString(R.string.device_bluetooth_connected_to, deviceName)
            } else{
                binding.textViewConectadoA.text = getString(R.string.device_bluetooth_disconnected)
            }
        }

        // Opcional: observar el estado de conexión más detallado
        bluetoothViewModel.connectionStatus.observe(this) { status ->
            // Podrías usar este status para mensajes más detallados si es necesario,
            // pero connectedDevice ya te dice si estás o no conectado.
            // binding.someOtherTextView.text = status
            // binding.textViewConectadoA.text = getString(R.string.device_bluetooth_connected_to, status)
        }

        binding.buttonDesconectar.setOnClickListener {
            bluetoothViewModel.disconnect() // Asegúrate que tu ViewModel tiene esta función que llama al servicio
        }

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
            //Toast.makeText(this, "Estado: $status", Toast.LENGTH_LONG).show()
            // Actualizar UI según el estado (ej. ProgressBar, texto)
            if (status.contains("Conectado a")) {
                // Aquí podrías cerrar la activity y devolver el dispositivo conectado
                // o navegar a otra pantalla.
                // Por ejemplo, devolver el dispositivo conectado a la actividad que llamó
                val deviceName = status.substringAfter("Conectado a ")
                //Toast.makeText(this, "Conectado a $deviceName", Toast.LENGTH_LONG).show()

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
                // Toast.makeText(this, "Actualmente conectado a ${getDeviceNameSafe(connectedDevice)}", Toast.LENGTH_SHORT).show()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        // No es necesario llamar a bluetoothViewModel.disconnect() aquí
        // a menos que quieras desconectar explícitamente al salir de esta pantalla.
        // El servicio puede seguir conectado en segundo plano si así está diseñado.
    }
}