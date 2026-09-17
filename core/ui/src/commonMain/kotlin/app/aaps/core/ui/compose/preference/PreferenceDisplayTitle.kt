package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import java.util.Locale

/**
 * True when a title carries no text, so there is nothing to show for it.
 *
 * Many keys declare `title = TextRef.Literal("")` because their label lives elsewhere - in the
 * caller, or in an XML screen that no longer exists. That is the same thing the old `titleResId == 0`
 * meant, and the difference matters: a blank title still renders a row, it just renders it with no
 * name on it, and it still lands in the joined summary line of a parent submenu.
 */
internal fun TextRef.isBlankTitle(): Boolean = this is TextRef.Literal && text.isEmpty()

/**
 * Label for a preference row. Keys with an empty [TextRef.Literal] title (common when titles are set
 * only in XML) still need text in Compose — fall back to a readable form of the storage key.
 */
@Composable
internal fun preferenceDisplayTitle(title: TextRef, storageKey: String): String =
    if (title.isBlankTitle()) {
        humanizeStorageKey(storageKey)
    } else {
        stringResource(title)
    }

/**
 * The one-line list of child names shown under a collapsed submenu or category header, or null when
 * no child has a name. Blank titles are skipped: joining them leaves a leading or doubled comma.
 */
@Composable
internal fun preferenceSummaryLine(summaryItems: List<TextRef>): String? =
    summaryItems
        .filterNot { it.isBlankTitle() }
        .map { stringResource(it) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

private fun humanizeStorageKey(storageKey: String): String {
    var rest = storageKey.trim()
    if (rest.startsWith("key_")) {
        rest = rest.removePrefix("key_")
    }
    return rest.split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
        }
        .ifEmpty { storageKey }
}
