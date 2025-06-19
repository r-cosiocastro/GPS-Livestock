package com.dasc.pecustrack.ui.view

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DispositivoBottomSheetDialog : BottomSheetDialogFragment(){
    private lateinit var dispositivo: Dispositivo
    private val viewModel: MapsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_dispositivo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val textDistancia = view.findViewById<TextView>(R.id.textDistancia)

        viewModel.distanciaTexto.observe(viewLifecycleOwner) { texto ->
            textDistancia.text = "Distancia: $texto"
        }

        viewModel.dispositivoSeleccionado.observe(viewLifecycleOwner) { dispositivo ->
            view.findViewById<TextView>(R.id.textTitulo).text = dispositivo?.nombre
            view.findViewById<TextView>(R.id.textDescripcion).text = dispositivo?.descripcion
        }

        val btnNavegar = view.findViewById<Button>(R.id.btnNavegar)
        val btnEditar = view.findViewById<Button>(R.id.btnEditar)

        btnNavegar.setOnClickListener {
            val uri = "google.navigation:q=${dispositivo.latitud},${dispositivo.longitud}".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }

        btnEditar.setOnClickListener {
            viewModel.dispositivoSeleccionado.value?.let { dispositivo ->
                val editBottomSheet = EditDispositivoBottomSheet.newInstance(dispositivo.id)
                editBottomSheet.show(parentFragmentManager, EditDispositivoBottomSheet.TAG)
                // Cerrar el BottomSheet actual
                dismiss()
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

    private val handler = Handler(Looper.getMainLooper())
    private val actualizarTiempoRunnable = object : Runnable {
        override fun run() {
            val dispositivo = viewModel.dispositivoSeleccionado.value
            val textoConexion = viewModel.formatearTiempoConexion(dispositivo?.ultimaConexion)
            val textoEstado = if (!dispositivo?.activo!! && !dispositivo.dentroDelArea) {
                "Fuera del área e inactivo"
            } else if(!dispositivo.activo) {
                "Inactivo"
            } else if (!dispositivo.dentroDelArea) {
                "Fuera del área"
            } else {
                "Activo y dentro del área"
            }
            view?.findViewById<TextView>(R.id.textUltConexion)?.text = resources.getString(R.string.ultima_conexion, textoConexion)
            view?.findViewById<TextView>(R.id.textEstado)?.text = resources.getString(R.string.estado_del_dispositivo, textoEstado)

            handler.postDelayed(this, 60_000L) // Repetir cada minuto
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(actualizarTiempoRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(actualizarTiempoRunnable)
    }
}