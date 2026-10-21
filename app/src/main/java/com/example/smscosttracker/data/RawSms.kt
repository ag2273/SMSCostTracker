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
