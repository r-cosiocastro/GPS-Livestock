package com.dasc.pecustrack.ui.viewmodel

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.dasc.pecustrack.domain.editor.PoligonoEditorManager
import com.dasc.pecustrack.domain.usecase.device.VerificarEstadoDispositivosUseCase
import com.dasc.pecustrack.location.ILocationProvider
import com.dasc.pecustrack.utils.LocationUtils.calcularDistancia
import com.dasc.pecustrack.utils.StringFormatUtils.formatearDistancia
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModoEdicionPoligono {
    NINGUNO,    // No se está creando ni editando
    CREANDO,    // Creando un nuevo polígono
    EDITANDO    // Editando un polígono existente (esta parte es más compleja y puede requerir más estado)
}
@HiltViewModel
class MapsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dispositivoRepository: DispositivoRepository,
    private val poligonoRepository: PoligonoRepository,
    private val poligonoEditorManager: PoligonoEditorManager,
    private val locationProvider: ILocationProvider,
    private val verificarEstadoDispositivosUseCase: VerificarEstadoDispositivosUseCase,
    private val guardarPoligonoUseCase: com.dasc.pecustrack.domain.usecase.polygon.GuardarPoligonoUseCase
) : ViewModel() {

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    val distanciaTexto = MutableLiveData<String>()

    private val _dispositivoSeleccionado = MutableStateFlow<Dispositivo?>(null) // Inicializa con null
    val dispositivoSeleccionado: StateFlow<Dispositivo?> = _dispositivoSeleccionado.asStateFlow()

    val markerDispositivoMap = mutableMapOf<Marker, Dispositivo>()
    val idPoligonoActualmenteEnEdicion: StateFlow<Int?> = poligonoEditorManager.idPoligonoEnEdicion

    //val dispositivos: LiveData<List<Dispositivo>> get() = _dispositivos

    val dispositivos: StateFlow<List<Dispositivo>> = dispositivoRepository.getAllDispositivos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily, // O SharingStarted.WhileSubscribed(5000)
            initialValue = emptyList()
        )

    // Poligonos como StateFlow para observación en UI

    val poligonos: StateFlow<List<Poligono>> = poligonoRepository.getAllPoligonos()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Observa el estado del PoligonoEditorManager
    val modoEdicionPoligono: StateFlow<ModoEdicionPoligono> = poligonoEditorManager.modoEdicion
    val puntosPoligonoActualParaDibujar: StateFlow<List<LatLng>> = poligonoEditorManager.puntosPoligonoActual

    // El ID del polígono seleccionado por el usuario para ver detalles o iniciar edición
    private val _poligonoSeleccionadoId = MutableStateFlow<Int?>(null)
    val poligonoSeleccionadoId: StateFlow<Int?> = _poligonoSeleccionadoId.asStateFlow()

    init {
        // Obtener la última ubicación conocida al iniciar
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

        insertarDispositivosEjemplo()
        verificarEstadoDispositivos()
    }

    fun obtenerIdPoligonoEnEdicion(): Int? {
        val id = poligonoEditorManager.idPoligonoEnEdicion.value // Accede al valor actual del StateFlow
        Log.d("MapsViewModel", "obtenerIdPoligonoEnEdicion: ID actual en edición es $id")
        return id
    }

    fun seleccionarPoligonoPorId(idPoligono: Int?) {
        if (_poligonoSeleccionadoId.value == idPoligono && idPoligono != null) {
            // Si se hace clic de nuevo en el mismo polígono, se podría deseleccionar
            // o se podría preparar para la edición de puntos (ver siguiente función)
            _poligonoSeleccionadoId.value = null
            poligonoEditorManager.cancelarCreacionEdicionPoligono() // Cancela cualquier edición
        } else {
            _poligonoSeleccionadoId.value = idPoligono
            poligonoEditorManager.establecerPoligonoParaEdicion(idPoligono)
            poligonoEditorManager.cancelarCreacionEdicionPoligono() // Cancela al seleccionar uno nuevo
        }
    }

    fun deseleccionarPoligono() {
        // Si poligonoSeleccionadoId es MutableStateFlow:
        _poligonoSeleccionadoId.value = null
        // Si poligonoSeleccionadoId es el StateFlow del PoligonoEditorManager para el ID del que se está editando,
        // entonces esta lógica estaría más bien en PoligonoEditorManager.cancelarEdicion() o similar.
        // Aquí asumimos un ID de polígono seleccionado para visualización/resaltado general.
        poligonoEditorManager.establecerPoligonoParaEdicion(null) // Esto pondría el modo en NINGUNO
    }

    fun actualizarPuntoPoligonoEnEdicion(indice: Int, nuevaPosicion: LatLng) {
        Log.d("MapsViewModel", "actualizarPuntoPoligonoEnEdicion: Índice $indice, Posición $nuevaPosicion")
        poligonoEditorManager.actualizarPuntoPoligonoActual(indice, nuevaPosicion) // <-- PUNTO CRÍTICO 2
    }

    fun iniciarModoCreacionPoligono() {
        _poligonoSeleccionadoId.value = null // Deselecciona cualquier polígono
        poligonoEditorManager.iniciarModoCreacionPoligono()
    }

    fun iniciarEdicionPuntosPoligonoSeleccionado() {
        val idPoligonoAEditar = _poligonoSeleccionadoId.value ?: return
        viewModelScope.launch {
            // Obtener los puntos del polígono seleccionado del repositorio
            val poligonoParaEditar = poligonoRepository.getPoligonoById(idPoligonoAEditar) // Asume/crea este
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
        // No es necesario resetear _poligonoSeleccionadoId aquí,
        // porque la cancelación es solo de la edición de puntos,
        // el polígono podría seguir "seleccionado" visualmente.
        // _puntosPoligonoActual.value = emptyList()
    }

    fun agregarPuntoAPoligonoActual(punto: LatLng) {
        poligonoEditorManager.anadirPuntoAPoligonoActual(punto)
    }

    fun deshacerUltimoPuntoPoligono() { // Renombrado para claridad
        poligonoEditorManager.deshacerUltimoPunto()
    }

    fun reiniciarPuntosPoligonoActual() { // Renombrado para claridad
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
            // PoligonoEditorManager ya decidió que no se puede guardar (ej. pocos puntos)
            // Aquí puedes mostrar un Toast o mensaje al usuario.
            // Ejemplo: _uiEvents.trySend(UiEvent.ShowToast("Se necesitan al menos 3 puntos."))
            Log.w("MapsViewModel", "No se puede guardar el polígono: datos inválidos desde PoligonoEditorManager.")
            Toast.makeText(appContext, "Debe haber al menos 3 puntos para crear un polígono.", Toast.LENGTH_LONG).show()
            // Considera si quieres cancelar la edición aquí también
            poligonoEditorManager.cancelarCreacionEdicionPoligono()
            return
        }

        val (id, puntos) = datosParaGuardar
        viewModelScope.launch {
            try {
                // El caso de uso maneja si es un insert o un update basado en si el id es null
                guardarPoligonoUseCase(id, puntos)
                Log.d("MapsViewModel", "Polígono guardado/actualizado. ID: $id, Puntos: ${puntos.size}")
                poligonoEditorManager.cancelarCreacionEdicionPoligono() // Sale del modo edición
                _poligonoSeleccionadoId.value = null // Opcional: deseleccionar después de guardar
                // La lista de polígonos se actualizará automáticamente si observas el Flow del repositorio.
            } catch (e: Exception) {
                Log.e("MapsViewModel", "Error al guardar el polígono", e)
                // Mostrar error al usuario
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
            val dispositivosActualizados = verificarEstadoDispositivosUseCase()
            dispositivoRepository.insertAllDispositivos(dispositivosActualizados)
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

            // Actualiza el dispositivo seleccionado si es el mismo
            if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                _dispositivoSeleccionado.value = dispositivoActualizado
            }
        }
    }

     */

    fun insertarDispositivosEjemplo() {
        val lista = listOf(
            Dispositivo(1, "Vaquita mú", "Vaca con manchas negras", 24.102455, -110.316152, 1750271280, System.currentTimeMillis(), true),
            //Dispositivo(2, "Vaquita lechera", "Da mucha leche", 24.1051774, -110.3698646, 1750271280, System.currentTimeMillis(), true),
            Dispositivo(3, "Vaquita del aramburo", "Casi nos la quita el Chedraui™️", 24.1108454, -110.3129548, 1750271280, 1750271280, false),
            Dispositivo(4, "Vaquita de la calle", "No es de nadie, pero es de todos", 24.1487217, -110.2767691, 1750271280, System.currentTimeMillis(), true)
        )
        viewModelScope.launch {
            dispositivoRepository.insertAllDispositivos(lista)
        }
    }

    fun seleccionarDispositivo(dispositivo: Dispositivo) {
        _dispositivoSeleccionado.value = dispositivo
    }

    fun deseleccionarDispositivo() {
        _dispositivoSeleccionado.value = null
    }

    fun actualizarDetallesDispositivo(dispositivoActualizado: Dispositivo) {
        viewModelScope.launch {
            try {
                dispositivoRepository.updateDispositivo(dispositivoActualizado) // Asume este método en el repo
                // El StateFlow 'dispositivos' se actualizará automáticamente si el repo emite el cambio.

                // Si el dispositivo editado es el que está seleccionado, actualiza también _dispositivoSeleccionado
                // Si _dispositivoSeleccionado es LiveData:
                if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                    _dispositivoSeleccionado.value = dispositivoActualizado
                }
                // Si _dispositivoSeleccionado fuera un MutableStateFlow:
                // if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                //     _dispositivoSeleccionado.value = dispositivoActualizado
                // }

            } catch (e: Exception) {
                Log.e("MapsViewModel", "Error al actualizar dispositivo", e)
                // Manejar error (e.g., mostrar Toast/Snackbar)
            }
        }
    }

    fun obtenerBoundsParaMapa(): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()

        _userLocation.value?.let { location -> // Usa el safe call ?. y un nombre de variable (location)
            listPuntos.add(LatLng(location.latitude, location.longitude))
        }

        val dispositivosActuales = dispositivos.value
        if (dispositivosActuales.isNotEmpty()) { // Comprueba si la lista no está vacía
            dispositivosActuales.forEach { d ->
                listPuntos.add(LatLng(d.latitud, d.longitud))
            }
        } else{
            return null
        }

        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun obtenerBoundsParaDispositivo(dispositivo: Dispositivo): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _userLocation.value?.let { location ->
            listPuntos.add(LatLng(location.latitude, location.longitude))
        }
        listPuntos.add(LatLng(dispositivo.latitud, dispositivo.longitud))

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
        dispositivoSeleccionado.value?.let { dispositivo ->
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

    override fun onCleared() {
        super.onCleared()
        detenerActualizacionesDeUbicacionUsuario()
    }


}