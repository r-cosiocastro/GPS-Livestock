package com.dasc.pecustrack.domain.usecase.device

import com.dasc.pecustrack.data.model.Dispositivo
import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.dasc.pecustrack.utils.LocationUtils.dispositivoEstaDentroDeCualquierPoligono
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class VerificarEstadoDispositivosUseCase @Inject constructor(
    private val dispositivoRepository: DispositivoRepository,
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(): List<Dispositivo> {
        val ahora = System.currentTimeMillis()
        val poligonosActuales = poligonoRepository.getAllPoligonosOnce()
        val dispositivosActuales = dispositivoRepository.getAllDispositivosOnce()

        return dispositivosActuales.map { dispositivo ->
            val ultimaConexion = dispositivo.ultimaConexion ?: 0L
            val activo = (ahora - ultimaConexion) <= 60_000L

            val ubicacion = LatLng(dispositivo.latitud, dispositivo.longitud)
            val estaDentro = dispositivoEstaDentroDeCualquierPoligono(ubicacion, poligonosActuales)

            if (dispositivo.activo != activo || dispositivo.dentroDelArea != estaDentro) {
                dispositivo.copy(activo = activo, dentroDelArea = estaDentro)
            } else {
                dispositivo
            }
        }
    }
}