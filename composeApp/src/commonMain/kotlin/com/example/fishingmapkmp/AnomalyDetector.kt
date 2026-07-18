package com.example.fishingmapkmp

expect class AnomalyDetector() {
    fun loadModel(modelPath: String)
    fun detectAnomaly(inputData: FloatArray): Float
}