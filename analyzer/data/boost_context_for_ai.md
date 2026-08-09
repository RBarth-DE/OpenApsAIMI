# BOOST Plugin — Algorithm & Parameter Reference

> For AI-assisted parameter tuning. Branch: `dev_OAPSAIMI_RB`

## Algorithm Overview

BOOST (Blood Glucose Optimized Open Source Tuning) is a dynamic ISF engine that computes
insulin sensitivity from total daily dose (TDD) instead of a static profile value. It blends
multiple TDD windows (7-day, 1-day, 24h, 8h, 4h) with a weighted formula, adjusts for temp
targets, circadian rhythm, activity, sleep, heart-rate stress, and post-exercise recovery.
A parallel EMA(τ=3h) sensitivity shadow is computed for comparison but does NOT affect dosing.

The V5/V6/V7 meal models add meal hypothesis with committed/confirmed cap thresholds,
pre-meal targeting, and anticipation learning. A 6-position ML feature ring buffer stores
recent-SMB stats for learning.

## Core ISF Formula (TDD-based)

```
blendedTDD = W8H×0.33 + 7D×0.34 + 1D×0.33
W8H = (1.4×4H_TDD + 0.6×8-4H_TDD) × 3
If W8H < 75% of 7D: pull 7D down, then blend
sensitivity = 1800 / (blendedTDD × ln(normalTarget/insulinDivisor + 1))
Adjustment factor (ApsBoostDynIsfAdjustmentFactor%) scales TDD further.
```

## Feature Groups

### 1. TDD-based ISF (`boost_use_tdd` gate)
Controls whether BOOST computes dynamic ISF from TDD or falls back to profile ISF.
**Key parameters:**
- `boost_use_tdd` — Main gate: enables TDD-based ISF
- `boost_adjust_sensitivity` — Adjust ISF by 24H/7D TDD ratio
- `boost_autosens_when_no_tdd` — Fall back to autosens when TDD unavailable
- `enableCircadianISF` — Time-of-day ISF variation
- `DynISFAdjust` — TDD adjustment factor (%): >100 = inflate TDD = more insulin
- `boost_dynisf_velocity` — How fast ISF adapts (higher = faster, risk of oscillation)
- `boost_dynisf_bg_cap` — BG cap for DynISF: BG above this is damped (mg/dL)
- `boost_dynisf_normal_target` — Normal target BG for ln formula (mg/dL)

### 2. Meal Model V5/V6/V7
Multi-version meal hypothesis with committed/confirmed cap thresholds.
**Key parameters:**
- `boost_v5_active_dosing` — Enable V5 meal model
- `boost_v5_aggression` — Overall V5 aggression (1.0 = neutral)
- `boost_v5_hypo_caution` — V5 hypo caution (1.0 = neutral, >1 = more conservative)
- `boost_v5_confirmed_cap_u` — Max bolus for CONFIRMED meals (U)
- `boost_v5_committed_cap_u` — Max bolus for COMMITTED meals (U)
- `boost_v6_pre_meal_target` — Enable V6 pre-meal targeting
- `boost_v6_pre_meal_target_mgdl` — Target BG before meals (mg/dL)
- `boost_v6_pre_meal_lead_min` — Minutes before meal to start pre-meal dosing
- `boost_v5_composed_floor_active` — Composed floor for meal dosing
- `boost_v5_velocity_budget_active` — Velocity-based budget control
- `boost_v5_fast_carb_confirm` — Fast carb confirmation
- `boost_v5_aggressive_early_confirm` — More aggressive early confirmation
- `boost_v5_primer_tbr_fallback` — TBR fallback for primer
- `boost_v5_primer_bolus_mode` — Bolus mode for primer
- `boost_v5_sensitivity` — V5 sensitivity adjustment
- `boost_v5_primer_cap_u` — Primer cap (U)

### 3. Activity & Steps (`boost_activity_shadow_enabled` gate)
Step counting from Wear OS, Health Connect, or phone sensors.
**Key parameters:**
- `boost_activity_pct` — Activity ISF scaling (%): higher = more ISF when active = less insulin
- `boost_inactivity_pct` — Inactivity ISF scaling (%): higher = more ISF when sedentary
- `boost_activity_steps_5/15/30/60` — Step thresholds for activity level
- `boost_inactivity_steps` — Step threshold for inactivity

### 4. Night Mode (`boost_night_mode_enabled` gate)
Reduces insulin during night window with configurable BG offset.
**Key parameters:**
- `boost_night_mode_enabled` — Enable night mode
- `boost_night_mode_start` / `boost_night_mode_end` — Window times (HH:MM)
- `boost_night_mode_bg_offset` — BG target offset during night mode (mg/dL)
- `boost_night_mode_auto_by_sleep` — Auto-activate on sleep detection
- `boost_night_mode_disable_with_cob` — Disable when COB > 0
- `boost_night_mode_disable_with_low_tt` — Disable during low temp target

### 5. Sleep Detection
Sleep state from steps/inactivity patterns.
**Key parameters:**
- `boost_sleep_in_hrs` — Hours of low activity for sleep classification
- `boost_sleep_in_steps` — Max steps during sleep window
- `boost_sleep_hysteresis_min` — Sustained low activity minutes before sleep
- `boost_pre_sleep_lead_min` — Minutes before sleep onset for insulin reduction
- `boost_wake_hr_hysteresis_min` — Sustained HR elevation minutes before wake

### 6. HR Stress Detection (`boost_health_connect_hr_enabled` gate)
Health Connect heart rate data for stress-based ISF adjustment.
**Key parameters:**
- `boost_health_connect_hr_enabled` — Enable HR data ingestion
- `boost_hr_stress_detection` — Elevated HR → less insulin (higher ISF)
- `boost_hr_max_bpm` — Max expected HR (for normalization)
- `boost_hr_resting_bpm` — Resting HR baseline
- `boost_hr_window_minutes` — HR stress averaging window
- `boost_health_connect_poll_min` — Poll interval for HC data

### 7. Post-Exercise Recovery (`boost_post_exercise_recovery_enabled` gate)
Recovery window after exercise with reduced insulin.
**Key parameters:**
- `boost_post_exercise_recovery_hours` — Recovery duration (hours)
- `boost_post_exercise_recovery_scale` — Scale factor (0-1, lower = less insulin)
- `boost_post_exercise_recovery_target` — Recovery target BG (mg/dL)
- `boost_post_exercise_min_duration` — Min exercise duration to trigger (min)

### 8. SMB Delivery
Boost SMB delivery controls.
**Key parameters:**
- `boost_bolus_cap` — Max meal bolus (U)
- `boost_max_iob` — Max IOB for Boost dosing (U)
- `boost_insulin_req_pct` — PRIMARY aggressiveness: >100% = more insulin
- `boost_scale_value` — Global ISF scale (1.0 = neutral)
- `boost_cumulative_smb_cap_60min` — Max total SMB in 60 min window (U)
- `boost_percent_scale_factor` — ISF percentage scaling
- `enableBoostPercentScale` — Enable percent scaling
- `enableBoost_with_high_temptarget` — Allow Boost during high TT

### 9. Shared AAPS Settings (also read by Boost)
- `openapsama_max_basal` — Max temporary basal (U/h)
- `openapsama_max_daily_multiplier` — Max daily insulin multiplier
- `openapsama_max_current_basal_multiplier` — Max current basal multiplier
- `openapsama_smb_max_iob` — Max IOB for SMB
- `openapsama_use_smb` — Master SMB gate
- `openapsama_use_autosens` — Enable autosens
- `openapsama_use_dynamic_sensitivity` — Enable DynISF
- `openapsama_use_uam` — Enable unannounced meals
- `openapsama_autosens_max` / `openapsama_autosens_min` — Autosens ratio bounds

## Tuning Guidelines

1. **Start**: `boost_use_tdd=ON`, `boost_insulin_req_pct=100` (neutral)
2. **TIR < 70%, mean BG > 160**: increase `boost_insulin_req_pct` to 105–115%
3. **Hypo > 3%**: decrease `boost_insulin_req_pct` to 85–95%, or raise `boost_max_iob`
4. **Post-meal spikes > 250**: increase `boost_v6_pre_meal_lead_min`, raise `boost_bolus_cap`
5. **Overnight hypos**: enable night mode, `boost_night_mode_start=22:00`, `boost_night_mode_end=07:00`
6. **Exercise-related hypos**: enable post-exercise recovery, `boost_post_exercise_recovery_scale=0.5`
7. **Too conservative overall**: check `boost_percent_scale_factor` (increase to 110%), or `DynISFAdjust` (increase to 110%)
8. **ISF Shadow divergence**: large gap between real ISF and shadow suggests TDD data quality issues

## Known Interactions

- `boost_insulin_req_pct` AND `boost_max_iob` both control aggressiveness — change one at a time
- `boost_dynisf_velocity` > 80% can cause oscillation — reduce if CV > 36%
- `boost_night_mode_bg_offset` adds to profile target — start small (10-20 mg/dL)
- Activity settings and HR settings can stack — reduce activity_pct if HR stress detection is enabled
- `boost_autosens_when_no_tdd` keeps some adaptation even without TDD data — safer than flat profile ISF
