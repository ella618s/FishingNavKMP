package com.example.fishingmapkmp // 🎯 必須與 App.kt 檔案最上方的 package 完全一樣

import kotlin.math.*

object LocationUtils {
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2.0) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceInMeters = r * c

        return if (distanceInMeters >= 1000) {
            "${(distanceInMeters / 1000).toString().take(5)} km"
        } else {
            "${distanceInMeters.toInt()} m"
        }
    }
}