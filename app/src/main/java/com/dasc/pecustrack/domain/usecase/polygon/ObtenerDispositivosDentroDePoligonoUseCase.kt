package com.dasc.pecustrack.domain.usecase.polygon

import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.dasc.pecustrack.utils.LocationUtils.dispositivoEstaDentroDelPoligono
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class ObtenerDispositivosDentroDePoligonoUseCase @Inject constructor(
    private val dispositivoRepository: DispositivoRepository,
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(idPoligono: Int): List<Dispositivo> {
        val poligonoObjetivo: Poligono? = poligonoRepository.getPoligonoById(idPoligono)

        if (poligonoObjetivo == null || poligonoObjetivo.puntos.size < 3) {
            return emptyList()
        }

        val todosLosDispositivos = dispositivoRepository.getAllDispositivosOnce()
        if (todosLosDispositivos.isEmpty()) {
            return emptyList()
        }

        return todosLosDispositivos.filter { dispositivo ->
            val ubicacionActual = LatLng(dispositivo.latitud, dispositivo.longitud)

            dispositivoEstaDentroDelPoligono(ubicacionActual, poligonoObjetivo.puntos)
        }
    }
}