package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent

/**
 * Adapter for active intents RecyclerView.
 *
 * Displays each intent with:
 * - Icon + Type + Intensity
 * - Time remaining
 * - Remove and Extend buttons
 */
class ContextIntentAdapter(
    private val onRemove: (String) -> Unit,
    private val onExtend: (String) -> Unit,
    private val getTimeRemaining: (ContextIntent) -> String,
    private val getDisplayString: (ContextIntent) -> String
) : ListAdapter<Pair<String, ContextIntent>, ContextIntentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_active_intent, parent, false)
        return ViewHolder(root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * One row of the active intents list.
     *
     * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
     * classes, so there is no `ItemActiveIntentBinding` to use. See `ActivityContextViews` in
     * ContextActivity for the same reason.
     */
    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {

        private val textIntentType: TextView = root.findViewById(R.id.textIntentType)
        private val textTimeRemaining: TextView = root.findViewById(R.id.textTimeRemaining)
        private val textConfidence: TextView = root.findViewById(R.id.textConfidence)
        private val btnExtend: Button = root.findViewById(R.id.btnExtend)
        private val btnRemove: Button = root.findViewById(R.id.btnRemove)

        fun bind(item: Pair<String, ContextIntent>) {
            val (id, intent) = item

            // Display string (e.g., "🏃 Activity: CARDIO HIGH")
            textIntentType.text = getDisplayString(intent)

            // Time remaining
            textTimeRemaining.text = getTimeRemaining(intent)

            // Confidence (if available)
            val confidenceText = when {
                intent.confidence >= 0.90 -> "✓ Haute confiance"
                intent.confidence >= 0.70 -> "~ Moyenne confiance"
                intent.confidence >= 0.50 -> "? Faible confiance"
                else -> ""
            }
            textConfidence.text = confidenceText

            // Remove button
            btnRemove.setOnClickListener {
                onRemove(id)
            }

            // Extend button
            btnExtend.setOnClickListener {
                onExtend(id)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<Pair<String, ContextIntent>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, ContextIntent>,
            newItem: Pair<String, ContextIntent>
        ): Boolean {
            return oldItem.first == newItem.first
        }

        override fun areContentsTheSame(
            oldItem: Pair<String, ContextIntent>,
            newItem: Pair<String, ContextIntent>
        ): Boolean {
            return oldItem == newItem
        }
    }
}
