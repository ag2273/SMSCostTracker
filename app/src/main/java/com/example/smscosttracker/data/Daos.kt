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

    @Query(
        "SELECT * FROM raw_sms WHERE address = :address AND date BETWEEN :from AND :to " +
            "ORDER BY date DESC LIMIT 20"
    )
    fun messagesFor(address: String, from: Long, to: Long): List<RawSmsEntity>
}

@Dao
interface TransactionDao {
    @Insert
    fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions")
    fun getAll(): List<TransactionEntity>

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    fun updateCategory(id: Long, category: String)

    @Query("UPDATE transactions SET category = :category WHERE merchant = :merchant")
    fun updateCategoryByMerchant(merchant: String, category: String)

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Int
}

@Dao
interface CategoryCorrectionDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(correction: CategoryCorrectionEntity)

    @Query("SELECT * FROM category_corrections")
    fun getAll(): List<CategoryCorrectionEntity>
}
