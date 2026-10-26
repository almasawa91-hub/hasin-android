package com.hasin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hasin.mobile.data.entity.NetworkConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkConnectionDao {
    @Query("SELECT * FROM network_connections ORDER BY timestamp DESC LIMIT 100")
    fun getRecentConnections(): Flow<List<NetworkConnectionEntity>>

    @Insert
    suspend fun insertConnection(connection: NetworkConnectionEntity)
    
    @Query("DELETE FROM network_connections")
    suspend fun clearAll()
}
