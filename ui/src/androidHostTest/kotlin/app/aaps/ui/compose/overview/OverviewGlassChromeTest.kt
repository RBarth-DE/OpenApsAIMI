package app.aaps.ui.compose.overview

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture
import app.aaps.ui.compose.testing.setAapsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glass look must only change the frame of the overview home, never its content: the same
 * labels have to be displayed with [LocalOverviewGlass] turned on as with it off. The look itself
 * is checked by the user on a device; these tests pin that the content survives the glass branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverviewGlassChromeTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: OverviewViewModelFixture
    private lateinit var context: Context

    private lateinit var expandLabel: String
    private lateinit var statusHeading: String

    private val statusLightsDef = PreferenceSubScreenDef(
        key = "statuslights",
        titleResId = app.aaps.core.ui.R.string.statuslights
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        viewModels = OverviewViewModelFixture(screen)
        context = RuntimeEnvironment.getApplication()
        expandLabel = context.getString(app.aaps.core.ui.R.string.expand)
        statusHeading = context.getString(app.aaps.core.ui.R.string.status)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun glassChrome(enabled: Boolean = true, isDark: Boolean = true): OverviewGlassChrome =
        OverviewGlassChrome(enabled = enabled, isDark = isDark)

    @Test
    fun `glass on keeps the stacked content visible`() {
        val status = viewModels.awaitStatusItems()
        compose.setAapsContent(screen) {
            CompositionLocalProvider(LocalOverviewGlass provides glassChrome()) {
                OverviewScreenStacked(
                    tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                    tempTargetProgress = 0f, tempTargetReason = null,
                    runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                    runningModeRemaining = "", runningModeProgress = 0f,
                    smbEnabled = false, isSimpleMode = true,
                    graphViewModel = viewModels.graphViewModel,
                    chipsViewModel = viewModels.chipsViewModel,
                    manageViewModel = viewModels.manageViewModel,
                    statusViewModel = status,
                    statusLightsDef = statusLightsDef,
                    onNavigate = {},
                    paddingValues = PaddingValues(0.dp)
                )
            }
        }

        compose.onNodeWithText(NO_BG_PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithContentDescription(expandLabel).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `glass on keeps the expanded status card content`() {
        val status = viewModels.awaitStatusItems()
        compose.setAapsContent(screen) {
            CompositionLocalProvider(LocalOverviewGlass provides glassChrome()) {
                OverviewScreenSplit(
                    tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                    tempTargetProgress = 0f, tempTargetReason = null,
                    runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                    runningModeRemaining = "", runningModeProgress = 0f,
                    smbEnabled = false, isSimpleMode = true,
                    graphViewModel = viewModels.graphViewModel,
                    chipsViewModel = viewModels.chipsViewModel,
                    manageViewModel = viewModels.manageViewModel,
                    statusViewModel = status,
                    statusLightsDef = statusLightsDef,
                    onNavigate = {},
                    paddingValues = PaddingValues(0.dp)
                )
            }
        }

        compose.onNodeWithText(statusHeading).assertIsDisplayed()
    }

    @Test
    fun `glass off is the default chrome`() {
        val status = viewModels.awaitStatusItems()
        compose.setAapsContent(screen) {
            OverviewScreenStacked(
                tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                tempTargetProgress = 0f, tempTargetReason = null,
                runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                runningModeRemaining = "", runningModeProgress = 0f,
                smbEnabled = false, isSimpleMode = true,
                graphViewModel = viewModels.graphViewModel,
                chipsViewModel = viewModels.chipsViewModel,
                manageViewModel = viewModels.manageViewModel,
                statusViewModel = status,
                statusLightsDef = statusLightsDef,
                onNavigate = {},
                paddingValues = PaddingValues(0.dp)
            )
        }

        compose.onNodeWithText(NO_BG_PLACEHOLDER).assertIsDisplayed()
    }

    private companion object {
        private const val WIDE = "w1280dp-h800dp"
        private const val TT_TEXT = "100"
        private const val RM_TEXT = "Open Loop"
        private const val NO_BG_PLACEHOLDER = "---"
    }
}
