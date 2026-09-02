package com.example.callruleblocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AllowColor = Color(0xFF35D59A)
val BlockColor = Color(0xFFFF6B72)

val PremiumPurpleTop = Color(0xFF090611)
val PremiumPurpleMid = Color(0xFF171022)
val PremiumPurpleBottom = Color(0xFF2A1439)
val PremiumCard = Color(0xFF17121F)
val PremiumCardPressed = Color(0xFF2A1D39)
val PremiumAccent = Color(0xFFA98BFF)
val PremiumAccentSoft = Color(0xFFD2C5FF)
val PremiumGreen = Color(0xFF35D59A)
val PremiumBlue = Color(0xFF5B8CFF)

private val PremiumDarkColors = darkColorScheme(
    primary = PremiumAccent,
    onPrimary = Color(0xFF160C28),
    primaryContainer = Color(0xFF35234C),
    onPrimaryContainer = Color(0xFFF1E9FF),
    secondary = PremiumAccentSoft,
    onSecondary = Color(0xFF1C112B),
    secondaryContainer = Color(0xFF30253D),
    onSecondaryContainer = Color(0xFFEDE3FA),
    tertiary = PremiumGreen,
    onTertiary = Color(0xFF002116),
    background = PremiumPurpleTop,
    onBackground = Color(0xFFF8F3FF),
    surface = PremiumCard,
    onSurface = Color(0xFFF8F3FF),
    surfaceVariant = Color(0xFF241C2D),
    onSurfaceVariant = Color(0xFFCEC3D8),
    outline = Color(0xFF776B82),
    outlineVariant = Color(0xFF3C3245),
    error = Color(0xFFFF6B72),
    onError = Color.White,
    errorContainer = Color(0xFF4D171D),
    onErrorContainer = Color(0xFFFFDADC)
)

@Composable
fun CallRuleBlockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PremiumDarkColors, content = content)
}
