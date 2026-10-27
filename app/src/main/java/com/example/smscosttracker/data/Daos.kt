package com.example.smscosttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(messages: List<RawSmsEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM raw_sms")
    fun count(): Int

    @Query("SELECT * FROM raw_sms")
    fun getAll(): List<RawSmsEntity>

    @Query("SELECT * FROM raw_sms WHERE address = :address AND body = :body LIMIT 1")
    fun find(address: String, body: String): RawSmsEntity?

    @Query(
        "SELECT * FROM raw_sms WHERE address = :address AND date BETWEEN :from AND :to " +
            "ORDER BY ABS(date - :anchor) LIMIT 20"
    )
    fun messagesFor(address: String, from: Long, to: Long, anchor: Long): List<RawSmsEntity>
}

@Dao
interface TransactionDao {
    @Insert
    fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions")
    fun getAll(): List<TransactionEntity>

    @Query("UPDATE transactions SET category = :category, categoryConfidence = 1.0 WHERE id = :id")
    fun updateCategory(id: Long, category: String)

    @Query("UPDATE transactions SET category = :category WHERE merchant = :merchant")
    fun updateCategoryByMerchant(merchant: String, category: String)

    @Query("UPDATE transactions SET account = :account, channel = :channel WHERE id = :id")
    fun updateInstrument(id: Long, account: String, channel: String)

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Int

    @Query("DELETE FROM transactions")
    fun deleteAll()
}

@Dao
interface CategoryCorrectionDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(correction: CategoryCorrectionEntity)

    @Query("SELECT * FROM category_corrections")
    fun getAll(): List<CategoryCorrectionEntity>
}

data class LabelledSms(
    val rawSmsId: Long,
    val type: String,
    val merchant: String?,
    val amount: String?,
    val instrument: String?,
    val updatedAt: Long,
    val address: String,
    val body: String
)

@Dao
interface MessageLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(label: MessageLabelEntity)

    @Query("SELECT * FROM message_labels WHERE rawSmsId = :rawSmsId")
    fun get(rawSmsId: Long): MessageLabelEntity?

    @Query("DELETE FROM message_labels WHERE rawSmsId = :rawSmsId")
    fun delete(rawSmsId: Long)

    @Query("SELECT COUNT(*) FROM message_labels")
    fun count(): Int

    @Query("SELECT MAX(updatedAt) FROM message_labels")
    fun lastUpdatedAt(): Long?

    @Query(
        "SELECT l.rawSmsId, l.type, l.merchant, l.amount, l.instrument, l.updatedAt, " +
            "r.address, r.body FROM message_labels l JOIN raw_sms r ON r.id = l.rawSmsId " +
            "ORDER BY l.updatedAt"
    )
    fun getAllWithMessages(): List<LabelledSms>
}

@Dao
interface InstrumentDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsertAll(instruments: List<InstrumentEntity>)

    @Query("SELECT * FROM instruments")
    fun getAll(): List<InstrumentEntity>
}
