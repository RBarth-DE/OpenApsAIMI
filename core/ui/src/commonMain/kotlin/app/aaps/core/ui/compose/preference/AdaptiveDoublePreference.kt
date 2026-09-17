/*
 * Adaptive Double Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.UnitType
import app.aaps.core.keys.decimalPlaces
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.step
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.isDuration
import app.aaps.core.ui.compose.rangeText
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.core.ui.compose.unitFormat
import app.aaps.core.ui.compose.unitLabel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Composable double preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses doubleKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveDoublePreferencePreview
 */
@Composable
fun AdaptiveDoublePreferenceItem(
    doubleKey: DoublePreferenceKey,
    title: TextRef? = null,
    unit: String = "",
    visibilityContext: VisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val effectiveTitle = title ?: doubleKey.title

    val visibility = calculatePreferenceVisibility(
        preferenceKey = doubleKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible || (preferences.simpleMode && doubleKey.calculatedBySM)) return

    val state = rememberPreferenceDoubleState(doubleKey)
    // A stored value outside the allowed range would make the slider and the text field show
    // something the user cannot enter again, so clamp it once per key.
    LaunchedEffect(doubleKey.key) {
        val v = state.value
        if (v < doubleKey.min || v > doubleKey.max) {
            state.value = v.coerceIn(doubleKey.min, doubleKey.max)
        }
    }
    val value = state.value
    val theme = LocalPreferenceTheme.current

    // Get formatting info from UnitType
    val unitType = doubleKey.unitType
    val span = (doubleKey.max - doubleKey.min).let { if (abs(it) < 1e-12) 1e-9 else it }
    // Keys without a unit get the number of decimals and the step from the value range, so that small
    // ranges stay usable in the slider and the text field.
    val (decimalPlaces, step) = if (unitType == UnitType.NONE) {
        when {
            span <= 0.15 -> 3 to 0.001
            span <= 1.5  -> 2 to 0.01
            span <= 25.0 -> 1 to 0.1
            else         -> 0 to 1.0
        }
    } else {
        unitType.decimalPlaces() to unitType.step()
    }
    // Template and rounding in one value, so this cannot hand the slider a %d format with a Double -
    // the exception that used to reach [formatSliderDisplayValue] from exactly that mistake.
    val unitFormat = unitType.unitFormat()

    // Get unit label from UnitType (for dialog input suffix)
    val unitLabelRef = unitType.unitLabel() ?: unit.takeIf { it.isNotEmpty() }?.let { TextRef.Literal(it) }
    val unitLabelText = unitLabelRef?.let { stringResource(it) } ?: ""

    val valueFormat = NumberFormat.withDecimals(decimalPlaces)

    // Get summary if available
    val summary = stringResourceOrNull(doubleKey.summary)

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
                text = preferenceDisplayTitle(effectiveTitle, doubleKey.key),
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
                unitFormat = unitFormat,
                valueFormat = valueFormat,
                unitLabel = unitLabelRef,
                asDuration = unitType.isDuration(),
                dialogLabel = preferenceDisplayTitle(effectiveTitle, doubleKey.key),
                dialogSummary = summary,
                enabled = visibility.enabled
            )
        }
    } else {
        // For unspecified ranges, use text field with range summary
        // Same whole-number rule as the slider above: the range text uses the same %d formats.
        val rangeRef = if (unitFormat?.asWholeNumber == true) {
            unitType.rangeText(value.roundToInt(), doubleKey.min.roundToInt(), doubleKey.max.roundToInt())
        } else {
            unitType.rangeText(value, doubleKey.min, doubleKey.max)
        }
        val summaryText = if (rangeRef != null) {
            stringResource(rangeRef)
        } else {
            stringResource(CoreUiStrings.preference_range_summary, valueFormat.format(value), unitLabelText, valueFormat.format(doubleKey.min), valueFormat.format(doubleKey.max))
        }
        TextFieldPreference(
            state = state,
            title = { PreferenceTitleWithSyncBadge(effectiveTitle, doubleKey) },
            textToValue = { text ->
                text.toDoubleOrNull()?.coerceIn(doubleKey.min, doubleKey.max)
            },
            enabled = visibility.enabled,
            summary = { Text(summaryText) }
        )
    }
}
