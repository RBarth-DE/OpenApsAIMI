package app.aaps.ui.tabs

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import androidx.viewpager2.adapter.FragmentViewHolder
import android.util.Log
import androidx.fragment.app.FragmentActivity

class TabPageAdapter(private val activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    private val visibleFragmentList = ArrayList<PluginBase>()

    override fun getItemCount(): Int = visibleFragmentList.size

    // Use stable IDs to avoid confusion during view recycling.
    override fun getItemId(position: Int): Long {
        return visibleFragmentList[position].name.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return visibleFragmentList.any { it.name.hashCode().toLong() == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        val plugin = visibleFragmentList[position]
        val className = plugin.pluginDescription.fragmentClass ?: Fragment::class.java.name

        // Use the Activity's Factory, but make sure we don't crash.
        return try {
            activity.supportFragmentManager.fragmentFactory.instantiate(activity.classLoader, className).apply {
                if (this is PluginFragment) {
                    this.plugin = plugin
                }
            }
        } catch (e: Exception) {
            Log.e("TabPageAdapter", "Error creating fragment for${plugin.name}", e)
            Fragment() // Fallback to empty fragment instead of crash
        }
    }

    fun getPluginAt(position: Int): PluginBase = visibleFragmentList[position]

    fun registerNewFragment(plugin: PluginBase) {
        if (!visibleFragmentList.contains(plugin)) {
            visibleFragmentList.add(plugin)
            notifyItemInserted(visibleFragmentList.size - 1)
        }
    }

    // Safety check when tying
    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int, payloads: MutableList<Any>) {
        val context = holder.itemView.context ?: return
        val activity = context as? FragmentActivity

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }


        try {
            super.onBindViewHolder(holder, position, payloads)
        } catch (e: IllegalArgumentException) {
            // Here we catch the "No view found for id" crash!
            Log.e("TabPageAdapter", "ViewPager2 wanted to bind Fragment, but View was gone: ${e.message}")
        }
    }
}