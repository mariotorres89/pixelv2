package com.example.pixelv2

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.lang.Exception

class MqttPublisherService : Service() {
    private lateinit var mqttPublisher: MqttPublisher
    private lateinit var mqttManager: MqttManager
    private lateinit var sensorDataDao: SensorDataDao

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create the MqttManager and SensorDataDao instances
        mqttManager = MqttManager()
        sensorDataDao = AppDatabase.getDatabase(applicationContext).sensorDataDao()
        mqttPublisher = MqttPublisher(mqttManager, sensorDataDao)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                mqttManager.connect()
                mqttPublisher.startPublishing()
            } catch (e: Exception) {
                Log.e("MqttPublisherService", "Failed to connect to MQTT or start publishing", e)
            }
        }

        val channelId = "MyServiceChannelId 2"
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "My Service Channel 2"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("My Service 2")
            .setContentText("Running in the background 2")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(2, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch(Dispatchers.IO) {
            mqttManager.disconnect()
        }
    }
}
