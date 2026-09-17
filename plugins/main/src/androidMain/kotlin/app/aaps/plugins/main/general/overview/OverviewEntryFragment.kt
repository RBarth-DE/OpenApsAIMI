package app.aaps.plugins.main.general.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.DashboardFragment
import app.aaps.plugins.main.general.overview.boost.BoostOverviewFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import dev.zacsweers.metro.Inject

class OverviewEntryFragment : Fragment() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger

    // The layout is a single FrameLayout, so the root view is the container itself. The views are
    // found by hand: a Kotlin Multiplatform module never gets generated view binding classes.
    private var _binding: View? = null
    private val binding get() = _binding!!

    /**
     * Runs the bus subscription. A RxBus subscription is a Flow now, so it lives in a scope rather
     * than in a RxJava disposable; [onStop] cancels it at the point the disposable used to be cleared.
     */
    private var subscriptionScope: CoroutineScope? = null
    private var currentTag: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = inflater.inflate(R.layout.fragment_overview_entry, container, false)
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentTag = childFragmentManager.findFragmentById(R.id.overview_entry_container)?.tag
        showSelectedOverview()
    }

    override fun onResume() {
        super.onResume()
        // Always re-check when the tab becomes visible — catches preference changes
        // that may not have fired EventPreferenceChange for the BOOST keys.
        aapsLogger.debug(LTag.UI, "OverviewEntryFragment.onResume — re-checking overview selection")
        showSelectedOverview()
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(Dispatchers.Main + Job())
        subscriptionScope = scope
        // The bus has no replay, so the collector has to subscribe before this call returns.
        rxBus.toFlow(EventPreferenceChange::class)
            .collectResilient(scope, aapsLogger, LTag.UI, start = CoroutineStart.UNDISPATCHED) { event ->
                if (event.isChanged(BooleanKey.OverviewUseDashboardLayout.key) ||
                    event.isChanged(BooleanKey.OverviewUseBoostOverview.key)) {
                    aapsLogger.debug(LTag.UI, "OverviewEntryFragment pref change: dashboard=${event.isChanged(BooleanKey.OverviewUseDashboardLayout.key)} boost=${event.isChanged(BooleanKey.OverviewUseBoostOverview.key)}")
                    showSelectedOverview()
                }
            }
    }

    override fun onStop() {
        subscriptionScope?.cancel()
        subscriptionScope = null
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun showSelectedOverview() {
        val binding = _binding ?: return
        val useDashboard = preferences.get(BooleanKey.OverviewUseDashboardLayout)
        val useBoostOverview = preferences.get(BooleanKey.OverviewUseBoostOverview)

        aapsLogger.debug(LTag.UI, "showSelectedOverview: dashboard=$useDashboard boost=$useBoostOverview currentTag=$currentTag")

        val fragment: Fragment
        val newTag: String
        when {
            useBoostOverview -> {
                aapsLogger.debug(LTag.UI, "→ BOOST Overview")
                fragment = BoostOverviewFragment()
                newTag = "overview_boost"
            }
            useDashboard -> {
                aapsLogger.debug(LTag.UI, "→ Dashboard")
                fragment = DashboardFragment()
                newTag = DASHBOARD_TAG
            }
            else -> {
                aapsLogger.debug(LTag.UI, "→ Legacy Overview")
                fragment = OverviewFragment()
                newTag = OVERVIEW_TAG
            }
        }

        if (newTag == currentTag && childFragmentManager.findFragmentByTag(newTag) != null) {
            aapsLogger.debug(LTag.UI, "showSelectedOverview: skipped (already showing $newTag)")
            return
        }

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.overview_entry_container, fragment, newTag)
        }
        currentTag = newTag
    }

    companion object {
        private const val DASHBOARD_TAG = "overview_dashboard"
        private const val OVERVIEW_TAG = "overview_legacy"
    }
}
