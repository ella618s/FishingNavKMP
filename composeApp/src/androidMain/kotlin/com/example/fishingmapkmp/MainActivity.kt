package com.example.fishingmapkmp

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), SensorEventListener { // 實作感應器監聽介面
    // 在 MainActivity 定義 ViewModel，確保它是單一來源
    private val detector = AnomalyDetector()
    private val viewModel = SharedViewModel(detector)

    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 跳出視窗向使用者請求定位權限
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            1001
        )

        // 🎯 安全權限檢查：只有使用者授權後才執行定位
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFinePermission || hasCoarsePermission) {
            startLocationUpdates()
        }

        // 建立感應器管理員並取得氣壓計
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        setContent {
            App(viewModel)
        }
    }

    // 當 Activity 回到前景時，向系統註冊監聽氣壓計
    override fun onResume() {
        super.onResume()
        pressureSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    // Activity 進入背景時，解除註冊以省電
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    // 處理硬體回傳的氣壓數值
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val millibarsOfPressure = event.values[0].toDouble() // 取得 hPa 數值
            viewModel.updateBarometerPressure(millibarsOfPressure) // 🚀 餵進 KMP 大腦進行離線時序分析
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 暫無須處理精確度更動
    }

    // 🎯 把 GPS 監聽獨立出來，確保有權限時才呼叫
    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    viewModel.updateShipStatus(
                        speedMps = location.speed.toDouble(),
                        headingDegrees = location.bearing.toDouble(),
                        lat = location.latitude,
                        lng = location.longitude
                    )
                }
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🎯 當使用者在畫面點擊「允許權限」後，立刻啟動 GPS
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val testDetector = AnomalyDetector()
    App(SharedViewModel(testDetector))
}