package app.aaps.di

import app.aaps.MainActivity
import app.aaps.activities.HistoryBrowseActivity
import dagger.Module
import app.aaps.plugins.aps.OpenAPSFragment
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesHistoryBrowseActivity(): HistoryBrowseActivity
    @ContributesAndroidInjector abstract fun contributesMainActivity(): MainActivity
    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
}
