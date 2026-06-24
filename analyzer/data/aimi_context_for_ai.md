# AIMI Algorithm Context
Version: 2.2
Source commit: 6f8b4fe4768b9ffb224bdad304c0c0e4fcc28c0d
Generated: 2026-06-24T21:22:48.641654+00:00

## Algorithm Overview
AIMI (AI-powered Modular Insulin)

The AIMI algorithm runs a 46-stage decision pipeline per 5-minute tick.

STAGES 1-8 (BOOTSTRAP + CONTEXT):
1. runEarlyDetermineBasalStages
2. bootstrapPhysiologyAfterEarlyTick
3. buildDecisionContextInitRtSosAndFlatShadow
4. runRealtimePhysioIobProfilerAndInsulinObserver
5. ensureWCycleAndLoadGlucoseStatusOrAbort
6. runT9PhysioEarlyPkpdAndTubeBootstrap
7. runCombinedDeltaByodaAndDynamicPeak
8. buildPreTherapyAutodriveByodaBootstrap

STAGES 9-13 (CLOCK + THERAPY GATE + MODES):
9. runTickClockMaxSmbTirCarbAndGlucoseCopy
10. runTherapyHydrateClocksAndExerciseLockoutGate — THERAPY GATE
11. runManualMealModesAfterTherapyGate
12. runT3cBrittleBypassOrReturn — T3C BYPASS
13. runSignalPreparationPkpdRuntimePhase

STAGES 14-19 (TRAJECTORY + PREDICTIONS + SAFETY):
14-19. Trajectory context, predictions, safety gates, meal advisor, hard brake, correction aggression

STAGES 20-27 (AUTODRIVE + POST-HYPO + BASAL BOOTSTRAP):
20-27. Autodrive V3/V2, post-hypo classification, drift terminator, basal schedule, IOB/TDD/carb limits, endo+activity adjustments

STAGES 28-33 (PKPD + UAM + SMB EXECUTION):
28-33. PKPD predictions, UAM model, SMB decision/execute, finalizeAndCapSMB, PKPD guard, snapshot

STAGES 34-39 (MEAL BOOST + SAFETY + NGR + IOB GATE):
34-39. Meal hyper basal boost, WCycle/ISF, carbs advisor safety, NGR headroom, MaxIOB gate, activity relax

STAGES 40-46 (BASAL ENGINE + LEARNERS + EXPORT):
40-46. Basal decision engine, learners, snapshot/JSONL export, final safety checks, basal floor, reason string, return RT

## Key Differences from oref1
- Multi-tier plateau/slope system (5 tiers) for SMB sizing
- Recursive Belief Engine (RBT) with 4 time scales
- PKPD model with Weibull curves and TAP Peak Shift
- Scenario Projection Engine (CLINICAL_FLOOR + SCENARIO_BEST)
- Trajectory Guard with phase-space analysis (5 classifications)
- Autodrive V3 as MPC-like controller
- UAM hypothesis competition for meal detection
- ApplyBasalFirstPolicy — disables SMB when BG<110 and fragile
- Unified Reactivity Learner — ML-powered time-based replacement
- T3c Brittle Mode with separate PI controller
- Night Growth Resistance monitor for paediatric patients
- StraightLineTubeAdvisor — MPC-lite SMB cap regulator
- Risk Envelope system — two immutable snapshots per tick
- Meal Absorption Phase state machine
- Physiological Phase Classification with BehavioralRiskPolicy
- Governance system — hypo detection, hold states, anticipation
- Adaptive Kernel Bank — cosine similarity gates
- Dynamic ISF trajectory tuning

## Features

### autodrive_v3
Gate: key_use_aimi_autodrive_active

MPC-like controller activated by the AutoDriveGater when BG>120 and rising or meal context. Gater checks: HR<140, step count, COB, UAM confidence. Engages in 5 states (DISENGAGED, ENGAGED, POST_HYPO_RECOVERY, SAFETY_HOLD, LEARNING). If engaged: builds AutoDriveState with physiological phase, HTR classification, and meal absorption phase. Computes optimal SMB via MPC with insulin cost, SMB fraction limits, and scenario projection. If authoritative: sets skipLegacySmbBlender=true, bypassing executeSmbInstruction entirely. V3 has priority lockout over V2.

Key parameters (14):
- aimi_mpc_insulin_u_per_kg_per_5min
- key_Acceleration_autodrive_mode
- key_aimi_autodrive_v3_authoritative
- key_aimi_hyper_trajectory_release
- key_aimi_recursive_belief_authority
- key_combinedDelta_autodrive_mode
- key_mindeviation_autodrive_mode
- key_oaps_aimi_autodriveBG
- key_oaps_aimi_autodriveTarget
- key_prebolus_autodrive_mode
- key_prebolussmall_autodrive_mode
- key_use_Aimi_autoDrive
- key_use_aimi_autodrive_active
- key_use_aimi_autodrive_v3_enhanced_gater

### legacy_meal_modes
Gate: None

P1/P2 prebolus windows with persistent lockout (30 min delivery TTL). P1 checks hypo credibility, P2 checks short-term dominance in the Recursive Belief Resolver. Enables pump-independent retries on BLE failures. Three-layer safety net.

Key parameters (14):
- aimi_last_legacy_prebolus_time
- key_prebolus2_BF_mode
- key_prebolus2_dinner_mode
- key_prebolus2_lunch_mode
- key_prebolus_BF_mode
- key_prebolus_autodrive_mode
- key_prebolus_dinner_mode
- key_prebolus_highcarb_mode
- key_prebolus_highcarb_mode2
- key_prebolus_lunch_mode
- key_prebolus_meal_mode
- key_prebolus_snack_mode
- key_prebolussmall_autodrive_mode
- oa_aimi_last_prebolus_time_ms

### t3c_brittle_mode
Gate: key_aimi_t3c_brittle_mode

Special mode for pancreatogenic type-3c diabetics. Bypasses standard AIMI algorithm and uses a dedicated PI controller with parabolic projection and resistance factor. Only activates at BG >= thresholds. Triple-layer prebolus protection. NO SMB delivery — TBR only via PI controller.

Key parameters (8):
- key_aimi_adaptive_basal_max_scaling
- key_aimi_t3c_activation_threshold
- key_aimi_t3c_aggressiveness
- key_aimi_t3c_anticipation_strength
- key_aimi_t3c_brittle_mode
- key_aimi_t3c_cfrd_cob_delay_min
- key_aimi_t3c_cfrd_lgs_floor
- key_use_aimi_t3c_adaptive_basal

### ngr
Gate: key_oaps_aimi_ngr_enabled

Nocturnal growth resistance detection for paediatric patients (< 18 years). State machine (INACTIVE → SUSPECTED → CONFIRMED → DECAY) monitors delta, short/longAvgDelta and eventualBG in night window. Assigns SMB and basal multipliers plus extra IOB headroom.

Key parameters (12):
- key_oaps_aimi_ngr_age_years
- key_oaps_aimi_ngr_basal_multiplier
- key_oaps_aimi_ngr_decay_minutes
- key_oaps_aimi_ngr_enabled
- key_oaps_aimi_ngr_max_iob_extra
- key_oaps_aimi_ngr_max_smb_clamp
- key_oaps_aimi_ngr_min_duration
- key_oaps_aimi_ngr_min_eventual_over_target
- key_oaps_aimi_ngr_min_rise_slope
- key_oaps_aimi_ngr_night_end
- key_oaps_aimi_ngr_night_start
- key_oaps_aimi_ngr_smb_multiplier

### pkpd
Gate: key_aimi_pkpd_enabled

Real-time PKPD model with Weibull-based insulin action curves, adaptive estimator and InsulinActionProfiler. Computes fusedIsf from profile ISF, dynamic ISF and learned values. PkpdAbsorptionGuard dampens SMB during limited absorption. IOB consensus reconciles AAPS IOB with PKPD IOB.

Key parameters (21):
- aimi_pkpd_anchor_dia_h
- aimi_pkpd_anchor_peak_min
- aimi_pkpd_bounds_dia_max_h
- aimi_pkpd_bounds_dia_min_h
- aimi_pkpd_bounds_peak_min_max
- aimi_pkpd_bounds_peak_min_min
- aimi_pkpd_initial_dia_h
- aimi_pkpd_initial_peak_min
- aimi_pkpd_max_dia_change_per_day_h
- aimi_pkpd_max_peak_change_per_day_min
- aimi_pkpd_pragmatic_relief_min_factor
- aimi_pkpd_state_dia_h
- aimi_pkpd_state_effective_peak
- aimi_pkpd_state_peak_min
- aimi_pkpd_state_physio_peak
- ... and 6 more

### unified_reactivity
Gate: key_use_Aimi_UnifiedReactivityLearner

ML-powered learner replacing old time-based bucket system. Analyses TIR 70-180, CV%, hypo count and oscillations. Combines globalFactor (24h, 60% weight) with shortTermFactor (2h, 40%) into total factor (0.5-1.5).

### iob_surveillance
Gate: key_aimi_iob_surveillance_guard

Monitors IOB utilisation and prevents insulin stacking. Red Carpet mechanism restores SMB doses reduced by safety mechanisms. capSmbDose() limits SMB based on IOB headroom.

Key parameters (4):
- aimi_priority_max_iob_extra_u
- aimi_priority_max_iob_factor
- aimi_red_carpet_restore_threshold
- key_aimi_iob_surveillance_guard

### trajectory_guard
Gate: key_aimi_trajectory_guard_enabled

Analyses insulin-glucose dynamics as geometric trajectory in phase space. Generates modulation factors: OPEN_DIVERGING→aggressive, CLOSING_CONVERGING→less, TIGHT_SPIRAL→strong damping, STABLE_ORBIT→minimal intervention.

Key parameters (2):
- key_aimi_straight_line_tube_enabled
- key_aimi_trajectory_guard_enabled

### basal_first_policy
Gate: None

Disables SMB completely and uses TBR only when BG < 110 and either isLearnerPrudent or isFragileBg. Not active during confirmedHighRise or BG >= 110.

### recursive_belief
Gate: key_aimi_recursive_belief_shadow

Multi-scale recursive belief tree (RBT) with MR-7 clause evaluation at 4 time scales (15/60/180/480 min). 12 active belief leaves. Resolves through credibility cascade and tension analysis.

Key parameters (3):
- key_aimi_recursive_belief_authority
- key_aimi_recursive_belief_shadow
- key_aimi_recursive_belief_wavelet

### scenario_projection
Gate: None

Produces two authoritative prediction curves per tick: CLINICAL_FLOOR (pessimistic) and SCENARIO_BEST (realistic, fuses 7 layers). Maps to RT.predBGs.

### risk_envelope
Gate: None

Two immutable risk snapshots per tick (EARLY and DECISION). IOB consensus resolves PKPD vs AAPS IOB. Composite min BG computes overall floor.

### meal_absorption_phase
Gate: None

Single cross-tick state machine (NONE→FIRST_WAVE→PEAK_CORRECTION→INTER_WAVE→SECOND_WAVE→LATE_FAT) unifying meal absorption context with phase-aware IOB surveillance bypass and HTR modulation.

### physiological_phase
Gate: aimi_physio_assistant_enable

Classifies current physiological phase from BG, delta, HR, steps, WCycle phase, HTR tier. Each phase carries a BehavioralRiskPolicy with maxHtrTier, smbFloorCapU, mpcInsulinCostMultiplier, mpcMaxSmbFraction.

Key parameters (6):
- aimi_physio_assistant_enable
- aimi_physio_debug_logs
- aimi_physio_hrv_enable
- aimi_physio_llm_enable
- aimi_physio_llm_provider
- aimi_physio_sleep_enable

### governance
Gate: key_use_aimi_t3c_adaptive_basal

Adaptive basal governance system for T3c and universal adaptive basal. Manages hypo detection (enter/exit rates, BG thresholds), hold states (basal/agg floor rates and decay rates for standard and severe hypo), and anticipation logic with lookback windows and margin parameters.

Key parameters (18):
- key_aimi_gov_anticipation_decay_blend_max
- key_aimi_gov_anticipation_hypo_damp
- key_aimi_gov_anticipation_lookback_samples
- key_aimi_gov_anticipation_margin_mgdl
- key_aimi_gov_hold_agg_decay_rate
- key_aimi_gov_hold_agg_decay_severe
- key_aimi_gov_hold_agg_floor_rate
- key_aimi_gov_hold_agg_floor_severe
- key_aimi_gov_hold_basal_decay_rate
- key_aimi_gov_hold_basal_decay_severe
- key_aimi_gov_hold_basal_floor_rate
- key_aimi_gov_hold_basal_floor_severe
- key_aimi_gov_hypo_bg_mgdl
- key_aimi_gov_hypo_rate_enter
- key_aimi_gov_hypo_rate_exit
- ... and 3 more

### tube_advisor
Gate: key_aimi_straight_line_tube_enabled

MPC-lite SMB cap regulator computing a PKPD tube that limits SMBs falling outside safe bounds. Controls hypo floor, hyper band, aggressiveness, basal trim max, and kappa safety margin.

Key parameters (6):
- key_aimi_straight_line_tube_enabled
- key_aimi_tube_aggressiveness
- key_aimi_tube_basal_trim_max
- key_aimi_tube_hyper_band_mgdl
- key_aimi_tube_hypo_floor_mgdl
- key_aimi_tube_kappa_margin

### endometriosis
Gate: aimi_endo_enable

Endometriosis & cycle management. Adjusts basal multiplier and SMB dampening during flare phases. Configurable flare duration and suppression window.

Key parameters (6):
- aimi_endo_basal_mult
- aimi_endo_enable
- aimi_endo_flare
- aimi_endo_flare_duration
- aimi_endo_smb_dampen
- aimi_endo_suppression

### cosine_gate
Gate: aimi_cosine_gate_enabled

Adaptive Kernel Bank using cosine similarity gates to modulate sensitivity based on data quality and physiological context. Alpha controls gate sharpness, min/max sensitivity define bounds.

Key parameters (6):
- aimi_cosine_gate_alpha
- aimi_cosine_gate_enabled
- aimi_cosine_gate_max_sens
- aimi_cosine_gate_max_shift
- aimi_cosine_gate_min_dq
- aimi_cosine_gate_min_sens

### dyn_isf_trajectory
Gate: aimi_dyn_isf_trajectory_tuning_enabled

Dynamic ISF trajectory tuning that adjusts ISF based on trajectory phase. Max fraction controls how much ISF can be modified per qualifying tick.

Key parameters (3):
- aimi_dyn_isf_trajectory_max_fraction
- aimi_dyn_isf_trajectory_shadow_only
- aimi_dyn_isf_trajectory_tuning_enabled

### peak_governor
Gate: key_aimi_peak_governor_enabled

Peak Governor learns and adjusts insulin peak timing. Learned weight blends observed peak data with prior estimates.

Key parameters (2):
- aimi_peak_governor_learned_weight
- key_aimi_peak_governor_enabled

### ism_fusion
Gate: None

ISF Fusion blends profile ISF, TDD ISF and PKPD scale into unified ISF with configurable min/max bounds and per-tick change limits.

Key parameters (3):
- aimi_isf_fusion_max_change_per_tick
- aimi_isf_fusion_max_factor
- aimi_isf_fusion_min_factor

### tpo
Gate: key_aimi_tpo_enabled

Transient Preference Overlay (TPO) system. Allows temporary parameter overrides with optional LLM confirmation and notification on apply.

Key parameters (3):
- key_aimi_tpo_enabled
- key_aimi_tpo_llm_confirm_enabled
- key_aimi_tpo_notify_on_apply

### thyroid
Gate: key_aimi_thyroid_enabled

Thyroid module for hormone-aware insulin sensitivity adjustments. Debug mode enables additional logging.

Key parameters (6):
- key_aimi_thyroid_debug
- key_aimi_thyroid_enabled
- key_aimi_thyroid_guard_level
- key_aimi_thyroid_manual_status
- key_aimi_thyroid_mode
- key_aimi_thyroid_treatment_phase

### sos_emergency
Gate: aimi_emergency_sos_enable

Emergency SOS detection for critically stale or dangerous glucose conditions. Configurable thresholds for immediate, stale, and standard alerts.

Key parameters (6):
- aimi_emergency_sos_enable
- aimi_emergency_sos_immediate_threshold
- aimi_emergency_sos_phone
- aimi_emergency_sos_phone2
- aimi_emergency_sos_stale_threshold
- aimi_emergency_sos_threshold

### auditor
Gate: aimi_auditor_enabled

Auditor module monitors algorithm decisions and provides confidence scoring. Configurable max checks per hour, min confidence threshold, and timeout.

Key parameters (5):
- aimi_auditor_enabled
- aimi_auditor_max_per_hour
- aimi_auditor_min_confidence
- aimi_auditor_mode
- aimi_auditor_timeout_seconds

### context_llm
Gate: key_aimi_context_enabled

Context-aware LLM integration for enriched decision making.

Key parameters (10):
- aimi_context_llm_claude_key
- aimi_context_llm_deepseek_key
- aimi_context_llm_gemini_key
- aimi_context_llm_openai_key
- aimi_context_llm_provider
- aimi_context_mode
- aimi_context_storage
- aimi_tuning_context_selection
- key_aimi_context_enabled
- key_aimi_context_llm_enabled

### loop_config
Gate: None

Loop execution configuration: exclusive invocation mode, blackbox file logging, intratick stall detection.

Key parameters (3):
- key_aimi_intratick_stall_seconds
- key_aimi_loop_blackbox_file_enabled
- key_aimi_loop_exclusive_invocation

### adaptive_basal
Gate: key_use_aimi_t3c_adaptive_basal

AIMI adaptive basal system with plateau detection, kicker mechanism, anti-stall bias, and zero-resume logic.

Key parameters (20):
- OApsAIMIAntiStallBias
- OApsAIMIAntiStallBias_NUMERIC
- OApsAIMIDeltaPosRelease_NUMERIC
- OApsAIMIKickerMaxMin
- OApsAIMIKickerMinUph
- OApsAIMIKickerMinUph_NUMERIC
- OApsAIMIKickerStartMin
- OApsAIMIKickerStep
- OApsAIMIKickerStep_NUMERIC
- OApsAIMIMaxMultiplier
- OApsAIMIMaxMultiplier_NUMERIC
- OApsAIMIPlateauBandAbs
- OApsAIMIPlateauBandAbs_NUMERIC
- OApsAIMIR2Confident
- OApsAIMIR2Confident_NUMERIC
- ... and 5 more

### hyper_trajectory
Gate: key_aimi_hyper_trajectory_release

Hyper trajectory release system for managing established and deep hyperglycemia. Controls when to release insulin constraints based on deviation magnitude.

Key parameters (4):
- key_aimi_hyper_deep_dev_mgdl
- key_aimi_hyper_established_dev_mgdl
- key_aimi_hyper_trajectory_release
- key_aimi_hyper_trajectory_release_aggressive

### wcycle
Gate: key_use_Aimi_wcycle

Women's cycle awareness for insulin sensitivity adjustments.

Key parameters (11):
- key_oaps_aimi_wcycle_contraceptive
- key_oaps_aimi_wcycle_thyroid
- key_oaps_aimi_wcycle_tracking_mode
- key_oaps_aimi_wcycle_verneuil
- key_use_Aimi_wcycle
- key_use_Aimi_wcycle_require_confirm
- key_use_Aimi_wcycle_shadow
- key_wcycle_avg_length
- key_wcycle_clamp_max
- key_wcycle_clamp_min
- key_wcycledateday

## Known Bugs

### BUG_001: LastLegacyPrebolusTime Math.max loop
- **Symptom:** Prebolus system keeps finding old delivery confirmations, wasting CPU
- **Technical:** internalLastLegacyPrebolusMillis getter uses Math.max(memValue, storedValue). A very old timestamp can never be reset — Math.max always returns the larger value. Thus pendingLegacyPrebolusUnit > 0 && internalLastLegacyPrebolusMillis > 0L is permanently true.
- **Workaround:** None (ADB reset fails on release builds). Proposed fix: getter should ignore timestamps older than 24h (set to 0L).

### BUG_002: OApsAIMIautoDrive is deprecated but still in code
- **Symptom:** Autodrive v3 does not activate even though key_use_aimi_autodrive_active=true
- **Technical:** BooleanKey.OApsAIMIautoDrive (key_use_Aimi_autoDrive) is the old Autodrive v2 gate. Autodrive v3 uses OApsAIMIautoDriveActive. The old key is still read in buildPreTherapyAutodriveByodaBootstrap() and blocks v3 when false.
- **Workaround:** Set key_use_Aimi_autoDrive=true even if you only want to use v3.

### BUG_003: Dead variables pbolusAS / pbolusA
- **Symptom:** Changing autodrivePrebolus / autodrivesmallPrebolus has no effect
- **Technical:** In runSignalPreparationPkpdRuntimePhase(), DoubleKey.OApsAIMIautodrivesmallPrebolus and DoubleKey.OApsAIMIautodrivePrebolus are read, but the results (pbolusAS, pbolusA) are never used afterward — dead variables.
- **Workaround:** None — parameters are ineffective.

## Parameter Summary by Feature Group

### adaptive_basal (11 params)
- `OApsAIMIAntiStallBias` [default: 0.1]
- `OApsAIMIKickerMaxMin` [default: 30]
- `OApsAIMIKickerMinUph` [default: 0.2]
- `OApsAIMIKickerStartMin` [default: 10]
- `OApsAIMIKickerStep` [default: 0.15]
- `OApsAIMIMaxMultiplier` [default: 1.6]
- `OApsAIMIPlateauBandAbs` [default: 2.5]
- `OApsAIMIR2Confident` [default: 0.7]
- `OApsAIMIZeroResumeFrac` [default: 0.25]
- `OApsAIMIZeroResumeMax` [default: 30]

### auditor (5 params)
- `aimi_auditor_enabled`
- `aimi_auditor_max_per_hour` [default: 12]
- `aimi_auditor_min_confidence` [default: 65]
- `aimi_auditor_mode`
- `aimi_auditor_timeout_seconds` [default: 120]

### autodrive_v3 (12 params)
- `aimi_mpc_insulin_u_per_kg_per_5min` [default: 0.065]
- `key_Acceleration_autodrive_mode` [default: 1.0]
- `key_aimi_autodrive_v3_authoritative` [default: True]
- `key_combinedDelta_autodrive_mode` [default: 1.0]
- `key_mindeviation_autodrive_mode` [default: 1.0]
- `key_oaps_aimi_autodriveBG` [default: 90]
- `key_oaps_aimi_autodriveTarget` [default: 70]
- `key_prebolus_autodrive_mode` [default: 1.0]
- `key_prebolussmall_autodrive_mode` [default: 0.1]
- `key_use_Aimi_autoDrive` [default: True]

### context_llm (10 params)
- `aimi_context_llm_claude_key`
- `aimi_context_llm_deepseek_key`
- `aimi_context_llm_gemini_key`
- `aimi_context_llm_openai_key`
- `aimi_context_llm_provider`
- `aimi_context_mode`
- `aimi_context_storage`
- `aimi_tuning_context_selection`
- `key_aimi_context_enabled`
- `key_aimi_context_llm_enabled`

### core (81 params)
- `OApsAIMIDeltaPosRelease` [default: 1.0]
- `OApsAIMIHighBg` [default: 180.0]
- `OApsAIMI_Enable_night`
- `absorption_cutoff` [default: 6.0]
- `absorption_maxtime` [default: 6.0]
- `activity_scale_factor` [default: 1.0]
- `activity_target` [default: 140.0]
- `aimi_advisor_claude_key`
- `aimi_advisor_deepseek_key`
- `aimi_advisor_gemini_key`

### cosine_gate (6 params)
- `aimi_cosine_gate_alpha` [default: 2.0]
- `aimi_cosine_gate_enabled`
- `aimi_cosine_gate_max_sens` [default: 1.3]
- `aimi_cosine_gate_max_shift` [default: 15]
- `aimi_cosine_gate_min_dq` [default: 0.3]
- `aimi_cosine_gate_min_sens` [default: 0.7]

### dyn_isf_trajectory (3 params)
- `aimi_dyn_isf_trajectory_max_fraction` [default: 0.06]
- `aimi_dyn_isf_trajectory_shadow_only` [default: True]
- `aimi_dyn_isf_trajectory_tuning_enabled` [default: False]

### endometriosis (6 params)
- `aimi_endo_basal_mult` [default: 1.3]
- `aimi_endo_enable`
- `aimi_endo_flare`
- `aimi_endo_flare_duration` [default: 4]
- `aimi_endo_smb_dampen` [default: 0.7]
- `aimi_endo_suppression`

### governance (17 params)
- `key_aimi_gov_anticipation_decay_blend_max` [default: 0.5]
- `key_aimi_gov_anticipation_hypo_damp` [default: 0.55]
- `key_aimi_gov_anticipation_lookback_samples` [default: 18.0]
- `key_aimi_gov_anticipation_margin_mgdl` [default: 12.0]
- `key_aimi_gov_hold_agg_decay_rate` [default: 0.97]
- `key_aimi_gov_hold_agg_decay_severe` [default: 0.965]
- `key_aimi_gov_hold_agg_floor_rate` [default: 0.7]
- `key_aimi_gov_hold_agg_floor_severe` [default: 0.72]
- `key_aimi_gov_hold_basal_decay_rate` [default: 0.98]
- `key_aimi_gov_hold_basal_decay_severe` [default: 0.975]

### hyper_trajectory (4 params)
- `key_aimi_hyper_deep_dev_mgdl` [default: 0.0]
- `key_aimi_hyper_established_dev_mgdl` [default: 0.0]
- `key_aimi_hyper_trajectory_release` [default: True]
- `key_aimi_hyper_trajectory_release_aggressive` [default: False]

### iob_surveillance (4 params)
- `aimi_priority_max_iob_extra_u` [default: 2.0]
- `aimi_priority_max_iob_factor` [default: 1.2]
- `aimi_red_carpet_restore_threshold` [default: 0.75]
- `key_aimi_iob_surveillance_guard`

### ism_fusion (3 params)
- `aimi_isf_fusion_max_change_per_tick` [default: 0.4]
- `aimi_isf_fusion_max_factor` [default: 2.0]
- `aimi_isf_fusion_min_factor` [default: 0.75]

### loop_config (2 params)
- `key_aimi_loop_blackbox_file_enabled`
- `key_aimi_loop_exclusive_invocation`

### meal_advisor (2 params)
- `OApsAIMILastEstimatedCarbTime` [default: 0.0]
- `OApsAIMILastEstimatedCarbs` [default: 0.0]

### meal_modes (22 params)
- `aimi_meal_advisor_trigger`
- `aimi_physio_sleep_enable`
- `key_oaps_aimi_dinner_factor` [default: 50.0]
- `key_oaps_aimi_dinner_interval` [default: 3]
- `key_oaps_aimi_lunch_factor` [default: 50.0]
- `key_oaps_aimi_lunch_interval` [default: 3]
- `key_oaps_aimi_meal_factor` [default: 50.0]
- `key_oaps_aimi_meal_interval` [default: 3]
- `key_oaps_aimi_sleep_factor` [default: 60.0]
- `key_oaps_aimi_sleep_interval` [default: 3]

### ngr (11 params)
- `key_oaps_aimi_ngr_age_years` [default: 14]
- `key_oaps_aimi_ngr_basal_multiplier` [default: 1.1]
- `key_oaps_aimi_ngr_decay_minutes` [default: 20]
- `key_oaps_aimi_ngr_enabled`
- `key_oaps_aimi_ngr_max_iob_extra` [default: 0.5]
- `key_oaps_aimi_ngr_min_duration` [default: 30]
- `key_oaps_aimi_ngr_min_eventual_over_target` [default: 15]
- `key_oaps_aimi_ngr_min_rise_slope` [default: 5.0]
- `key_oaps_aimi_ngr_night_end`
- `key_oaps_aimi_ngr_night_start`

### physiological_phase (5 params)
- `aimi_physio_assistant_enable`
- `aimi_physio_debug_logs`
- `aimi_physio_hrv_enable`
- `aimi_physio_llm_enable`
- `aimi_physio_llm_provider`

### pkpd (19 params)
- `aimi_pkpd_bounds_dia_max_h` [default: 24.0]
- `aimi_pkpd_bounds_dia_min_h` [default: 4.0]
- `aimi_pkpd_bounds_peak_min_max` [default: 240.0]
- `aimi_pkpd_bounds_peak_min_min` [default: 30.0]
- `aimi_pkpd_initial_dia_h` [default: 6.0]
- `aimi_pkpd_initial_peak_min` [default: 75.0]
- `aimi_pkpd_max_dia_change_per_day_h` [default: 3.0]
- `aimi_pkpd_max_peak_change_per_day_min` [default: 20.0]
- `aimi_pkpd_pragmatic_relief_min_factor` [default: 0.75]
- `aimi_pkpd_state_dia_h` [default: 6.0]

### recursive_belief (3 params)
- `key_aimi_recursive_belief_authority` [default: True]
- `key_aimi_recursive_belief_shadow` [default: True]
- `key_aimi_recursive_belief_wavelet` [default: False]

### smb_tail (4 params)
- `aimi_smb_exercise_damping` [default: 0.6]
- `aimi_smb_late_fat_damping` [default: 0.7]
- `aimi_smb_tail_damping` [default: 0.5]
- `aimi_smb_tail_threshold` [default: 0.25]

### sos_emergency (6 params)
- `aimi_emergency_sos_enable`
- `aimi_emergency_sos_immediate_threshold` [default: 50]
- `aimi_emergency_sos_phone`
- `aimi_emergency_sos_phone2`
- `aimi_emergency_sos_stale_threshold` [default: 30]
- `aimi_emergency_sos_threshold` [default: 55]

### t3c_adaptive_basal (9 params)
- `key_aimi_t3c_activation_threshold` [default: 130.0]
- `key_aimi_t3c_aggressiveness` [default: 1.0]
- `key_aimi_t3c_anticipation_strength` [default: 0.0]
- `key_aimi_t3c_brittle_mode`
- `key_aimi_t3c_cfrd_cob_delay_min` [default: 0.0]
- `key_aimi_t3c_cfrd_exacerbation`
- `key_aimi_t3c_cfrd_lgs_floor` [default: 80.0]
- `key_aimi_t3c_cfrd_mode`
- `key_use_aimi_t3c_adaptive_basal`

### thyroid (6 params)
- `key_aimi_thyroid_debug`
- `key_aimi_thyroid_enabled`
- `key_aimi_thyroid_guard_level`
- `key_aimi_thyroid_manual_status`
- `key_aimi_thyroid_mode`
- `key_aimi_thyroid_treatment_phase`

### tpo (3 params)
- `key_aimi_tpo_enabled`
- `key_aimi_tpo_llm_confirm_enabled`
- `key_aimi_tpo_notify_on_apply`

### trajectory_guard (1 params)
- `key_aimi_trajectory_guard_enabled`

### tube_advisor (6 params)
- `key_aimi_straight_line_tube_enabled` [default: False]
- `key_aimi_tube_aggressiveness` [default: 1.0]
- `key_aimi_tube_basal_trim_max` [default: 0.12]
- `key_aimi_tube_hyper_band_mgdl` [default: 35.0]
- `key_aimi_tube_hypo_floor_mgdl` [default: 72.0]
- `key_aimi_tube_kappa_margin` [default: 0.08]

### uam (1 params)
- `AIMI_UAM_CONFIDENCE` [default: 0.5]

### wcycle (11 params)
- `key_oaps_aimi_wcycle_contraceptive`
- `key_oaps_aimi_wcycle_thyroid`
- `key_oaps_aimi_wcycle_tracking_mode`
- `key_oaps_aimi_wcycle_verneuil`
- `key_use_Aimi_wcycle` [default: False]
- `key_use_Aimi_wcycle_require_confirm`
- `key_use_Aimi_wcycle_shadow`
- `key_wcycle_avg_length` [default: 28]
- `key_wcycle_clamp_max` [default: 1.25]
- `key_wcycle_clamp_min` [default: 0.8]