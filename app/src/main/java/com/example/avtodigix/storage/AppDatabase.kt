package com.example.avtodigix.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ScanSnapshotEntity::class, WifiScanSnapshotEntity::class, DailyCheckSessionEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(ScanSnapshotConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSnapshotDao(): ScanSnapshotDao
    abstract fun wifiScanSnapshotDao(): WifiScanSnapshotDao
    abstract fun dailyCheckSessionDao(): DailyCheckSessionDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
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

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_check_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        vehicleId TEXT NOT NULL,
                        startedAtMillis INTEGER NOT NULL,
                        finishedAtMillis INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        trafficLightStatus TEXT NOT NULL,
                        keyMetrics TEXT NOT NULL,
                        rawDtcList TEXT NOT NULL,
                        hasNewDtc INTEGER NOT NULL,
                        scannerId TEXT,
                        vin TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_daily_check_sessions_vehicleId_finishedAtMillis
                    ON daily_check_sessions(vehicleId, finishedAtMillis)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_daily_check_sessions_vehicleId_success_finishedAtMillis
                    ON daily_check_sessions(vehicleId, success, finishedAtMillis)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
