package app.aaps.plugins.main.general.dashboard
import kotlinx.coroutines.runBlocking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.overview.OverviewMenus.CharType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UIRunnable
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.FragmentDashboardBinding
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationUiBinder
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import dagger.android.support.DaggerFragment
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStatusLiveData
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorNotificationManager
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStatusIndicator
import javax.inject.Inject
import javax.inject.Provider
import app.aaps.plugins.main.general.dashboard.views.CircleTopActionListener
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity
import app.aaps.plugins.main.general.dashboard.modes.DashboardModesController
import android.view.MotionEvent
import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import app.aaps.core.interfaces.plugin.PluginBase
// OKDialog import removed
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.model.AuditorUIState
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState

class DashboardFragment : DaggerFragment() {

    @Inject lateinit var lastBgData: LastBgData
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var loop: Loop
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var graphDataProvider: Provider<GraphData>
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var automation: Automation
    @Inject lateinit var notificationUiBinder: NotificationUiBinder
    @Inject lateinit var auditorStatusLiveData: AuditorStatusLiveData
    @Inject lateinit var auditorNotificationManager: AuditorNotificationManager
    @Inject lateinit var trajectoryGuard: app.aaps.plugins.aps.openAPSAIMI.trajectory.TrajectoryGuard // 🌀 Trajectory Injection
    @Inject lateinit var autodriveEngine: app.aaps.plugins.aps.openAPSAIMI.autodrive.AutodriveEngine // 🧠 Engine Injection
    @Inject lateinit var activityProvider: app.aaps.plugins.aps.openAPSAIMI.steps.UnifiedActivityProviderMTR

    private lateinit var modesController: DashboardModesController
    private var availableAutomationEvents: List<AutomationEvent> = emptyList()

    private val disposables = CompositeDisposable()
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private var currentRange = 0
    private var auditorIndicator: AuditorStatusIndicator? = null
    private var graphViewportLayoutListener: View.OnLayoutChangeListener? = null


    private val viewModel: OverviewViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)

        binding.bottomNavigation.selectedItemId = R.id.dashboard_nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.dashboard_nav_home -> {
                    true
                }
                R.id.dashboard_nav_history -> {
                    openHistory()
                }
                R.id.dashboard_nav_bolus -> {
                    openBolus()
                }
                R.id.dashboard_nav_adjustments -> {
                    openModes()
                }
                R.id.dashboard_nav_settings -> {
                    openSensorApp()
                }
                else -> true
            }
        }

        binding.overviewNotifications.layoutManager = LinearLayoutManager(context)

        syncGraphRange(preferences.get(IntNonKey.RangeToDisplay), false)
        viewModel.statusCardState.observe(viewLifecycleOwner) {
            binding.statusCard.updateWithState(it)
            binding.pulseView.update(it)
        }

        //new DashboardModeView
        modesController = DashboardModesController(
            requireActivity(),
            automation,
            resourceHelper,
            rxBus,
            aapsSchedulers
        )

        bindModes()
        //END modes stuff

        viewModel.graphMessage.observe(viewLifecycleOwner) {
            binding.glucoseGraph.setUpdateMessage(it)
            updateGraph()
        }

        binding.statusCard.isClickable = true
        binding.statusCard.isFocusable = true

        // Setup Action Listeners (Advisor, Adjust, Prefs, Stats)
        binding.statusCard.setActionListener(object : CircleTopActionListener {
            override fun onAimiAdvisorClicked() {
                try {
                    val intent = Intent(requireContext(), AimiProfileAdvisorActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to launch Advisor: ${e.message}")
                }
            }
            override fun onAdjustClicked()
                {
                    openAdjustmentDetails()
            }
            override fun onAimiPreferencesClicked() {
                // PreferencesActivity expects UiInteraction.PLUGIN_NAME = plugin class simpleName.
                val pluginName = resolveAimiPluginName() ?: run {
                    aapsLogger.error(LTag.CORE, "AIMI Pref: Plugin name could not be resolved")
                }
                protectionCheck.queryProtection(requireActivity(), ProtectionCheck.Protection.PREFERENCES, {
                    val intent = Intent(requireContext(), uiInteraction.preferencesActivity)
                        .setAction("info.nightscout.androidaps.MainActivity")
                        .putExtra(UiInteraction.PLUGIN_NAME, pluginName as String)
                    startActivity(intent)
                })
            }
            override fun onStatsClicked() {
                try {
                    val c = Class.forName("app.aaps.ui.activities.StatsActivity")
                    startActivity(Intent(requireContext(), c).setAction("info.nightscout.androidaps.MainActivity"))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to launch ContextActivity: ${e.message}")
                }
            }

            override fun onAimiFoodClicked() {
                try {
                    val intent = Intent().setClassName(requireContext(), "app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to launch MealAdvisorActivity: ${e.message}")
                }
            }

            override fun onAimiPulseClicked() {
                try {
                    val intent = Intent().setClassName(requireContext(), "app.aaps.plugins.aps.openAPSAIMI.advisor.pulse.AimiPulseDetailActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to launch AimiPulseDetailActivity: ${e.message}")
                }
            }

            override fun onAimiContextClicked() {
                try {
                    val intent = Intent().setClassName(requireContext(), "app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to launch ContextActivity: ${e.message}")
                }
            }

            private fun resolveAimiPluginName(): String? {

                val candidates = listOf(
                    "app.aaps.plugins.aps.openAPSAIMI.OpenAPSAIMIPlugin",
                    "app.aaps.plugins.aps.openAPSAIMI.OpenApsAIMIPlugin",
                    "app.aaps.plugins.aps.openAPSAIMI.OpenAPSAIMI",
                    "app.aaps.plugins.aps.openAPSAIMI.OpenApsAIMI",
                    "app.aaps.plugins.aps.openAPSAIMI.AimiPlugin",
                )
                for (cn in candidates) {
                    try {
                        val c = Class.forName(cn)
                        return c.simpleName
                    } catch (_: Throwable) { }
                }
                // last resort: some builds register the plugin under this name
                return "OpenAPSAIMIPlugin"
            }
        })

        // Loop Dialog on general click or specific indicator
        binding.statusCard.setOnClickListener { openLoopDialog() }
        binding.statusCard.getLoopIndicator().setOnClickListener { openLoopDialog() }

        // Context Indicator Click
        binding.statusCard.getContextIndicator().setOnClickListener {
            try {
                val intent = Intent().setClassName(requireContext(), "app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity")
                startActivity(intent)
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Failed to launch ContextActivity: ${e.message}")
            }
        }
        setupDashboardGraphChrome()
        /*
         * Glucose Graph
         */
        binding.glucoseGraph.graph.gridLabelRenderer?.gridColor = resourceHelper.gac(requireContext(), app.aaps.core.ui.R.attr.graphGrid)
        binding.glucoseGraph.graph.viewport.isScrollable = false
        binding.glucoseGraph.graph.viewport.isScalable = true
        binding.glucoseGraph.graph.setBackgroundColor(Color.TRANSPARENT)

        val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val nextRange = when (overviewData.rangeToDisplay) {
                    6    -> 9
                    9    -> 12
                    12   -> 18
                    18   -> 24
                    else -> 6
                }
                syncGraphRange(nextRange)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                syncGraphRange(6)
            }
        })

        binding.glucoseGraph.graph.viewport.apply {
            isScrollable = false          // No Scroll
            isScalable = true             // Zoom OK
        }
        binding.glucoseGraph.graph.viewport.setScalable(true)
        binding.glucoseGraph.graph.viewport.isXAxisBoundsManual = true

        @SuppressLint("ClickableViewAccessibility")
        binding.glucoseGraph.graph.setOnTouchListener { v, event ->

            // Accessibility
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performClick()
            }

            // Evaluate only Tap / LongPress / DoubleTap
            val handledByGesture = gestureDetector.onTouchEvent(event)

            // IMPORTANT:
            // - No requestDisallowInterceptTouchEvent
            // - Do NOT block MOVE events
            // - Zoom remains internal to GraphView
            handledByGesture
        }


        binding.glucoseGraph.graph.gridLabelRenderer?.reloadStyles()

        // Setup range selection button
        bindDashboardGraphHeightToViewport()

        binding.glucoseGraph.rangeButton.setOnClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
            popup.menu.add(android.view.Menu.NONE, 6, android.view.Menu.NONE, getString(R.string.graph_long_scale_6h))
            popup.menu.add(android.view.Menu.NONE, 9, android.view.Menu.NONE, getString(R.string.graph_long_scale_9h))
            popup.menu.add(android.view.Menu.NONE, 12, android.view.Menu.NONE, getString(R.string.graph_long_scale_12h))
            popup.menu.add(android.view.Menu.NONE, 18, android.view.Menu.NONE, getString(R.string.graph_long_scale_18h))
            popup.menu.add(android.view.Menu.NONE, 24, android.view.Menu.NONE, getString(R.string.graph_long_scale_24h))
            popup.setOnMenuItemClickListener { item ->
                syncGraphRange(item.itemId)
                true
            }
            popup.show()
        }

        // 🔍 Setup Auditor badge
        setupAuditorIndicator()
    }

    private fun bindModes() {
        availableAutomationEvents =
            automation.userEvents()
                .filter { it.isEnabled }
                .take(10)

        binding.modesView.setButtons(
            availableAutomationEvents.map { it.title }
        )

        binding.modesView.setOnButtonClickListener { index ->
            aapsLogger.debug(LTag.CORE,"RBarth: bindModes called by button press")
            val event = availableAutomationEvents.getOrNull(index) ?: return@setOnButtonClickListener
            modesController.runEventWithConfirmation(event)
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("DashboardFragment", "onStart")
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    override fun onResume() {
        super.onResume()
        Log.d("DashboardFragment", "onResume")
        updateContextBadge()
        // viewModel.start() - removed

        //bind / refresh mode buttons in case of a background change
        bindModes()

        notificationUiBinder.bind(
            overviewBus = activePlugin.activeOverview.overviewBus,
            notificationsView = binding.overviewNotifications,
            disposable = disposables,
        )
        disposables += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event ->
                           if (true) {
                               syncGraphRange(preferences.get(IntNonKey.RangeToDisplay), false)
                           }
                           if (true) {
                               updateContextBadge()
                           }
                       }, fabricPrivacy::logException)

    }

    private fun updateContextBadge() {
        try {
            val jsonStr = preferences.get(app.aaps.core.keys.StringKey.OApsAIMIContextStorage)
            val hasContext = jsonStr.length > 5 // "[]" length is 2
            binding.statusCard.getContextIndicator().visibility = if (hasContext) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Failed to update context badge: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        // viewModel.stop() - removed
        disposables.clear()
    }

    override fun onDestroyView() {
        graphViewportLayoutListener?.let { listener ->
            _binding?.root?.getChildAt(0)?.let { child ->
                (child as? NestedScrollView)?.removeOnLayoutChangeListener(listener)
            }
        }
        graphViewportLayoutListener = null
        super.onDestroyView()
        auditorIndicator?.stopAnimations()
        auditorIndicator = null
        _binding = null
    }

    /** Same Y-axis gutter logic as [app.aaps.plugins.main.general.overview.OverviewFragment] so labels are not cramped. */
    private fun graphAxisWidthPx(): Int = when {
        resources.displayMetrics.densityDpi <= 120 -> 3
        resources.displayMetrics.densityDpi <= 160 -> 10
        resources.displayMetrics.densityDpi <= 320 -> 35
        resources.displayMetrics.densityDpi <= 420 -> 50
        resources.displayMetrics.densityDpi <= 560 -> 70
        else -> 80
    }

    private fun setupDashboardGraphChrome() {
        val ctx = context ?: return
        val graph = binding.glucoseGraph.graph
        graph.gridLabelRenderer?.labelVerticalWidth = graphAxisWidthPx()
        graph.gridLabelRenderer?.gridColor = resourceHelper.gac(ctx, app.aaps.core.ui.R.attr.graphGrid)
        graph.viewport.backgroundColor = resourceHelper.gac(ctx, app.aaps.core.ui.R.attr.viewPortBackgroundColor)
        graph.gridLabelRenderer?.reloadStyles()
    }

    /**
     * Graph height scales with the visible scroll viewport (rotation, split-screen, different tallies),
     * clamped between [R.dimen.dashboard_graph_height_min] and [R.dimen.dashboard_graph_height_max].
     */
    private fun bindDashboardGraphHeightToViewport() {
        val sv = binding.root.getChildAt(0) as? NestedScrollView ?: return
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyDashboardGraphHeight(sv)
        }
        graphViewportLayoutListener = listener
        sv.addOnLayoutChangeListener(listener)
        sv.post { applyDashboardGraphHeight(sv) }
    }

    private fun applyDashboardGraphHeight(scrollView: NestedScrollView) {
        if (_binding == null) return
        val viewportH = scrollView.height
        if (viewportH <= 0) return
        val minPx = resources.getDimensionPixelSize(R.dimen.dashboard_graph_height_min)
        val maxPx = resources.getDimensionPixelSize(R.dimen.dashboard_graph_height_max)
        val fraction = resources.getFraction(R.fraction.dashboard_graph_viewport_fraction, 1, 1)
        val raw = (viewportH * fraction).toInt()
        // Cap below full viewport so the status block can scroll; slightly relaxed vs 50% to fit X-axis labels.
        val viewportCapPx = (viewportH * 0.50f).toInt()
        val effectiveMaxPx = minOf(maxPx, viewportCapPx)
        val effectiveMinPx = minOf(minPx, effectiveMaxPx)
        val target = raw.coerceIn(effectiveMinPx, effectiveMaxPx)
        val lp = binding.glucoseGraph.layoutParams
        if (lp.height == target) return
        lp.height = target
        binding.glucoseGraph.layoutParams = lp
        binding.glucoseGraph.requestLayout()
        binding.root.post {
            if (_binding != null) {
                setupDashboardGraphChrome()
                updateGraph()
            }
        }
    }

    private fun setupAuditorIndicator() {
        try {
            aapsLogger.debug(LTag.CORE, "🔍 [Dashboard] Searching for Auditor badge...")

            val container = binding.statusCard.getAuditorContainer()

            aapsLogger.debug(LTag.CORE, "✅ [Dashboard] Badge container found!")

            auditorIndicator = AuditorStatusIndicator(requireContext())
            container.removeAllViews()
            container.addView(auditorIndicator)

            auditorIndicator?.setOnClickListener {
                aapsLogger.debug(LTag.CORE, "Auditor badge clicked")
                handleAuditorClick()
            }

            auditorStatusLiveData.uiState.observe(viewLifecycleOwner) { uiState ->
                auditorIndicator?.setState(uiState)
                if (uiState.shouldNotify) {
                    auditorNotificationManager.showInsightAvailable(uiState)
                }
                container.visibility = View.VISIBLE
                aapsLogger.debug(LTag.CORE, "[Dashboard] Badge state: ${uiState.type}")
            }

            auditorStatusLiveData.forceUpdate()

        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "[Dashboard] Badge setup error: ${e.message}", e)
        }
    }

    private fun handleAuditorClick() {
        val state = auditorIndicator?.getCurrentState()

        if (state == null) {
            aapsLogger.debug(LTag.CORE, "Auditor click: state is null")
            return
        }

        aapsLogger.debug(
            LTag.CORE,
            "Auditor click: type=${state.type}, message=${state.statusMessage}"
        )

        when (state.type) {
            AuditorUIState.StateType.READY,
            AuditorUIState.StateType.WARNING -> {
                aapsLogger.debug(LTag.CORE, "Auditor click: entering READY/WARNING branch")

                auditorStatusLiveData.markAsRead()
                auditorNotificationManager.cancelNotification()

                val intent = Intent(
                    requireContext(),
                    app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorVerdictActivity::class.java
                )

                aapsLogger.debug(LTag.CORE, "Auditor click: starting AuditorVerdictActivity")
                startActivity(intent)
                aapsLogger.debug(LTag.CORE, "Auditor click: startActivity returned")
            }

            AuditorUIState.StateType.PROCESSING -> {
                aapsLogger.debug(LTag.CORE, "Auditor click: PROCESSING branch")
                activity?.let { activity ->
                    uiInteraction.showOkDialog(requireContext(), "Auditor", "Analysis in progress, please wait...")
                }
            }

            AuditorUIState.StateType.ERROR -> {
                aapsLogger.debug(LTag.CORE, "Auditor click: ERROR branch")
                activity?.let { activity ->
                    uiInteraction.showOkDialog(requireContext(), resourceHelper.gs(app.aaps.core.ui.R.string.error), state.statusMessage
                    )
                }
            }

            else -> {
                aapsLogger.debug(LTag.CORE, "Auditor click: ELSE/IDLE branch")
                activity?.let { activity ->
                    uiInteraction.showOkDialog(requireContext(), "Auditor", "Auditor will activate at next trigger")
                }
            }
        }
    }

    private fun openHistory(): Boolean {
        // History view not available
        return true
    }

    private fun openBolus(): Boolean {
        activity?.let { activity ->
            protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                uiInteraction.runInsulinDialog(childFragmentManager)
            })
        }
        return true
    }

    private fun openModes(): Boolean {
        val context = context ?: return false
        startActivity(Intent(context, DashboardModesActivity::class.java))
        return true
    }

    private fun openLoopDialog() {
        activity?.let { activity ->
            protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 0)
            })
        }
    }

    private fun openSensorApp(): Boolean {
        val ctx = context ?: run {
            aapsLogger.debug(LTag.CORE, "Context is null, cannot open sensor app")
            return false
        }

        val possiblePackages = listOf(
            "com.eveningoutpost.dexdrip",
            "tk.glucodata",
            "com.dexcom.g6byod",
            "com.dexcom.g7byod"
        )

        val pm = ctx.packageManager

        for (pkg in possiblePackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ctx.startActivity(intent)
                aapsLogger.debug(LTag.CORE, "Launched CGM app: $pkg")
                return true
            }
        }
        aapsLogger.debug(LTag.CORE, "No known CGM app installed.")
        return false
    }


    private fun openAdjustmentDetails(): Boolean {
        val context = context ?: return false
        val state: AdjustmentCardState = viewModel.adjustmentState.value ?: return false
        val intent = Intent(context, AdjustmentDetailsActivity::class.java)
            .putExtra(AdjustmentDetailsActivity.EXTRA_ADJUSTMENT_STATE, state)
        startActivity(intent)
        return true
    }
    private fun syncGraphRange(hours: Int, userInitiated: Boolean = true) {
        val clampedHours = when (hours) {
            6, 9, 12, 18, 24 -> hours
            else             -> 6
        }
        if (!userInitiated && clampedHours == currentRange) {
            binding.glucoseGraph.rangeButton.text = overviewMenus.scaleString(clampedHours)
            return
        }

        currentRange = clampedHours
        overviewData.rangeToDisplay = clampedHours
        overviewData.initRange()
        binding.glucoseGraph.rangeButton.text = overviewMenus.scaleString(clampedHours)
        preferences.put(IntNonKey.RangeToDisplay, clampedHours)
        preferences.put(BooleanNonKey.ObjectivesScaleUsed, true)
        rxBus.send(EventConfigBuilderChange())
        if (userInitiated) {
            app.aaps.core.ui.toast.ToastUtils.infoToast(context, getString(R.string.graph_range_updated, clampedHours))
        }
    }

    private fun updateGraph() {
        if (_binding == null) return
        val menuChartSettings = overviewMenus.setting
        if (menuChartSettings.isEmpty()) return
        val graphData = graphDataProvider.get().with(binding.glucoseGraph.graph, overviewData)
        val now = dateUtil.now()

        val hasBgData = overviewData.bgReadingsArray.isNotEmpty()
        binding.glucoseGraph.showPlaceholder(!hasBgData)
        if (!hasBgData) {
            aapsLogger.debug(LTag.CORE, "Dashboard graph skipped: no BG data")
            return
        }

        graphData.addInRangeArea(
            overviewData.fromTime,
            overviewData.endTime,
            preferences.get(UnitDoubleKey.OverviewLowMark),
            preferences.get(UnitDoubleKey.OverviewHighMark)
        )
        graphData.addBgReadings(menuChartSettings[0][CharType.PRE.ordinal], context)
        graphData.addBucketedData()
        graphData.addTreatments(context)
        if ((config.AAPSCLIENT || activePlugin.activePump.pumpDescription.isTempBasalCapable) && menuChartSettings[0][CharType.BAS.ordinal]) {
            graphData.addBasals()
        }

        //added treatments (sport, meal,... + restarts)
        graphData.addEps(context, 0.95)
        if (menuChartSettings[0][CharType.TREAT.ordinal])
            graphData.addTherapyEvents()
        // add predictions
        if (menuChartSettings[0][CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        // adding HR and Steps based on menu settings
        val g = 0
        if (menuChartSettings.size > g + 1) {
            val settings = menuChartSettings[g + 1]
            // Add heart rate (HR) if enabled in the menu
            if (settings[CharType.HR.ordinal]) {
                graphData.addHeartRate(false, 0.8)
                Log.d("Dashboard", "HR added to graph")
            }
            // Add steps (STEPS) if enabled in the menu2
            if (settings[CharType.STEPS.ordinal]) {
                graphData.addSteps(false, 0.8)
                Log.d("Dashboard", "Steps added to graph")
            }
        }
        else
        {
            Log.d("Dashboard", "menuChartSettings.size = ${menuChartSettings.size} ")
        }

        graphData.addTargetLine()
        graphData.addRunningModes()
        graphData.addNowLine(now)
        graphData.setNumVerticalLabels()
        graphData.formatAxis(overviewData.fromTime, overviewData.endTime)
        graphData.performUpdate()
    }

    private var isHypoRiskDialogShowing = false

    private fun showHypoRiskDialog() {
        if (isHypoRiskDialogShowing) return
        isHypoRiskDialogShowing = true
        uiInteraction.showOkDialog(requireContext(), getString(R.string.hypo_risk_notification_title), getString(R.string.hypo_risk_notification_text)) {
            isHypoRiskDialogShowing = false
        }
    }
}
