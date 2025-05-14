package com.example.myapplication

import android.content.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var etIpAddress: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var batteryInfoText: TextView

    private var batteryService: BatteryService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BatteryService.LocalBinder
            batteryService = binder.getService()
            isBound = true
            batteryService?.setUpdateListener(batteryUpdateListener)
            Log.d("BatteryDebug", "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            batteryService = null
            Log.d("BatteryDebug", "Service disconnected")
        }
    }

    private val batteryUpdateListener = object : BatteryService.BatteryUpdateListener {
        override fun onBatteryUpdate(
            level: Int,
            chargeCounter: Int,
            currentAvg: Int,
            currentNow: Int
        ) {
            runOnUiThread {
                updateBatteryUI(level, chargeCounter, currentAvg, currentNow)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация View
        etIpAddress = findViewById(R.id.etIpAddress)
        btnConnect = findViewById(R.id.btnConnect)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        batteryInfoText = findViewById(R.id.batteryInfoText)

        setupButtons()
        bindBatteryService()
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener {
            val ip = etIpAddress.text.toString()
            if (validateIp(ip)) {
                batteryService?.updateIpAddress(ip)
                batteryService?.startSending()
                updateUI(true)
            }
        }

        btnStop.setOnClickListener {
            batteryService?.stopSending()
            updateUI(false)
        }
    }

    private fun bindBatteryService() {
        Intent(this, BatteryService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun updateBatteryUI(level: Int, chargeCounter: Int, currentAvg: Int, currentNow: Int) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        batteryInfoText.text = """
            Уровень заряда: $level%
            Ёмкость: ${chargeCounter*2 / 1000} мАч
            Ток (средний): ${currentAvg / 1000} мА
            Ток (мгновенный): ${currentNow / 1000} мА
            Обновлено: $time
        """.trimIndent()
    }

    private fun updateUI(isRunning: Boolean) {
        btnConnect.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
        tvStatus.text = if (isRunning) "Статус: активно" else "Статус: неактивно"
    }

    private fun validateIp(ip: String): Boolean {
        return ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$"))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
