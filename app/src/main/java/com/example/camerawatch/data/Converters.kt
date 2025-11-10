package com.example.camerawatch.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String?): Set<String> {
        if (value.isNullOrEmpty()) return emptySet()
        return value.split(',').filter { it.isNotBlank() }.toSet()
    }

    @TypeConverter
    fun toString(set: Set<String>?): String {
        return set?.joinToString(separator = ",") ?: ""
    }

    @TypeConverter
    fun fromFrontRearHint(value: String?): CameraSession.FrontRearHint {
        return value?.let { runCatching { CameraSession.FrontRearHint.valueOf(it) }.getOrNull() }
            ?: CameraSession.FrontRearHint.UNKNOWN
    }

    @TypeConverter
    fun toFrontRearHint(value: CameraSession.FrontRearHint?): String {
        return value?.name ?: CameraSession.FrontRearHint.UNKNOWN.name
    }

    @TypeConverter
    fun fromSessionSource(value: String?): CameraSession.SessionSource {
        return value?.let { runCatching { CameraSession.SessionSource.valueOf(it) }.getOrNull() }
            ?: CameraSession.SessionSource.AVAILABILITY
    }

    @TypeConverter
    fun toSessionSource(value: CameraSession.SessionSource?): String {
        return value?.name ?: CameraSession.SessionSource.AVAILABILITY.name
    }
}
