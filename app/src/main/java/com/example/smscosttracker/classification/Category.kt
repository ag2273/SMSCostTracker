package com.example.smscosttracker.classification

enum class Category(val displayName: String) {
    GROCERIES("Groceries"),
    DINING("Dining"),
    TRANSPORT("Transport"),
    FUEL("Fuel"),
    UTILITIES("Utilities"),
    TELECOM("Telecom"),
    RENT("Rent"),
    HEALTH("Health"),
    EDUCATION("Education"),
    SHOPPING("Shopping"),
    ENTERTAINMENT("Entertainment"),
    TRAVEL("Travel"),
    FEES("Fees"),
    TRANSFERS("Transfers"),
    OTHER("Other");

    companion object {
        fun fromStorage(value: String): Category =
            entries.firstOrNull { it.name == value || it.displayName.equals(value, ignoreCase = true) }
                ?: OTHER
    }
}
