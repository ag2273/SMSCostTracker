package com.example.smscosttracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.alerts.SubscriptionAlerts
import com.example.smscosttracker.analysis.ActivityFilterState
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.HomeFilterState
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.parsing.TransactionParser
import com.example.smscosttracker.rates.ExchangeRateSync
import com.example.smscosttracker.sms.SmsSync
import com.example.smscosttracker.ui.CrashReportScreen
import com.example.smscosttracker.ui.components.LedgerBottomBar
import com.example.smscosttracker.ui.onboarding.OnboardingFlow
import com.example.smscosttracker.ui.screens.ActivityScreen
import com.example.smscosttracker.ui.screens.ExchangeRatesScreen
import com.example.smscosttracker.ui.screens.ExportScreen
import com.example.smscosttracker.ui.screens.HomeScreen
import com.example.smscosttracker.ui.screens.InsightsScreen
import com.example.smscosttracker.ui.screens.InstrumentsScreen
import com.example.smscosttracker.ui.screens.ReimportScreen
import com.example.smscosttracker.ui.screens.SettingsRoute
import com.example.smscosttracker.ui.screens.SettingsScreen
import com.example.smscosttracker.ui.screens.SubscriptionsScreen
import com.example.smscosttracker.ui.screens.UnparsedMessagesScreen
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.SmsCostTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    /** Set when a subscription alert is tapped; the shell opens that series and clears it. */
    private val openSeriesKey = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(SubscriptionAlerts.EXTRA_SERIES_KEY)?.let { openSeriesKey.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            openSeriesKey.value = intent?.getStringExtra(SubscriptionAlerts.EXTRA_SERIES_KEY)
        }
        val crashFile = File(filesDir, "last_crash.txt")
        val lastUpdate = runCatching {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        if (crashFile.isFile && crashFile.lastModified() < lastUpdate) crashFile.delete()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { crashFile.writeText(throwable.stackTraceToString()) }
            previousHandler?.uncaughtException(thread, throwable)
        }
        setContent {
            SmsCostTrackerTheme {
                val context = LocalContext.current
                var crashText by remember {
                    mutableStateOf(
                        crashFile.takeIf { it.isFile }
                            ?.readText()
                            ?.takeIf { it.isNotBlank() }
                    )
                }
                if (crashText != null) {
                    CrashReportScreen(
                        stackTrace = crashText!!,
                        onTryAgain = {
                            crashFile.delete()
                            crashText = null
                        }
                    )
                    return@SmsCostTrackerTheme
                }
                val database = remember { AppDatabase.get(context) }
                val repository = remember { TransactionRepository(database) }
                val importer = remember { TransactionImporter(database) }
                val preferences = remember { OnboardingPreferences(context) }
                var showOnboarding by remember { mutableStateOf(!preferences.complete) }
                var rebuilding by remember { mutableStateOf(false) }
                var refreshKey by remember { mutableStateOf(0) }

                val syncReady by rememberUpdatedState(!showOnboarding && !rebuilding)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && syncReady) {
                            thread {
                                val result = runCatching { SmsSync.syncNow(context) }.getOrNull()
                                if ((result?.rawInserted ?: 0) > 0) {
                                    Handler(Looper.getMainLooper()).post { refreshKey++ }
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(Unit) {
                    if (preferences.autoSync) SmsSync.schedule(context)
                    SubscriptionAlerts.schedule(context)
                    ExchangeRateSync.schedule(context)
                    val needsRebuild = preferences.complete &&
                        preferences.parserVersion < TransactionParser.PARSER_VERSION
                    if (needsRebuild) {
                        rebuilding = true
                        runCatching {
                            withContext(Dispatchers.IO) { importer.rebuildFromStored() }
                        }.onSuccess {
                            preferences.parserVersion = TransactionParser.PARSER_VERSION
                            refreshKey++
                        }
                        rebuilding = false
                    }
                }

                when {
                    showOnboarding -> OnboardingFlow(
                        repository = repository,
                        importer = importer,
                        preferences = preferences,
                        onFinished = {
                            preferences.parserVersion = TransactionParser.PARSER_VERSION
                            showOnboarding = false
                            refreshKey++
                        }
                    )
                    rebuilding -> UpdatingTransactionsScreen()
                    else -> MainShell(
                        repository = repository,
                        importer = importer,
                        preferences = preferences,
                        refreshKey = refreshKey,
                        openSeriesKey = openSeriesKey.value,
                        onSeriesOpened = { openSeriesKey.value = null },
                        onDataChanged = { refreshKey++ },
                        onRestartOnboarding = {
                            preferences.reset()
                            showOnboarding = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatingTransactionsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Ledger.Primary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Updating parsed transactions…",
                style = MaterialTheme.typography.bodyMedium,
                color = Ledger.Muted
            )
        }
    }
}

@Composable
private fun MainShell(
    repository: TransactionRepository,
    importer: TransactionImporter,
    preferences: OnboardingPreferences,
    refreshKey: Int,
    openSeriesKey: String?,
    onSeriesOpened: () -> Unit,
    onDataChanged: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.OVERVIEW) }
    var homeFilter by remember { mutableStateOf(HomeFilterState()) }
    var activityFilters by remember { mutableStateOf(ActivityFilterState()) }
    var showSubscriptions by remember { mutableStateOf(false) }
    var subscriptionsSeriesKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openSeriesKey) {
        if (openSeriesKey != null) {
            subscriptionsSeriesKey = openSeriesKey
            showSubscriptions = true
            onSeriesOpened()
        }
    }

    BackHandler(enabled = tab == 3 && settingsRoute != SettingsRoute.OVERVIEW) {
        settingsRoute = SettingsRoute.OVERVIEW
    }
    BackHandler(enabled = showSubscriptions) { showSubscriptions = false }

    Scaffold(
        containerColor = Ledger.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            LedgerBottomBar(
                selected = tab,
                onSelect = { index ->
                    tab = index
                    showSubscriptions = false
                    subscriptionsSeriesKey = null
                    if (index == 3) settingsRoute = SettingsRoute.OVERVIEW
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showSubscriptions) {
                SubscriptionsScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    initialSeriesKey = subscriptionsSeriesKey,
                    onBack = {
                        showSubscriptions = false
                        subscriptionsSeriesKey = null
                    },
                    onChanged = onDataChanged
                )
            } else when (tab) {
                0 -> HomeScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    filter = homeFilter,
                    onFilterChange = { homeFilter = it },
                    onOpenInstruments = {
                        tab = 3
                        settingsRoute = SettingsRoute.INSTRUMENTS
                    },
                    onOpenActivity = { tab = 1 },
                    onOpenReview = {
                        activityFilters = activityFilters.copy(
                            filter = activityFilters.filter.copy(
                                preset = DatePreset.ALL,
                                needsReview = true
                            )
                        )
                        tab = 1
                    },
                    onOpenSubscriptions = {
                        subscriptionsSeriesKey = null
                        showSubscriptions = true
                    },
                    onImport = {
                        tab = 3
                        settingsRoute = SettingsRoute.REIMPORT
                    }
                )
                1 -> ActivityScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    filters = activityFilters,
                    onFiltersChange = { activityFilters = it },
                    onDataChanged = onDataChanged
                )
                2 -> InsightsScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    onOpenSubscriptions = {
                        subscriptionsSeriesKey = null
                        showSubscriptions = true
                    }
                )
                3 -> when (settingsRoute) {
                    SettingsRoute.OVERVIEW -> SettingsScreen(
                        repository = repository,
                        preferences = preferences,
                        refreshKey = refreshKey,
                        onOpenInstruments = { settingsRoute = SettingsRoute.INSTRUMENTS },
                        onOpenUnparsed = { settingsRoute = SettingsRoute.UNPARSED },
                        onOpenReimport = { settingsRoute = SettingsRoute.REIMPORT },
                        onOpenExport = { settingsRoute = SettingsRoute.EXPORT },
                        onOpenRates = { settingsRoute = SettingsRoute.RATES },
                        onRestartOnboarding = onRestartOnboarding
                    )
                    SettingsRoute.INSTRUMENTS -> InstrumentsScreen(
                        repository = repository,
                        importer = importer,
                        refreshKey = refreshKey,
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW },
                        onChanged = {
                            onDataChanged()
                            settingsRoute = SettingsRoute.OVERVIEW
                        }
                    )
                    SettingsRoute.UNPARSED -> UnparsedMessagesScreen(
                        repository = repository,
                        refreshKey = refreshKey,
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW },
                        onChanged = onDataChanged
                    )
                    SettingsRoute.REIMPORT -> ReimportScreen(
                        importer = importer,
                        preferences = preferences,
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW },
                        onFinished = {
                            onDataChanged()
                            settingsRoute = SettingsRoute.OVERVIEW
                        }
                    )
                    SettingsRoute.RATES -> ExchangeRatesScreen(
                        preferences = preferences,
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW }
                    )
                    SettingsRoute.EXPORT -> ExportScreen(
                        repository = repository,
                        refreshKey = refreshKey,
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW }
                    )
                }
            }
        }
    }
}
