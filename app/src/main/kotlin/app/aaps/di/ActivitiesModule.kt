package app.aaps.di

import dagger.Module
import app.aaps.plugins.aps.OpenAPSFragment
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
}
