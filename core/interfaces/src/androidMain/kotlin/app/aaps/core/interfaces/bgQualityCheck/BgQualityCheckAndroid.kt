package app.aaps.core.interfaces.bgQualityCheck

import androidx.annotation.DrawableRes

/**
 * The Android-only half of [BgQualityCheck].
 *
 * The icon is a platform resource id, so it cannot be part of the multiplatform interface. Upstream
 * removed it and renders the state in Compose. The fork's old overview screen still uses it, so it
 * lives here. Callers cast the injected [BgQualityCheck] to this interface.
 */
interface BgQualityCheckAndroid : BgQualityCheck {

    @DrawableRes
    fun icon(): Int
}
