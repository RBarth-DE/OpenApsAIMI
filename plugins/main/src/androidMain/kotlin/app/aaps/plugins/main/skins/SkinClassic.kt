package app.aaps.plugins.main.skins

import app.aaps.core.interfaces.configuration.Config
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.OverviewFragmentViews
import dev.zacsweers.metro.Inject

class SkinClassic @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.classic_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun preProcessLandscapeOverviewLayout(
        binding: OverviewFragmentViews,
        isLandscape: Boolean,
        isTablet: Boolean,
        isSmallHeight: Boolean
    ) {
        super.preProcessLandscapeOverviewLayout(binding, isLandscape, isTablet, isSmallHeight)
        if (!config.AAPSCLIENT && (isSmallHeight || isLandscape)) moveButtonsLayout(binding.root)
    }
}
