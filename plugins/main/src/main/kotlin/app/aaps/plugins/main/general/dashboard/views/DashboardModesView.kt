package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import app.aaps.plugins.main.databinding.ComponentDashboardModesBinding
import com.google.android.material.button.MaterialButton


class DashboardModesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding =
        ComponentDashboardModesBinding.inflate(LayoutInflater.from(context), this, true)

    private var clickListener: ((Int) -> Unit)? = null

    private val buttons: List<MaterialButton> by lazy {
        listOf(
            binding.btn0,
            binding.btn1,
            binding.btn2,
            binding.btn3,
            binding.btn4,
            binding.btn5,
            binding.btn6,
            binding.btn7,
            binding.btn8,
            binding.btn9
        )
    }

    init {
        orientation = VERTICAL
        buttons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                if (btn.isEnabled) {
                    clickListener?.invoke(index)
                }
            }
        }
    }

    fun setOnButtonClickListener(listener: (Int) -> Unit) {
        clickListener = listener
    }

    /**
     * Passes the titles to be displayed (max. 10)
     */
    fun setButtons(titles: List<String>) {
        buttons.forEachIndexed { index, btn ->
            if (index < titles.size) {
                btn.text = titles[index]
                btn.isEnabled = true
                btn.alpha = 1f
            } else {
                btn.text = ""
                btn.isEnabled = false
                btn.alpha = 0.35f
            }
        }
    }
}
