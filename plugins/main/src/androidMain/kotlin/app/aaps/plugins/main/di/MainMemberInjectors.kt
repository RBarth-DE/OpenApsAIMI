package app.aaps.plugins.main.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.main.general.dashboard.AdjustmentDetailsActivity
import app.aaps.plugins.main.general.dashboard.AimiAdaptationStatusActivity
import app.aaps.plugins.main.general.dashboard.LoopStateActivity
import app.aaps.plugins.main.general.overview.boost.widget.BoostWidget
import app.aaps.plugins.main.general.overview.boost.widget.BoostWidgetConfigureActivity
import app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the fork's dashboard / AIMI activities in :plugins:main.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object MainMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AdjustmentDetailsActivity::class)
    fun bindAdjustmentDetailsActivity(injector: MembersInjector<AdjustmentDetailsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiAdaptationStatusActivity::class)
    fun bindAimiAdaptationStatusActivity(injector: MembersInjector<AimiAdaptationStatusActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(LoopStateActivity::class)
    fun bindLoopStateActivity(injector: MembersInjector<LoopStateActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BoostWidget::class)
    fun bindBoostWidget(injector: MembersInjector<BoostWidget>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BoostWidgetConfigureActivity::class)
    fun bindBoostWidgetConfigureActivity(injector: MembersInjector<BoostWidgetConfigureActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DismissNotificationReceiver::class)
    fun bindDismissNotificationReceiver(injector: MembersInjector<DismissNotificationReceiver>): MembersInjector<*> = injector
}
