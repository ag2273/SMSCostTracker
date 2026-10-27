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
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smscosttracker.analysis.ActivityFilterState
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.RawSmsEntity
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.classification.MerchantNames
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ui.screens.ActivityScreen
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
        confidence: Double = 0.9
    ): TransactionEntity {
        val id = database.transactionDao().insert(
            TransactionEntity(
                amountMinor = amountMinor,
                currency = "SAR",
                merchant = merchant,
                occurredAt = now,
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
}
