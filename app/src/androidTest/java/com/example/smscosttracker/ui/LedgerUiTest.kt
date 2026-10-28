package com.example.smscosttracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smscosttracker.analysis.ActivityFilterState
import com.example.smscosttracker.analysis.CardKind
import com.example.smscosttracker.analysis.OriginalAmount
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.InstrumentEntity
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.ExchangeRateEntity
import com.example.smscosttracker.data.ExchangeRateRepository
import com.example.smscosttracker.data.RawSmsEntity
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.classification.MerchantNames
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.analysis.HomeFilterState
import com.example.smscosttracker.ui.screens.ActivityScreen
import com.example.smscosttracker.ui.screens.HomeScreen
import com.example.smscosttracker.ui.screens.ExchangeRatesScreen
import com.example.smscosttracker.ui.screens.SubscriptionsScreen
import com.example.smscosttracker.ui.sheets.MessageLabelSheet
import com.example.smscosttracker.ui.sheets.TransactionDetailSheet
import com.example.smscosttracker.ui.theme.SmsCostTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LedgerUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TransactionRepository
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TransactionRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun storeSms(body: String): RawSmsEntity {
        database.rawSmsDao().insertAll(
            listOf(RawSmsEntity(address = "SAB", body = body, date = now, threadId = 1L))
        )
        return database.rawSmsDao().find("SAB", body)!!
    }

    private fun storeTransaction(
        merchant: String,
        amountMinor: Long,
        confidence: Double = 0.9,
        occurredAt: Long = now
    ): TransactionEntity {
        val id = database.transactionDao().insert(
            TransactionEntity(
                amountMinor = amountMinor,
                currency = "SAR",
                merchant = merchant,
                occurredAt = occurredAt,
                sourceAddress = "SAB",
                category = "Shopping",
                categoryConfidence = confidence
            )
        )
        return database.transactionDao().getAll().first { it.id == id }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun detailSheetShowsParsedFieldsAndSourceSms() {
        val body = "Purchase of SAR 120.00 at Jarir with card **4432"
        storeSms(body)
        val transaction = storeTransaction("Jarir", 12_000)

        compose.setContent {
            SmsCostTrackerTheme {
                TransactionDetailSheet(
                    transaction = transaction,
                    repository = repository,
                    onDismiss = {},
                    onCategory = {},
                    onChanged = {}
                )
            }
        }

        waitForText(body)
        compose.onNodeWithText("What we parsed").assertExists()
        compose.onNodeWithText("Fix how this SMS is read").performScrollTo().assertExists()
    }

    @Test
    fun labelSheetSavesTheChosenMessageType() {
        val raw = storeSms("Your SAB card ending 4432 is ready for collection")

        compose.setContent {
            SmsCostTrackerTheme {
                MessageLabelSheet(
                    rawSmsId = raw.id,
                    address = raw.address,
                    body = raw.body,
                    repository = repository,
                    initialType = MessageType.PURCHASE,
                    onDismiss = {},
                    onSaved = {}
                )
            }
        }

        compose.onNodeWithText(MessageType.NOTICE.label).performScrollTo().performClick()
        compose.onNodeWithText("Save and re-read messages").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { repository.messageLabel(raw.id) != null }

        assertEquals(MessageType.NOTICE.name, repository.messageLabel(raw.id)!!.type)
    }

    @Test
    fun activitySearchNarrowsTheList() {
        storeTransaction("Panda", 1_000)
        storeTransaction("Jarir", 2_000)

        compose.setContent {
            SmsCostTrackerTheme {
                var filters by remember { mutableStateOf(ActivityFilterState()) }
                ActivityScreen(
                    repository = repository,
                    refreshKey = 0,
                    filters = filters,
                    onFiltersChange = { filters = it },
                    onDataChanged = {}
                )
            }
        }

        waitForText("Panda")
        compose.onNode(hasSetTextAction()).performTextInput("Jarir")

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Panda")).fetchSemanticsNodes().isEmpty()
        }
        // The search field holds "Jarir" too, so the row makes it at least two nodes.
        assertTrue(compose.onAllNodes(hasText("Jarir")).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun needsReviewChipShowsOnlyUncertainTransactions() {
        storeTransaction("Panda", 1_000, confidence = 0.95)
        storeTransaction("Zxqv Trading", 2_000, confidence = 0.3)

        compose.setContent {
            SmsCostTrackerTheme {
                var filters by remember { mutableStateOf(ActivityFilterState()) }
                ActivityScreen(
                    repository = repository,
                    refreshKey = 0,
                    filters = filters,
                    onFiltersChange = { filters = it },
                    onDataChanged = {}
                )
            }
        }

        waitForText("Needs review · 1")
        compose.onNodeWithText("Needs review · 1").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Panda")).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Zxqv Trading").assertExists()
    }

    @Test
    fun merchantVariantsShareOneNameAndMergesSurviveReparse() {
        storeSms("Purchase of SAR 10.00 at AMAZON.SA on 01/10/2026")
        storeSms("Purchase of SAR 12.00 at Amazon SA Riyadh on 02/10/2026")
        storeSms("Purchase of SAR 30.00 at STCPAY Wallet on 03/10/2026")
        storeSms("Purchase of SAR 40.00 at STC Pay on 04/10/2026")
        val importer = TransactionImporter(database)
        importer.rebuildFromStored()

        val names = database.transactionDao().getAll().map { it.merchant }.toSet()
        assertEquals(1, names.count { MerchantNames.key(it) == "amazon" })

        val wallet = database.transactionDao().getAll().first { it.merchant.startsWith("STCPAY") }
        repository.mergeMerchant(wallet, "STC Pay")
        importer.rebuildFromStored()

        assertTrue(database.transactionDao().getAll().none { it.merchant.startsWith("STCPAY") })
        assertEquals(2, database.transactionDao().getAll().count { it.merchant == "STC Pay" })
    }

    private fun storeMonthly(merchant: String, amountMinor: Long, months: Int) {
        val zone = ZoneId.systemDefault()
        repeat(months) { back ->
            val day = LocalDate.now().minusMonths(back.toLong())
            storeTransaction(merchant, amountMinor, occurredAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
        }
    }

    private fun showSubscriptions(initialSeriesKey: String? = null) {
        compose.setContent {
            SmsCostTrackerTheme {
                var refreshKey by remember { mutableStateOf(0) }
                SubscriptionsScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    onBack = {},
                    onChanged = { refreshKey++ },
                    initialSeriesKey = initialSeriesKey
                )
            }
        }
    }

    @Test
    fun subscriptionsShowEmptyStateWhenNothingRepeats() {
        storeTransaction("Panda", 1_000)

        showSubscriptions()

        waitForText("No subscriptions found yet")
    }

    @Test
    fun renamedSubscriptionKeepsItsNewName() {
        storeMonthly("Fitness Time", 30_000, months = 4)
        showSubscriptions()

        waitForText("Fitness Time")
        compose.onNodeWithText("Fitness Time").performClick()
        compose.onNodeWithText("Rename").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Gym")
        compose.onNodeWithText("Save name").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            repository.recurringSeries().singleOrNull()?.merchant == "Gym"
        }
        val series = repository.recurringSeries().single()
        assertEquals("Gym", series.merchant)
        assertTrue(series.confirmed)
    }

    @Test
    fun dismissedSubscriptionStaysDismissed() {
        storeMonthly("Netflix", 4_900, months = 4)
        showSubscriptions()

        waitForText("Netflix")
        compose.onNodeWithText("Netflix").performClick()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()

        waitForText("No subscriptions found yet")
        TransactionImporter(database).rebuildFromStored()
        storeMonthly("Netflix", 4_900, months = 4)
        assertTrue(repository.recurringSeries().isEmpty())
    }

    @Test
    fun alertTapOpensTheSeriesDetail() {
        storeMonthly("Netflix", 4_900, months = 4)

        showSubscriptions(initialSeriesKey = "netflix|SAR")

        waitForText("Past payments")
        compose.onNodeWithText("Confirm subscription").performScrollTo().assertExists()
    }

    @Test
    fun trackAsSubscriptionAddsTheMerchant() {
        val transaction = storeTransaction("Zain", 23_900)
        assertTrue(repository.recurringSeries().isEmpty())

        compose.setContent {
            SmsCostTrackerTheme {
                TransactionDetailSheet(
                    transaction = transaction,
                    repository = repository,
                    onDismiss = {},
                    onCategory = {},
                    onChanged = {}
                )
            }
        }

        waitForText("Track as subscription")
        compose.onNodeWithText("Track as subscription").performScrollTo().performClick()
        compose.onNodeWithText("Monthly").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 10_000) { repository.recurringSeries().isNotEmpty() }
        val series = repository.recurringSeries().single()
        assertEquals("Zain", series.merchant)
        assertTrue(series.confirmed)
    }

    @Test
    fun rateChangesApplyOnlyFromThenOn() {
        val rates = ExchangeRateRepository(database)
        val before = rates.converter().convert(10_000, "USD", "EGP", at = now)

        assertTrue(rates.setManual("EGP", 50.0, now = now + 1_000))

        val converter = rates.converter()
        assertEquals(before, converter.convert(10_000, "USD", "EGP", at = now))
        assertEquals(500_000L, converter.convert(10_000, "USD", "EGP", at = now + 2_000))
        assertTrue(rates.versions().size > 4)
    }

    @Test
    fun exchangeRatesScreenListsTheRates() {
        compose.setContent {
            SmsCostTrackerTheme {
                ExchangeRatesScreen(onBack = {})
            }
        }

        waitForText("3.7500")
        compose.onNodeWithText("EGP").assertExists()
        assertTrue(compose.onAllNodes(hasText("Built-in starting rate")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun linkedDebitCardShowsAmountsInItsAccountCurrency() {
        database.instrumentDao().upsertAll(
            listOf(
                InstrumentEntity("SAB", "001", TransactionChannel.ACCOUNT.name, currency = "EGP"),
                InstrumentEntity(
                    "SAB",
                    "1214",
                    TransactionChannel.CARD.name,
                    cardKind = CardKind.DEBIT.name,
                    linkedAccount = "SAB 001"
                )
            )
        )
        val id = database.transactionDao().insert(
            TransactionEntity(
                amountMinor = 1_000,
                currency = "USD",
                merchant = "Steam",
                occurredAt = now,
                sourceAddress = "SAB",
                category = "Entertainment",
                account = "SAB 1214",
                channel = TransactionChannel.CARD.name
            )
        )

        val shown = repository.shownTransactions()

        val row = shown.transactions.single { it.id == id }
        assertEquals("EGP", row.currency)
        assertEquals(OriginalAmount(1_000, "USD"), shown.originals[id])
        assertEquals(mapOf("SAB 1214" to "SAB 001"), shown.linkedAccounts)
        assertEquals("USD", repository.storedTransaction(id)!!.currency)
    }

    @Test
    fun displayCurrencyChangesHomeTotals() {
        storeTransaction("Panda", 10_000)
        val inUsd = TransactionRepository(database) { "USD" }

        compose.setContent {
            SmsCostTrackerTheme {
                var filter by remember { mutableStateOf(HomeFilterState()) }
                HomeScreen(
                    repository = inUsd,
                    refreshKey = 0,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onOpenInstruments = {},
                    onOpenActivity = {},
                    onOpenReview = {},
                    onOpenSubscriptions = {},
                    onImport = {}
                )
            }
        }

        // SAR 100.00 at the built-in 3.75 per USD.
        waitForText("USD 26.67")
        assertTrue(compose.onAllNodes(hasText("SAR 100.00")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun builtInRatesAddedByAnUpdateJoinStoredOnes() {
        // An install from before KWD had a rate.
        database.exchangeRateDao().insertAll(listOf(ExchangeRateEntity(currency = "SAR", unitsPerUsd = 3.75, effectiveFrom = 0, source = "BUILT_IN")))

        val converter = ExchangeRateRepository(database).converter()

        assertEquals(1_221L, converter.convert(100, "KWD", "SAR", at = now))
        assertTrue(database.exchangeRateDao().getAll().any { it.currency == "KWD" })
    }
}
