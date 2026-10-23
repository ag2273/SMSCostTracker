package com.example.smscosttracker.data

import com.example.smscosttracker.classification.CategoryClassifier
import com.example.smscosttracker.parsing.Deduplicator
import com.example.smscosttracker.parsing.ParseOutcome
import com.example.smscosttracker.parsing.TransactionParser
import java.util.concurrent.Callable

data class ImportProgress(
    val processed: Int,
    val total: Int,
    val parsed: Int,
    val skipped: Int,
    val unparsed: Int,
    val currentAddress: String = "",
    val currentBody: String = ""
)

data class ImportResult(
    val rawInserted: Int,
    val parsed: Int,
    val stored: Int,
    val skipped: Int = 0,
    val unparsed: Int = 0
)

private data class ParsedBatch(
    val transactions: List<ParsedTransaction>,
    val skipped: Int,
    val unparsed: Int
)

class TransactionImporter(
    private val database: AppDatabase,
    private val parser: TransactionParser = TransactionParser
) {
    fun import(
        messages: List<RawSms>,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult =
        database.runInTransaction(Callable {
            val rawBefore = database.rawSmsDao().count()
            database.rawSmsDao().insertAll(
                messages.map {
                    RawSmsEntity(
                        address = it.address,
                        body = it.body,
                        date = it.date,
                        threadId = it.threadId
                    )
                }
            )
            val rawInserted = database.rawSmsDao().count() - rawBefore

            val parsed = parseAll(messages, onProgress)
            ImportResult(
                rawInserted = rawInserted,
                parsed = parsed.transactions.size,
                stored = store(parsed.transactions),
                skipped = parsed.skipped,
                unparsed = parsed.unparsed
            )
        })

    fun rebuildFromStored(
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult =
        database.runInTransaction(Callable {
            val stored = database.rawSmsDao().getAll()
            database.transactionDao().deleteAll()
            val messages = stored.map {
                RawSms(
                    address = it.address,
                    body = it.body,
                    date = it.date,
                    threadId = it.threadId
                )
            }
            val parsed = parseAll(messages, onProgress)
            ImportResult(
                rawInserted = stored.size,
                parsed = parsed.transactions.size,
                stored = store(parsed.transactions),
                skipped = parsed.skipped,
                unparsed = parsed.unparsed
            )
        })

    private fun parseAll(
        messages: List<RawSms>,
        onProgress: (ImportProgress) -> Unit
    ): ParsedBatch {
        val instruments = database.instrumentDao().getAll()
            .filterNot { it.ignored }
            .associate {
                TransactionParser.instrumentKey(it.address, it.digits) to
                    TransactionChannel.fromStorage(it.channel)
            }
        val model = SmsModelStore.load(database)
        val transactions = ArrayList<ParsedTransaction>(messages.size)
        var parsed = 0
        var skipped = 0
        var unparsed = 0
        messages.forEachIndexed { index, message ->
            when (val outcome = parser.analyze(message, instruments = instruments, model = model)) {
                is ParseOutcome.Parsed -> {
                    transactions += outcome.transaction
                    parsed++
                }
                is ParseOutcome.Skipped -> skipped++
                is ParseOutcome.Unparsed -> unparsed++
            }
            if (index == messages.lastIndex || index % 10 == 0) {
                onProgress(
                    ImportProgress(
                        processed = index + 1,
                        total = messages.size,
                        parsed = parsed,
                        skipped = skipped,
                        unparsed = unparsed,
                        currentAddress = message.address,
                        currentBody = message.body
                    )
                )
            }
        }
        return ParsedBatch(transactions, skipped, unparsed)
    }

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
                    category = classifier.classify(candidate.merchant).displayName,
                    account = candidate.account,
                    channel = candidate.channel.name
                )
            )
        }
        return accepted.size
    }
}
