// MqttManager.kt

package com.example.pixelv2

import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence


class MqttManager {

    private val brokerUrl = "tcp://cpshealthcare.cl:3883"
    private val clientId = "pixel-watch-client-sensors-1"
    private val mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
    val options = MqttConnectOptions().apply {
        userName = "mqttUser"
        password = "mosquitto306".toCharArray()

    }
    init {

        mqttClient.connect(options)

    }

    fun connect() {
        if (!mqttClient.isConnected) {
            try {
                mqttClient.connect(options)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        if (mqttClient.isConnected) {
            try {
                mqttClient.disconnect()
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    fun publish(topic: String, message: String): Boolean {
        return try {
            mqttClient.publish(topic, MqttMessage(message.toByteArray()))
            true
        } catch (e: MqttException) {
            e.printStackTrace()
            false
        }
    }
}
