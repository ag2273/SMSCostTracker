package com.example.smscosttracker

import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.CrashReportScreen
import com.example.smscosttracker.ui.components.LedgerBottomBar
import com.example.smscosttracker.ui.onboarding.OnboardingFlow
import com.example.smscosttracker.ui.screens.ActivityScreen
import com.example.smscosttracker.ui.screens.HomeScreen
import com.example.smscosttracker.ui.screens.InsightsScreen
import com.example.smscosttracker.ui.screens.InstrumentsScreen
import com.example.smscosttracker.ui.screens.ReimportScreen
import com.example.smscosttracker.ui.screens.SettingsRoute
import com.example.smscosttracker.ui.screens.SettingsScreen
import com.example.smscosttracker.ui.screens.UnparsedMessagesScreen
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.SmsCostTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                var refreshKey by remember { mutableStateOf(0) }

                if (showOnboarding) {
                    OnboardingFlow(
                        repository = repository,
                        importer = importer,
                        preferences = preferences,
                        onFinished = {
                            showOnboarding = false
                            refreshKey++
                        }
                    )
                } else {
                    MainShell(
                        repository = repository,
                        importer = importer,
                        preferences = preferences,
                        refreshKey = refreshKey,
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
private fun MainShell(
    repository: TransactionRepository,
    importer: TransactionImporter,
    preferences: OnboardingPreferences,
    refreshKey: Int,
    onDataChanged: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.OVERVIEW) }

    BackHandler(enabled = tab == 3 && settingsRoute != SettingsRoute.OVERVIEW) {
        settingsRoute = SettingsRoute.OVERVIEW
    }

    Scaffold(
        containerColor = Ledger.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            LedgerBottomBar(
                selected = tab,
                onSelect = { index ->
                    tab = index
                    if (index == 3) settingsRoute = SettingsRoute.OVERVIEW
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    onOpenInstruments = {
                        tab = 3
                        settingsRoute = SettingsRoute.INSTRUMENTS
                    },
                    onOpenActivity = { tab = 1 },
                    onImport = {
                        tab = 3
                        settingsRoute = SettingsRoute.REIMPORT
                    }
                )
                1 -> ActivityScreen(
                    repository = repository,
                    refreshKey = refreshKey,
                    onDataChanged = onDataChanged
                )
                2 -> InsightsScreen(repository = repository, refreshKey = refreshKey)
                3 -> when (settingsRoute) {
                    SettingsRoute.OVERVIEW -> SettingsScreen(
                        repository = repository,
                        preferences = preferences,
                        refreshKey = refreshKey,
                        onOpenInstruments = { settingsRoute = SettingsRoute.INSTRUMENTS },
                        onOpenUnparsed = { settingsRoute = SettingsRoute.UNPARSED },
                        onOpenReimport = { settingsRoute = SettingsRoute.REIMPORT },
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
                        onBack = { settingsRoute = SettingsRoute.OVERVIEW }
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
                }
            }
        }
    }
}
