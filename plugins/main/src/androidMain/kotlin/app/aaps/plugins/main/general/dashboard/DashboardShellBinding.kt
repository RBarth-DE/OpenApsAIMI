package app.aaps.plugins.main.general.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.views.AdjustmentStatusView
import app.aaps.plugins.main.general.dashboard.views.CircleTopDashboardView
import app.aaps.plugins.main.general.dashboard.views.GlucoseGraphView
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * View ports used by [DashboardShellController] for both the classic [DashboardFragment] layout and
 * the Compose-shell column (Compose hero + body [AndroidView]).
 *
 * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
 * classes, so there is no `FragmentDashboardBinding` to use.
 */
internal data class DashboardShellBinding(
    val root: View,
    val nestedScrollView: NestedScrollView?,
    val overviewNotifications: RecyclerView?,
    val glucoseGraph: GlucoseGraphView?,
    val adjustmentStatus: AdjustmentStatusView?,
    val bottomNavigation: BottomNavigationView?,
    val statusCard: CircleTopDashboardView?,
    private val standaloneAuditorHost: FrameLayout?,
) {

    fun auditorHost(): FrameLayout =
        standaloneAuditorHost
            ?: statusCard?.getAuditorContainer()
            ?: error("DashboardShellBinding: missing auditor host")

    internal companion object {

        /** Inflates `fragment_dashboard.xml` and collects its views. */
        fun inflate(inflater: LayoutInflater, parent: ViewGroup?): DashboardShellBinding =
            fromFragmentDashboard(inflater.inflate(R.layout.fragment_dashboard, parent, false))

        fun fromFragmentDashboard(root: View): DashboardShellBinding {
            // fragment_dashboard.xml is a LinearLayout, so the root is a ViewGroup and the
            // NestedScrollView is its first child.
            val nested = (root as ViewGroup).getChildAt(0) as NestedScrollView
            return DashboardShellBinding(
                root = root,
                nestedScrollView = nested,
                overviewNotifications = root.findViewById(R.id.overview_notifications),
                glucoseGraph = root.findViewById(R.id.glucose_graph),
                adjustmentStatus = root.findViewById(R.id.adjustment_status),
                bottomNavigation = root.findViewById(R.id.bottom_navigation),
                statusCard = root.findViewById(R.id.status_card),
                standaloneAuditorHost = null,
            )
        }

        fun fromComposeEmbeddedColumn(
            shellPostRoot: View,
            auditorHost: FrameLayout,
            glucoseGraph: GlucoseGraphView?,
        ): DashboardShellBinding =
            DashboardShellBinding(
                root = shellPostRoot,
                nestedScrollView = null,
                overviewNotifications = null,
                glucoseGraph = glucoseGraph,
                adjustmentStatus = null,
                bottomNavigation = null,
                statusCard = null,
                standaloneAuditorHost = auditorHost,
            )
    }
}
