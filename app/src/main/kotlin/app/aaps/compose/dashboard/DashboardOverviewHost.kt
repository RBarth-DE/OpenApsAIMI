package app.aaps.compose.dashboard

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.plugins.main.general.dashboard.AimiDashboardComposeRootView
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.rxjava3.disposables.Disposable

private const val TAG = "DashboardOverviewHost"

@Composable
fun DashboardOverviewHost(
    paddingValues: PaddingValues,
    fabBottomOffset: Dp,
    modifier: Modifier = Modifier,
) {
    val preferences = LocalPreferences.current
    val context = LocalContext.current
    var useBoostOverview by remember { mutableStateOf(preferences.get(BooleanKey.OverviewUseBoostOverview)) }
    var recomposeTick by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        Log.d(TAG, "Subscribing to EventPreferenceChange for boost overview toggle")
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, RxBusEntryPoint::class.java)
        val disp: Disposable = entryPoint.rxBus()
            .toObservable(EventPreferenceChange::class.java)
            .subscribe { event ->
                if (event.isChanged(BooleanKey.OverviewUseBoostOverview.key)) {
                    val newVal = preferences.get(BooleanKey.OverviewUseBoostOverview)
                    Log.d(TAG, "Boost overview changed to: $newVal")
                    useBoostOverview = newVal
                    recomposeTick++
                }
            }
        onDispose { disp.dispose() }
    }

    Log.d(TAG, "Rendering: useBoostOverview=$useBoostOverview tick=$recomposeTick")

    if (useBoostOverview) {
        Box(modifier = modifier.fillMaxSize().padding(paddingValues).padding(bottom = fabBottomOffset)
            .background(Color(0xFF1B5E20.toInt())), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("BOOST OVERVIEW", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("BOOST algorithm (V1 + V5) active", style = MaterialTheme.typography.titleMedium, color = Color(0xFFA5D6A7))
                Text("\nFull BOOST dashboard coming soon.", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        }
    } else {
        AndroidView(
            modifier = modifier.fillMaxSize().padding(paddingValues).padding(bottom = fabBottomOffset),
            factory = { a -> AimiDashboardComposeRootView(a as FragmentActivity) },
            update = { },
        )
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RxBusEntryPoint {
    fun rxBus(): RxBus
}
