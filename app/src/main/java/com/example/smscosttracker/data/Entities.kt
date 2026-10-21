package com.example.smscosttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_sms")
data class RawSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long,
    val threadId: Long
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val occurredAt: Long,
    val sourceAddress: String,
    val category: String,
    val account: String = "",
    val channel: String = ""
)

@Entity(tableName = "category_corrections")
data class CategoryCorrectionEntity(
    @PrimaryKey val merchantKey: String,
    val category: String
)

@Entity(tableName = "instruments", primaryKeys = ["address", "digits"])
data class InstrumentEntity(
    val address: String,
    val digits: String,
    val channel: String,
    val label: String = "",
    val ignored: Boolean = false
)
