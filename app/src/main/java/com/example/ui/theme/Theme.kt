package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.domain.AppAccentPreset
import com.example.domain.AppThemeMode

val PocketSshShapes = Shapes(
    extraSmall = RoundedCornerShape(ObsidianRadius.sm),
    small = RoundedCornerShape(ObsidianRadius.md),
    medium = RoundedCornerShape(ObsidianRadius.lg),
    large = RoundedCornerShape(ObsidianRadius.xl),
    extraLarge = RoundedCornerShape(ObsidianRadius.xl)
)

@Composable
fun PocketSshTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentPreset: AppAccentPreset = AppAccentPreset.CYAN,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accentPrimaryDark = Color(accentPreset.primaryArgb)
    val accentSecondaryDark = Color(accentPreset.secondaryArgb)

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accentPrimaryDark,
            onPrimary = OnAuroraCyan,
            primaryContainer = accentPrimaryDark.copy(alpha = 0.18f),
            onPrimaryContainer = OnAuroraCyanContainer,
            secondary = accentSecondaryDark,
            onSecondary = OnAuroraLime,
            secondaryContainer = accentSecondaryDark.copy(alpha = 0.18f),
            onSecondaryContainer = OnAuroraLimeContainer,
            tertiary = AuroraLime,
            onTertiary = OnAuroraLime,
            background = ObsidianBg,
            onBackground = AuroraTextPrimary,
            surface = ObsidianSurface,
            onSurface = AuroraTextPrimary,
            surfaceVariant = ObsidianElevated,
            onSurfaceVariant = AuroraTextSecondary,
            surfaceContainerLowest = ObsidianBg,
            surfaceContainerLow = ObsidianSurfaceLow,
            surfaceContainer = ObsidianSurface,
            surfaceContainerHigh = ObsidianElevated,
            surfaceContainerHighest = ObsidianSurfaceHighest,
            outline = ObsidianBorder,
            outlineVariant = ObsidianBorderSubtle,
            error = AuroraRed,
            onError = OnAuroraRed,
            errorContainer = AuroraRedContainer,
            onErrorContainer = OnAuroraRedContainer
        )
    } else {
        val lightPrimary = when (accentPreset) {
            AppAccentPreset.CYAN -> AuroraLightCyan
            AppAccentPreset.LIME -> AuroraLightLime
            AppAccentPreset.ICE_BLUE -> Color(0xFF1565C0)
            AppAccentPreset.VIOLET -> Color(0xFF6D28D9)
        }
        lightColorScheme(
            primary = lightPrimary,
            onPrimary = Color.White,
            primaryContainer = AuroraLightCyanContainer,
            onPrimaryContainer = AuroraLightOnCyanContainer,
            secondary = AuroraLightLime,
            onSecondary = Color.White,
            secondaryContainer = AuroraLightLimeContainer,
            onSecondaryContainer = AuroraLightOnLimeContainer,
            tertiary = AuroraLightLime,
            onTertiary = Color.White,
            background = AuroraLightBg,
            onBackground = AuroraLightTextPrimary,
            surface = AuroraLightSurface,
            onSurface = AuroraLightTextPrimary,
            surfaceVariant = AuroraLightElevated,
            onSurfaceVariant = AuroraLightTextSecondary,
            surfaceContainerLowest = AuroraLightSurface,
            surfaceContainerLow = AuroraLightBg,
            surfaceContainer = AuroraLightSurface,
            surfaceContainerHigh = AuroraLightElevated,
            surfaceContainerHighest = AuroraLightSurfaceHighest,
            outline = AuroraLightBorder,
            outlineVariant = AuroraLightBorder.copy(alpha = 0.7f),
            error = Color(0xFFC62828),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD9),
            onErrorContainer = Color(0xFF410002)
        )
    }

    val extendedColors = if (useDark) {
        ObsidianExtendedColors(
            background = ObsidianBg,
            surface = ObsidianSurface,
            elevated = ObsidianElevated,
            border = ObsidianBorder,
            subtleBorder = ObsidianBorderSubtle,
            cyan = accentPrimaryDark,
            lime = accentSecondaryDark,
            amber = AuroraAmber,
            red = AuroraRed,
            textPrimary = AuroraTextPrimary,
            textSecondary = AuroraTextSecondary,
            isDark = true
        )
    } else {
        ObsidianExtendedColors(
            background = AuroraLightBg,
            surface = AuroraLightSurface,
            elevated = AuroraLightElevated,
            border = AuroraLightBorder,
            subtleBorder = AuroraLightBorder.copy(alpha = 0.7f),
            cyan = colorScheme.primary,
            lime = AuroraLightLime,
            amber = Color(0xFFB76E00),
            red = Color(0xFFC62828),
            textPrimary = AuroraLightTextPrimary,
            textSecondary = AuroraLightTextSecondary,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalObsidianColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = PocketSshShapes,
            content = content
        )
    }
}
