// SensorDataDao.kt

package com.example.pixelv2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDataDao {

    @Insert
    fun insert(sensorData: SensorData)

    @Query("DELETE FROM sensor_data")
    fun deleteAll()

    @Query("UPDATE sensor_data SET sent = 1 WHERE id = :id")
    fun markAsSent(id: Int): Int

    @Query("SELECT * FROM sensor_data WHERE sent = 0 ORDER BY timestamp DESC LIMIT 1")
    fun getLastUnsentData(): SensorData?

    @Query("SELECT COUNT(*) FROM sensor_data WHERE sent = 0")
    fun getCountNotSent(): Int

    @Query("SELECT COUNT(*) FROM sensor_data WHERE sent = 1")
    fun getCountSent(): Int

    @Query("SELECT * FROM sensor_data WHERE sensor IN (:sensor1, :sensor2) AND timestamp BETWEEN :startTime AND :endTime")
    fun getSensorDataInRange(sensor1: String, sensor2: String, startTime: Long, endTime: Long): List<SensorData>

    @Query("SELECT * FROM sensor_data WHERE timestamp BETWEEN :startTime AND :endTime")
    fun getBatchUnsentDataDate( startTime: Long, endTime: Long): List<SensorData>

    @Query("SELECT * FROM sensor_data WHERE sent = 0 LIMIT :range")
    fun getBatchUnsentData( range: Int): List<SensorData>


    @Query("SELECT MAX(timestamp) FROM sensor_data WHERE sensor = :sensorName AND sent = 0" )
    fun getLastTimestamp(sensorName: String): Long

    @Insert()
    fun insertAll(sensorData: List<SensorData>)


}
