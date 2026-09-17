package app.aaps.implementation.resources

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.implementation.ImplementationStringIds
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * Created by adrian on 2019-12-23.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ResourceHelperImpl(
    var context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val preferences: Preferences
) : ResourceHelper {

    // Lazy for the same reason as the context below: `Dispatchers.Main` does not exist in a plain JVM
    // test ("Dispatchers.Main was accessed when the platform dispatcher was absent"), and this class is
    // built for real there now that Metro owns it. Only `start()` uses the scope, so nothing is delayed
    // that anything waits on.
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // Built on first use rather than at construction. `LocaleHelper.currentLocale` reads Android
    // resources, and this class is built for real in the plain-JVM graph tests once Metro owns it.
    @Volatile
    private var localizedContextOrNull: Context? = null

    private val localizedContext: Context
        get() = localizedContextOrNull ?: buildLocalizedContext().also { localizedContextOrNull = it }

    /**
     * Registers the string owners this class is responsible for, and starts following the locale.
     *
     * **Called from the `Application`, not from `init`.** Doing it while the object is being built tied
     * it to whenever something happened to inject `ResourceHelper`, which is not a defined moment - the
     * same trap `BaseTestApp` records for the Firebase flags ("runs when that class is injected, far too
     * late here and often not at all"). It also made the class impossible for Metro to own, because a
     * contributed class is constructed for real in the plain-JVM graph tests.
     *
     * Must run before anything asks for text, so it belongs beside `registerStringOwners()` - the other
     * twelve owners are registered there already.
     */
    fun start() {
        // Teach ResourceHelper the names :core:ui owns. It cannot see CoreUiStringIds itself - :core:ui
        // depends on :core:interfaces, not the other way round - so without this a `ui` name read
        // outside a Composable renders as the raw name ("format_carbs" instead of "12 g").
        TextRefIdRegistry.register("coreUi") { name -> CoreUiStringIds.idOf(name) }
        // Same for this module's own names: the classes here name their strings now, and ResourceHelper
        // cannot see ImplementationStringIds from :core:interfaces either.
        TextRefIdRegistry.register("implementation") { name -> ImplementationStringIds.idOf(name) }

        // GeneralLanguage changes trigger Activity.recreate() which rebuilds the context
        // via attachBaseContext/LocaleHelper.wrap — no need to rebuild here and race on Main.
        preferences.observe(BooleanKey.GeneralSimpleMode).drop(1).onEach {
            localizedContextOrNull = buildLocalizedContext()
        }.launchIn(scope)
    }

    private fun buildLocalizedContext(): Context {
        val locale = LocaleHelper.currentLocale(context)
        return if (locale == Locale.getDefault()) context
        else context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
    }

    override fun gs(@StringRes id: Int): String =
        localizedContext.resources.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String {
        return try {
            localizedContext.resources.getString(id, *args)
        } catch (exception: Exception) {
            val resourceName = context.resources.getResourceEntryName(id)
            val resourceValue = context.getString(id)
            val currentLocale: Locale = context.resources.configuration.locales[0]
            fabricPrivacy.logMessage("Failed to get string for resource $resourceName ($id) '$resourceValue' for locale $currentLocale with args ${args.map { it.toString() }}")
            fabricPrivacy.logException(exception)
            try {
                gsNotLocalised(id, *args)
            } catch (exceptionNonLocalized: Exception) {
                fabricPrivacy.logMessage("Fallback failed to get string for resource $resourceName ($id) '$resourceValue' with args ${args.map { it.toString() }}")
                fabricPrivacy.logException(exceptionNonLocalized)
                "FAILED to get string $resourceName"
            }
        }
    }

    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, quantity, *args)

    override fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String =
        with(Configuration(context.resources.configuration)) {
            setLocale(Locale.ENGLISH)
            context.createConfigurationContext(this).getString(id, *args)
        }

    override fun gc(@ColorRes id: Int): Int = ContextCompat.getColor(context, id)

    override fun gac(attributeId: Int): Int = gac(context, attributeId)

    override fun gac(context: Context?, attributeId: Int): Int {
        val baseContext = context ?: this.context
        val typedValue = TypedValue()
        if (!baseContext.theme.resolveAttribute(attributeId, typedValue, true)) {
            return ContextCompat.getColor(baseContext, android.R.color.white)
        }
        // Theme attrs can be literal ARGB (data) or a referenced color resource (resourceId).
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(baseContext, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    override fun gd(@DrawableRes id: Int): Drawable? = AppCompatResources.getDrawable(context, id)

    override fun gb(@BoolRes id: Int): Boolean = context.resources.getBoolean(id)

    @SuppressLint("ResourceType")
    override fun gcs(@ColorRes id: Int): String =
        gs(id).replace("#ff", "#")

    override fun gsa(@ArrayRes id: Int): Array<String> =
        context.resources.getStringArray(id)

    override fun openRawResourceFd(id: Int): AssetFileDescriptor =
        context.resources.openRawResourceFd(id)

    override fun decodeResource(id: Int): Bitmap =
        BitmapFactory.decodeResource(context.resources, id)

    override fun getDisplayMetrics(): DisplayMetrics =
        context.resources.displayMetrics

    override fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun dpToPx(dp: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun shortTextMode(): Boolean = !gb(app.aaps.core.ui.R.bool.isTablet)
}
