package app.aaps.plugins.main.general.overview.boost.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.DaggerActivity
import javax.inject.Inject

/**
 * Configuration screen for the [BoostWidget].
 */
class BoostWidgetConfigureActivity : DaggerActivity() {

    @Inject lateinit var preferences: Preferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Build UI programmatically
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "Boost Widget — Opacity"
        title.textSize = 18f
        layout.addView(title)

        val seekBar = SeekBar(this)
        seekBar.max = 255
        seekBar.progress = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                preferences.put(IntComposedKey.WidgetOpacity, appWidgetId, value = p)
                BoostWidget.updateWidget(this@BoostWidgetConfigureActivity, "BoostWidgetConfigure")
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        layout.addView(seekBar)

        val useBlack = CheckBox(this)
        useBlack.text = "Black background"
        useBlack.isChecked = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)
        useBlack.setOnCheckedChangeListener { _, value ->
            preferences.put(BooleanComposedKey.WidgetUseBlack, appWidgetId, value = value)
            BoostWidget.updateWidget(this@BoostWidgetConfigureActivity, "BoostWidgetConfigure")
        }
        layout.addView(useBlack)

        val done = Button(this)
        done.text = "Done"
        done.setOnClickListener {
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
        layout.addView(done)

        setContentView(layout)
    }
}
