package com.example.smscosttracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.ui.theme.Ledger
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

enum class PillTone { Neutral, Primary, Accent, Success, Danger }

@Composable
fun Pill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> Ledger.SurfaceAlt to Ledger.Muted
        PillTone.Primary -> Ledger.PrimarySoft to Ledger.Primary
        PillTone.Accent -> Ledger.AccentSoft to Ledger.Accent
        PillTone.Success -> Ledger.SurfaceSoft to Ledger.Success
        PillTone.Danger -> Ledger.DangerSoft to Ledger.Danger
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(99.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun LedgerChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(99.dp),
        color = if (selected) Ledger.Primary else Ledger.Surface,
        border = BorderStroke(1.dp, if (selected) Ledger.Primary else Ledger.Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Ledger.OnPrimary else Ledger.Ink,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Ledger.Surface,
        border = BorderStroke(1.dp, Ledger.Outline)
    ) {
        Column(content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ledger.Primary,
            contentColor = Ledger.OnPrimary,
            disabledContainerColor = Ledger.Primary.copy(alpha = 0.35f),
            disabledContentColor = Ledger.OnPrimary.copy(alpha = 0.75f)
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Ledger.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Ledger.Ink,
            disabledContentColor = Ledger.Muted
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LedgerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ledger.Primary
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Ledger.Ink)
            }
            Spacer(Modifier.size(2.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Ledger.Ink)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun SectionRow(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Ledger.Ink
        )
        if (action != null) {
            LedgerTextButton(text = action, onClick = { onAction?.invoke() })
        }
    }
}

@Composable
fun LedgerDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Ledger.Outline.copy(alpha = 0.65f))
    )
}

@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ledger.Background)
            .statusBarsPadding()
    ) {
        content()
    }
}

@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = Ledger.Muted) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ledger.Primary,
            unfocusedBorderColor = Ledger.Outline,
            focusedContainerColor = Ledger.Surface,
            unfocusedContainerColor = Ledger.Surface,
            cursorColor = Ledger.Primary,
            focusedTextColor = Ledger.Ink,
            unfocusedTextColor = Ledger.Ink
        )
    )
}

@Composable
fun Avatar(
    letter: String,
    color: Color,
    size: Int = 34,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.take(1).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChannelIcon(channel: TransactionChannel, modifier: Modifier = Modifier, size: Int = 13) {
    Icon(
        imageVector = if (channel == TransactionChannel.CARD) {
            Icons.Outlined.CreditCard
        } else {
            Icons.Outlined.AccountBalance
        },
        contentDescription = null,
        modifier = modifier.size(size.dp),
        tint = Ledger.Muted
    )
}

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ledger.SurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Ledger.Ink,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ReviewBanner(
    title: String,
    body: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        color = Ledger.AccentSoft,
        border = BorderStroke(1.dp, Ledger.Accent.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Ledger.Accent,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                }
            }
            if (onClick != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Ledger.Accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            IconTile(icon)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(value, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = Ledger.Primary)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Ledger.Muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Ledger.PrimarySoft else Ledger.Surface,
        border = BorderStroke(1.dp, if (selected) Ledger.Primary else Ledger.Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Ledger.Primary else Ledger.Ink
            )
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Ledger.Primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DropdownChip(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.clickable { expanded = true },
            shape = RoundedCornerShape(99.dp),
            color = Ledger.Surface,
            border = BorderStroke(1.dp, Ledger.Outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Ledger.Ink)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = Ledger.Muted, modifier = Modifier.size(14.dp))
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 12.dp)
                .size(width = 34.dp, height = 4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Ledger.Outline)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Ledger.Ink)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
                }
            }
            Surface(
                modifier = Modifier.size(36.dp).clickable(onClick = onClose),
                shape = CircleShape,
                color = Ledger.SurfaceAlt
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Ledger.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = Ledger.SurfaceAlt, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Ledger.Muted, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Ledger.Ink,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = Ledger.Muted,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun LedgerBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val items = listOf(
        Triple("Home", Icons.Outlined.Home, "home"),
        Triple("Activity", Icons.Outlined.ReceiptLong, "activity"),
        Triple("Insights", Icons.Outlined.Insights, "insights"),
        Triple("Settings", Icons.Outlined.Settings, "settings")
    )
    NavigationBar(
        containerColor = Ledger.Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        items.forEachIndexed { index, (label, icon, _) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ledger.Primary,
                    selectedTextColor = Ledger.Primary,
                    indicatorColor = Ledger.PrimarySoft,
                    unselectedIconColor = Ledger.Muted,
                    unselectedTextColor = Ledger.Muted
                )
            )
        }
    }
}

fun formatMoney(amountMinor: Long, currency: String): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val code = currency.ifBlank { "?" }
    return "$code ${formatter.format(amountMinor / 100.0)}"
}

fun formatExpenseMoney(amountMinor: Long, currency: String): String =
    "−" + formatMoney(amountMinor, currency)

fun formatCompactMoney(amountMinor: Long, currency: String): String {
    val value = amountMinor / 100.0
    val text = if (value >= 1000) {
        String.format(Locale.US, "%.1fk", value / 1000.0)
    } else {
        String.format(Locale.US, "%.0f", value)
    }
    return "$currency $text"
}

fun currencySymbol(currency: String): String = when (currency.uppercase(Locale.ROOT)) {
    "SAR" -> "SAR"
    "EGP" -> "EGP"
    "AED" -> "AED"
    "USD" -> "USD"
    else -> currency
}

fun channelLabel(channel: TransactionChannel): String = when (channel) {
    TransactionChannel.CARD -> "Card"
    TransactionChannel.ACCOUNT -> "Account"
    TransactionChannel.OTHER -> "Other"
}
