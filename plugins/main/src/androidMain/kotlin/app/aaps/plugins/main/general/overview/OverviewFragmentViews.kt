package app.aaps.plugins.main.general.overview

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.ui.elements.SingleClickButton
import app.aaps.plugins.main.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.jjoe64.graphview.GraphView

/**
 * The views of `overview_fragment.xml`, the classic XML overview.
 *
 * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
 * classes, so there is no `OverviewFragmentBinding` to use. The names are the ones the generated
 * class used, so the code that reads them did not have to change.
 *
 * The skins take this type too, for the landscape and small-screen rearranging of the same views.
 */
class OverviewFragmentViews(
    /** The root of `overview_fragment.xml` is a `LinearLayout`; the skins move the buttons into it. */
    val root: LinearLayout,
    val topPartScrollbar: NestedScrollView,
    val notifications: RecyclerView,
    val infoCard: MaterialCardView,
    val statusCard: MaterialCardView,
    val graphCard: MaterialCardView,
    val nsclientCard: MaterialCardView,
    val infoLayout: InfoLayoutViews,
    val statusLightsLayout: StatusLightsViews,
    val graphsLayout: GraphsLayoutViews,
    val buttonsLayout: ButtonsLayoutViews,
    val activeProfile: TextView,
    val tempTarget: TextView,
    val pumpStatusLayout: LinearLayout,
    val pumpStatus: TextView,
    val progressBar: ProgressBar,
    val pump: TextView,
    val openaps: TextView,
    val uploader: TextView,
) {

    /** The views of the included `overview_info_layout.xml`. */
    class InfoLayoutViews(
        val root: View,
        val apsMode: ImageView,
        val apsModeText: TextView,
        val arrow: ImageView,
        val asLayout: LinearLayout,
        val avgDelta: TextView,
        val basalLayout: LinearLayout,
        val baseBasal: TextView,
        val baseBasalIcon: ImageView,
        val bg: TextView,
        val bgQuality: ImageView,
        val carbsIcon: ImageView,
        val cob: TextView,
        val cobLayout: LinearLayout,
        val delta: TextView,
        val deltaLarge: TextView,
        val extendedBolus: TextView,
        val extendedLayout: LinearLayout,
        val iob: TextView,
        val iobLayout: LinearLayout,
        val longAvgDelta: TextView,
        val sensitivity: TextView,
        val sensitivityIcon: ImageView,
        val simpleMode: ImageView,
        val time: TextView,
        val timeAgo: TextView,
        val timeAgoShort: TextView,
        val timeLayout: LinearLayout,
        val variableSensitivity: TextView,
        val version: TextView,
    ) {
        companion object {
            fun from(root: View): InfoLayoutViews = InfoLayoutViews(
                root = root.findViewById(R.id.info_layout),
                apsMode = root.findViewById(R.id.aps_mode),
                apsModeText = root.findViewById(R.id.aps_mode_text),
                arrow = root.findViewById(R.id.arrow),
                asLayout = root.findViewById(R.id.as_layout),
                avgDelta = root.findViewById(R.id.avg_delta),
                basalLayout = root.findViewById(R.id.basal_layout),
                baseBasal = root.findViewById(R.id.base_basal),
                baseBasalIcon = root.findViewById(R.id.base_basal_icon),
                bg = root.findViewById(R.id.bg),
                bgQuality = root.findViewById(R.id.bg_quality),
                carbsIcon = root.findViewById(R.id.carbs_icon),
                cob = root.findViewById(R.id.cob),
                cobLayout = root.findViewById(R.id.cob_layout),
                delta = root.findViewById(R.id.delta),
                deltaLarge = root.findViewById(R.id.delta_large),
                extendedBolus = root.findViewById(R.id.extended_bolus),
                extendedLayout = root.findViewById(R.id.extended_layout),
                iob = root.findViewById(R.id.iob),
                iobLayout = root.findViewById(R.id.iob_layout),
                longAvgDelta = root.findViewById(R.id.long_avg_delta),
                sensitivity = root.findViewById(R.id.sensitivity),
                sensitivityIcon = root.findViewById(R.id.sensitivity_icon),
                simpleMode = root.findViewById(R.id.simple_mode),
                time = root.findViewById(R.id.time),
                timeAgo = root.findViewById(R.id.time_ago),
                timeAgoShort = root.findViewById(R.id.time_ago_short),
                timeLayout = root.findViewById(R.id.time_layout),
                variableSensitivity = root.findViewById(R.id.variable_sensitivity),
                version = root.findViewById(R.id.version),
            )
        }
    }

    /** The views of the included `overview_graphs_layout.xml`. */
    class GraphsLayoutViews(
        val bgGraph: GraphView,
        val scaleButton: Button,
        val chartMenuButton: ImageButton,
        val secondaryGraphs: LinearLayout,
    ) {
        companion object {
            fun from(root: View): GraphsLayoutViews = GraphsLayoutViews(
                bgGraph = root.findViewById(R.id.bg_graph),
                scaleButton = root.findViewById(R.id.scale_button),
                chartMenuButton = root.findViewById(R.id.chart_menu_button),
                secondaryGraphs = root.findViewById(R.id.secondary_graphs),
            )
        }
    }

    /** The views of the included `overview_buttons_layout.xml`. */
    class ButtonsLayoutViews(
        val acceptTempButton: SingleClickButton,
        val userButtonsLayout: LinearLayout,
    ) {
        companion object {
            fun from(root: View): ButtonsLayoutViews = ButtonsLayoutViews(
                acceptTempButton = root.findViewById(R.id.accept_temp_button),
                userButtonsLayout = root.findViewById(R.id.user_buttons_layout),
            )
        }
    }

    /** The views of the included `overview_statuslights_layout.xml`. */
    class StatusLightsViews(
        val cannulaOrPatch: ImageView,
        val cannulaAge: TextView,
        val insulinAge: TextView,
        val reservoirLevel: TextView,
        val sensorAge: TextView,
        val batteryLayout: LinearLayout,
        val pbAge: TextView,
        val pbLevel: TextView,
    ) {
        companion object {
            fun from(root: View): StatusLightsViews = StatusLightsViews(
                cannulaOrPatch = root.findViewById(R.id.cannula_or_patch),
                cannulaAge = root.findViewById(R.id.cannula_age),
                insulinAge = root.findViewById(R.id.insulin_age),
                reservoirLevel = root.findViewById(R.id.reservoir_level),
                sensorAge = root.findViewById(R.id.sensor_age),
                batteryLayout = root.findViewById(R.id.battery_layout),
                pbAge = root.findViewById(R.id.pb_age),
                pbLevel = root.findViewById(R.id.pb_level),
            )
        }
    }

    companion object {
        fun from(root: View): OverviewFragmentViews = OverviewFragmentViews(
            // The cast is what the generated binding did as well: the layout root is a LinearLayout.
            root = root as LinearLayout,
            topPartScrollbar = root.findViewById(R.id.top_part_scrollbar),
            notifications = root.findViewById(R.id.notifications),
            infoCard = root.findViewById(R.id.infoCard),
            statusCard = root.findViewById(R.id.statusCard),
            graphCard = root.findViewById(R.id.graphCard),
            nsclientCard = root.findViewById(R.id.nsclientCard),
            infoLayout = InfoLayoutViews.from(root),
            statusLightsLayout = StatusLightsViews.from(root),
            graphsLayout = GraphsLayoutViews.from(root),
            buttonsLayout = ButtonsLayoutViews.from(root),
            activeProfile = root.findViewById(R.id.active_profile),
            tempTarget = root.findViewById(R.id.temp_target),
            pumpStatusLayout = root.findViewById(R.id.pump_status_layout),
            pumpStatus = root.findViewById(R.id.pump_status),
            progressBar = root.findViewById(R.id.progress_bar),
            pump = root.findViewById(R.id.pump),
            openaps = root.findViewById(R.id.openaps),
            uploader = root.findViewById(R.id.uploader),
        )
    }
}

/**
 * The views of `overview_notification_item.xml`, one row of the overview notification list.
 *
 * Hand-written for the same reason as [OverviewFragmentViews].
 */
class OverviewNotificationItemViews(
    val cv: View,
    val text: TextView,
    val dismiss: MaterialButton,
) {
    companion object {
        fun from(itemView: View): OverviewNotificationItemViews = OverviewNotificationItemViews(
            cv = itemView,
            text = itemView.findViewById(R.id.text),
            dismiss = itemView.findViewById(R.id.dismiss),
        )
    }
}
