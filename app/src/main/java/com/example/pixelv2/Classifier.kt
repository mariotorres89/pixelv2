package com.example.pixelv2


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import android.content.res.AssetManager
import android.util.Log

import com.github.psambit9791.jdsp.filter.Butterworth
import com.github.psambit9791.jdsp.filter.Median
import org.jtransforms.fft.DoubleFFT_1D
import org.nield.kotlinstatistics.median
import org.nield.kotlinstatistics.standardDeviation

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class Classifier(private val sensorDataDao: SensorDataDao, private val assetManager: AssetManager) {

    private val tensorModel = TensorModel(assetManager, "nn_100epch_64bs.tflite")

    suspend fun computeFeatures() {
        println("Comenzando computeFeatures:")
        val end = sensorDataDao.getLastTimestamp("android.sensor.accelerometer")
        val start = end - TimeUnit.SECONDS.toMillis(15)

        println("Query start: $start")
        println("Query end: $end")

        println("Comenzando query:")

        val sensorData = withContext(Dispatchers.IO) {
            sensorDataDao.getSensorDataInRange("android.sensor.accelerometer", "android.sensor.gyroscope", start, end)
        }
        getFeatures(sensorData)
    }

    suspend fun computeFeaturesList(sensorData: List<SensorData>) {

        getFeatures(sensorData)
    }

    private fun computeAndLogMedian(sensorData: List<SensorData>) {

        val accelerometerX = sensorData.filter { it.sensor == "android.sensor.accelerometer" }.map { it.measures[0] }
        val accelerometerY = sensorData.filter { it.sensor == "android.sensor.accelerometer" }.map { it.measures[1] }
        val accelerometerZ = sensorData.filter { it.sensor == "android.sensor.accelerometer" }.map { it.measures[2] }

        val gyroscopeX = sensorData.filter { it.sensor == "android.sensor.gyroscope" }.map { it.measures[0] }
        val gyroscopeY = sensorData.filter { it.sensor == "android.sensor.gyroscope" }.map { it.measures[1] }
        val gyroscopeZ = sensorData.filter { it.sensor == "android.sensor.gyroscope" }.map { it.measures[2] }

        val medianAccelerometerX = accelerometerX.average().toFloat()
        val medianAccelerometerY = accelerometerY.average().toFloat()
        val medianAccelerometerZ = accelerometerZ.average().toFloat()

        val medianGyroscopeX = gyroscopeX.average().toFloat()
        val medianGyroscopeY = gyroscopeY.average().toFloat()
        val medianGyroscopeZ = gyroscopeZ.average().toFloat()

        val features = listOf(
            medianAccelerometerX,
            medianAccelerometerY,
            medianAccelerometerZ,
            medianGyroscopeX,
            medianGyroscopeY,
            medianGyroscopeZ,
            medianGyroscopeZ,
            medianGyroscopeZ,
        )
//        println("Median values: $features")
        Log.d("Classifier", "Median values: $features")

        val output = tensorModel.classify(features)
        val outputArray = floatArrayOf(output[0],output[0])
        val index = output.indices.maxByOrNull { outputArray[it] } ?: -1
        val predictedClass = if (index == 1) 1 else 0
        Log.d("Classifier", "Model Output: ${output[0]}, ${output[1]}, $predictedClass")

        // Log the median and prediction.
//        println("Model Output: ${output[0]}, ${output[1]}, $predictedClass")

    }

    companion object {
        private const val TAG = "Features"
    }
    private fun getFeatures(data: List<SensorData>) {

        /* Function that returns a DoubleArray with the next features of a data sample
        * of the accelerometer and gyroscope x, y, z components
        * [sdNormaAcc_mf, psd_acySkew, medianAcx_bp, afz2, abmeanGyz, meanAcx]
        */

        // Accelerometer features
        val accData = data.filter { it.sensor.endsWith("accelerometer") }

        // Se obtienen los arrays con los datos de cada eje del acelerometro
        val acx = accData.map { it.measures[0].toDouble() }.toDoubleArray()
        val acy = accData.map { it.measures[1].toDouble() }.toDoubleArray()
        val acz = accData.map { it.measures[2].toDouble() }.toDoubleArray()

        // sdNormaAcc_mf
        val mfAcxSignal = medianFilter(acx)
        val mfAcySignal = medianFilter(acy)
        val mfAczSignal = medianFilter(acz)
        val mfAccSignal = Array(mfAcxSignal.size) { i ->
            doubleArrayOf(mfAcxSignal[i], mfAcySignal[i], mfAczSignal[i])
        }
        val normMfAccSignal = norm(mfAccSignal)
        val sdNormaAccMf = normMfAccSignal.standardDeviation()

        // psd_acySkew
        val (frequencies, PSD) = calculatePSD(acy)
        val psdAcySkew = calculateSkewness(PSD)

        // medianAcx_bp
        val bpAcxSignal = bandPassFilter(medianFilter(acx))
        val medianAcxBp = bpAcxSignal.median()

        // meanAcx
        val meanAcx = acx.average()

        // afz2
        val afz2 = findSecondFrequencyPeak(acz)

        // Gyroscope features
        val gyData = data.filter { it.sensor.endsWith("gyroscope") }

        // Se obtiene el array de datos del eje z del giroscopio
        val gyz = gyData.map { it.measures[2] }

        // abmeanGyz
        val abGyz = gyz.map { abs(it) }
        val abMeanGyz = abGyz.average()

        Log.d(TAG, "$sdNormaAccMf, $psdAcySkew, $medianAcxBp, $afz2, $abMeanGyz, $meanAcx")
        val features = listOf(sdNormaAccMf, psdAcySkew, medianAcxBp, afz2, abMeanGyz, meanAcx)
        val featuresFloat = features.map { it.toFloat() }
        val output = tensorModel.classify(featuresFloat)
        val outputArray = floatArrayOf(output[0],output[0])
        val index = output.indices.maxByOrNull { outputArray[it] } ?: -1
        val predictedClass = if (index == 1) 1 else 0
        Log.d("Classifier", "Model Output: ${output[0]}, ${output[1]}, $predictedClass")

        // Broker and client details
        val brokerUrl = "tcp://cpshealthcare.cl:3883"
        val clientId = "pixel-watch-client-classifier-1"
        val mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

        val options = MqttConnectOptions().apply {
            userName = "mqttUser"
            password = "mosquitto306".toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
        }

        try {
            mqttClient.connect(options)
        } catch (e: MqttException) {
            Log.e(TAG, "Error connecting to MQTT broker", e)
            return // If we can't connect, exit the function early
        }

        // Construct the JSON object
        val currentTime = System.currentTimeMillis() // Epoch time in milliseconds

        val featuresJson = JSONObject().apply {
            put("sdNormaAccMf", sdNormaAccMf)
            put("psdAcySkew", psdAcySkew)
            put("medianAcxBp", medianAcxBp)
            put("afz2", afz2)
            put("abMeanGyz", abMeanGyz)
            put("meanAcx", meanAcx)
        }

        val mainJson = JSONObject().apply {
            put("test_id", 1)
            put("timestamp", currentTime)
            put("values", JSONObject().apply {
                put("features", featuresJson)
                put("prediction", predictedClass)
            })
        }

        // Publish over MQTT
        try {
            if (mqttClient.isConnected) {
                mqttClient.publish(
                    "mealtracker-results",
                    MqttMessage(mainJson.toString().toByteArray())
                )
            } else {
                Log.e(TAG, "MQTT client is not connected")
            }
        } catch (e: MqttException) {
            Log.e(TAG, "Error sending MQTT message", e)
        } finally {
            // Disconnect from the MQTT broker once done
            try {
                mqttClient.disconnect()
            } catch (e: MqttException) {
                Log.e(TAG, "Error disconnecting from MQTT broker", e)
            }
        }

    }
    private fun calculatePSD(signal: DoubleArray): Pair<DoubleArray, DoubleArray> {
        /*
        * Esta función retorna la densidad espectral de potencia de la señal (real)
        * y los bins de frecuencia, de la misma manera como lo calcula scipy.signal.periodogram
        */
        val mean = signal.average()
        val zeroMeanSignal = signal.map { it - mean }.toDoubleArray()

        val n = zeroMeanSignal.size
        val samplingFrequency = n/15
        // Calculate the FFT
        val fft = DoubleFFT_1D(n.toLong())
        fft.realForward(zeroMeanSignal)
        val fftResult = zeroMeanSignal.map { abs(it) }
        // Calculate the squared magnitudes of the DFT result
        val nOneSide: Int
        val psd: DoubleArray
        // Caso n par
        if (n%2 == 0) {
            nOneSide = n/2 + 1
            psd = DoubleArray(nOneSide)
            for (i in 0 until nOneSide) {
                if (i == 0) {
                    psd[0] = fftResult[0].pow(2) / (samplingFrequency * n)
                }
                else if (i == nOneSide-1) {
                    psd[nOneSide-1] = fftResult[1].pow(2)/(samplingFrequency*n)
                }
                else {
                    val real = fftResult[2*i]
                    val img = fftResult[2*i+1]
                    psd[i] = 2*(real.pow(2)+img.pow(2))/(samplingFrequency*n)
                }
            }
        }
        // Caso n impar
        else {
            nOneSide = (n-1)/2 + 1
            psd = DoubleArray(nOneSide)
            for (i in 0 until nOneSide) {
                if (i == 0) {
                    psd[0] = fftResult[0].pow(2) / (samplingFrequency * n)
                }
                else if (i == nOneSide-1) {
                    psd[nOneSide-1] = (fftResult[1].pow(2) + fftResult[i].pow(2))/(samplingFrequency*n)
                }
                else {
                    val real = fftResult[2*i]
                    val img = fftResult[2*i+1]
                    psd[i] = 2*(real.pow(2)+img.pow(2))/(samplingFrequency*n)
                }
            }
        }
        val df = samplingFrequency.toDouble()/n.toDouble()
        val frequencies = (0 until nOneSide).map { it.toDouble() * df}.toDoubleArray()
        return frequencies to psd
    }
    private fun findSecondFrequencyPeak(signal: DoubleArray): Double {
        /*
        * Esta función retorna la frecuencia en donde ocurre
        * el segundo peak de la densidad espectral de potencia de la señal
        */
        val (frequencies, PSD) = calculatePSD(signal)
        val secondFreqPeak = frequencies[findSecondMaxIndex(PSD)]
        return secondFreqPeak
    }
    private fun medianFilter(signal: DoubleArray): DoubleArray {
        /*
        * Esta función aplica un median filter a la señal
        */
        val filter = Median(5)
        return filter.filter(signal)
    }
    private fun bandPassFilter(signal: DoubleArray): DoubleArray {
        /*
        * Esta función filtra la señal con un filtro pasabanda entre 5-10 Hz
        */
        val fs = signal.size/15     // Se dvide en 15 porque la señal se muestreó durante 15s
        val lowCutOff = 5.0
        val highCutOff = 10.0
        val filter = Butterworth(fs.toDouble())
        return filter.bandPassFilter(signal, 4, lowCutOff, highCutOff)
    }
    private fun norm(arrayVector: Array<DoubleArray>): DoubleArray {
        /*
        * Esta función retorna un array con las normas de los vectores
        * del array de vectores entregados
        */
        val n = arrayVector.size
        val normArray = DoubleArray(n)
        for (i in 0 until n) {
            normArray[i] = sqrt(arrayVector[i].sumOf { it * it })
        }
        return normArray
    }
    private fun calculateSkewness(data: DoubleArray): Double {
        /*
        * Esta función retorna el Skweness de la señal
        */
        val n = data.size.toDouble()
        val mean = data.sum() / n
        val sumCubedDeviations = data.sumOf { (it - mean).pow(3) } / n
        val variance = data.sumOf { (it - mean).pow(2) } / n
        val skewness = sumCubedDeviations / sqrt(variance).pow(3)
        return skewness
    }
    private fun findSecondMaxIndex(array: DoubleArray): Int {
        /*
        * Esta función retorna el índice del array en donde se encuentra
        * el segundo mayor valor
        */
        var max = Double.MIN_VALUE
        var secondMax = Double.MIN_VALUE
        var maxIndex = -1
        var secondMaxIndex = -1
        for (i in array.indices) {
            val num = array[i]
            if (num > max) {
                secondMax = max
                secondMaxIndex = maxIndex
                max = num
                maxIndex = i
            } else if (num > secondMax && num < max) {
                secondMax = num
                secondMaxIndex = i
            }
        }
        return secondMaxIndex
    }
}
