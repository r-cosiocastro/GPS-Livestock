package com.rafaelcosio.gpslivestock.ui.view

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.rafaelcosio.gpslivestock.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.databinding.BottomSheetEditRastreadorBinding
import com.rafaelcosio.gpslivestock.ui.viewmodel.MapsViewModel


class EditRastreadorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditRastreadorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var rastreadorActual: Rastreador? = null

    companion object {
        const val TAG = "EditDispositivoBottomSheet"
        private const val ARG_DISPOSITIVO = "dispositivo_a_editar"

        fun newInstance(rastreador: Rastreador): EditRastreadorBottomSheet {
            val fragment = EditRastreadorBottomSheet()
            val args = Bundle()
            args.putParcelable(ARG_DISPOSITIVO, rastreador)
            fragment.arguments = args
            return fragment
        }
    }

    private fun setupTipoAnimalSpinner() {
        val tiposAnimalNombres = resources.getStringArray(R.array.tipos_animal_nombres)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            tiposAnimalNombres
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTipoAnimal.adapter = adapter
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditRastreadorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (rastreadorActual == null) {
            Toast.makeText(context, "Error: No se proporcionó dispositivo para editar.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        setupTipoAnimalSpinner()

        rastreadorActual?.let { disp ->
            binding.editTextNombre.setText(disp.nombre)
            binding.editTextDescripcion.setText(disp.descripcion)
            if (disp.tipoAnimal >= 0 && disp.tipoAnimal < (binding.spinnerTipoAnimal.adapter?.count ?: 0)) {
                binding.spinnerTipoAnimal.setSelection(disp.tipoAnimal)
            } else {
                binding.spinnerTipoAnimal.setSelection(0)
            }
        }

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

        val tipoAnimalNuevo = binding.spinnerTipoAnimal.selectedItemPosition

        rastreadorActual?.let { dispositivoOriginal ->
            val dispositivoEditado = dispositivoOriginal.copy(
                nombre = nombreNuevo,
                descripcion = descripcionNueva,
                tipoAnimal = tipoAnimalNuevo
            )
            viewModel.actualizarDetallesDispositivo(dispositivoEditado)
            dismiss()
        } ?: run {
            Toast.makeText(context, "Error: No se pudo encontrar el dispositivo original a editar", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            rastreadorActual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_DISPOSITIVO, Rastreador::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_DISPOSITIVO)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}