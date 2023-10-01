// SensorData.kt

package com.example.pixelv2

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val client_id: Int,
    val timestamp: Long,
    val sensor: String,
    @TypeConverters(Converters::class) val measures: List<Float>,
    var sent: Boolean = false
)