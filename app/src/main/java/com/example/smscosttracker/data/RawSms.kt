package com.example.smscosttracker.data

data class RawSms(
    val address: String,
    val body: String,
    val date: Long,
    val threadId: Long
)

data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val occurredAt: Long,
    val sourceAddress: String,
    val account: String = "",
    val channel: TransactionChannel = TransactionChannel.OTHER
)

data class InstrumentCandidate(
    val digits: String,
    val channel: TransactionChannel
)

data class DetectedInstrument(
    val address: String,
    val digits: String,
    val channel: TransactionChannel,
    val messageCount: Int,
    val verified: Boolean,
    val ignored: Boolean = false
)
