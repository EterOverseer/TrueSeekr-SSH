package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object ObsidianSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object ObsidianRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
    val pill = 999.dp
}

object ObsidianElevation {
    val flat = 0.dp
    val card = 2.dp
    val elevated = 6.dp
}

object ObsidianMotion {
    const val FastMs = 150
    const val StandardMs = 200

    fun <T> standardTween(reducedMotion: Boolean): FiniteAnimationSpec<T> {
        return tween(durationMillis = if (reducedMotion) 0 else StandardMs)
    }

    fun <T> fastTween(reducedMotion: Boolean): FiniteAnimationSpec<T> {
        return tween(durationMillis = if (reducedMotion) 0 else FastMs)
    }

    fun <T> snappySpring(reducedMotion: Boolean): FiniteAnimationSpec<T> {
        return if (reducedMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        }
    }
}

@Immutable
data class ObsidianExtendedColors(
    val background: Color = ObsidianBg,
    val surface: Color = ObsidianSurface,
    val elevated: Color = ObsidianElevated,
    val border: Color = ObsidianBorder,
    val subtleBorder: Color = ObsidianBorderSubtle,
    val cyan: Color = AuroraCyan,
    val lime: Color = AuroraLime,
    val amber: Color = AuroraAmber,
    val red: Color = AuroraRed,
    val textPrimary: Color = AuroraTextPrimary,
    val textSecondary: Color = AuroraTextSecondary,
    val isDark: Boolean = true
) {
    fun hairlineBorder(): BorderStroke = BorderStroke(1.dp, border)
    fun subtleHairlineBorder(): BorderStroke = BorderStroke(1.dp, subtleBorder)

    fun auroraHeaderBrush(accent: Color = cyan): Brush = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.14f else 0.10f),
            elevated.copy(alpha = if (isDark) 0.92f else 0.96f),
            surface
        )
    )

    fun glassCardBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            elevated.copy(alpha = if (isDark) 0.85f else 0.96f),
            surface
        )
    )
}

val LocalObsidianColors = staticCompositionLocalOf { ObsidianExtendedColors() }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        }.getOrDefault(false)
    }
}

fun HapticFeedback.performSubtleConfirm(enabled: Boolean = true) {
    if (enabled) {
        performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}
