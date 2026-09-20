package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins `Modifier.interceptLongPress` against the recompositions that happen around it.
 *
 * A graph panel is recomposed while the finger is held down - the overview ticker, a new reading,
 * a new mode list. The gesture used to be keyed on the callback, and a lambda written at the call
 * site is a new object on every recomposition, so `pointerInput` threw its coroutine away in the
 * middle of the press and the long press never fired.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GraphsSectionLongPressTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a long press survives a recomposition in the middle of the press`() {
        var longPressed = false
        val tick = mutableIntStateOf(0)
        val runs = mutableListOf<Int>()

        compose.setContent {
            MaterialTheme {
                // Read and recorded in the same content that builds the modifier: changing tick
                // recomposes this content, so the interceptor is handed a brand-new callback
                // lambda - exactly what the overview does when its data changes under the finger.
                val reading = tick.intValue
                runs.add(reading)
                Box(
                    Modifier
                        .testTag("panel")
                        .size(120.dp)
                        .interceptLongPress(enabled = true, onLongPress = { longPressed = true })
                ) {
                    Text(text = "tick $reading")
                }
            }
        }

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("panel").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(50) // part-way into the press
        compose.runOnUiThread { tick.intValue++ } // new data arrives with the finger still down
        compose.waitForIdle() // ... and that recomposition is committed
        compose.mainClock.advanceTimeBy(5_000) // well past the long-press timeout
        compose.onNodeWithTag("panel").performTouchInput { up() }
        compose.waitForIdle()

        // Without this the test proves nothing: a press that was never recomposed under fires
        // on the old code too.
        assertThat(runs).hasSize(2)
        assertThat(longPressed).isTrue()
    }
}
