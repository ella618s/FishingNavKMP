package com.example.fishingmapkmp

actual class AnomalyDetector {
    private var iosPredictor: ((FloatArray) -> Float)? = null

    fun setupNativePredictor(predictor: (FloatArray) -> Float) {
        this.iosPredictor = predictor
    }

    actual fun loadModel(modelPath: String) {
        // iOS 端由原生處理
    }

    actual fun detectAnomaly(inputData: FloatArray): Float {
        return iosPredictor?.invoke(inputData) ?: 0.0f
    }
}