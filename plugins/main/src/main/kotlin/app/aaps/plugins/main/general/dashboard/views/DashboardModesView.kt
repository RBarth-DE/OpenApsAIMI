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
    private var activeMode: DashboardModes? = null

    init {
        orientation = VERTICAL

        bind(binding.btnBig, DashboardModes.BIG)
        bind(binding.btnMed, DashboardModes.MED)
        bind(binding.btnSmall, DashboardModes.SMALL)
        bind(binding.btnSnack1, DashboardModes.SNACK1)
        bind(binding.btnSnack2, DashboardModes.SNACK2)
        bind(binding.btnSport, DashboardModes.SPORT)
        bind(binding.btnHypo, DashboardModes.HYPO)
        bind(binding.btnBeer, DashboardModes.BEER)
        bind(binding.btnStop, DashboardModes.STOP)
    }

    fun setOnModesClickListener(l: (DashboardModes) -> Unit) {
        listener = l
    }

    fun setEnabledModes(enabled: Set<DashboardModes>) {
        allButtons().forEach { (mode, button) ->
            button.isEnabled = mode in enabled
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
    }

    private fun bind(button: MaterialButton, mode: DashboardModes) {
        button.setOnClickListener {
            setActiveMode(mode)
            listener?.invoke(mode)
        }
    }

    private fun setActiveMode(mode: DashboardModes) {
        activeMode = mode
        updateActiveUi()

        // optional: Active-State nach 2s zurück
        postDelayed({
                        activeMode = null
                        updateActiveUi()
                    }, 2000)
    }

    private fun updateActiveUi() {
        allButtons().forEach { (mode, button) ->
            button.isActivated = (mode == activeMode)
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
    )
}

