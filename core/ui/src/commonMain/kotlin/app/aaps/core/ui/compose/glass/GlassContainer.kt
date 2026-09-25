package app.aaps.core.ui.compose.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The hero card of the Glass visual language: translucent gradient fill, rounded corners,
 * thin bright border, soft shadow and the signature top sheen. Used by the Glass dashboard skin
 * (`:plugins:main`) and by Glass-restyled screens in `:ui`.
 */
@Composable
fun GlassContainer(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xEC1C2640), Color(0xF5080E18)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xF0E8EEF6)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }
    val borderColor = if (isDark) Color(0x40FFFFFF) else Color(0xE0FFFFFF)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 26.dp else 16.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color(0x660F172A),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0x400F172A)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(1.5.dp, borderColor, shape)
    ) {
        // The sheen is decoration only. It must not take part in sizing: as a direct child the
        // 40.dp sheen forces every card to be at least 40.dp tall and leaves empty glass under
        // the content of shorter cards. Inside a matchParentSize overlay it is clamped to the
        // card, so cards taller than 40.dp keep the same 40.dp sheen as before.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(1.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (isDark) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 1.0f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        content()
    }
}
