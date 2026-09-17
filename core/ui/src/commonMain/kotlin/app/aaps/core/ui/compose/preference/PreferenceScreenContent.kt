package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Hierarchy level for preference sections.
 * TOP_LEVEL sections collapse all other top-level sections (accordion across plugins).
 * SUB_SECTION sections collapse only siblings under the same parent.
 */
enum class SectionLevel {

    TOP_LEVEL,
    SUB_SECTION
}

/**
 * State holder for collapsible preference sections.
 * Tracks which sections are expanded and persists across configuration changes.
 * Uses accordion behavior: only one section can be expanded at a time within the same hierarchy level.
 */
class PreferenceSectionState(
    private val expandedSections: SnapshotStateMap<String, Boolean> = mutableStateMapOf(),
    private val sectionLevels: MutableMap<String, SectionLevel> = mutableMapOf(),
    private val sectionParents: MutableMap<String, String> = mutableMapOf()
) {

    /**
     * Check if a section is expanded (default: false - collapsed)
     */
    fun isExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] ?: false

    /**
     * Toggle the expanded state of a section.
     * Expanding a section collapses only sibling sections (same hierarchy level).
     *
     * @param sectionKey Unique key for this section
     * @param level Hierarchy level of this section
     * @param parentKey Parent section key (used for sub-section sibling detection)
     */
    fun toggle(sectionKey: String, level: SectionLevel = SectionLevel.TOP_LEVEL, parentKey: String? = null) {
        // Register level and parent
        sectionLevels[sectionKey] = level
        if (parentKey != null) sectionParents[sectionKey] = parentKey

        val newState = !isExpanded(sectionKey)

        if (newState) {
            // Collapse only sibling sections (convert to list to avoid concurrent modification)
            expandedSections.keys.toList().forEach { key ->
                if (key != sectionKey) {
                    val keyLevel = sectionLevels[key] ?: SectionLevel.TOP_LEVEL

                    val shouldCollapse = when {
                        // Top-level sections collapse all other top-level sections
                        level == SectionLevel.TOP_LEVEL && keyLevel == SectionLevel.TOP_LEVEL -> true
                        // Sub-sections collapse only siblings under the same parent
                        level == SectionLevel.SUB_SECTION && keyLevel == SectionLevel.SUB_SECTION
                            && sectionParents[key] == parentKey                               -> true

                        else                                                                  -> false
                    }

                    if (shouldCollapse) {
                        expandedSections[key] = false
                    }
                }
            }
        }

        expandedSections[sectionKey] = newState
    }

    companion object {

        /**
         * Saved as one flat list instead of a `Bundle`.
         *
         * A `Bundle` is Android only, and it bought nothing here: `listSaver` stores the same
         * `String`, `Boolean` and `Int` through the SaveableStateRegistry a Bundle-backed saver used.
         * Each section contributes four entries - key, expanded flag, level ordinal and parent key -
         * so the accordion hierarchy survives a configuration change too.
         */
        val Saver: Saver<PreferenceSectionState, Any> = listSaver(
            save = { state ->
                state.expandedSections.flatMap { (key, expanded) ->
                    listOf(
                        key,
                        expanded,
                        (state.sectionLevels[key] ?: SectionLevel.TOP_LEVEL).ordinal,
                        state.sectionParents[key] ?: ""
                    )
                }
            },
            restore = { saved ->
                val expandedSections = mutableStateMapOf<String, Boolean>()
                val sectionLevels = mutableMapOf<String, SectionLevel>()
                val sectionParents = mutableMapOf<String, String>()
                saved.chunked(4).forEach { entry ->
                    val key = entry.getOrNull(0) as? String ?: return@forEach
                    expandedSections[key] = entry.getOrNull(1) as? Boolean ?: false
                    val ordinal = entry.getOrNull(2) as? Int ?: 0
                    sectionLevels[key] = SectionLevel.entries.getOrElse(ordinal) { SectionLevel.TOP_LEVEL }
                    val parent = entry.getOrNull(3) as? String
                    if (!parent.isNullOrEmpty()) sectionParents[key] = parent
                }
                PreferenceSectionState(
                    expandedSections = expandedSections,
                    sectionLevels = sectionLevels,
                    sectionParents = sectionParents
                )
            }
        )
    }
}

/**
 * Remember and save preference section state across configuration changes.
 * Uses accordion behavior by default.
 */
@Composable
fun rememberPreferenceSectionState(): PreferenceSectionState {
    return rememberSaveable(saver = PreferenceSectionState.Saver) {
        PreferenceSectionState()
    }
}
