package app.aaps.plugins.main.di

import android.content.Context
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorNotificationManager
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStatusLiveData
import app.aaps.plugins.aps.openAPSAIMI.autodrive.AutodriveEngine
import app.aaps.plugins.aps.openAPSAIMI.physio.AIMIPhysioDataRepositoryMTR
import app.aaps.plugins.aps.openAPSAIMI.trajectory.TrajectoryGuard
import app.aaps.plugins.main.general.dashboard.DashboardShellDeps
import app.aaps.plugins.main.general.dashboard.viewmodel.AimiAdaptationStatusViewModel
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.plugins.main.general.overview.OverviewMenusImpl
import app.aaps.plugins.main.general.overview.OverviewPlugin
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationUiBinder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.binding

/**
 * Fork dashboard providers, migrated from the Hilt OverviewModule.
 * OverviewData is bound by [app.aaps.implementation.overview.OverviewDataImpl]'s @ContributesBinding;
 * activity/fragment injection moved to [MainMemberInjectors].
 */
@ContributesTo(AppScope::class)
@BindingContainer
object OverviewModule {

    @Provides
    fun providesGraphData(
        profileFunction: ProfileFunction,
        preferences: Preferences,
        rh: ResourceHelper
    ): GraphData = GraphData(profileFunction, preferences, rh)

    @Provides
    fun provideOverviewViewModelFactory(
        context: Context,
        lastBgData: LastBgData,
        trendCalculator: TrendCalculator,
        iobCobCalculator: IobCobCalculator,
        glucoseStatusProvider: GlucoseStatusProvider,
        profileUtil: ProfileUtil,
        profileFunction: ProfileFunction,
        resourceHelper: ResourceHelper,
        dateUtil: DateUtil,
        loop: Loop,
        processedTbrEbData: ProcessedTbrEbData,
        persistenceLayer: PersistenceLayer,
        decimalFormatter: DecimalFormatter,
        activePlugin: ActivePlugin,
        rxBus: RxBus,
        aapsSchedulers: AapsSchedulers,
        fabricPrivacy: FabricPrivacy,
        preferences: Preferences,
        overviewData: OverviewData,
        trajectoryGuard: TrajectoryGuard,
        autodriveEngine: AutodriveEngine,
        aimiPhysioDataRepository: AIMIPhysioDataRepositoryMTR,
    ): OverviewViewModel.Factory = OverviewViewModel.Factory(
        context,
        lastBgData,
        trendCalculator,
        iobCobCalculator,
        glucoseStatusProvider,
        profileUtil,
        profileFunction,
        resourceHelper,
        dateUtil,
        loop,
        processedTbrEbData,
        persistenceLayer,
        decimalFormatter,
        activePlugin,
        rxBus,
        aapsSchedulers,
        fabricPrivacy,
        preferences,
        overviewData,
        trajectoryGuard,
        autodriveEngine,
        aimiPhysioDataRepository,
    )

    @Provides
    fun provideAimiAdaptationStatusViewModelFactory(
        loop: Loop,
        rxBus: RxBus,
        preferences: Preferences,
    ): AimiAdaptationStatusViewModel.Factory =
        AimiAdaptationStatusViewModel.Factory(loop, rxBus, preferences)

    @Provides
    fun provideDashboardShellDeps(
        overviewViewModelFactory: OverviewViewModel.Factory,
        resourceHelper: ResourceHelper,
        decimalFormatter: DecimalFormatter,
        preferences: Preferences,
        dateUtil: DateUtil,
        loop: Loop,
        activePlugin: ActivePlugin,
        rxBus: RxBus,
        aapsSchedulers: AapsSchedulers,
        fabricPrivacy: FabricPrivacy,
        overviewData: OverviewData,
        overviewMenus: OverviewMenus,
        graphDataProvider: () -> GraphData,
        config: Config,
        protectionCheck: ProtectionCheck,
        uiInteraction: UiInteraction,
        aapsLogger: AAPSLogger,
        xDripSource: XDripSource,
        dexcomBoyda: DexcomBoyda,
        notificationUiBinder: NotificationUiBinder,
        auditorStatusLiveData: AuditorStatusLiveData,
        auditorNotificationManager: AuditorNotificationManager,
        overviewDataCache: () -> OverviewDataCache,
        persistenceLayer: PersistenceLayer,
    ): DashboardShellDeps = DashboardShellDeps(
        overviewViewModelFactory = overviewViewModelFactory,
        resourceHelper = resourceHelper,
        decimalFormatter = decimalFormatter,
        preferences = preferences,
        dateUtil = dateUtil,
        loop = loop,
        activePlugin = activePlugin,
        rxBus = rxBus,
        aapsSchedulers = aapsSchedulers,
        fabricPrivacy = fabricPrivacy,
        overviewData = overviewData,
        overviewMenus = overviewMenus,
        graphDataProvider = graphDataProvider,
        config = config,
        protectionCheck = protectionCheck,
        uiInteraction = uiInteraction,
        aapsLogger = aapsLogger,
        xDripSource = xDripSource,
        dexcomBoyda = dexcomBoyda,
        notificationUiBinder = notificationUiBinder,
        auditorStatusLiveData = auditorStatusLiveData,
        auditorNotificationManager = auditorNotificationManager,
        overviewDataCache = overviewDataCache,
        persistenceLayer = persistenceLayer,
    )

    @Provides
    fun bindOverviewMenus(overviewMenusImpl: OverviewMenusImpl): OverviewMenus = overviewMenusImpl

    @Provides
    fun bindOverview(overviewPlugin: OverviewPlugin): Overview = overviewPlugin
}
