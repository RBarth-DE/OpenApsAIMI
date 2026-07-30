package app.aaps.plugins.main.general.overview.boost

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import dagger.android.support.DaggerFragment

/**
 * BOOST Overview — stub for v4 port.
 *
 * The v3 fragment (1400 lines) relies on v3-specific event classes and DataBinding
 * to boost_overview_fragment.xml. This stub shows a placeholder while the full UI
 * is being adapted. Selecting "Use Boost Overview" will display this placeholder
 * instead of crashing; the AIMI dashboard remains the default.
 */
class BoostOverviewFragment : DaggerFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tv = TextView(requireContext()).apply {
            text = "█████ BOOST OVERVIEW ACTIVE █████\n\nBOOST algorithm (V1 + V5) is available in ConfigBuilder → APS.\nFull BOOST dashboard with BG bobble, DynISF, and tier display\ncoming in a future update."
            textSize = 18f
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1B5E20.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        return tv
    }
}
