package app.aaps.plugins.main.general.overview.boost

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dagger.android.support.DaggerFragment

/** BOOST Overview V2 — stub for v4 port. See [BoostOverviewFragment]. */
class BoostOverviewV2Fragment : DaggerFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tv = TextView(requireContext()).apply {
            text = "BOOST Overview V2 — port in progress."
            textSize = 16f; setPadding(48, 48, 48, 48)
        }
        return tv
    }
}
