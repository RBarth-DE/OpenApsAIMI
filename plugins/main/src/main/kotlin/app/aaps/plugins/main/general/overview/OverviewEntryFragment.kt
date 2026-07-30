package app.aaps.plugins.main.general.overview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.main.databinding.FragmentOverviewEntryBinding
import app.aaps.plugins.main.general.dashboard.DashboardFragment
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class OverviewEntryFragment : DaggerFragment() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsLogger: AAPSLogger

    private var _binding: FragmentOverviewEntryBinding? = null
    private val binding get() = _binding!!
    private val disposable = CompositeDisposable()
    private var currentTag: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOverviewEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentTag = childFragmentManager.findFragmentById(binding.overviewEntryContainer.id)?.tag
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
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.isChanged(BooleanKey.OverviewUseDashboardLayout.key) ||
                               it.isChanged(BooleanKey.OverviewUseBoostOverview.key)) {
                               aapsLogger.debug(LTag.UI, "OverviewEntryFragment pref change: dashboard=${it.isChanged(BooleanKey.OverviewUseDashboardLayout.key)} boost=${it.isChanged(BooleanKey.OverviewUseBoostOverview.key)}")
                               showSelectedOverview()
                           }
                       }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
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

        val fragment: DaggerFragment
        val newTag: String
        when {
            useBoostOverview -> {
                aapsLogger.debug(LTag.UI, "→ BOOST Overview")
                fragment = app.aaps.plugins.main.general.overview.boost.BoostOverviewFragment()
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
            replace(binding.overviewEntryContainer.id, fragment, newTag)
        }
        currentTag = newTag
    }

    companion object {
        private const val DASHBOARD_TAG = "overview_dashboard"
        private const val OVERVIEW_TAG = "overview_legacy"
    }
}
