package com.dasc.pecustrack.ui.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.dasc.pecustrack.data.model.Dispositivo // Asegúrate que la ruta sea correcta
import com.dasc.pecustrack.databinding.BottomSheetEditDispositivoBinding // ViewBinding
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel // Asegúrate que la ruta sea correcta


class EditDispositivoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditDispositivoBinding? = null
    private val binding get() = _binding!!

    // Usar activityViewModels si el ViewModel es compartido con la Activity
    private val mapsViewModel: MapsViewModel by activityViewModels()

    private var dispositivoAEditar: Dispositivo? = null

    companion object {
        const val TAG = "EditDispositivoBottomSheet"
        private const val ARG_DISPOSITIVO_ID = "dispositivo_id"

        fun newInstance(dispositivoId: Int): EditDispositivoBottomSheet {
            val fragment = EditDispositivoBottomSheet()
            val args = Bundle()
            args.putInt(ARG_DISPOSITIVO_ID, dispositivoId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditDispositivoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dispositivoId = arguments?.getInt(ARG_DISPOSITIVO_ID)
        if (dispositivoId == null) {
            // Manejar error o cerrar el diálogo si no hay ID
            dismiss()
            return
        }

        // Observa la lista de dispositivos para encontrar el que se va a editar
        // O mejor, si tienes un LiveData para el dispositivo seleccionado en el ViewModel, úsalo.
        // Aquí asumimos que lo buscamos en la lista completa por ID:
        mapsViewModel.dispositivos.observe(viewLifecycleOwner) { listaDispositivos ->
            dispositivoAEditar = listaDispositivos?.find { it.id == dispositivoId }
            dispositivoAEditar?.let { dispositivo ->
                binding.editTextNombre.setText(dispositivo.nombre)
                binding.editTextDescripcion.setText(dispositivo.descripcion)
            }
        }
        // Si tienes un LiveData específico para el dispositivo seleccionado en MapsViewModel:
        // mapsViewModel.dispositivoSeleccionadoLiveData.observe(viewLifecycleOwner) { dispositivo ->
        //     dispositivoAEditar = dispositivo
        //     dispositivoAEditar?.let {
        //         binding.editTextNombre.setText(it.nombre)
        //         binding.editTextDescripcion.setText(it.descripcion)
        //         // Si el dispositivo seleccionado se vuelve nulo mientras el diálogo está abierto, cierra el diálogo.
        //         if (it == null) dismiss()
        //     } ?: dismiss() // Cierra si el dispositivo es nulo
        // }


        binding.buttonGuardar.setOnClickListener {
            guardarCambios()
        }

        binding.buttonCancelar.setOnClickListener {
            dismiss()
        }
    }

    private fun guardarCambios() {
        val nombreNuevo = binding.editTextNombre.text.toString().trim()
        val descripcionNueva = binding.editTextDescripcion.text.toString().trim()

        if (nombreNuevo.isEmpty()) {
            binding.textFieldNombreLayout.error = "El nombre no puede estar vacío"
            return
        } else {
            binding.textFieldNombreLayout.error = null
        }

        dispositivoAEditar?.let { dispositivoOriginal ->
            val dispositivoActualizado = dispositivoOriginal.copy(
                nombre = nombreNuevo,
                descripcion = descripcionNueva
            )
            mapsViewModel.actualizarDetallesDispositivo(dispositivoActualizado) // Necesitarás esta función en tu ViewModel
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}