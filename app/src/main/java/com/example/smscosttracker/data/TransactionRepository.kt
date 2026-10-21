package com.example.smscosttracker.data

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.classification.CategoryClassifier
import com.example.smscosttracker.parsing.TransactionParser

class TransactionRepository(private val database: AppDatabase) {
    fun transactions(): List<TransactionEntity> = database.transactionDao().getAll()

    fun count(): Int = database.transactionDao().count()

    fun rawMessagesFor(transaction: TransactionEntity): List<RawSmsEntity> {
        val window = 45L * 24 * 60 * 60 * 1000
        return database.rawSmsDao().messagesFor(
            address = transaction.sourceAddress,
            from = transaction.occurredAt - window,
            to = transaction.occurredAt + window,
            anchor = transaction.occurredAt
        )
    }

    fun updateCategory(transaction: TransactionEntity, category: Category) {
        database.transactionDao().updateCategoryByMerchant(transaction.merchant, category.displayName)
        database.categoryCorrectionDao().upsert(
            CategoryCorrectionEntity(
                merchantKey = CategoryClassifier.normalizeMerchant(transaction.merchant),
                category = category.displayName
            )
        )
    }

    fun detectedInstruments(): List<DetectedInstrument> {
        val configured = database.instrumentDao().getAll().associateBy {
            TransactionParser.instrumentKey(it.address, it.digits)
        }
        val counts = LinkedHashMap<Pair<String, String>, MutableMap<TransactionChannel, Int>>()
        database.rawSmsDao().getAll().forEach { message ->
            TransactionParser.candidates(message.body).forEach { candidate ->
                val key = message.address to candidate.digits
                counts.getOrPut(key) { mutableMapOf() }
                    .merge(candidate.channel, 1, Int::plus)
            }
        }
        return counts.mapNotNull { (key, channels) ->
            val stored = configured[TransactionParser.instrumentKey(key.first, key.second)]
            if (stored?.ignored == true) return@mapNotNull null
            DetectedInstrument(
                address = key.first,
                digits = key.second,
                channel = stored?.let { TransactionChannel.fromStorage(it.channel) }
                    ?: channels.maxByOrNull { it.value }!!.key,
                messageCount = channels.values.sum(),
                verified = stored != null,
                ignored = false
            )
        }.sortedWith(compareBy({ it.address }, { it.digits }))
    }

    fun saveInstruments(instruments: List<DetectedInstrument>) {
        database.instrumentDao().upsertAll(
            instruments.map {
                InstrumentEntity(
                    address = it.address,
                    digits = it.digits,
                    channel = it.channel.name,
                    ignored = it.ignored
                )
            }
        )
    }

    fun candidateInstruments(transaction: TransactionEntity): List<InstrumentCandidate> {
        val fromMessages = rawMessagesFor(transaction).flatMap {
            TransactionParser.candidates(it.body)
        }
        val currentDigits = transaction.account.substringAfterLast(' ')
        val current = if (currentDigits.length >= 2) {
            listOf(
                InstrumentCandidate(
                    currentDigits,
                    TransactionChannel.fromStorage(transaction.channel)
                )
            )
        } else {
            emptyList()
        }
        val ignored = database.instrumentDao().getAll()
            .filter { it.ignored && it.address == transaction.sourceAddress }
            .map { it.digits }
            .toSet()
        return (fromMessages + current)
            .filterNot { it.digits in ignored }
            .distinctBy { it.digits }
    }

    fun assignInstrument(
        transaction: TransactionEntity,
        candidate: InstrumentCandidate
    ) {
        database.instrumentDao().upsertAll(
            listOf(
                InstrumentEntity(
                    address = transaction.sourceAddress,
                    digits = candidate.digits,
                    channel = candidate.channel.name
                )
            )
        )
        database.transactionDao().updateInstrument(
            id = transaction.id,
            account = "${transaction.sourceAddress} ${candidate.digits}",
            channel = candidate.channel.name
        )
    }
}
