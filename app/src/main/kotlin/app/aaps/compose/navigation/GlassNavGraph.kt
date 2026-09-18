package app.aaps.compose.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.aaps.appshell.navigation.AppRoute
import app.aaps.appshell.navigation.safePopBackStack
import app.aaps.core.data.model.TE
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.plugins.main.general.dashboard.glass.GlassBasalDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassBatteryDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassCannulaDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassInsulinDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassLoopDashboardScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassLoopDashboardViewModel
import app.aaps.plugins.main.general.dashboard.glass.GlassLoopDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassPumpDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassSensorInsertDetailScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassSensorQualityScreen
import app.aaps.plugins.main.general.dashboard.glass.GlassTargetDetailScreen
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel

/**
 * The route details of the "Glass" skin, registered on Android only.
 *
 * Those screens live in the `androidMain` part of `:plugins:main`, so the shared [appNavGraph] in
 * `:appshell` cannot name them: its `commonMain` is also compiled for iOS and the desktop. Android
 * therefore passes this builder in through the graph's `glassRoutes` slot, and the other two
 * platforms pass nothing - they have no skins, so they have no Glass screens to reach.
 *
 * Call it from inside `NavHost`, next to [appNavGraph], so both build the same graph.
 */
fun NavGraphBuilder.glassRoutes(
    navController: NavHostController,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    overviewViewModel: OverviewViewModel,
    glassLoopDashboardViewModel: GlassLoopDashboardViewModel,
    tempTargetManagementViewModel: TempTargetManagementViewModel,
    runningModeManagementViewModel: RunningModeManagementViewModel,
    builtInSearchables: BuiltInSearchables,
    onShowDeliveryError: (comment: String, title: TextRef) -> Unit,
    onOpenAimiContext: () -> Unit,
) {
    composable(AppRoute.GlassLoopDashboard.route) {
        val uiState by glassLoopDashboardViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { glassLoopDashboardViewModel.refresh() }
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassLoopDashboardScreen(
            uiState = uiState,
            onBack = { navController.safePopBackStack() },
            isDark = isDark,
            onOpenAimiContext = onOpenAimiContext,
        )
    }

    composable(AppRoute.GlassSensorQuality.route) {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassSensorQualityScreen(
            overviewViewModel = overviewViewModel,
            onBack = { navController.safePopBackStack() },
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassPumpDetail.route) {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassPumpDetailScreen(
            overviewViewModel = overviewViewModel,
            onBack = { navController.safePopBackStack() },
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassBatteryDetail.route) {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassBatteryDetailScreen(
            overviewViewModel = overviewViewModel,
            onBack = { navController.safePopBackStack() },
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassInsulinDetail.route) {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassInsulinDetailScreen(
            insulinButtonsDef = builtInSearchables.insulinButtons,
            bgInfoState = graphViewModel.bgInfoState,
            iobUiState = chipsViewModel.iobUiState,
            cobUiState = chipsViewModel.cobUiState,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, CoreUiStrings.treatmentdeliveryerror)
            },
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassCannulaDetail.route) { backStackEntry ->
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        val siteLocation = backStackEntry.savedStateHandle.get<String>("site_location")
        val siteArrow = backStackEntry.savedStateHandle.get<String>("site_arrow")
        val siteResult = if (siteLocation != null || siteArrow != null) Pair(siteLocation, siteArrow) else null

        GlassCannulaDetailScreen(
            fillButtonsDef = builtInSearchables.fillButtons,
            onNavigateBack = { navController.safePopBackStack() },
            onPickSiteLocation = {
                navController.navigate(AppRoute.SiteLocationPicker.createRoute(TE.Type.CANNULA_CHANGE))
            },
            siteLocationResult = siteResult,
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassBasalDetail.route) {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        GlassBasalDetailScreen(
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, CoreUiStrings.temp_basal_delivery_error)
            },
            isDark = isDark,
        )
    }

    composable(AppRoute.GlassTargetDetail.route) {
        // Activity-scoped ViewModel, NOT hiltViewModel(): reuse the SAME instance the shared
        // TempTargetManagement destination (above) already binds to, following that destination's own
        // pattern for obtaining this ViewModel.
        GlassTargetDetailScreen(
            viewModel = tempTargetManagementViewModel,
            onNavigateBack = { navController.safePopBackStack() },
        )
    }

    composable(AppRoute.GlassLoopDetail.route) {
        // Activity-scoped ViewModel, NOT hiltViewModel(): reuse the SAME instance the shared
        // RunningMode destination (above) already binds to, following that destination's own
        // pattern for obtaining this ViewModel.
        GlassLoopDetailScreen(
            viewModel = runningModeManagementViewModel,
            onNavigateBack = { navController.safePopBackStack() },
        )
    }

    composable(
        route = AppRoute.GlassSensorInsertDetail.route,
        arguments = listOf(navArgument("eventTypeOrdinal") { type = NavType.IntType })
    ) { backStackEntry ->
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        val siteLocation = backStackEntry.savedStateHandle.get<String>("site_location")
        val siteArrow = backStackEntry.savedStateHandle.get<String>("site_arrow")
        val siteResult = if (siteLocation != null || siteArrow != null) Pair(siteLocation, siteArrow) else null

        GlassSensorInsertDetailScreen(
            onNavigateBack = { navController.safePopBackStack() },
            onPickSiteLocation = {
                navController.navigate(AppRoute.SiteLocationPicker.createRoute(TE.Type.SENSOR_CHANGE))
            },
            siteLocationResult = siteResult,
            isDark = isDark,
        )
    }
}
