package com.dasc.pecustrack.ui.viewmodel

import android.Manifest
import android.app.Application
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dasc.pecustrack.data.database.entities.DispositivoDatabase
import com.dasc.pecustrack.data.database.entities.PoligonoDatabase
import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.model.Poligono
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.launch

enum class ModoEdicionPoligono {
    NINGUNO,    // No se está creando ni editando
    CREANDO,    // Creando un nuevo polígono
    EDITANDO    // Editando un polígono existente (esta parte es más compleja y puede requerir más estado)
}

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val dispositivosDao = DispositivoDatabase.obtenerInstancia(application).dispositivoDao()
    private val _dispositivos = MutableLiveData<List<Dispositivo>>()
    val dispositivos: LiveData<List<Dispositivo>> get() = _dispositivos

    private val poligonosDao = PoligonoDatabase.obtenerInstancia(application).poligonoDao()
    private val _poligonos = MutableLiveData<List<Poligono>>()
    val poligonos: LiveData<List<Poligono>> get() = _poligonos

    // --- Estado para la Creación de Polígonos ---
    private val _modoEdicion = MutableLiveData<ModoEdicionPoligono>(ModoEdicionPoligono.NINGUNO)
    val modoEdicion: LiveData<ModoEdicionPoligono> = _modoEdicion

    private val _puntosPoligonoActual = MutableLiveData<List<LatLng>>(emptyList())
    val puntosPoligonoActual: LiveData<List<LatLng>> = _puntosPoligonoActual

    // --- Estado para la Selección de Polígonos Existentes ---
    private val _poligonoSeleccionadoId = MutableLiveData<Int?>() // ID de tu PoligonoData
    val poligonoSeleccionadoId: LiveData<Int?> = _poligonoSeleccionadoId

    private val _ubicacionActual = MutableLiveData<Location>()
    val ubicacionActual: LiveData<Location> get() = _ubicacionActual

    private val _distanciaTexto = MediatorLiveData<String>()
    val distanciaTexto: LiveData<String> get() = _distanciaTexto


    private val _dispositivoSeleccionado = MutableLiveData<Dispositivo?>()
    val dispositivoSeleccionado: LiveData<Dispositivo?> get() = _dispositivoSeleccionado

    val markerDispositivoMap = mutableMapOf<Marker, Dispositivo>()

    private lateinit var locationCallback: LocationCallback

    init {
        _distanciaTexto.addSource(_ubicacionActual) { actualizarDistancia() }
        _distanciaTexto.addSource(_dispositivoSeleccionado) { actualizarDistancia() }
    }

    fun iniciarModoCreacionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.CREANDO
        _puntosPoligonoActual.value = emptyList()
        _poligonoSeleccionadoId.value = null // Deselecciona cualquier polígono existente
    }

    fun cancelarCreacionEdicionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        _puntosPoligonoActual.value = emptyList()
    }

    fun añadirPuntoAPoligonoActual(punto: LatLng) {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO /* || _modoEdicion.value == ModoEdicionPoligono.EDITANDO */) {
            val puntosActuales = _puntosPoligonoActual.value?.toMutableList() ?: mutableListOf()
            puntosActuales.add(punto)
            _puntosPoligonoActual.value = puntosActuales
        }
    }

    fun deshacerUltimoPunto() {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO /* || ... */ && _puntosPoligonoActual.value?.isNotEmpty() == true) {
            val puntosActuales = _puntosPoligonoActual.value!!.toMutableList()
            puntosActuales.removeAt(puntosActuales.size - 1)
            _puntosPoligonoActual.value = puntosActuales
        }
    }

    fun reiniciarPoligonoActual() {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO /* || ... */) {
            _puntosPoligonoActual.value = emptyList()
        } else{
            if(_modoEdicion.value == ModoEdicionPoligono.EDITANDO) {

            } else {
                Log.w("MapsViewModel", "No se está creando ni editando un polígono.")
            }
        }
    }

    fun guardarPoligonoActual() { // Nombre podría ser opcional si editas
        val puntosAGuardar = _puntosPoligonoActual.value
        if (puntosAGuardar.isNullOrEmpty() || puntosAGuardar.size < 3) { // Un polígono necesita al menos 3 puntos
            Log.w("MapsViewModel", "No hay suficientes puntos para guardar el polígono.")
            Toast.makeText(getApplication(), "Debe haber al menos 3 puntos para crear un polígono.", Toast.LENGTH_LONG).show()
            cancelarCreacionEdicionPoligono()
            return
        }

        when (_modoEdicion.value) {
            ModoEdicionPoligono.CREANDO -> {
                viewModelScope.launch {
                    // val nombreReal = nombre ?: "Área ${System.currentTimeMillis()}" // Nombre por defecto si es nulo
                    // val nuevoPoligono = PoligonoData(id = 0 /* o generado por DB */, nombre = nombreReal, puntos = puntosAGuardar)
                    // poligonoRepository.insertarPoligono(nuevoPoligono)

                        val poligono = Poligono(
                            id = 0,
                            puntos = puntosAGuardar.map { LatLng(it.latitude, it.longitude) }
                        )
                        poligonosDao.insertar(poligono)
                    Log.d("MapsViewModel", "Guardando NUEVO polígono con puntos: $puntosAGuardar")
                    // cargarPoligonos() // Vuelve a cargar la lista de polígonos
                }
            }
            ModoEdicionPoligono.EDITANDO -> {
                val idPoligonoEditado = _poligonoSeleccionadoId.value
                if (idPoligonoEditado != null) {
                    viewModelScope.launch {
                        // val poligonoActualizado = PoligonoData(id = idPoligonoEditado, nombre = "Nombre del polígono editado si es necesario", puntos = puntosAGuardar)
                        // poligonoRepository.actualizarPoligono(poligonoActualizado)
                        val poligono = Poligono(
                            id = idPoligonoEditado,
                            puntos = puntosAGuardar.map { LatLng(it.latitude, it.longitude) }
                        )
                        poligonosDao.insertar(poligono)
                        Log.d("MapsViewModel", "Actualizando polígono ID $idPoligonoEditado con puntos: $puntosAGuardar")
                        // cargarPoligonos() // Vuelve a cargar la lista de polígonos
                    }
                } else {
                    Log.e("MapsViewModel", "Error: Se intentó guardar en modo edición sin un ID de polígono seleccionado.")
                }
            }
            else -> {
                Log.w("MapsViewModel", "Guardar llamado en modo incorrecto: ${_modoEdicion.value}")
                return // No hacer nada si no estamos creando o editando
            }
        }
        _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        _puntosPoligonoActual.value = emptyList() // Limpia los puntos después de guardar

        cargarPoligonosBD()
    }

    fun seleccionarPoligonoPorId(idPoligono: Int?) {
        if (_poligonoSeleccionadoId.value == idPoligono && idPoligono != null) {
            // Si se hace clic de nuevo en el mismo polígono seleccionado, podría deseleccionarlo o iniciar edición de puntos
            // Para ejemplo, lo deseleccionaremos.
            _poligonoSeleccionadoId.value = null
            _modoEdicion.value = ModoEdicionPoligono.NINGUNO // Salir de cualquier modo de edición
        } else {
            _poligonoSeleccionadoId.value = idPoligono
            _modoEdicion.value =
                ModoEdicionPoligono.NINGUNO // Por defecto, solo seleccionar, no editar puntos
        }
    }

    fun iniciarEdicionPuntosPoligonoSeleccionado() {
        val idPoligonoAEditar = _poligonoSeleccionadoId.value ?: return // No hay nada seleccionado

        // Encuentra el polígono en tu lista de polígonos guardados (o cárgalo desde el repositorio)
        // Este es un ejemplo si tienes 'poligonosGuardados' como un LiveData<List<TuClasePoligono>>
        // Deberías tener una forma de acceder a los datos de tus polígonos.
        val poligonos = this.poligonos.value // 'this.poligonos' se refiere al LiveData de tus polígonos guardados
        val poligonoParaEditar = poligonos?.find { it.id == idPoligonoAEditar }

        if (poligonoParaEditar != null) {
            _puntosPoligonoActual.value = poligonoParaEditar.puntos.toList() // Crea una nueva lista para asegurar la observabilidad
            _modoEdicion.value = ModoEdicionPoligono.EDITANDO
            Log.d("MapsViewModel", "Iniciando edición para polígono ID: $idPoligonoAEditar. Puntos cargados: ${poligonoParaEditar.puntos.size}")
        } else {
            Log.w("MapsViewModel", "No se encontró el polígono con ID $idPoligonoAEditar para editar.")
            // Opcionalmente, volver a NINGUNO si no se puede editar
            // _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        }
    }

    fun actualizarPuntoPoligono(indice: Int, nuevoPunto: LatLng) {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            val puntosActuales = _puntosPoligonoActual.value?.toMutableList() ?: return
            if (indice >= 0 && indice < puntosActuales.size) {
                puntosActuales[indice] = nuevoPunto
                _puntosPoligonoActual.value = puntosActuales
            }
        }
    }

    fun eliminarPuntoPoligono(indice: Int) {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            val puntosActuales = _puntosPoligonoActual.value?.toMutableList() ?: return
            if (indice >= 0 && indice < puntosActuales.size) {
                puntosActuales.removeAt(indice)
                _puntosPoligonoActual.value = puntosActuales
            }
        }
    }

    fun eliminarPoligonoSeleccionado() {
        val idPoligonoAEliminar = _poligonoSeleccionadoId.value ?: return // No hay nada seleccionado
        viewModelScope.launch {
            poligonosDao.eliminarPorId(idPoligonoAEliminar)
            _poligonos.value = poligonosDao.obtenerTodos()
            _poligonoSeleccionadoId.value = null // Deselecciona el polígono después de eliminarlo
            _modoEdicion.value = ModoEdicionPoligono.NINGUNO // Salir de cualquier modo de edición
        }
    }

    fun cargarDispositivosBD() {
        viewModelScope.launch {
            _dispositivos.value = dispositivosDao.obtenerTodos()
        }
    }

    fun cargarPoligonosBD() {
        viewModelScope.launch {
            _poligonos.value = poligonosDao.obtenerTodos()
        }
    }

    fun guardarPoligono(id: Int, lista: List<LatLng>){
        //TODO: Implementar lógica para guardar polígono
        viewModelScope.launch {
            val poligono = Poligono(
                id = id,
                puntos = lista.map { LatLng(it.latitude, it.longitude) }
            )
            poligonosDao.insertar(poligono)
            _poligonos.value = poligonosDao.obtenerTodos()
        }
    }

    fun guardarDispositivos(lista: List<Dispositivo>) {
        viewModelScope.launch {
            dispositivosDao.insertarTodos(lista)
            _dispositivos.value = lista
        }
    }

    fun verificarEstadoDispositivos() {
        Log.d("MapsViewModel", "Verificando estado de dispositivos...")
        viewModelScope.launch {
            val ahora = System.currentTimeMillis()
            Log.d("MapsViewModel", "Cantidad de poligonos: ${_poligonos.value?.size ?: 0}")
            Log.d("MapsViewModel", "Cantidad de dispositivos: ${_dispositivos.value?.size ?: 0}")
            val poligonosActuales = _poligonos.value ?: emptyList() // Usar emptyList si es nulo
            val dispositivosActualizados = _dispositivos.value?.map { dispositivo ->
                val ultimaConexion = dispositivo.ultimaConexion ?: 0L
                val activo = (ahora - ultimaConexion) <= 60_000L

                val ubicacion = LatLng(dispositivo.latitud, dispositivo.longitud)
                val estaDentro = dispositivoEstaDentroDeAlgunPoligono(ubicacion, poligonosActuales)

                if(dispositivo.activo != activo || dispositivo.dentroDelArea != estaDentro) {
                    dispositivo.copy(
                        activo = activo,
                        dentroDelArea = estaDentro
                    )
                }else{
                    dispositivo
                }
            }

            dispositivosActualizados?.let {
                dispositivosDao.insertarTodos(it)
                _dispositivos.value = it
            }
        }
    }

    fun actualizarDetallesDispositivo(dispositivoActualizado: Dispositivo) {
        viewModelScope.launch {
            dispositivosDao.insertar(dispositivoActualizado)

            // Luego, actualiza la lista en el LiveData para que la UI reaccione
            val listaActual = _dispositivos.value?.toMutableList() ?: mutableListOf()
            val index = listaActual.indexOfFirst { it.id == dispositivoActualizado.id }
            if (index != -1) {
                listaActual[index] = dispositivoActualizado
                _dispositivos.value = listaActual // Emite la lista actualizada
            }
            // O si tu repositorio devuelve un Flow o LiveData, la UI podría actualizarse automáticamente
            // tras la operación de base de datos.
        }
    }

    fun insertarDispositivosEjemplo() {
        val lista = listOf(
            Dispositivo(1, "Vaquita mú", "Vaca con manchas negras", 24.102455, -110.316152, 1750271280, System.currentTimeMillis(), true),
            //Dispositivo(2, "Vaquita lechera", "Da mucha leche", 24.1051774, -110.3698646, 1750271280, System.currentTimeMillis(), true),
            Dispositivo(3, "Vaquita del aramburo", "Casi nos la quita el Chedraui™️", 24.1108454, -110.3129548, 1750271280, 1750271280, false),
            Dispositivo(4, "Vaquita de la calle", "No es de nadie, pero es de todos", 24.1487217, -110.2767691, 1750271280, System.currentTimeMillis(), true)
        )
        viewModelScope.launch {
            dispositivosDao.insertarTodos(lista)
            _dispositivos.value = lista
        }
        cargarDispositivosBD()
        cargarPoligonosBD()
    }

    fun seleccionarDispositivo(dispositivo: Dispositivo) {
        _dispositivoSeleccionado.value = dispositivo
    }

    fun deseleccionarDispositivo() {
        _dispositivoSeleccionado.value = null
    }

    private fun actualizarDistancia() {
        val ubicacion = _ubicacionActual.value
        val dispositivo = _dispositivoSeleccionado.value

        if (ubicacion != null && dispositivo != null) {
            val destino = Location("").apply {
                latitude = dispositivo.latitud
                longitude = dispositivo.longitud
            }
            val distanciaMetros = calcularDistancia(ubicacion, destino)
            _distanciaTexto.value = if (distanciaMetros >= 1000) {
                String.format("%.2f km", distanciaMetros / 1000)
            } else {
                String.format("%.0f m", distanciaMetros)
            }
        } else {
            if(modoEdicion.value == ModoEdicionPoligono.NINGUNO) {
                _distanciaTexto.value = "Selecciona un dispositivo"
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun iniciarActualizacionUbicacion() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    _ubicacionActual.postValue(it)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    fun detenerActualizacionUbicacion() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun calcularDistancia(origen: Location, destino: Location): Float {
        return origen.distanceTo(destino)
    }

    fun actualizarUbicacionDispositivo(dispositivoActualizado: Dispositivo) {
        val entry = markerDispositivoMap.entries.find { it.value.id == dispositivoActualizado.id }
        entry?.let { (marker, _) ->
            marker.position = LatLng(dispositivoActualizado.latitud, dispositivoActualizado.longitud)
            markerDispositivoMap[marker] = dispositivoActualizado
            if (_dispositivoSeleccionado.value?.id == dispositivoActualizado.id) {
                _dispositivoSeleccionado.value = dispositivoActualizado
            }
        }
    }

    fun obtenerBoundsParaMapa(): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _ubicacionActual.value.let { listPuntos.add(LatLng(_ubicacionActual.value!!.latitude, _ubicacionActual.value!!.longitude)) }
        _dispositivos.value?.let { dispositivos ->
            dispositivos.forEach { d ->
                listPuntos.add(LatLng(d.latitud, d.longitud))
            }
        }
        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun obtenerBoundsParaDispositivo(dispositivo: Dispositivo): LatLngBounds? {
        val listPuntos = mutableListOf<LatLng>()
        _ubicacionActual.value.let { listPuntos.add(LatLng(_ubicacionActual.value!!.latitude, _ubicacionActual.value!!.longitude)) }
        listPuntos.add(LatLng(dispositivo.latitud, dispositivo.longitud))
        if (listPuntos.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        listPuntos.forEach { builder.include(it) }
        return builder.build()
    }

    fun formatearTiempoConexion(timestamp: Long?): String {
        if (timestamp == null) return "Sin datos"

        val ahora = System.currentTimeMillis()
        val diferencia = ahora - timestamp

        val minutos = diferencia / 60_000
        val horas = diferencia / 3_600_000

        return when {
            minutos < 1 -> "Hace menos de 1 minuto"
            minutos in 1..59 -> "Hace $minutos minuto${if (minutos == 1L) "" else "s"}"
            horas == 1L -> "Hace 1 hora"
            horas in 2..23 -> "Hace $horas horas"
            else -> "Hace más de 24 horas"
        }
    }

    fun dispositivoEstaDentroDelPoligono(
        ubicacionDispositivo: LatLng,
        puntosPoligono: List<LatLng>,
        geodesic: Boolean = false // Usualmente true para cálculos más precisos en la esfera terrestre
    ): Boolean {
        if (puntosPoligono.size < 3) return false // Un polígono necesita al menos 3 puntos
        return PolyUtil.containsLocation(ubicacionDispositivo, puntosPoligono, geodesic)
    }

    // Función para encontrar en qué polígono está un dispositivo específico
    fun encontrarPoligonoParaDispositivo(dispositivo: Dispositivo): Poligono? {
        val ubicacionDispositivo = LatLng(dispositivo.latitud, dispositivo.longitud) // Ajusta según tu modelo Dispositivo
        return poligonos.value?.find { poligono ->
            dispositivoEstaDentroDelPoligono(ubicacionDispositivo, poligono.puntos)
        }
    }

    // Función para obtener todos los dispositivos dentro de un polígono específico
    fun obtenerDispositivosDentroDePoligono(idPoligono: Int): List<Dispositivo> {
        val poligonoObjetivo = poligonos.value?.find { it.id == idPoligono } ?: return emptyList()
        val ubicacionesDispositivos = dispositivos.value ?: return emptyList()

        return ubicacionesDispositivos.filter { dispositivo ->
            val ubicacionActual = LatLng(dispositivo.latitud, dispositivo.longitud)
            dispositivoEstaDentroDelPoligono(ubicacionActual, poligonoObjetivo.puntos)
        }
    }

    // Función para actualizar el estado de todos los dispositivos (por ejemplo, después de una actualización de ubicación)
    // Esto podría exponer un LiveData con la "membresía" de los dispositivos a los polígonos.
    private val _dispositivosConPoligono = MutableLiveData<Map<Int, List<Int>>>() // Map<IdPoligono, List<IdDispositivo>>
    val dispositivosConPoligono: LiveData<Map<Int, List<Int>>> = _dispositivosConPoligono

    fun verificarDispositivosEnPoligonos() {
        val poligonosActuales = poligonos.value ?: return
        val dispositivosActuales = dispositivos.value ?: return
        val nuevaMembresia = mutableMapOf<Int, MutableList<Int>>()

        poligonosActuales.forEach { poligono ->
            nuevaMembresia[poligono.id] = mutableListOf()
            dispositivosActuales.forEach { dispositivo ->
                val ubicacion = LatLng(dispositivo.latitud, dispositivo.longitud)
                if (dispositivoEstaDentroDelPoligono(ubicacion, poligono.puntos)) {
                    nuevaMembresia[poligono.id]?.add(dispositivo.id) // Asume que Dispositivo tiene un 'id'
                }
            }
        }
        _dispositivosConPoligono.value = nuevaMembresia
        Log.d("MapsViewModel", "Verificación de dispositivos en polígonos completada: $nuevaMembresia")
    }

    private fun dispositivoEstaDentroDeAlgunPoligono(
        ubicacionDispositivo: LatLng,
        listaPoligonos: List<Poligono> // Pasa la lista actual de polígonos
    ): Boolean {
        if (listaPoligonos.isEmpty()) return false
        return listaPoligonos.any { poligono ->
            if (poligono.puntos.size < 3) false
            else PolyUtil.containsLocation(ubicacionDispositivo, poligono.puntos, true)
        }
    }

    fun actualizarEstadoDentroDelAreaDispositivos() {
        val dispositivosActuales = _dispositivos.value ?: return
        val poligonosActuales = poligonos.value ?: emptyList() // Usar emptyList si es nulo

        // Crea una NUEVA lista con los estados actualizados para que LiveData emita una actualización
        val dispositivosActualizados = dispositivosActuales.map { dispositivo ->
            val ubicacion = LatLng(dispositivo.latitud, dispositivo.longitud)
            val estaDentro = dispositivoEstaDentroDeAlgunPoligono(ubicacion, poligonosActuales)
            // O si quieres asociarlo a un polígono específico:
            // val idPoligonoContenedor = obtenerIdPoligonoContenedor(ubicacion, poligonosActuales)
            // dispositivo.copy(dentroDelArea = (idPoligonoContenedor != null), idPoligonoActual = idPoligonoContenedor)

            if (dispositivo.dentroDelArea != estaDentro) { // Solo copia si el estado cambia
                dispositivo.copy(dentroDelArea = estaDentro)
            } else {
                dispositivo // Sin cambios, devuelve el original
            }
        }

        // Solo actualiza el LiveData si hubo cambios reales en el estado 'dentroDelArea' de algún dispositivo,
        // o si la lista de dispositivos en sí cambió (lo cual ya maneja LiveData).
        // Una forma simple es siempre postear, LiveData es eficiente si el contenido no cambia.
        if (dispositivosActuales != dispositivosActualizados) { // Comprueba si la lista transformada es diferente
            _dispositivos.value = dispositivosActualizados
            Log.d("MapsViewModel", "Estado 'dentroDelArea' de dispositivos actualizado.")
        }
    }


}