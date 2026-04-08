package app.aaps.plugins.main.general.dashboard
import kotlinx.coroutines.runBlocking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import app.aaps.core.graph.data.GraphViewWithCleanup
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.configuration.Config
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
import app.aaps.plugins.main.general.dashboard.views.DashboardModesView
import app.aaps.plugins.main.general.dashboard.views.DashboardPulseView
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationUiBinder
import app.aaps.plugins.main.general.overview.views.OverviewTirView
import app.aaps.plugins.main.skins.SkinProvider
import com.jjoe64.graphview.GraphView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import app.aaps.core.ui.extensions.toVisibility
import kotlin.math.min
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
    @Inject lateinit var skinProvider: SkinProvider

    private lateinit var modesController: DashboardModesController
    private var availableAutomationEvents: List<AutomationEvent> = emptyList()

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()
    private val secondaryModesViews = ArrayList<DashboardModesView>()
    private val secondaryPulseViews = ArrayList<DashboardPulseView>()
    private val secondaryTirViews = ArrayList<OverviewTirView>()

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
        // Action listeners removed

        // Loop Dialog on general click or specific indicator
        binding.statusCard.setOnClickListener { openLoopDialog() }
        // getLoopIndicator removed

        // Context Indicator Click
        // getContextIndicator click - removed
        run {
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

        // Setup chart type menu + range scale button
        overviewMenus.setupChartMenu(binding.glucoseGraph.chartMenuButton, binding.glucoseGraph.rangeButton)

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
            null // getContextIndicator removed.visibility = if (hasContext) View.VISIBLE else View.GONE
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
        for (graph in secondaryGraphs) {
            graph.setOnLongClickListener(null)
            graph.removeAllSeries()
        }
        secondaryGraphs.clear()
        secondaryGraphsLabel.clear()
        secondaryModesViews.clear()
        secondaryPulseViews.clear()
        secondaryTirViews.clear()
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

            val container = null // getAuditorContainer removed

            // Auditor badge removed

            auditorStatusLiveData.uiState.observe(viewLifecycleOwner) { uiState ->
                auditorIndicator?.setState(uiState)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Failed to update Auditor badge: ${e.message}")
        }
    }

    private fun handleAuditorClick() {
        val state = auditorIndicator?.getCurrentState()

        if (state == null) {
            aapsLogger.debug(LTag.CORE, "Auditor click: state is null ")
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

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        if (numOfGraphs != secondaryGraphs.size - 1) {
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            secondaryModesViews.clear()
            secondaryPulseViews.clear()
            secondaryTirViews.clear()
            binding.secondaryGraphs.removeAllViews()
            val slotHeight = resourceHelper.dpToPx(skinProvider.activeSkin().secondaryGraphHeight)
            val slotMargins: LinearLayout.LayoutParams.() -> Unit = { setMargins(0, resourceHelper.dpToPx(15), 0, resourceHelper.dpToPx(10)) }
            (1 until numOfGraphs).forEach { _ ->
                val relativeLayout = RelativeLayout(context)
                relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val graph = GraphViewWithCleanup(requireContext())
                graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, slotHeight).also(slotMargins)
                graph.gridLabelRenderer?.gridColor = resourceHelper.gac(context, app.aaps.core.ui.R.attr.graphGrid)
                graph.gridLabelRenderer?.reloadStyles()
                graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                graph.gridLabelRenderer?.labelVerticalWidth = graphAxisWidthPx()
                graph.gridLabelRenderer?.numVerticalLabels = 3
                graph.viewport.backgroundColor = resourceHelper.gac(context, app.aaps.core.ui.R.attr.viewPortBackgroundColor)
                relativeLayout.addView(graph)

                val modesView = DashboardModesView(requireContext())
                modesView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, slotHeight).also(slotMargins)
                modesView.visibility = View.GONE
                relativeLayout.addView(modesView)
                secondaryModesViews.add(modesView)

                val pulseView = DashboardPulseView(requireContext())
                pulseView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, slotHeight).also(slotMargins)
                pulseView.visibility = View.GONE
                relativeLayout.addView(pulseView)
                secondaryPulseViews.add(pulseView)

                val tirView = OverviewTirView(requireContext())
                tirView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, slotHeight).also(slotMargins)
                tirView.visibility = View.GONE
                relativeLayout.addView(tirView)
                secondaryTirViews.add(tirView)

                val label = TextView(context)
                val labelParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(resourceHelper.dpToPx(30), resourceHelper.dpToPx(25), 0, 0) }
                labelParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                labelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                label.layoutParams = labelParams
                relativeLayout.addView(label)
                secondaryGraphsLabel.add(label)

                binding.secondaryGraphs.addView(relativeLayout)
                secondaryGraphs.add(graph)
            }
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
        graphData.addEps(context, 0.95)
        if (menuChartSettings[0][CharType.TREAT.ordinal])
            graphData.addTherapyEvents()
        if (menuChartSettings[0][CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        graphData.addTargetLine()
        graphData.addRunningModes()
        graphData.addNowLine(now)
        graphData.setNumVerticalLabels()
        graphData.formatAxis(overviewData.fromTime, overviewData.endTime)
        graphData.performUpdate()

        // Secondary graphs
        prepareGraphsIfNeeded(menuChartSettings.size)
        val secondaryGraphsData = ArrayList<GraphData>()
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size - 1)) {
            val settings = menuChartSettings[g + 1]
            val hasCustomView = settings[CharType.MODES.ordinal] || settings[CharType.PULSE.ordinal] || settings[CharType.TIR.ordinal]
            val secondGraphData = graphDataProvider.get().with(secondaryGraphs[g], overviewData)
            if (!hasCustomView) {
                var useABSForScale = false; var useIobForScale = false; var useCobForScale = false
                var useDevForScale = false; var useRatioForScale = false; var useVarSensForScale = false
                var useDSForScale = false; var useBGIForScale = false
                var useHRForScale = false; var useSTEPSForScale = false
                when {
                    settings[CharType.ABS.ordinal]      -> useABSForScale = true
                    settings[CharType.IOB.ordinal]      -> useIobForScale = true
                    settings[CharType.COB.ordinal]      -> useCobForScale = true
                    settings[CharType.DEV.ordinal]      -> useDevForScale = true
                    settings[CharType.BGI.ordinal]      -> useBGIForScale = true
                    settings[CharType.SEN.ordinal]      -> useRatioForScale = true
                    settings[CharType.VAR_SEN.ordinal]  -> useVarSensForScale = true
                    settings[CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                    settings[CharType.HR.ordinal]       -> useHRForScale = true
                    settings[CharType.STEPS.ordinal]    -> useSTEPSForScale = true
                }
                val alignDevBgi = settings[CharType.DEV.ordinal] && settings[CharType.BGI.ordinal]
                if (settings[CharType.ABS.ordinal]) secondGraphData.addAbsIob(useABSForScale, 1.0)
                if (settings[CharType.IOB.ordinal]) secondGraphData.addIob(useIobForScale, 1.0)
                if (settings[CharType.COB.ordinal]) secondGraphData.addCob(useCobForScale, if (useCobForScale) 1.0 else 0.5)
                if (settings[CharType.DEV.ordinal]) secondGraphData.addDeviations(useDevForScale, 1.0)
                if (settings[CharType.BGI.ordinal]) secondGraphData.addMinusBGI(useBGIForScale, if (alignDevBgi) 1.0 else 0.8)
                if (settings[CharType.SEN.ordinal]) secondGraphData.addRatio(useRatioForScale, if (useRatioForScale) 1.0 else 0.8)
                if (settings[CharType.VAR_SEN.ordinal]) secondGraphData.addVarSens(useVarSensForScale, if (useVarSensForScale) 1.0 else 0.8)
                if (settings[CharType.DEVSLOPE.ordinal] && config.isDev()) secondGraphData.addDeviationSlope(useDSForScale, if (useDSForScale) 1.0 else 0.8, useRatioForScale)
                if (settings[CharType.HR.ordinal]) secondGraphData.addHeartRate(useHRForScale, if (useHRForScale) 1.0 else 0.8)
                if (settings[CharType.STEPS.ordinal]) secondGraphData.addSteps(useSTEPSForScale, if (useSTEPSForScale) 1.0 else 0.8)
                secondGraphData.formatAxis(overviewData.fromTime, overviewData.endTime)
                secondGraphData.addNowLine(now)
            }
            secondaryGraphsData.add(secondGraphData)
        }

        var hasTirEnabled = false
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size - 1)) {
            val settings = menuChartSettings[g + 1]
            val hasModes = settings[CharType.MODES.ordinal]
            val hasPulse = settings[CharType.PULSE.ordinal]
            val hasTir = settings[CharType.TIR.ordinal]
            val hasCustomView = hasModes || hasPulse || hasTir

            secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)

            if (hasCustomView) {
                secondaryGraphs[g].visibility = View.GONE
                secondaryModesViews[g].visibility = if (hasModes) View.VISIBLE else View.GONE
                secondaryPulseViews[g].visibility = if (hasPulse && !hasModes) View.VISIBLE else View.GONE
                secondaryTirViews[g].visibility = if (hasTir && !hasModes && !hasPulse) View.VISIBLE else View.GONE
                when {
                    hasModes -> {
                        val events = automation.userEvents().filter { it.isEnabled }.take(10)
                        secondaryModesViews[g].setButtons(events.map { it.title })
                        secondaryModesViews[g].setOnButtonClickListener { index ->
                            val event = events.getOrNull(index) ?: return@setOnButtonClickListener
                            modesController.runEventWithConfirmation(event)
                        }
                    }
                    hasPulse -> {
                        val lastRun = loop.lastRun
                        val title = OverviewViewModel.buildAimiPulseTitle(lastRun?.lastAPSRun, dateUtil, resourceHelper)
                        val summary = OverviewViewModel.buildAimiPulseSummary(lastRun?.request, resourceHelper, decimalFormatter)
                        val meta = OverviewViewModel.buildAimiPulseMeta(lastRun?.request, resourceHelper, decimalFormatter)
                        val hypoRisk = (lastRun?.request?.rawData() as? RT)?.isHypoRisk == true
                        secondaryPulseViews[g].updatePulse(title, summary, meta, hypoRisk)
                    }
                }
                if (hasTir) hasTirEnabled = true
            } else {
                secondaryModesViews[g].visibility = View.GONE
                secondaryPulseViews[g].visibility = View.GONE
                secondaryTirViews[g].visibility = View.GONE
                secondaryGraphs[g].visibility = (
                    settings[CharType.ABS.ordinal] || settings[CharType.IOB.ordinal] ||
                        settings[CharType.COB.ordinal] || settings[CharType.DEV.ordinal] ||
                        settings[CharType.BGI.ordinal] || settings[CharType.SEN.ordinal] ||
                        settings[CharType.VAR_SEN.ordinal] || settings[CharType.DEVSLOPE.ordinal] ||
                        settings[CharType.HR.ordinal] || settings[CharType.STEPS.ordinal]
                    ).toVisibility()
                secondaryGraphsData[g].performUpdate()
            }
        }
        if (hasTirEnabled) updateTirViews(menuChartSettings)
    }

    private fun updateTirViews(menuChartSettings: List<Array<Boolean>>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val from = dateUtil.beginOfDay(now)
            val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(from, now, true)
            if (readings.isEmpty()) return@launch
            val values = readings.map { it.value }
            val count = values.size.toDouble()
            val vl = (values.count { it < 54.0 } / count) * 100.0
            val l = (values.count { it in 54.0..69.99 } / count) * 100.0
            val tr = (values.count { it in 70.0..180.0 } / count) * 100.0
            val h = (values.count { it in 180.01..250.0 } / count) * 100.0
            val vh = (values.count { it > 250.0 } / count) * 100.0
            val avg = values.average()
            val a1c = (avg + 46.7) / 28.7
            withContext(Dispatchers.Main) {
                _binding ?: return@withContext
                for (g in 0 until min(secondaryTirViews.size, menuChartSettings.size - 1)) {
                    val settings = menuChartSettings[g + 1]
                    if (settings[CharType.TIR.ordinal] && !settings[CharType.MODES.ordinal] && !settings[CharType.PULSE.ordinal]) {
                        secondaryTirViews[g].update(vl, l, tr, h, vh, avg, a1c)
                    }
                }
            }
        }
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

