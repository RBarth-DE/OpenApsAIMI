package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.aaps.ui.compose.overview.graphs.TirUiState
import kotlin.math.roundToInt


private val TirColorVeryLow  = Color(0xFFB71C1C)  // dark red
private val TirColorLow      = Color(0xFFEF5350)  // red
private val TirColorInRange  = Color(0xFF43A047)  // green
private val TirColorHigh     = Color(0xFFFFA726)  // orange
private val TirColorVeryHigh = Color(0xFFE64A19)  // deep orange

@Composable
internal fun VerticalTirPanel(
    state: TirUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(min = 20.dp, max = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {

        // Header
        // Text("AVG", style = MaterialTheme.typography.labelSmall,
        //      color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (state.readingCount > 0) state.avgMgDl.toInt().toString() else "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Einziger gestapelter Balken
        val segments = listOf(
            state.veryHigh to TirColorVeryHigh,
            state.high     to TirColorHigh,
            state.inRange  to TirColorInRange,
            state.low      to TirColorLow,
            state.veryLow  to TirColorVeryLow,
        ).filter { it.first >= 0.5f }

        Column(
            modifier = Modifier
                .width(20.dp) //bar width
                .height(126.dp)  // ~5 Buttons à 26dp + spacing
                .clip(RoundedCornerShape(3.dp))
        ) {
            segments.forEach { (pct, color) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(pct)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    if (pct >= 8f) {
                        Text(
                            text = "${pct.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Footer
        // Text("A1C", style = MaterialTheme.typography.labelSmall,
        //      color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${"%.1f".format(state.a1c)}%",
             style = MaterialTheme.typography.labelMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)

    }
}

