package com.dasc.pecustrack.ui.view

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel
import com.dasc.pecustrack.utils.StringFormatUtils.formatearTiempoConexion
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DispositivoBottomSheetDialog : BottomSheetDialogFragment() {
    private val viewModel: MapsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_dispositivo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textTitulo = view.findViewById<TextView>(R.id.textTitulo)
        val textDescripcion = view.findViewById<TextView>(R.id.textDescripcion)
        val textDistancia = view.findViewById<TextView>(R.id.textDistancia)
        val textUltConexion = view.findViewById<TextView>(R.id.textUltConexion)
        val textEstado = view.findViewById<TextView>(R.id.textEstado)
        val btnNavegar = view.findViewById<Button>(R.id.btnNavegar)
        val btnEditar = view.findViewById<Button>(R.id.btnEditar)

        viewModel.distanciaTexto.observe(viewLifecycleOwner) { texto ->
            textDistancia.text = "Distancia: $texto"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dispositivoSeleccionado.collect { dispositivo ->
                    if (dispositivo != null) {
                        textTitulo.text = dispositivo.nombre
                        textDescripcion.text = dispositivo.descripcion
                        actualizarInformacionDinamica(dispositivo)

                        btnEditar.setOnClickListener {
                            val editSheet = EditDispositivoBottomSheet.newInstance(dispositivo)
                            editSheet.show(parentFragmentManager, EditDispositivoBottomSheet.TAG)
                            Log.d(
                                "DispositivoBottomSheetDialog",
                                "Edit button clicked for device: ${dispositivo.nombre}"
                            )
                        }

                        btnNavegar.setOnClickListener {
                            val uri =
                                "google.navigation:q=${dispositivo.latitud},${dispositivo.longitud}".toUri()
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            intent.setPackage("com.google.android.apps.maps")
                            startActivity(intent)
                        }
                    } else {
                        if (isAdded && dialog?.isShowing == true) {
                            dismissAllowingStateLoss()
                        }
                    }


                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.deseleccionarDispositivo()
    }

    companion object {
        fun newInstance(dispositivo: Dispositivo): DispositivoBottomSheetDialog {
            val fragment = DispositivoBottomSheetDialog()
            val bundle = Bundle().apply {
                putParcelable("dispositivo", dispositivo)
            }
            fragment.arguments = bundle
            return fragment
        }
    }

    private fun actualizarInformacionDinamica(dispositivo: Dispositivo?) {
        val textoConexion = formatearTiempoConexion(dispositivo?.ultimaConexion)
        val textoEstado = if (dispositivo != null) {
            when {
                !dispositivo.activo && !dispositivo.dentroDelArea -> "Fuera del área e inactivo"
                !dispositivo.activo -> "Inactivo"
                !dispositivo.dentroDelArea -> "Fuera del área"
                else -> "Activo y dentro del área"
            }
        } else {
            "Desconocido"
        }
        view?.findViewById<TextView>(R.id.textUltConexion)?.text =
            resources.getString(R.string.ultima_conexion, textoConexion)
        view?.findViewById<TextView>(R.id.textEstado)?.text =
            resources.getString(R.string.estado_del_dispositivo, textoEstado)
        view?.findViewById<TextView>(R.id.textEstado)?.setTextColor(
            when {
                dispositivo == null -> resources.getColor(R.color.colorEstadoDesconocido, null)
                !dispositivo.activo && !dispositivo.dentroDelArea -> resources.getColor(R.color.colorEstadoInactivoFueraArea, null)
                !dispositivo.activo -> resources.getColor(R.color.colorEstadoInactivo, null)
                !dispositivo.dentroDelArea -> resources.getColor(R.color.colorEstadoFueraArea, null)
                else -> resources.getColor(R.color.colorEstadoActivoDentroArea, null)
            }
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val actualizarTiempoRunnable = object : Runnable {
        override fun run() {
            val dispositivoActual = viewModel.dispositivoSeleccionado.value
            actualizarInformacionDinamica(dispositivoActual)

            if (isVisible && dispositivoActual != null) {
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.dispositivoSeleccionado.value != null) {
            handler.post(actualizarTiempoRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(actualizarTiempoRunnable)
    }
}