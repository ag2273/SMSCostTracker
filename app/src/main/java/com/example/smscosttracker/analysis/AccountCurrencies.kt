package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity

enum class CardKind(val label: String) { DEBIT("Debit"), CREDIT("Credit") }

/**
 * The user's settings for one card or account, named by its Account label ("SAB 1214").
 * [currency] null means "the currency most of its transactions use". Only a debit card has a
 * [linkedAccount]; it then uses that account's currency.
 */
data class InstrumentProfile(
    val account: String,
    val channel: TransactionChannel,
    val currency: String? = null,
    val cardKind: CardKind? = null,
    val linkedAccount: String? = null
)

/** An amount as the SMS stated it, kept when the shown amount was converted. */
data class OriginalAmount(val amountMinor: Long, val currency: String)

/**
 * Transactions with amounts in their card's or account's currency (or one [displayCurrency]),
 * the originals of converted ones by transaction id, and each linked debit card's account.
 */
data class ShownTransactions(
    val transactions: List<TransactionEntity>,
    val originals: Map<Long, OriginalAmount>,
    val linkedAccounts: Map<String, String>
)

object AccountCurrencies {
    private const val MIN_OWN_TRANSACTIONS = 5
    private const val MIN_OWN_SHARE = 0.6

    /**
     * Each account's likely currency: the one most of its own transactions use when it has enough
     * of them and a clear majority, otherwise the one its bank's transactions mostly use (a card
     * used a few times abroad is still a local card).
     */
    fun inferred(transactions: List<TransactionEntity>): Map<String, String> {
        val rows = transactions.filter { it.account.isNotBlank() && it.currency.isNotBlank() }
        val bySender = rows.groupBy { it.sourceAddress }.mapValues { (_, list) -> majority(list).first }
        return rows.groupBy { it.account }.mapValues { (_, list) ->
            val (currency, count) = majority(list)
            if (list.size >= MIN_OWN_TRANSACTIONS && count >= list.size * MIN_OWN_SHARE) {
                currency
            } else {
                bySender[list.first().sourceAddress] ?: currency
            }
        }
    }

    /** The most used currency and how often; ties go to the alphabetically first. */
    private fun majority(rows: List<TransactionEntity>): Pair<String, Int> =
        rows.groupingBy { it.currency }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .first().let { it.key to it.value }

    /** Only debit cards link, and only to an account that is not the card itself. */
    fun linkedAccount(profile: InstrumentProfile?, profiles: Map<String, InstrumentProfile>): String? {
        if (profile == null || profile.channel != TransactionChannel.CARD || profile.cardKind != CardKind.DEBIT) return null
        val target = profile.linkedAccount?.takeIf { it != profile.account } ?: return null
        return target.takeIf { profiles[it]?.channel != TransactionChannel.CARD }
    }

    /** The currency an account's amounts are shown in, or null when it has no transactions to tell. */
    fun currencyOf(
        account: String,
        profiles: Map<String, InstrumentProfile>,
        inferred: Map<String, String>
    ): String? {
        val profile = profiles[account]
        val owner = linkedAccount(profile, profiles) ?: account
        return profiles[owner]?.currency?.takeIf { it.isNotBlank() }
            ?: inferred[owner]
            ?: profile?.currency?.takeIf { it.isNotBlank() }
            ?: inferred[account]
    }

    /**
     * Converts each transaction to its card's or account's currency, or to [displayCurrency] when
     * one is set, with the rate in effect when it happened. Amounts without a rate stay as they were.
     */
    fun convert(
        transactions: List<TransactionEntity>,
        profiles: List<InstrumentProfile>,
        converter: CurrencyConverter,
        displayCurrency: String? = null
    ): ShownTransactions {
        val byAccount = profiles.associateBy { it.account }
        val inferred = inferred(transactions)
        val originals = HashMap<Long, OriginalAmount>()
        val shown = transactions.map { transaction ->
            val target = displayCurrency
                ?: transaction.account.takeIf { it.isNotBlank() }?.let { currencyOf(it, byAccount, inferred) }
            if (target == null || target == transaction.currency) return@map transaction
            val converted = converter.convert(transaction.amountMinor, transaction.currency, target, transaction.occurredAt)
                ?: return@map transaction
            originals[transaction.id] = OriginalAmount(transaction.amountMinor, transaction.currency)
            transaction.copy(amountMinor = converted, currency = target)
        }
        val linked = byAccount.values.mapNotNull { profile ->
            linkedAccount(profile, byAccount)?.let { profile.account to it }
        }.toMap()
        return ShownTransactions(shown, originals, linked)
    }
}
