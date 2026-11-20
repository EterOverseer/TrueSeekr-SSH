package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.SessionStatus
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianMotion
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing
import com.example.ui.theme.rememberReducedMotion

/**
 * Glass-inspired bordered card following the Obsidian Aurora Command Center visual language.
 */
@Composable
fun ObsidianSurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ObsidianRadius.lg),
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalObsidianColors.current
    val borderColor = accentBorder ?: colors.border
    val bgBrush = if (elevated) {
        colors.glassCardBrush()
    } else {
        Brush.verticalGradient(listOf(colors.surface, colors.surface))
    }

    Surface(
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .background(bgBrush, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(content = content)
    }
}

/**
 * Consistent connection status pill pairing icon + color + readable label.
 */
@Composable
fun ConnectionStatusPill(
    status: SessionStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = LocalObsidianColors.current
    val reducedMotion = rememberReducedMotion()

    val targetTint = when (status) {
        SessionStatus.CONNECTED -> colors.lime
        SessionStatus.CONNECTION_IDLE -> colors.cyan
        SessionStatus.CONNECTING,
        SessionStatus.VERIFYING_HOST,
        SessionStatus.AUTHENTICATING -> colors.cyan
        SessionStatus.CHECKING_CONNECTION,
        SessionStatus.RECONNECTING -> colors.amber
        SessionStatus.CONNECTION_LOST,
        SessionStatus.ERROR -> colors.red
        SessionStatus.DISCONNECTED,
        SessionStatus.IDLE -> colors.textSecondary
    }
    val tint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = ObsidianMotion.fastTween(reducedMotion),
        label = "statusTint"
    )
    val isInProgress = status == SessionStatus.CONNECTING ||
        status == SessionStatus.VERIFYING_HOST ||
        status == SessionStatus.AUTHENTICATING ||
        status == SessionStatus.CHECKING_CONNECTION

    val icon = when (status) {
        SessionStatus.CONNECTED, SessionStatus.CONNECTION_IDLE -> Icons.Default.CheckCircle
        SessionStatus.RECONNECTING -> Icons.Default.Refresh
        SessionStatus.CONNECTION_LOST,
        SessionStatus.ERROR -> Icons.Default.ErrorOutline
        SessionStatus.DISCONNECTED -> Icons.Default.LinkOff
        else -> Icons.Default.RadioButtonUnchecked
    }

    val containerColor = tint.copy(alpha = if (colors.isDark) 0.14f else 0.12f)
    val borderColor = tint.copy(alpha = 0.38f)

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(ObsidianRadius.pill),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = tint
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = status.label,
                    tint = tint,
                    modifier = Modifier.size(if (compact) 13.dp else 14.dp)
                )
            }
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Host key trust status pill showing whether a known SHA-256 fingerprint is registered locally.
 */
@Composable
fun HostTrustIndicator(
    isTrusted: Boolean,
    modifier: Modifier = Modifier,
    isMismatch: Boolean = false
) {
    val colors = LocalObsidianColors.current
    val tint = when {
        isMismatch -> colors.red
        isTrusted -> colors.cyan
        else -> colors.amber
    }
    val icon = when {
        isMismatch -> Icons.Default.GppBad
        isTrusted -> Icons.Default.VerifiedUser
        else -> Icons.Default.Shield
    }
    val label = when {
        isMismatch -> "Key Mismatch"
        isTrusted -> "Host Verified"
        else -> "Unverified Host"
    }

    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(ObsidianRadius.sm),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * Metadata pill badge for group, auth method, protocol, latency, or route.
 */
@Composable
fun ObsidianMetadataTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null,
    monospace: Boolean = false
) {
    val colors = LocalObsidianColors.current
    val resolvedTextColor = tint ?: colors.textSecondary
    Surface(
        color = colors.elevated,
        shape = RoundedCornerShape(ObsidianRadius.sm),
        border = BorderStroke(1.dp, colors.subtleBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedTextColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                style = if (monospace) {
                    MonoCodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize)
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = resolvedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact overview metric tile for the Command Center dashboard.
 */
@Composable
fun OverviewMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalObsidianColors.current
    ObsidianSurfaceCard(
        modifier = modifier,
        shape = RoundedCornerShape(ObsidianRadius.md),
        elevated = true,
        accentBorder = accentColor.copy(alpha = 0.28f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ObsidianSpacing.sm, vertical = ObsidianSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ObsidianSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    accentColor: Color? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val colors = LocalObsidianColors.current
    val resolvedAccent = accentColor ?: colors.cyan
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null) {
                Surface(
                    color = resolvedAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(ObsidianRadius.pill),
                    border = BorderStroke(1.dp, resolvedAccent.copy(alpha = 0.30f))
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = resolvedAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        trailingAction?.invoke()
    }
}

@Composable
fun ObsidianEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionTestTag: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalObsidianColors.current
    ObsidianSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        elevated = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ObsidianSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(colors.cyan.copy(alpha = 0.12f))
                    .border(1.dp, colors.cyan.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.cyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .then(if (actionTestTag != null) Modifier else Modifier)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

enum class BannerTone { INFO, WARNING, ERROR }

@Composable
fun ObsidianStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = LocalObsidianColors.current
    val accent = when (tone) {
        BannerTone.INFO -> colors.cyan
        BannerTone.WARNING -> colors.amber
        BannerTone.ERROR -> colors.red
    }
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(ObsidianRadius.md),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (tone == BannerTone.ERROR) colors.red else colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "ปิดแจ้งเตือน",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
