package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the auditor button: a bar chart frame with a checkmark over it.
 *
 * Converted from the Android drawable `ic_audit_monitor`. Callers tint it, so the white fill
 * here is only a fallback.
 */
val IcAuditMonitor: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAuditMonitor",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Frame
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 1.0f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()

            moveTo(19f, 19f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(14f)
            verticalLineTo(19f)
            close()
        }

        // Bars
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 1.0f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(7f, 17f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineTo(7f)
            verticalLineTo(17f)
            close()

            moveTo(11f, 17f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(-2f)
            verticalLineTo(17f)
            close()

            moveTo(15f, 17f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-2f)
            verticalLineTo(17f)
            close()
        }

        // Checkmark
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.7f,
            stroke = null,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(9.5f, 14.5f)
            lineToRelative(-2f, -2f)
            lineToRelative(-1.4f, 1.4f)
            lineToRelative(3.4f, 3.4f)
            lineToRelative(7f, -7f)
            lineToRelative(-1.4f, -1.4f)
            close()
        }
    }.build()
}
