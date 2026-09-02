package com.example.callruleblocker.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREFS_NAME = "appearance_prefs_v1"

@Immutable
data class AppearanceSettings(
    val accentColor: Color = Color(0xFFA98BFF), // Premium Purple as default
    val uiScale: Float = 0.95f, // Standard scale
    val callScreenThemeId: String = "Premium",
    val dialPadThemeId: String = "AMOLED"
)

val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }

object PersonalizationManager {
    // Professional Scale Presets
    val SCALE_SMALL = 0.85f
    val SCALE_STANDARD = 0.95f
    val SCALE_LARGE = 1.05f
    val SCALE_XLARGE = 1.15f
    val SCALE_HUGE = 1.30f
    
    val ALL_SCALES = listOf(SCALE_SMALL, SCALE_STANDARD, SCALE_LARGE, SCALE_XLARGE, SCALE_HUGE)

    fun getSettings(context: Context): AppearanceSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString("accent_color", "FF24C98A") ?: "FF24C98A"
        val scale = prefs.getFloat("font_scale", 1.0f)
        val callTheme = prefs.getString("call_screen_theme", "Classic") ?: "Classic"
        val dialTheme = prefs.getString("dial_pad_theme", "Classic") ?: "Classic"
        return AppearanceSettings(
            accentColor = Color(colorHex.toLong(16)),
            uiScale = scale,
            callScreenThemeId = callTheme,
            dialPadThemeId = dialTheme
        )
    }

    fun saveAccentColor(context: Context, color: Color) {
        val hex = "%02X%02X%02X%02X".format(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("accent_color", hex).apply()
    }

    fun saveUiScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("font_scale", scale).apply()
    }

    fun saveCallScreenTheme(context: Context, themeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("call_screen_theme", themeId).apply()
    }

    fun saveDialPadTheme(context: Context, themeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("dial_pad_theme", themeId).apply()
    }
}

fun TextUnit.scaled(scale: Float): TextUnit = (this.value * scale).sp

fun Dp.scaled(scale: Float): Dp = (this.value * scale).dp

@Composable
fun TextUnit.scaled(): TextUnit = scaled(LocalAppearance.current.uiScale)

@Composable
fun Dp.scaled(): Dp = scaled(LocalAppearance.current.uiScale)

@Composable
fun Float.scaledSp(): TextUnit = (this * LocalAppearance.current.uiScale).sp

val premiumColorPresets = mapOf(
    "Emerald Green" to Color(0xFF24C98A),
    "Shyna Blue" to Color(0xFF2979FF),
    "Royal Purple" to Color(0xFFA98BFF),
    "Sunset Orange" to Color(0xFFFF7043),
    "Rose Pink" to Color(0xFFF06292)
)

val uiScalePresets = mapOf(
    "Small" to 0.85f,
    "Standard" to 0.95f,
    "Large" to 1.05f,
    "Extra Large" to 1.15f,
    "Huge" to 1.30f
)
