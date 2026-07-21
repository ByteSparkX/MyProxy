package com.myproxy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myproxy.app.model.ProxyNode

@Database(
    entities = [ProxyNode::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    // 使用 applicationContext，避免持有 Activity 导致内存泄漏。
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myproxy.db",
                ).build()
                    .also { instance = it }
            }
        }
    }
}
