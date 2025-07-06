package com.rafaelcosio.gpslivestock.domain.usecase.polygon

import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class GuardarPoligonoUseCase @Inject constructor(
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(idExistente: Int?, puntos: List<LatLng>) {
        if (puntos.size < 3) {
            throw IllegalArgumentException("Un polígono debe tener al menos 3 puntos.")
        }

        val poligono = Poligono(
            id = idExistente ?: 0,
            puntos = puntos.map { LatLng(it.latitude, it.longitude) }
        )

        if (idExistente != null) {
            poligonoRepository.updatePoligono(poligono)
        } else {
            poligonoRepository.insertPoligono(poligono)
        }
    }
}