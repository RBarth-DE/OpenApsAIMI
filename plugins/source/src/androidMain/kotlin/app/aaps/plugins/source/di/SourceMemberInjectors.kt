package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.source.activities.CgmDriverLogActivity
import app.aaps.plugins.source.activities.DexcomOnePlusStartActivity
import app.aaps.plugins.source.activities.DexcomOnePlusStatusActivity
import app.aaps.plugins.source.activities.DexcomOnePlusWarmupActivity
import app.aaps.plugins.source.activities.EversenseCalibrationActivity
import app.aaps.plugins.source.activities.Libre3StartActivity
import app.aaps.plugins.source.activities.Libre3StatusActivity
import app.aaps.plugins.source.activities.Libre3WarmupActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the fork's native CGM activities (Eversense / Dexcom ONE+ / Libre 3).
 * Android builds the activities, so they cannot take dependencies in a constructor; they call
 * `injectMetroMembers(this)` and look their injector up here by class.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SourceMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CgmDriverLogActivity::class)
    fun bindCgmDriverLogActivity(injector: MembersInjector<CgmDriverLogActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DexcomOnePlusStartActivity::class)
    fun bindDexcomOnePlusStartActivity(injector: MembersInjector<DexcomOnePlusStartActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DexcomOnePlusStatusActivity::class)
    fun bindDexcomOnePlusStatusActivity(injector: MembersInjector<DexcomOnePlusStatusActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DexcomOnePlusWarmupActivity::class)
    fun bindDexcomOnePlusWarmupActivity(injector: MembersInjector<DexcomOnePlusWarmupActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(EversenseCalibrationActivity::class)
    fun bindEversenseCalibrationActivity(injector: MembersInjector<EversenseCalibrationActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3StartActivity::class)
    fun bindLibre3StartActivity(injector: MembersInjector<Libre3StartActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3StatusActivity::class)
    fun bindLibre3StatusActivity(injector: MembersInjector<Libre3StatusActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3WarmupActivity::class)
    fun bindLibre3WarmupActivity(injector: MembersInjector<Libre3WarmupActivity>): MembersInjector<*> = injector
}
