package com.example.smscosttracker.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.smscosttracker.data.DetectedInstrument
import com.example.smscosttracker.data.ImportProgress
import com.example.smscosttracker.data.ImportResult
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.OnboardingStage
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.parsing.TransactionParser
import com.example.smscosttracker.sms.SmsReader
import com.example.smscosttracker.sms.SmsThread
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.ChannelIcon
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerTextButton
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ReviewBanner
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SecondaryButton
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

private enum class ReviewStage {
    GROUPS,
    CONFIRM
}

@Composable
fun OnboardingFlow(
    repository: TransactionRepository,
    importer: TransactionImporter,
    preferences: OnboardingPreferences,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var stage by remember {
        mutableStateOf(
            if (preferences.complete) OnboardingStage.COMPLETE else preferences.stage
        )
    }
    var permissionGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var reviewingAddress by remember { mutableStateOf<String?>(null) }
    var reviewStage by remember { mutableStateOf(ReviewStage.GROUPS) }
    var scanNonce by remember { mutableStateOf(0) }

    fun go(next: OnboardingStage) {
        stage = next
        preferences.stage = next
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasSmsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            go(OnboardingStage.THREADS)
        }
    }

    BackHandler(enabled = stage != OnboardingStage.WELCOME) {
        when (stage) {
            OnboardingStage.PERMISSION -> go(OnboardingStage.WELCOME)
            OnboardingStage.THREADS -> go(OnboardingStage.PERMISSION)
            OnboardingStage.SCAN -> go(OnboardingStage.THREADS)
            OnboardingStage.REVIEW -> {
                if (reviewStage == ReviewStage.CONFIRM) {
                    reviewStage = ReviewStage.GROUPS
                    reviewingAddress = null
                } else {
                    go(OnboardingStage.COMPLETE)
                }
            }
            OnboardingStage.COMPLETE -> {
                preferences.markComplete()
                onFinished()
            }
            OnboardingStage.WELCOME -> Unit
        }
    }

    when (stage) {
        OnboardingStage.WELCOME -> WelcomeScreen(
            onStart = { go(OnboardingStage.PERMISSION) }
        )

        OnboardingStage.PERMISSION -> PermissionScreen(
            granted = permissionGranted,
            onRequest = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
            onContinue = { go(OnboardingStage.THREADS) },
            onSkip = {
                preferences.markComplete()
                onFinished()
            },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        OnboardingStage.THREADS -> ThreadsScreen(
            granted = permissionGranted,
            selectedFromPrefs = preferences.selectedThreadIds,
            onBack = { go(OnboardingStage.PERMISSION) },
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.READ_SMS)
            },
            onScan = { selected ->
                preferences.selectedThreadIds = selected
                reviewStage = ReviewStage.GROUPS
                reviewingAddress = null
                scanNonce++
                go(OnboardingStage.SCAN)
            },
            onNotNow = {
                preferences.markComplete()
                onFinished()
            }
        )

        OnboardingStage.SCAN -> ScanScreen(
            selectedThreadIds = preferences.selectedThreadIds,
            importer = importer,
            onCancel = { go(OnboardingStage.THREADS) },
            onComplete = {
                importResult = it
                go(OnboardingStage.REVIEW)
            }
        )

        OnboardingStage.REVIEW -> {
            if (reviewStage == ReviewStage.CONFIRM) {
                ReviewConfirmScreen(
                    address = reviewingAddress.orEmpty(),
                    repository = repository,
                    importer = importer,
                    onBack = {
                        reviewStage = ReviewStage.GROUPS
                        reviewingAddress = null
                    },
                    onPause = { go(OnboardingStage.COMPLETE) },
                    onSaved = { nextAddress ->
                        if (nextAddress == null) {
                            reviewStage = ReviewStage.GROUPS
                            reviewingAddress = null
                            go(OnboardingStage.COMPLETE)
                        } else {
                            reviewingAddress = nextAddress
                        }
                    }
                )
            } else {
                ReviewGroupsScreen(
                    repository = repository,
                    onBack = { go(OnboardingStage.COMPLETE) },
                    onReview = { address ->
                        reviewingAddress = address
                        reviewStage = ReviewStage.CONFIRM
                    },
                    onReviewLater = { go(OnboardingStage.COMPLETE) }
                )
            }
        }

        OnboardingStage.COMPLETE -> CompleteScreen(
            repository = repository,
            importResult = importResult,
            onHome = {
                preferences.markComplete()
                onFinished()
            },
            onReviewPending = {
                reviewStage = ReviewStage.GROUPS
                reviewingAddress = null
                go(OnboardingStage.REVIEW)
            }
        )
    }
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun OnboardingPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            content = content
        )
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    OnboardingPage {
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = Ledger.Primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("S", color = Ledger.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "Your spending,\nfrom your SMS.",
            style = MaterialTheme.typography.displayLarge,
            color = Ledger.Ink
        )
        Text(
            "Turn bank messages into clear transactions and charts. Parsing happens entirely on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ledger.Muted,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.height(26.dp))
        LedgerCard(modifier = Modifier.padding(bottom = 18.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PrivacyPoint(Icons.Outlined.Lock, "On-device only", "SMS is read and parsed locally. Nothing is uploaded.")
                PrivacyPoint(Icons.Outlined.AccountBalance, "No bank login", "We never ask for a password, PIN or account access.")
                PrivacyPoint(Icons.Outlined.Check, "You stay in control", "Choose the threads to scan and undo corrections anytime.")
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Get started", onStart)
        Text(
            "By continuing you agree to keep your device secure. You can revoke SMS access in Android Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 10.dp, end = 10.dp)
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PrivacyPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = Ledger.SurfaceSoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Ledger.Primary, modifier = Modifier.size(17.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
        }
    }
}

@Composable
private fun PermissionScreen(
    granted: Boolean,
    onRequest: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    OnboardingPage {
        Spacer(Modifier.height(20.dp))
        PageHeader("Allow SMS access", "Step 2 of 7")
        Spacer(Modifier.height(34.dp))
        Surface(
            modifier = Modifier.size(92.dp).align(Alignment.CenterHorizontally),
            shape = CircleShape,
            color = Ledger.SurfaceSoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Ledger.Primary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Text(
            "We need to read bank messages to build your transactions.",
            style = MaterialTheme.typography.headlineMedium,
            color = Ledger.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 26.dp)
        )
        Text(
            "Only the threads you select are imported. Messages are parsed on this phone and are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp)
        )
        Spacer(Modifier.height(24.dp))
        LedgerCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyPoint(Icons.Outlined.Check, "Read SMS", "Bank and payment messages only, from the threads you choose.")
                PrivacyPoint(Icons.Outlined.Lock, "No internet required", "Parsing works offline. No message text leaves the device.")
                PrivacyPoint(Icons.Outlined.AccountBalance, "Revoke anytime", "You can change access in Android Settings later.")
            }
        }
        Spacer(Modifier.height(24.dp))
        if (granted) {
            PrimaryButton("Continue", onContinue)
        } else {
            PrimaryButton("Allow SMS access", onRequest)
            LedgerTextButton(
                text = "Open app settings",
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            LedgerTextButton(
                text = "Continue without SMS",
                onClick = onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Text(
            "You can continue without access, but there will be no transactions to review.",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ThreadsScreen(
    granted: Boolean,
    selectedFromPrefs: Set<Long>,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onScan: (Set<Long>) -> Unit,
    onNotNow: () -> Unit
) {
    val context = LocalContext.current
    var threads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var selected by remember { mutableStateOf(selectedFromPrefs) }
    var loading by remember { mutableStateOf(granted) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(granted) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        thread {
            runCatching { SmsReader.readThreads(context) }
                .onSuccess { loaded ->
                    Handler(Looper.getMainLooper()).post {
                        threads = loaded
                        loading = false
                    }
                }
                .onFailure {
                    Handler(Looper.getMainLooper()).post {
                        error = it.message ?: "Could not read SMS threads."
                        loading = false
                    }
                }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            PageHeader(
                title = "Choose bank threads",
                subtitle = "Step 3 of 7 · only selected threads are scanned",
                onBack = onBack
            )
            if (!granted) {
                Spacer(Modifier.height(30.dp))
                Text(
                    "SMS access is off. Grant access to load your bank conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ledger.Muted
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Allow SMS access", onRequestPermission)
            } else if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (error != null) {
                Spacer(Modifier.height(24.dp))
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Ledger.Danger)
            } else {
                val selectedMessages = threads.filter { it.threadId in selected }.sumOf { it.messageCount }
                Text(
                    "${selected.size} selected · ${"%,d".format(selectedMessages)} messages",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(threads, key = { it.threadId }) { thread ->
                        ThreadChoiceRow(
                            thread = thread,
                            checked = thread.threadId in selected,
                            onToggle = {
                                selected = if (thread.threadId in selected) {
                                    selected - thread.threadId
                                } else {
                                    selected + thread.threadId
                                }
                            }
                        )
                        LedgerDivider()
                    }
                }
                PrimaryButton(
                    text = "Scan selected threads",
                    onClick = { onScan(selected) },
                    enabled = selected.isNotEmpty()
                )
                LedgerTextButton(
                    text = "Not now",
                    onClick = onNotNow,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ThreadChoiceRow(
    thread: SmsThread,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(thread.address, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(
                thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.Muted,
                maxLines = 1
            )
            Text(
                "${thread.messageCount} messages",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
        }
    }
}

@Composable
private fun ScanScreen(
    selectedThreadIds: Set<Long>,
    importer: TransactionImporter,
    onCancel: () -> Unit,
    onComplete: (ImportResult) -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var total by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(true) }
    var cancelled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (selectedThreadIds.isEmpty()) {
            error = "No threads selected. Go back and choose at least one bank thread."
            running = false
            return@LaunchedEffect
        }
        thread {
            try {
                val messages = selectedThreadIds.flatMap { SmsReader.readMessages(context, it) }
                Handler(Looper.getMainLooper()).post {
                    total = messages.size
                    progress = ImportProgress(0, messages.size, 0, 0, 0)
                }
                val result = importer.import(messages) { p ->
                    Handler(Looper.getMainLooper()).post { progress = p }
                }
                Handler(Looper.getMainLooper()).post {
                    running = false
                    if (!cancelled) onComplete(result)
                }
            } catch (t: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    error = t.message ?: "Scan failed."
                    running = false
                }
            }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            PageHeader(
                title = "Reading your bank messages",
                subtitle = "Step 4 of 7 · on-device scan"
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "Scanning selected threads",
                style = MaterialTheme.typography.headlineMedium,
                color = Ledger.Ink
            )
            Text(
                "Balance lines, OTPs and duplicates are skipped. Nothing leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Progress",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ledger.Ink
                        )
                        Text(
                            if (total == 0) "Preparing" else "${progress?.processed ?: 0} / $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ledger.Primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = if (total == 0) 0f else {
                            ((progress?.processed ?: 0).toFloat() / total).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Ledger.Primary,
                        trackColor = Ledger.SurfaceAlt
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScanStat("Transactions", progress?.parsed ?: 0)
                        ScanStat("Skipped", progress?.skipped ?: 0)
                        ScanStat("Unparsed", progress?.unparsed ?: 0)
                    }
                }
            }
            if (error != null) {
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ledger.Danger,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (running) {
                SecondaryButton("Cancel scan", {
                    cancelled = true
                    onCancel()
                })
            } else {
                PrimaryButton("Back to threads", onCancel)
            }
            Text(
                "Your messages stay on this phone. The scan only reads the threads you selected.",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RowScope.ScanStat(label: String, value: Int) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = Ledger.SurfaceAlt
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
            Text(
                "%,d".format(value),
                style = MaterialTheme.typography.titleMedium,
                color = Ledger.Ink,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ReviewGroupsScreen(
    repository: TransactionRepository,
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onReviewLater: () -> Unit
) {
    var instruments by remember { mutableStateOf<List<DetectedInstrument>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        thread {
            val loaded = repository.detectedInstruments()
            Handler(Looper.getMainLooper()).post {
                instruments = loaded
                loading = false
            }
        }
    }

    val pending = instruments.filter { !it.verified }
    val groups = pending.groupBy { it.address }
        .toList()
        .sortedByDescending { (_, rows) -> rows.size }
    val firstAddress = groups.firstOrNull()?.first

    ScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            PageHeader(
                title = "Review instruments",
                subtitle = if (groups.isEmpty()) "All values confirmed" else "${pending.size} values need review",
                onBack = onBack
            )
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 34.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (groups.isEmpty()) {
                LedgerCard(modifier = Modifier.padding(top = 22.dp)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Ledger.Success, modifier = Modifier.size(34.dp))
                        Text(
                            "Everything is confirmed",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ledger.Ink,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            "You can change any instrument later in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ledger.Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    "Which numbers are cards,\nand which are accounts?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ledger.Ink,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    "Grouped by sender. You can pause and finish this later from Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.first }) { (address, rows) ->
                        InstrumentGroupRow(
                            address = address,
                            rows = rows,
                            isFirst = address == firstAddress,
                            onClick = { onReview(address) }
                        )
                    }
                }
                PrimaryButton(
                    text = if (firstAddress == null) "Continue" else "Review ${firstAddress.displayName()} first",
                    onClick = { firstAddress?.let(onReview) },
                    enabled = firstAddress != null
                )
                LedgerTextButton(
                    text = "Review later",
                    onClick = onReviewLater,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Only unverified values are shown. You can return from Home or Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun String.displayName(): String =
    if (length <= 12) this else take(10) + "…"

@Composable
private fun InstrumentGroupRow(
    address: String,
    rows: List<DetectedInstrument>,
    isFirst: Boolean,
    onClick: () -> Unit
) {
    val cards = rows.count { it.channel == TransactionChannel.CARD }
    val accounts = rows.count { it.channel == TransactionChannel.ACCOUNT }
    LedgerCard(modifier = Modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Avatar(address.take(2), LedgerChartColors[(address.hashCode() and Int.MAX_VALUE) % LedgerChartColors.size])
            Column(modifier = Modifier.weight(1f)) {
                Text(address, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                Text(
                    "${rows.size} values · $cards cards · $accounts accounts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted
                )
            }
            if (isFirst) {
                Pill("Start here", PillTone.Primary)
            } else {
                Pill("Review", PillTone.Neutral)
            }
        }
    }
}

@Composable
private fun ReviewConfirmScreen(
    address: String,
    repository: TransactionRepository,
    importer: TransactionImporter,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onSaved: (String?) -> Unit
) {
    var instruments by remember { mutableStateOf<List<DetectedInstrument>>(emptyList()) }
    var choices by remember { mutableStateOf<Map<String, TransactionChannel?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun key(instrument: DetectedInstrument) =
        TransactionParser.instrumentKey(instrument.address, instrument.digits)

    LaunchedEffect(address) {
        loading = true
        thread {
            val all = repository.detectedInstruments().filter { it.address == address }
            Handler(Looper.getMainLooper()).post {
                instruments = all
                choices = all.associate { key(it) to it.channel }
                loading = false
            }
        }
    }

    fun save() {
        saving = true
        error = null
        val toSave = instruments.map { instrument ->
            val choice = choices[key(instrument)]
            instrument.copy(
                channel = choice ?: instrument.channel,
                ignored = choice == null
            )
        }
        thread {
            try {
                repository.saveInstruments(toSave)
                importer.rebuildFromStored()
                val remaining = repository.pendingInstruments()
                    .groupBy { it.address }
                    .keys
                    .firstOrNull { it != address }
                    ?: repository.pendingInstruments().firstOrNull()?.address
                Handler(Looper.getMainLooper()).post {
                    saving = false
                    onSaved(remaining)
                }
            } catch (t: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    saving = false
                    error = t.message ?: "Could not save instruments."
                }
            }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            PageHeader(
                title = "Confirm $address numbers",
                subtitle = "Review every value from this sender",
                onBack = onBack
            )
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 34.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else {
                Text(
                    "Which numbers are cards,\nand which are accounts?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ledger.Ink,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    "The suggestion comes from the SMS wording. You can change any value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(instruments, key = { key(it) }) { instrument ->
                        InstrumentChoiceCard(
                            instrument = instrument,
                            selected = choices[key(instrument)],
                            onSelect = { channel ->
                                choices = choices + (key(instrument) to channel)
                            }
                        )
                    }
                }
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = Ledger.Danger)
                }
                if (saving) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ledger.Primary)
                        Text("Applying this to stored SMS…", style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
                    }
                } else {
                    PrimaryButton("Save and review next", ::save)
                    LedgerTextButton(
                        text = "Review later",
                        onClick = onPause,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        "Saving rebuilds parsed transactions from stored SMS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InstrumentChoiceCard(
    instrument: DetectedInstrument,
    selected: TransactionChannel?,
    onSelect: (TransactionChannel?) -> Unit
) {
    LedgerCard {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(instrument.digits, style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                        ChannelIcon(instrument.channel)
                    }
                    Text(
                        "${instrument.messageCount} message(s)" + if (instrument.verified) " · confirmed" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.Muted
                    )
                }
                if (instrument.verified) {
                    Pill("Confirmed", PillTone.Success)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill(
                    label = "Card",
                    selected = selected == TransactionChannel.CARD,
                    onClick = { onSelect(TransactionChannel.CARD) },
                    modifier = Modifier.weight(1f)
                )
                ChoicePill(
                    label = "Account",
                    selected = selected == TransactionChannel.ACCOUNT,
                    onClick = { onSelect(TransactionChannel.ACCOUNT) },
                    modifier = Modifier.weight(1f)
                )
                ChoicePill(
                    label = "Ignore",
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
        color = if (selected) Ledger.PrimarySoft else Ledger.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Ledger.Primary else Ledger.Outline
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Ledger.Primary else Ledger.Ink
            )
        }
    }
}

@Composable
private fun CompleteScreen(
    repository: TransactionRepository,
    importResult: ImportResult?,
    onHome: () -> Unit,
    onReviewPending: () -> Unit
) {
    var transactionCount by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf(0) }
    var totalInstruments by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        thread {
            val detected = repository.detectedInstruments()
            val count = repository.count()
            Handler(Looper.getMainLooper()).post {
                transactionCount = count
                totalInstruments = detected.size
                pending = detected.count { !it.verified }
            }
        }
    }

    val confirmed = (totalInstruments - pending).coerceAtLeast(0)
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.size(92.dp).align(Alignment.CenterHorizontally),
                shape = CircleShape,
                color = Ledger.SurfaceSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        color = Ledger.Primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Ledger.OnPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            Text(
                "You're all set.",
                style = MaterialTheme.typography.displayMedium,
                color = Ledger.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            Text(
                "${"%,d".format(transactionCount)} transactions from " +
                    "${"%,d".format(importResult?.rawInserted ?: 0)} messages are ready on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ledger.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 18.dp, end = 18.dp)
            )
            Spacer(Modifier.height(22.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompleteStat("Transactions", "%,d".format(transactionCount), Modifier.weight(1f))
                        CompleteStat("Skipped", "%,d".format(importResult?.skipped ?: 0), Modifier.weight(1f))
                    }
                    LedgerDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompleteStat("Confirmed", "$confirmed / $totalInstruments", Modifier.weight(1f))
                        CompleteStat("Unparsed", "%,d".format(importResult?.unparsed ?: 0), Modifier.weight(1f))
                    }
                }
            }
            if (pending > 0) {
                ReviewBanner(
                    title = "$pending card/account values need review",
                    body = "You can review them now or later from Home.",
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Go to Home", onHome)
            if (pending > 0) {
                SecondaryButton(
                    text = "Review $pending values",
                    onClick = onReviewPending,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "You can change instruments, categories and exports later in Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CompleteStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ledger.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
