package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModeSettingsActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorReportActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorVerdictActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.pulse.AimiPulseDetailActivity
import app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity
import app.aaps.plugins.aps.openAPSAutoISF.advisor.AutoIsfProfileAdvisorActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the activities this module owns.
 *
 * Android builds an activity itself, so it cannot take its dependencies in a constructor. Each class
 * below fills its own `@Inject` fields from this map in `onCreate`, through
 * [app.aaps.core.ui.compose.MetroAppCompatActivity].
 *
 * It lives in `androidMain` for the same reason the plugin registrations next door do, and for one
 * more: [ContextActivity] is an `androidMain` class, so a `commonMain` container could not name it.
 * `androidMain` sees both source sets, which keeps all eight in one file.
 *
 * An activity missing from here fails on launch, naming itself, rather than later at the first use of
 * a field that was never set.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApsMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiModeSettingsActivity::class)
    fun bindAimiModeSettingsActivity(injector: MembersInjector<AimiModeSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiProfileAdvisorActivity::class)
    fun bindAimiProfileAdvisorActivity(injector: MembersInjector<AimiProfileAdvisorActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiPulseDetailActivity::class)
    fun bindAimiPulseDetailActivity(injector: MembersInjector<AimiPulseDetailActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AuditorReportActivity::class)
    fun bindAuditorReportActivity(injector: MembersInjector<AuditorReportActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AuditorVerdictActivity::class)
    fun bindAuditorVerdictActivity(injector: MembersInjector<AuditorVerdictActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AutoIsfProfileAdvisorActivity::class)
    fun bindAutoIsfProfileAdvisorActivity(injector: MembersInjector<AutoIsfProfileAdvisorActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ContextActivity::class)
    fun bindContextActivity(injector: MembersInjector<ContextActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MealAdvisorActivity::class)
    fun bindMealAdvisorActivity(injector: MembersInjector<MealAdvisorActivity>): MembersInjector<*> = injector
}
