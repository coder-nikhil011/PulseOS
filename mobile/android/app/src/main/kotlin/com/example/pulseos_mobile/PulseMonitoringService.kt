package com.example.pulseos_mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class PulseMonitoringService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            monitorBatteryState()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private var lastMessage: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("PulseOS monitoring", "Checking device health..."))
        handler.post(checkRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun monitorBatteryState() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        val thermalText = if (temperature > 0) {
            String.format(Locale.US, "%.1f", temperature / 10.0)
        } else {
            "unknown"
        }

        val message = when {
            batteryLevel in 0..19 -> "Battery is low (${batteryLevel}%) — charge soon."
            temperature > 450 -> "Device is overheating (${thermalText}°C)."
            batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING -> "Phone is charging normally."
            else -> "PulseOS monitoring is active."
        }

        if (message != lastMessage) {
            showNotification("PulseOS health", message)
            lastMessage = message
        }
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, message))
    }

    private fun buildNotification(title: String, message: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "PulseOS health monitoring"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "pulseos_monitoring_channel"
        private const val CHANNEL_NAME = "PulseOS Monitoring"
        private const val NOTIFICATION_ID = 2001
        private const val CHECK_INTERVAL_MS = 30_000L

        fun startMonitoring(context: Context) {
            val intent = Intent(context, PulseMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMonitoring(context: Context) {
            context.stopService(Intent(context, PulseMonitoringService::class.java))
        }
    }
}
