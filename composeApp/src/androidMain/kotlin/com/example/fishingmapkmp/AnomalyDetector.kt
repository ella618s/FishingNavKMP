package com.example.fishingmapkmp

actual class AnomalyDetector {
    actual fun loadModel(modelPath: String) {
        // 先留空，讓編譯通過
    }

    actual fun detectAnomaly(inputData: FloatArray): Float {
        // 先回傳 0.0f，讓編編譯通過
        return 0.0f
    }
}