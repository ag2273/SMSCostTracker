package com.example.smscosttracker.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.ExchangeRates
import com.example.smscosttracker.analysis.RateSource
import com.example.smscosttracker.analysis.RateVersion
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.ExchangeRateRepository
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.rates.ExchangeRateSync
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerTextButton
import com.example.smscosttracker.ui.components.LedgerTextField
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SelectRow
import com.example.smscosttracker.ui.theme.Ledger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

private val UPDATED = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault())

@Composable
fun ExchangeRatesScreen(
    preferences: OnboardingPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { ExchangeRateRepository(AppDatabase.get(context)) }
    var rates by remember { mutableStateOf<Map<String, RateVersion>>(emptyMap()) }
    var reloadKey by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var autoUpdate by remember { mutableStateOf(preferences.autoUpdateRates) }

    LaunchedEffect(reloadKey) {
        thread {
            val loaded = repository.converter().current()
            Handler(Looper.getMainLooper()).post { rates = loaded }
        }
    }

    fun save(currency: String) {
        val value = draft.trim().replace(',', '.').toDoubleOrNull()
        if (value == null || value <= 0.0) {
            status = "Enter a number above zero, for example 3.75."
            return
        }
        editing = null
        thread {
            val saved = repository.setManual(currency, value)
            Handler(Looper.getMainLooper()).post {
                status = if (saved) "$currency saved. It applies to transactions from now on." else "$currency is unchanged."
                reloadKey++
            }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            PageHeader(title = "Exchange rates", subtitle = "Units for 1 US dollar", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            LedgerCard {
                Column {
                    ExchangeRates.SUPPORTED.filter { it != "USD" }.forEachIndexed { index, currency ->
                        if (index > 0) LedgerDivider()
                        val version = rates[currency]
                        if (editing == currency) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("1 USD in $currency", style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                                Spacer(Modifier.height(8.dp))
                                LedgerTextField(value = draft, onValueChange = { draft = it }, placeholder = "e.g. 3.75")
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    LedgerTextButton("Cancel", onClick = { editing = null })
                                    LedgerTextButton("Save", onClick = { save(currency) })
                                }
                            }
                        } else {
                            RateRow(currency, version) {
                                editing = currency
                                draft = version?.let { ExchangeRates.format(it.unitsPerUsd) }.orEmpty()
                                status = null
                            }
                        }
                    }
                }
            }
            Text(
                "Tap a rate to set it yourself. A new rate applies to transactions from the moment it " +
                    "changes; earlier transactions keep the rate they had.",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (updating) "Updating…" else "Update from internet",
                enabled = !updating,
                onClick = {
                    updating = true
                    status = null
                    thread {
                        val result = runCatching { ExchangeRateSync.refresh(context, replaceManual = true) }
                            .getOrDefault(ExchangeRateSync.Result.Unavailable)
                        Handler(Looper.getMainLooper()).post {
                            updating = false
                            status = when (result) {
                                is ExchangeRateSync.Result.Updated ->
                                    if (result.changed == 0) "Rates are up to date." else "${result.changed} rate(s) updated."
                                ExchangeRateSync.Result.Unavailable -> "Couldn't download rates. Check the connection and try again."
                            }
                            reloadKey++
                        }
                    }
                }
            )
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            LedgerCard {
                SelectRow(
                    label = "Update daily",
                    value = "Downloads rates once a day. Rates you set yourself are kept.",
                    icon = Icons.Outlined.Sync,
                    trailing = if (autoUpdate) "On" else "Off",
                    onClick = {
                        autoUpdate = !autoUpdate
                        preferences.autoUpdateRates = autoUpdate
                        ExchangeRateSync.schedule(context)
                    }
                )
            }
            Text(
                "Only the public rate list is downloaded. Nothing about you, your SMS or your spending is sent.",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RateRow(currency: String, version: RateVersion?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(currency, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(
                when {
                    version == null -> "No rate yet"
                    version.effectiveFrom <= 0L -> "Built-in starting rate"
                    else -> "Since ${UPDATED.format(Instant.ofEpochMilli(version.effectiveFrom))}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
        }
        version?.let {
            Pill(
                text = it.source.label,
                tone = if (it.source == RateSource.MANUAL) PillTone.Accent else PillTone.Neutral
            )
        }
        Text(
            version?.let { ExchangeRates.format(it.unitsPerUsd) } ?: "—",
            style = MaterialTheme.typography.titleSmall,
            color = Ledger.Ink
        )
    }
}
