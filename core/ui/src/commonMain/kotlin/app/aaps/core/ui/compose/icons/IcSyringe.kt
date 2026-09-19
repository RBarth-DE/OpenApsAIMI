package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the IOB chip: a syringe.
 *
 * Converted from the Android drawable `ic_dashboard_iob`, which tinted itself with the theme
 * attribute `colorControlNormal`. Callers tint it, so the white fill here is only a fallback.
 */
val IcSyringe: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcSyringe",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeAlpha = 0.85f,
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 2f)
            lineTo(19f, 11f)
            moveTo(12f, 2f)
            lineTo(5f, 11f)
            moveTo(6f, 13.2f)
            arcTo(
                horizontalEllipseRadius = 6f,
                verticalEllipseRadius = 6f,
                theta = 0f,
                isMoreThanHalf = true,
                isPositiveArc = false,
                x1 = 18f,
                y1 = 13.2f
            )
        }

        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeAlpha = 0.85f,
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 11f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
        }

        // Plunger cross
        group(
            translationX = 11.6f,
            translationY = 8f
        ) {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.85f,
                stroke = null,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(-0.7f, -2.1f)
                horizontalLineToRelative(1.4f)
                verticalLineToRelative(1.4f)
                horizontalLineToRelative(1.4f)
                verticalLineToRelative(1.4f)
                horizontalLineToRelative(-1.4f)
                verticalLineToRelative(1.4f)
                horizontalLineToRelative(-1.4f)
                verticalLineToRelative(-1.4f)
                horizontalLineToRelative(-1.4f)
                verticalLineToRelative(-1.4f)
                horizontalLineToRelative(1.4f)
                close()
            }
        }

        // Barrel graduation
        group(
            translationX = 12.2f,
            translationY = 16f
        ) {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0.85f,
                stroke = null,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(-3f, -0.7f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(1.4f)
                horizontalLineToRelative(-6f)
                close()
            }
        }
    }.build()
}
