package edu.bu.cs501.habitable.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.ui.theme.HabitableColors

/** Reusable Material 3 pieces shared by the screens. All touch targets ≥ 48 dp. */

// ---------------------------------------------------------------------------
// Tones
// ---------------------------------------------------------------------------

enum class ChipTone { Heat, Cold, Good, Warn, Neutral }

data class TonePalette(val background: Color, val content: Color, val accent: Color)

@Composable
fun ChipTone.palette(): TonePalette = when (this) {
    ChipTone.Heat -> TonePalette(HabitableColors.HeatSoft, HabitableColors.HeatInk, HabitableColors.Heat)
    ChipTone.Cold -> TonePalette(HabitableColors.ColdSoft, HabitableColors.ColdInk, HabitableColors.Cold)
    ChipTone.Good -> TonePalette(HabitableColors.GoodSoft, HabitableColors.GoodInk, HabitableColors.Good)
    ChipTone.Warn -> TonePalette(HabitableColors.AmberSoft, HabitableColors.AmberInk, HabitableColors.Amber)
    ChipTone.Neutral -> TonePalette(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// Layout primitives
// ---------------------------------------------------------------------------

/** Big screen title with an optional small eyebrow line above and a trailing slot. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = HabitableColors.Heat)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * White card with a hairline border and a soft navy shadow. Pass [onClick] to
 * make the whole card one touch target (its children merge for TalkBack).
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = shape, ambientColor = HabitableColors.Navy.copy(alpha = 0.08f), spotColor = HabitableColors.Navy.copy(alpha = 0.12f))
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun RowScope.Gap(width: Int = 10) = Spacer(Modifier.width(width.dp))

@Composable
fun VGap(height: Int = 10) = Spacer(Modifier.height(height.dp))

// ---------------------------------------------------------------------------
// Chips, dots and badges
// ---------------------------------------------------------------------------

/** A dot that breathes outward, used for "sensor live". Static when [pulse] is false. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = true, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 2.6f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulseScale",
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulseAlpha",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (pulse) {
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = haloAlpha }
                    .background(color, CircleShape)
            )
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    pulse: Boolean = false,
    icon: ImageVector? = null,
) {
    val p = tone.palette()
    Row(
        modifier
            .background(p.background, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            PulsingDot(p.accent, pulse = pulse, size = 7.dp)
            Spacer(Modifier.width(7.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = p.content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = p.content, maxLines = 1)
    }
}

@Composable
fun IconDot(icon: ImageVector, tint: Color, background: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(modifier.size(36.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Rounded-square icon tile. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(background, RoundedCornerShape(size / 3)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

data class CategoryVisual(val icon: ImageVector, val tint: Color, val background: Color)

fun IssueCategory.visual(): CategoryVisual = when (this) {
    IssueCategory.HEAT -> CategoryVisual(Icons.Filled.Thermostat, HabitableColors.HeatInk, HabitableColors.HeatSoft)
    IssueCategory.WATER -> CategoryVisual(Icons.Filled.WaterDrop, HabitableColors.ColdInk, HabitableColors.ColdSoft)
    IssueCategory.MOLD -> CategoryVisual(Icons.Filled.Grain, HabitableColors.GoodInk, HabitableColors.GoodSoft)
    IssueCategory.PESTS -> CategoryVisual(Icons.Filled.BugReport, HabitableColors.AmberInk, HabitableColors.AmberSoft)
    IssueCategory.ELECTRICAL -> CategoryVisual(Icons.Filled.Bolt, HabitableColors.AmberInk, HabitableColors.AmberSoft)
    IssueCategory.NOISE -> CategoryVisual(Icons.Filled.GraphicEq, HabitableColors.ColdInk, HabitableColors.ColdSoft)
    IssueCategory.LOCKS -> CategoryVisual(Icons.Filled.Lock, HabitableColors.Navy, HabitableColors.Card)
    IssueCategory.OTHER -> CategoryVisual(Icons.Filled.Build, HabitableColors.Muted, HabitableColors.Card)
}

@Composable
fun CategoryBadge(category: IssueCategory, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val v = category.visual()
    IconTile(v.icon, v.tint, v.background, modifier, size)
}

fun IssueStatus.tone(): ChipTone = when (this) {
    IssueStatus.OPEN -> ChipTone.Heat
    IssueStatus.NOTICE_SENT -> ChipTone.Warn
    IssueStatus.INSPECTION_REQUESTED -> ChipTone.Cold
    IssueStatus.CERTIFIED -> ChipTone.Cold
    IssueStatus.RESOLVED -> ChipTone.Good
}

/** The legal escalation path, as the four steps a tenant walks through. */
val EscalationSteps = listOf("Open", "Notice", "Inspection", "Certified")

/** How many of the four [EscalationSteps] are done for this status. */
fun IssueStatus.stepsDone(): Int = when (this) {
    IssueStatus.OPEN -> 1
    IssueStatus.NOTICE_SENT -> 2
    IssueStatus.INSPECTION_REQUESTED -> 3
    IssueStatus.CERTIFIED -> 4
    IssueStatus.RESOLVED -> 4
}

/** Thin four-segment bar showing how far an issue has escalated. */
@Composable
fun EscalationProgress(status: IssueStatus, modifier: Modifier = Modifier) {
    val done = status.stepsDone()
    val fill = if (status == IssueStatus.RESOLVED) HabitableColors.Good else status.tone().palette().accent
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = "Step $done of 4: ${status.label}" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(EscalationSteps.size) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (i < done) fill else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

/** Stat with an icon tile, a big value and a short label. */
@Composable
fun StatTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" }, contentPadding = PaddingValues(14.dp)) {
        IconTile(icon, accent, accent.copy(alpha = 0.12f), size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Plain stat without an icon (kept for older call sites). */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" }, contentPadding = PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TwoStats(a: Pair<String, String>, b: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(a.first, a.second, Modifier.weight(1f))
        StatCard(b.first, b.second, Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// Empty, banner
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    title: String? = null,
    tone: ChipTone = ChipTone.Neutral,
) {
    val p = tone.palette()
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).background(p.background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = p.content, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun InlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = tone.palette()
    val icon = when (tone) {
        ChipTone.Heat -> Icons.Filled.CloudOff
        ChipTone.Warn -> Icons.Filled.Warning
        ChipTone.Good -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(p.background, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.content)
        } else {
            Icon(icon, contentDescription = null, tint = p.content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = p.content, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = p.content, fontWeight = FontWeight.Bold) }
        }
    }
}
