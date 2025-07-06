package com.rafaelcosio.gpslivestock.domain.usecase.polygon

import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.utils.LocationUtils.dispositivoEstaDentroDelPoligono
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class EncontrarPoligonoParaDispositivoUseCase @Inject constructor(
    private val poligonoRepository: PoligonoRepository // Inyecta la interfaz del repositorio
) {
    suspend operator fun invoke(rastreador: Rastreador): Poligono? {
        val ubicacionDispositivo = LatLng(rastreador.latitud, rastreador.longitud)

        val poligonosActuales = poligonoRepository.getAllPoligonosOnce()

        return poligonosActuales.find { poligono ->
            dispositivoEstaDentroDelPoligono(ubicacionDispositivo, poligono.puntos)
        }
    }
}