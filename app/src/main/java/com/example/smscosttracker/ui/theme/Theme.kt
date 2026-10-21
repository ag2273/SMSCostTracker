package com.example.smscosttracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LedgerLightColors = lightColorScheme(
    primary = Ledger.Primary,
    onPrimary = Ledger.OnPrimary,
    primaryContainer = Ledger.PrimarySoft,
    onPrimaryContainer = Ledger.Primary,
    secondary = Ledger.Accent,
    onSecondary = Ledger.OnPrimary,
    secondaryContainer = Ledger.AccentSoft,
    onSecondaryContainer = Ledger.Accent,
    tertiary = Ledger.Success,
    onTertiary = Ledger.OnPrimary,
    background = Ledger.Background,
    onBackground = Ledger.Ink,
    surface = Ledger.Surface,
    onSurface = Ledger.Ink,
    surfaceVariant = Ledger.SurfaceAlt,
    onSurfaceVariant = Ledger.Muted,
    outline = Ledger.Outline,
    outlineVariant = Ledger.Outline,
    error = Ledger.Danger,
    onError = Ledger.OnPrimary,
    errorContainer = Ledger.DangerSoft,
    onErrorContainer = Ledger.Danger
)

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun SmsCostTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LedgerLightColors,
        typography = LedgerTypography,
        shapes = LedgerShapes,
        content = content
    )
}
