package com.hasin.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hasin.mobile.data.dao.NetworkConnectionDao
import com.hasin.mobile.data.dao.ScannedAppDao
import com.hasin.mobile.data.entity.NetworkConnectionEntity
import com.hasin.mobile.data.entity.ScannedAppEntity

@Database(
    entities = [ScannedAppEntity::class, NetworkConnectionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class HasinDatabase : RoomDatabase() {

    abstract fun scannedAppDao(): ScannedAppDao
    abstract fun networkConnectionDao(): NetworkConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: HasinDatabase? = null

        fun getInstance(context: Context): HasinDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HasinDatabase::class.java,
                    "hasin.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
