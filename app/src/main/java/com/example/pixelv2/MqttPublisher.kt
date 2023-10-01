package com.example.pixelv2

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.concurrent.TimeUnit
import android.util.Log

data class SensorDataMqtt(
    val timestamp: Long,
    val sensor: String,
    val client_id: Int,
    val measures: List<Float>
)

class MqttPublisher(
    private val mqttManager: MqttManager,
    private val sensorDataDao: SensorDataDao
) {
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private var isRunning = false

    fun startPublishing() {
        publishData()
    }

    fun stopPublishing() {
        isRunning = false
    }



    private fun publishData() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                while(true) {
                    val unsentDataCount = sensorDataDao.getCountNotSent()
                    val sentDataCount = sensorDataDao.getCountSent()
                    Log.d("MqttPublisher", "Data not Sent: $unsentDataCount, Data Sent: $sentDataCount")

                    if(unsentDataCount > 0) {
                        val end = sensorDataDao.getLastTimestamp("android.sensor.accelerometer")
                        val start = end - TimeUnit.SECONDS.toMillis(1)

                        val sensorData = sensorDataDao.getBatchUnsentData(33*3*2*1) // Get 1 sec worth of data
                        if (sensorData.isNotEmpty()) {

                            sensorData?.let {
                                val sensorDataToSendList = it.map { sensorData ->
                                    when (sensorData.sensor) {
                                        "android.sensor.accelerometer" -> {
                                            mapOf(
                                                "test_id" to -1,
                                                "timestamp" to sensorData.timestamp,
                                                "values" to mapOf(
                                                    "accel_x" to sensorData.measures[0],
                                                    "accel_y" to sensorData.measures[1],
                                                    "accel_z" to sensorData.measures[2]
                                                )
                                            )
                                        }
                                        "android.sensor.gyroscope" -> {
                                            mapOf(
                                                "test_id" to -1,
                                                "timestamp" to sensorData.timestamp,
                                                "values" to mapOf(
                                                    "gyro_x" to sensorData.measures[0],
                                                    "gyro_y" to sensorData.measures[1],
                                                    "gyro_z" to sensorData.measures[2]
                                                )
                                            )
                                        }
                                        "battery" -> {
                                            mapOf(
                                                "test_id" to -1,
                                                "timestamp" to sensorData.timestamp,
                                                "values" to mapOf(
                                                    "battery" to sensorData.measures[0]
                                                )
                                            )
                                        }
                                        "android.sensor.heart_rate" -> {
                                            mapOf(
                                                "test_id" to -1,
                                                "timestamp" to sensorData.timestamp,
                                                "values" to mapOf(
                                                    "heart_rate" to sensorData.measures[0]
                                                )
                                            )
                                        }
                                        else -> throw IllegalArgumentException("Unsupported sensor type: ${sensorData.sensor}")
                                    }
                                }

                                Log.d("MqttPublisher", "message: $sensorDataToSendList")

                                val message = MqttMessage(gson.toJson(sensorDataToSendList).toByteArray())

                                try {
                                    if (mqttManager.publish("mealtracker-raw-measures-array", message.toString())) {
                                        // Mark as sent
                                        sensorData.forEach { sensorData ->
                                            sensorDataDao.markAsSent(sensorData.id)
                                        }
                                    } else {
                                        Log.e("MqttPublisher", "Failed to get or send sensor data")
                                    }
                                } catch (e: Exception) {
                                    Log.e("MqttPublisher", "Failed to get or send sensor data", e)
                                }
                            }
                        }


                    } else {
                        // Sleep for a while if there is no unsent data
                        Thread.sleep(10000)
                    }
                }
            } catch (e: Exception) {
                Log.e("MqttPublisher", "Failed to get or send sensor data", e)
            }
        }
    }


}
