package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

enum class UnitDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val minMgdl: Int,
    override val maxMgdl: Int,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val sync: SyncSpec? = null
) : UnitDoublePreferenceKey {

    OverviewEatingSoonTarget( key = "eatingsoon_target", defaultValue =90.0, minMgdl =72, maxMgdl =160, title = TextRef.Literal(""), defaultedBySM = true),
    OverviewActivityTarget( key = "activity_target", defaultValue =140.0, minMgdl =108, maxMgdl =180, title = TextRef.Literal(""), defaultedBySM = true),
    OverviewHypoTarget( key = "hypo_target", defaultValue =160.0, minMgdl =108, maxMgdl =180, title = TextRef.Literal(""), defaultedBySM = true),
    OverviewLowMark(key = "low_mark", defaultValue = 72.0, minMgdl = 25, maxMgdl = 160, title = KeysStrings.pref_title_low_mark, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    OverviewHighMark(key = "high_mark", defaultValue = 180.0, minMgdl = 90, maxMgdl = 250, title = KeysStrings.pref_title_high_mark, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsLgsThreshold(
        key = "lgsThreshold",
        defaultValue = 65.0,
        minMgdl = 60,
        maxMgdl = 100,
        title = KeysStrings.pref_title_lgs_threshold,
        summary = KeysStrings.lgs_threshold_summary,
        defaultedBySM = true,
        dependency = BooleanKey.ApsUseDynamicSensitivity,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AlertRapidFallDrop(
        key = "alert_rapid_fall_drop",
        defaultValue = 30.0,
        minMgdl = 15,
        maxMgdl = 60,
        title = KeysStrings.pref_title_alert_rapid_fall_drop,
        dependency = BooleanKey.AlertRapidFall
    ),
    AlertHyperThreshold(
        key = "alert_hyper_threshold",
        defaultValue = 250.0,
        minMgdl = 140,
        maxMgdl = 400,
        title = KeysStrings.pref_title_alert_hyper_threshold,
        dependency = BooleanKey.AlertHyper
    ),
    AlertHypoThreshold(
        key = "alert_hypo_threshold",
        defaultValue = 70.0,
        minMgdl = 50,
        maxMgdl = 100,
        title = KeysStrings.pref_title_alert_hypo_threshold,
        dependency = BooleanKey.AlertHypo
    ),
    ApsBoostPostExerciseRecoveryTarget( "boost_post_exercise_recovery_target", 144.0, 90, 200, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_post_exercise_target, defaultedBySM = true),
    ApsBoostNightModeBgOffset( "boost_night_mode_bg_offset", 27.0, 0, 90, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_night_bg_offset, defaultedBySM = true),
    ApsBoostDynIsfNormalTarget( "boost_dynisf_normal_target", 99.0, 70, 120, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_dynisf_normal_target, defaultedBySM = true),
    ApsBoostDynIsfBgCap( "boost_dynisf_bg_cap", 210.0, 100, 300, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_dynisf_bg_cap, defaultedBySM = true),
    ;

}
