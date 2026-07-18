package com.example.fishingmapkmp

data class NavSnapshot(
    val timestamp: Long,
    val speedKnots: Double,
    val courseDegrees: Double,
    val latitude: Double,
    val longitude: Double
)

class SlidingWindowBuffer(private val windowSize: Int = 15) { // 每 2 秒更新一次，15 筆約為 30 秒的時序視窗
    private val buffer = mutableListOf<NavSnapshot>()

    /**
     * 新增定位快照，當視窗填滿（達到 windowSize）時，會回傳完整的時序列表供 AI 預測
     */
    fun addSample(snapshot: NavSnapshot): List<NavSnapshot>? {
        buffer.add(snapshot)
        if (buffer.size > windowSize) {
            buffer.removeAt(0) // 移除最舊的一筆，保持滑動視窗大小
        }
        return if (buffer.size == windowSize) buffer.toList() else null
    }
}

/**
 * 🎯 On-Device AI 離線時序異常偵測器
 * 核心演算法：多變量協方差馬氏距離 (Mahalanobis Distance for Multivariate Time-Series)
 */
class AnomalyDetector {

    private val meanSpeed = 10.0
    private val meanHeadingDiff = 0.0

    private val invCov00 = 0.125
    private val invCov01 = -0.015
    private val invCov10 = -0.015
    private val invCov11 = 0.050

    // 🚀 讓 Swift 可以傳入閉包設定 Native 預測器
    private var nativePredictor: ((FloatArray) -> Float)? = null

    fun setupNativePredictor(predictor: (FloatArray) -> Float) {
        this.nativePredictor = predictor
    }

    fun loadModel(modelPath: String) {
        // Edge AI 數學引擎初始化完成
    }

    /**
     * 接收從特徵工程傳入的 FloatArray 進行時序分析
     */
    fun detectAnomaly(inputData: FloatArray): Float {
        if (inputData.size < 4) return 0.0f

        var totalDistance = 0.0
        val pairCount = inputData.size / 2

        for (i in 0 until pairCount) {
            val speed = inputData[i * 2].toDouble()
            val heading = inputData[i * 2 + 1].toDouble()

            val headingDiff = if (i > 0) {
                val prevHeading = inputData[(i - 1) * 2 + 1].toDouble()
                var diff = heading - prevHeading
                while (diff < -180.0) diff += 360.0
                while (diff > 180.0) diff -= 360.0
                diff
            } else {
                0.0
            }

            val deltaX = speed - meanSpeed
            val deltaY = headingDiff - meanHeadingDiff

            val mahalanobisSq = (deltaX * invCov00 + deltaY * invCov10) * deltaX +
                    (deltaX * invCov01 + deltaY * invCov11) * deltaY

            val distance = if (mahalanobisSq > 0) kotlin.math.sqrt(mahalanobisSq) else 0.0
            totalDistance += distance
        }

        val avgDistance = totalDistance / pairCount
        val anomalyProbability = (avgDistance / 5.0).toFloat()

        return anomalyProbability.coerceIn(0.0f, 1.0f)
    }
}