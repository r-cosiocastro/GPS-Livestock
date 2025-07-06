package com.rafaelcosio.gpslivestock.domain.usecase.tracker

import android.content.Context
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepository
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.utils.LocationUtils.dispositivoEstaDentroDeCualquierPoligono
import com.rafaelcosio.gpslivestock.utils.NotificationHelper
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VerificarEstadoRastreadoresUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val rastreadorRepository: RastreadorRepository,
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(): List<Rastreador> {
        val ahora = System.currentTimeMillis()
        val poligonosActuales = poligonoRepository.getAllPoligonosOnce()
        val rastreadoresActuales = rastreadorRepository.getAllDispositivosOnce()

        return rastreadoresActuales.map { rastreador ->
            val ultimaConexion = rastreador.ultimaConexion ?: 0L
            val activo = (ahora - ultimaConexion) <= 60_000L

            if(rastreador.activo && !activo){
                NotificationHelper.showTrackerDisconnectedNotification(appContext,
                    rastreador.nombre.toString(), rastreador.id)
            }

            val ubicacion = LatLng(rastreador.latitud, rastreador.longitud)
            val estaDentro = dispositivoEstaDentroDeCualquierPoligono(ubicacion, poligonosActuales)

            if(rastreador.dentroDelArea && !estaDentro){
                NotificationHelper.showTrackerOutOfAreaNotification(appContext,
                    rastreador.nombre.toString(), rastreador.id)
            }

            if (rastreador.activo != activo || rastreador.dentroDelArea != estaDentro) {
                rastreador.copy(activo = activo, dentroDelArea = estaDentro)
            } else {
                rastreador
            }
        }
    }
}