package com.dasc.pecustrack.ui.view

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.net.toUri
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DispositivoBottomSheetDialog : BottomSheetDialogFragment(){
    private lateinit var dispositivo: Dispositivo
    private val viewModel: MapsViewModel by lazy {
        MapsViewModel(requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recuperar objeto desde argumentos
        arguments?.let {
            dispositivo = it.getParcelable("dispositivo", Dispositivo::class.java)!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_dispositivo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val titulo = view.findViewById<TextView>(R.id.textTitulo)
        val descripcion = view.findViewById<TextView>(R.id.textDescripcion)
        val btnNavegar = view.findViewById<Button>(R.id.btnNavegar)

        titulo.text = dispositivo.nombre
        descripcion.text = dispositivo.descripcion

        btnNavegar.setOnClickListener {
            val uri = "google.navigation:q=${dispositivo.latitud},${dispositivo.longitud}".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
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
}