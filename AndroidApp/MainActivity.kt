package com.example.myapplication

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var batteryTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Используем layout-файл вместо программного TextView

        batteryTextView = findViewById(R.id.batteryTextView)

        // Запускаем сервис для фоновой работы
        startBatteryService()

        // Первоначальное обновление данных
        updateBatteryInfo()
    }

    private fun startBatteryService() {
        val serviceIntent = Intent(this, BatteryService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateBatteryInfo() {
        val batteryLevel = getBatteryLevel()
        val batteryChargeCounter = getBatteryChargeCounter()
        val batteryCurrentAvg = getBatteryCurrentAverage()
        val batteryCurrentNow = getBatteryCurrentNow()

        batteryTextView.text = """
            Battery: $batteryLevel%
            Charge counter: $batteryChargeCounter µAh
            Average Current: $batteryCurrentAvg mA
            Current now: $batteryCurrentNow mA
        """.trimIndent()
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

    private fun getBatteryCurrentNow(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Сервис продолжит работать после закрытия Activity
        // Для остановки сервиса используйте stopService()
    }
}
