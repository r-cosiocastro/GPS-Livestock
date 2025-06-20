package com.dasc.pecustrack.domain.editor

import android.util.Log
import com.dasc.pecustrack.ui.viewmodel.ModoEdicionPoligono
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject


class PoligonoEditorManager @Inject constructor() {

    private val _modoEdicion = MutableStateFlow(ModoEdicionPoligono.NINGUNO)
    val modoEdicion: StateFlow<ModoEdicionPoligono> = _modoEdicion.asStateFlow()

    private val _puntosPoligonoActual = MutableStateFlow<List<LatLng>>(emptyList())
    val puntosPoligonoActual: StateFlow<List<LatLng>> = _puntosPoligonoActual.asStateFlow()

    private val _idPoligonoEnEdicion = MutableStateFlow<Int?>(null) // Inicialmente null
    val idPoligonoEnEdicion: StateFlow<Int?> = _idPoligonoEnEdicion.asStateFlow() // Exponer como StateFlow


    fun actualizarPuntoPoligonoActual(indice: Int, nuevaPosicion: LatLng) {
        if (_modoEdicion.value != ModoEdicionPoligono.EDITANDO && _modoEdicion.value != ModoEdicionPoligono.CREANDO) {
            Log.w("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Ignorado, no en modo edición (${_modoEdicion.value})")
            return
        }
        if (indice >= 0 && indice < _puntosPoligonoActual.value.size) {
            val puntosActuales = _puntosPoligonoActual.value
            val nuevaLista = puntosActuales.toMutableList()
            nuevaLista[indice] = nuevaPosicion
            _puntosPoligonoActual.value = nuevaLista // <-- PUNTO CRÍTICO 3: ¿Se emite un nuevo valor?
            Log.d("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Puntos actualizados. Nueva lista emitida. Tamaño: ${nuevaLista.size}, Punto $indice ahora en $nuevaPosicion")
        } else {
            Log.w("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Índice $indice fuera de rango (${_puntosPoligonoActual.value.size}).")
        }
    }

    fun establecerPoligonoParaEdicion(idPoligono: Int?, puntosExistentes: List<LatLng> = emptyList()) {
        if (idPoligono == null) {
            // Si se pasa null, o se quiere cancelar la edición actual.
            // Esto es similar a iniciar una nueva creación o cancelar todo.
            _idPoligonoEnEdicion.value = null
            _puntosPoligonoActual.value = emptyList() // O los puntos iniciales para creación
            _modoEdicion.value = ModoEdicionPoligono.NINGUNO // O CREANDO si es el flujo deseado
            // Depende de la semántica de esta función.
            // Podrías necesitar funciones más específicas.
            Log.d("PoligonoEditorManager", "EstablecerPoligonoParaEdicion: ID null, modo NINGUNO, puntos limpiados.")

        } else {
            // Editando un polígono existente
            _idPoligonoEnEdicion.value = idPoligono
            _puntosPoligonoActual.value = puntosExistentes.toList() // Copia defensiva
            _modoEdicion.value = ModoEdicionPoligono.EDITANDO
            Log.d("PoligonoEditorManager", "EstablecerPoligonoParaEdicion: Editando ID $idPoligono, ${puntosExistentes.size} puntos, modo EDITANDO.")
        }
    }

    fun iniciarModoCreacionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.CREANDO
        _puntosPoligonoActual.value = emptyList()
        _idPoligonoEnEdicion.value = null // Importante: al crear uno nuevo, no hay ID existente.
        Log.d("PoligonoEditorManager", "iniciarModoCreacionPoligono: Modo CREANDO, puntos limpiados, ID null.")
    }

    fun iniciarModoEdicionPuntos(idPoligono: Int, puntosExistentes: List<LatLng>) {
        if (puntosExistentes.isEmpty()) { // O < 3 si es tu mínimo
            Log.w("PoligonoEditorManager", "iniciarModoEdicionPuntos: No se puede editar polígono $idPoligono sin puntos suficientes.")
            cancelarCreacionEdicionPoligono()
            return
        }
        _idPoligonoEnEdicion.value = idPoligono // Actualiza el StateFlow
        _puntosPoligonoActual.value = puntosExistentes.toList()
        _modoEdicion.value = ModoEdicionPoligono.EDITANDO
        Log.d("PoligonoEditorManager", "iniciarModoEdicionPuntos: Editando ID $idPoligono, ${puntosExistentes.size} puntos, modo EDITANDO.")
    }

    fun cancelarCreacionEdicionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        _puntosPoligonoActual.value = emptyList()
        _idPoligonoEnEdicion.value = null // Asegúrate que el ID también se limpia
        Log.d("PoligonoEditorManager", "cancelarCreacionEdicionPoligono: Modo NINGUNO, puntos limpiados, ID null.")
    }

    fun anadirPuntoAPoligonoActual(punto: LatLng) {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            _puntosPoligonoActual.update { it + punto }
        }
    }

    fun deshacerUltimoPunto() {
        if ((_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) &&
            _puntosPoligonoActual.value.isNotEmpty()
        ) {
            _puntosPoligonoActual.update { it.dropLast(1) }
        }
    }

    fun reiniciarPoligonoActual() { // Borra todos los puntos del polígono en creación/edición
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            _puntosPoligonoActual.value = emptyList()
            // En modo EDICIÓN, esto significa que el usuario borró todos los puntos.
            // Podrías necesitar lógica adicional aquí para manejar qué pasa si guardas un polígono vacío.
        }
    }

    fun actualizarPuntoPoligono(indice: Int, nuevoPunto: LatLng) {
        if ((_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) &&
            indice >= 0 && indice < _puntosPoligonoActual.value.size
        ) {
            _puntosPoligonoActual.update { puntosActuales ->
                puntosActuales.toMutableList().apply { this[indice] = nuevoPunto }
            }
        }
    }

    fun eliminarPuntoPoligono(indice: Int) {
        if ((_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) &&
            indice >= 0 && indice < _puntosPoligonoActual.value.size
        ) {
            _puntosPoligonoActual.update { puntosActuales ->
                puntosActuales.toMutableList().apply { removeAt(indice) }
            }
        }
    }

    fun finalizarEdicionPoligono() {
        Log.d("PoligonoEditorManager", "finalizarEdicionPoligono: Limpiando estado de edición.")
        _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        _puntosPoligonoActual.value = emptyList()
        _idPoligonoEnEdicion.value = null
    }

    /**
     * Prepara los datos para guardar. El guardado real (llamada al repositorio)
     * se hará desde el ViewModel/UseCase.
     * @return Un par con el ID del polígono (si se está editando) y la lista de puntos,
     * o null si no se puede guardar (ej. menos de 3 puntos).
     */
    fun obtenerDatosParaGuardar(): Pair<Int?, List<LatLng>>? {
        val puntosAGuardar = _puntosPoligonoActual.value
        if (puntosAGuardar.size < 3 && _modoEdicion.value != ModoEdicionPoligono.NINGUNO) { // Permitir "guardar" (finalizar) NINGUNO
            Log.w("PoligonoEditorManager", "obtenerDatosParaGuardar: No se puede guardar, puntos insuficientes (${puntosAGuardar.size}) y no es NINGUNO.")
            return null
        }

        // El ID del polígono a guardar es el que está en _idPoligonoEnEdicion.value
        // si estamos en modo EDITANDO. Si estamos en CREANDO, el ID es null (para nuevo polígono).
        val idParaGuardar = if (_modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            _idPoligonoEnEdicion.value
        } else {
            null // Para CREANDO o si por alguna razón no hay ID
        }

        Log.d("PoligonoEditorManager", "obtenerDatosParaGuardar: ID: $idParaGuardar, Puntos: ${puntosAGuardar.size}, Modo: ${_modoEdicion.value}")
        return Pair(idParaGuardar, puntosAGuardar)
    }

}