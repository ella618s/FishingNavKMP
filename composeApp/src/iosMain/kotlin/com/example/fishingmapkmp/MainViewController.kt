package com.example.fishingmapkmp

import androidx.compose.ui.window.ComposeUIViewController

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun MainViewController() = ComposeUIViewController(
    // ... 裡面的代碼
) {
    App()
}