package app.aaps.plugins.sync.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.tidepool.auth.AuthFlowIn
import app.aaps.plugins.sync.wear.activities.CwfInfosActivity
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the Android entry points in this module.
 *
 * Separate from [OpenHumansMetroGraph], which is still a root graph of its own for the reason written up
 * there. This one contributes straight into the app root, so it needs no mention in `:app` at all.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AuthFlowIn::class)
    fun bindAuthFlowIn(injector: MembersInjector<AuthFlowIn>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WearDataReceiver::class)
    fun bindWearDataReceiver(injector: MembersInjector<WearDataReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DataLayerListenerServiceMobile::class)
    fun bindDataLayerListenerServiceMobile(
        injector: MembersInjector<DataLayerListenerServiceMobile>
    ): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(NSClientV3Service::class)
    fun bindNSClientV3Service(injector: MembersInjector<NSClientV3Service>): MembersInjector<*> = injector

    /**
     * The one entry here that builds its [MembersInjector] itself, because this activity has no
     * `@Inject` members at all.
     *
     * Metro only generates a `MembersInjector<T>` for a class that has something to inject, so
     * `MembersInjector<CwfInfosActivity>` has no binding and the usual `bindX(injector)` form above
     * does not compile for it. The entry is still needed: the activity extends
     * `MetroAppCompatActivity`, which refuses to start without one. Injecting nothing into a class
     * that needs nothing is the honest answer, so this is a no-op.
     *
     * If an `@Inject` field is ever added to it, replace this with the normal form.
     */
    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CwfInfosActivity::class)
    fun bindCwfInfosActivity(): MembersInjector<*> = MembersInjector<CwfInfosActivity> { }
}
