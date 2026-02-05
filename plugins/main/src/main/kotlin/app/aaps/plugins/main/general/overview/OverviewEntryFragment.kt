package app.aaps.plugins.main.general.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
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
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

class OverviewEntryFragment : DaggerFragment() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy

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
        currentTag = childFragmentManager
            .findFragmentById(binding.overviewEntryContainer.id)
            ?.tag

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                showSelectedOverview()
            }
        }
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        showSelectedOverview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun showSelectedOverview() {

        val b = _binding ?: return
        if (!isAdded || view == null) {
            Log.d("OverviewEntryFragment", "ABORT: isAdded == $isAdded || view == null")
            return
        }
        val container = b.overviewEntryContainer
        if (container.parent == null) {
            Log.d("OverviewEntryFragment", "ABORT: container.parent == null")
            return
        }


        val useDashboard = preferences.get(BooleanKey.OverviewUseDashboardLayout)
        val newTag = if (useDashboard) DASHBOARD_TAG else OVERVIEW_TAG
        if (newTag == currentTag && childFragmentManager.findFragmentByTag(newTag) != null) {
            Log.d("OverviewEntryFragment", "Fragment $currentTag is already active or childFragmentManager not found")
            return
        }

        val fragment = if (useDashboard) DashboardFragment() else OverviewFragment()
        try {
            childFragmentManager.commit {
                setReorderingAllowed(true)
                // Use the ID directly from the container object
                replace(container.id, fragment, newTag)
            }
            currentTag = newTag
            Log.d("OverviewEntryFragment", "Successfully replaced by $newTag")
        } catch (e: Exception) {
            Log.e("OverviewEntryFragment", "CRASH prevented: ${e.message}")
        }
    }

    companion object {
        private const val DASHBOARD_TAG = "overview_dashboard"
        private const val OVERVIEW_TAG = "overview_legacy"
    }
}
