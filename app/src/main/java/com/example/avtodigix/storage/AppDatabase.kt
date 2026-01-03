package com.example.avtodigix.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ScanSnapshotEntity::class, WifiScanSnapshotEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(ScanSnapshotConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSnapshotDao(): ScanSnapshotDao
    abstract fun wifiScanSnapshotDao(): WifiScanSnapshotDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wifi_scan_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        responseFormat TEXT NOT NULL,
                        keyMetrics TEXT NOT NULL,
                        dtcList TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "avtodigix.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
