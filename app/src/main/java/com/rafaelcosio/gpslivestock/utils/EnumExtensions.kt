package com.rafaelcosio.gpslivestock.utils

import com.rafaelcosio.gpslivestock.data.model.UserType

/**
 * Extensión para convertir UserType a su representación en español
 */
fun UserType.toSpanish(): String {
    return when (this) {
        UserType.ADMINISTRATOR -> "Administrador"
        UserType.RANCHER -> "Ganadero"
        UserType.REGULAR_USER -> "Usuario Regular"
    }
}