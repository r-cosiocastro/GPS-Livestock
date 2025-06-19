package com.dasc.pecustrack.utils

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.gms.maps.model.LatLng // Assuming you are using Google Maps LatLng

class LatLngListConverter {
    @TypeConverter
    fun fromLatLngList(value: List<LatLng>?): String? {
        if (value == null) {
            return null
        }
        val gson = Gson()
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLatLngList(value: String?): List<LatLng>? {
        if (value == null) {
            return null
        }
        val gson = Gson()
        val type = object : TypeToken<List<LatLng>>() {}.type
        return gson.fromJson(value, type)
    }
}