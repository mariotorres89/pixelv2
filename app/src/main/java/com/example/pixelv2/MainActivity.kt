package com.example.pixelv2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private val PERMISSIONS_REQUEST_BODY_SENSORS = 1  // This is an arbitrary number to identify the request

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Acquire a wakelock to keep the device awake
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AccelerometerService::Wakelock")
        wakeLock.acquire()

        // Check if the permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), PERMISSIONS_REQUEST_BODY_SENSORS)
        } else {
            // Permission has already been granted, start the services
            startServices()
        }
    }

    private fun startServices() {
        // Start SensorLoggerService
        startService(Intent(this, SensorLoggerService::class.java))

        // Start MqttPublisherService
        startService(Intent(this, MqttPublisherService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_BODY_SENSORS -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted, start the services
                    startServices()
                }
                return
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the wakelock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Stop SensorLoggerService
        stopService(Intent(this, SensorLoggerService::class.java))

        // Stop MqttPublisherService
        stopService(Intent(this, MqttPublisherService::class.java))
    }
}
