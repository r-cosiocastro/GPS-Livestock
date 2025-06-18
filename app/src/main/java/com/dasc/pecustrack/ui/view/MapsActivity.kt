package com.dasc.pecustrack.ui.view

import android.Manifest
import android.content.Context
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModelProvider
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var viewModel: MapsViewModel

    private var marcadorUbicacionActual: Marker? = null
    private lateinit var cowboyIcon: BitmapDescriptor
    private var centrarMapa = true

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        cowboyIcon = bitmapDescriptorFromVector(this, R.drawable.cowboy)!!

        viewModel =
            ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application)).get(
                MapsViewModel::class.java
            )

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Pedir permisos de ubicación
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }

        viewModel.ubicacionActual.observe(this) { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            actualizarMarcadorUbicacionActual(latLng)
            centrarMapa()
        }

        viewModel.dispositivoSeleccionado.observe(this) { dispositivo ->
            if (dispositivo == null) {
                centrarMapa = true
                findViewById<TextView>(R.id.top_text)?.text = "Selecciona un dispositivo"
                return@observe
            }

            val ubicacion = viewModel.ubicacionActual.value ?: return@observe
            val destino = Location("destino").apply {
                latitude = dispositivo.latitud
                longitude = dispositivo.longitud
            }
            val distancia = viewModel.calcularDistancia(ubicacion, destino)
            // Si la distancia es menor a 1 kilómetro, mostrar en metros
            if (distancia < 1000) {
                findViewById<TextView>(R.id.top_text)?.text =
                    "Distancia: ${String.format("%.0f m", distancia)}"
            } else {
                findViewById<TextView>(R.id.top_text)?.text =
                    "Distancia: ${String.format("%.2f km", distancia / 1000)}"
            }
            centrarMapa = false
        }

        viewModel.cargarDispositivosEjemplo()
        viewModel.iniciarActualizacionUbicacion()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val cowIcon = bitmapDescriptorFromVector(this, R.drawable.cow)
        val posicionDefault = LatLng(24.1426, -110.3128) // La Paz, BCS
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicionDefault, 12f))


        viewModel.dispositivos.observe(this) { lista ->
            for (dispositivo in lista) {
                val marcador = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(dispositivo.latitud, dispositivo.longitud))
                        .title(dispositivo.nombre)
                        .snippet(dispositivo.descripcion ?: "")
                        .icon(cowIcon)
                )
                if (marcador != null) {
                    viewModel.markerDispositivoMap[marcador] = dispositivo
                }
            }
        }

        map.setOnMarkerClickListener { marker ->
            val dispositivo = viewModel.markerDispositivoMap[marker]
            if (dispositivo != null) {
                viewModel.seleccionarDispositivo(dispositivo)
                mostrarInfoDispositivo(dispositivo)
                centrarDispositivoEnMapa(dispositivo)
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.detenerActualizacionUbicacion()
    }

    private fun actualizarMarcadorUbicacionActual(latLng: LatLng) {
        if (marcadorUbicacionActual == null) {
            marcadorUbicacionActual = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Tu ubicación")
                    .icon(cowboyIcon)
            )
        } else {
            marcadorUbicacionActual?.position = latLng
        }
    }

    private fun mostrarInfoDispositivo(dispositivo: Dispositivo) {
        val bottomSheet = DispositivoBottomSheetDialog.newInstance(dispositivo)
        bottomSheet.show(supportFragmentManager, "DispositivoBottomSheet")
    }

    fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth * 2.5.toInt(),
            vectorDrawable.intrinsicHeight * 2.5.toInt()
        )
        val bitmap = createBitmap(
            vectorDrawable.intrinsicWidth * 2.5.toInt(),
            vectorDrawable.intrinsicHeight * 2.5.toInt()
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun centrarMapa() {
        if (!centrarMapa)
            return
        val bounds = viewModel.obtenerBoundsParaMapa() ?: return

        val padding = 150
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        map.animateCamera(cu)
    }

    private fun centrarDispositivoEnMapa(dispositivo: Dispositivo) {
        val bounds = viewModel.obtenerBoundsParaDispositivo(dispositivo) ?: return

        val padding = 150
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        map.animateCamera(cu)
    }


}