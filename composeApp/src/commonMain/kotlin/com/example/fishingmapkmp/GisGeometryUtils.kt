package com.example.fishingmapkmp

import kotlin.math.*
import kotlin.math.PI // 🎯 確保有這個

/**
 * 專為航海導航設計的輕量級 GIS 計算幾何工具
 */
object GisGeometryUtils {

    // 地球半徑 (公尺)
    private const val EARTH_RADIUS = 6371000.0

    /**
     * 簡單的經緯度點模型
     */
    data class LatLng(val lat: Double, val lng: Double)

    /**
     * 1. 預測未來位置線段
     * 根據當前位置、航速（公尺/秒）與航向（角度），預測未來某段時間後的經緯度點
     * @param speedMps 當前航速 (公尺/秒)
     * @param headingDegrees 當前航向角 (0度為正北，順時針 0~360)
     * @param durationSeconds 預測未來的時間 (例如 180 秒，即 3 分鐘)
     */
    fun predictFutureLocation(current: LatLng, speedMps: Double, headingDegrees: Double, durationSeconds: Double): LatLng {
        if (speedMps <= 0.1) return current // 如果船沒動，預測點就是目前位置

        val distance = speedMps * durationSeconds
        val angularDistance = distance / EARTH_RADIUS

        // 🎯 使用純 Kotlin 的 PI 進行角度轉弧度
        val latRad = current.lat * (PI / 180.0)
        val lngRad = current.lng * (PI / 180.0)
        val headingRad = headingDegrees * (PI / 180.0)

        val predictedLatRad = asin(
            sin(latRad) * cos(angularDistance) +
                    cos(latRad) * sin(angularDistance) * cos(headingRad)
        )

        val predictedLngRad = lngRad + atan2(
            sin(headingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(predictedLatRad)
        )

        // 🎯 使用純 Kotlin 的 PI 進行弧度轉角度
        val finalLat = predictedLatRad * (180.0 / PI)
        val finalLng = predictedLngRad * (180.0 / PI)

        return LatLng(finalLat, finalLng)
    }

    /**
     * 2. 核心演算法：判斷兩條線段 (A-B) 與 (C-D) 是否相交
     * 用於比對「船隻預測軌跡線段」與「風場多邊形的某一條邊」
     */
    fun isSegmentsIntersect(A: LatLng, B: LatLng, C: LatLng, D: LatLng): Boolean {
        // 利用向量外積符號判斷 C, D 是否在直線 AB 的兩側，且 A, B 在直線 CD 的兩側
        val ccw1 = ccw(A, B, C)
        val ccw2 = ccw(A, B, D)
        val ccw3 = ccw(C, D, A)
        val ccw4 = ccw(C, D, B)

        return ((ccw1 > 0 && ccw2 < 0) || (ccw1 < 0 && ccw2 > 0)) &&
                ((ccw3 > 0 && ccw4 < 0) || (ccw3 < 0 && ccw4 > 0))
    }

    private fun ccw(A: LatLng, B: LatLng, C: LatLng): Double {
        // 簡單的二維平面投影交叉相乘（適用於局部海域）
        return (B.lng - A.lng) * (C.lat - A.lat) - (B.lat - A.lat) * (C.lng - A.lng)
    }
}