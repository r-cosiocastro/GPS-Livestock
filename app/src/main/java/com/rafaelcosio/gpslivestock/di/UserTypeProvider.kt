package com.rafaelcosio.gpslivestock.di

import com.rafaelcosio.gpslivestock.data.model.UserType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserTypeProvider @Inject constructor() {

    private val _userType = MutableStateFlow(UserType.REGULAR_USER)
    val userType = _userType.asStateFlow()

    fun updateUserType(newUserType: UserType) {
        _userType.value = newUserType
    }

    fun getCurrentUserType(): UserType {
        return _userType.value
    }

    fun clear() {
        _userType.value = UserType.REGULAR_USER
    }
}