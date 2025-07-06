package com.rafaelcosio.gpslivestock.ui.view

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
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rafaelcosio.gpslivestock.R
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.ui.viewmodel.MapsViewModel
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper.TIPO_ANIMAL_CABALLO
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper.TIPO_ANIMAL_CABRA
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper.TIPO_ANIMAL_CERDO
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper.TIPO_ANIMAL_OVEJA
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper.TIPO_ANIMAL_VACA
import com.rafaelcosio.gpslivestock.utils.StringFormatUtils.formatearTiempoConexion
import com.rafaelcosio.gpslivestock.utils.TextUtils.boldTitle
import com.rafaelcosio.gpslivestock.utils.TextUtils.boldTitleFromResource
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class DetailsRastreadorBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: MapsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_rastreador, container, false)
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
        val btnEliminar = view.findViewById<Button>(R.id.btnEliminar)

        viewModel.distanciaTexto.observe(viewLifecycleOwner) { texto ->
            textDistancia.text = boldTitle("Distancia: ", texto)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rastreadorSeleccionado.collect { dispositivo ->
                    if (dispositivo != null) {
                        textTitulo.text = dispositivo.nombre
                        textDescripcion.text = dispositivo.descripcion
                        actualizarInformacionDinamica(dispositivo)

                        btnEditar.setOnClickListener {
                            val editSheet = EditRastreadorBottomSheet.newInstance(dispositivo)
                            editSheet.show(parentFragmentManager, EditRastreadorBottomSheet.TAG)
                            Log.d(
                                "DispositivoBottomSheetDialog",
                                "Edit button clicked for device: ${dispositivo.nombre}"
                            )
                        }

                        btnEliminar.setOnClickListener {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Eliminar Dispositivo")
                                .setMessage(
                                    "¿Desea eliminar el dispositivo ${dispositivo.nombre}? " +
                                            "El dispositivo volverá a agregarse si el rastreador lo vuelve a ubicar."
                                )
                                .setPositiveButton("Eliminar") { _, _ ->
                                    viewModel.eliminarDispositivo(dispositivo)
                                    if (isAdded && dialog?.isShowing == true) {
                                        dismissAllowingStateLoss()
                                    }
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()

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
        fun newInstance(rastreador: Rastreador): DetailsRastreadorBottomSheet {
            val fragment = DetailsRastreadorBottomSheet()
            val bundle = Bundle().apply {
                putParcelable("dispositivo", rastreador)
            }
            fragment.arguments = bundle
            return fragment
        }
    }

    private fun actualizarInformacionDinamica(rastreador: Rastreador?) {
        val context = requireContext()
        val textoConexion = formatearTiempoConexion(rastreador?.ultimaConexion)
        val textoEstado = if (rastreador != null) {
            when {
                !rastreador.activo && !rastreador.dentroDelArea -> "Fuera del área e inactivo"
                !rastreador.activo -> "Inactivo"
                !rastreador.dentroDelArea -> "Fuera del área"
                else -> "Activo y dentro del área"
            }
        } else {
            "Desconocido"
        }

        val icono = when (rastreador?.tipoAnimal) {
            TIPO_ANIMAL_VACA -> R.drawable.ic_cow
            TIPO_ANIMAL_CABALLO -> R.drawable.ic_horse
            TIPO_ANIMAL_OVEJA -> R.drawable.ic_sheep
            TIPO_ANIMAL_CABRA -> R.drawable.ic_goat
            TIPO_ANIMAL_CERDO -> R.drawable.ic_pig
            else -> R.drawable.ic_cow
        }

        view?.findViewById<TextView>(R.id.textTitulo)?.setCompoundDrawablesWithIntrinsicBounds(
            icono, 0, 0, 0
        )

        view?.findViewById<TextView>(R.id.textUltConexion)?.text =
            resources.getString(R.string.ultima_conexion, textoConexion)
        view?.findViewById<TextView>(R.id.textEstado)?.text =
            boldTitleFromResource(context, R.string.estado_del_dispositivo, textoEstado)
        view?.findViewById<TextView>(R.id.textEstado)?.setTextColor(
            when {
                rastreador == null -> resources.getColor(R.color.colorEstadoDesconocido, null)
                !rastreador.activo && !rastreador.dentroDelArea -> resources.getColor(R.color.colorEstadoInactivoFueraArea, null)
                !rastreador.activo -> resources.getColor(R.color.colorEstadoInactivo, null)
                !rastreador.dentroDelArea -> resources.getColor(R.color.colorEstadoFueraArea, null)
                else -> resources.getColor(R.color.colorEstadoActivoDentroArea, null)
            }
        )

        view?.findViewById<TextView>(R.id.textTipoAnimal)?.text = boldTitleFromResource(
            context,
            R.string.tipo_de_animal,
            obtenerNombreTipoAnimal(rastreador)
        )
    }

    private fun obtenerNombreTipoAnimal(rastreador: Rastreador?): String {
        return when (rastreador?.tipoAnimal) {
            TIPO_ANIMAL_VACA -> "Vaca"
            TIPO_ANIMAL_CABALLO -> "Caballo"
            TIPO_ANIMAL_OVEJA -> "Oveja"
            TIPO_ANIMAL_CABRA -> "Cabra"
            TIPO_ANIMAL_CERDO -> "Cerdo"
            else -> "Desconocido"
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val actualizarTiempoRunnable = object : Runnable {
        override fun run() {
            val dispositivoActual = viewModel.rastreadorSeleccionado.value
            actualizarInformacionDinamica(dispositivoActual)

            if (isVisible && dispositivoActual != null) {
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.rastreadorSeleccionado.value != null) {
            handler.post(actualizarTiempoRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(actualizarTiempoRunnable)
    }
}