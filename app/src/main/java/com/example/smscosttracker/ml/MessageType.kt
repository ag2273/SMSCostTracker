package com.example.smscosttracker.ml

enum class MessageType(val label: String, val isSpending: Boolean) {
    PURCHASE("Purchase", true),
    BILL_OR_TRANSFER("Bill or transfer", true),
    WITHDRAWAL("Cash withdrawal", true),
    CREDIT("Credit, refund or income", false),
    INTERNAL("Own-account move", false),
    STATEMENT("Statement or reminder", false),
    DECLINED("Declined or failed", false),
    NOTICE("OTP or notice", false);

    companion object {
        fun fromStorage(value: String): MessageType =
            entries.firstOrNull { it.name == value } ?: NOTICE
    }
}

data class MessageLabel(
    val address: String,
    val body: String,
    val type: MessageType,
    val merchant: String? = null,
    val amount: String? = null,
    val instrument: String? = null
)
