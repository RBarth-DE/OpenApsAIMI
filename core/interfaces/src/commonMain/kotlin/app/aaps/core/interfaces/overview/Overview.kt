package app.aaps.core.interfaces.overview

import app.aaps.core.interfaces.rx.bus.RxBus

/**
 * Overview plugin contract.
 *
 * Only the part that is platform free lives here. Upstream dropped this interface with the Compose
 * migration; the fork keeps it for the old overview screen and the dashboard shell, which are
 * Android only. The Android members (version view, status lights) are in `OverviewAndroid`.
 */
interface Overview {

    val overviewBus: RxBus
}
