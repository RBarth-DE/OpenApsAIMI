package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

/**
 * Registers the openAPS plugins that exist on every target: AMA (210) and SMB (220).
 *
 * The fork's own APS plugins - AIMI, the Boost family and AutoISF - are Android only and are
 * registered in `ApsForkPluginRegistrations` in `androidMain` instead. They are built on Android
 * activities, Health Connect and file export, so they carry no iOS code to link.
 *
 * ## Unqualified, and that is deliberate
 *
 * The `@Binds` block this replaces said `@AllConfigs`, not `@APS` - despite living in a file called
 * `ApsPluginsModule`. `:app` merges the unqualified Metro bucket unconditionally, which is precisely
 * what `@AllConfigs` meant, so this keeps the existing behaviour.
 *
 * Reaching for `@APS` here because the module name says APS would have compiled, passed every test, and
 * quietly dropped these plugins from every follower build. The mirror of that mistake is the virtual
 * pump's, where carrying `@AllConfigs` over would have dropped the plugin from the list entirely, since
 * nothing reads a Metro map under that qualifier. Neither is visible from the annotation: the only way
 * to know is to read which bucket `MetroGraphs.allPlugins` merges, and under what condition.
 *
 * Keys 210 and 220 are unchanged. The fork plugins keep 225, 230, 231 and 239 in the Android file. Two
 * more carry their own `@ContributesIntoMap` instead of an entry here, because each also binds an
 * interface much of the app depends on and so moves for that reason: Loop (200) with the rest of
 * commonMain, and Autotune (240), which is Android only.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApsPluginRegistrations {

    @Provides
    @IntoMap
    @IntKey(210)
    fun openApsAmaEntry(plugin: OpenAPSAMAPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(220)
    fun openApsSmbEntry(plugin: OpenAPSSMBPlugin): PluginBase = plugin
}
