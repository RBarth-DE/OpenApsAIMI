/*
 * Adaptive Double Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.decimalPlaces
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.rangeResId
import app.aaps.core.keys.step
import app.aaps.core.keys.unitLabelResId
import app.aaps.core.keys.valueResId
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalPreferences
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Composable double preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses doubleKey.titleResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveDoublePreferencePreview
 */
@Composable
fun AdaptiveDoublePreferenceItem(
    doubleKey: DoublePreferenceKey,
    titleResId: Int = 0,
    unit: String = "",
    visibilityContext: VisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val effectiveTitleResId = if (titleResId != 0) titleResId else doubleKey.titleResId
    val titleText = preferenceDisplayTitle(effectiveTitleResId, doubleKey.key)

    val visibility = calculatePreferenceVisibility(
        preferenceKey = doubleKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible || (preferences.simpleMode && doubleKey.calculatedBySM)) return

    val state = rememberPreferenceDoubleState(doubleKey)
    val value = state.value
    val theme = LocalPreferenceTheme.current

    // Get formatting info from UnitType
    val unitType = doubleKey.unitType
    val decimalPlaces = unitType.decimalPlaces()
    val step = unitType.step()
    val valueFormatResId = unitType.valueResId()

    // Get unit label from UnitType (for dialog input suffix)
    val unitLabelResId = unitType.unitLabelResId()
    val unitLabel = unitLabelResId?.let { stringResource(it) } ?: unit

    val valueFormat = if (decimalPlaces == 0) DecimalFormat("0") else DecimalFormat("0.${"0".repeat(decimalPlaces)}")

    // Get summary if available
    val summaryResId = doubleKey.summaryResId
    val summary = if (summaryResId != null && summaryResId != 0) stringResource(summaryResId) else null

    // Use slider if min/max range is specified (not default extreme values)
    // Note: Double.MIN_VALUE is smallest positive value, not most negative
    val hasValidRange = doubleKey.min != -Double.MAX_VALUE && doubleKey.max != Double.MAX_VALUE

    if (hasValidRange) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.padding)
        ) {
            TextWithSyncBadge(
                text = titleText,
                key = doubleKey,
                style = theme.titleTextStyle,
                // Mirror Preference's disabled styling (the switch row greys the same way) since this
                // slider branch builds its own row instead of going through Preference.
                color = theme.titleColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = theme.summaryTextStyle,
                    color = theme.summaryColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
                )
            }
            PreferenceSliderWithButtons(
                value = value,
                onValueChange = { newValue ->
                    if (visibility.enabled) {
                        state.value = newValue
                    }
                },
                valueRange = doubleKey.min..doubleKey.max,
                step = step,
                showValue = true,
                valueFormatResId = valueFormatResId,
                formatAsInt = decimalPlaces == 0,
                valueFormat = valueFormat,
                unitLabel = unitLabel,
                dialogLabel = titleText,
                dialogSummary = summary,
                enabled = visibility.enabled
            )
        }
    } else {
        // For unspecified ranges, use text field with range summary
        val rangeFormatResId = unitType.rangeResId()
        val displayedValue = if (decimalPlaces == 0) value.roundToInt() else value
        val displayedMin = if (decimalPlaces == 0) doubleKey.min.roundToInt() else doubleKey.min
        val displayedMax = if (decimalPlaces == 0) doubleKey.max.roundToInt() else doubleKey.max
        val summaryText = if (rangeFormatResId != null) {
            stringResource(rangeFormatResId, displayedValue, displayedMin, displayedMax)
        } else {
            stringResource(R.string.preference_range_summary, valueFormat.format(value), unitLabel, valueFormat.format(doubleKey.min), valueFormat.format(doubleKey.max))
        }
        TextFieldPreference(
            state = state,
            title = { PreferenceTitleWithSyncBadge(effectiveTitleResId, doubleKey) },
            textToValue = { text ->
                text.toDoubleOrNull()?.coerceIn(doubleKey.min, doubleKey.max)
            },
            enabled = visibility.enabled,
            summary = { Text(summaryText) }
        )
    }
}
