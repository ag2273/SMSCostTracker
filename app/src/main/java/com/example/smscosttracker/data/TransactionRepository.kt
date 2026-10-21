package com.example.smscosttracker.data

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.classification.CategoryClassifier
import com.example.smscosttracker.parsing.ParseOutcome
import com.example.smscosttracker.parsing.TransactionParser

data class UnparsedSms(
    val address: String,
    val body: String,
    val date: Long,
    val reason: String
)

class TransactionRepository(private val database: AppDatabase) {
    fun transactions(): List<TransactionEntity> = database.transactionDao().getAll()

    fun count(): Int = database.transactionDao().count()

    fun rawMessageCount(): Int = database.rawSmsDao().count()

    fun hasImportedData(): Boolean =
        database.rawSmsDao().count() > 0 || database.transactionDao().count() > 0

    fun sourceMessagesFor(transaction: TransactionEntity): List<RawSmsEntity> {
        val instruments = configuredInstrumentMap()
        val candidates = nearbyMessagesFor(transaction).mapNotNull { raw ->
            val parsed = TransactionParser.parse(
                RawSms(
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    threadId = raw.threadId
                ),
                instruments = instruments
            ) ?: return@mapNotNull null
            raw to parsed
        }
        val sameTransaction = candidates.filter { (_, parsed) ->
            parsed.amountMinor == transaction.amountMinor &&
                parsed.merchant == transaction.merchant &&
                parsed.occurredAt == transaction.occurredAt &&
                parsed.sourceAddress == transaction.sourceAddress
        }
        val sameAmountAndTime = candidates.filter { (_, parsed) ->
            parsed.amountMinor == transaction.amountMinor &&
                parsed.occurredAt == transaction.occurredAt &&
                parsed.sourceAddress == transaction.sourceAddress
        }
        return (sameTransaction.ifEmpty { sameAmountAndTime })
            .map { it.first }
            .sortedBy { kotlin.math.abs(it.date - transaction.occurredAt) }
    }

    fun nearbyMessagesFor(transaction: TransactionEntity): List<RawSmsEntity> {
        val window = 45L * 24 * 60 * 60 * 1000
        return database.rawSmsDao().messagesFor(
            address = transaction.sourceAddress,
            from = transaction.occurredAt - window,
            to = transaction.occurredAt + window,
            anchor = transaction.occurredAt
        )
    }

    fun updateCategory(transaction: TransactionEntity, category: Category) {
        val merchantKey = CategoryClassifier.normalizeMerchant(transaction.merchant)
        database.transactionDao().getAll()
            .filter { CategoryClassifier.normalizeMerchant(it.merchant) == merchantKey }
            .forEach { database.transactionDao().updateCategory(it.id, category.displayName) }
        database.categoryCorrectionDao().upsert(
            CategoryCorrectionEntity(
                merchantKey = merchantKey,
                category = category.displayName
            )
        )
    }

    fun pendingInstruments(): List<DetectedInstrument> =
        detectedInstruments().filter { !it.verified }

    fun pendingInstrumentKeys(): Set<String> =
        pendingInstruments()
            .map { TransactionParser.instrumentKey(it.address, it.digits) }
            .toSet()

    fun unparsedMessages(): List<UnparsedSms> {
        val instruments = configuredInstrumentMap()
        return database.rawSmsDao().getAll().mapNotNull { raw ->
            val outcome = TransactionParser.analyze(
                RawSms(
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    threadId = raw.threadId
                ),
                instruments = instruments
            )
            if (outcome is ParseOutcome.Unparsed) {
                UnparsedSms(
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    reason = outcome.reason
                )
            } else {
                null
            }
        }.sortedByDescending { it.date }
    }

    fun detectedInstruments(): List<DetectedInstrument> =
        allDetectedInstruments().filterNot { it.ignored }

    fun allDetectedInstruments(): List<DetectedInstrument> {
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
            DetectedInstrument(
                address = key.first,
                digits = key.second,
                channel = stored?.let { TransactionChannel.fromStorage(it.channel) }
                    ?: channels.maxByOrNull { it.value }!!.key,
                messageCount = channels.values.sum(),
                verified = stored != null,
                ignored = stored?.ignored == true
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
        val fromMessages = nearbyMessagesFor(transaction).flatMap {
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

    private fun configuredInstrumentMap(): Map<String, TransactionChannel> =
        database.instrumentDao().getAll()
            .filterNot { it.ignored }
            .associate {
                TransactionParser.instrumentKey(it.address, it.digits) to
                    TransactionChannel.fromStorage(it.channel)
            }
}
