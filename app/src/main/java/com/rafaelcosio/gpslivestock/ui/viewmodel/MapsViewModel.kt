package com.rafaelcosio.gpslivestock.ui.viewmodel

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaelcosio.gpslivestock.bluetooth.BluetoothStateManager
import com.rafaelcosio.gpslivestock.bluetooth.ConnectionStatusInfo
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepository
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.domain.editor.PoligonoEditorManager
import com.rafaelcosio.gpslivestock.domain.usecase.tracker.VerificarEstadoRastreadoresUseCase
import com.rafaelcosio.gpslivestock.location.ILocationProvider
import com.rafaelcosio.gpslivestock.utils.LocationUtils.calcularDistancia
import com.rafaelcosio.gpslivestock.utils.StringFormatUtils.formatearDistancia
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.di.UserTypeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class MapsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bluetoothStateManager: BluetoothStateManager,
    private val rastreadorRepository: RastreadorRepository,
    private val poligonoRepository: PoligonoRepository,
    private val poligonoEditorManager: PoligonoEditorManager,
    private val userTypeProvider: UserTypeProvider,
    private val locationProvider: ILocationProvider,
    private val verificarEstadoRastreadoresUseCase: VerificarEstadoRastreadoresUseCase,
    private val guardarPoligonoUseCase: com.rafaelcosio.gpslivestock.domain.usecase.polygon.GuardarPoligonoUseCase
) : ViewModel() {
    private val _connectionStatusText = MutableLiveData<String>("Desconectado")
    val connectionStatusText: LiveData<String> = _connectionStatusText

    private val _connectedDeviceName = MutableLiveData<String?>()
    val connectedDeviceName: LiveData<String?> = _connectedDeviceName

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    val distanciaTexto = MutableLiveData<String>()

    private val _rastreadorSeleccionado = MutableStateFlow<Rastreador?>(null)
    val rastreadorSeleccionado: StateFlow<Rastreador?> = _rastreadorSeleccionado.asStateFlow()

    val markerRastreadorMap = mutableMapOf<Marker, Rastreador>()
    val idPoligonoActualmenteEnEdicion: StateFlow<Int?> = poligonoEditorManager.idPoligonoEnEdicion

    val dispositivos: StateFlow<List<Rastreador>> = rastreadorRepository.getAllDispositivos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    val poligonos: StateFlow<List<Poligono>> = poligonoRepository.getAllPoligonos()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val modoEdicionPoligono: StateFlow<ModoEdicionPoligono> = poligonoEditorManager.modoEdicion
    val puntosPoligonoActualParaDibujar: StateFlow<List<LatLng>> = poligonoEditorManager.puntosPoligonoActual
    private val _poligonoSeleccionadoId = MutableStateFlow<Int?>(null)
    val poligonoSeleccionadoId: StateFlow<Int?> = _poligonoSeleccionadoId.asStateFlow()
    val userType: StateFlow<UserType> = userTypeProvider.userType

    private val attemptingConnectionObserver = Observer<String> { deviceName ->
        Log.d("MapsViewModel", "StateManager - Attempting Connection to: $deviceName")
        _connectionStatusText.value = "Conectando al rastreador..."
        _isConnected.value = false
    }

    private val connectionSuccessfulObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.i("MapsViewModel", "StateManager - Connection Successful to: ${statusInfo.deviceDisplayName}")
        _connectionStatusText.value = "Conectado al rastreador"
        _connectedDeviceName.value = statusInfo.deviceDisplayName
        _isConnected.value = true
    }

    private val connectionFailedObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.e("MapsViewModel", "StateManager - Connection Failed to: ${statusInfo.deviceDisplayName}, Error: ${statusInfo.errorMessage}")
        _connectionStatusText.value = "Falló conexión con con el rastreador: ${statusInfo.errorMessage}"
        _connectedDeviceName.value = null
        _isConnected.value = false
    }

    private val deviceDisconnectedObserver = Observer<ConnectionStatusInfo> { statusInfo ->
        Log.i("MapsViewModel", "StateManager - Device Disconnected: ${statusInfo.deviceDisplayName}")
        _connectionStatusText.value = "Desconectado del rastreador bluetooth"
        _connectedDeviceName.value = null
        _isConnected.value = false
    }

    init {
        distanciaTexto.value = "Calculando"
        viewModelScope.launch {
            val lastLocation = locationProvider.getLastKnownLocationSuspending()
            _userLocation.value = lastLocation
            lastLocation?.let { recalcularDistancias(it) }
        }

        viewModelScope.launch {
            locationProvider.lastKnownLocation.collect { location ->
                if (location != null) {
                    _userLocation.value = location
                    recalcularDistancias(location)
                }
            }
        }
        verificarEstadoDispositivos()

        bluetoothStateManager.attemptingConnection.observeForever(attemptingConnectionObserver)
        bluetoothStateManager.connectionSuccessful.observeForever(connectionSuccessfulObserver)
        bluetoothStateManager.connectionFailed.observeForever(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.observeForever(deviceDisconnectedObserver)
    }



    fun obtenerIdPoligonoEnEdicion(): Int? {
        val id = poligonoEditorManager.idPoligonoEnEdicion.value
        Log.d("MapsViewModel", "obtenerIdPoligonoEnEdicion: ID actual en edición es $id")
        return id
    }

    fun seleccionarPoligonoPorId(idPoligono: Int?) {
        if (_poligonoSeleccionadoId.value == idPoligono && idPoligono != null) {
            _poligonoSeleccionadoId.value = null
            poligonoEditorManager.cancelarCreacionEdicionPoligono()
        } else {
            _poligonoSeleccionadoId.value = idPoligono
            poligonoEditorManager.establecerPoligonoParaEdicion(idPoligono)
            poligonoEditorManager.cancelarCreacionEdicionPoligono()
        }
    }

    fun deseleccionarPoligono() {
        _poligonoSeleccionadoId.value = null
        poligonoEditorManager.establecerPoligonoParaEdicion(null)
    }

    fun actualizarPuntoPoligonoEnEdicion(indice: Int, nuevaPosicion: LatLng) {
        Log.d("MapsViewModel", "actualizarPuntoPoligonoEnEdicion: Índice $indice, Posición $nuevaPosicion")
        poligonoEditorManager.actualizarPuntoPoligonoActual(indice, nuevaPosicion)
    }

    fun iniciarModoCreacionPoligono() {
        _poligonoSeleccionadoId.value = null
        poligonoEditorManager.iniciarModoCreacionPoligono()
    }

    fun iniciarEdicionPuntosPoligonoSeleccionado() {
        val idPoligonoAEditar = _poligonoSeleccionadoId.value ?: return
        viewModelScope.launch {
            val poligonoParaEditar = poligonoRepository.getPoligonoById(idPoligonoAEditar)
            if (poligonoParaEditar != null) {
                poligonoEditorManager.iniciarModoEdicionPuntos(idPoligonoAEditar, poligonoParaEditar.puntos)
            } else {
                Log.w("MapsViewModel", "No se encontró el polígono con ID $idPoligonoAEditar para editar.")
                Toast.makeText(appContext, "No se encontró el polígono para editar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelarCreacionEdicionPoligono() {
        poligonoEditorManager.cancelarCreacionEdicionPoligono()
    }

    fun agregarPuntoAPoligonoActual(punto: LatLng) {
        poligonoEditorManager.anadirPuntoAPoligonoActual(punto)
    }

    fun deshacerUltimoPuntoPoligono() {
        poligonoEditorManager.deshacerUltimoPunto()
    }

    fun reiniciarPuntosPoligonoActual() {
        poligonoEditorManager.reiniciarPoligonoActual()
    }

    fun actualizarPuntoPoligono(indice: Int, nuevoPunto: LatLng) {
        poligonoEditorManager.actualizarPuntoPoligono(indice, nuevoPunto)
    }

    fun eliminarPuntoPoligono(indice: Int) {
        poligonoEditorManager.eliminarPuntoPoligono(indice)
    }

    fun guardarPoligonoEditadoActual() {
        val datosParaGuardar = poligonoEditorManager.obtenerDatosParaGuardar()

        if (datosParaGuardar == null) {
            Log.w("MapsViewModel", "No se puede guardar el polígono: datos inválidos desde PoligonoEditorManager.")
            Toast.makeText(appContext, "Debe haber al menos 3 puntos para crear un polígono.", Toast.LENGTH_LONG).show()
            poligonoEditorManager.cancelarCreacionEdicionPoligono()
            return
        }

        val (id, puntos) = datosParaGuardar
        viewModelScope.launch {
            try {
                guardarPoligonoUseCase(id, puntos)
                Log.d("MapsViewModel", "Polígono guardado/actualizado. ID: $id, Puntos: ${puntos.size}")
                poligonoEditorManager.cancelarCreacionEdicionPoligono()
                _poligonoSeleccionadoId.value = null
            } catch (e: Exception) {
                Log.e("MapsViewModel", "Error al guardar el polígono", e)
            }
        }
    }

    fun eliminarPoligonoSeleccionado() {
        val idPoligonoAEliminar = _poligonoSeleccionadoId.value ?: return
        viewModelScope.launch {
            poligonoRepository.deletePoligonoById(idPoligonoAEliminar)
            _poligonoSeleccionadoId.value = null
            poligonoEditorManager.cancelarCreacionEdicionPoligono()
        }
    }

    fun guardarPoligono(id: Int, lista: List<LatLng>){
        viewModelScope.launch {
            val poligono = Poligono(
                id = id,
                puntos = lista.map { LatLng(it.latitude, it.longitude) }
            )
            poligonoRepository.insertPoligono(poligono)
        }
    }

    fun verificarEstadoDispositivos() {
        viewModelScope.launch {
            val dispositivosActualizados = verificarEstadoRastreadoresUseCase()
            rastreadorRepository.insertAllDispositivos(dispositivosActualizados)
        }
    }

    /*
    fun actualizarDetallesDispositivo(dispositivoActualizado: Dispositivo) {
        viewModelScope.launch {
            dispositivoRepository.insertDispositivo(dispositivoActualizado)

            val listaActual = _dispositivos.value?.toMutableList() ?: mutableListOf()
            val index = listaActual.indexOfFirst { it.id == dispositivoActualizado.id }
            if (index != -1) {
                listaActual[index] = dispositivoActualizado
                _dispositivos.value = listaActual
            }
            if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                _dispositivoSeleccionado.value = dispositivoActualizado
            }
        }
    }

     */

    fun insertarDispositivosEjemplo() {
        val lista = listOf(
            Rastreador(1, "Vaquita mú", "Vaca con manchas negras", 24.102455, -110.316152, 1, 1750271280, System.currentTimeMillis(), true),
            Rastreador(3, "Vaquita del aramburo", "Casi nos la quita el Chedraui™️", 24.1108454, -110.3129548, 2, 1750271280, 1750271280, false),
            Rastreador(4, "Vaquita de la calle", "No es de nadie, pero es de todos", 24.1487217, -110.2767691, 3, 1750271280, System.currentTimeMillis(), true)
        )
        viewModelScope.launch {
            rastreadorRepository.insertAllDispositivos(lista)
        }
    }

    fun seleccionarDispositivo(rastreador: Rastreador) {
        _rastreadorSeleccionado.value = rastreador
    }

    fun deseleccionarDispositivo() {
        _rastreadorSeleccionado.value = null
    }

    fun eliminarDispositivo(rastreador: Rastreador) {
        viewModelScope.launch {
            rastreadorRepository.deleteDispositivo(rastreador)
            if (_rastreadorSeleccionado.value?.id == rastreador.id) {
                _rastreadorSeleccionado.value = null
            }
        }
    }

    fun actualizarDetallesDispositivo(rastreadorActualizado: Rastreador) {
        viewModelScope.launch {
            try {
                rastreadorRepository.updateDispositivo(rastreadorActualizado)
                if (_rastreadorSeleccionado.value?.id == rastreadorActualizado.id) {
                    _rastreadorSeleccionado.value = rastreadorActualizado
                }

            } catch (e: Exception) {
                Log.e("MapsViewModel", "Error al actualizar dispositivo", e)
            }
        }
    }

    fun calcularDistanciaAlUsuario(dispositivo: Rastreador): Float? {
        val userLoc = userLocation.value ?: return null
        val dispositivoLoc = Location("").apply {
            latitude = dispositivo.latitud
            longitude = dispositivo.longitud
        }
        val distancia = calcularDistancia(userLoc, dispositivoLoc)
        distanciaTexto.value = formatearDistancia(distancia)
        return distancia
    }

    fun obtenerBoundsParaMapa(): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()

        _userLocation.value?.let { location ->
            listPuntos.add(LatLng(location.latitude, location.longitude))
        }

        if(userType.value != UserType.REGULAR_USER) {
            val dispositivosActuales = dispositivos.value
            if (dispositivosActuales.isNotEmpty()) {
                dispositivosActuales.forEach { d ->
                    listPuntos.add(LatLng(d.latitud, d.longitud))
                }
            } else {
                return null
            }
        }

        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun obtenerBoundsParaDispositivo(rastreador: Rastreador): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _userLocation.value?.let { location ->
            listPuntos.add(LatLng(location.latitude, location.longitude))
        }
        listPuntos.add(LatLng(rastreador.latitud, rastreador.longitud))

        if (listPuntos.isEmpty()) return null
        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun iniciarActualizacionesDeUbicacionUsuario() {
        locationProvider.startLocationUpdates { location ->
            recalcularDistancias(location)
        }
    }

    fun detenerActualizacionesDeUbicacionUsuario() {
        locationProvider.stopLocationUpdates()
    }

    private fun recalcularDistancias(ubicacionActual: Location) {
        rastreadorSeleccionado.value?.let { dispositivo ->
            val distancia = calcularDistancia(
                ubicacionActual,
                Location("").apply {
                    latitude = dispositivo.latitud
                    longitude = dispositivo.longitud
                }
            )
            distanciaTexto.postValue(formatearDistancia(distancia))
        }
    }

    fun calcularDistancias() {
        rastreadorSeleccionado.value?.let { dispositivo ->
            val distancia = calcularDistancia(
                userLocation.value ?: return,
                Location("").apply {
                    latitude = dispositivo.latitud
                    longitude = dispositivo.longitud
                }
            )
            distanciaTexto.postValue(formatearDistancia(distancia))
        }
    }

    override fun onCleared() {
        bluetoothStateManager.attemptingConnection.removeObserver(attemptingConnectionObserver)
        bluetoothStateManager.connectionSuccessful.removeObserver(connectionSuccessfulObserver)
        bluetoothStateManager.connectionFailed.removeObserver(connectionFailedObserver)
        bluetoothStateManager.deviceDisconnected.removeObserver(deviceDisconnectedObserver)
        detenerActualizacionesDeUbicacionUsuario()
        super.onCleared()
    }
}

enum class ModoEdicionPoligono {
    NINGUNO,
    CREANDO,
    EDITANDO
}