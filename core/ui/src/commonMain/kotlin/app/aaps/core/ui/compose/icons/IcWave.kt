package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the basal chip: three stacked waves.
 *
 * Converted from the Android drawable `ic_dashboard_wave`. The three waves are drawn out one by
 * one rather than generated in a loop: the source gives them slightly different control points,
 * so a loop would change the shape. Callers tint it, so the white fill here is only a fallback.
 */
val IcWave: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcWave",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Bottom wave (y = 16.99)
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.85f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(17f, 16.99f)
            curveToRelative(-1.35f, 0f, -2.2f, 0.42f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.33f, -1.18f, 0.6f, -2.05f, 0.6f)
            curveToRelative(-0.9f, 0f, -1.4f, -0.25f, -2.05f, -0.6f)
            curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
            reflectiveCurveToRelative(-2.2f, 0.42f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.33f, -1.17f, 0.6f, -2.05f, 0.6f)
            verticalLineToRelative(1.95f)
            curveToRelative(1.35f, 0f, 2.2f, -0.42f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.33f, 1.17f, -0.6f, 2.05f, -0.6f)
            reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
            reflectiveCurveToRelative(2.2f, -0.42f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.33f, 1.18f, -0.6f, 2.05f, -0.6f)
            curveToRelative(0.9f, 0f, 1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
            verticalLineToRelative(-1.95f)
            curveToRelative(-0.9f, 0f, -1.4f, -0.25f, -2.05f, -0.6f)
            curveTo(19.2f, 17.41f, 18.35f, 16.99f, 17f, 16.99f)
            close()
        }

        // Middle wave (y = 12.54)
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.85f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(17f, 12.54f)
            curveToRelative(-1.35f, 0f, -2.2f, 0.43f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.32f, -1.18f, 0.6f, -2.05f, 0.6f)
            curveToRelative(-0.9f, 0f, -1.4f, -0.25f, -2.05f, -0.6f)
            curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
            reflectiveCurveToRelative(-2.2f, 0.43f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.32f, -1.17f, 0.6f, -2.05f, 0.6f)
            verticalLineToRelative(1.93f)
            curveToRelative(1.35f, 0f, 2.2f, -0.43f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
            reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
            reflectiveCurveToRelative(2.2f, -0.43f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
            reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
            verticalLineToRelative(-1.93f)
            curveToRelative(-0.9f, 0f, -1.4f, -0.25f, -2.05f, -0.6f)
            curveTo(19.2f, 12.97f, 18.35f, 12.54f, 17f, 12.54f)
            close()
        }

        // Top wave (y = 8.09)
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.85f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(17f, 8.09f)
            curveToRelative(-1.35f, 0f, -2.2f, 0.43f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.35f, -1.15f, 0.6f, -2.05f, 0.6f)
            reflectiveCurveToRelative(-1.4f, -0.25f, -2.05f, -0.6f)
            curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
            reflectiveCurveToRelative(-2.2f, 0.43f, -2.95f, 0.8f)
            curveToRelative(-0.65f, 0.35f, -1.15f, 0.6f, -2.05f, 0.6f)
            verticalLineToRelative(1.93f)
            curveToRelative(1.35f, 0f, 2.2f, -0.43f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
            reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
            reflectiveCurveToRelative(2.2f, -0.43f, 2.95f, -0.8f)
            curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
            reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
            curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
            verticalLineTo(9.49f)
            curveToRelative(-0.9f, 0f, -1.4f, -0.25f, -2.05f, -0.6f)
            curveTo(19.2f, 8.52f, 18.35f, 8.09f, 17f, 8.09f)
            close()
        }
    }.build()
}
