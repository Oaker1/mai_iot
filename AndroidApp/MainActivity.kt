package com.example.myapplication

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.concurrent.thread
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var batteryTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val intervalMillis = 10_000L // 10 секунд

    private val sendBatteryDataRunnable = object : Runnable {
        override fun run() {
            sendBatteryData()
            handler.postDelayed(this, intervalMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        batteryTextView = TextView(this)
        setContentView(batteryTextView)

        handler.post(sendBatteryDataRunnable) // запускаем таймер
    }

    private fun sendBatteryData() {
        val batteryLevel = getBatteryLevel()
        val batteryChargeCounter = getBatteryChargeCounter()
        val batteryCurrentAvg = getBatteryCurrentAverage()

        // Логируем данные
        Log.d("BatterySender", "Battery level: $batteryLevel%")
        Log.d("BatterySender", "Battery charge counter: $batteryChargeCounter")
        Log.d("BatterySender", "Battery current average: $batteryCurrentAvg")

        // Обновляем UI
        runOnUiThread {
            batteryTextView.text = "Battery: $batteryLevel%\nCharge counter: $batteryChargeCounter\nAverage Current: $batteryCurrentAvg"
        }

        // Отправляем данные
        sendBatteryToESP32(batteryLevel, batteryChargeCounter, batteryCurrentAvg)
    }

    private fun getBatteryLevel(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    private fun getBatteryChargeCounter(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    }

    private fun getBatteryCurrentAverage(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
    }

    private fun sendBatteryToESP32(level: Int, chargeCounter: Int, currentAvg: Int) {
        thread {
            val url = "http://123.456.0.789/update?battery_level=$level&charge_counter=$chargeCounter&current_avg=$currentAvg" // http://IP_ESP32/update...

            val request = Request.Builder()
                .url(url)
                .build()

            val client = OkHttpClient()
            try {
                val response = client.newCall(request).execute()
                Log.d("BatterySender", "ESP32 response: ${response.body?.string()}")
            } catch (e: IOException) {
                Log.e("BatterySender", "Ошибка отправки на ESP32: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(sendBatteryDataRunnable) // останавливаем при выходе
    }
}
