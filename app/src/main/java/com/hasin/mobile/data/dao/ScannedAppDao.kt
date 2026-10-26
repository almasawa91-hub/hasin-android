package com.hasin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasin.mobile.data.entity.ScannedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedAppDao {

    @Query("SELECT * FROM scanned_apps ORDER BY threatScore DESC")
    fun getAllAppsByRisk(): Flow<List<ScannedAppEntity>>

    @Query("SELECT * FROM scanned_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): ScannedAppEntity?

    @Query("SELECT COUNT(*) FROM scanned_apps")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT AVG(threatScore) FROM scanned_apps")
    fun getAverageScore(): Flow<Double>

    @Query("SELECT COUNT(*) FROM scanned_apps WHERE threatScore >= 60")
    fun getHighRiskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scanned_apps WHERE threatScore >= 30 AND threatScore < 60")
    fun getMediumRiskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scanned_apps WHERE threatScore < 30")
    fun getLowRiskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scanned_apps WHERE scoreTrend = 1")
    fun getWorsenedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: ScannedAppEntity)

    @Query("DELETE FROM scanned_apps")
    suspend fun clearAll()
}
