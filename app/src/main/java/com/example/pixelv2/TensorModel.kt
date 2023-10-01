package com.example.pixelv2

import org.tensorflow.lite.Interpreter
import android.content.res.AssetManager
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

import java.nio.ByteBuffer

class TensorModel (assetManager: AssetManager, modelPath: String){
    private var tflite: Interpreter

    init {
        val options = Interpreter.Options()
        tflite = Interpreter(loadModelFile(assetManager, modelPath), options)
    }

    fun classify(features: List<Float>): FloatArray {
        val inputBuffer: ByteBuffer = convertBitmapToByteBuffer(features)
        val output = Array(1) { FloatArray(2) }  // Modify the output array shape to [1, 2]
        tflite.run(inputBuffer, output)
        return output[0]
    }

    fun close() {
        tflite.close()
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(features: List<Float>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * features.size)
        byteBuffer.order(java.nio.ByteOrder.nativeOrder())
        for (feature in features) {
            byteBuffer.putFloat(feature)
        }
        return byteBuffer
    }
}
