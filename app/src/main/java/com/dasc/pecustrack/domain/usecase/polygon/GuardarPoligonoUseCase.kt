package com.dasc.pecustrack.domain.usecase.polygon

import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

class GuardarPoligonoUseCase @Inject constructor(
    private val poligonoRepository: PoligonoRepository
) {
    suspend operator fun invoke(idExistente: Int?, puntos: List<LatLng>) {
        if (puntos.size < 3) {
            throw IllegalArgumentException("Un polígono debe tener al menos 3 puntos.")
            // O puedes manejar esto devolviendo un Result<Unit> o similar
        }

        val poligono = Poligono(
            id = idExistente ?: 0, // Si es nuevo, Room generará el ID si es autogenerado
            puntos = puntos.map { LatLng(it.latitude, it.longitude) } // Asegura copia
            // nombre = ... si tienes un campo nombre y lo gestionas
        )

        if (idExistente != null) {
            // Actualizar existente (asume que tu insertPoligono maneja el conflicto o tienes un update)
            poligonoRepository.updatePoligono(poligono) // Necesitarás este método
        } else {
            // Insertar nuevo
            poligonoRepository.insertPoligono(poligono)
        }
        // No es necesario llamar a cargarPoligonosBD desde aquí;
        // El ViewModel observará los cambios del repositorio.
    }
}