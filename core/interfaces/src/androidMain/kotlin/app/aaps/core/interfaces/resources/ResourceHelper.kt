package app.aaps.core.interfaces.resources

import android.content.res.AssetFileDescriptor
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.AttrRes
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.InterfacesStringIds
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs

interface ResourceHelper : TextResolver {

    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String
    @ColorInt fun gc(@ColorRes id: Int): Int
    @ColorInt fun gac(@AttrRes attributeId: Int): Int
    @ColorInt fun gac(context: Context?, @AttrRes attributeId: Int): Int
    fun gd(@DrawableRes id: Int): Drawable?
    fun gb(@BoolRes id: Int): Boolean
    fun gcs(@ColorRes id: Int): String
    fun gsa(@ArrayRes id: Int): Array<String>
    fun openRawResourceFd(@RawRes id: Int): AssetFileDescriptor?

    fun decodeResource(id: Int): Bitmap
    fun getDisplayMetrics(): DisplayMetrics
    fun dpToPx(dp: Int): Int
    fun dpToPx(dp: Float): Int

    /**
     * Resolves a [TextRef] outside Compose. Inside Compose use
     * `app.aaps.core.ui.compose.stringResource` instead.
     *
     * Every non-Compose reader of a preference title goes through here, which is the point: a module
     * that stops naming resource ids in its own code changes only this method, not its call sites.
     *
     * [TextRef.Named] still ends up in `Resources` on Android - the name is turned into an id first -
     * so locale matching behaves exactly as it does for [TextRef.AndroidRes].
     */
    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.AndroidRes ->
            if (ref.args.isEmpty()) gs(ref.id)
            else gs(ref.id, *ref.args.toTypedArray())

        is TextRef.Named      -> {
            val id = keysIdOf(ref)
            when {
                id == null         -> ref.name
                ref.args.isEmpty() -> gs(id)
                else               -> gs(id, *ref.args.toTypedArray())
            }
        }
    }

    /** Same, with format arguments - mirrors `gs(id, vararg)`. */
    override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref.withArgs(*args))

    /** Same, but always in English - used to build the search index. */
    override fun gsNotLocalised(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.AndroidRes -> gsNotLocalised(ref.id, *ref.args.toTypedArray())
        is TextRef.Named      -> keysIdOf(ref)
            ?.let { gsNotLocalised(it, *ref.args.toTypedArray()) }
            ?: ref.name
    }
}

/**
 * Resolves a [TextRef.Named] that this module can see.
 *
 * Two owners are resolvable directly: `keys`, via the `:core:keys` dependency, and `interfaces`,
 * whose map is generated into this module.
 *
 * Anything else is asked of [TextRefIdRegistry], which is how a module further up - `:core:ui` and
 * its `ui` names - makes itself resolvable here. Only when nobody has claimed the owner does this
 * fall back to showing the raw name, which is visibly wrong rather than silently blank.
 */
private fun keysIdOf(ref: TextRef.Named): Int? = when (ref.owner) {
    "keys"       -> KeysStringIds.idOf(ref.name)
    "interfaces" -> InterfacesStringIds.idOf(ref.name)
    else         -> TextRefIdRegistry.idOf(ref)
}
