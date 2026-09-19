package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.skin.SkinDescriptionProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No skins, so the list is empty.
 *
 * This is an answer about the platform, not a piece of work left undone. A skin in AAPS is a
 * `LinearLayout` subclass that a screen inflates in place of another one - `SkinProvider` in
 * `:plugins:main`'s androidMain is a list of View classes, and switching skins swaps the View tree.
 * An iOS client draws Compose and has no View hierarchy to swap, so there is nothing here to choose
 * between; porting skins would mean designing a second theming system, not translating this one.
 *
 * An empty list is the honest form of that: the skin preference and its search entry are built from
 * this list, so on iOS they do not appear rather than appearing and doing nothing. The reader is
 * `BuiltInSearchables` in `:ui`'s commonMain, which is why the binding has to exist at all.
 *
 * If an iOS skin system is ever built, this file is deleted rather than edited.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosSkinDescriptionProvider() : SkinDescriptionProvider {

    override val skinDescriptions: List<Pair<String, Int>> = emptyList()
}
