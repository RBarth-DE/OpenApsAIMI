package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.glass.GlassColors
import app.aaps.core.ui.compose.glass.GlassContainer

/**
 * Whether the overview home should paint its glass look, and for which dark mode.
 *
 * Provided by the overview dispatcher from the `OverviewGlassLook` preference. The default is
 * "off", so previews and tests that compose a single component keep the classic look.
 */
data class OverviewGlassChrome(
    val enabled: Boolean,
    val isDark: Boolean,
)

val LocalOverviewGlass = staticCompositionLocalOf {
    OverviewGlassChrome(enabled = false, isDark = false)
}

/**
 * Side inset for full-width overview cards when the glass look is on. The top block and the
 * cards below share it so they all have the same width. Classic layouts keep their own insets.
 */
internal val OverviewGlassSideInset = 16.dp

/** Shared translucent card paint: card gradient plus the thin glass border. */
@Composable
internal fun glassCardBrush(isDark: Boolean): Brush =
    Brush.verticalGradient(
        listOf(GlassColors.cardBgStart(isDark), GlassColors.cardBgEnd(isDark))
    )

/**
 * A card that keeps the classic [ElevatedCard] when the glass look is off and swaps only the frame
 * for [GlassContainer] when it is on. The content lambda is shared by both branches.
 */
@Composable
fun OverviewGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glass = LocalOverviewGlass.current
    if (glass.enabled) {
        CompositionLocalProvider(LocalContentColor provides GlassColors.textBright(glass.isDark)) {
            GlassContainer(isDark = glass.isDark, modifier = modifier) { content() }
        }
    } else {
        ElevatedCard(
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) { content() }
    }
}

/**
 * A small glass frame for chips and quick tiles. Used only when the glass look is on — the call
 * site keeps its classic Material surface when it is off, so that look stays unchanged.
 * Icon and label accent colors stay with the caller; the content color is set to the glass text
 * tone for children that do not set their own.
 */
@Composable
fun OverviewGlassChipFrame(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalOverviewGlass.current
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(glassCardBrush(glass.isDark))
            .then(clickModifier)
            .border(1.dp, GlassColors.borderCard(glass.isDark), shape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides GlassColors.textBright(glass.isDark)) {
            content()
        }
    }
}

/**
 * Wraps a block of content in a small glass card when the glass look is on. When it is off the
 * content is drawn unchanged and receives the caller's modifier, so layout is identical either way.
 * Meant for the TIR column, the BG ring and each single graph.
 */
@Composable
fun OverviewGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(AapsSpacing.small),
    content: @Composable (Modifier) -> Unit
) {
    val glass = LocalOverviewGlass.current
    if (glass.enabled) {
        CompositionLocalProvider(LocalContentColor provides GlassColors.textBright(glass.isDark)) {
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(glassCardBrush(glass.isDark))
                    .border(1.dp, GlassColors.borderCard(glass.isDark), shape)
                    .padding(contentPadding)
            ) {
                content(Modifier.fillMaxWidth())
            }
        }
    } else {
        content(modifier)
    }
}
