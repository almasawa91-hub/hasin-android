package com.hasin.mobile.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_apps")
data class ScannedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val threatScore: Int,
    val highRiskPermissions: String, // JSON array
    val mediumRiskPermissions: String, // JSON array
    val totalPermissions: Int,
    val lastScannedAt: Long,
    val previousScore: Int? = null, // لتتبع التغير
    val scoreTrend: Int = 0 // -1: انخفض، 0: ثابت، 1: زاد
)
