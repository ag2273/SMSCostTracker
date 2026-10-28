package com.example.smscosttracker.data

import com.example.smscosttracker.analysis.RecurringOverrideState
import com.example.smscosttracker.analysis.RecurringPayments
import com.example.smscosttracker.analysis.RecurringSeries
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.classification.CategoryClassifier
import com.example.smscosttracker.classification.MerchantNames
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ml.SmsModel
import com.example.smscosttracker.parsing.ParseOutcome
import com.example.smscosttracker.parsing.TransactionParser

data class UnparsedSms(
    val rawSmsId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val reason: String
)

class TransactionRepository(private val database: AppDatabase) {
    // Stored SMS are only ever added, so their count tells when a re-scan is needed.
    private data class UnparsedVersion(
        val messages: Int,
        val instruments: Map<String, TransactionChannel>,
        val model: SmsModel
    )

    @Volatile
    private var unparsedCache: Pair<UnparsedVersion, List<UnparsedSms>>? = null

    @Volatile
    private var candidateCache: Pair<Int, Map<Pair<String, String>, Map<TransactionChannel, Int>>>? = null

    fun transactions(): List<TransactionEntity> = database.transactionDao().getAll()

    fun count(): Int = database.transactionDao().count()

    fun rawMessageCount(): Int = database.rawSmsDao().count()

    fun hasImportedData(): Boolean =
        database.rawSmsDao().count() > 0 || database.transactionDao().count() > 0

    fun sourceMessagesFor(transaction: TransactionEntity): List<RawSmsEntity> {
        val instruments = configuredInstrumentMap()
        val model = SmsModelStore.load(database)
        val candidates = nearbyMessagesFor(transaction).mapNotNull { raw ->
            val parsed = TransactionParser.parse(
                RawSms(
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    threadId = raw.threadId
                ),
                instruments = instruments,
                model = model
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

    fun messageLabel(rawSmsId: Long): MessageLabelEntity? =
        database.messageLabelDao().get(rawSmsId)

    fun ruleType(message: RawSmsEntity): MessageType = TransactionParser.ruleType(message.body)

    /** Saves the user's reading of one SMS and re-parses everything so the model applies it. */
    fun saveMessageLabel(
        rawSmsId: Long,
        type: MessageType,
        merchant: String? = null,
        amount: String? = null,
        instrument: String? = null
    ): ImportResult {
        database.messageLabelDao().upsert(
            MessageLabelEntity(
                rawSmsId = rawSmsId,
                type = type.name,
                merchant = merchant?.trim()?.takeIf { it.isNotEmpty() },
                amount = amount?.trim()?.takeIf { it.isNotEmpty() },
                instrument = instrument?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
        )
        return TransactionImporter(database).rebuildFromStored()
    }

    fun clearMessageLabel(rawSmsId: Long): ImportResult {
        database.messageLabelDao().delete(rawSmsId)
        return TransactionImporter(database).rebuildFromStored()
    }

    /** Distinct merchant names, most used first, for the "Same merchant as" picker. */
    fun merchantNames(): List<String> =
        database.transactionDao().getAll()
            .map { it.merchant }
            .filterNot { it.isBlank() || it.equals("Unknown", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    fun hasMerges(merchant: String): Boolean =
        database.merchantAliasDao().getAll().any { it.merchant == merchant }

    /** Treats [transaction]'s merchant as [target] from now on, then re-reads stored SMS. */
    fun mergeMerchant(transaction: TransactionEntity, target: String): ImportResult {
        database.merchantAliasDao().upsert(
            MerchantAliasEntity(
                aliasKey = MerchantNames.key(transaction.merchant),
                merchant = target,
                updatedAt = System.currentTimeMillis()
            )
        )
        return TransactionImporter(database).rebuildFromStored()
    }

    /** Removes the user's merges into or out of [transaction]'s merchant, then re-reads stored SMS. */
    fun undoMerchantMerges(transaction: TransactionEntity): ImportResult {
        database.merchantAliasDao().deleteFor(transaction.merchant, MerchantNames.key(transaction.merchant))
        return TransactionImporter(database).rebuildFromStored()
    }

    fun recurringSeries(transactions: List<TransactionEntity> = transactions()): List<RecurringSeries> =
        RecurringPayments.detect(transactions, database.recurringOverrideDao().getAll())

    fun confirmRecurring(series: RecurringSeries) {
        val name = database.recurringOverrideDao().get(series.key)?.name
        saveRecurringOverride(series, RecurringOverrideState.CONFIRMED, name)
    }

    /** Renaming a series also confirms it. */
    fun renameRecurring(series: RecurringSeries, name: String) {
        saveRecurringOverride(series, RecurringOverrideState.CONFIRMED, name.trim().takeIf { it.isNotEmpty() })
    }

    /** A dismissed series is not suggested again, including after a re-parse. */
    fun dismissRecurring(series: RecurringSeries) {
        saveRecurringOverride(series, RecurringOverrideState.DISMISSED, null)
    }

    private fun saveRecurringOverride(series: RecurringSeries, state: RecurringOverrideState, name: String?) {
        database.recurringOverrideDao().upsert(
            RecurringOverrideEntity(
                seriesKey = series.key,
                state = state.name,
                name = name,
                updatedAt = System.currentTimeMillis()
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
        val model = SmsModelStore.load(database)
        val version = UnparsedVersion(database.rawSmsDao().count(), instruments, model)
        unparsedCache?.takeIf { it.first == version }?.let { return it.second }
        return database.rawSmsDao().getAll().mapNotNull { raw ->
            val outcome = TransactionParser.analyze(
                RawSms(
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    threadId = raw.threadId
                ),
                instruments = instruments,
                model = model
            )
            if (outcome is ParseOutcome.Unparsed) {
                UnparsedSms(
                    rawSmsId = raw.id,
                    address = raw.address,
                    body = raw.body,
                    date = raw.date,
                    reason = outcome.reason
                )
            } else {
                null
            }
        }.sortedByDescending { it.date }.also { unparsedCache = version to it }
    }

    fun detectedInstruments(): List<DetectedInstrument> =
        allDetectedInstruments().filterNot { it.ignored }

    fun allDetectedInstruments(): List<DetectedInstrument> {
        val configured = database.instrumentDao().getAll().associateBy {
            TransactionParser.instrumentKey(it.address, it.digits)
        }
        return candidateCounts().mapNotNull { (key, channels) ->
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

    private fun candidateCounts(): Map<Pair<String, String>, Map<TransactionChannel, Int>> {
        val messages = database.rawSmsDao().count()
        candidateCache?.takeIf { it.first == messages }?.let { return it.second }
        val counts = LinkedHashMap<Pair<String, String>, MutableMap<TransactionChannel, Int>>()
        database.rawSmsDao().getAll().forEach { message ->
            TransactionParser.candidates(message.body).forEach { candidate ->
                val key = message.address to candidate.digits
                counts.getOrPut(key) { mutableMapOf() }
                    .merge(candidate.channel, 1, Int::plus)
            }
        }
        candidateCache = messages to counts
        return counts
    }

    private fun configuredInstrumentMap(): Map<String, TransactionChannel> =
        database.instrumentDao().getAll()
            .filterNot { it.ignored }
            .associate {
                TransactionParser.instrumentKey(it.address, it.digits) to
                    TransactionChannel.fromStorage(it.channel)
            }
}
