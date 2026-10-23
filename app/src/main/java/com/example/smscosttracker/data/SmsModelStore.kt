package com.example.smscosttracker.data

import com.example.smscosttracker.ml.MessageLabel
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ml.SmsModel
import com.example.smscosttracker.parsing.TransactionParser

/** Trains the [SmsModel] from stored labels, reusing it until the labels or messages change. */
object SmsModelStore {
    private data class Version(val labels: Int, val lastLabel: Long?, val messages: Int)

    @Volatile
    private var cached: Pair<Version, SmsModel>? = null

    fun load(database: AppDatabase): SmsModel {
        val labelDao = database.messageLabelDao()
        val version = Version(
            labels = labelDao.count(),
            lastLabel = labelDao.lastUpdatedAt(),
            messages = database.rawSmsDao().count()
        )
        if (version.labels == 0) return SmsModel.EMPTY
        cached?.takeIf { it.first == version }?.let { return it.second }
        val model = SmsModel.train(
            messages = database.rawSmsDao().getAll().map {
                RawSms(address = it.address, body = it.body, date = it.date, threadId = it.threadId)
            },
            labels = labelDao.getAllWithMessages().map {
                MessageLabel(
                    address = it.address,
                    body = it.body,
                    type = MessageType.fromStorage(it.type),
                    merchant = it.merchant,
                    amount = it.amount,
                    instrument = it.instrument
                )
            },
            ruleType = TransactionParser::ruleType
        )
        cached = version to model
        return model
    }
}
