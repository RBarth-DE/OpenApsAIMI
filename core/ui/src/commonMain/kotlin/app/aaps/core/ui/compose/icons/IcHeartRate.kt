package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the heart rate chip: a heart.
 *
 * Converted from the Android drawable `ic_cp_heart_rate`. Callers tint it, so the red fill here
 * is only a fallback.
 */
val IcHeartRate: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcHeartRate",
        defaultWidth = 46.dp,
        defaultHeight = 40.dp,
        viewportWidth = 123f,
        viewportHeight = 108f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFED1B24)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(60.83f, 17.18f)
            curveToRelative(8f, -8.35f, 13.62f, -15.57f, 26f, -17f)
            curveTo(110f, -2.46f, 131.27f, 21.26f, 119.57f, 44.61f)
            curveToRelative(-3.33f, 6.65f, -10.11f, 14.56f, -17.61f, 22.32f)
            curveToRelative(-8.23f, 8.52f, -17.34f, 16.87f, -23.72f, 23.2f)
            lineToRelative(-17.4f, 17.26f)
            lineTo(46.46f, 93.55f)
            curveTo(29.16f, 76.89f, 1f, 55.92f, 0f, 29.94f)
            curveTo(-0.63f, 11.74f, 13.73f, 0.08f, 30.25f, 0.29f)
            curveToRelative(14.76f, 0.2f, 21f, 7.54f, 30.58f, 16.89f)
            close()
        }
    }.build()
}
