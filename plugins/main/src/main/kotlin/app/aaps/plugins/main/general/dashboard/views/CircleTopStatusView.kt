package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ComponentCircleTopStatusBinding
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState
import com.google.android.material.card.MaterialCardView

/**
 * Top tile (circle design) for the Dashboard.
 *
 * NOTE: No HTML parsing, no parsing of pumpStatusText. This view only uses
 * explicit fields from an extended StatusCardState (via reflection) and otherwise
 * falls back to "--".
 */
class CircleTopStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding = ComponentCircleTopStatusBinding.inflate(LayoutInflater.from(context), this)

    private var debugNose = false
    private var lastState: StatusCardState? = null

    init {
        // Long-press on the ring toggles debug (shows nose angle in the sub text)
        binding.glucoseRing.setOnLongClickListener {
            debugNose = !debugNose
            lastState?.let { update(it) }
            true
        }
    }

    /** API-compat for DashboardFragment (some branches call this). */
    /*fun setOnAimiIconClickListener(listener: OnClickListener?) {
        val icon: View? = try { binding.aimiIcon } catch (_: Throwable) { findViewById(R.id.aimi_icon) }
        (icon ?: this).setOnClickListener(listener)
    }

    fun setOnAimiIconLongClickListener(listener: OnLongClickListener?) {
        val icon: View? = try { binding.aimiIcon } catch (_: Throwable) { findViewById(R.id.aimi_icon) }
        (icon ?: this).setOnLongClickListener(listener)
    }*/

    fun update(state: StatusCardState) {
        //lastState = state
        // Prefer extended fields if present (reflection), otherwise show placeholders.
        val reservoirText = getStringProp(state, "reservoirText") ?: "--"
        val infusionAge = getStringProp(state, "infusionAgeText") ?: "--"
        val sensorAge = getStringProp(state, "sensorAgeText") ?: "--"
        val basalText = getStringProp(state, "basalText") ?: "--"
        val lastUpdateText = getStringProp( state,"lastUpdateText" )  ?: "--"
        val lastSensorValueText = getStringProp(state, "lastSensorValueText")  ?: "--"
        // Activity (basal percentage)
        val activityPctText =
            getStringProp(state, "activityPctText")
                ?: getStringProp(state, "activityText")
                ?: "--"

        // Texts left
        binding.reservoirChip.text = reservoirText
        binding.infusionAgeText.text = infusionAge
        binding.iobText.text = state.iobText
        binding.pumpBatteryText.text = state.pumpBatteryText

        // Texts right
        binding.lastUpdateText.text = lastUpdateText
        binding.lastSensorValueText.text = lastSensorValueText
        binding.sensorAgeText.text = sensorAge
        binding.activityText.text = activityPctText
        binding.basalText.text = basalText

        // Ring (use extended values if present)
        val bgMgdl = getIntProp(state, "glucoseMgdl")
        val noseAngleDeg = getFloatProp(state, "noseAngleDeg")
        val subLeft = getStringProp(state, "ringLeftText") ?: (state.timeAgo + "m")
        val subRight = getStringProp(state, "ringRightText") ?: state.deltaText

        binding.glucoseRing.update(
            bgMgdl = bgMgdl,
            mainText = state.glucoseText,
            subLeftText = subLeft,
            subRightText = subRight,
            noseAngleDeg = noseAngleDeg
        )
    }

    private fun getStringProp(state: Any, name: String): String? {
        return runCatching {
            val m = state.javaClass.methods.firstOrNull {
                it.name.equals("get${name.replaceFirstChar { c -> c.uppercaseChar() }}")
            }
            (m?.invoke(state) as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun getIntProp(state: Any, name: String): Int? {
        return runCatching {
            val m = state.javaClass.methods.firstOrNull {
                it.name.equals("get${name.replaceFirstChar { c -> c.uppercaseChar() }}")
            }
            when (val v = m?.invoke(state)) {
                is Int -> v
                is Number -> v.toInt()
                else -> null
            }
        }.getOrNull()
    }

    private fun getFloatProp(state: Any, name: String): Float? {
        return runCatching {
            val m = state.javaClass.methods.firstOrNull {
                it.name.equals("get${name.replaceFirstChar { c -> c.uppercaseChar() }}")
            }
            when (val v = m?.invoke(state)) {
                is Float -> v
                is Double -> v.toFloat()
                is Number -> v.toFloat()
                else -> null
            }
        }.getOrNull()
    }
}
