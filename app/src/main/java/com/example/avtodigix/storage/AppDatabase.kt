package com.example.avtodigix.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ScanSnapshotEntity::class], version = 1, exportSchema = true)
@TypeConverters(ScanSnapshotConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSnapshotDao(): ScanSnapshotDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "avtodigix.db"
            ).build()
        }
    }
}
