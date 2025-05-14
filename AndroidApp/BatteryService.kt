package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class BatteryService : Service() {
    private val binder = LocalBinder()
    private lateinit var handler: Handler
    private var updateListener: BatteryUpdateListener? = null
    private var isSending = false
    private var espIp = "192.168.0.145"
    private val updateInterval = 2000L // 2 секунды
    private val httpClient = OkHttpClient()
    private lateinit var notificationManager: NotificationManager

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isSending) {
                updateBatteryData()
                handler.postDelayed(this, updateInterval)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(1, createNotification("Сервис запущен"))
        Log.d("BatteryDebug", "Service created")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun setUpdateListener(listener: BatteryUpdateListener) {
        updateListener = listener
    }

    fun updateIpAddress(newIp: String) {
        espIp = newIp
        updateNotification("Отправка на $espIp")
    }

    fun startSending() {
        if (!isSending) {
            isSending = true
            handler.post(updateRunnable)
            updateNotification("Отправка данных...")
            Log.d("BatteryDebug", "Started sending data")
        }
    }

    fun stopSending() {
        isSending = false
        handler.removeCallbacks(updateRunnable)
        updateNotification("Сервис активен, отправка остановлена")
        Log.d("BatteryDebug", "Stopped sending data")
    }

    private fun updateBatteryData() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargeCounter = getBatteryChargeCounter(batteryManager)
        val currentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        Log.d("BatteryDebug", "Level: $level%, Charge: $chargeCounter, Current: $currentAvg/$currentNow")

        updateListener?.onBatteryUpdate(level, chargeCounter, currentAvg, currentNow)
        sendToEsp32(level, chargeCounter*2, currentAvg, currentNow)
    }

    private fun getBatteryChargeCounter(batteryManager: BatteryManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } else {
            -1 // Не поддерживается на старых версиях
        }
    }

    private fun sendToEsp32(level: Int, chargeCounter: Int, currentAvg: Int, currentNow: Int) {
        val url = "http://$espIp/update?battery_level=$level&charge_counter=$chargeCounter&current_avg=$currentAvg&current_now=$currentNow"
        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BatteryService", "Ошибка отправки: ${e.message}")
                updateNotification("Ошибка подключения к $espIp")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("BatteryService", "Ошибка HTTP: ${response.code}")
                    updateNotification("Ошибка HTTP ${response.code}")
                } else {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    updateNotification("Данные отправлены ($time)")
                }
                response.close()
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "battery_channel",
                "Мониторинг батареи",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отправки данных батареи"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "battery_channel")
            .setContentTitle("Мониторинг батареи")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(1, createNotification(text))
    }

    inner class LocalBinder : Binder() {
        fun getService(): BatteryService = this@BatteryService
    }

    interface BatteryUpdateListener {
        fun onBatteryUpdate(
            level: Int,
            chargeCounter: Int,
            currentAvg: Int,
            currentNow: Int
        )
    }
}
