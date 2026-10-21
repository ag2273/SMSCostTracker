package com.example.smscosttracker.data

enum class TransactionChannel(val label: String) {
    CARD("card"),
    ACCOUNT("account"),
    OTHER("other");

    companion object {
        fun fromStorage(value: String): TransactionChannel =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
