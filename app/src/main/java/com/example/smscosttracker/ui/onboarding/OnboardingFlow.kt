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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.smscosttracker.ui.components.LedgerTextField
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
            onBack = { go(OnboardingStage.WELCOME) },
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
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            content = content
        )
    }
}

@Composable
private fun OnboardingHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                shape = RoundedCornerShape(12.dp),
                color = Ledger.Surface,
                border = BorderStroke(1.dp, Ledger.Outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Ledger.Ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ledger.Ink)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.padding(top = 2.dp)) { badge() }
        }
    }
}

@Composable
private fun StepProgress(current: Int, total: Int = 4) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (index < current) Ledger.Primary else Ledger.Outline)
            )
        }
    }
}

@Composable
private fun HeroPanel(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(24.dp),
        color = Ledger.Surface,
        border = BorderStroke(1.dp, Ledger.Outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(height * 0.82f)
                    .clip(CircleShape)
                    .background(Ledger.Primary.copy(alpha = 0.07f))
            )
            Box(
                modifier = Modifier
                    .size(height * 0.54f)
                    .clip(CircleShape)
                    .background(Ledger.Accent.copy(alpha = 0.08f))
            )
            content()
        }
    }
}

@Composable
private fun NoticeBanner(
    title: String,
    body: String,
    icon: ImageVector = Icons.Outlined.Info,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Ledger.AccentSoft,
        border = BorderStroke(1.dp, Ledger.Accent.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ledger.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun BrandCheckbox(checked: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(7.dp),
        color = if (checked) Ledger.Primary else Ledger.Surface,
        border = BorderStroke(1.5.dp, if (checked) Ledger.Primary else Ledger.Outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Ledger.OnPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    OnboardingPage {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(11.dp),
                color = Ledger.Primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "S",
                        color = Ledger.OnPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "SMSCostTracker",
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                modifier = Modifier.weight(1f)
            )
            Pill("v${version ?: "0.2"}")
        }
        Spacer(Modifier.height(16.dp))
        HeroPanel(210.dp) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = Ledger.Primary,
                    modifier = Modifier.size(84.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 12.dp, y = 8.dp)
                        .size(44.dp),
                    shape = CircleShape,
                    color = Ledger.Accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = Ledger.OnPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Your spending,\nfrom your SMS.",
            style = MaterialTheme.typography.displayLarge,
            color = Ledger.Ink
        )
        Text(
            "Turn bank messages into clear transactions and charts. Parsing happens entirely on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ledger.Muted,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrivacyPoint(
                Icons.Outlined.Shield,
                "On-device only",
                "No server, no account, no SMS upload."
            )
            PrivacyPoint(
                Icons.Outlined.AccountBalance,
                "No bank login",
                "We read only the bank threads you pick."
            )
            PrivacyPoint(
                Icons.Outlined.Lock,
                "You stay in control",
                "Review every card and account before import."
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Get started", onStart)
        Text(
            "By continuing you agree to read only the threads you select. You can revoke SMS access in Android Settings at any time.",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 8.dp, end = 8.dp)
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PrivacyPoint(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = Ledger.Primary.copy(alpha = 0.10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Ledger.Primary,
                    modifier = Modifier.size(17.dp)
                )
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
    onBack: () -> Unit,
    onRequest: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    OnboardingPage {
        OnboardingHeader(
            title = "Allow SMS access",
            subtitle = "Step 1 of 4 · Permission",
            onBack = onBack,
            badge = { Pill("Required", PillTone.Primary) }
        )
        Spacer(Modifier.height(12.dp))
        StepProgress(current = 1)
        Spacer(Modifier.height(14.dp))
        HeroPanel(210.dp) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Chat,
                    contentDescription = null,
                    tint = Ledger.Primary,
                    modifier = Modifier.size(82.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 6.dp)
                        .size(46.dp),
                    shape = CircleShape,
                    color = Ledger.Primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Ledger.OnPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
        Text(
            "We need to read bank messages to build your transactions.",
            style = MaterialTheme.typography.headlineMedium,
            color = Ledger.Ink,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Android will show a system prompt. SMSCostTracker only reads the conversations you select in the next step.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ledger.Muted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(16.dp))
        LedgerCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                PrivacyPoint(
                    Icons.Outlined.Chat,
                    "Read SMS",
                    "Needed to find purchase, transfer and bill messages."
                )
                PrivacyPoint(
                    Icons.Outlined.Shield,
                    "No internet required",
                    "Parsing and classification run locally."
                )
                PrivacyPoint(
                    Icons.Outlined.Settings,
                    "Revoke anytime",
                    "Android Settings → Apps → Permissions."
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (granted) {
            PrimaryButton("Continue", onContinue)
        } else {
            PrimaryButton("Allow SMS access", onRequest)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Not now", onSkip)
            if (!granted) {
                LedgerTextButton(
                    text = "Open app settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
        Text(
            "We never send SMS or share message text.",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
    var search by remember { mutableStateOf("") }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            OnboardingHeader(
                title = "Choose bank threads",
                subtitle = "Step 2 of 4 · Select conversations",
                onBack = onBack
            )
            Spacer(Modifier.height(12.dp))
            StepProgress(current = 2)
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (error != null) {
                Spacer(Modifier.height(24.dp))
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Ledger.Danger)
            } else {
                val filtered = remember(threads, search) {
                    if (search.isBlank()) threads
                    else threads.filter { it.address.contains(search, ignoreCase = true) }
                }
                val selectedMessages = threads
                    .filter { it.threadId in selected }
                    .sumOf { it.messageCount }
                val allSelected = filtered.isNotEmpty() &&
                    filtered.all { it.threadId in selected }

                Spacer(Modifier.height(14.dp))
                LedgerTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search sender or number",
                    leadingIcon = Icons.Outlined.Search
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Suggested bank threads",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ledger.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    LedgerTextButton(
                        text = if (allSelected) "Clear all" else "Select all",
                        onClick = {
                            val ids = filtered.map { it.threadId }.toSet()
                            selected = if (allSelected) selected - ids else selected + ids
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                LedgerCard(modifier = Modifier.weight(1f)) {
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No threads match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ledger.Muted
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(filtered, key = { _, thread -> thread.threadId }) { index, thread ->
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
                                if (index != filtered.lastIndex) LedgerDivider()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                NoticeBanner(
                    title = "Only selected threads are scanned",
                    body = "You can add or remove threads later in Settings → Banks & threads."
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${"%,d".format(selectedMessages)} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted
                    )
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Scan selected",
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

private val lastSeenFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun lastSeenLabel(millis: Long): String {
    if (millis <= 0L) return "No messages"
    return "Last " + lastSeenFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

@Composable
private fun ThreadChoiceRow(
    thread: SmsThread,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BrandCheckbox(checked = checked, onToggle = onToggle)
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = Ledger.SurfaceAlt
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    thread.address.take(2).uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                thread.address,
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${"%,d".format(thread.messageCount)} messages · ${lastSeenLabel(thread.lastDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.Muted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ScanStatTile(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Ledger.Surface,
        border = BorderStroke(1.dp, Ledger.Outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = Ledger.Ink,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(hint, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
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

    val processed = progress?.processed ?: 0
    val percent = if (total == 0) 0 else (processed * 100 / total).coerceIn(0, 100)
    val current = progress

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
        ) {
            OnboardingHeader(
                title = "Scanning",
                subtitle = "Step 3 of 4 · On-device",
                onBack = onCancel,
                badge = { Pill("Local only", PillTone.Primary) }
            )
            Spacer(Modifier.height(12.dp))
            StepProgress(current = 3)
            Spacer(Modifier.height(14.dp))
            Text(
                "Reading your bank messages",
                style = MaterialTheme.typography.headlineMedium,
                color = Ledger.Ink
            )
            Text(
                "${"%,d".format(processed)} of ${"%,d".format(total)} messages",
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Overall progress",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ledger.Ink
                        )
                        Text(
                            if (total == 0) "Preparing" else "$percent%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ledger.Primary
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = if (total == 0) 0f else processed.toFloat() / total,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Ledger.Primary,
                        trackColor = Ledger.SurfaceAlt
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanStatTile(
                            "Transactions",
                            "%,d".format(progress?.parsed ?: 0),
                            "from ${"%,d".format(total)} messages",
                            Modifier.weight(1f)
                        )
                        ScanStatTile(
                            "Skipped",
                            "%,d".format(progress?.skipped ?: 0),
                            "balances, OTPs, duplicates",
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanStatTile(
                            "Unparsed",
                            "%,d".format(progress?.unparsed ?: 0),
                            "you can review later",
                            Modifier.weight(1f)
                        )
                        ScanStatTile(
                            "Processed",
                            "%,d".format(processed),
                            "of ${"%,d".format(total)} messages",
                            Modifier.weight(1f)
                        )
                    }
                }
            }
            if (current != null && current.currentBody.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Currently parsing",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ledger.Ink
                )
                Spacer(Modifier.height(8.dp))
                LedgerCard {
                    Column(modifier = Modifier.padding(13.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Avatar(current.currentAddress, Ledger.Primary, size = 32)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    current.currentAddress,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ledger.Ink
                                )
                                Text(
                                    "Message ${"%,d".format(processed)} of ${"%,d".format(total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ledger.Muted
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Ledger.SurfaceAlt,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                current.currentBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = Ledger.Ink,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ledger.Danger
                )
            }
            Spacer(Modifier.weight(1f))
            NoticeBanner(
                title = "Your messages stay on this phone",
                body = "Keep this screen open while the scan finishes. Nothing is uploaded.",
                icon = Icons.Outlined.Shield
            )
            Spacer(Modifier.height(10.dp))
            if (running) {
                SecondaryButton("Cancel scan", {
                    cancelled = true
                    onCancel()
                })
            } else {
                PrimaryButton("Back to threads", onCancel)
            }
            Spacer(Modifier.height(10.dp))
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

    LaunchedEffect(Unit) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
        ) {
            OnboardingHeader(
                title = "Review instruments",
                subtitle = when {
                    loading -> "Loading values…"
                    groups.isEmpty() -> "All values confirmed"
                    else -> "${pending.size} values found in ${groups.size} threads"
                },
                onBack = onBack,
                badge = { Pill("1 of 2") }
            )
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (groups.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                LedgerCard {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Ledger.Success,
                            modifier = Modifier.size(34.dp)
                        )
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
                Spacer(Modifier.weight(1f))
                PrimaryButton("Continue", onReviewLater)
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Which numbers are cards,\nand which are accounts?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ledger.Ink
                )
                Text(
                    "We grouped the numbers by sender and suggested a type from the SMS wording. You can change any of them later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
                LedgerCard(modifier = Modifier.weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(groups, key = { _, group -> group.first }) { index, group ->
                            InstrumentGroupRow(
                                address = group.first,
                                rows = group.second,
                                isFirst = group.first == firstAddress,
                                onClick = { onReview(group.first) }
                            )
                            if (index != groups.lastIndex) LedgerDivider()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                NoticeBanner(
                    title = "${pending.size} values need review",
                    body = "Transactions still import. Unverified values are tagged until you confirm them.",
                    icon = Icons.Outlined.WarningAmber
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = if (firstAddress == null) "Continue" else "Review ${firstAddress.displayName()} first",
                    onClick = { firstAddress?.let(onReview) ?: onReviewLater() },
                    enabled = firstAddress != null
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Review later", onReviewLater)
                Text(
                    "Only unverified values are shown. You can pause and return from Home.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(9.dp),
            color = Ledger.SurfaceAlt
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    address.take(2).uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(address, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(
                "${rows.size} values · $cards cards · $accounts accounts",
                style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun SegmentedChoice(
    selected: TransactionChannel?,
    onSelect: (TransactionChannel?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ledger.SurfaceAlt)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(
            TransactionChannel.CARD to "Card",
            TransactionChannel.ACCOUNT to "Account",
            null to "Ignore"
        ).forEach { (value, label) ->
            val active = selected == value
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(9.dp),
                color = if (active) Ledger.Surface else Color.Transparent,
                border = if (active) BorderStroke(1.dp, Ledger.Outline) else null
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Ledger.Ink else Ledger.Muted
                    )
                }
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
    var nextAddress by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun key(instrument: DetectedInstrument) =
        TransactionParser.instrumentKey(instrument.address, instrument.digits)

    LaunchedEffect(address) {
        loading = true
        thread {
            val all = repository.detectedInstruments().filter { it.address == address }
            val next = repository.pendingInstruments()
                .firstOrNull { it.address != address }
                ?.address
            Handler(Looper.getMainLooper()).post {
                instruments = all
                nextAddress = next
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

    val saveLabel = when {
        nextAddress != null -> "Save ${instruments.size} and review ${nextAddress!!.displayName()}"
        else -> "Save ${instruments.size} values"
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
        ) {
            OnboardingHeader(
                title = "Confirm ${address.displayName()} numbers",
                subtitle = if (loading) {
                    "Loading values…"
                } else {
                    "${instruments.size} values · suggested from SMS wording"
                },
                onBack = onBack,
                badge = { Pill("2 of 2") }
            )
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else {
                Spacer(Modifier.height(14.dp))
                LedgerCard(modifier = Modifier.weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(instruments, key = { _, instrument -> key(instrument) }) { index, instrument ->
                            InstrumentChoiceRow(
                                instrument = instrument,
                                selected = choices[key(instrument)],
                                onSelect = { channel ->
                                    choices = choices + (key(instrument) to channel)
                                }
                            )
                            if (index != instruments.lastIndex) LedgerDivider()
                        }
                    }
                }
                LedgerTextButton(
                    text = "Mark all ${address.displayName()} values as cards",
                    onClick = {
                        choices = choices.mapValues { TransactionChannel.CARD }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                NoticeBanner(
                    title = "Why we suggest a type",
                    body = "“card ending 1214” means Card. “from account 001” means Account. You can override every value."
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = Ledger.Danger)
                }
                Spacer(Modifier.height(10.dp))
                if (saving) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Ledger.Primary
                        )
                        Text(
                            "Applying this to stored SMS…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ledger.Muted
                        )
                    }
                } else {
                    PrimaryButton(saveLabel, ::save)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Save and review later", onPause)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InstrumentChoiceRow(
    instrument: DetectedInstrument,
    selected: TransactionChannel?,
    onSelect: (TransactionChannel?) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                instrument.digits,
                style = MaterialTheme.typography.titleMedium,
                color = Ledger.Ink
            )
            Spacer(Modifier.width(8.dp))
            Pill("${instrument.messageCount} txns")
            Spacer(Modifier.weight(1f))
            if (instrument.verified) {
                Pill("Confirmed", PillTone.Success)
            } else {
                val suggested = when (instrument.channel) {
                    TransactionChannel.CARD -> "Card suggested"
                    TransactionChannel.ACCOUNT -> "Account suggested"
                    else -> "Suggested"
                }
                Pill(
                    suggested,
                    if (instrument.channel == TransactionChannel.CARD) {
                        PillTone.Primary
                    } else {
                        PillTone.Neutral
                    }
                )
            }
        }
        Text(
            when {
                instrument.verified -> "Confirmed earlier"
                instrument.channel == TransactionChannel.CARD -> "Seen in card purchase messages"
                instrument.channel == TransactionChannel.ACCOUNT -> "Seen in transfer or bill messages"
                else -> "Suggested from SMS wording"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(selected = selected, onSelect = onSelect)
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
    OnboardingPage {
        OnboardingHeader(
            title = "Import complete",
            subtitle = "First run · finished",
            badge = { Pill("✓ Ready", PillTone.Success) }
        )
        Spacer(Modifier.height(16.dp))
        HeroPanel(200.dp) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Ledger.Primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Ledger.OnPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Text(
            "You're all set.",
            style = MaterialTheme.typography.headlineLarge,
            color = Ledger.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
        )
        Text(
            "${"%,d".format(transactionCount)} transactions from " +
                "${"%,d".format(importResult?.rawInserted ?: 0)} messages are ready on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 14.dp, end = 14.dp)
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompleteStat(
                label = "Transactions",
                value = "%,d".format(transactionCount),
                hint = "Jan – Oct 2026",
                modifier = Modifier.weight(1f)
            )
            CompleteStat(
                label = "Skipped",
                value = "%,d".format(importResult?.skipped ?: 0),
                hint = "balances, OTPs, duplicates",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompleteStat(
                label = "Instruments",
                value = "$confirmed / $totalInstruments",
                hint = "confirmed",
                modifier = Modifier.weight(1f)
            )
            CompleteStat(
                label = "Unparsed",
                value = "%,d".format(importResult?.unparsed ?: 0),
                hint = "messages to review",
                modifier = Modifier.weight(1f)
            )
        }
        if (pending > 0) {
            Spacer(Modifier.height(14.dp))
            ReviewBanner(
                title = "$pending card/account values need review",
                body = "You can review them now or later from Home."
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Go to Home", onHome)
        if (pending > 0) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Review $pending values",
                onClick = onReviewPending
            )
        }
        Text(
            "You can change instruments, categories and exports later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CompleteStat(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    LedgerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                label.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = Ledger.Ink,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(hint, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        }
    }
}
