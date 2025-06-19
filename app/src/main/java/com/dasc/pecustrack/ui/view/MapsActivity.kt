package com.dasc.pecustrack.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModelProvider
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.databinding.ActivityMapsBinding
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
import androidx.core.graphics.get
import androidx.core.graphics.set
import com.dasc.pecustrack.ui.viewmodel.ModoEdicionPoligono
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.maps.android.PolyUtil

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var map: GoogleMap
    private lateinit var viewModel: MapsViewModel

    private var marcadorUbicacionActual: Marker? = null
    private lateinit var cowboyIcon: BitmapDescriptor
    private lateinit var fenceIcon: BitmapDescriptor
    private var centrarMapa = true

    val puntosPoligono = mutableListOf<LatLng>()
    var poligonoDibujado: Polygon? = null
    private var modoEdicion = false
    private var poligonoSeleccionado: Polygon? = null

    private var poligonoEnCreacionVisual: Polygon? = null
    private val poligonosExistentesVisual =
        mutableMapOf<Int, Polygon>() // Key: Tu ID de PoligonoData
    private var idPoligonoResaltadoVisualmente: Int? = null
    private val marcadoresVertices = mutableListOf<Marker>()
    private lateinit var cowIcon: BitmapDescriptor
    private lateinit var cowIconDisabled: BitmapDescriptor
    private lateinit var cowIconWarning: BitmapDescriptor


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cowIcon = bitmapDescriptorFromVector(this, R.drawable.cow)!!
        cowIconDisabled = bitmapDescriptorFromVector(this, R.drawable.cow, 2.5f, true)!!
        cowIconWarning = bitmapDescriptorFromVector(this, R.drawable.cow_warning)!!

        cowboyIcon = bitmapDescriptorFromVector(this, R.drawable.cowboy)!!
        fenceIcon = bitmapDescriptorFromVector(this, R.drawable.ic_location, 1.0f)!!

        viewModel =
            ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application)).get(
                MapsViewModel::class.java
            )

        configurarObservadoresViewModel()
        configurarListenersUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Pedir permisos de ubicación
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }

        viewModel.insertarDispositivosEjemplo()
        viewModel.iniciarActualizacionUbicacion()

    }

    private fun configurarObservadoresViewModel() {


        viewModel.ubicacionActual.observe(this) { location ->
            if (viewModel.modoEdicion.value != ModoEdicionPoligono.NINGUNO)
                return@observe
            val latLng = LatLng(location.latitude, location.longitude)
            actualizarMarcadorUbicacionActual(latLng)
            centrarMapa()
            Log.d("MapsActivity", "Ubicación actual: $latLng")
        }

        viewModel.distanciaTexto.observe(this) { texto ->
            binding.topText.text = texto
        }

        viewModel.dispositivoSeleccionado.observe(this) { dispositivo ->
            if (dispositivo == null) {
                return@observe
            }
            centrarMapa = false
        }

        viewModel.modoEdicion.observe(this) { modo ->
            if (modo == ModoEdicionPoligono.NINGUNO) {
                limpiarMarcadoresDeVertices()
                poligonoEnCreacionVisual?.remove()
                poligonoEnCreacionVisual = null
                // Restaura el estado de fabEditar basado en si hay un polígono seleccionado
                if (viewModel.poligonoSeleccionadoId.value != null) {
                    binding.fabEditar.text = "Editar Área ${viewModel.poligonoSeleccionadoId.value}"
                    binding.fabEditar.setIconResource(R.drawable.ic_edit)
                } else {
                    binding.fabEditar.text = "Agregar Área"
                    binding.fabEditar.setIconResource(R.drawable.ic_add)
                }
                binding.fabOpcionesEdicion.visibility = View.GONE
                binding.fabEditar.visibility = View.VISIBLE

            } else if (modo == ModoEdicionPoligono.CREANDO || modo == ModoEdicionPoligono.EDITANDO) {
                // Asegúrate de que los marcadores de vértices se dibujen/actualicen
                // cuando entramos en estos modos Y el mapa está listo.
                if (::map.isInitialized) {
                    actualizarMarcadoresDeVertices(
                        viewModel.puntosPoligonoActual.value ?: emptyList()
                    )
                }
                binding.fabOpcionesEdicion.visibility = View.VISIBLE
                binding.fabEditar.visibility = View.GONE
                if (modo == ModoEdicionPoligono.CREANDO) {
                    binding.topText.text = "Creando nueva área..."
                    desresaltarPoligonoExistenteSiHay() // Si empezamos a crear, deseleccionamos visualmente
                } else { // EDITANDO
                    binding.topText.text =
                        "Editando Área ${viewModel.poligonoSeleccionadoId.value ?: ""}"
                    // No necesariamente des-resaltamos aquí, ya que estamos editando el resaltado.
                }
            }
        }

        viewModel.puntosPoligonoActual.observe(this) { puntos ->
            if (!::map.isInitialized) return@observe

            // 1. Dibuja/actualiza el polígono en creación/edición (como ya lo haces)
            poligonoEnCreacionVisual?.remove()
            if (puntos.size >= 2 && (viewModel.modoEdicion.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicion.value == ModoEdicionPoligono.EDITANDO)) {
                poligonoEnCreacionVisual = map.addPolygon(
                    PolygonOptions()
                        .addAll(puntos)
                        .strokeColor(Color.GREEN)
                        .fillColor(0x2200FF00)
                        .strokeWidth(3f)
                        .zIndex(1f)
                )
            } else {
                poligonoEnCreacionVisual = null // No dibujar polígono si hay menos de 2 puntos
            }

            // 2. Dibuja/actualiza los marcadores/círculos de los vértices
            actualizarMarcadoresDeVertices(puntos)
        }

        viewModel.poligonoSeleccionadoId.observe(this) { idSeleccionado ->
            desresaltarPoligonoExistenteSiHay() // Limpia el resaltado anterior

            if (idSeleccionado != null) {
                val polygonVisual = poligonosExistentesVisual[idSeleccionado]
                polygonVisual?.let {
                    it.strokeColor = Color.RED
                    it.fillColor = 0x22FF0000 // Resaltar
                    it.zIndex = 0.5f // Asegurar que otros polígonos no lo tapen al seleccionarlo
                    idPoligonoResaltadoVisualmente = idSeleccionado
                }
                binding.fabEditar.text = "Editar Área $idSeleccionado"
                binding.fabEditar.setIconResource(R.drawable.ic_edit)
                Log.d("MapsActivity", "Polígono seleccionado: $idSeleccionado")
            } else {
                // No hay polígono seleccionado, restaurar texto del FAB si no estamos creando
                if (viewModel.modoEdicion.value == ModoEdicionPoligono.NINGUNO) {
                    binding.fabEditar.text = "Agregar Área"
                    binding.fabEditar.setIconResource(R.drawable.ic_add)
                }
            }
        }


    }

    private fun actualizarMarcadoresDeVertices(puntos: List<LatLng>) {
        Log.d("MapsActivity", "Actualizando marcadores de vértices: ${puntos.size} puntos")
        if (!::map.isInitialized) return

        limpiarMarcadoresDeVertices() // Limpia los marcadores antiguos

        if (viewModel.modoEdicion.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            puntos.forEachIndexed { index, punto ->
                val markerOptions = MarkerOptions()
                    .position(punto)
                    .draggable(true)
                    .anchor(0.5f, 0.5f) // Centrar el icono del marcador
                    // Puedes usar un icono personalizado para el vértice:
                    // .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_vertex_handle))
                    // O crear un círculo pequeño mediante un BitmapDescriptor programáticamente:
                    .icon(fenceIcon)

                val marker = map.addMarker(markerOptions)
                if (marker != null) {
                    marker.tag = index // Almacenar el índice del punto en el tag del marcador
                    marcadoresVertices.add(marker)
                }
            }
        }
    }

    private fun limpiarMarcadoresDeVertices() {
        marcadoresVertices.forEach { it.remove() }
        marcadoresVertices.clear()
    }

    private fun desresaltarPoligonoExistenteSiHay() {
        idPoligonoResaltadoVisualmente?.let { idResaltado ->
            poligonosExistentesVisual[idResaltado]?.let { poly ->
                poly.strokeColor = Color.BLUE // Color original
                poly.fillColor = 0x220000FF   // Color original
                poly.zIndex = 0f
            }
        }
        idPoligonoResaltadoVisualmente = null
    }


    private fun configurarListenersUI() {
        binding.btnCentrar.setOnClickListener {
            centrarMapa = true
            centrarMapa()
            binding.btnCentrar.visibility = View.GONE
        }

        binding.fabEditar.setOnClickListener {
            val modoActual = viewModel.modoEdicion.value
            val idSeleccionado = viewModel.poligonoSeleccionadoId.value

            if (modoActual == ModoEdicionPoligono.NINGUNO) {
                if (idSeleccionado != null) {
                    // Si hay un polígono seleccionado, y el modo es NINGUNO,
                    // pasa a modo de edición de puntos para ESE polígono.
                    viewModel.iniciarEdicionPuntosPoligonoSeleccionado()
                } else {
                    // No hay polígono seleccionado, y modo NINGUNO,
                    // inicia la creación de un nuevo polígono.
                    viewModel.iniciarModoCreacionPoligono()
                }
            }
        }

        binding.fabFinalizar.setOnClickListener {
            Log.d("MapsActivity", "Finalizando polígono actual")
            viewModel.guardarPoligonoActual() // El ViewModel decidirá si es nuevo o existente
        }

        binding.fabReiniciar.setOnClickListener {
            if (viewModel.modoEdicion.value == ModoEdicionPoligono.CREANDO) {
                // Si estamos creando un polígono, reiniciamos el proceso
                viewModel.reiniciarPoligonoActual()
            } else if (viewModel.modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
                AlertDialog.Builder(this)
                    .setTitle("Eliminar Área")
                    .setMessage("¿Deseas eliminar esta área?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.eliminarPoligonoSeleccionado()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        binding.fabDeshacer.setOnClickListener {
            viewModel.deshacerUltimoPunto()
        }

        // (Opcional) Un botón de cancelar explícito si fabEditar no cumple esa función
        // binding.fabCancelarEdicion.setOnClickListener {
        //     viewModel.cancelarCreacionEdicionPoligono()
        // }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        //map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.uiSettings.isCompassEnabled = true

        val posicionDefault = LatLng(24.1426, -110.3128) // La Paz, BCS
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicionDefault, 12f))

        googleMap.setOnCameraMoveStartedListener(
            object : GoogleMap.OnCameraMoveStartedListener {
                override fun onCameraMoveStarted(var1: Int) {
                    if (var1 == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        centrarMapa = false
                        binding.btnCentrar.visibility = View.VISIBLE
                    } else {
                        centrarMapa = true
                        binding.btnCentrar.visibility = View.GONE
                    }
                }
            }
        )

        googleMap.setOnMapClickListener { puntoClickeado ->
            val modoActual = viewModel.modoEdicion.value
            if (modoActual == ModoEdicionPoligono.CREANDO || modoActual == ModoEdicionPoligono.EDITANDO) {
                viewModel.añadirPuntoAPoligonoActual(puntoClickeado)
            } else if (idPoligonoResaltadoVisualmente != null) {
                // Si hay un polígono resaltado y se hace clic en el mapa, lo des-resaltamos
                viewModel.seleccionarPoligonoPorId(null) // Llama al VM para limpiar la selección lógica
            }
            // No hacer nada más si no estamos en modo edición ni des-seleccionando
        }

        googleMap.setOnPolygonClickListener { polygonApi ->
            val idPoligonoClickeado = polygonApi.tag as? Int
            if (idPoligonoClickeado != null) {
                viewModel.seleccionarPoligonoPorId(idPoligonoClickeado)
            }
        }

        googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
                // Opcional: Cambiar apariencia del marcador/vértice al iniciar arrastre
                val indice = marker.tag as? Int ?: return
                if (viewModel.modoEdicion.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                }
            }

            override fun onMarkerDrag(marker: Marker) {
                val indice = marker.tag as? Int ?: return
                // Actualizar el polígono visual en tiempo real mientras se arrastra (opcional, puede ser costoso)
                // viewModel.actualizarPuntoPoligono(indice, marker.position)
                // Por ahora, solo actualizaremos al finalizar el arrastre.

                // Si quieres que el polígono se actualice en tiempo real (puede parpadear o ser lento):
                val puntosActuales = viewModel.puntosPoligonoActual.value?.toMutableList() ?: return
                if (indice >= 0 && indice < puntosActuales.size) {
                    puntosActuales[indice] = marker.position
                    poligonoEnCreacionVisual?.points =
                        puntosActuales // Actualiza el polígono visual directamente
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                val indice = marker.tag as? Int ?: return
                viewModel.actualizarPuntoPoligono(indice, marker.position)
                // El LiveData se actualizará y el observador en `puntosPoligonoActual`
                // se encargará de redibujar todo (polígono y marcadores).
            }
        })

        googleMap.setOnMarkerClickListener { marker ->
            val indiceVertice = marker.tag as? Int // Verifica si es un marcador de vértice
            if (viewModel.modoEdicion.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
                if (indiceVertice != null) {
                    // Lógica para eliminar el vértice al hacer clic en él
                    // Mostrar un diálogo de confirmación antes de eliminar sería una buena idea
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar Vértice")
                        .setMessage("¿Deseas eliminar este vértice?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            viewModel.eliminarPuntoPoligono(indiceVertice)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    return@setOnMarkerClickListener true // Evento consumido
                }
            }

            // Si no es un marcador de vértice, o no estamos en modo edición,
            // dejar que el listener original de marcadores (para dispositivos) maneje el clic.
            val dispositivo =
                viewModel.markerDispositivoMap[marker] // Revisa si esta lógica aún aplica o se sobrescribe
            if (dispositivo != null) {
                viewModel.seleccionarDispositivo(dispositivo)
                mostrarInfoDispositivo(dispositivo)
                centrarDispositivoEnMapa(dispositivo)
                return@setOnMarkerClickListener true // Evento consumido
            }

            return@setOnMarkerClickListener false // Dejar que otros listeners manejen si no es nuestro caso
        }

        viewModel.poligonos.observe(this) { listaPoligonosGuardados ->
            // Limpia polígonos viejos del mapa (una forma simple, podría ser más eficiente)
            poligonosExistentesVisual.values.forEach { it.remove() }
            poligonosExistentesVisual.clear()

            listaPoligonosGuardados.forEach { poligonoData ->
                val options = PolygonOptions()
                    .addAll(poligonoData.puntos)
                    .strokeColor(Color.BLUE)
                    .fillColor(0x220000FF)
                    .strokeWidth(2f)
                    .clickable(true)

                val mapPolygon = map.addPolygon(options)
                mapPolygon.tag = poligonoData.id // ¡Importante para el OnPolygonClickListener!
                poligonosExistentesVisual[poligonoData.id] = mapPolygon

                // Si este polígono es el que está seleccionado en el ViewModel, resaltarlo
                if (poligonoData.id == viewModel.poligonoSeleccionadoId.value) {
                    mapPolygon.strokeColor = Color.RED
                    mapPolygon.fillColor = 0x22FF0000
                    idPoligonoResaltadoVisualmente = poligonoData.id
                }
            }
        }

        viewModel.verificarEstadoDispositivos()

        viewModel.dispositivos.observe(this) { lista ->
            if (!::map.isInitialized) {
                return@observe
            }
            map.clear()
            for (dispositivo in lista) {
                val icon = if (dispositivo.activo) cowIcon
                else if (!dispositivo.dentroDelArea)
                    cowIconWarning
                else cowIconDisabled
                val marcador = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(dispositivo.latitud, dispositivo.longitud))
                        .title(dispositivo.nombre)
                        .snippet(dispositivo.descripcion ?: "")
                        .icon(icon)
                )
                if (marcador != null) {
                    viewModel.markerDispositivoMap[marcador] = dispositivo
                }
            }
            Log.d("MapsActivity", "Dispositivos cargados: ${lista.size}")
            Log.d("MapsActivity", "Dispositivos fuera del área: ${lista.count { !it.dentroDelArea }}")
            binding.textTotalDispositivos.text = getString(R.string.dispositivos_total, lista.size)
            binding.textDispositivosFueraRango.text = getString(
                R.string.dispositivos_fuera_rango,
                lista.count { !it.dentroDelArea }
            )
            binding.textDispositivosActivos.text = getString(
                R.string.dispositivos_activos,
                lista.count { it.activo }
            )
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

    fun bitmapDescriptorFromVector(
        context: Context,
        vectorResId: Int,
        scale: Float = 2.5f,
        grayscale: Boolean = false
    ): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth * scale.toInt(),
            vectorDrawable.intrinsicHeight * scale.toInt()
        )
        val bitmap = createBitmap(
            vectorDrawable.intrinsicWidth * scale.toInt(),
            vectorDrawable.intrinsicHeight * scale.toInt()
        )
        if (grayscale) {
            val canvas = Canvas(bitmap)
            vectorDrawable.draw(canvas)
            // Convert to grayscale
            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    val pixel = bitmap[x, y]
                    val r = (pixel shr 16 and 0xff) * 0.3
                    val g = (pixel shr 8 and 0xff) * 0.59
                    val b = (pixel and 0xff) * 0.11
                    val gray = (r + g + b).toInt()
                    bitmap[x, y] =
                        (pixel and 0xff000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
                }
            }
        } else {
            vectorDrawable.draw(Canvas(bitmap))
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun centrarMapa() {
        if (!centrarMapa || modoEdicion)
            return
        val bounds = viewModel.obtenerBoundsParaMapa() ?: return

        val padding = 150
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        map.animateCamera(cu, object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                binding.btnCentrar.visibility = View.GONE
                centrarMapa = false
            }

            override fun onCancel() {
                Log.d("MapsActivity", "Centrado de mapa cancelado")
            }
        })
    }

    private fun centrarDispositivoEnMapa(dispositivo: Dispositivo) {
        val bounds = viewModel.obtenerBoundsParaDispositivo(dispositivo) ?: return

        val padding = 150
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        map.animateCamera(cu)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkDispositivosRunnable = object : Runnable {
        override fun run() {
            viewModel.verificarEstadoDispositivos()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(checkDispositivosRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkDispositivosRunnable)
    }


}