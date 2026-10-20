package com.example.smscosttracker.data

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.classification.CategoryClassifier

class TransactionRepository(private val database: AppDatabase) {
    fun transactions(): List<TransactionEntity> = database.transactionDao().getAll()

    fun count(): Int = database.transactionDao().count()

    fun updateCategory(transaction: TransactionEntity, category: Category) {
        database.transactionDao().updateCategoryByMerchant(transaction.merchant, category.displayName)
        database.categoryCorrectionDao().upsert(
            CategoryCorrectionEntity(
                merchantKey = CategoryClassifier.normalizeMerchant(transaction.merchant),
                category = category.displayName
            )
        )
    }
}
