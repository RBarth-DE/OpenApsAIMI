package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.modes.DashboardModes
import app.aaps.plugins.main.databinding.ComponentDashboardModesBinding
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import android.util.Log
class DashboardModesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding =
        ComponentDashboardModesBinding.inflate(LayoutInflater.from(context), this, true)

    private var listener: ((DashboardModes) -> Unit)? = null

    init {
        orientation = VERTICAL
        allButtons().forEach { (mode, button) ->
            button.setOnClickListener {
                setActiveMode(mode)
                listener?.invoke(mode)
            }
        }
    }

    fun setOnModesClickListener(l: (DashboardModes) -> Unit) {
        listener = l
    }

    fun setEnabledModes(enabled: Set<DashboardModes>) {
        allButtons().forEach { (mode, button) ->
            button.isEnabled = enabled.contains(mode)
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
    }

    fun setActiveMode(mode: DashboardModes?) {
        allButtons().forEach { (m, button) ->
            button.isActivated = (m == mode)
            button.alpha = if (m == mode) 1f else 0.6f
        }
    }


    private fun allButtons(): Map<DashboardModes, MaterialButton> = mapOf(
        DashboardModes.BIG to binding.btnBig,
        DashboardModes.MED to binding.btnMed,
        DashboardModes.SMALL to binding.btnSmall,
        DashboardModes.SNACK1 to binding.btnSnack1,
        DashboardModes.SNACK2 to binding.btnSnack2,
        DashboardModes.SPORT to binding.btnSport,
        DashboardModes.HYPO to binding.btnHypo,
        DashboardModes.BEER to binding.btnBeer,
        DashboardModes.STOP to binding.btnStop
    ).also { map ->
        map.forEach { (mode, btn) ->
            btn.setOnClickListener { listener?.invoke(mode) }
        }
    }
}

