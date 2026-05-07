package com.azra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AzraService : Service() {

    private val CHANNEL_ID = "AzraServiceChannel"
    private var virtualCameraManager: VirtualCameraManager? = null

    private var associationId: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification(), getForegroundServiceCameraType())
        
        virtualCameraManager = VirtualCameraManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("associationId")) {
            associationId = intent.getIntExtra("associationId", 0)
            virtualCameraManager?.start(associationId)
        } else {
            // Fallback if started without ID, though it might fail later
            virtualCameraManager?.start(0)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualCameraManager?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getForegroundServiceCameraType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Azra Virtual Camera Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Azra Virtual Camera")
            .setContentText("Virtual camera is active")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()
    }
}
