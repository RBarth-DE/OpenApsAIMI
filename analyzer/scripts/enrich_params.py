#!/usr/bin/env python3
"""
Enrich aimi_parameters.json with specific effect descriptions
by reading source code usage of each parameter.
"""
import json, re, os, subprocess

REPO = '/home/happy/StudioProjects/OpenApsAIMI_V4'
MAIN_KT = f'{REPO}/plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt'
PARAMS_FILE = f'{REPO}/analyzer/data/aimi_parameters.json'
CONTEXT_FILE = f'{REPO}/analyzer/data/aimi_context_for_ai.json'

# ── Parameter key → DoubleKey/BoolKey enum name mapping ──────────────────
KEY_TO_ENUM = {
    # ── SMB / PKPD critical ──
    'key_openapsaimi_max_smb': 'OApsAIMIMaxSMB',
    'key_openapsaimi_high_bg_max_smb': 'OApsAIMIHighBgMaxSMB',
    'aimi_smb_tail_damping': 'OApsAIMISmbTailDamping',
    'aimi_smb_tail_threshold': 'OApsAIMISmbTailThreshold',
    'aimi_smb_exercise_damping': 'OApsAIMISmbExerciseDamping',
    'aimi_smb_late_fat_damping': 'OApsAIMISmbLateFatDamping',
    'aimi_isf_fusion_min_factor': 'OApsAIMIIsfFusionMinFactor',
    'aimi_isf_fusion_max_factor': 'OApsAIMIIsfFusionMaxFactor',
    'aimi_isf_fusion_max_change_per_tick': 'OApsAIMIIsfFusionMaxChangePerTick',
    'aimi_pkpd_anchor_dia_h': 'OApsAIMIPkpdAnchorDiaH',
    'aimi_pkpd_anchor_peak_min': 'OApsAIMIPkpdAnchorPeakMin',
    'key_aimi_t3c_aggressiveness': 'OApsAIMIT3cAggressiveness',
    'key_aimi_adaptive_basal_max_scaling': 'OApsAIMIAdaptiveBasalMaxScaling',
    'aimi_pkpd_pragmatic_relief_min_factor': 'OApsAIMIPkpdPragmaticReliefMinFactor',
    'aimi_priority_max_iob_factor': 'OApsAIMIPriorityMaxIobFactor',
    'aimi_priority_max_iob_extra_u': 'OApsAIMIPriorityMaxIobExtraU',
    'aimi_red_carpet_restore_threshold': 'OApsAIMIRedCarpetRestoreThreshold',
    # ── Governance ──
    'key_aimi_gov_hypo_bg_mgdl': 'OApsAIMIGovernanceHypoBgMgdl',
    'key_aimi_gov_hypo_rate_enter': 'OApsAIMIGovernanceHypoRateEnter',
    'key_aimi_gov_hypo_rate_exit': 'OApsAIMIGovernanceHypoRateExit',
    'key_aimi_gov_severe_hypo_bg_mgdl': 'OApsAIMIGovernanceSevereHypoBgMgdl',
    'key_aimi_gov_hold_basal_decay_rate': 'OApsAIMIGovernanceHoldBasalDecayRate',
    'key_aimi_gov_hold_basal_floor_rate': 'OApsAIMIGovernanceHoldBasalFloorRate',
    'key_aimi_gov_hold_basal_decay_severe': 'OApsAIMIGovernanceHoldBasalDecaySevere',
    'key_aimi_gov_hold_basal_floor_severe': 'OApsAIMIGovernanceHoldBasalFloorSevere',
    'key_aimi_gov_hold_agg_decay_rate': 'OApsAIMIGovernanceHoldAggDecayRate',
    'key_aimi_gov_hold_agg_floor_rate': 'OApsAIMIGovernanceHoldAggFloorRate',
    'key_aimi_gov_hold_agg_decay_severe': 'OApsAIMIGovernanceHoldAggDecaySevere',
    'key_aimi_gov_hold_agg_floor_severe': 'OApsAIMIGovernanceHoldAggFloorSevere',
    'key_aimi_gov_anticipation_lookback_samples': 'OApsAIMIGovernanceAnticipationLookbackSamples',
    'key_aimi_gov_anticipation_margin_mgdl': 'OApsAIMIGovernanceAnticipationMarginMgdl',
    'key_aimi_gov_anticipation_hypo_damp': 'OApsAIMIGovernanceAnticipationHypoDamp',
    'key_aimi_gov_anticipation_decay_blend_max': 'OApsAIMIGovernanceAnticipationDecayBlendMax',
    # ── Tube ──
    'key_aimi_tube_aggressiveness': 'OApsAIMITubeAggressiveness',
    'key_aimi_tube_hypo_floor_mgdl': 'OApsAIMITubeHypoFloorMgdl',
    'key_aimi_tube_hyper_band_mgdl': 'OApsAIMITubeHyperBandMgdl',
    'key_aimi_tube_basal_trim_max': 'OApsAIMITubeBasalTrimMax',
    'key_aimi_tube_kappa_margin': 'OApsAIMITubeKappaMargin',
    # ── Gate booleans ──
    'key_aimi_t3c_brittle_mode': 'OApsAIMIT3cBrittleMode',
    'key_use_aimi_autodrive_active': 'OApsAIMIautoDriveActive',
    'key_use_aimi_autodrive_v3_enhanced_gater': 'OApsAIMIAutodriveV3EnhancedGater',
    'key_aimi_autodrive_v3_authoritative': 'OApsAIMIAutodriveV3Authoritative',
    'key_aimi_autodrive_aggressive_smb_floor': 'OApsAIMIAutodriveAggressiveSmbFloor',
    'key_aimi_hyper_trajectory_release': 'OApsAIMIHyperTrajectoryRelease',
    'key_aimi_peak_governor_enabled': 'OApsAIMIPeakGovernorEnabled',
    'key_aimi_dia_governor_enabled': 'OApsAIMIDiaGovernorEnabled',
    'key_aimi_pkpd_enabled': 'OApsAIMIPkpdEnabled',
    'key_aimi_pkpd_pragmatic_relief_enabled': 'OApsAIMIPkpdPragmaticReliefEnabled',
    'key_aimi_pkpd_prediction_kinetics': 'OApsAIMIPkpdPredictionKinetics',
    'key_aimi_pkpd_endo_reversion': 'OApsAIMIPkpdEndoReversion',
    'key_aimi_pkpd_setup_wizard_completed': 'OApsAIMIPkpdSetupWizardCompleted',
    'key_use_aimi_t3c_adaptive_basal': 'OApsAIMIT3cAdaptiveBasalEnabled',
    'key_aimi_t3c_cfrd_mode': 'OApsAIMIT3cCfrdMode',
    'key_aimi_t3c_physio_informed': 'OApsAIMIT3cPhysioInformed',
    'key_aimi_t3c_cfrd_exacerbation': 'OApsAIMIT3cCfrdExacerbation',
    'key_aimi_recursive_belief_authority': 'OApsAIMIRecursiveBeliefAuthority',
    'key_aimi_recursive_belief_shadow': 'OApsAIMIRecursiveBeliefShadow',
    'key_aimi_recursive_belief_wavelet': 'OApsAIMIRecursiveBeliefWavelet',
    'key_aimi_intelligence_single_learn_path': 'OApsAIMIIntelligenceSingleLearnPath',
    'key_aimi_prediction_authority_enabled': 'OApsAIMIPredictionAuthorityEnabled',
    'key_aimi_prediction_authority_shadow': 'OApsAIMIPredictionAuthorityShadow',
    'key_aimi_smb_comparator_enabled': 'OApsAIMISmbComparatorEnabled',
    'key_aimi_straight_line_tube_enabled': 'OApsAIMIStraightLineTubeEnabled',
    'key_aimi_trajectory_guard_enabled': 'OApsAIMITrajectoryGuardEnabled',
    'key_aimi_loop_blackbox_file_enabled': 'OApsAIMILoopBlackboxFileEnabled',
    'key_aimi_loop_exclusive_invocation': 'OApsAIMILoopExclusiveInvocation',
    'key_aimi_iob_surveillance_guard': 'OApsAIMIIobSurveillanceGuard',
    'key_aimi_effective_iob_release_enabled': 'OApsAIMIEffectiveIobReleaseEnabled',
    'key_aimi_undeclared_cob_enabled': 'OApsAIMIUndeclaredCobEnabled',
    'aimi_physio_assistant_enable': 'OApsAIMIPhysioAssistantEnable',
    'aimi_physio_debug_logs': 'OApsAIMIPhysioDebugLogs',
    'aimi_physio_hrv_enable': 'OApsAIMIPhysioHrvEnable',
    'aimi_physio_sleep_enable': 'OApsAIMIPhysioSleepEnable',
    'aimi_physio_llm_enable': 'OApsAIMIPhysioLLMEnable',
    'key_aimi_context_enabled': 'OApsAIMIContextEnabled',
    'key_aimi_context_llm_enabled': 'OApsAIMIContextLLMEnabled',
    'key_aimi_tpo_enabled': 'OApsAIMITpoEnabled',
    'key_aimi_tpo_llm_confirm_enabled': 'OApsAIMITpoLLMConfirmEnabled',
    'key_aimi_tpo_notify_on_apply': 'OApsAIMITpoNotifyOnApply',
    'key_aimi_thyroid_enabled': 'OApsAIMIThyroidEnabled',
    'key_aimi_thyroid_debug': 'OApsAIMIThyroidDebug',
    'key_use_AimiPregnancy': 'OApsAIMIPregnancy',
    'key_use_AimiForceLimits': 'OApsAIMIForceLimits',
    'key_use_Aimi_honeymoon': 'OApsAIMIHoneymoon',
    'key_use_Aimi_wcycle': 'OApsAIMIWCycle',
    'key_use_Aimi_wcycle_require_confirm': 'OApsAIMIWCycleRequireConfirm',
    'key_use_Aimi_wcycle_shadow': 'OApsAIMIWCycleShadow',
    'key_use_Aimi_xdripOM': 'OApsAIMIXdripOM',
    'key_enable_ML_training': 'OApsAIMIEnableMLTraining',
    'key_enable_basal': 'OApsAIMIEnableBasal',
    'aimi_meal_advisor_trigger': 'OApsAIMIMealAdvisorTrigger',
    'aimi_cosine_gate_enabled': 'OApsAIMICosineGateEnabled',
    'aimi_cosine_gate_alpha': 'OApsAIMICosineGateAlpha',
    'aimi_cosine_gate_max_sens': 'OApsAIMICosineGateMaxSens',
    'aimi_cosine_gate_min_sens': 'OApsAIMICosineGateMinSens',
    'aimi_cosine_gate_min_dq': 'OApsAIMICosineGateMinDq',
    'aimi_cosine_gate_max_shift': 'OApsAIMICosineGateMaxShift',
    'aimi_dyn_isf_trajectory_tuning_enabled': 'OApsAIMIDynIsfTrajectoryTuningEnabled',
    'aimi_dyn_isf_trajectory_max_fraction': 'OApsAIMIDynIsfTrajectoryMaxFraction',
    'aimi_dyn_isf_trajectory_shadow_only': 'OApsAIMIDynIsfTrajectoryShadowOnly',
    'aimi_emergency_sos_enable': 'OApsAIMIEmergencySosEnable',
    'aimi_emergency_sos_immediate_threshold': 'OApsAIMIEmergencySosImmediateThreshold',
    'aimi_emergency_sos_stale_threshold': 'OApsAIMIEmergencySosStaleThreshold',
    'aimi_emergency_sos_threshold': 'OApsAIMIEmergencySosThreshold',
    'openapsama_enable_autoISF': 'OApsAIMIEnableAutoISF',
    'key_aimi_hyper_deep_dev_mgdl': 'OApsAIMIHyperDeepDevMgdl',
    'key_aimi_hyper_established_dev_mgdl': 'OApsAIMIHyperEstablishedDevMgdl',
    # ── DIA/Peak governors ──
    'aimi_dia_governor_learned_weight': 'OApsAIMIDiaGovernorLearnedWeight',
    'aimi_peak_governor_learned_weight': 'OApsAIMIPeakGovernorLearnedWeight',
    # ── NGR ──
    'key_aimi_ngr_max_iob_extra': 'OApsAIMINgrMaxIobExtra',
    'key_aimi_ngr_max_smb_clamp': 'OApsAIMINgrMaxSmbClamp',
    'key_aimi_ngr_min_duration': 'OApsAIMINgrMinDuration',
    'key_aimi_ngr_min_eventual_over_target': 'OApsAIMINgrMinEventualOverTarget',
    'key_aimi_ngr_min_rise_slope': 'OApsAIMINgrMinRiseSlope',
    'key_aimi_ngr_basal_multiplier': 'OApsAIMINgrBasalMultiplier',
    'key_aimi_ngr_smb_multiplier': 'OApsAIMINgrSmbMultiplier',
    'key_aimi_ngr_decay_minutes': 'OApsAIMINgrDecayMinutes',
    'key_aimi_ngr_enabled': 'OApsAIMINgrEnabled',
    # ── T3c ──
    'key_aimi_t3c_activation_threshold': 'OApsAIMIT3cActivationThreshold',
    'key_aimi_t3c_anticipation_strength': 'OApsAIMIT3cAnticipationStrength',
    'key_aimi_t3c_cfrd_cob_delay_min': 'OApsAIMIT3cCfrdCobDelayMin',
    'key_aimi_t3c_cfrd_lgs_floor': 'OApsAIMIT3cCfrdLgsFloor',
    # ── Misc ──
    'key_aimi_intratick_stall_seconds': 'OApsAIMIIntratickStallSeconds',
    'key_aimi_hyper_trajectory_release_aggressive': 'OApsAIMIHyperTrajectoryReleaseAggressive',
    'aimi_undelared_cob_max_g': 'OApsAIMIUndeclaredCobMaxG',
}

# ── Specific descriptions based on actual source code behavior ──────────────
SPECIFIC_EFFECTS = {
    # ═══ SMB / PKPD CRITICAL ═══
    'key_openapsaimi_max_smb': {
        'effect_high': 'Permits larger SMB boluses (up to the set limit) — corrects highs faster but increases insulin stacking risk if TDD or IOB constraints are loose.',
        'effect_low': 'Restricts each SMB to a smaller maximum — safer for hypo-prone users but may under-correct persistent hyperglycemia.',
        'impact': 'critical',
    },
    'key_openapsaimi_high_bg_max_smb': {
        'effect_high': 'Allows larger SMB doses specifically when BG is above the high-BG threshold — provides extra correction headroom for stubborn highs.',
        'effect_low': 'Keeps SMBs close to the standard cap even during high BG — reduces stacking risk but may slow correction of severe hyperglycemia.',
        'impact': 'critical',
    },
    'aimi_smb_tail_damping': {
        'effect_high': 'Weaker damping — SMBs retain most of their size even late in the insulin activity curve (more aggressive, similar to standard oref1 tail).',
        'effect_low': 'Stronger damping — SMBs are shrunk more aggressively during the insulin tail phase (conservative, reduces late stacking at the cost of correction speed).',
        'impact': 'critical',
    },
    'aimi_smb_tail_threshold': {
        'effect_high': 'Activates damping sooner — SMB suppression begins at a higher IOB level, constraining corrections more aggressively.',
        'effect_low': 'Delays damping — allows SMBs to proceed at full size until IOB drops very low, preserving correction intent but risking stacking.',
        'impact': 'critical',
    },
    'aimi_smb_exercise_damping': {
        'effect_high': 'Weaker exercise damping — SMBs are only mildly reduced during physical activity (suitable for users with stable BG during exercise).',
        'effect_low': 'Stronger exercise damping — SMBs are heavily suppressed when exercise is detected (prevents hypos during activity).',
        'impact': 'critical',
    },
    'aimi_smb_late_fat_damping': {
        'effect_high': 'Weaker late-fat damping — SMBs are less constrained during slow-digesting meal contexts (helps cover pizza/high-fat meals).',
        'effect_low': 'Stronger late-fat damping — SMBs are aggressively suppressed during suspected late fat absorption (conservative, prevents delayed stacking).',
        'impact': 'critical',
    },
    'aimi_isf_fusion_min_factor': {
        'effect_high': 'Raises the ISF floor — prevents fusion from lowering ISF below this fraction of profile ISF (protects against over-aggressive corrections that could cause hypos).',
        'effect_low': 'Lowers the ISF floor — allows fusion to reduce ISF further, making corrections more aggressive when PKPD + TDD signal suggests higher sensitivity.',
        'impact': 'critical',
    },
    'aimi_isf_fusion_max_factor': {
        'effect_high': 'Raises the ISF ceiling — allows fusion to increase ISF more aggressively, making corrections weaker (conservative, suitable for hypo-prone users).',
        'effect_low': 'Lowers the ISF ceiling — constrains ISF increase from fusion, preventing excessive correction weakening during insulin-resistant states.',
        'impact': 'critical',
    },
    'aimi_isf_fusion_max_change_per_tick': {
        'effect_high': 'Allows larger ISF adjustments per 5-min tick — ISF adapts faster to changing conditions but may oscillate.',
        'effect_low': 'Restricts ISF adjustment speed — ISF moves more gradually (smoother but may lag during rapid sensitivity changes).',
        'impact': 'high',
    },
    'aimi_pkpd_anchor_dia_h': {
        'effect_high': 'Pulls the PKPD learner toward a longer DIA estimate — IOB decays more slowly, insulin tail is respected longer, SMBs are more constrained on the tail.',
        'effect_low': 'Pulls the PKPD learner toward a shorter DIA — IOB clears faster, SMBs face less tail damping, corrections are more aggressive.',
        'impact': 'high',
    },
    'aimi_pkpd_anchor_peak_min': {
        'effect_high': 'Pulls the PKPD learner toward a later insulin peak — peak activity window is shifted later, affecting IOB calculation and SMB timing.',
        'effect_low': 'Pulls the PKPD learner toward an earlier peak — insulin activity is front-loaded, peak IOB arrives sooner.',
        'impact': 'high',
    },
    'key_aimi_t3c_aggressiveness': {
        'effect_high': 'Stronger PI controller action — T3c Brittle Mode delivers larger and more frequent TBR increases for hyperglycemia (faster correction but higher hypo risk).',
        'effect_low': 'Weaker PI controller — T3c mode is more conservative with TBR adjustments (safer but slower to address rising BG).',
        'impact': 'critical',
    },
    'key_aimi_adaptive_basal_max_scaling': {
        'effect_high': 'Allows the neural learner to scale basal up to a larger multiple of profile basal during hyperglycemic patterns (more aggressive automatic basal increases).',
        'effect_low': 'Caps neural basal scaling tighter — limits automatic basal increases to a smaller fraction above profile (more conservative adaptation).',
        'impact': 'high',
    },
    # ═══ GOVERNANCE ═══
    'key_aimi_gov_hypo_bg_mgdl': {
        'effect_high': 'Raises the hypo detection threshold — governance enters HOLD state at higher BG values (more protective, activates sooner).',
        'effect_low': 'Lowers the hypo threshold — governance only triggers HOLD at lower BG (less protective, allows more aggressive basal/SMB before intervening).',
        'impact': 'critical',
    },
    'key_aimi_gov_hypo_rate_enter': {
        'effect_high': 'Requires a higher fraction of hypo samples before entering HOLD — governance is less sensitive, tolerating more low readings before clamping down.',
        'effect_low': 'Triggers HOLD with fewer hypo samples — governance clamps down faster on emerging hypo patterns (more responsive but may over-correct).',
        'impact': 'critical',
    },
    'key_aimi_gov_hypo_rate_exit': {
        'effect_high': 'Exits HOLD sooner after hypo pressure clears — less hysteresis, allows basal/SMB to normalize faster.',
        'effect_low': 'Requires lower hypo rate before releasing HOLD — stronger hysteresis, keeps conservative stance longer after hypos resolve.',
        'impact': 'critical',
    },
    'key_aimi_gov_severe_hypo_bg_mgdl': {
        'effect_high': 'Raises the severe hypo threshold — applies stronger governance guardrails at higher BG (more protective).',
        'effect_low': 'Lowers the severe hypo threshold — severe-tier guardrails only engage at dangerously low BG (less restrictive).',
        'impact': 'critical',
    },
    'key_aimi_gov_hold_basal_decay_rate': {
        'effect_high': 'Slower decay — basal scaling factor decays toward floor more slowly during HOLD (extends conservative restraint).',
        'effect_low': 'Faster decay — basal scaling drops toward floor faster when governance is active (more aggressive clamp-down).',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_basal_floor_rate': {
        'effect_high': 'Higher floor — basal cannot be scaled below this fraction during HOLD (preserves more basal delivery even during hypo governance).',
        'effect_low': 'Lower floor — allows basal to be cut more severely during HOLD (more aggressive hypo protection).',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_basal_decay_severe': {
        'effect_high': 'Slower decay in severe tier — basal scaling decays more gradually during severe hypo conditions.',
        'effect_low': 'Faster decay in severe tier — basal scaling drops more aggressively when severe hypo is detected.',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_basal_floor_severe': {
        'effect_high': 'Higher severe floor — more basal delivery preserved even during severe hypo conditions.',
        'effect_low': 'Lower severe floor — allows deeper basal cuts during severe hypo events.',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_agg_decay_rate': {
        'effect_high': 'Slower aggressiveness decay — T3C aggressiveness factor decays more slowly during HOLD.',
        'effect_low': 'Faster aggressiveness decay — T3C aggressiveness is rolled back more aggressively during hypo pressure.',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_agg_floor_rate': {
        'effect_high': 'Higher aggressiveness floor — preserves more T3C aggressiveness during HOLD.',
        'effect_low': 'Lower aggressiveness floor — allows T3C aggressiveness to be cut more severely during hypo governance.',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_agg_decay_severe': {
        'effect_high': 'Slower severe-tiers aggressiveness decay — T3C aggressiveness drops more gradually during severe hypo.',
        'effect_low': 'Faster severe-tiers aggressiveness decay — aggressiveness is cut more aggressively in severe hypo tier.',
        'impact': 'medium',
    },
    'key_aimi_gov_hold_agg_floor_severe': {
        'effect_high': 'Higher severe aggressiveness floor — preserves more aggressiveness even during severe hypo events.',
        'effect_low': 'Lower severe aggressiveness floor — allows deeper aggressiveness cuts in severe hypo conditions.',
        'impact': 'medium',
    },
    'key_aimi_gov_anticipation_lookback_samples': {
        'effect_high': 'Uses more recent samples for anticipation — short-term predicted recovery has stronger influence on governance decisions.',
        'effect_low': 'Averages over fewer samples — anticipation relief is based on a tighter recent window.',
        'impact': 'medium',
    },
    'key_aimi_gov_anticipation_margin_mgdl': {
        'effect_high': 'Requires predicted trough to be well above hypo threshold — stricter criteria for granting governance relief from anticipatory recovery.',
        'effect_low': 'Smaller margin — governance relief is granted more easily when prediction suggests imminent recovery.',
        'impact': 'medium',
    },
    'key_aimi_gov_anticipation_hypo_damp': {
        'effect_high': 'Stronger dampening — anticipation relief has more power to soften the governance HOLD (faster normalization after predicted recovery).',
        'effect_low': 'Weaker dampening — anticipation relief barely softens governance (HOLD persists even when recovery is predicted).',
        'impact': 'high',
    },
    'key_aimi_gov_anticipation_decay_blend_max': {
        'effect_high': 'Maximum softening of hold decay rates when prediction is favorable — governance relaxes faster.',
        'effect_low': 'Less softening — decay rates stay closer to the configured values regardless of prediction.',
        'impact': 'medium',
    },

    # ═══ TUBE ═══
    'key_aimi_tube_aggressiveness': {
        'effect_high': 'Narrower safety tube — SMBs are constrained more aggressively, harder cap on deviations (more protective).',
        'effect_low': 'Wider safety tube — SMBs face less constraint from the tube advisor (more aggressive corrections allowed).',
        'impact': 'critical',
    },
    'key_aimi_tube_hypo_floor_mgdl': {
        'effect_high': 'Raises the absolute BG floor — SMBs are blocked at a higher BG, preventing insulin delivery closer to normal range.',
        'effect_low': 'Lowers the BG floor — allows SMBs at lower BG (more aggressive, but higher hypo risk).',
        'impact': 'critical',
    },
    'key_aimi_tube_hyper_band_mgdl': {
        'effect_high': 'Raises the hyper band — tube advisor tolerates higher BG before constraining SMB size (more aggressive corrections).',
        'effect_low': 'Lowers the hyper band — tube advisor begins constraining SMBs at lower BG (more conservative).',
        'impact': 'high',
    },
    'key_aimi_tube_basal_trim_max': {
        'effect_high': 'Allows larger basal trims from the tube advisor — more aggressive basal reductions when deviation signals over-insulinization.',
        'effect_low': 'Smaller basal trim cap — more conservative basal adjustments from tube advisor.',
        'impact': 'high',
    },
    'key_aimi_tube_kappa_margin': {
        'effect_high': 'Larger safety margin — more buffer between predicted BG and the tube boundary (more conservative constraint).',
        'effect_low': 'Smaller safety margin — tube boundary is tighter around the prediction (less conservative, allows more aggressive dosing).',
        'impact': 'high',
    },

    # ═══ GATE BOOLEANS ═══
    'key_aimi_t3c_brittle_mode': {
        'effect_high': 'T3c mode is active — bypasses standard AIMI logic, disables SMB, uses PI controller with TBR-only delivery (critical for exocrine-insufficient patients).',
        'effect_low': 'Standard AIMI algorithm runs normally with full SMB + TBR support (normal for non-T3c users).',
        'impact': 'critical',
    },
    'key_use_aimi_autodrive_active': {
        'effect_high': 'Autodrive V3 is active — MPC-like controller overrides standard SMB decisions when AutoDriveGater criteria are met (BG>120, rising, valid context).',
        'effect_low': 'Standard AIMI SMB decision logic runs without MPC override.',
        'impact': 'critical',
    },
    'key_aimi_autodrive_v3_authoritative': {
        'effect_high': 'Autodrive V3 completely overrides standard SMB logic — sets skipLegacySmbBlender=true, bypasses executeSmbInstruction entirely when engaged.',
        'effect_low': 'Autodrive V3 runs in shadow mode — computes but does not override standard SMB decisions.',
        'impact': 'critical',
    },
    'key_aimi_autodrive_aggressive_smb_floor': {
        'effect_high': 'SMB floor during Autodrive is raised — Autodrive cannot reduce SMBs below this level when engaged (preserves minimum correction).',
        'effect_low': 'SMB floor is lower — Autodrive has more freedom to zero out SMBs when context is unfavorable.',
        'impact': 'high',
    },
    'key_aimi_hyper_trajectory_release': {
        'effect_high': 'Hyper-Trajectory Release is active — relaxes SMB constraints when trajectory analysis confirms sustained hyperglycemia with low hypo risk.',
        'effect_low': 'Standard trajectory safety constraints apply — no automatic relaxation for stubborn highs.',
        'impact': 'high',
    },
    'key_aimi_peak_governor_enabled': {
        'effect_high': 'Peak Governor is active — blends PKPD-learned insulin peak with profile peak, applying physio/site/trajectory shifts per tick.',
        'effect_low': 'Profile-configured insulin peak time is used unchanged — PKPD learning is ignored for peak timing.',
        'impact': 'high',
    },
    'key_aimi_dia_governor_enabled': {
        'effect_high': 'DIA Governor is active — blends PKPD-learned DIA with profile DIA, adjusting IOB decay curves in real-time.',
        'effect_low': 'Profile-configured DIA is used unchanged — PKPD learning is ignored for DIA.',
        'impact': 'high',
    },
    'key_aimi_pkpd_enabled': {
        'effect_high': 'PKPD module active — Weibull-based insulin kinetics learner adapts DIA and peak from observed BG response data; ISF fusion adjusts sensitivity.',
        'effect_low': 'PKPD module disabled — profile DIA/peak/ISF are used without online adaptation.',
        'impact': 'high',
    },
    'key_aimi_pkpd_pragmatic_relief_enabled': {
        'effect_high': 'Pragmatic Relief active — relaxes PKPD tail damping constraints during explicit meal and high-rise contexts, allowing more aggressive SMBs when confidence is high.',
        'effect_low': 'PKPD damping applies uniformly — meal context does not automatically relax tail damping.',
        'impact': 'high',
    },
    'aimi_pkpd_pragmatic_relief_min_factor': {
        'effect_high': 'Higher minimum PKPD factor — preserves more SMB intent even during damping (less suppression in priority contexts).',
        'effect_low': 'Lower minimum factor — allows PKPD damping to reduce SMBs more aggressively during tail/activity windows.',
        'impact': 'high',
    },
    'aimi_priority_max_iob_factor': {
        'effect_high': 'Higher MaxIOB multiplier — allows IOB to exceed standard safe limits more during explicit meal/priority windows (faster meal coverage).',
        'effect_low': 'Lower MaxIOB multiplier — less IOB headroom, closer to standard safety limits even during priority contexts.',
        'impact': 'critical',
    },
    'aimi_priority_max_iob_extra_u': {
        'effect_high': 'Larger absolute IOB headroom — additional IOB allowance in priority windows (can push mealtime coverage further above safety caps).',
        'effect_low': 'Less extra IOB — tightens the priority IOB budget closer to standard safety limits.',
        'impact': 'critical',
    },
    'aimi_red_carpet_restore_threshold': {
        'effect_high': 'Restores SMB damping faster after the red-carpet (tail activity) phase ends — SMBs return to full strength sooner.',
        'effect_low': 'Delays damping restoration — SMBs stay damped longer after the tail phase (more conservative, less SMB snap-back risk).',
        'impact': 'critical',
    },

    # ═══ DIA/PEAK GOVERNORS ═══
    'aimi_dia_governor_learned_weight': {
        'effect_high': 'Heavily favors PKPD-learned DIA over profile DIA — IOB curves adapt quickly to observed patient kinetics.',
        'effect_low': 'Mostly follows profile DIA — learned DIA has minimal influence, PKPD adaptation is slow.',
        'impact': 'high',
    },
    'aimi_peak_governor_learned_weight': {
        'effect_high': 'Heavily favors PKPD-learned peak time over profile peak — insulin timing adapts quickly to observed response.',
        'effect_low': 'Mostly follows profile peak time — learned peak has minimal influence.',
        'impact': 'high',
    },

    # ═══ KEY BOOLEAN GATES (continued) ═══
    'key_aimi_pkpd_prediction_kinetics': {
        'effect_high': 'Prediction curves use learned PK/PD kinetics — IOB predictions reflect the patient-specific DIA and peak from the PKPD learner.',
        'effect_low': 'Predictions use legacy kinetics — standard profile DIA/peak curves are used for all forward IOB projections.',
        'impact': 'high',
    },
    'key_use_aimi_t3c_adaptive_basal': {
        'effect_high': 'Neural network adapts basal scaling in real-time for T3c mode — governance may scale basal up/down within configured limits based on learned patterns.',
        'effect_low': 'Fixed T3c basal controller — only the PI controller runs without neural adaptation.',
        'impact': 'medium',
    },
    'key_aimi_recursive_belief_authority': {
        'effect_high': 'RBT Authority mode — Recursive Belief Engine decisions override standard AIMI outputs (full production mode after shadow review).',
        'effect_low': 'RBT runs in shadow-only — beliefs are computed and logged but do not control decisions.',
        'impact': 'high',
    },
    'key_aimi_recursive_belief_shadow': {
        'effect_high': 'RBT Shadow mode active — Recursive Belief Engine computes multi-scale beliefs and exports them to JSONL for offline review/validation.',
        'effect_low': 'RBT is inactive — no belief computation or export occurs.',
        'impact': 'medium',
    },
    'key_aimi_recursive_belief_wavelet': {
        'effect_high': 'Wavelet-based signal decomposition active — RBT uses wavelet analysis for multi-scale feature extraction (more nuanced than simple averages).',
        'effect_low': 'Standard signal processing — RBT uses time-domain features without wavelet decomposition.',
        'impact': 'medium',
    },
    'key_aimi_straight_line_tube_enabled': {
        'effect_high': 'Straight-Line Tube Advisor active — MPC-lite SMB cap regulator constrains SMBs based on predicted trajectory vs safety tube boundaries.',
        'effect_low': 'Tube advisor disabled — SMB caps rely only on standard MaxSMB and safety limits.',
        'impact': 'high',
    },
    'key_aimi_trajectory_guard_enabled': {
        'effect_high': 'Trajectory Guard active — phase-space analysis classifies BG trajectory into 5 states (NORMAL, SPIRAL_UP, SPIRAL_DOWN, PLATEAU, RAPID_FALL) and modulates SMB accordingly.',
        'effect_low': 'No trajectory classification — SMB sizing does not account for phase-space dynamics.',
        'impact': 'high',
    },
    'aimi_cosine_gate_enabled': {
        'effect_high': 'Adaptive Kernel Bank active — cosine similarity gates compare current physiological context to learned kernels, modulating ISF based on similarity match.',
        'effect_low': 'Adaptive Kernel Bank disabled — ISF modulation does not use cosine similarity to historical patterns.',
        'impact': 'high',
    },
    'aimi_cosine_gate_alpha': {
        'effect_high': 'Sharper gate — ISF modulation responds more aggressively to small changes in cosine similarity (more sensitive to context shifts).',
        'effect_low': 'Softer gate — ISF modulation is smoother and slower to change with physiological context.',
        'impact': 'high',
    },
    'aimi_cosine_gate_max_sens': {
        'effect_high': 'Higher ceiling — ISF can be scaled up more when physiological context matches strongly (up to this multiplier).',
        'effect_low': 'Lower ceiling — ISF scaling is constrained to a smaller range even at strong similarity.',
        'impact': 'high',
    },
    'aimi_cosine_gate_min_sens': {
        'effect_high': 'Higher floor — ISF cannot drop below this fraction even when similarity is weak (preserves minimum sensitivity).',
        'effect_low': 'Lower floor — ISF can be reduced more aggressively when physiological context is unfavorable.',
        'impact': 'high',
    },
    'aimi_cosine_gate_min_dq': {
        'effect_high': 'Higher data quality threshold — cosine gate only activates with cleaner sensor data (more selective).',
        'effect_low': 'Lower quality threshold — cosine gate activates more often, even with noisier CGM readings.',
        'impact': 'medium',
    },
    'aimi_cosine_gate_max_shift': {
        'effect_high': 'Larger ISF shift allowed — cosine gate can change ISF more dramatically per tick when context demands it.',
        'effect_low': 'Smaller shift — ISF changes more gradually regardless of context similarity strength.',
        'impact': 'high',
    },

    # ═══ PREDICTION / INTELLIGENCE / SMB ═══
    'key_aimi_intelligence_single_learn_path': {
        'effect_high': 'Only one code path may update learners — other paths run in read-only mode (avoids conflicting gradient updates to PKPD/Basal learners).',
        'effect_low': 'Multiple code paths may concurrently update learners (higher throughput but potential for conflicting updates).',
        'impact': 'medium',
    },
    'key_aimi_prediction_authority_enabled': {
        'effect_high': 'PKPD-based terminal BG predictions override legacy eventualBG — scenario projection engine with CLINICAL_FLOOR and SCENARIO_BEST drives dosing.',
        'effect_low': 'Legacy eventualBG prediction is used — PKPD scenario projections are ignored for dosing decisions.',
        'impact': 'high',
    },
    'key_aimi_prediction_authority_shadow': {
        'effect_high': 'Predictions from PKPD authority are logged alongside legacy predictions for comparison — no actual override of dosing.',
        'effect_low': 'Shadow mode off — no parallel prediction comparison is logged.',
        'impact': 'medium',
    },
    'key_aimi_smb_comparator_enabled': {
        'effect_high': 'SMB comparator active — AIMI SMB decisions are compared against standard oref1 SMB for audit and validation.',
        'effect_low': 'No SMB comparison — only AIMI SMB output is recorded.',
        'impact': 'medium',
    },

    # ═══ LOOP / LOGGING / IOB ═══
    'key_aimi_loop_blackbox_file_enabled': {
        'effect_high': 'Blackbox logging active — complete loop state is written to file each tick for post-hoc analysis and debugging.',
        'effect_low': 'Blackbox logging disabled — reduced I/O load but no forensic trace for troubleshooting.',
        'impact': 'low',
    },
    'key_aimi_loop_exclusive_invocation': {
        'effect_high': 'Prevents concurrent loop invocations — if a previous tick is still running, the new one is skipped (safety lock).',
        'effect_low': 'Loop may overlap — multiple ticks could run concurrently (higher throughput but risk of state corruption).',
        'impact': 'medium',
    },
    'key_aimi_iob_surveillance_guard': {
        'effect_high': 'IOB Surveillance active — monitors cumulative IOB against TDD-based safety limits, can trigger alerts and SMB suppression.',
        'effect_low': 'No IOB surveillance — IOB safety limits are not actively monitored beyond standard MaxIOB checks.',
        'impact': 'high',
    },
    'key_aimi_effective_iob_release_enabled': {
        'effect_high': 'Effective IOB release active — IOB burden is relaxed based on recent glycemic trajectory (allows more insulin when BG is rising despite IOB).',
        'effect_low': 'Standard IOB constraints — no automatic relaxation based on trajectory.',
        'impact': 'medium',
    },

    # ═══ UAM / COB ═══
    'key_aimi_undeclared_cob_enabled': {
        'effect_high': 'Undeclared COB detection active — algorithm estimates carbs from BG rise pattern when user does not announce meals.',
        'effect_low': 'Only declared carbs are used — unannounced meals are treated as UAM (unannounced meal) with different handling.',
        'impact': 'medium',
    },
    'aimi_meal_advisor_trigger': {
        'effect_high': 'Meal Advisor triggers more readily — suggests carb entries at lower confidence thresholds for detected meals.',
        'effect_low': 'Meal Advisor is more conservative — requires stronger evidence before suggesting carb entries.',
        'impact': 'medium',
    },

    # ═══ PHYSIO ═══
    'aimi_physio_assistant_enable': {
        'effect_high': 'Physio Assistant active — reads Health Connect data (HRV, sleep, steps, temperature) to modulate ISF, peak timing, and activity flags.',
        'effect_low': 'No physiological data integration — activity/sleep/HRV have no effect on insulin calculations.',
        'impact': 'medium',
    },
    'aimi_physio_debug_logs': {
        'effect_high': 'Debug logging for Physio Assistant enabled — detailed sensor readings and adjustments are logged each tick.',
        'effect_low': 'Normal logging level — only significant physio events are recorded.',
        'impact': 'low',
    },
    'aimi_physio_hrv_enable': {
        'effect_high': 'HRV analysis active — heart rate variability from Health Connect is used to estimate stress/autonomic state and adjust ISF.',
        'effect_low': 'HRV data is ignored — stress estimation does not affect ISF.',
        'impact': 'medium',
    },
    'aimi_physio_sleep_enable': {
        'effect_high': 'Sleep detection active — sleep data from Health Connect is used to reduce insulin sensitivity estimates and adjust basal targets.',
        'effect_low': 'Sleep data ignored — insulin calculations are uniform across sleep/wake cycles.',
        'impact': 'medium',
    },

    # ═══ CONTEXT / LLM / TPO ═══
    'key_aimi_context_enabled': {
        'effect_high': 'Context-Aware module active — loop state snapshots are built for LLM consumption and advisor analysis.',
        'effect_low': 'Context module disabled — advisor and LLM features cannot access runtime loop context.',
        'impact': 'medium',
    },
    'key_aimi_context_llm_enabled': {
        'effect_high': 'LLM integration active — context snapshots are formatted for LLM prompts when advisor or external AI features are used.',
        'effect_low': 'LLM features are disabled — context data may be collected but is not sent to any LLM endpoint.',
        'impact': 'medium',
    },
    'key_aimi_tpo_enabled': {
        'effect_high': 'Transient Preference Overlay active — temporary parameter overrides can be applied (e.g., sick-day adjustments) with automatic expiry.',
        'effect_low': 'No TPO overrides — all preferences follow their configured values without time-limited adjustments.',
        'impact': 'medium',
    },
    'key_aimi_tpo_llm_confirm_enabled': {
        'effect_high': 'LLM must confirm TPO overrides before they are applied — AI validates the override against clinical context.',
        'effect_low': 'TPO overrides are applied immediately without LLM confirmation.',
        'impact': 'low',
    },
    'key_aimi_tpo_notify_on_apply': {
        'effect_high': 'Android notification is shown each time a TPO override is applied or expires.',
        'effect_low': 'No notification — TPO changes happen silently.',
        'impact': 'low',
    },

    # ═══ THYROID ═══
    'key_aimi_thyroid_enabled': {
        'effect_high': 'Thyroid module active — hormone-aware ISF adjustments based on thyroid treatment phase (hyper/hypo/euthyroid status).',
        'effect_low': 'Thyroid status is ignored — no hormone-based ISF modulation.',
        'impact': 'medium',
    },

    # ═══ WOMEN'S CYCLE / PREGNANCY / FORCE LIMITS ═══
    'key_use_AimiPregnancy': {
        'effect_high': 'Pregnancy mode active — targets and ISF are adjusted for tighter control (lower targets, higher insulin sensitivity during pregnancy progression).',
        'effect_low': 'Standard targets — no pregnancy-specific adjustments.',
        'impact': 'high',
    },
    'key_use_AimiForceLimits': {
        'effect_high': 'Force Limits active — safety caps (MaxIOB, MaxBasal, MaxSMB) are enforced with no exceptions or priority-headroom relaxations.',
        'effect_low': 'Standard enforcement — priority contexts may temporarily relax safety caps within configured bounds.',
        'impact': 'high',
    },
    'key_use_Aimi_honeymoon': {
        'effect_high': 'Honeymoon detection active — insulin needs are reduced based on detected residual beta-cell function patterns (lower basal and ISF).',
        'effect_low': 'No honeymoon adjustment — standard insulin calculations.',
        'impact': 'medium',
    },
    'key_use_Aimi_wcycle': {
        'effect_high': 'Women\'s Cycle tracking active — ISF, basal, and SMB are modulated based on menstrual cycle phase (luteal vs follicular insulin sensitivity shifts).',
        'effect_low': 'No cycle-based modulation — uniform insulin calculations across cycle phases.',
        'impact': 'medium',
    },
    'key_use_Aimi_xdripOM': {
        'effect_high': 'xDrip+ Online Measurements active — reads extended CGM metadata from xDrip+ for enhanced signal processing.',
        'effect_low': 'Standard CGM data only — no xDrip+-specific metadata enhancements.',
        'impact': 'medium',
    },

    # ═══ ML / AUTOISF ═══
    'key_enable_ML_training': {
        'effect_high': 'ML training pipeline active — background model training runs on collected data (neural networks for basal/T3c adaptation).',
        'effect_low': 'ML training disabled — models use frozen weights from last training run or default initializations.',
        'impact': 'medium',
    },
    'key_enable_basal': {
        'effect_high': 'Basal delivery enabled — pump receives TBR commands from AIMI (normal closed-loop operation).',
        'effect_low': 'Basal delivery suspended — loop runs in open-loop/simulation mode without commanding the pump.',
        'impact': 'critical',
    },
    'openapsama_enable_autoISF': {
        'effect_high': 'AutoISF active — ISF is dynamically adjusted based on TDD, deviation patterns, and circadian rhythm (autosens-like behavior).',
        'effect_low': 'Static ISF — profile-configured ISF is used without dynamic adjustment.',
        'impact': 'high',
    },

    # ═══ DYNAMIC ISF TRAJECTORY ═══
    'aimi_dyn_isf_trajectory_tuning_enabled': {
        'effect_high': 'Dynamic ISF trajectory tuning active — ISF adapts per tick using trajectory energy and phase-space classification for patient-specific sensitivity mapping.',
        'effect_low': 'Static ISF — trajectory-based ISF adjustments are disabled.',
        'impact': 'high',
    },
    'aimi_dyn_isf_trajectory_max_fraction': {
        'effect_high': 'Larger ISF change per tick — trajectory-driven ISF can shift more aggressively (faster adaptation but potential oscillation).',
        'effect_low': 'Smaller ISF change per tick — ISF moves gradually even with strong trajectory signals.',
        'impact': 'high',
    },
    'aimi_dyn_isf_trajectory_shadow_only': {
        'effect_high': 'Shadow-only mode — trajectory ISF adjustments are computed and logged but do not affect actual dosing (safe validation mode).',
        'effect_low': 'Full production mode — trajectory ISF adjustments are applied to dosing in real-time.',
        'impact': 'high',
    },

    # ═══ T3C CONTINUED ═══
    'key_aimi_t3c_activation_threshold': {
        'effect_high': 'T3c mode activates at higher BG — more conservative entry, only engages PI controller for more significant hyperglycemia.',
        'effect_low': 'T3c mode engages at lower BG — more aggressive activation, PI controller handles milder elevations.',
        'impact': 'high',
    },
    'key_aimi_t3c_anticipation_strength': {
        'effect_high': 'Stronger anticipation — T3c PI controller looks further ahead on the projected BG curve, applying TBR preventively (reactive-anticipatory hybrid).',
        'effect_low': 'More reactive — T3c controller responds mainly to current BG rather than projected trajectory.',
        'impact': 'high',
    },

    # ═══ NGR ═══
    'key_aimi_ngr_max_iob_extra': {
        'effect_high': 'More IOB headroom during Night Growth Resistance — allows more aggressive overnight insulin to counter pediatric growth hormone spikes.',
        'effect_low': 'Less extra IOB during NGR — tighter IOB cap overnight.',
        'impact': 'critical',
    },
    'key_aimi_ngr_max_smb_clamp': {
        'effect_high': 'Higher SMB cap during NGR — allows larger individual boluses to counter nocturnal growth-hormone-driven BG rises.',
        'effect_low': 'Lower SMB cap during NGR — restricts bolus size overnight.',
        'impact': 'critical',
    },

    # ═══ EMERGENCY SOS ═══
    'aimi_emergency_sos_enable': {
        'effect_high': 'Emergency SOS active — triggers alerts and notifications when BG data is stale or severe hypo persists beyond configured thresholds.',
        'effect_low': 'Emergency SOS disabled — no automated emergency alerts.',
        'impact': 'critical',
    },
    'aimi_emergency_sos_immediate_threshold': {
        'effect_high': 'Longer wait before immediate SOS — more tolerance for data gaps before triggering emergency alert.',
        'effect_low': 'Shorter wait — immediate SOS triggers sooner after data loss.',
        'impact': 'critical',
    },
    'aimi_emergency_sos_stale_threshold': {
        'effect_high': 'Longer tolerance for stale data — warning is delayed, more CGM gaps are tolerated.',
        'effect_low': 'Stale data warning triggers faster — more sensitive to CGM interruptions.',
        'impact': 'medium',
    },

    # ═══ ADVISOR / API KEYS ═══
    'aimi_advisor_claude_key': {
        'effect_high': 'Uses the configured Anthropic Claude API key — enables Claude as the LLM backend for advisor clinical analysis.',
        'effect_low': 'Empty or invalid key — Claude-based advisor features are unavailable.',
        'impact': 'medium',
    },
    'aimi_advisor_deepseek_key': {
        'effect_high': 'Uses the configured DeepSeek API key — enables DeepSeek as the LLM backend for advisor analysis.',
        'effect_low': 'Empty or invalid key — DeepSeek-based advisor features are unavailable.',
        'impact': 'medium',
    },
    'aimi_advisor_gemini_key': {
        'effect_high': 'Uses the configured Google Gemini API key — enables Gemini as the LLM backend for advisor analysis.',
        'effect_low': 'Empty or invalid key — Gemini-based advisor features are unavailable.',
        'impact': 'medium',
    },
    'aimi_advisor_openai_key': {
        'effect_high': 'Uses the configured OpenAI API key — enables GPT models as the LLM backend for advisor analysis.',
        'effect_low': 'Empty or invalid key — OpenAI-based advisor features are unavailable.',
        'impact': 'medium',
    },
    'aimi_advisor_provider': {
        'effect_high': 'Selects which LLM provider backend is used for the advisor — chooses between Claude, DeepSeek, Gemini, or OpenAI for clinical analysis.',
        'effect_low': 'Different provider selection — switches the AI model used for advisor recommendations.',
        'impact': 'medium',
    },

    # ═══ AUDITOR ═══
    'aimi_auditor_max_per_hour': {
        'effect_high': 'Allows more auditor checks per hour — more frequent algorithm oversight but higher computational cost.',
        'effect_low': 'Fewer checks per hour — less oversight overhead but may miss transient decision anomalies.',
        'impact': 'medium',
    },
    'aimi_auditor_min_confidence': {
        'effect_high': 'Higher confidence threshold — auditor only flags decisions when the algorithm is very uncertain (fewer false positives).',
        'effect_low': 'Lower threshold — auditor flags more decisions for review, catching subtle anomalies but with more noise.',
        'impact': 'medium',
    },
    'aimi_auditor_mode': {
        'effect_high': 'Different operational mode — changes how the auditor evaluates algorithm decisions (e.g., strict vs permissive, shadow vs authoritative).',
        'effect_low': 'Different mode — affects which decision types are audited and what actions are taken on findings.',
        'impact': 'medium',
    },
    'aimi_auditor_timeout_seconds': {
        'effect_high': 'Longer timeout for auditor checks — allows more time for deep analysis but may delay loop completion.',
        'effect_low': 'Shorter timeout — auditor checks must complete faster, potentially with shallower analysis.',
        'impact': 'medium',
    },

    # ═══ PKPD BOUNDS / INITIAL / STATE ═══
    'aimi_pkpd_bounds_dia_min_h': {
        'effect_high': 'Higher minimum DIA bound — PKPD learner cannot converge below this value (prevents unrealistically short insulin action).',
        'effect_low': 'Lower minimum DIA — allows PKPD to learn shorter DIA values (suitable for users with genuinely fast insulin clearance).',
        'impact': 'high',
    },
    'aimi_pkpd_bounds_dia_max_h': {
        'effect_high': 'Higher maximum DIA bound — PKPD learner can converge to longer DIA values (suitable for users with slow insulin clearance).',
        'effect_low': 'Lower maximum DIA — constrains learned DIA to shorter maximum (prevents unrealistically long insulin action).',
        'impact': 'high',
    },
    'aimi_pkpd_bounds_peak_min_min': {
        'effect_high': 'Higher minimum peak bound — PKPD cannot learn a peak earlier than this (prevents unrealistically fast absorption).',
        'effect_low': 'Lower minimum peak — allows PKPD to learn earlier peak times (suitable for fast-absorbing insulins).',
        'impact': 'high',
    },
    'aimi_pkpd_bounds_peak_min_max': {
        'effect_high': 'Higher maximum peak bound — PKPD can learn later peak times (suitable for slow-absorbing insulins or site issues).',
        'effect_low': 'Lower maximum peak — constrains learned peak to an earlier maximum.',
        'impact': 'high',
    },
    'aimi_pkpd_initial_dia_h': {
        'effect_high': 'Starts PKPD learner with a longer DIA estimate — more conservative initial IOB calculations until learning converges.',
        'effect_low': 'Starts PKPD with a shorter DIA — more aggressive initial IOB model until learning adapts.',
        'impact': 'high',
    },
    'aimi_pkpd_initial_peak_min': {
        'effect_high': 'Starts PKPD learner with a later peak time — insulin activity curve is shifted later initially.',
        'effect_low': 'Starts PKPD with an earlier peak — insulin activity peaks sooner in initial model.',
        'impact': 'high',
    },
    'aimi_pkpd_max_dia_change_per_day_h': {
        'effect_high': 'Allows larger DIA adjustments per day — PKPD learner adapts faster but may oscillate between values.',
        'effect_low': 'Smaller daily DIA change — PKPD adapts more gradually (smoother convergence, less responsive to acute changes).',
        'impact': 'high',
    },
    'aimi_pkpd_max_peak_change_per_day_min': {
        'effect_high': 'Allows larger peak time adjustments per day — PKPD learner adapts peak faster but may be less stable.',
        'effect_low': 'Smaller daily peak change — peak timing adapts more gradually and smoothly.',
        'impact': 'high',
    },
    'aimi_pkpd_state_dia_h': {
        'effect_high': 'Stored learned DIA value — the PKPD learner has converged to a longer DIA (slower insulin clearance profile).',
        'effect_low': 'Stored learned DIA value — the PKPD learner has converged to a shorter DIA (faster insulin clearance).',
        'impact': 'medium',
    },
    'aimi_pkpd_state_peak_min': {
        'effect_high': 'Stored learned peak time — PKPD has converged to a later insulin peak (delayed absorption profile).',
        'effect_low': 'Stored learned peak time — PKPD has converged to an earlier insulin peak (faster absorption).',
        'impact': 'medium',
    },
    'aimi_pkpd_state_prior_peak': {
        'effect_high': 'Stored prior peak from TapPeakGovernor — previous tick\'s effective peak was later in the insulin window.',
        'effect_low': 'Stored prior peak — previous tick\'s effective peak was earlier in the insulin window.',
        'impact': 'medium',
    },
    'aimi_pkpd_state_physio_peak': {
        'effect_high': 'Physiological peak shift is positive — physio factors (activity, HRV, sleep) are shifting peak later.',
        'effect_low': 'Physiological peak shift is negative — physio factors are shifting peak earlier.',
        'impact': 'medium',
    },
    'aimi_pkpd_state_site_peak': {
        'effect_high': 'Site-related peak shift is positive — infusion site age is delaying insulin absorption.',
        'effect_low': 'Site-related peak shift is negative — site factors are accelerating absorption.',
        'impact': 'medium',
    },
    'aimi_pkpd_state_traj_peak': {
        'effect_high': 'Trajectory-based peak nudge is positive — trajectory analysis suggests later peak timing for safety.',
        'effect_low': 'Trajectory-based peak nudge is negative — trajectory suggests earlier peak is safe.',
        'impact': 'medium',
    },
    'aimi_pkpd_state_effective_peak': {
        'effect_high': 'Overall effective peak is later — combined PKPD + physio + site + trajectory result in delayed insulin activity.',
        'effect_low': 'Overall effective peak is earlier — combined factors produce faster insulin activity onset.',
        'impact': 'medium',
    },

    # ═══ PREBOLUS WINDOWS (14 params) ═══
    'key_prebolus_BF_mode': {
        'effect_high': 'Longer prebolus window for breakfast — more time between bolus and meal for insulin to start working.',
        'effect_low': 'Shorter prebolus window — less time between bolus and eating (higher postprandial spike risk).',
        'impact': 'high',
    },
    'key_prebolus_lunch_mode': {
        'effect_high': 'Longer prebolus window for lunch — more lead time for insulin action before eating.',
        'effect_low': 'Shorter prebolus window — less insulin lead time at lunch.',
        'impact': 'high',
    },
    'key_prebolus_dinner_mode': {
        'effect_high': 'Longer prebolus window for dinner — more lead time for insulin action before evening meal.',
        'effect_low': 'Shorter prebolus window — less insulin lead time at dinner.',
        'impact': 'high',
    },
    'key_prebolus_meal_mode': {
        'effect_high': 'Longer prebolus window for general meals — default lead time for insulin before any meal.',
        'effect_low': 'Shorter prebolus window — less default lead time.',
        'impact': 'high',
    },
    'key_prebolus_snack_mode': {
        'effect_high': 'Longer prebolus window for snacks — more insulin lead time before small eating events.',
        'effect_low': 'Shorter prebolus window for snacks — less lead time for small meals.',
        'impact': 'medium',
    },
    'key_prebolus_highcarb_mode': {
        'effect_high': 'Longer prebolus window for high-carb meals — more lead time to handle larger glucose loads.',
        'effect_low': 'Shorter prebolus window for high-carb — less lead time, higher postprandial excursion risk.',
        'impact': 'high',
    },
    'key_prebolus_highcarb_mode2': {
        'effect_high': 'Longer secondary prebolus window for high-carb — extended or alternative lead time for very large meals.',
        'effect_low': 'Shorter secondary prebolus window — less lead time for extended high-carb scenarios.',
        'impact': 'medium',
    },
    'key_prebolus_autodrive_mode': {
        'effect_high': 'Longer prebolus window when Autodrive is active — more lead time under MPC control.',
        'effect_low': 'Shorter prebolus window during Autodrive — faster insulin-to-meal timing under MPC.',
        'impact': 'high',
    },
    'key_prebolussmall_autodrive_mode': {
        'effect_high': 'Longer small prebolus window during Autodrive — extra lead time for micro-boluses under MPC.',
        'effect_low': 'Shorter small prebolus window — tighter timing for micro-boluses under Autodrive.',
        'impact': 'medium',
    },
    'key_prebolus2_BF_mode': {
        'effect_high': 'Secondary breakfast prebolus window — alternative or supplementary lead time for breakfast.',
        'effect_low': 'Shorter secondary breakfast window — less supplementary lead time.',
        'impact': 'high',
    },
    'key_prebolus2_lunch_mode': {
        'effect_high': 'Secondary lunch prebolus window — alternative lead time for lunch.',
        'effect_low': 'Shorter secondary lunch window.',
        'impact': 'high',
    },
    'key_prebolus2_dinner_mode': {
        'effect_high': 'Secondary dinner prebolus window — alternative lead time for dinner.',
        'effect_low': 'Shorter secondary dinner window.',
        'impact': 'high',
    },

    # ═══ MEAL / DIET FACTORS ═══
    'key_oaps_aimi_meal_factor': {
        'effect_high': 'Stronger insulin multiplier during meals — delivers more insulin per gram of carb (aggressive meal coverage).',
        'effect_low': 'Weaker meal multiplier — less insulin per carb gram (conservative, suitable for hypo-prone users).',
        'impact': 'high',
    },
    'key_oaps_aimi_meal_interval': {
        'effect_high': 'Longer meal interval — meal-mode insulin adjustments persist longer after eating.',
        'effect_low': 'Shorter meal interval — post-meal insulin adjustments return to baseline faster.',
        'impact': 'high',
    },
    'key_oaps_aimi_BF_factor': {
        'effect_high': 'Stronger breakfast multiplier — delivers more insulin at breakfast (counters dawn phenomenon).',
        'effect_low': 'Weaker breakfast multiplier — less insulin at breakfast (safer for morning hypo-prone users).',
        'impact': 'medium',
    },
    'key_oaps_aimi_BF_interval': {
        'effect_high': 'Longer breakfast interval — breakfast-mode adjustments persist longer.',
        'effect_low': 'Shorter breakfast interval — returns to baseline faster after breakfast.',
        'impact': 'medium',
    },
    'key_oaps_aimi_lunch_factor': {
        'effect_high': 'Stronger lunch multiplier — more insulin at midday meals.',
        'effect_low': 'Weaker lunch multiplier — less aggressive lunch coverage.',
        'impact': 'medium',
    },
    'key_oaps_aimi_lunch_interval': {
        'effect_high': 'Longer lunch interval — lunch-mode adjustments persist longer.',
        'effect_low': 'Shorter lunch interval — faster return to baseline after lunch.',
        'impact': 'medium',
    },
    'key_oaps_aimi_dinner_factor': {
        'effect_high': 'Stronger dinner multiplier — more insulin at evening meals.',
        'effect_low': 'Weaker dinner multiplier — more conservative dinner coverage.',
        'impact': 'medium',
    },
    'key_oaps_aimi_dinner_interval': {
        'effect_high': 'Longer dinner interval — dinner-mode adjustments persist longer into the night.',
        'effect_low': 'Shorter dinner interval — faster return to baseline after dinner.',
        'impact': 'medium',
    },
    'key_oaps_aimi_HC_factor': {
        'effect_high': 'Stronger high-carb multiplier — delivers proportionally more insulin for carb-heavy meals.',
        'effect_low': 'Weaker high-carb multiplier — less aggressive coverage for large carb loads.',
        'impact': 'medium',
    },
    'key_oaps_aimi_HC_interval': {
        'effect_high': 'Longer high-carb interval — high-carb insulin adjustments persist longer.',
        'effect_low': 'Shorter high-carb interval — faster normalization after high-carb meals.',
        'impact': 'medium',
    },
    'key_oaps_aimi_snack_factor': {
        'effect_high': 'Stronger snack multiplier — more insulin for small between-meal eating events.',
        'effect_low': 'Weaker snack multiplier — less aggressive snack coverage.',
        'impact': 'medium',
    },
    'key_oaps_aimi_snack_interval': {
        'effect_high': 'Longer snack interval — snack-mode adjustments persist longer.',
        'effect_low': 'Shorter snack interval — faster return to baseline after snacks.',
        'impact': 'medium',
    },
    'key_oaps_aimi_highBG_interval': {
        'effect_high': 'Longer high-BG interval — high-BG detection mode persists longer (extended aggressive response to highs).',
        'effect_low': 'Shorter high-BG interval — aggressive high-BG response expires faster.',
        'impact': 'medium',
    },
    'key_oaps_aimi_sleep_factor': {
        'effect_high': 'Stronger sleep multiplier — more insulin adjustment during detected sleep (higher basal/SMB during sleep if rising).',
        'effect_low': 'Weaker sleep multiplier — less adjustment during sleep (conservative overnight).',
        'impact': 'medium',
    },
    'key_oaps_aimi_sleep_interval': {
        'effect_high': 'Longer sleep interval — sleep-mode adjustments persist longer after waking.',
        'effect_low': 'Shorter sleep interval — faster return to daytime mode after sleep ends.',
        'impact': 'medium',
    },

    # ═══ NGR ADDITIONAL ═══
    'key_aimi_ngr_min_duration': {
        'effect_high': 'Requires longer sustained elevation before NGR activates — more conservative detection of growth-hormone patterns.',
        'effect_low': 'Shorter minimum duration — NGR activates faster for brief nocturnal rises.',
        'impact': 'high',
    },
    'key_aimi_ngr_min_eventual_over_target': {
        'effect_high': 'Requires higher eventual BG above target before NGR triggers — NGR only activates for significant projected hyperglycemia.',
        'effect_low': 'Lower threshold — NGR activates for milder projections above target.',
        'impact': 'high',
    },
    'key_aimi_ngr_min_rise_slope': {
        'effect_high': 'Requires steeper BG rise before NGR activates — only detects pronounced growth-hormone-driven spikes.',
        'effect_low': 'Detects gentler rises — NGR may activate for mild nocturnal upward trends.',
        'impact': 'high',
    },
    'key_aimi_ngr_decay_minutes': {
        'effect_high': 'Longer decay — NGR multiplier persists longer after the growth hormone spike resolves.',
        'effect_low': 'Faster decay — NGR effect fades more quickly once the nocturnal rise subsides.',
        'impact': 'medium',
    },
    'key_aimi_ngr_age_years': {
        'effect_high': 'Older patient — NGR sensitivity and thresholds are adjusted for age-appropriate growth hormone patterns (relevant for pediatric patients).',
        'effect_low': 'Younger patient — NGR thresholds are calibrated for child-typical overnight patterns.',
        'impact': 'medium',
    },
    'key_aimi_ngr_night_start': {
        'effect_high': 'NGR detection window starts later — growth hormone monitoring begins later in the evening.',
        'effect_low': 'NGR detection starts earlier — monitoring begins sooner after bedtime.',
        'impact': 'medium',
    },
    'key_aimi_ngr_night_end': {
        'effect_high': 'NGR detection window ends later — growth hormone monitoring continues later into the morning.',
        'effect_low': 'NGR detection ends earlier — monitoring stops sooner after dawn.',
        'impact': 'medium',
    },
    'key_aimi_ngr_max_iob_extra': {
        'effect_high': 'Larger IOB headroom during NGR — allows more aggressive overnight dosing for growth-hormone-driven rises.',
        'effect_low': 'Less extra IOB — tighter IOB safety cap during NGR periods.',
        'impact': 'critical',
    },

    # ═══ WOMEN'S CYCLE ═══
    'key_use_Aimi_wcycle_require_confirm': {
        'effect_high': 'Requires user confirmation before applying cycle-derived ISF/basal adjustments — manual gate for safety.',
        'effect_low': 'Cycle adjustments apply automatically — no manual confirmation step needed.',
        'impact': 'low',
    },
    'key_use_Aimi_wcycle_shadow': {
        'effect_high': 'Shadow mode — cycle adjustments are computed and logged but do not affect dosing.',
        'effect_low': 'Live mode — cycle adjustments are applied to insulin delivery.',
        'impact': 'low',
    },
    'key_oaps_aimi_wcycle_contraceptive': {
        'effect_high': 'Uses contraceptive data to adjust cycle phase detection — accounts for hormonal contraception effects on insulin sensitivity.',
        'effect_low': 'Contraceptive data not used — cycle detection relies only on tracked menstrual data.',
        'impact': 'low',
    },
    'key_oaps_aimi_wcycle_thyroid': {
        'effect_high': 'Integrates thyroid status into cycle adjustments — cycle ISF modulation accounts for thyroid treatment phase.',
        'effect_low': 'Thyroid status is not factored into cycle adjustments.',
        'impact': 'low',
    },
    'key_oaps_aimi_wcycle_tracking_mode': {
        'effect_high': 'Different cycle tracking mode — changes how menstrual phase is determined (manual entry vs automatic detection vs hybrid).',
        'effect_low': 'Different tracking mode — affects which data sources drive cycle phase classification.',
        'impact': 'low',
    },
    'key_oaps_aimi_wcycle_verneuil': {
        'effect_high': 'Verneuil disease mode active — cycle adjustments account for Verneuil-related inflammation effects on insulin sensitivity.',
        'effect_low': 'Standard cycle adjustments without Verneuil disease considerations.',
        'impact': 'low',
    },
    'key_wcycle_avg_length': {
        'effect_high': 'Longer average cycle length — cycle phase timing is stretched (longer luteal/follicular phase windows).',
        'effect_low': 'Shorter average cycle length — cycle phases are compressed.',
        'impact': 'low',
    },
    'key_wcycle_clamp_max': {
        'effect_high': 'Higher cycle ISF clamp maximum — cycle ISF adjustments can go higher during sensitivity peaks.',
        'effect_low': 'Lower cycle ISF clamp — tighter ceiling on cycle-driven ISF increases.',
        'impact': 'low',
    },
    'key_wcycle_clamp_min': {
        'effect_high': 'Higher cycle ISF clamp minimum — prevents ISF from dropping too low during luteal insulin resistance (safety floor).',
        'effect_low': 'Lower cycle ISF clamp — allows ISF to drop further during insulin-resistant phases.',
        'impact': 'low',
    },
    'key_wcycledateday': {
        'effect_high': 'Sets the reference date for cycle day calculation — shifts the entire phase tracking timeline.',
        'effect_low': 'Different reference date — changes which cycle day the system considers current.',
        'impact': 'low',
    },

    # ═══ ENDO / FLARE ═══
    'aimi_endo_flare_duration': {
        'effect_high': 'Longer flare duration — endometriosis-related insulin adjustments persist for more days per flare.',
        'effect_low': 'Shorter flare duration — adjustments resolve faster after a flare episode.',
        'impact': 'medium',
    },

    # ═══ AUTODRIVE ═══
    'key_Acceleration_autodrive_mode': {
        'effect_high': 'Higher acceleration threshold — Autodrive requires faster BG acceleration before engaging (more cautious activation).',
        'effect_low': 'Lower acceleration threshold — Autodrive engages with less pronounced BG acceleration (more sensitive activation).',
        'impact': 'high',
    },
    'key_combinedDelta_autodrive_mode': {
        'effect_high': 'Higher combined delta threshold — Autodrive requires stronger multi-signal confirmation of rising BG before engaging.',
        'effect_low': 'Lower combined delta threshold — Autodrive activates with weaker multi-signal evidence.',
        'impact': 'high',
    },
    'key_mindeviation_autodrive_mode': {
        'effect_high': 'Higher minimum deviation — Autodrive requires larger positive deviation from predicted BG before engaging.',
        'effect_low': 'Lower minimum deviation — Autodrive engages with smaller deviations (more sensitive).',
        'impact': 'high',
    },
    'key_oaps_aimi_autodriveBG': {
        'effect_high': 'Higher BG required for Autodrive engagement — MPC controller only activates at higher starting BG.',
        'effect_low': 'Lower BG threshold — Autodrive can engage at more moderate hyperglycemia levels.',
        'impact': 'high',
    },
    'key_oaps_aimi_autodriveTarget': {
        'effect_high': 'Higher target BG for Autodrive — MPC aims for a higher setpoint (more conservative control).',
        'effect_low': 'Lower target BG — MPC aims for tighter control (more aggressive).',
        'impact': 'high',
    },

    # ═══ OPENAPS LEGACY/SAFETY ═══
    'openapsama_current_basal_safety_multiplier': {
        'effect_high': 'Higher basal safety multiplier — allows temporary TBRs to exceed current basal by a larger factor (more aggressive temporary basal).',
        'effect_low': 'Lower multiplier — tighter constraint on temporary basal increases (more conservative).',
        'impact': 'critical',
    },
    'openapsama_max_daily_safety_multiplier': {
        'effect_high': 'Higher daily safety multiplier — allows total daily basal to exceed profile by a larger factor (more headroom for hyper correction).',
        'effect_low': 'Lower daily multiplier — tighter daily basal cap (prevents excessive basal over 24h).',
        'impact': 'critical',
    },
    'openapsma_max_basal': {
        'effect_high': 'Higher max basal rate — allows pump to deliver higher temporary basal rates (U/h) for correcting highs.',
        'effect_low': 'Lower max basal — restricts maximum temporary basal rate (safer but slower correction).',
        'impact': 'critical',
    },
    'openapsma_max_iob': {
        'effect_high': 'Higher max IOB — allows more insulin on board before capping further delivery (permits more aggressive insulin stacking).',
        'effect_low': 'Lower max IOB — restricts total IOB (safer, prevents excessive stacking).',
        'impact': 'critical',
    },
    'openapsmb_max_iob': {
        'effect_high': 'Higher max IOB for SMB — allows more SMB insulin to accumulate before capping (more aggressive micro-bolus delivery).',
        'effect_low': 'Lower max IOB for SMB — restricts SMB accumulation (safer, prevents SMB stacking).',
        'impact': 'critical',
    },
    'openaps_smb_min_5m_carbimpact': {
        'effect_high': 'Higher minimum carb impact — SMB calculations assume larger BG drop per carb gram (more conservative SMB sizing).',
        'effect_low': 'Lower minimum carb impact — SMB calculations assume smaller carb effect (more aggressive SMB sizing).',
        'impact': 'medium',
    },
    'openapsama_min_5m_carbimpact': {
        'effect_high': 'Higher minimum carb impact for AMA — AMA assumes larger per-gram carb effect (more conservative correction).',
        'effect_low': 'Lower minimum carb impact — AMA assumes smaller carb effect (more aggressive correction).',
        'impact': 'medium',
    },
    'autosens_max': {
        'effect_high': 'Higher autosens ceiling — allows autosens to detect and apply larger sensitivity increases (more aggressive ISF reduction when sensitive).',
        'effect_low': 'Lower autosens ceiling — limits how much sensitivity can increase (prevents over-aggressive ISF reduction).',
        'impact': 'high',
    },
    'autoISF_max': {
        'effect_high': 'Higher AutoISF ceiling — allows AutoISF to raise ISF further (more conservative corrections during insulin-sensitive periods).',
        'effect_low': 'Lower AutoISF ceiling — constrains ISF increase from AutoISF (less conservative).',
        'impact': 'high',
    },
    'autoISF_min': {
        'effect_high': 'Higher AutoISF floor — prevents ISF from dropping too low via AutoISF (conservative safety floor).',
        'effect_low': 'Lower AutoISF floor — allows AutoISF to reduce ISF further (more aggressive corrections during resistance).',
        'impact': 'high',
    },
    'lgsThreshold': {
        'effect_high': 'Higher LGS (Low Glucose Suspend) threshold — pump suspends at a higher BG (more protective against hypos).',
        'effect_low': 'Lower LGS threshold — suspension triggers at lower BG (less protective, avoids unnecessary suspensions).',
        'impact': 'medium',
    },

    # ═══ MISC / INTERNAL ═══
    'AIMI_UAM_CONFIDENCE': {
        'effect_high': 'Stronger UAM (Unannounced Meal) confidence — algorithm treats BG rises as meals more aggressively (higher SMB/correction response to unexpected rises).',
        'effect_low': 'Weaker UAM confidence — algorithm is more skeptical that a BG rise is an unannounced meal (more conservative response to unexpected rises).',
        'impact': 'medium',
    },
    'OApsAIMIHighBg': {
        'effect_high': 'Higher internal high-BG threshold — AIMI classifies more BG readings as "high," triggering more aggressive correction logic.',
        'effect_low': 'Lower high-BG threshold — fewer readings are classified as high (less aggressive high-BG response).',
        'impact': 'medium',
    },
    'OApsAIMIMaxMultiplier': {
        'effect_high': 'Higher internal multiplier ceiling — AIMI can scale ISF/basal adjustments to a larger factor (more dynamic range).',
        'effect_low': 'Lower multiplier ceiling — adjustments are constrained to a smaller range (more stable, less adaptive).',
        'impact': 'medium',
    },
    'OApsAIMILastEstimatedCarbTime': {
        'effect_high': 'Longer lookback for estimated carb timing — considers older carb estimates as still relevant for current decisions.',
        'effect_low': 'Shorter lookback — older carb estimates expire faster (less influence from distant past).',
        'impact': 'medium',
    },
    'OApsAIMILastEstimatedCarbs': {
        'effect_high': 'Higher value for last estimated carbs — AIMI believes more unannounced carbs were consumed (stronger meal response).',
        'effect_low': 'Lower value for last estimated carbs — AIMI estimates fewer unannounced carbs (weaker meal response).',
        'impact': 'medium',
    },

    # ═══ CONTEXT / STORAGE ═══
    'aimi_context_mode': {
        'effect_high': 'Different context collection mode — changes what data is included in loop state snapshots for advisor/LLM analysis.',
        'effect_low': 'Different mode — affects snapshot detail level and which features can use context data.',
        'impact': 'medium',
    },
    'aimi_context_storage': {
        'effect_high': 'Different storage backend — changes where context snapshots are persisted (in-memory, file, database).',
        'effect_low': 'Different storage — affects persistence durability and performance.',
        'impact': 'medium',
    },
    'aimi_tuning_context_selection': {
        'effect_high': 'Different context selection strategy for tuning — changes which historical data is used for parameter auto-tuning.',
        'effect_low': 'Different strategy — affects which data windows and features inform automatic parameter adjustments.',
        'impact': 'medium',
    },

    # ═══ THYROID ═══
    'key_aimi_thyroid_guard_level': {
        'effect_high': 'Higher guard level — thyroid-related adjustments are more constrained by safety limits.',
        'effect_low': 'Lower guard level — thyroid adjustments have more freedom to modulate insulin.',
        'impact': 'medium',
    },
    'key_aimi_thyroid_manual_status': {
        'effect_high': 'Manually set thyroid status — overrides automatic detection for hyper/hypo/euthyroid classification.',
        'effect_low': 'Different manual status — changes which thyroid phase ISF adjustments are applied.',
        'impact': 'medium',
    },
    'key_aimi_thyroid_mode': {
        'effect_high': 'Different thyroid mode — changes how thyroid status modulates ISF (off, auto-detect, or manual).',
        'effect_low': 'Different mode — affects whether and how thyroid phase impacts insulin sensitivity.',
        'impact': 'medium',
    },
    'key_aimi_thyroid_treatment_phase': {
        'effect_high': 'Different treatment phase — ISF modulation is calibrated for active thyroid treatment (e.g., levothyroxine titration).',
        'effect_low': 'Different phase — changes the ISF adjustment curve for the specific treatment stage.',
        'impact': 'medium',
    },

    # ═══ EMERGENCY SOS ═══
    'aimi_emergency_sos_phone': {
        'effect_high': 'Sets the primary emergency contact phone number for SOS alerts — called when critical thresholds are exceeded.',
        'effect_low': 'No phone set — SOS phone call feature cannot activate.',
        'impact': 'critical',
    },
    'aimi_emergency_sos_phone2': {
        'effect_high': 'Sets the secondary/backup emergency contact phone number.',
        'effect_low': 'No secondary phone set — only primary contact is available for emergency calls.',
        'impact': 'medium',
    },
    'aimi_emergency_sos_threshold': {
        'effect_high': 'Longer standard SOS threshold — more time without BG data before standard SOS alert triggers.',
        'effect_low': 'Shorter threshold — standard SOS alert triggers sooner after data loss.',
        'impact': 'critical',
    },

    # ═══ HYPER / T3C / ACTIVITY ═══
    'key_aimi_hyper_deep_dev_mgdl': {
        'effect_high': 'Higher deep deviation threshold — trajectory must be further above target before deep hyper classification triggers more aggressive response.',
        'effect_low': 'Lower deep deviation threshold — deep hyper response triggers for milder elevations.',
        'impact': 'high',
    },
    'key_aimi_hyper_established_dev_mgdl': {
        'effect_high': 'Higher established deviation threshold — requires larger sustained deviation before classifying as established hyper (more conservative).',
        'effect_low': 'Lower established deviation threshold — established hyper is declared sooner (more aggressive correction).',
        'impact': 'high',
    },
    'key_aimi_activity_basal_cap_factor': {
        'effect_high': 'Higher basal cap during activity — allows more basal delivery despite detected physical activity (less suppression).',
        'effect_low': 'Lower basal cap — more aggressively reduces basal during activity to prevent hypos.',
        'impact': 'medium',
    },
    'key_aimi_intratick_stall_seconds': {
        'effect_high': 'Longer stall tolerance — more time allowed for slow operations within a tick before aborting.',
        'effect_low': 'Shorter stall tolerance — slow operations are aborted sooner (faster loop completion but may skip important work).',
        'impact': 'medium',
    },

    # ═══ T3C CONTINUED ═══
    'key_aimi_t3c_cfrd_cob_delay_min': {
        'effect_high': 'Longer COB delay for CFRD — carbs-on-board are considered active longer for cystic fibrosis-related diabetes patients.',
        'effect_low': 'Shorter COB delay — COB clears faster in T3c CFRD context.',
        'impact': 'medium',
    },
    'key_aimi_t3c_cfrd_lgs_floor': {
        'effect_high': 'Higher LGS floor in CFRD mode — low glucose suspend triggers at higher BG for additional safety.',
        'effect_low': 'Lower LGS floor in CFRD — less protective suspension threshold.',
        'impact': 'medium',
    },

    # ═══ PHYSIO ═══
    'aimi_physio_llm_provider': {
        'effect_high': 'Selects which LLM backend is used for Physio Assistant analysis of health data.',
        'effect_low': 'Different provider — changes the AI model used for physiological pattern analysis.',
        'impact': 'medium',
    },

    # ═══ UNDECLARED COB ═══
    'key_aimi_undeclared_cob_max_g': {
        'effect_high': 'Higher maximum grams for undeclared COB estimation — algorithm can assume larger unannounced meals.',
        'effect_low': 'Lower maximum — constrains undeclared COB estimates to smaller meals (conservative).',
        'impact': 'medium',
    },
}

def enrich_params():
    with open(PARAMS_FILE) as f:
        data = json.load(f)

    params = data['parameters']
    updated = 0

    for p in params:
        if p.get('orphaned'):
            continue

        key = p['key']
        effects = SPECIFIC_EFFECTS.get(key)

        if effects:
            has_specific = True
            old_eh = p.get('effect_high', '')
            old_el = p.get('effect_low', '')

            # Only update if currently generic or missing
            if 'Higher value' in (old_eh or '') or 'Lower value' in (old_el or '') or not old_eh:
                p['effect_high'] = effects.get('effect_high', p.get('effect_high', ''))
                p['effect_low'] = effects.get('effect_low', p.get('effect_low', ''))
                p['impact'] = effects.get('impact', p.get('impact', 'medium'))
                updated += 1
                print(f"  ✅ {key}")

    with open(PARAMS_FILE, 'w') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Updated {updated} parameters with specific effects")
    return data

def update_context(data):
    """Update aimi_context_for_ai.json with parameter knowledge."""
    with open(CONTEXT_FILE) as f:
        ctx = json.load(f)

    params = data['parameters']

    ctx['parameter_knowledge'] = [
        {
            'key': p['key'],
            'name': p.get('name', ''),
            'logic_summary': p.get('logic_summary', ''),
            'effect_high': p.get('effect_high', ''),
            'effect_low': p.get('effect_low', ''),
            'feature_group': p.get('feature_group', ''),
            'impact': p.get('impact', 'medium'),
            'settings_path': p.get('settings_path', ''),
            'negative_gate_key': p.get('negative_gate_key'),
        }
        for p in params
        if not p.get('orphaned') and p.get('logic_summary')
    ]

    with open(CONTEXT_FILE, 'w') as f:
        json.dump(ctx, f, indent=2, ensure_ascii=False)

    print(f"✅ Updated context file with {len(ctx['parameter_knowledge'])} parameter entries")

def validate():
    with open(PARAMS_FILE) as f:
        data = json.load(f)

    params = data['parameters']
    active = [p for p in params if not p.get('orphaned')]

    with_summary = [p for p in active if p.get('logic_summary')]
    with_effects = [p for p in active if p.get('effect_high') and 'Higher value' not in (p.get('effect_high') or '')]

    generic = [p for p in active if 'Higher value' in (p.get('effect_high') or '')]
    critical = [p for p in active if p.get('impact') == 'critical']
    critical_no_summary = [p for p in critical if not p.get('logic_summary')]
    critical_no_effects = [p for p in critical if not p.get('effect_high') or 'Higher value' in (p.get('effect_high') or '')]

    print(f"\n{'='*60}")
    print(f"VALIDATION RESULTS")
    print(f"{'='*60}")
    print(f"Active params:        {len(active)}")
    print(f"With summary:         {len(with_summary)} ({len(with_summary)*100//len(active)}%)")
    print(f"With specific effects:{len(with_effects)} ({len(with_effects)*100//len(active)}%)")
    print(f"Still generic:        {len(generic)}")
    print(f"Critical enriched:    {len(critical)-len(critical_no_effects)}/{len(critical)}")

    if critical_no_summary:
        print(f"  ❌ Critical missing summary: {[p['key'] for p in critical_no_summary]}")
    if critical_no_effects:
        print(f"  ❌ Critical missing effects: {[p['key'] for p in critical_no_effects]}")

if __name__ == '__main__':
    data = enrich_params()
    update_context(data)
    validate()
