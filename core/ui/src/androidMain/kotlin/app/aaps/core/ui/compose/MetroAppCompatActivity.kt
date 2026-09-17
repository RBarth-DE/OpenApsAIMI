package app.aaps.core.ui.compose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.di.MetroMemberInjector

/**
 * It lives here rather than beside `MetroBroadcastReceiver` and `MetroService` in `:core:objects`,
 * because AppCompat is a `:core:ui` dependency and adding it to `:core:objects` would buy nothing.
 * A missing binding fails loudly here too - see `MetroAndroidEntryPoints` for why.
 */
abstract class MetroAppCompatActivity : AppCompatActivity() {

    /**
     * The view models are Metro's, so the activity's default factory has to be Metro's as well.
     * A `by viewModels()` with no factory argument reads this property, and without the override it
     * would get the platform factory, which builds a view model through its no-arg constructor - and
     * none of ours has one. Under Hilt the `@AndroidEntryPoint` transform filled this in; nothing
     * does that anymore.
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (applicationContext as MetroViewModelFactoryOwner).metroViewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        val application = applicationContext
        check(application is MetroMemberInjector) {
            "Application does not implement MetroMemberInjector, so ${this::class.java.name} cannot be injected"
        }
        check(application.injectMembers(this)) {
            "No Metro binding for ${this::class.java.name}. Add a @Provides @IntoMap @ClassKey entry for it."
        }
        super.onCreate(savedInstanceState)
    }
}
