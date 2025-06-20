package com.dasc.pecustrack.domain.usecase.polygon

import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.dasc.pecustrack.utils.LocationUtils.dispositivoEstaDentroDelPoligono
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class EncontrarPoligonoParaDispositivoUseCase @Inject constructor(
    private val poligonoRepository: PoligonoRepository // Inyecta la interfaz del repositorio
) {
    suspend operator fun invoke(dispositivo: Dispositivo): Poligono? {
        val ubicacionDispositivo = LatLng(dispositivo.latitud, dispositivo.longitud)

        val poligonosActuales = poligonoRepository.getAllPoligonosOnce()

        return poligonosActuales.find { poligono ->
            dispositivoEstaDentroDelPoligono(ubicacionDispositivo, poligono.puntos)
        }
    }
}