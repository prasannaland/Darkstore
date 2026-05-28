package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Product::class, WarehouseOrder::class], version = 1, exportSchema = false)
abstract class DarkStoreDatabase : RoomDatabase() {
    abstract fun darkStoreDao(): DarkStoreDao

    companion object {
        @Volatile
        private var INSTANCE: DarkStoreDatabase? = null

        fun getDatabase(context: Context): DarkStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DarkStoreDatabase::class.java,
                    "dark_store_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
