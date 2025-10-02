package com.rafaelcosio.gpslivestock.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rafaelcosio.gpslivestock.R
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.databinding.ActivityMapsBinding
import com.rafaelcosio.gpslivestock.bluetooth.BluetoothService
import com.rafaelcosio.gpslivestock.bluetooth.BluetoothStateManager
import com.rafaelcosio.gpslivestock.ui.viewmodel.MapsViewModel
import com.rafaelcosio.gpslivestock.ui.viewmodel.ModoEdicionPoligono
import com.rafaelcosio.gpslivestock.utils.AppPreferences
import com.rafaelcosio.gpslivestock.utils.LoadingDialog
import com.rafaelcosio.gpslivestock.utils.MarcadorIconHelper
import com.rafaelcosio.gpslivestock.utils.toSpanish // <-- AÑADIDA IMPORTACIÓN
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rafaelcosio.gpslivestock.location.LocationProviderSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// ELIMINADA LA FUNCIÓN DUPLICADA UserType.toSpanish() DE AQUÍ

@AndroidEntryPoint
class MapsView : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLoadedCallback {

    @Inject
    lateinit var bluetoothStateManager: BluetoothStateManager
    @Inject
    lateinit var locationSource: LocationProviderSource

    private lateinit var binding: ActivityMapsBinding
    private lateinit var googleMap: GoogleMap
    private val viewModel: MapsViewModel by viewModels()

    private var marcadorUbicacionActual: Marker? = null
    private var centrarMapa = true
    var poligonoDibujado: Polygon? = null
    private val marcadoresDeDispositivosActuales = mutableListOf<Marker>()
    private var poligonoEnCreacionVisual: Polygon? = null
    private val poligonosExistentesVisual = mutableMapOf<Int, Polygon>()
    private var idPoligonoResaltadoVisualmente: Int? = null
    private lateinit var loadingDialog: LoadingDialog
    private var currentUserType: UserType = UserType.REGULAR_USER
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.bottomCardDeviceDetails.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupUserInfo()
        updateUi()

        loadingDialog = LoadingDialog(this@MapsView)
        loadingDialog.showLoadingDialog("Cargando mapa", R.raw.cow)

        Intent(this, BluetoothService::class.java).also { serviceIntent ->
            startService(serviceIntent)
        }


        val bottomSheet = binding.bottomCardDeviceDetails
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 280
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val arrowIcon = findViewById<ImageView>(R.id.arrow_icon)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        isExpanded = true
                    }
                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        isExpanded = false
                    }
                    else -> { /* No action needed */ }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                arrowIcon.rotation = 180 * slideOffset
            }
        })

        arrowIcon.setOnClickListener {
            behavior.state = if (isExpanded) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
        }

        configurarObservadoresViewModel()
        configurarListenersUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userLocation.collect { location ->
                        location?.let {
                            Log.d(
                                "MapsActivity",
                                "Ubicación del usuario observada en Activity: $it"
                            )
                            if (viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO) return@collect
                            centrarMapa()
                        }
                    }
                }
                launch {
                    viewModel.userType.collect { userType ->
                        Log.d(
                            "MapsActivity",
                            "UserType observado en Activity: ${userType.toSpanish()}"
                        )
                        currentUserType = userType
                        updateUi()
                    }
                }
            }
        }
        Log.d("MapsActivity", "UserType: " + currentUserType.toSpanish())
    }

    private fun updateUi(){
        if(currentUserType == UserType.REGULAR_USER){
            binding.fabEditar.visibility = View.GONE
            binding.bottomCardDeviceDetails.visibility = View.GONE
        } else {
            binding.fabEditar.visibility = View.VISIBLE
            binding.bottomCardDeviceDetails.visibility = View.VISIBLE
        }
    }

    private fun setupUserInfo() {
        val userDisplayName = AppPreferences.getUserDisplayName(this) ?: "Usuario"
        //currentUserType = AppPreferences.getUserType(this)!!
        // TEST
        //currentUserType = UserType.REGULAR_USER
        val ranchName = AppPreferences.getUserRanchName(this)

        var userInfoText = "Bienvenido $userDisplayName"

        currentUserType.let {
            userInfoText += " (${it.toSpanish()}" // Usando la función de extensión importada
            if (it == UserType.RANCHER && !ranchName.isNullOrBlank()) {
                userInfoText += " - Rancho: $ranchName"
            }
            userInfoText += ")"
        }
        binding.tvUserInfo.text = userInfoText
    }

    private fun configurarObservadoresViewModel() {
        viewModel.isConnected.observe(this) { connected ->
            //binding.connectedIcon.setImageResource(if (connected) R.drawable.ic_bluetooth_connected_badge else R.drawable.ic_bluetooth_disconnected_badge)
        }

        viewModel.connectionStatusText.observe(this) { status ->
            if (status.isNotEmpty()) {
                currentToast?.cancel()
                currentToast = Toast.makeText(this, status, Toast.LENGTH_SHORT)
                currentToast?.show()
                binding.textEstadoBluetooth.text = getString(R.string.estado_bluetooth, status)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rastreadorSeleccionado.collect { dispositivo ->
                    if (dispositivo != null) {
                        centrarMapa = false
                        Log.d("MapsActivity", "Dispositivo seleccionado: ${dispositivo.id}. Centrar mapa desactivado.")
                    }
                }
            }
        }
    }

    private fun actualizarVisualizacionPoligonoYVertices() {
        if (!::googleMap.isInitialized) return

        val modoActual = viewModel.modoEdicionPoligono.value
        val puntosActuales = viewModel.puntosPoligonoActualParaDibujar.value

        Log.d("MapsActivity_Draw", "actualizarVisualizacionPoligonoYVertices: Modo: $modoActual, Puntos: ${puntosActuales.joinToString { "(${it.latitude},${it.longitude})" }}")

        poligonoEnCreacionVisual?.remove()
        poligonoEnCreacionVisual = null
        limpiarMarcadoresDeVerticesVisual()

        if (modoActual == ModoEdicionPoligono.CREANDO || modoActual == ModoEdicionPoligono.EDITANDO) {
            if (puntosActuales.size >= 2) {
                poligonoEnCreacionVisual = googleMap.addPolygon(
                    PolygonOptions()
                        .addAll(puntosActuales)
                        .strokeColor(Color.GREEN)
                        .fillColor(0x2200FF00)
                        .strokeWidth(5f)
                        .zIndex(1f)
                )
            }
            if (puntosActuales.isNotEmpty()) {
                actualizarMarcadoresDeVerticesVisual(puntosActuales, modoActual)
            }
        }
    }

    private fun actualizarMarcadoresDeVerticesVisual(puntos: List<LatLng>, modo: ModoEdicionPoligono) {
        if (!::googleMap.isInitialized) return
        puntos.forEachIndexed { index, latLng ->
            val markerOptions = MarkerOptions()
                .position(latLng)
                .draggable(modo == ModoEdicionPoligono.EDITANDO || modo == ModoEdicionPoligono.CREANDO)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .anchor(0.5f, 0.5f)
            val marker = googleMap.addMarker(markerOptions)
            if (marker != null) {
                marker.tag = "vertice_$index"
                marcadoresVerticesActuales.add(marker)
            }
        }
    }

    private fun limpiarMarcadoresDeVerticesVisual() {
        marcadoresVerticesActuales.forEach { it.remove() }
        marcadoresVerticesActuales.clear()
    }

    private fun desresaltarPoligonoExistenteSiHay() {
        idPoligonoResaltadoVisualmente?.let {
            poligonosExistentesVisual[it]?.apply {
                strokeColor = Color.BLUE
                fillColor = 0x220000FF
                zIndex = 0f
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
            viewModel.guardarPoligonoEditadoActual()
            actualizarUiPorModoEdicion(viewModel.modoEdicionPoligono.value, viewModel.poligonoSeleccionadoId.value)
            viewModel.verificarEstadoDispositivos()
        }

        binding.fabReiniciar.setOnClickListener {
            if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO) {
                viewModel.reiniciarPuntosPoligonoActual()
            } else if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO) {
                AlertDialog.Builder(this)
                    .setTitle("Eliminar Área")
                    .setMessage("¿Deseas eliminar esta área?")
                    .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarPoligonoSeleccionado() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        binding.fabDeshacer.setOnClickListener { viewModel.deshacerUltimoPuntoPoligono() }
        binding.fabCancelar.setOnClickListener { viewModel.cancelarCreacionEdicionPoligono() }
    }

    override fun onMapLoaded() {
        loadingDialog.dismiss()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(map: GoogleMap) {
        if (isFinishing || isDestroyed) {
            Log.w("MapsActivity", "onMapReady llamada pero la actividad está terminándose.")
            return
        }
        this@MapsView.googleMap = map
        map.setOnMapLoadedCallback(this)
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        map.setLocationSource(locationSource)
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(24.1426, -110.3128), 12f))

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                centrarMapa = false
                binding.btnCentrar.visibility = View.VISIBLE
            }
        }

        map.setOnMapClickListener { latLng ->
            if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO ||
                viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO) {
                viewModel.agregarPuntoAPoligonoActual(latLng)
            } else {
                viewModel.deseleccionarPoligono()
                viewModel.deseleccionarDispositivo()
            }
        }

        map.setOnPolygonClickListener { polygonApi ->
            val idPoligonoClickeado = polygonApi.tag as? Int
            if (idPoligonoClickeado != null && viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.NINGUNO) {
                viewModel.seleccionarPoligonoPorId(idPoligonoClickeado)
                viewModel.deseleccionarDispositivo()
            }
        }

        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) { /* No action */ }
            override fun onMarkerDrag(marker: Marker) { /* No action */ }
            override fun onMarkerDragEnd(marker: Marker) {
                if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.NINGUNO) return
                (marker.tag as? String)?.substringAfter("vertice_")?.toIntOrNull()?.let {
                    viewModel.actualizarPuntoPoligonoEnEdicion(it, marker.position)
                }
            }
        })

        map.setOnMarkerClickListener { marker ->
            val dispositivo = viewModel.markerRastreadorMap[marker]
            if (dispositivo != null) {
                if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.NINGUNO) {
                    viewModel.seleccionarDispositivo(dispositivo)
                    viewModel.deseleccionarPoligono()
                    mostrarInfoDispositivo(dispositivo)
                    centrarDispositivoEnMapa(dispositivo)
                    viewModel.calcularDistancias()
                }
                return@setOnMarkerClickListener true
            }
            (marker.tag as? String)?.takeIf { it.startsWith("vertice_") }?.let {
                if (viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.CREANDO || viewModel.modoEdicionPoligono.value == ModoEdicionPoligono.EDITANDO) {
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar Vértice")
                        .setMessage("¿Deseas eliminar este vértice?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            it.substringAfter("vertice_").toIntOrNull()?.let { index -> viewModel.eliminarPuntoPoligono(index) }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    return@setOnMarkerClickListener true
                }
            }
            false
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.poligonos.collect { actualizarPoligonosEnMapa(it) } }
                launch { viewModel.dispositivos.collect { actualizarDispositivosEnMapa(it); viewModel.verificarEstadoDispositivos() } }
                launch { viewModel.poligonoSeleccionadoId.collect { idSeleccionado -> handlePoligonoSeleccionado(idSeleccionado) } }
                launch { viewModel.modoEdicionPoligono.collect { actualizarUiPorModoEdicion(it, viewModel.poligonoSeleccionadoId.value) } }
                launch { viewModel.puntosPoligonoActualParaDibujar.collect { actualizarVisualizacionPoligonoYVertices() } }
            }
        }
        viewModel.verificarEstadoDispositivos()
    }

    private fun handlePoligonoSeleccionado(idSeleccionado: Int?) {
        if (!::googleMap.isInitialized) return
        desresaltarPoligonoExistenteSiHay()
        if (idSeleccionado != null) {
            poligonosExistentesVisual[idSeleccionado]?.apply {
                strokeColor = Color.RED
                fillColor = 0x22FF0000
                zIndex = 0.5f
            }
            idPoligonoResaltadoVisualmente = idSeleccionado
            binding.fabEditar.text = getString(R.string.fab_edit, idSeleccionado.toString())
            binding.fabEditar.setIconResource(R.drawable.ic_edit)
        } else {
            binding.fabEditar.text = getString(R.string.fab_add)
            binding.fabEditar.setIconResource(R.drawable.ic_add)
        }
    }

    private fun actualizarUiPorModoEdicion(modo: ModoEdicionPoligono, idPoligonoSeleccionado: Int?) {
        if (!::googleMap.isInitialized) return
        when (modo) {
            ModoEdicionPoligono.NINGUNO -> {
                limpiarMarcadoresDeVerticesVisual()
                poligonoEnCreacionVisual?.remove()
                poligonoEnCreacionVisual = null
                binding.fabEditar.text = if (idPoligonoSeleccionado != null) getString(R.string.fab_editing, idPoligonoSeleccionado.toString()) else getString(R.string.fab_add)
                binding.fabEditar.setIconResource(if (idPoligonoSeleccionado != null) R.drawable.ic_edit else R.drawable.ic_add)
                binding.fabOpcionesEdicion.visibility = View.GONE
                binding.fabEditar.visibility = if(currentUserType == UserType.REGULAR_USER) View.GONE else View.VISIBLE
                binding.topAppBar.subtitle = if(currentUserType == UserType.REGULAR_USER) "" else getString(R.string.selecciona_dispositivo_o_area)
                binding.topAppBar.title = "Mapa"
            }
            ModoEdicionPoligono.CREANDO -> {
                actualizarMarcadoresDeVerticesVisual(viewModel.puntosPoligonoActualParaDibujar.value, modo)
                binding.fabOpcionesEdicion.visibility = View.VISIBLE
                binding.fabEditar.visibility = View.GONE
                binding.topAppBar.subtitle = getString(R.string.creando_nueva_area)
                binding.topAppBar.title = "Creando Área"
                desresaltarPoligonoExistenteSiHay()
                binding.fabDeshacer.visibility = View.VISIBLE
            }
            ModoEdicionPoligono.EDITANDO -> {
                actualizarMarcadoresDeVerticesVisual(viewModel.puntosPoligonoActualParaDibujar.value, modo)
                binding.fabOpcionesEdicion.visibility = View.VISIBLE
                binding.fabEditar.visibility = View.GONE
                binding.topAppBar.subtitle = getString(R.string.editando_area)
                binding.topAppBar.title = getString(R.string.editando_area_id, viewModel.poligonoSeleccionadoId.value?.toString() ?: "")
                binding.fabDeshacer.visibility = View.GONE
            }
        }
    }

    private fun actualizarPoligonosEnMapa(listaPoligonosGuardados: List<Poligono>) {
        if (!::googleMap.isInitialized) return
        if (currentUserType == UserType.REGULAR_USER) return
        poligonosExistentesVisual.values.forEach { it.remove() }
        poligonosExistentesVisual.clear()
        listaPoligonosGuardados.forEach { poligonoData ->
            val options = PolygonOptions().addAll(poligonoData.puntos).strokeColor(Color.BLUE).fillColor(0x220000FF).strokeWidth(2f).clickable(true)
            val mapPolygon = googleMap.addPolygon(options)
            mapPolygon.tag = poligonoData.id
            poligonosExistentesVisual[poligonoData.id] = mapPolygon
            if (poligonoData.id == viewModel.poligonoSeleccionadoId.value) {
                mapPolygon.strokeColor = Color.RED
                mapPolygon.fillColor = 0x22FF0000
                mapPolygon.zIndex = 0.5f
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
            val iconoMarcador = if(currentUserType != UserType.REGULAR_USER)
                MarcadorIconHelper.obtenerIconoMarcador(this, dispositivo)
            else
                MarcadorIconHelper.obtenerIconoMarcadorSimple(this, dispositivo)
            val markerOptions = MarkerOptions().position(LatLng(dispositivo.latitud, dispositivo.longitud)).title(dispositivo.nombre).snippet(dispositivo.descripcion ?: "").icon(iconoMarcador)

            if(currentUserType == UserType.REGULAR_USER){
                // Solo agregar marcador si el dispositivo está a menos de 500 metros del usuario
                val distanciaAlUsuario = viewModel.calcularDistanciaAlUsuario(dispositivo)
                if (distanciaAlUsuario == null || distanciaAlUsuario > 1000) {
                    return@forEach
                }
            }
            googleMap.addMarker(markerOptions)?.also {
                viewModel.markerRastreadorMap[it] = dispositivo
                marcadoresDeDispositivosActuales.add(it)
            }
        }
        binding.textTotalDispositivos.text = getString(R.string.dispositivos_total, listaDeRastreadores.size)
        binding.textDispositivosFueraRango.text = getString(R.string.dispositivos_fuera_rango, listaDeRastreadores.count { !it.dentroDelArea })
        binding.textDispositivosActivos.text = getString(R.string.dispositivos_activos, listaDeRastreadores.count { it.activo })
    }

    private fun mostrarInfoDispositivo(rastreador: Rastreador) {
        DetailsRastreadorBottomSheet.newInstance(rastreador).show(supportFragmentManager, "DispositivoBottomSheet")
    }

    private fun centrarMapa() {
        if (!centrarMapa || viewModel.modoEdicionPoligono.value != ModoEdicionPoligono.NINGUNO || !::googleMap.isInitialized || viewModel.rastreadorSeleccionado.value != null) return
        viewModel.obtenerBoundsParaMapa()?.let {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(it, 150), object : GoogleMap.CancelableCallback {
                override fun onFinish() { binding.btnCentrar.visibility = View.GONE }
                override fun onCancel() { Log.d("MapsActivity", "Centrado de mapa cancelado") }
            })
        }
    }

    private fun centrarDispositivoEnMapa(rastreador: Rastreador) {
        if (!::googleMap.isInitialized) return
        viewModel.obtenerBoundsParaDispositivo(rastreador)?.let { googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(it, 150)) }
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
        handler.removeCallbacks(checkDispositivosRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkDispositivosRunnable)
        viewModel.detenerActualizacionesDeUbicacionUsuario()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, BluetoothDevicesView::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Sí") { _, _ -> logoutUser() }
            .setNegativeButton("No", null)
            .show()
        return super.onSupportNavigateUp()
    }

    private fun logoutUser() {
        AppPreferences.clearUserSession(this)
        val intent = Intent(this, LoginView::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}