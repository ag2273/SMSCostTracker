package com.example.smscosttracker.data

import com.example.smscosttracker.classification.CategoryClassifier
import com.example.smscosttracker.parsing.Deduplicator
import com.example.smscosttracker.parsing.TransactionParser
import java.util.concurrent.Callable

data class ImportResult(
    val rawInserted: Int,
    val parsed: Int,
    val stored: Int
)

class TransactionImporter(
    private val database: AppDatabase,
    private val parser: TransactionParser = TransactionParser
) {
    fun import(messages: List<RawSms>): ImportResult =
        database.runInTransaction(Callable {
            val rawInserted = database.rawSmsDao().insertAll(
                messages.map {
                    RawSmsEntity(
                        address = it.address,
                        body = it.body,
                        date = it.date,
                        threadId = it.threadId
                    )
                }
            ).size

            val parsed = messages.mapNotNull { parser.parse(it) }
            ImportResult(
                rawInserted = rawInserted,
                parsed = parsed.size,
                stored = store(parsed)
            )
        })

    fun rebuildFromStored(): ImportResult =
        database.runInTransaction(Callable {
            val stored = database.rawSmsDao().getAll()
            database.transactionDao().deleteAll()
            val parsed = stored.mapNotNull {
                parser.parse(
                    RawSms(
                        address = it.address,
                        body = it.body,
                        date = it.date,
                        threadId = it.threadId
                    )
                )
            }
            ImportResult(
                rawInserted = stored.size,
                parsed = parsed.size,
                stored = store(parsed)
            )
        })

    private fun store(parsed: List<ParsedTransaction>): Int {
        val existing = database.transactionDao().getAll()
        val accepted = Deduplicator.dedupe(parsed, existing)
        val classifier = CategoryClassifier(
            corrections = database.categoryCorrectionDao().getAll()
                .associate { it.merchantKey to it.category }
        )
        accepted.forEach { candidate ->
            database.transactionDao().insert(
                TransactionEntity(
                    amountMinor = candidate.amountMinor,
                    currency = candidate.currency,
                    merchant = candidate.merchant,
                    occurredAt = candidate.occurredAt,
                    sourceAddress = candidate.sourceAddress,
                    category = classifier.classify(candidate.merchant).displayName
                )
            )
        }
        return accepted.size
    }
}
