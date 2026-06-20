package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.overview.AuditorStateProvider
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStateProviderImpl
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.aps.openAPSAIMI.di.WCycleModule
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModeSettingsActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity
import app.aaps.plugins.aps.openAPSAutoISF.advisor.AutoIsfProfileAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorReportActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        AutotuneModule::class,
        LoopModule::class,
        WCycleModule::class,
        app.aaps.plugins.aps.openAPSAIMI.di.AIMIStepsProviderModuleMTR::class, // 🏥 MTR Steps Integration
        AIMIPhysioModuleMTR::class, // 🏥 MTR Physiological Assistant
        ApsModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ApsModule {

    @ContributesAndroidInjector abstract fun contributesAimiProfileAdvisorActivity(): AimiProfileAdvisorActivity
    @ContributesAndroidInjector abstract fun contributesAutoIsfProfileAdvisorActivity(): AutoIsfProfileAdvisorActivity
    @ContributesAndroidInjector abstract fun contributesAuditorReportActivity(): AuditorReportActivity
    @ContributesAndroidInjector abstract fun contributesAimiModeSettingsActivity(): AimiModeSettingsActivity
    @ContributesAndroidInjector abstract fun contributesMealAdvisorActivity(): MealAdvisorActivity
    @ContributesAndroidInjector abstract fun contributesAimiPulseDetailActivity(): app.aaps.plugins.aps.openAPSAIMI.advisor.pulse.AimiPulseDetailActivity
    @ContributesAndroidInjector abstract fun contributesAuditorVerdictActivity(): app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorVerdictActivity
    @ContributesAndroidInjector abstract fun contributesContextActivity(): ContextActivity

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindLoop(loopPlugin: LoopPlugin): Loop
        @Binds fun bindAutotune(autotunePlugin: AutotunePlugin): Autotune
        @Binds fun bindAuditorStateProvider(impl: AuditorStateProviderImpl): AuditorStateProvider
    }
}