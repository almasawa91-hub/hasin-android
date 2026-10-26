package com.hasin.mobile.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_connections")
data class NetworkConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destIp: String,
    val destPort: Int,
    val protocol: String,
    val timestamp: Long,
    val isSuspicious: Boolean,
    val reason: String
)
