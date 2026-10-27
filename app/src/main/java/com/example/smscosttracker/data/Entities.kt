package com.example.smscosttracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_sms",
    indices = [Index(value = ["address", "body", "date", "threadId"], unique = true)]
)
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
    val channel: String = "",
    /** Classifier probability for [category]; 1.0 after a correction, -1.0 when unknown. */
    val categoryConfidence: Double = -1.0
)

@Entity(tableName = "category_corrections")
data class CategoryCorrectionEntity(
    @PrimaryKey val merchantKey: String,
    val category: String
)

@Entity(tableName = "message_labels")
data class MessageLabelEntity(
    @PrimaryKey val rawSmsId: Long,
    val type: String,
    val merchant: String? = null,
    val amount: String? = null,
    val instrument: String? = null,
    val updatedAt: Long
)

@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    @PrimaryKey val aliasKey: String,
    val merchant: String,
    val updatedAt: Long
)

@Entity(tableName = "instruments", primaryKeys = ["address", "digits"])
data class InstrumentEntity(
    val address: String,
    val digits: String,
    val channel: String,
    val label: String = "",
    val ignored: Boolean = false
)
