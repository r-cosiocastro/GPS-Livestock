package com.rafaelcosio.gpslivestock.data.model

import androidx.room.TypeConverter

class UserTypeConverter {
    @TypeConverter
    fun fromUserType(userType: UserType?): String? {
        return userType?.name
    }

    @TypeConverter
    fun toUserType(name: String?): UserType? {
        return name?.let { UserType.valueOf(it) }
    }
}