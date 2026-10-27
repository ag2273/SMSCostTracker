package com.example.smscosttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RawSmsEntity::class,
        TransactionEntity::class,
        CategoryCorrectionEntity::class,
        InstrumentEntity::class,
        MessageLabelEntity::class,
        MerchantAliasEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryCorrectionDao(): CategoryCorrectionDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun messageLabelDao(): MessageLabelDao
    abstract fun merchantAliasDao(): MerchantAliasDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `instruments` " +
                        "(`address` TEXT NOT NULL, `digits` TEXT NOT NULL, " +
                        "`channel` TEXT NOT NULL, `label` TEXT NOT NULL DEFAULT '', " +
                        "PRIMARY KEY(`address`, `digits`))"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `instruments` ADD COLUMN `ignored` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "DELETE FROM raw_sms WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM raw_sms GROUP BY address, body, date, threadId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_raw_sms_address_body_date_threadId " +
                        "ON raw_sms (address, body, date, threadId)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_labels` " +
                        "(`rawSmsId` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`merchant` TEXT, `amount` TEXT, `instrument` TEXT, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`rawSmsId`))"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `categoryConfidence` REAL NOT NULL DEFAULT -1.0"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `merchant_aliases` " +
                        "(`aliasKey` TEXT NOT NULL, `merchant` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`aliasKey`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_cost_tracker.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                ).build().also { instance = it }
            }
    }
}
