// SensorLoggerService.kt

package com.example.pixelv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SensorLoggerService : Service() {
    private lateinit var sensorDataDao: SensorDataDao
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorLogger: SensorLogger
    private lateinit var classifier: Classifier
    private lateinit var mqttManager: MqttManager

    private val handler = Handler(Looper.getMainLooper())

    private val loggerRunnable = object : Runnable {
        override fun run() {
            sensorLogger.startLogging()
            handler.postDelayed({
                Log.e("SensorLoggerService", "15 seg has passed, stopping")
                sensorLogger.stopLogging()

//                GlobalScope.launch {
//                    Log.e("SensorLoggerService", "Initializing classifier")
//                    classifier.computeFeatures()}
            }, TimeUnit.SECONDS.toMillis(15))

            handler.postDelayed(this, TimeUnit.MINUTES.toMillis(1))
        }
    }

    private val databaseRunnable = object : Runnable {
        override fun run() {
            GlobalScope.launch {
                val notSentCount = sensorDataDao.getCountNotSent()
                val sentCount = sensorDataDao.getCountSent()
                Log.d("DatabaseInfo", "Not sent: $notSentCount, Sent: $sentCount")
            }
            handler.postDelayed(this, TimeUnit.MINUTES.toMillis(1))
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorDataDao = AppDatabase.getDatabase(applicationContext).sensorDataDao()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mqttManager = MqttManager()
        sensorLogger = SensorLogger(this, sensorDataDao, mqttManager)
        classifier = Classifier(sensorDataDao, assets)
        GlobalScope.launch {
            try {
                Log.d("SensorLoggerService", "Deleting existing DataBase")
                sensorDataDao.deleteAll()
            } catch (e: Exception) {
                Log.e("SensorLoggerService", "Failed to truncate database", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "MyServiceChannelId"
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "My Service Channel"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("My Service")
            .setContentText("Running in the background")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        handler.post(loggerRunnable)
//        handler.post(databaseRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(loggerRunnable)
        sensorLogger.stopLogging()
        super.onDestroy()
    }
}
