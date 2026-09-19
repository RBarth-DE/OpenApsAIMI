package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the last SMB chip: a single drop.
 *
 * Converted from the Android drawable `ic_dashboard_droplet`, which tinted itself with the theme
 * attribute `colorControlNormal`. Callers tint it, so the white fill is only a fallback.
 */
val IcDroplet: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcDroplet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.85f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(12f, 2f)
            curveTo(9.2f, 6.1f, 6.5f, 9.5f, 6.5f, 13.2f)
            curveTo(6.5f, 16.9f, 9.4f, 20f, 12f, 20f)
            curveTo(14.6f, 20f, 17.5f, 16.9f, 17.5f, 13.2f)
            curveTo(17.5f, 9.5f, 14.8f, 6.1f, 12f, 2f)
            close()
        }
    }.build()
}
