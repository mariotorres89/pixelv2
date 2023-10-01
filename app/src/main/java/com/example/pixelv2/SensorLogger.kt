package com.example.pixelv2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttMessage

class SensorLogger(
    private val context: Context,
    private val sensorDataDao: SensorDataDao,
    private val mqttManager: MqttManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val heartrate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val sensorDataBuffer = mutableListOf<SensorData>()
    private val gson = Gson()
    private val classifier = Classifier(sensorDataDao, context.assets)

    fun startLogging() {
        Log.d("SensorLogger", "Start Logging....")
        sensorManager.registerListener(this, accelerometer, 33333) // 30 Hz
        sensorManager.registerListener(this, gyroscope, 33333) // 30 Hz
        sensorManager.registerListener(this, heartrate, 1000000) // 1 Hz
        logBatteryLevel()
    }

    fun stopLogging() {
        Log.d("SensorLogger", "Stopping Mqtt....")
        mqttManager.disconnect()
        Log.d("SensorLogger", "Stopping Logging....")
        sensorManager.unregisterListener(this)
        GlobalScope.launch {
            try {
                Log.d("SensorLogger", "Running Classifier....")
                classifier.computeFeaturesList(sensorDataBuffer)
                Log.d("SensorLogger", "Classifier Completed....")

                Log.d("SensorLogger", "Inserting Buffer....")
                sensorDataDao.insertAll(sensorDataBuffer)
                Log.d("SensorLogger", "Buffer Inserted")


                sensorDataBuffer.clear()
//                val sentDataCount = sensorDataDao.getCountSent()
//                Log.d("SensorLogger", "Data Sent: $sentDataCount")
            } catch (e: Exception) {
                Log.e("SensorLogger", "Failed to insert sensor data", e)
            }
        }

    }

    override fun onSensorChanged(event: SensorEvent) {
        // Create an instance of SensorDataMqtt
        val sensorDataToSend = SensorDataMqtt(
            timestamp = System.currentTimeMillis(),
            sensor = event.sensor.stringType,
            client_id = 1, // replace with your client id
            measures = event.values.toList()
        )
//        Log.d("SensorLogger", "enviando dato por mqtt")
        val message = MqttMessage(gson.toJson(sensorDataToSend).toByteArray())
//        val mqttSent = mqttManager.publish("test-topic-1", message.toString())
        val mqttSent = false
//        Log.d("SensorLogger", "Dato : $sensorDataToSend")

        // Create a sensor data object with 'sent' field based on MQTT publishing success
        val sensorData = SensorData(
            id = 0,
            timestamp = sensorDataToSend.timestamp,
            sensor = sensorDataToSend.sensor,
            client_id = sensorDataToSend.client_id,
            measures = sensorDataToSend.measures,
            sent = mqttSent
        )

        sensorDataBuffer.add(sensorData)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle sensor accuracy changes
    }

    private fun logBatteryLevel() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val sensorData = SensorData(
            id = 0, // Room will auto-generate this
            client_id = 1, // Replace with your actual client ID
            timestamp = System.currentTimeMillis(),
            sensor = "battery",
            measures = listOf(batteryLevel.toFloat()),
            sent = false
        )
        sensorDataBuffer.add(sensorData)
    }
}
