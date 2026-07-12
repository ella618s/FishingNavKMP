package com.example.fishingmapkmp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    // 在 MainActivity 定義 ViewModel，確保它是單一來源
    private val viewModel = SharedViewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 啟動定位監聽 (這裡是 Android 原生區，不會有紅字)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // 🚀 核心：將硬體定位數據餵給 ViewModel 大腦
                viewModel.updateShipStatus(
                    speedMps = location.speed.toDouble(),
                    headingDegrees = location.bearing.toDouble()
                )
            }
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, listener)

        setContent {
            App(viewModel)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // 預覽時傳入一個測試用的 ViewModel
    App(SharedViewModel())
}