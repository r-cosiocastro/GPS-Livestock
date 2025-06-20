package com.dasc.pecustrack.ui.view

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.dasc.pecustrack.data.model.Dispositivo // Asegúrate que la ruta sea correcta
import com.dasc.pecustrack.databinding.BottomSheetEditDispositivoBinding // ViewBinding
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel // Asegúrate que la ruta sea correcta


class EditDispositivoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditDispositivoBinding? = null
    private val binding get() = _binding!!

    // Usar activityViewModels si el ViewModel es compartido con la Activity
    private val viewModel: MapsViewModel by activityViewModels()

    private var dispositivoActual: Dispositivo? = null

    companion object {
        const val TAG = "EditDispositivoBottomSheet"
        private const val ARG_DISPOSITIVO = "dispositivo_a_editar"

        fun newInstance(dispositivo: Dispositivo): EditDispositivoBottomSheet {
            val fragment = EditDispositivoBottomSheet()
            val args = Bundle()
            args.putParcelable(ARG_DISPOSITIVO, dispositivo)
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

        if (dispositivoActual == null) {
            // Si no se pasó un dispositivo para editar, cerramos el diálogo
            Toast.makeText(context, "Error: No se proporcionó dispositivo para editar.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        dispositivoActual?.let { disp ->
            binding.editTextNombre.setText(disp.nombre)
            binding.editTextDescripcion.setText(disp.descripcion)
            // Aquí podrías configurar otros campos si los tuvieras en el layout de edición
            // Por ejemplo, un Switch para 'activo'
            // binding.switchActivo.isChecked = disp.activo
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

        // Obtener el dispositivo original. Es importante que este sea el mismo objeto
        // (o una copia con el mismo ID) que está siendo observado por el otro BottomSheet.
        // Lo ideal es que el ViewModel maneje la instancia actual del dispositivo seleccionado.

        // Supongamos que viewModel.dispositivoSeleccionado.value es el que se está editando
        /*
        viewModel.dispositivoSeleccionado.value?.let { dispositivoOriginal ->
            val dispositivoActualizado = dispositivoOriginal.copy(
                nombre = nombreNuevo,
                descripcion = descripcionNueva
                // Asegúrate de copiar todos los demás campos que no se editan
            )
            // Llama a la función del ViewModel para actualizar
            viewModel.actualizarDetallesDispositivo(dispositivoActualizado)
            dismiss() // Cierra el diálogo de edición
        } ?: run {
            // Manejar el caso donde dispositivoSeleccionado.value es nulo, aunque no debería ocurrir
            // si el diálogo de edición se abrió correctamente.
            Toast.makeText(context, "Error: No se pudo encontrar el dispositivo a editar", Toast.LENGTH_SHORT).show()
            dismiss()
        }

         */

        dispositivoActual?.let { dispositivoOriginal ->
            val dispositivoEditado = dispositivoOriginal.copy( // Usa .copy() en el objeto original
                nombre = nombreNuevo,
                descripcion = descripcionNueva,
                // activo = esActivoNuevo, // Si editas el estado activo
                // Mantén los otros campos que no se editan desde el original
                // latitud, longitud, dentroDelArea usualmente no se editan aquí
                // ultimaConexion = System.currentTimeMillis() // Actualiza el timestamp si tienes este campo
            )
            viewModel.actualizarDetallesDispositivo(dispositivoEditado)
            dismiss() // Cierra el diálogo de edición
        } ?: run {
            Toast.makeText(context, "Error: No se pudo encontrar el dispositivo original a editar", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dispositivoActual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_DISPOSITIVO, Dispositivo::class.java)
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