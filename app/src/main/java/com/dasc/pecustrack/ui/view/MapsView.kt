package com.dasc.pecustrack.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dasc.pecustrack.R
import com.dasc.pecustrack.data.model.Rastreador
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.databinding.ActivityMapsBinding
import com.dasc.pecustrack.bluetooth.BluetoothService
import com.dasc.pecustrack.bluetooth.BluetoothStateManager
import com.dasc.pecustrack.ui.viewmodel.MapsViewModel
import com.dasc.pecustrack.ui.viewmodel.ModoEdicionPoligono
import com.dasc.pecustrack.utils.LoadingDialog
import com.dasc.pecustrack.utils.MarcadorIconHelper
import com.dasc.pecustrack.utils.NotificationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MapsView : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLoadedCallback {

    @Inject
    lateinit var bluetoothStateManager: BluetoothStateManager

    private lateinit var binding: ActivityMapsBinding
    private lateinit var googleMap: GoogleMap
    private val viewModel: MapsViewModel by viewModels()

    private var marcadorUbicacionActual: Marker? = null
    private var centrarMapa = true

    val puntosPoligono = mutableListOf<LatLng>()
    var poligonoDibujado: Polygon? = null
    private var modoEdicion = false
    private var poligonoSeleccionado: Polygon? = null

    private val marcadoresDeDispositivosActuales = mutableListOf<Marker>()

    private var poligonoEnCreacionVisual: Polygon? = null
    private val poligonosExistentesVisual =
        mutableMapOf<Int, Polygon>() // Key: Tu ID de PoligonoData
    private var idPoligonoResaltadoVisualmente: Int? = null
    private val marcadoresVertices = mutableListOf<Marker>()

    private lateinit var loadingDialog: LoadingDialog
    private var currentToast: Toast? = null

    private val marcadoresVerticesActuales = mutableListOf<Marker>()

    var isExpanded = false


    override fun onStop() {
        super.onStop()
        currentToast?.cancel()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadingDialog = LoadingDialog(this@MapsView)
        loadingDialog.showLoadingDialog("Cargando mapa", R.raw.cow)

        Intent(this, BluetoothService::class.java).also { serviceIntent ->
            startService(serviceIntent) // Asegura que el servicio se inicie y pueda vivir más allá de la Activity
        }

        binding.fabBluetooth.setOnClickListener {
            startActivity(Intent(this, BluetoothDevicesView::class.java))
        }

        val bottomSheet = binding.bottomCardDeviceDetails
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 220
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val arrowIcon = findViewById<ImageView>(R.id.arrow_icon)

        val offsetExpanded = 0f
        val offsetCollapsed = -80f

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        //arrowIcon.setImageResource(R.drawable.ic_arrow_down)
                        isExpanded = true
                        binding.fabBluetooth.animate()
                            .translationY(offsetExpanded)
                            .setDuration(250)
                            .start()
                    }

                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        //arrowIcon.setImageResource(R.drawable.ic_arrow_up)
                        isExpanded = false
                        binding.fabBluetooth.animate()
                            .translationY(offsetCollapsed)
                            .setDuration(250)
                            .start()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                arrowIcon.rotation = 180 * slideOffset
                val interpolatedOffset = offsetCollapsed * (1 - slideOffset)
                binding.fabBluetooth.translationY = interpolatedOffset
            }
        })

        arrowIcon.setOnClickListener {
            if (isExpanded) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }



        val not = NotificationHelper.createBasicNotification(
            this,
            NotificationHelper.BLUETOOTH_SERVICE_CHANNEL_ID,
            "¡Se te salieron las vacas wey!",
            "Córrele que te las van a hacer carnitas"
        )

        NotificationHelper.showNotification(this, NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, not)

        configurarObservadoresViewModel()
        configurarListenersUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        lifecycleScope.launch {
            // repeatOnLifecycle asegura que la colección solo ocurra cuando
            // el ciclo de vida está en el estado especificado (e.g., STARTED)
            // y se cancela y reinicia automáticamente.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userLocation.collect { location -> // Usa collect o collectLatest
                    // El código aquí se ejecutará cada vez que userLocation emita un nuevo valor
                    location?.let {
                        Log.d("MapsActivity", "Ubicación del usuario observada en Activity: $it")
                        // moverCamaraAUbicacion(LatLng(it.latitude, it.longitude))
                        // actualizarMarcadorUsuario(LatLng(it.latitude, it.longitude))
                        if (viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO)
                            return@collect
                        val latLng = LatLng(location.latitude, location.longitude)
                        //actualizarMarcadorUbicacionActual(latLng)
                        centrarMapa()
                    }
                }
            }
        }

        // viewModel.insertarDispositivosEjemplo()

    }

    private fun configurarObservadoresViewModel() {

        viewModel.distanciaTexto.observe(this) { texto ->
            // binding.topText.text = texto
        }

        lifecycleScope.launch { // Necesitas un scope de corrutina
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Y repetir en el ciclo de vida apropiado
                viewModel.rastreadorSeleccionado.collect { dispositivo ->
                    if (dispositivo != null) { // Si hay un dispositivo seleccionado
                        centrarMapa = false
                        Log.d("MapsActivity", "Dispositivo seleccionado: ${dispositivo.id}. Centrar mapa desactivado.")
                        // Aquí podrías añadir lógica adicional si es necesario cuando un dispositivo se selecciona,
                        // como mover la cámara al dispositivo si 'centrarMapa' ya era false.
                        // Sin embargo, si la única acción es desactivar el centrado automático, esto es suficiente.
                    }
                    // No necesitas un 'else' aquí si la única acción es con 'dispositivo != null'
                    // Si necesitaras hacer algo cuando se deselecciona (dispositivo == null), lo añadirías aquí.
                }
            }
        }
    }

    private fun actualizarVisualizacionPoligonoYVertices() {

        val modoActual = viewModel.modoEdicionPoligono.value
        val puntosActuales = viewModel.puntosPoligonoActualParaDibujar.value // Obtiene los puntos más recientes

        Log.d("MapsActivity_Draw", "actualizarVisualizacionPoligonoYVertices: Modo: $modoActual, Puntos: ${puntosActuales.joinToString { "(${it.latitude},${it.longitude})" }}")

        poligonoEnCreacionVisual?.remove()
        poligonoEnCreacionVisual = null
        limpiarMarcadoresDeVerticesVisual()

        if (modoActual == ModoEdicionPoligono.CREANDO || modoActual == ModoEdicionPoligono.EDITANDO) {
            if (puntosActuales.size >= 2) {
                Log.d("MapsActivity_Draw", "Dibujando polígono en creación visual con ${puntosActuales.size} puntos.")
                poligonoEnCreacionVisual = googleMap.addPolygon(
                    PolygonOptions()
                        .addAll(puntosActuales)
                        .strokeColor(Color.GREEN)
                        .fillColor(0x2200FF00)
                        .strokeWidth(5f)
                        .zIndex(1f)
                )
            } else {
                Log.d("MapsActivity_Draw", "No se dibuja polígono en creación, puntos insuficientes: ${puntosActuales.size}")
            }

            if (puntosActuales.isNotEmpty()) {
                Log.d("MapsActivity_Draw", "Actualizando marcadores de vértices visuales con ${puntosActuales.size} puntos.")
                actualizarMarcadoresDeVerticesVisual(puntosActuales, modoActual) // Esta función debe limpiar y redibujar marcadores
            } else {
                Log.d("MapsActivity_Draw", "No hay puntos para dibujar marcadores de vértices.")
            }
        } else {
            Log.d("MapsActivity_Draw", "No en modo CREANDO o EDITANDO. Limpiando visualizaciones.")
        }
    }

    private fun actualizarMarcadoresDeVerticesVisual(puntos: List<LatLng>, modo: ModoEdicionPoligono) {
        // La limpieza ya se hace en actualizarVisualizacionPoligonoYVertices con limpiarMarcadoresDeVerticesVisual()
        // marcadoresVerticesActuales.forEach { it.remove() } <--- Asegúrate que esto está en limpiarMarcadoresDeVerticesVisual()
        // marcadoresVerticesActuales.clear() <--- Y esto también

        Log.d("MapsActivity_Draw", "actualizarMarcadoresDeVerticesVisual: Redibujando ${puntos.size} marcadores. Modo: $modo")
        puntos.forEachIndexed { index, latLng ->
            val markerOptions = MarkerOptions()
                .position(latLng)
                .draggable(modo == ModoEdicionPoligono.EDITANDO || modo == ModoEdicionPoligono.CREANDO)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .anchor(0.5f, 0.5f)

            val marker = googleMap.addMarker(markerOptions)

            if (marker != null) {
                val tagParaAsignar = "vertice_$index"
                marker.tag = tagParaAsignar
                marcadoresVerticesActuales.add(marker)
                Log.d("MapsActivity_Draw", "Vértice $index creado en $latLng. Tag asignado: '$tagParaAsignar'")
            } else {
                Log.e("MapsActivity_Draw", "Error: googleMap.addMarker devolvió null para el vértice $index")
            }
        }
        Log.d("MapsActivity_Draw", "Total de marcadores de vértices visuales ahora: ${marcadoresVerticesActuales.size}")
    }

    private fun limpiarMarcadoresDeVerticesVisual() {
        Log.d("MapsActivity_Draw", "limpiarMarcadoresDeVerticesVisual: Limpiando ${marcadoresVerticesActuales.size} marcadores.")
        marcadoresVerticesActuales.forEach { it.remove() }
        marcadoresVerticesActuales.clear()
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
            val modoActual = viewModel.modoEdicionPoligono.value
            val idSeleccionado = viewModel.poligonoSeleccionadoId.value

            if (modoActual == ModoEdicionPoligono.NINGUNO) {
                if (idSeleccionado != null) {
                    viewModel.iniciarEdicionPuntosPoligonoSeleccionado()
                } else {
                    viewModel.iniciarModoCreacionPoligono()
                }
            }
        }

        binding.fabFinalizar.setOnClickListener {
            Log.d("MapsActivity", "Finalizando polígono actual")
            viewModel.guardarPoligonoEditadoActual()
            actualizarUiPorModoEdicion(viewModel.modoEdicionPoligono.value, viewModel.poligonoSeleccionadoId.value)
            viewModel.verificarEstadoDispositivos()
        }

        binding.fabReiniciar.setOnClickListener {
            if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO) {
                // Si estamos creando un polígono, reiniciamos el proceso
                viewModel.reiniciarPuntosPoligonoActual()
            } else if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO) {
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
            viewModel.deshacerUltimoPuntoPoligono()
        }

        // (Opcional) Un botón de cancelar explícito si fabEditar no cumple esa función
        binding.fabCancelar.setOnClickListener {
            viewModel.cancelarCreacionEdicionPoligono()
        }
    }

    override fun onMapLoaded() {
        loadingDialog.dismiss()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(map: GoogleMap) {
        this@MapsView.googleMap = map
        map.setOnMapLoadedCallback(this)

        googleMap.setMapStyle(
            com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                this,
                R.raw.map_style
            )
        )

        map.isMyLocationEnabled = true


        val posicionDefault = LatLng(24.1426, -110.3128) // La Paz, BCS
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(posicionDefault, 12f))

        map.setOnCameraMoveStartedListener { var1 ->
            if (var1 == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                centrarMapa = false
                binding.btnCentrar.visibility = View.VISIBLE
            } else {
                centrarMapa = true
                binding.btnCentrar.visibility = View.GONE
            }
        }

        map.setOnMapClickListener { latLng ->
            // Si estamos en modo de creación o edición de polígonos, añadimos un punto.
            if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO ||
                viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO) {
                viewModel.agregarPuntoAPoligonoActual(latLng)
            } else {
                // Si NO estamos en modo edición de polígonos:
                // 1. Deseleccionamos cualquier polígono que pudiera estar visualmente resaltado.
                //    (viewModel.deseleccionarPoligono() se encargará de poner poligonoSeleccionadoId a null,
                //     lo que debería hacer que el observador quite el resaltado)
                viewModel.deseleccionarPoligono()

                // 2. Deseleccionamos cualquier dispositivo que pudiera estar seleccionado.
                viewModel.deseleccionarDispositivo()

                Log.d("MapsActivity", "Clic en mapa (no en modo edición): Polígono y dispositivo deseleccionados.")
            }
        }

        map.setOnPolygonClickListener { polygonApi ->
            val idPoligonoClickeado = polygonApi.tag as? Int // Asegúrate que el tag de tus polígonos guardados es Int
            if (idPoligonoClickeado != null) {
                if (viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO) {
                    // Si estamos en medio de una creación/edición, quizás no queremos
                    // permitir la selección de otro polígono, o podríamos querer cancelar la edición actual.
                    // Por ahora, lo ignoraremos si estamos editando/creando.
                    Log.d("MapsActivity", "Clic en polígono $idPoligonoClickeado ignorado (modo edición activo).")
                    return@setOnPolygonClickListener
                }
                viewModel.seleccionarPoligonoPorId(idPoligonoClickeado)
                viewModel.deseleccionarDispositivo() // También deselecciona dispositivo si seleccionas un polígono
                Log.d("MapsActivity", "Polígono $idPoligonoClickeado seleccionado por clic directo.")
            }
        }

        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
                val tag = marker.tag as? String
                if (tag != null && tag.startsWith("vertice_") && viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO) {
                    Log.d("MapsActivity_Drag", "onMarkerDragStart: Vértice $tag")
                    // Opcional: Cambiar apariencia
                }
            }

            override fun onMarkerDrag(marker: Marker) {
                if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.NINGUNO) return

                Log.d("MapsActivity_Drag", "onMarkerDrag: Marker ID: ${marker.id}, Position: ${marker.position}, Tag: ${marker.tag}")

                /*
                val tag = marker.tag as? String
                if (tag != null && tag.startsWith("vertice_")) {
                    try {
                        val indice = tag.substringAfter("vertice_").toInt()
                        Log.d("MapsActivity_Drag", "onMarkerDrag: Vértice $tag (índice $indice) en ${marker.position}")
                        } catch (e: NumberFormatException) {
                        Log.e("MapsActivity_Drag", "Error al parsear índice en onMarkerDrag", e)
                    }
                } else {
                    Log.d("MapsActivity_Drag", "onMarkerDrag: Tag no es de vértice o es nulo: $tag")
                }

                 */
            }

            override fun onMarkerDragEnd(marker: Marker) {
                if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.NINGUNO) return

                val tag = marker.tag as? String
                if (tag != null && tag.startsWith("vertice_")) {
                    try {
                        val indice = tag.substringAfter("vertice_").toInt()
                        Log.i("MapsActivity_Drag", "onMarkerDragEnd: Vértice $tag (índice $indice) finalizado en ${marker.position}. Estado debería estar actualizado.")
                        // El estado ya se actualizó en onMarkerDrag si usas el Enfoque 1.
                        // Aquí podrías tener lógica como "auto-guardar" si fuera necesario, o simplemente loguear.
                        viewModel.actualizarPuntoPoligonoEnEdicion(indice, marker.position)
                    } catch (e: NumberFormatException) {
                        Log.e("MapsActivity_Drag", "Error al parsear índice en onMarkerDragEnd", e)
                    }
                }
            }
        })

        map.setOnMarkerClickListener { marker ->
            // Si no es un marcador de vértice, o no estamos en modo edición,
            // dejar que el listener original de marcadores (para dispositivos) maneje el clic.
            // Si es un marcador de dispositivo
            val dispositivo = viewModel.markerRastreadorMap[marker] // Asumiendo que aún usas este mapa
            if (dispositivo != null) {
                if (viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO) {
                    Log.d("MapsActivity", "Clic en marcador de dispositivo ignorado (modo edición activo).")
                    return@setOnMarkerClickListener true // Consume el evento
                }
                viewModel.seleccionarDispositivo(dispositivo)
                viewModel.deseleccionarPoligono() // Deselecciona polígono si seleccionas un dispositivo
                mostrarInfoDispositivo(dispositivo) // O la lógica para mostrar el BottomSheet del dispositivo
                centrarDispositivoEnMapa(dispositivo) // Opcional
                viewModel.calcularDistancias()
                Log.d("MapsActivity", "Dispositivo ${dispositivo.id} seleccionado por clic en marcador.")
                return@setOnMarkerClickListener true // Evento consumido
            }

            // Si es un marcador de vértice durante la edición (tu lógica existente)
            val tagVertice = marker.tag as? String
            if (tagVertice != null && tagVertice.startsWith("vertice_") &&
                (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO)) {
                // Lógica para eliminar el vértice al hacer clic en él
                AlertDialog.Builder(this)
                    .setTitle("Eliminar Vértice")
                    .setMessage("¿Deseas eliminar este vértice?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        try {
                            val indice = tagVertice.substringAfter("vertice_").toInt()
                            viewModel.eliminarPuntoPoligono(indice)
                        } catch (e: NumberFormatException) {
                            Log.e("MapsActivity", "Error al parsear índice del marcador de vértice para eliminar", e)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return@setOnMarkerClickListener true // Evento consumido
            }
            return@setOnMarkerClickListener false // No consumido, deja que otros listeners actúen si es necesario
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Colector para Polígonos
                launch {
                    viewModel.poligonos.collect { listaPoligonosGuardados ->
                        // Lógica para dibujar polígonos...
                        Log.d("MapsActivity", "Polígonos actualizados: ${listaPoligonosGuardados.size}")
                        actualizarPoligonosEnMapa(listaPoligonosGuardados)
                    }
                }

                // Colector para Dispositivos
                launch {
                    viewModel.dispositivos.collect { listaDispositivos ->
                        // Lógica para dibujar dispositivos...
                        Log.d("MapsActivity", "Dispositivos actualizados: ${listaDispositivos.size}")
                        actualizarDispositivosEnMapa(listaDispositivos)
                        viewModel.verificarEstadoDispositivos()
                    }
                }

                launch {
                    viewModel.poligonoSeleccionadoId.collect { idSeleccionado ->
                        if (!::googleMap.isInitialized) return@collect // Asegúrate de que el mapa está listo

                        desresaltarPoligonoExistenteSiHay() // Limpia el resaltado anterior

                        if (idSeleccionado != null) {
                            val polygonVisual = poligonosExistentesVisual[idSeleccionado]
                            polygonVisual?.let {
                                it.strokeColor = Color.RED
                                it.fillColor = 0x22FF0000 // Resaltar
                                it.zIndex = 0.5f // Asegurar que otros polígonos no lo tapen al seleccionarlo
                                idPoligonoResaltadoVisualmente = idSeleccionado // Actualiza tu variable de seguimiento
                            }
                            binding.fabEditar.text = getString(R.string.fab_edit, idSeleccionado.toString())
                            binding.fabEditar.setIconResource(R.drawable.ic_edit)
                            Log.d("MapsActivity", "Polígono seleccionado (StateFlow): $idSeleccionado")
                        } else {
                            binding.fabEditar.text = getString(R.string.fab_add)
                            binding.fabEditar.setIconResource(R.drawable.ic_add)
                            Log.d("MapsActivity", "Ningún polígono seleccionado (StateFlow).")
                        }
                    }
                }

                // Colector para modoEdicionPoligono (necesario para la lógica del FAB cuando no hay selección)
                launch {
                    viewModel.modoEdicionPoligono.collect { modo ->
                        // Actualiza el FAB y otros elementos de la UI basados en el modo
                        actualizarUiPorModoEdicion(modo, viewModel.poligonoSeleccionadoId.value)
                    }
                }

                // Colector para puntosPoligonoActualParaDibujar (si la Activity es responsable de dibujarlos)
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        // Asegúrate que usas el StateFlow correcto que expone PoligonoEditorManager.puntosPoligonoActual
                        viewModel.puntosPoligonoActualParaDibujar.collect { puntos -> // <-- PUNTO CRÍTICO 4
                            Log.d("MapsActivity_Observer", "Colector de puntosPoligonoActualParaDibujar activado. Cantidad de puntos: ${puntos.size}. Modo actual: ${viewModel.modoEdicionPoligono.value}")
                            // Esta función es la responsable de redibujar todo
                            actualizarVisualizacionPoligonoYVertices()
                            //actualizarVisualizacionPoligonoEnEdicion(puntos, viewModel.modoEdicionPoligono.value)
                        }
                    }
                }
            }
        }

        viewModel.verificarEstadoDispositivos()
    }

    private fun actualizarUiPorModoEdicion(modo: ModoEdicionPoligono, idPoligonoSeleccionado: Int?) {
        if (!::googleMap.isInitialized) return

        when (modo) {
            ModoEdicionPoligono.NINGUNO -> {
                limpiarMarcadoresDeVerticesVisual() // Desde tu código original
                poligonoEnCreacionVisual?.remove() // Desde tu código original
                poligonoEnCreacionVisual = null // Desde tu código original

                if (idPoligonoSeleccionado != null) {
                    binding.fabEditar.text = getString(R.string.fab_editing, idPoligonoSeleccionado.toString())
                    binding.fabEditar.setIconResource(R.drawable.ic_edit)
                } else {
                    binding.fabEditar.text = getString(R.string.fab_add)
                    binding.fabEditar.setIconResource(R.drawable.ic_add)
                }
                binding.fabOpcionesEdicion.visibility = View.GONE
                binding.fabEditar.visibility = View.VISIBLE
                // También actualiza topText si es necesario
                binding.topText.text = "Selecciona un dispositivo o un área para editar"
            }
            ModoEdicionPoligono.CREANDO -> {
                actualizarMarcadoresDeVerticesVisual(viewModel.puntosPoligonoActualParaDibujar.value, modo)
                binding.fabOpcionesEdicion.visibility = View.VISIBLE
                binding.fabEditar.visibility = View.GONE
                binding.topText.text = getString(R.string.creando_nueva_area)
                desresaltarPoligonoExistenteSiHay()
                binding.fabDeshacer.visibility = View.VISIBLE
            }
            ModoEdicionPoligono.EDITANDO -> {
                actualizarMarcadoresDeVerticesVisual(
                    viewModel.puntosPoligonoActualParaDibujar.value, modo
                )
                binding.fabOpcionesEdicion.visibility = View.VISIBLE
                binding.fabEditar.visibility = View.GONE
                binding.topText.text = getString(R.string.editando_area_id, viewModel.poligonoSeleccionadoId.value?.toString() ?: "")
                binding.fabDeshacer.visibility = View.GONE
            }
        }
    }

    private fun actualizarPoligonosEnMapa(listaPoligonosGuardados: List<Poligono>) {
        if (!::googleMap.isInitialized) return
        poligonosExistentesVisual.values.forEach { it.remove() }
        poligonosExistentesVisual.clear()

        listaPoligonosGuardados.forEach { poligonoData ->
            val options = PolygonOptions()
                .addAll(poligonoData.puntos)
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF)
                .strokeWidth(2f)
                .clickable(true)
            val mapPolygon = googleMap.addPolygon(options)
            mapPolygon.tag = poligonoData.id
            poligonosExistentesVisual[poligonoData.id] = mapPolygon
            if (poligonoData.id == viewModel.poligonoSeleccionadoId.value) {
                mapPolygon.strokeColor = Color.RED
                mapPolygon.fillColor = 0x22FF0000
                idPoligonoResaltadoVisualmente = poligonoData.id
            }
        }
    }

    private fun actualizarDispositivosEnMapa(listaDeRastreadores: List<Rastreador>) {
        if (!::googleMap.isInitialized) return

        marcadoresDeDispositivosActuales.forEach { it.remove() }
        marcadoresDeDispositivosActuales.clear()
        viewModel.markerRastreadorMap.clear()

        listaDeRastreadores.forEach { dispositivo ->
            val iconoMarcador = MarcadorIconHelper.obtenerIconoMarcador(
                this,
                dispositivo
            )
            val markerOptions = MarkerOptions()
                .position(LatLng(dispositivo.latitud, dispositivo.longitud))
                .title(dispositivo.nombre)
                .snippet(dispositivo.descripcion ?: "")
                .icon(iconoMarcador)
            val marcador = googleMap.addMarker(markerOptions)
            if (marcador != null) {
                viewModel.markerRastreadorMap[marcador] = dispositivo
                marcadoresDeDispositivosActuales.add(marcador)
            }
        }
        // Actualizar contadores de UI
        binding.textTotalDispositivos.text = getString(R.string.dispositivos_total, listaDeRastreadores.size)
        binding.textDispositivosFueraRango.text = getString(
            R.string.dispositivos_fuera_rango,
            listaDeRastreadores.count { !it.dentroDelArea }
        )
        binding.textDispositivosActivos.text = getString(
            R.string.dispositivos_activos,
            listaDeRastreadores.count { it.activo }
        )
    }

    private fun actualizarMarcadorUbicacionActual(latLng: LatLng) {
        if (!::googleMap.isInitialized) return
        if (marcadorUbicacionActual == null) {
            marcadorUbicacionActual = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Tu ubicación")
                    //.icon(cowboyIcon)
            )
        } else {
            marcadorUbicacionActual?.position = latLng
        }
    }

    private fun mostrarInfoDispositivo(rastreador: Rastreador) {
        val bottomSheet = DetailsRastreadorBottomSheet.newInstance(rastreador)
        bottomSheet.show(supportFragmentManager, "DispositivoBottomSheet")
    }

    private fun centrarMapa() {
        if (!centrarMapa || viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO
            || !::googleMap.isInitialized || viewModel.rastreadorSeleccionado.value != null
        ) return

        val bounds = viewModel.obtenerBoundsParaMapa() ?: return

            val padding = 150
            val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            googleMap.animateCamera(cu, object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    binding.btnCentrar.visibility = View.GONE
                }

                override fun onCancel() {
                    Log.d("MapsActivity", "Centrado de mapa cancelado")
                }
            })
    }

    private fun centrarDispositivoEnMapa(rastreador: Rastreador) {
        val bounds = viewModel.obtenerBoundsParaDispositivo(rastreador) ?: return

        val padding = 150
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        googleMap.animateCamera(cu)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkDispositivosRunnable = object : Runnable {
        override fun run() {
            viewModel.verificarEstadoDispositivos()
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(checkDispositivosRunnable)
    }

    override fun onPause() {
        super.onPause()
        // handler.removeCallbacks(checkDispositivosRunnable)
        // viewModel.detenerActualizacionesDeUbicacionUsuario()
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkDispositivosRunnable)
        viewModel.detenerActualizacionesDeUbicacionUsuario()
        super.onDestroy()

    }


}