package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.aps.MealHypothesisHistorySource
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.openAPSAIMI.OpenAPSAIMIPlugin
import app.aaps.plugins.aps.openAPSAutoISF.OpenAPSAutoISFPlugin
import app.aaps.plugins.aps.openAPSBoost.OpenAPSBoostPlugin
import app.aaps.plugins.aps.openAPSBoostV5.OpenAPSBoostV5Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

/**
 * Registers the fork's own APS plugins: AIMI (225), AutoISF (230), Boost (231) and Boost V5 (239).
 *
 * These are Android only, so they are registered from `androidMain` and the plugins themselves live
 * there too. Other targets link this module but not these plugins, exactly as `:plugins:source`
 * already does with its Android-only CGM plugins: on iOS the plugin is absent instead of present and
 * dead.
 *
 * The keys are the same as when this lived in `ApsPluginRegistrations` in `commonMain`. AMA (210) and
 * SMB (220) stayed behind there, and the unqualified bucket and the `@AllConfigs` reasoning described
 * in that file apply here unchanged.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApsForkPluginRegistrations {

    @Provides
    @IntoMap
    @IntKey(225)
    fun openApsAimiEntry(plugin: OpenAPSAIMIPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(230)
    fun openApsAutoIsfEntry(plugin: OpenAPSAutoISFPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(231)
    fun openApsBoostEntry(plugin: OpenAPSBoostPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(239)
    fun openApsBoostV5Entry(plugin: OpenAPSBoostV5Plugin): PluginBase = plugin

    /**
     * The meal hypothesis history lives inside the Boost V5 plugin. Callers (the overview graph)
     * ask for the interface, so the plugin has to be reachable under it.
     */
    @Provides
    fun mealHypothesisHistorySource(plugin: OpenAPSBoostV5Plugin): MealHypothesisHistorySource = plugin
}
