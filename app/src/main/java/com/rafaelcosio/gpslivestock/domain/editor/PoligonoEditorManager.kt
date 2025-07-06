package com.rafaelcosio.gpslivestock.domain.editor

import android.util.Log
import com.rafaelcosio.gpslivestock.ui.viewmodel.ModoEdicionPoligono
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

    private val _idPoligonoEnEdicion = MutableStateFlow<Int?>(null)
    val idPoligonoEnEdicion: StateFlow<Int?> = _idPoligonoEnEdicion.asStateFlow()


    fun actualizarPuntoPoligonoActual(indice: Int, nuevaPosicion: LatLng) {
        if (_modoEdicion.value == ModoEdicionPoligono.NINGUNO) {
            Log.w("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Ignorado, no en modo edición (${_modoEdicion.value})")
            return
        }
        if (indice >= 0 && indice < _puntosPoligonoActual.value.size) {
            val puntosActuales = _puntosPoligonoActual.value
            val nuevaLista = puntosActuales.toMutableList()
            nuevaLista[indice] = nuevaPosicion
            _puntosPoligonoActual.value = nuevaLista
            Log.d("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Puntos actualizados. Nueva lista emitida. Tamaño: ${nuevaLista.size}, Punto $indice ahora en $nuevaPosicion")
        } else {
            Log.w("PoligonoEditorManager", "actualizarPuntoPoligonoActual: Índice $indice fuera de rango (${_puntosPoligonoActual.value.size}).")
        }
    }

    fun establecerPoligonoParaEdicion(idPoligono: Int?, puntosExistentes: List<LatLng> = emptyList()) {
        if (idPoligono == null) {
            _idPoligonoEnEdicion.value = null
            _puntosPoligonoActual.value = emptyList()
            _modoEdicion.value = ModoEdicionPoligono.NINGUNO
            Log.d("PoligonoEditorManager", "EstablecerPoligonoParaEdicion: ID null, modo NINGUNO, puntos limpiados.")

        } else {
            _idPoligonoEnEdicion.value = idPoligono
            _puntosPoligonoActual.value = puntosExistentes.toList()
            _modoEdicion.value = ModoEdicionPoligono.EDITANDO
            Log.d("PoligonoEditorManager", "EstablecerPoligonoParaEdicion: Editando ID $idPoligono, ${puntosExistentes.size} puntos, modo EDITANDO.")
        }
    }

    fun iniciarModoCreacionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.CREANDO
        _puntosPoligonoActual.value = emptyList()
        _idPoligonoEnEdicion.value = null
        Log.d("PoligonoEditorManager", "iniciarModoCreacionPoligono: Modo CREANDO, puntos limpiados, ID null.")
    }

    fun iniciarModoEdicionPuntos(idPoligono: Int, puntosExistentes: List<LatLng>) {
        if (puntosExistentes.isEmpty()) {
            Log.w("PoligonoEditorManager", "iniciarModoEdicionPuntos: No se puede editar polígono $idPoligono sin puntos suficientes.")
            cancelarCreacionEdicionPoligono()
            return
        }
        _idPoligonoEnEdicion.value = idPoligono
        _puntosPoligonoActual.value = puntosExistentes.toList()
        _modoEdicion.value = ModoEdicionPoligono.EDITANDO
        Log.d("PoligonoEditorManager", "iniciarModoEdicionPuntos: Editando ID $idPoligono, ${puntosExistentes.size} puntos, modo EDITANDO.")
    }

    fun cancelarCreacionEdicionPoligono() {
        _modoEdicion.value = ModoEdicionPoligono.NINGUNO
        _puntosPoligonoActual.value = emptyList()
        _idPoligonoEnEdicion.value = null
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

    fun reiniciarPoligonoActual() {
        if (_modoEdicion.value == ModoEdicionPoligono.CREANDO || _modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            _puntosPoligonoActual.value = emptyList()
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
    fun obtenerDatosParaGuardar(): Pair<Int?, List<LatLng>>? {
        val puntosAGuardar = _puntosPoligonoActual.value
        if (puntosAGuardar.size < 3 && _modoEdicion.value != ModoEdicionPoligono.NINGUNO) {
            Log.w("PoligonoEditorManager", "obtenerDatosParaGuardar: No se puede guardar, puntos insuficientes (${puntosAGuardar.size}) y no es NINGUNO.")
            return null
        }
        val idParaGuardar = if (_modoEdicion.value == ModoEdicionPoligono.EDITANDO) {
            _idPoligonoEnEdicion.value
        } else {
            null
        }

        Log.d("PoligonoEditorManager", "obtenerDatosParaGuardar: ID: $idParaGuardar, Puntos: ${puntosAGuardar.size}, Modo: ${_modoEdicion.value}")
        return Pair(idParaGuardar, puntosAGuardar)
    }

}