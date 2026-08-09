package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Режимы темы приложения
enum class ThemeMode {
    SYSTEM,   // Следовать за системной темой
    LIGHT,    // Всегда светлая
    DARK      // Всегда тёмная
}

// === ТЁМНАЯ СХЕМА (фирменная палитра "Радио Открытие") ===
private val DarkColorScheme =
    darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = AccentDark,
        onPrimaryContainer = TextPrimary,

        secondary = AccentLight,
        onSecondary = Color.Black,
        secondaryContainer = SurfaceLight,
        onSecondaryContainer = TextSecondary,

        tertiary = LiveOn,
        onTertiary = Color.Black,

        background = Background,
        onBackground = TextPrimary,

        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceLight,
        onSurfaceVariant = TextSecondary,

        outline = Divider,
        outlineVariant = Divider,

        error = Color(0xFFFF6B6B),
        onError = Color.Black,
        errorContainer = Color(0xFF4A1C1C),
        onErrorContainer = Color(0xFFFFDAD6),
    )

// === СВЕТЛАЯ СХЕМА (фирменная палитра "Радио Открытие") ===
private val LightColorScheme =
    lightColorScheme(
        primary = LightAccent,
        onPrimary = Color.White,
        primaryContainer = LightAccentLight,
        onPrimaryContainer = Color(0xFF3D1A00),

        secondary = LightAccentDark,
        onSecondary = Color.White,
        secondaryContainer = LightSurfaceLight,
        onSecondaryContainer = LightTextSecondary,

        tertiary = LightLiveOn,
        onTertiary = Color.White,

        background = LightBackground,
        onBackground = LightTextPrimary,

        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceLight,
        onSurfaceVariant = LightTextSecondary,

        outline = LightDivider,
        outlineVariant = LightDivider,

        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.SYSTEM -> darkTheme
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
