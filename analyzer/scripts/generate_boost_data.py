#!/usr/bin/env python3
"""
Boost Parameter Analyzer — Data File Generator
==============================================
Scans the AAPS source code for BOOST-related parameters and generates:
  data/boost_parameters.json       — Full parameter knowledge base
  data/boost_context_for_ai.json   — Feature descriptions, algorithm overview
  data/boost_context_compact.txt   — Compact text for AI prompts
  data/boost_param_lookup.json     — Quick lookup of parameter summaries

Usage:
  python3 generate_boost_data.py [--source-root /path/to/OpenApsAIMI_V4]
"""

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# ─── Configuration ───────────────────────────────────────────────────────────

_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT  = _SCRIPT_DIR.parent.parent

DEFAULT_SOURCE_ROOT = str(_AUTO_ROOT) if (_AUTO_ROOT / "plugins" / "aps").exists() else None

DATA_DIR = _SCRIPT_DIR.parent / "data"

# Boost source directories (relative to source root). The V5 meal model lives in
# openAPSBoostV5, a SIBLING of openAPSBoost — scanning only openAPSBoost silently drops
# all V5/V6 logic context (state machine, composed floor, primer, caps). Scan the parent
# and pick the openAPSBoost* siblings explicitly so unrelated aps packages don't leak in.
BOOST_SRC = "plugins/aps/src/main/kotlin/app/aaps/plugins/aps"
BOOST_SRC_DIRS = [
    "openAPSBoost",
    "openAPSBoostV5",
    "openAPSBoostV7",
    "openAPSBoostTing",
    "openAPSBoostTwin",
]
# Other plugin sources shared keys are actually used in (not Boost-owned). Scanned for
# usage detection only so shared keys like openapsama_autosens_period don't read as orphans.
BOOST_SRC_EXTRA_DIRS = [
    "plugins/sensitivity/src/main/kotlin/app/aaps/plugins/sensitivity",
    "plugins/main/src/main/kotlin/app/aaps/plugins/main/iob/iobCobCalculator",
    "plugins/sync/src/main/kotlin/app/aaps/plugins/sync/openhumans",
    "plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/configBuilder",
]

# Key definition files (relative to source root)
KEY_FILES = [
    "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/LongKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
]

# Enum entry names that are Boost-specific (starts with ApsBoost)
BOOST_ENUM_NAMES = {
    # Double keys
    "ApsBoostBolus", "ApsBoostMaxIob", "ApsBoostInsulinReqPct", "ApsBoostScale",
    "ApsBoostPercentScale", "ApsBoostDynIsfVelocity", "ApsBoostSleepInHours",
    "ApsBoostInactivityPct", "ApsBoostActivityPct", "ApsBoostPostExerciseRecoveryHours",
    "ApsBoostPostExerciseRecoveryScale", "ApsBoostCumulativeSmbCap60Min",
    "ApsBoostV5Aggression", "ApsBoostV5ConfirmedCapU", "ApsBoostV5CommittedCapU",
    "ApsBoostV5PrimerCapU", "ApsBoostV5HypoCaution", "ApsBoostV5Sensitivity",
    "ApsBoostV6PreMealTargetMgdl", "ApsBoostV6PreMealLeadMin",
    # Boolean keys
    "ApsBoostEnablePercentScale", "ApsBoostEnableCircadianIsf",
    "ApsBoostAllowWithHighTt", "ApsBoostUseTdd", "ApsBoostAdjustSensitivity",
    "ApsBoostAllowAllBgSources", "ApsBoostNightModeEnabled",
    "ApsBoostNightModeDisableWithCob", "ApsBoostNightModeDisableWithLowTt",
    "ApsBoostNightModeAutoBySleep", "ApsBoostHealthConnectHrEnabled",
    "ApsBoostBypassVersionCheck", "ApsBoostV5ActiveDosing",
    "ApsBoostV6PreMealTarget", "ApsBoostV5FastCarbConfirm",
    "ApsBoostV5AggressiveEarlyConfirm", "ApsBoostV5ComposedFloorActive",
    "ApsBoostV5VelocityBudgetActive", "ApsBoostV5PrimerTbrFallback",
    "ApsBoostV5PrimerBolusMode",
    # Boolean keys from OpenAPSBoostPlugin (preference screen + algorithm)
    "ApsBoostActivityShadowEnabled", "ApsBoostAutosensWhenNoTdd",
    "ApsBoostHrIntegrationEnabled", "ApsBoostHrStressDetection",
    "ApsBoostPostExerciseRecoveryEnabled",
    # Int keys
    "ApsBoostInactivitySteps", "ApsBoostSleepInSteps",
    "ApsBoostActivitySteps5", "ApsBoostActivitySteps15",
    "ApsBoostActivitySteps30", "ApsBoostActivitySteps60",
    "ApsBoostDynIsfAdjustmentFactor", "ApsBoostHrMaxBpm", "ApsBoostHrRestingBpm",
    "ApsBoostHrWindowMinutes", "ApsBoostPreSleepLeadMin",
    "ApsBoostSleepHysteresisMin", "ApsBoostWakeHrHysteresisMin",
    "ApsBoostHealthConnectPollMin", "ApsBoostPostExerciseMinDuration",
    # UnitDouble keys
    "ApsBoostDynIsfBgCap", "ApsBoostDynIsfNormalTarget",
    "ApsBoostNightModeBgOffset", "ApsBoostPostExerciseRecoveryTarget",
    # String keys
    "ApsBoostStartTime", "ApsBoostEndTime", "ApsBoostNightModeStart",
    "ApsBoostNightModeEnd", "ApsBoostV5State", "ApsBoostV7ResidualPools",
    "ApsBoostIsfShadowState", "ApsBoostAnticipHistory", "ApsBoostSleepState",
    "ApsBoostSleepHistory", "ApsBoostMlRingBuffer", "ApsBoostMealTimeHistory",
    "ApsBoostDailyStepHistory", "ApsBoostIntradayStepBank",
}

# Keys that exist in core/keys but do NOT belong in the Boost knowledge base.
# - The openapsama_smb_delivery_ratio* / openapsama_smb_max_range_extension family are
#   AutoISF advisor settings (ApsAutoIsfSmbDeliveryRatio*), owned by the autoisf analyzer.
#   The loose openapsama_ fallback in is_boost_key() used to drag them in as orphans.
# - boost_bypass_version_check: no usage anywhere in the repo (dead key).
# - boost_start_time / boost_end_time: retired 2026-07-02 — OpenAPSBoostPlugin comments
#   say "ApsBoostStartTime/EndTime are no longer read" and "are retired".
BOOST_EXCLUDE_KEYS = {
    "openapsama_smb_delivery_ratio",
    "openapsama_smb_delivery_ratio_min",
    "openapsama_smb_delivery_ratio_max",
    "openapsama_smb_delivery_ratio_bg_range",
    "openapsama_smb_max_range_extension",
    "boost_bypass_version_check",
    "boost_start_time",
    "boost_end_time",
}

# Additional shared keys that Boost's algorithm hard-references.
# Enum entry name → key string. These are added regardless of the enum name filter.
SHARED_BOOST_KEYS = {
    "ApsUseSmb":               "openapsama_use_smb",
    "ApsUseSmbAlways":         "openapsama_use_smb_always",
    "ApsUseSmbWithCob":        "openapsama_use_smb_with_cob",
    "ApsUseSmbAfterCarbs":     "openapsama_use_smb_after_carbs",
    "ApsUseSmbWithHighTt":     "openapsama_use_smb_with_high_tt",
    "ApsUseSmbWithLowTt":      "openapsama_use_smb_with_low_tt",
    "ApsUseUam":               "openapsama_use_uam",
    "ApsUseAutosens":          "openapsama_use_autosens",
    "ApsUseDynamicSensitivity":"openapsama_use_dynamic_sensitivity",
    "ApsMaxBasal":             "openapsama_max_basal",
    "ApsMaxDailyMultiplier":   "openapsama_max_daily_multiplier",
    "ApsMaxCurrentBasalMultiplier": "openapsama_max_current_basal_multiplier",
    "ApsSmbMaxIob":            "openapsama_smb_max_iob",
    "ApsMaxSmbFrequency":      "openapsama_max_smb_frequency",
    "ApsMaxMinutesOfBasalToLimitSmb": "openapsama_max_minutes_of_basal_to_limit_smb",
    "ApsUamMaxMinutesOfBasalToLimitSmb": "openapsama_uam_max_minutes_of_basal_to_limit_smb",
    "ApsCarbsRequestThreshold": "openapsama_carbs_request_threshold",
    "AutosensMax":             "openapsama_autosens_max",
    "AutosensMin":             "openapsama_autosens_min",
    "ApsAutoIsfHighTtRaisesSens":   "openapsama_autoisf_high_tt_raises_sens",
    "ApsAutoIsfLowTtLowersSens":    "openapsama_autoisf_low_tt_lowers_sens",
    "ApsLgsThreshold":         "openapsama_lgs_threshold",
    "AutosensPeriod":          "openapsama_autosens_period",
}

# Boolean-like values that represent BOOST feature gates
GATE_KEYS = {
    "boost_use_tdd",
    "boost_adjust_sensitivity",
    "enableCircadianISF",
    "enableBoost_with_high_temptarget",
    "enableBoostPercentScale",
    "boost_health_connect_hr_enabled",
    "boost_hr_stress_detection",
    "boost_night_mode_enabled",
    "boost_night_mode_auto_by_sleep",
    "boost_night_mode_disable_with_cob",
    "boost_night_mode_disable_with_low_tt",
    "boost_post_exercise_recovery_enabled",
    "boost_activity_shadow_enabled",
    "boost_autosens_when_no_tdd",
    "boost_v5_active_dosing",
    "boost_v6_pre_meal_target",
    "boost_v5_composed_floor_active",
    "boost_v5_velocity_budget_active",
    "boost_v5_primer_tbr_fallback",
    "boost_v5_primer_bolus_mode",
    "boost_allow_all_bg_sources",
    "openapsama_use_autosens",
    "openapsama_use_dynamic_sensitivity",
    "openapsama_use_smb",
    "openapsama_use_smb_always",
    "openapsama_use_smb_with_cob",
    "openapsama_use_smb_after_carbs",
    "openapsama_use_smb_with_high_tt",
    "openapsama_use_smb_with_low_tt",
    "openapsama_use_uam",
}

# ─── Source Scanner ──────────────────────────────────────────────────────────

# Cache of android string resource name → text, loaded from res/values/strings.xml
# files. Lets logic summaries use the REAL human-written summaryResId text instead
# of a generic fallback for every parameter.
_android_strings_cache: Optional[Dict[str, str]] = None

_ANDROID_STRING_RE = re.compile(
    r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL
)


def load_android_strings(source_root: Path) -> Dict[str, str]:
    """Load string resources from all res/values/strings.xml files under source root."""
    global _android_strings_cache
    if _android_strings_cache is not None:
        return _android_strings_cache
    strings: Dict[str, str] = {}
    for res_dir in source_root.rglob("values"):
        strings_file = res_dir / "strings.xml"
        if not strings_file.is_file():
            continue
        try:
            content = strings_file.read_text(encoding="utf-8")
        except OSError:
            continue
        for match in _ANDROID_STRING_RE.finditer(content):
            name = match.group(1)
            text = re.sub(r"<[^>]+>", " ", match.group(2))
            text = re.sub(r"\s+", " ", text).strip()
            if text:
                strings[name] = text
    _android_strings_cache = strings
    return strings


class BoostKeyScanner:
    """Parses Kotlin enum classes to extract key definitions for Boost."""

    def __init__(self, source_root: str):
        self.source_root = Path(source_root)
        self.keys: Dict[str, dict] = {}

    def is_boost_key(self, key: str, enum_name: str = "") -> bool:
        """Check if a key or its enum entry is Boost-related."""
        if key in BOOST_EXCLUDE_KEYS:
            return False
        if enum_name in BOOST_ENUM_NAMES:
            return True
        if enum_name in SHARED_BOOST_KEYS:
            return True
        # Also check key string pattern as fallback
        if key.startswith("boost_") or key.startswith("key_boost_"):
            return True
        # Shared keys may not have a matching enum name
        if key.startswith("openapsama_") and (
            "autosens" in key or "max_basal" in key or "max_daily" in key
            or "smb_" in key or "use_smb" in key or "use_autosens" in key
            or "use_uam" in key or "max_smb" in key or "max_minutes" in key
        ):
            return True
        return False

    def parse_all(self):
        """Parse all key definition files."""
        for kf in KEY_FILES:
            path = self.source_root / kf
            if path.exists():
                self._parse_file(path)
        return self.keys

    def _parse_file(self, filepath: Path):
        content = filepath.read_text(encoding="utf-8")

        type_map = {
            "DoubleKey": "Double",
            "BooleanKey": "Boolean",
            "IntKey": "Int",
            "UnitDoubleKey": "UnitDouble",
            "LongKey": "Long",
            "StringKey": "String",
        }

        class_match = re.search(
            r'enum\s+class\s+(\w+)\s*\(?\s*([\s\S]*?)\)?\s*:\s*(\w+)', content
        )
        if not class_match:
            return

        class_name = class_match.group(1)
        param_type = type_map.get(class_name, "Unknown")

        defaults = self._extract_defaults(content)

        # Match enum entries: Name(key="xxx", ...) or Name("xxx", ...)
        entries = re.finditer(
            r'([A-Z][A-Za-z0-9]+)\s*\(\s*((?:[^()]|\([^)]*\))*?)\s*\)\s*,?\s*(?://[^\n]*)?\s*$',
            content, re.MULTILINE
        )

        for match in entries:
            name = match.group(1)
            args_str = match.group(2)
            if name in type_map:
                continue
            entry = self._parse_entry_args(name, args_str, param_type, defaults)
            if entry and self.is_boost_key(entry.get("key", ""), name):
                self.keys[entry["key"]] = entry

    def _extract_defaults(self, content: str) -> dict:
        defaults = {}
        for match in re.finditer(
            r'override\s+(?:val|var)\s+(\w+)\s*:\s*(\S+)\s*=\s*([^,\n)]+)', content
        ):
            name = match.group(1)
            value = match.group(3).strip()
            try:
                if value == "true":
                    defaults[name] = True
                elif value == "false":
                    defaults[name] = False
                elif value == "null":
                    defaults[name] = None
                elif value == "0":
                    defaults[name] = 0
                else:
                    defaults[name] = value
            except Exception:
                defaults[name] = value
        return defaults

    def _parse_entry_args(self, name: str, args: str, ptype: str, defaults: dict) -> Optional[dict]:
        entry = {"name": name, "type": ptype}

        key_match = re.search(r'key\s*=\s*"([^"]+)"', args)
        if key_match:
            entry["key"] = key_match.group(1)
            named_args = dict(re.findall(r'(\w+)\s*=\s*([^,]+(?:\([^)]*\))?)', args))
        else:
            pos_args = re.findall(r'([^,]+(?:\([^)]*\))?)', args)
            cleaned = [a.strip() for a in pos_args]
            if cleaned and cleaned[0].startswith('"'):
                entry["key"] = cleaned[0].strip('"')
            else:
                return None
            named_args = {}

        if ptype in ("Double", "Int", "UnitDouble", "Long"):
            if "defaultValue" in named_args:
                entry["default"] = self._parse_number(named_args["defaultValue"])
            elif len([a for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]) >= 2:
                pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
                if ptype == "Double" and len(pos) >= 3:
                    entry["default"] = self._parse_number(pos[1])
                elif ptype == "Int" and len(pos) >= 3:
                    entry["default"] = self._parse_number(pos[1])

            if "min" in named_args:
                entry["min"] = self._parse_number(named_args["min"])
            elif ptype == "Double" and len(
                [a for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
            ) >= 4:
                pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
                entry["min"] = self._parse_number(pos[2])

            if "max" in named_args:
                entry["max"] = self._parse_number(named_args["max"])
            elif ptype == "Double" and len(
                [a for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
            ) >= 5:
                pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
                entry["max"] = self._parse_number(pos[3])

        if ptype == "Boolean":
            if "defaultValue" in named_args:
                entry["default"] = named_args["defaultValue"].strip().lower() == "true"
            elif "defaultValue" in defaults:
                entry["default"] = defaults["defaultValue"] is True
            else:
                # BooleanKey entries pass the default positionally: Name("key", false, ...)
                # or Name("key", true, ...). Without this, every Boolean default shows
                # as "None" in the generated context.
                pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
                if len(pos) >= 2 and pos[1] in ("true", "false"):
                    entry["default"] = pos[1] == "true"

        dep_match = re.search(r'dependency\s*=\s*(?:BooleanKey\.)?(\w+)', args)
        if dep_match:
            entry["dependency"] = dep_match.group(1)

        neg_dep_match = re.search(
            r'negativeDependency\s*=\s*(?:BooleanKey\.)?(\w+)', args
        )
        if neg_dep_match:
            entry["negative_dependency"] = neg_dep_match.group(1)

        unit_match = re.search(r'unitType\s*=\s*UnitType\.(\w+)', args)
        if unit_match:
            entry["unit_type"] = unit_match.group(1)

        step_match = re.search(r'step\s*=\s*([\d.]+)', args)
        if step_match:
            entry["step"] = self._parse_number(step_match.group(1))

        entry["defaulted_by_sm"] = "defaultedBySM = true" in args
        entry["calculated_by_sm"] = "calculatedBySM = true" in args

        title_match = re.search(r'titleResId\s*=\s*R\.string\.(\w+)', args)
        if title_match:
            entry["title_res_id"] = title_match.group(1)

        summary_match = re.search(r'summaryResId\s*=\s*R\.string\.(\w+)', args)
        if summary_match:
            entry["summary_res_id"] = summary_match.group(1)

        pref_match = re.search(r'preferenceType\s*=\s*PreferenceType\.(\w+)', args)
        if pref_match:
            entry["preference_type"] = pref_match.group(1)

        return entry

    @staticmethod
    def _parse_number(s: str) -> float:
        s = s.strip().rstrip('f').rstrip('F').rstrip('L').rstrip('d').rstrip('D')
        try:
            if '.' in s:
                return float(s)
            return int(s)
        except ValueError:
            return 0.0


class BoostSourceScanner:
    """Scans Boost source code to find where each key is used."""

    def __init__(self, source_root: str):
        self.source_root = Path(source_root)
        self.boost_base = self.source_root / BOOST_SRC
        self.files: Dict[str, str] = {}

    def scan_all(self):
        if not self.boost_base.exists():
            print(f"WARNING: Boost source directory not found: {self.boost_base}")
            return
        for dir_name in BOOST_SRC_DIRS:
            boost_dir = self.boost_base / dir_name
            if not boost_dir.exists():
                continue
            for kt_file in boost_dir.rglob("*.kt"):
                relative = str(kt_file.relative_to(self.boost_base))
                self.files[relative] = kt_file.read_text(encoding="utf-8")
        for rel_dir in BOOST_SRC_EXTRA_DIRS:
            extra_dir = self.source_root / rel_dir
            if not extra_dir.exists():
                continue
            for kt_file in extra_dir.rglob("*.kt"):
                relative = str(kt_file)
                self.files[relative] = kt_file.read_text(encoding="utf-8")

    def find_usages(self, key: str, enum_name: str = "") -> List[dict]:
        usages = []
        patterns = [f'"{key}"']
        if enum_name:
            patterns.append(f".{enum_name}")
            patterns.append(f"DoubleKey.{enum_name}")
            patterns.append(f"BooleanKey.{enum_name}")
            patterns.append(f"IntKey.{enum_name}")
            patterns.append(f"UnitDoubleKey.{enum_name}")
            patterns.append(f"LongKey.{enum_name}")
            patterns.append(f"StringKey.{enum_name}")
            patterns.append(f"preferences.getBoostDosing(BooleanKey.{enum_name}")
            patterns.append(f"preferences.getBoostDosing(DoubleKey.{enum_name}")
            patterns.append(f"preferences.getBoostDosing(IntKey.{enum_name}")
            # preferences.get(...) / preferences.observe(...) used by shared keys outside
            # the Boost packages (e.g. IntKey.AutosensPeriod in the sensitivity plugins)
            patterns.append(f"preferences.get({enum_name}")
            patterns.append(f"preferences.observe({enum_name}")

        for filepath, content in list(self.files.items()):
            matched = False
            for pattern in patterns:
                if pattern in content and not matched:
                    func = self._find_enclosing_function(content, content.index(pattern))
                    usages.append({
                        "file": filepath.split("/")[-1],
                        "function": func,
                    })
                    matched = True
                    break
        return usages[:8]

    def _find_enclosing_function(self, content: str, pos: int) -> Optional[str]:
        before = content[:pos]
        func_matches = list(re.finditer(
            r'(?:private\s+|internal\s+|override\s+)*fun\s+(\w+)', before
        ))
        if func_matches:
            return func_matches[-1].group(1)
        return "unknown"


# ─── Boost Feature Groups ────────────────────────────────────────────────────

BOOST_FEATURE_GROUPS = {
    "tdd_isf": {
        "id": "tdd_isf",
        "name": "TDD-based ISF",
        "description": (
            "Core Boost engine: computes dynamic ISF from total daily dose (TDD) "
            "using 7D/24H/8H/4H weighted blends. Uses ln(bg/divisor+1)*1800/TDD formula. "
            "Includes circadian ISF, DynISF velocity scaling, and sensitivity adjustment."
        ),
        "gate_key": "boost_use_tdd",
    },
    "meal_model": {
        "id": "meal_model",
        "name": "Meal Model (V5/V6/V7)",
        "description": (
            "Multi-version meal hypothesis system. V5: aggression/hypo-caution scaling. "
            "V6: pre-meal target, lead time, committed/confirmed cap thresholds. "
            "V7: residual pools. Anticipation history for meal-time learning."
        ),
        "gate_key": None,
    },
    "activity": {
        "id": "activity",
        "name": "Activity & Steps",
        "description": (
            "Step counting from Wear OS / Health Connect / phone sensors. "
            "Activity % and inactivity % scale ISF. Activity shadow mode computes "
            "but doesn't apply. Post-exercise recovery with configurable duration/scale."
        ),
        "gate_key": "boost_activity_shadow_enabled",
    },
    "night_mode": {
        "id": "night_mode",
        "name": "Night Mode",
        "description": (
            "Reduces insulin during configured night window. Auto-by-sleep option. "
            "COB and low-TT disable gates. BG offset for tighter overnight control."
        ),
        "gate_key": "boost_night_mode_enabled",
    },
    "sleep": {
        "id": "sleep",
        "name": "Sleep Detection",
        "description": (
            "Tracks sleep state from steps/inactivity patterns. Sleep-in hours/steps "
            "thresholds. Hysteresis for wake/sleep transitions. Pre-sleep lead time."
        ),
        "gate_key": None,
    },
    "hr_stress": {
        "id": "hr_stress",
        "name": "HR Stress Detection",
        "description": (
            "Health Connect heart rate integration. Detects stress from elevated "
            "resting HR. Configurable max/resting BPM, window minutes, polling interval."
        ),
        "gate_key": "boost_health_connect_hr_enabled",
    },
    "post_exercise": {
        "id": "post_exercise",
        "name": "Post-Exercise Recovery",
        "description": (
            "Recovery window after exercise ends. Configurable hours, scale factor, "
            "minimum duration gate, and recovery target BG."
        ),
        "gate_key": "boost_post_exercise_recovery_enabled",
    },
    "smb_delivery": {
        "id": "smb_delivery",
        "name": "SMB Delivery",
        "description": (
            "Boost SMB delivery controls: cumulative 60-min cap, bolus size, "
            "max IOB, insulin requirement %. Interacts with standard SMB settings "
            "(frequency, basal-to-limit, UAM limits)."
        ),
        "gate_key": "openapsama_use_smb",
    },
    "ml_features": {
        "id": "ml_features",
        "name": "ML Feature Ring Buffer",
        "description": (
            "6-position lookback ring buffer for ML features. Stores recent-SMB "
            "stats, BG trends. Serialized per cycle to persistence."
        ),
        "gate_key": None,
    },
    "isf_shadow": {
        "id": "isf_shadow",
        "name": "ISF Shadow (V4.4.2-style EMA)",
        "description": (
            "Parallel EMA(τ=3h) sensitivity ratio computed alongside real ISF. "
            "Does NOT influence dosing — diagnostic only. Shadow state persisted."
        ),
        "gate_key": None,
    },
    "shared_aaps": {
        "id": "shared_aaps",
        "name": "Shared AAPS Settings",
        "description": (
            "Standard OpenAPS settings that Boost's algorithm also reads: "
            "max basal, max IOB, SMB gates, autosens, dynamic sensitivity."
        ),
        "gate_key": None,
    },
    "safety": {
        "id": "safety",
        "name": "Safety",
        "description": (
            "Ketoacidosis protection, force limits, half-basal exercise target."
        ),
        "gate_key": None,
    },
}


# Settings paths for keys that don't appear in the Compose preference screen
# (algorithm-only keys, or shared keys referenced via code but not in getPreferenceScreenContent)
BOOST_SETTINGS_PATH_OVERRIDES = {
    # These keys are either algorithm-only or not reachable by the settings-path
    # generator. For keys that the generator CAN find, leave them out — the
    # generator's output (via _get_generated_path) takes precedence and stays in
    # sync automatically. Only add an override when the generator misses a key
    # that users genuinely need to find.
    #
    # Night mode string keys (not in any preference screen — algorithm-only)
    "boost_night_mode_start":                "OpenAPS BOOST V6 → OpenAPS BOOST V1 and up",
    "boost_night_mode_end":                  "OpenAPS BOOST V6 → OpenAPS BOOST V1 and up",
}
# ─── Generated settings paths cache ─────────────────────────────────────

_generated_paths_cache: dict[str, str] | None = None


def _get_generated_path(key_str: str) -> str | None:
    """Load generated paths from boost_settings_paths.json (lazy, cached)."""
    global _generated_paths_cache
    if _generated_paths_cache is None:
        paths_file = _SCRIPT_DIR.parent / "data" / "boost_settings_paths.json"
        if paths_file.exists():
            with open(paths_file, encoding="utf-8") as f:
                data = json.load(f)
            _generated_paths_cache = {
                k: v["path"] if isinstance(v, dict) else v
                for k, v in data.get("paths", {}).items()
            }
        else:
            _generated_paths_cache = {}
    return _generated_paths_cache.get(key_str)


# ─── Boost Logic Summary Generator ─────────────────────────────────────

def generate_boost_logic_summary(key: str, info: dict, source_root: Optional[Path] = None) -> str:
    """Generate an English logic summary from parameter name and type.

    Resolution order: the real summaryResId text from strings.xml (human-written,
    most accurate) → hand-written template for well-understood keys → generic fallback.
    """
    ptype = info.get("type", "")

    if source_root is not None:
        android_strings = load_android_strings(source_root)
        res_id = info.get("summary_res_id")
        if res_id and res_id in android_strings:
            return android_strings[res_id]

    name = key.replace("key_apsboost_", "").replace("key_boost_", "")\
              .replace("openapsama_", "").replace("openaps_", "")\
              .replace("ApsBoost", "").replace("activity_", "")

    templates = {
        "adjust_sensitivity": (
            "When ON: adjusts ISF based on 24H/7D TDD ratio. "
            "Higher recent TDD → less ISF (more insulin needed), lower TDD → more ISF (less insulin)."
        ),
        "autosens_when_no_tdd": (
            "When ON and TDD data unavailable: falls back to standard autosens "
            "instead of profile ISF. Prevents aggressive dosing during data gaps."
        ),
        "circadian_isf": (
            "Enables time-of-day ISF variation. Uses learned circadian rhythm "
            "to adjust ISF by hour. More insulin at known high-resistance times."
        ),
        "allow_with_high_tt": "When ON: Boost ISF scaling still applies during high BG temp targets.",
        "enable_percent_scale": (
            "When ON: applies BoostScale% multiplier to ISF. "
            "100% = no change. >100% = more ISF (less insulin). <100% = less ISF (more insulin)."
        ),
        "percent_scale": (
            "Global ISF scaling percentage. 100% = neutral. "
            ">100% = more ISF = less aggressive. <100% = less ISF = more aggressive."
        ),
        "hr_integration": "Enables Health Connect heart rate data for stress-based ISF adjustment.",
        "hr_stress_detection": (
            "When ON: elevated HR above resting → stress detection → "
            "increases ISF (less insulin needed during stress)."
        ),
        "hr_max_bpm": "Maximum expected heart rate. Used to normalize HR stress signal.",
        "hr_resting_bpm": "Resting heart rate. HR above this = potential stress. Lower = more sensitive.",
        "hr_window_minutes": "Lookback window for HR stress averaging (minutes).",
        "night_mode_enabled": "Enables reduced insulin during configured night window.",
        "night_mode_auto_by_sleep": (
            "When ON: night mode auto-activates based on sleep detection "
            "instead of fixed start/end times."
        ),
        "night_mode_disable_with_cob": "Disables night mode when carbs-on-board > 0.",
        "night_mode_disable_with_low_tt": "Disables night mode when low temp target is active.",
        "night_mode_start": "Night mode start time (HH:MM). Default 22:00.",
        "night_mode_end": "Night mode end time (HH:MM). Default 07:00.",
        "night_mode_bg_offset": "BG offset during night mode (mg/dL). Lowers target for tighter control.",
        "post_exercise_recovery_enabled": "Enables reduced insulin for hours after exercise ends.",
        "post_exercise_recovery_hours": "Recovery duration (hours). ISF stays elevated this long post-exercise.",
        "post_exercise_recovery_scale": (
            "Scale factor for recovery (0.0-1.0). Lower = stronger ISF boost = less insulin. "
            "1.0 = no recovery adjustment."
        ),
        "post_exercise_recovery_target": "Target BG during post-exercise recovery (mg/dL).",
        "post_exercise_min_duration": "Minimum exercise duration (min) to trigger recovery mode.",
        "activity_shadow_enabled": "When ON: activity ISF adjustments computed but NOT applied (shadow mode).",
        "activity_pct": (
            "Activity ISF scaling (%). Higher = more ISF when active = less insulin. "
            "Lower values if activity causes hypos."
        ),
        "inactivity_pct": (
            "Inactivity ISF scaling (%). Higher = more ISF when sedentary = less insulin. "
            "Lower values if inactivity causes hypers."
        ),
        "sleep_in_hours": "Hours of low activity to classify as sleep-in. Default 7.0.",
        "sleep_in_steps": "Maximum step count during sleep-in window. Default 50.",
        "sleep_hysteresis_min": "Minutes of sustained low activity before sleep state confirmed.",
        "wake_hr_hysteresis_min": "Minutes of sustained HR elevation before wake confirmed.",
        "pre_sleep_lead_min": "Minutes before detected sleep onset to start pre-sleep insulin reduction.",
        "cumulative_smb_cap": (
            "Maximum total SMB insulin (units) in a 60-minute window. "
            "Prevents stacking. Higher = more aggressive. 0 = no cap."
        ),
        "v6_pre_meal_target": "Target BG for pre-meal window (mg/dL). Lower = more aggressive before meals.",
        "v6_pre_meal_lead": "Minutes before detected meal to start pre-meal dosing.",
        "bolus": "Boost meal bolus size (units). Cap for meal-related SMB delivery.",
        "max_iob": "Maximum IOB for Boost dosing. Replaces standard max IOB in Boost mode.",
        "insulin_req_pct": (
            "Insulin requirement scaling (%). Above 100% = Boost delivers more insulin. "
            "Below 100% = Boost is more conservative. Primary aggressiveness control."
        ),
        "scale": "Global ISF scale factor. 1.0 = neutral. Direct multiplier on ISF.",
        "dyn_isf_velocity": "Dynamic ISF velocity scaling. Controls how fast ISF adapts to changing conditions.",
        "dyn_isf_bg_cap": "BG cap for DynISF calculation (mg/dL). BG above this gets damped.",
        "dyn_isf_normal_target": "Normal target BG for DynISF ln-based formula (mg/dL).",
        "dyn_isf_adjustment_factor": (
            "TDD adjustment factor (%). 100% = use TDD as-is. >100% = inflate TDD = more aggressive. "
            "<100% = deflate TDD = less aggressive."
        ),
        "v5_aggression": "V5 meal model aggression (1.0 = neutral). Higher = more insulin during meals.",
        "v5_hypo_caution": "V5 hypo caution (1.0 = neutral). Higher = more conservative near hypo thresholds.",
        "v5_committed_cap": "V5 max bolus for COMMITTED meals (units). Cap for confirmed meal dosing.",
        "v5_confirmed_cap": "V5 max bolus for CONFIRMED meals (units). Lower cap for less-certain meals.",
        "v5_primer_cap": (
            "V5 early primer cap (U) during OBSERVING. 0 = primer OFF. Fires once per meal "
            "session on delta_accl > 10 before CONFIRMED, reclaiming V1's ~15-min-earlier "
            "acceleration response. PrimerTbrFallback / PrimerBolusMode only choose the delivery "
            "method — the cap value is what turns the primer on."
        ),
        "v5_composed_floor": (
            "Composed safety floor for meal dosing: never dose below what a hypo-corrected basal "
            "would add. Fixes the multiplicative brake-stack defect (stateMult × velocityFactor × "
            "iobHeadroomBrake × decelerationBrake, median ~0.037) that collapses mid-meal doses to "
            "zero for 30+ minutes. Floors the dose at 25% of budget ONLY on meal-session high cycles "
            "(BG > 160, eventualBG > target+20, awake, budget > 0). Insulin-ADDING only: gated by "
            "trailing-14d TBR (below-63 < 2.0% AND below-70 < 3.5%) — fails closed if either is "
            "missing or over the bar."
        ),
        "v5_fast_carb_confirm": (
            "V5 fast-carb fast path: single-cycle IDLE/OBSERVING → CONFIRMED on a sharp, "
            "accelerating, score-corroborated rise (delta ≥ 6 mg/dL/5min, delta_accl ≥ FAST_CONFIRM_ACCL, "
            "score ≥ 0.65) while awake and not exercising. Default ON."
        ),
        "v5_aggressive_early_confirm": (
            "V5 aggressive early confirm: opens the OBSERVING → CONFIRMED age gate 2 cycles early "
            "(~10 min sooner) when the score has been ≥ CONFIRM_SCORE on consecutive cycles. "
            "Opt-in, auto-config managed."
        ),
        "v5_velocity_budget": (
            "V5 velocity-aware dose scaling: scales meal dose by the 30-min cumulative BG rise "
            "(100% for rises ≥ 50 mg/dL, 40% for rises ≤ 25 mg/dL). Slow meals get smaller doses. "
            "Toggle controls whether velocity scaling applies to the budget."
        ),
        "v5_active_dosing": (
            "V5 master gate: when ON, V5 decides and delivers meal insulin. When OFF, V5 runs as "
            "a shadow and only logs telemetry."
        ),
        "use_tdd": "Main gate: enables TDD-based dynamic ISF instead of static profile ISF.",
        "max_basal": "Maximum temporary basal rate (U/h). Shared with all OpenAPS plugins.",
        "max_daily_multiplier": "Maximum daily insulin multiplier × profile. Safety limit.",
        "max_current_basal_multiplier": "Maximum current basal rate multiplier. Prevents extreme TBRs.",
        "smb_max_iob": "Maximum IOB for SMB delivery. Capped by this limit.",
        "use_smb": "Master gate for SMB delivery. Must be ON for Boost SMBs.",
        "use_smb_always": "When ON: SMBs delivered regardless of BG trend.",
        "use_smb_with_cob": "When ON: allows SMBs when carbs-on-board are present.",
        "use_smb_after_carbs": "When ON: allows SMBs shortly after carb entry.",
        "use_smb_with_high_tt": "When ON: SMBs allowed during high BG temp targets.",
        "use_smb_with_low_tt": "When ON: SMBs allowed during low BG temp targets.",
        "max_smb_frequency": "Minimum minutes between SMB deliveries. Lower = more frequent SMBs.",
        "max_minutes_of_basal_to_limit_smb": (
            "SMB limit as minutes of basal rate. 30 = each SMB capped at 30 min worth of basal."
        ),
        "uam_max_minutes": "UAM SMB limit as minutes of basal rate.",
        "use_uam": "Enables unannounced meal detection for automatic SMB dosing.",
        "use_autosens": "Enables autosens ratio for ISF adjustment.",
        "use_dynamic_sensitivity": "Enables dynamic sensitivity (DynISF) engine.",
        "autosens_max": "Maximum autosens ratio. 1.2 = 20% more ISF (less insulin).",
        "autosens_min": "Minimum autosens ratio. 0.7 = 30% less ISF (more insulin).",
        "carbs_request_threshold": "Minimum carbs (g) to trigger a carb request notification.",
        "sensitivity_raises_target": "When ON: high sensitivity raises BG target.",
        "resistance_lowers_target": "When ON: insulin resistance lowers BG target.",
        "activity_scale_factor": "ISF multiplier when activity detected. Higher = more ISF = less insulin.",
        "inactivity_scale_factor": "ISF multiplier when inactive. Lower = less ISF = more insulin.",
        "activity_monitor_detection": "Enables phone movement-based activity detection.",
        "activity_monitor_overnight": "Keeps activity monitor active during night hours.",
        "activity_monitor_idle_start": "Start hour for idle detection window.",
        "activity_monitor_idle_end": "End hour for idle detection window.",
        "activity_monitor_use_steps": "Enables step counting for activity level.",
        "ketoacidosis_protection": "Enables DKA protection: limits or increases basal when pump suspended.",
        "activity_steps": "Step threshold for activity level classification.",
        "inactivity_steps": "Step threshold for inactivity classification.",
        "anticip_history": "Serialized meal anticipation history for ML learning.",
        "daily_step_history": "Serialized daily step history for activity trend analysis.",
        "intraday_step_bank": "Serialized intraday step bank for short-term activity tracking.",
        "isf_shadow_state": "Serialized ISF shadow state (V4.4.2 EMA diagnostic).",
        "meal_time_history": "Serialized meal time history for meal pattern learning.",
        "ml_ring_buffer": "Serialized ML feature ring buffer (6-position lookback).",
        "sleep_history": "Serialized sleep history for sleep pattern learning.",
        "sleep_state": "Current sleep state (detected/not detected).",
        "health_connect_hr_enabled": "Enables Health Connect HR data ingestion.",
        "health_connect_poll_min": "Minutes between Health Connect data polls.",
        "health_connect_last_sync": "Timestamp of last Health Connect sync (epoch ms).",
    }

    for pattern, desc in templates.items():
        if pattern.lower() in name.lower():
            return desc

    # Fallback templates
    if ptype == "Boolean":
        if "enable" in name.lower() or "use_" in name.lower():
            return f"Enables/disables {name}. Gate flag for Boost feature."
        return f"Boolean switch for {name}."
    elif ptype in ("Double", "Int"):
        return f"Used in Boost algorithm for {name}."
    elif ptype == "String":
        return f"Stored state or configuration string for {name}."
    return f"Parameter for {name}."


def generate_boost_effect(key: str, info: dict, direction: str) -> str:
    """Generate English effect description."""
    name = key.replace("key_apsboost_", "").replace("key_boost_", "")\
              .replace("openapsama_", "").replace("ApsBoost", "")
    ptype = info.get("type", "")

    if ptype == "Boolean":
        if direction == "high":
            return "Feature is ON — associated functionality runs"
        return "Feature is OFF — associated functionality is disabled"

    effect_patterns = {
        "use_tdd": ("TDD-based ISF active — ISF adapts to insulin usage",
                     "Static profile ISF — TDD changes ignored"),
        "insulin_req_pct": ("Boost delivers MORE insulin — reduces hypers faster, higher hypo risk",
                            "Boost is MORE conservative — safer but slower hyper correction"),
        "scale": ("Higher ISF = less insulin for same BG = more conservative",
                  "Lower ISF = more insulin for same BG = more aggressive"),
        "percent_scale": ("ISF scaled up = less insulin = more conservative",
                          "ISF scaled down = more insulin = more aggressive"),
        "max_iob": ("More IOB allowed — more aggressive corrections",
                    "IOB capped lower — safer, less stacking"),
        "bolus": ("Larger meal boluses allowed — faster post-meal correction",
                  "Smaller boluses — more conservative, less post-meal hypo risk"),
        "activity_pct": ("More ISF during activity = less insulin = safer",
                         "Less ISF during activity = more insulin = hypo risk"),
        "inactivity_pct": ("More ISF when sedentary = less insulin",
                           "Less ISF when sedentary = more insulin = hyper correction"),
        "night_mode": ("Stronger night-time insulin reduction",
                       "Weaker night-time reduction = tighter control, hypo risk"),
        "sleep_in_hours": ("Longer sleep window = more overnight protection",
                           "Shorter window = tighter overnight control"),
        "cumulative_smb": ("Higher 60-min cap = more SMB stacking allowed",
                           "Lower cap = less stacking, safer"),
        "dyn_isf_velocity": ("Faster ISF adaptation to changing conditions",
                             "Slower adaptation = more stable ISF"),
        "dyn_isf_adjustment": ("Inflated TDD = more aggressive ISF",
                               "Deflated TDD = more conservative ISF"),
        "autosens_max": ("Allows larger autosens sensitivity ratio = less insulin when sensitive",
                         "Limits sensitivity ratio = less reduction, more stable"),
        "max_basal": ("Higher max basal = more headroom for temporary basal increases",
                      "Lower max basal = safety limit"),
        "max_daily": ("Higher daily multiplier = allows more total daily insulin",
                      "Lower = safety limit"),
        "smb_max_iob": ("Higher max IOB for SMB = more correction insulin possible",
                        "Lower cap = safety limit for SMB stacking"),
        "autosens_min": ("Lower minimum = allows stronger ISF reduction when resistant",
                         "Higher minimum = less aggressive resistance response"),
    }

    for pattern, (high_eff, low_eff) in effect_patterns.items():
        if pattern.lower() in name.lower():
            return high_eff if direction == "high" else low_eff

    if direction == "high":
        return "Higher value — more insulin/greater effect"
    return "Lower value — less insulin/reduced effect"


def classify_boost_impact(key: str, param_type: str) -> str:
    """Classify parameter impact for Boost."""
    critical_patterns = [
        "max_iob", "bolus", "insulin_req", "cumulative_smb",
        "max_basal", "max_daily", "max_current_basal",
        "smb_max_iob", "use_smb", "use_uam",
        "hypo", "safety", "ketoacidosis",
    ]
    high_patterns = [
        "use_tdd", "adjust_sensitivity", "scale", "percent_scale",
        "dyn_isf", "activity_pct", "inactivity_pct",
        "night_mode", "sleep", "post_exercise",
        "hr_stress", "circadian_isf", "v5_", "v6_", "v7_",
        "autosens", "pre_meal",
    ]
    for p in critical_patterns:
        if p.lower() in key.lower():
            return "critical"
    for p in high_patterns:
        if p.lower() in key.lower():
            return "high"
    if "serialized" in key.lower() or "_state" in key.lower() or "_history" in key.lower():
        return "low"
    return "medium"


def classify_boost_feature_group(key: str) -> str:
    """Classify which feature group a Boost key belongs to."""
    groups = {
        "v5_": "meal_model", "v6_": "meal_model", "v7_": "meal_model",
        "meal": "meal_model", "anticip": "meal_model",
        "activity": "activity", "inactivity": "activity",
        "step": "activity", "daily_step": "activity", "intraday_step": "activity",
        "post_exercise": "post_exercise",
        "night_mode": "night_mode", "night_mode_auto": "night_mode",
        "sleep": "sleep", "wake": "sleep", "pre_sleep": "sleep",
        "hr_": "hr_stress", "health_connect": "hr_stress",
        "use_tdd": "tdd_isf", "adjust_sensitivity": "tdd_isf",
        "dyn_isf": "tdd_isf", "circadian_isf": "tdd_isf",
        "autosens_when_no_tdd": "tdd_isf",
        "scale": "tdd_isf", "percent_scale": "tdd_isf",
        "smb": "smb_delivery", "cumulative_smb": "smb_delivery",
        "bolus": "smb_delivery", "max_iob": "smb_delivery",
        "insulin_req": "smb_delivery",
        "ml_ring_buffer": "ml_features",
        "isf_shadow": "isf_shadow",
        "enable_percent_scale": "tdd_isf",
        "ketoacidosis": "safety",
        "max_basal": "shared_aaps", "max_daily": "shared_aaps",
        "max_current_basal": "shared_aaps", "smb_max_iob": "shared_aaps",
        "use_smb": "shared_aaps", "use_autosens": "shared_aaps",
        "use_dynamic": "shared_aaps", "use_uam": "shared_aaps",
        "autosens_max": "shared_aaps", "autosens_min": "shared_aaps",
        "carbs_request": "shared_aaps",
        "sensitivity_raises": "shared_aaps", "resistance_lowers": "shared_aaps",
        "activity_monitor": "activity",
    }
    for pattern, group in groups.items():
        if pattern.lower() in key.lower():
            return group
    return "shared_aaps"


# ─── Main Generator ──────────────────────────────────────────────────────────

def get_git_commit(source_root: str) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True, text=True, cwd=source_root, timeout=5
        )
        return result.stdout.strip()[:40]
    except Exception:
        return "unknown"


def generate():
    print("=== Boost Parameter Analyzer - Data Generator ===\n")

    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", default=DEFAULT_SOURCE_ROOT)
    args = parser.parse_args()

    source_root = args.source_root
    if not source_root:
        print("ERROR: Could not auto-detect source root. Pass --source-root.")
        sys.exit(1)
    if not (Path(source_root) / "plugins" / "aps").exists():
        print(f"ERROR: Source root invalid: {source_root}")
        sys.exit(1)

    commit = get_git_commit(source_root)
    print(f"Source: {source_root}")
    print(f"Commit: {commit[:8]}")

    # Step 1: Parse key definitions
    print("\n1. Parsing key definitions...")
    scanner = BoostKeyScanner(source_root)
    keys = scanner.parse_all()
    print(f"   Found {len(keys)} Boost-related keys")

    # Step 2: Scan Boost source for key usages
    print("2. Scanning Boost source code...")
    source_scanner = BoostSourceScanner(source_root)
    source_scanner.scan_all()
    print(f"   Scanned {len(source_scanner.files)} Boost source files")

    # Step 3: Build parameter knowledge base
    print("3. Building parameter knowledge base...")
    parameters = []
    orphaned_count = 0
    gate_count = 0

    for key_str, info in sorted(keys.items()):
        enum_name = info.get("name", "")
        used_in = source_scanner.find_usages(key_str, enum_name)
        info["used_in"] = used_in

        is_orphaned = (not used_in and key_str not in GATE_KEYS
                       and "_state_" not in key_str and "_history" not in key_str)
        if is_orphaned:
            orphaned_count += 1

        is_gate = key_str in GATE_KEYS
        if is_gate:
            gate_count += 1

        param = {
            "key": key_str,
            "name": enum_name,
            "type": info.get("type", "Unknown"),
            "default": info.get("default"),
            "min": info.get("min"),
            "max": info.get("max"),
            "used_in": used_in,
            "orphaned": is_orphaned,
            "negative_gate_key": None,
            "negative_gate_note": None,
            "logic_summary": generate_boost_logic_summary(key_str, info, Path(source_root)),
            "effect_high": generate_boost_effect(key_str, info, "high"),
            "effect_low": generate_boost_effect(key_str, info, "low"),
            "impact": classify_boost_impact(key_str, info.get("type", "")),
            "feature_group": classify_boost_feature_group(key_str),
            "is_gate": is_gate,
            "settings_path": BOOST_SETTINGS_PATH_OVERRIDES.get(key_str) or _get_generated_path(key_str),  # Override or from path generator
            "settings_gate": info.get("dependency"),
            # True only when the key is reachable from a Boost preference screen. Keys with
            # no path are algorithm-internal (read-only from the user's perspective, possibly
            # auto-config managed) — analyzers must not recommend changing them.
            "ui_available": bool(
                BOOST_SETTINGS_PATH_OVERRIDES.get(key_str) or _get_generated_path(key_str)
            ),
        }
        parameters.append(param)

    active_count = len(parameters) - orphaned_count
    with_used = sum(1 for p in parameters if p["used_in"])
    with_gate = sum(1 for p in parameters if p["is_gate"])
    with_path = sum(1 for p in parameters if p["settings_path"])

    print(f"   Total: {len(parameters)}")
    print(f"   Active: {active_count}")
    print(f"   Orphaned: {orphaned_count}")
    print(f"   With source usages: {with_used}")
    print(f"   Feature gates: {gate_count}")
    print(f"   With settings path: {with_path}")

    # Step 4: Build feature groups
    feature_groups_list = []
    for fg_id, fg_info in BOOST_FEATURE_GROUPS.items():
        fg_params = [p["key"] for p in parameters if p["feature_group"] == fg_id]
        feature_groups_list.append({
            "id": fg_id,
            "name": fg_info["name"],
            "description": fg_info["description"],
            "gate_key": fg_info.get("gate_key"),
            "param_count": len(fg_params),
            "key_params": fg_params[:20],
        })

    # Step 5: Build AI context
    print("4. Building AI context...")
    context_compact = build_ai_context(parameters, feature_groups_list)
    param_lookup = build_param_lookup(parameters)

    with_path_count = sum(1 for p in parameters if p["settings_path"])
    output = {
        "version": "1.0",
        "plugin": "boost",
        "source_commit": commit,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total": len(parameters),
        "active": active_count,
        "orphaned": orphaned_count,
        "with_used_in": with_used,
        "with_negative_gate": 0,
        "with_settings_path": with_path_count,
        "parameters": parameters,
        "feature_groups": {fg["id"]: fg for fg in feature_groups_list},
    }

    # Write files
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    json_path = DATA_DIR / "boost_parameters.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n✅ {json_path} ({len(parameters)} params)")

    ctx_path = DATA_DIR / "boost_context_compact.txt"
    with open(ctx_path, "w", encoding="utf-8") as f:
        f.write(context_compact)
    print(f"✅ {ctx_path} ({len(context_compact)} chars)")

    lookup_path = DATA_DIR / "boost_param_lookup.json"
    with open(lookup_path, "w", encoding="utf-8") as f:
        json.dump(param_lookup, f, indent=2, ensure_ascii=False)
    print(f"✅ {lookup_path} ({len(param_lookup)} params)")

    # Also write the feature groups as context for AI
    ctx_json_path = DATA_DIR / "boost_context_for_ai.json"
    ctx_json = {
        "plugin": "boost",
        "algorithm_overview": (
            "The BOOST (Blood Glucose Optimized Open Source Tuning) plugin is a "
            "dynamic ISF engine that computes insulin sensitivity from total daily dose "
            "(TDD) instead of a static profile value. It blends 7-day, 1-day, 24-hour, "
            "8-hour and 4-hour TDD windows with a weighted formula, adjusts for temp targets, "
            "circadian rhythm, activity level, sleep state, heart rate stress, and post-exercise "
            "recovery. A parallel V4.4.2-style EMA(τ=3h) ISF shadow runs for comparison but "
            "does not affect dosing. The V5/V6/V7 meal model adds multi-version meal hypothesis "
            "with committed/confirmed cap thresholds, pre-meal targeting, and anticipation learning."
        ),
        "feature_groups": {fg["id"]: fg for fg in feature_groups_list},
        "parameter_summaries": param_lookup,
        "tuning_guidelines": (
            "1. Start with boost_use_tdd=ON and boost_insulin_req_pct=100 (neutral). "
            "2. If TIR < 70% and mean BG > 160: increase insulinReqPct to 105-115%. "
            "3. If timeBelow70 > 3%: decrease insulinReqPct to 85-95% or raise maxIob. "
            "4. If post-meal spikes > 250: diagnose first. Raising caps (confirmed/committed) is a "
            "CEILING change and does nothing when the dose collapses to zero — the common cause is "
            "the multiplicative brake stack (stateMult × velocity × iobHeadroom × deceleration, "
            "median ~0.037). That is what boost_v5_composed_floor_active fixes. Timing fixes: "
            "boost_v5_aggressive_early_confirm, boost_v5_fast_carb_confirm, and a non-zero "
            "boost_v5_primer_cap_u (0 = primer off). Announcing carbs raises baseInsulinReq "
            "(the budget input) immediately, before any BG rise is visible. "
            "5. If overnight hypos: enable night mode, set start=22:00, end=07:00, try bgOffset=10. "
            "6. If exercise causes hypos: enable post-exercise recovery, set scale=0.5, hours=3. "
            "7. If too conservative overall: check ApsBoostPercentScale (try 110%) and dynIsfAdjustmentFactor. "
            "8. Monitor ISF shadow vs real ISF divergence — large gaps suggest TDD data quality issues."
        ),
    }
    ctx_json_path = DATA_DIR / "boost_context_for_ai.json"
    with open(ctx_json_path, "w", encoding="utf-8") as f:
        json.dump(ctx_json, f, indent=2, ensure_ascii=False)
    print(f"✅ {ctx_json_path}")

    # Validation
    print("\n=== Validation ===")
    print(f"✅ Active params:  {active_count}")
    print(f"✅ Source usages:  {with_used}")
    print(f"✅ Feature groups: {len(feature_groups_list)}")
    print(f"✅ AI context:     {len(context_compact)} chars, {len(param_lookup)} params")

    return 0


def build_ai_context(parameters: list, feature_groups: list) -> str:
    """Build compact AI prompt text."""
    lines = ["BOOST Plugin Algorithm Context", "=" * 40, ""]

    # Feature groups
    lines.append("## Feature Groups")
    for fg in feature_groups:
        lines.append(f"\n### {fg['name']} (gate: {fg.get('gate_key', 'none')})")
        lines.append(fg["description"])
        if fg.get("key_params"):
            lines.append(f"Key parameters: {', '.join(fg['key_params'][:10])}")

    # Key parameters with logic summaries
    lines.append("\n\n## Key Parameters")
    for p in parameters:
        if p.get("orphaned"):
            continue
        gate = " [GATE]" if p.get("is_gate") else ""
        lines.append(f"\n### {p['key']}{gate}")
        lines.append(f"Type: {p['type']}, Default: {p.get('default', 'N/A')}")
        if p.get("min") is not None:
            lines.append(f"Range: {p['min']} – {p.get('max', '∞')}")
        lines.append(f"Summary: {p['logic_summary']}")
        lines.append(f"High: {p['effect_high']}")
        lines.append(f"Low: {p['effect_low']}")
        lines.append(f"Impact: {p['impact']} | Feature: {p['feature_group']}")

    return "\n".join(lines)


def build_param_lookup(parameters: list) -> dict:
    """Build quick lookup of parameter key -> logic summary."""
    return {
        p["key"]: {
            "summary": p["logic_summary"],
            "effect_high": p["effect_high"],
            "impact": p["impact"],
            "feature": p["feature_group"],
            "settings_path": p.get("settings_path"),
            # Algorithm-internal keys (no settings UI) must not be recommended as
            # user-changeable. Auto-config may write them, but the user can't.
            "ui_available": p.get("ui_available", False),
        }
        for p in parameters
        if not p.get("orphaned")
    }


if __name__ == "__main__":
    sys.exit(generate())
