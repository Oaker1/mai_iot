package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class BatteryService : Service() {
    private lateinit var handler: Handler
    private val intervalMillis = 2_000L // 2 секунд
    private val notificationId = 1
    private val channelId = "battery_service_channel"

    private val sendBatteryDataRunnable = object : Runnable {
        override fun run() {
            sendBatteryData()
            handler.postDelayed(this, intervalMillis)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        startForeground(notificationId, createNotification())
        handler.post(sendBatteryDataRunnable)
        Log.d("BatteryService", "Service started")
    }

    override fun onDestroy() {
        handler.removeCallbacks(sendBatteryDataRunnable)
        Log.d("BatteryService", "Service stopped")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Battery Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for battery monitoring service"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Battery Monitor")
            .setContentText("Sending battery data to ESP32")
            .setSmallIcon(R.drawable.ic_stat_name) // Убедитесь что у вас есть этот ресурс
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendBatteryData() {
        val batteryLevel = getBatteryLevel()
        val batteryChargeCounter = getBatteryChargeCounter()
        val batteryCurrentAvg = getBatteryCurrentAverage()
        val batteryCurrentNow = getBatteryCurrentNow()

        Log.d("BatteryService", "Sending data - Level: $batteryLevel%, Counter: $batteryChargeCounter, Average Current: $batteryCurrentAvg, Current now:$batteryCurrentNow")

        sendToEsp32(batteryLevel, batteryChargeCounter, batteryCurrentAvg, batteryCurrentNow)
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } else {
            -1
        }
    }

    private fun getBatteryCurrentAverage(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        } else {
            -1
        }
    }

    private fun getBatteryCurrentNow(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } else {
            -1
        }
    }

    private fun sendToEsp32(level: Int, chargeCounter: Int, currentAvg: Int, batteryCurrentNow: Int) {
        Thread {
            val url = "http://ESP_IP/update?battery_level=$level&charge_counter=$chargeCounter&current_avg=$currentAvg&current_now=$batteryCurrentNow"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .build()

            try {
                val response = client.newCall(request).execute()
                Log.d("BatteryService", "ESP32 response: ${response.body?.string()}")
            } catch (e: IOException) {
                Log.e("BatteryService", "Error sending to ESP32: ${e.message}")
            }
        }.start()
    }
}
