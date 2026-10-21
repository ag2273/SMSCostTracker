package com.example.smscosttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RawSmsEntity::class, TransactionEntity::class, CategoryCorrectionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryCorrectionDao(): CategoryCorrectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_corrections` " +
                        "(`merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "PRIMARY KEY(`merchantKey`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `account` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `channel` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_cost_tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                    .also { instance = it }
            }
    }
}
