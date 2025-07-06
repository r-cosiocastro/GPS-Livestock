package com.rafaelcosio.gpslivestock.domain.usecase.polygon

import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepository
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.utils.LocationUtils.dispositivoEstaDentroDelPoligono
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class ObtenerDispositivosDentroDePoligonoUseCase @Inject constructor(
    private val rastreadorRepository: RastreadorRepository,
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(idPoligono: Int): List<Rastreador> {
        val poligonoObjetivo: Poligono? = poligonoRepository.getPoligonoById(idPoligono)

        if (poligonoObjetivo == null || poligonoObjetivo.puntos.size < 3) {
            return emptyList()
        }

        val todosLosDispositivos = rastreadorRepository.getAllDispositivosOnce()
        if (todosLosDispositivos.isEmpty()) {
            return emptyList()
        }

        return todosLosDispositivos.filter { dispositivo ->
            val ubicacionActual = LatLng(dispositivo.latitud, dispositivo.longitud)

            dispositivoEstaDentroDelPoligono(ubicacionActual, poligonoObjetivo.puntos)
        }
    }
}