package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the steps chip: a shoe with a footstep.
 *
 * Converted from the Android drawable `ic_dashboard_shoe`. Callers tint it, so the fill color
 * here only matters where no tint is applied.
 */
val IcShoe: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcShoe",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
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
            moveTo(19f, 1f)
            curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
            verticalLineToRelative(4.56f)
            curveToRelative(0f, 2.83f, 1.74f, 5.32f, 4.36f, 6.23f)
            lineTo(19f, 17.22f)
            lineToRelative(0.64f, -0.43f)
            curveToRelative(2.62f, -0.91f, 4.36f, -3.41f, 4.36f, -6.23f)
            verticalLineTo(6f)
            curveTo(24f, 3.24f, 21.76f, 1f, 19f, 1f)
            close()

            moveTo(4.36f, 6.81f)
            curveTo(4.85f, 3.67f, 7.72f, 1.38f, 10.95f, 1.75f)
            curveToRelative(3.08f, 0.35f, 5.38f, 3.06f, 5.04f, 6.24f)
            lineTo(15.2f, 16.5f)
            curveToRelative(-0.29f, 2.77f, -2.48f, 4.98f, -5.24f, 5.42f)
            curveTo(7.03f, 22.38f, 4.34f, 20.31f, 4f, 17.4f)
            lineTo(4.36f, 6.81f)
            close()
        }
    }.build()
}
