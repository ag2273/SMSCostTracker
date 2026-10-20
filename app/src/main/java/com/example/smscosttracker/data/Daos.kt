package com.example.smscosttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RawSmsDao {
    @Insert
    fun insertAll(messages: List<RawSmsEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM raw_sms")
    fun count(): Int
}

@Dao
interface TransactionDao {
    @Insert
    fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions")
    fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Int
}
