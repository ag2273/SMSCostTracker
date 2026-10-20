package com.example.smscosttracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class RawSms(
    val address: String,
    val body: String,
    val date: Long,
    val threadId: Long
)

class RawSmsStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE raw_sms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                address TEXT NOT NULL,
                body TEXT NOT NULL,
                date INTEGER NOT NULL,
                thread_id INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertAll(messages: List<RawSms>): Int {
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            for (message in messages) {
                val values = ContentValues().apply {
                    put("address", message.address)
                    put("body", message.body)
                    put("date", message.date)
                    put("thread_id", message.threadId)
                }
                if (db.insert("raw_sms", null, values) != -1L) {
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM raw_sms", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    companion object {
        private const val DATABASE_NAME = "sms_cost_tracker.db"
        private const val DATABASE_VERSION = 1
    }
}
