package com.example.fishingmapkmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        // 帶入預設 ViewModel 啟動主 Compose App 介面
        val viewModel = SharedViewModel(detector = AnomalyDetector())
        App(viewModel = viewModel)
    }
}