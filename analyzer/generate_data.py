#!/usr/bin/env python3
"""
AIMI Analyzer Data File Generator
==================================
Scans the AAPS source code for AIMI-related parameters and regenerates:
  data/aimi_parameters.json       — Full parameter knowledge base
  data/aimi_context_for_ai.json   — Feature descriptions, algorithm overview
  data/aimi_context_compact.txt   — Compact text for AI prompts
  data/aimi_param_lookup.json     — Quick lookup of parameter summaries

Usage:
  python3 generate_data.py [--source-root /path/to/OpenApsAIMI_V4]
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

# Auto-detect source root: when generate_data.py lives at <repo>/analyzer/,
# the repo root is the parent directory.
_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT  = _SCRIPT_DIR.parent

DEFAULT_SOURCE_ROOT = str(_AUTO_ROOT) if (_AUTO_ROOT / "plugins" / "aps").exists() else None

DATA_DIR = Path(__file__).resolve().parent / "data"

# AIMI source directory (relative to source root)
AIMI_SRC = "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI"

# Key definition files (relative to source root)
KEY_FILES = [
    "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/LongKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
]

# AIMI key prefixes for filtering
AIMI_PREFIXES = [
    "key_openapsaimi", "key_aimi", "aimi_", "OApsAIMI",
    "key_oaps_aimi", "key_use_Aimi", "key_use_aimi",
    "key_prebolus", "key_prebolus2", "key_prebolussmall",
    "key_combinedDelta", "key_mindeviation", "key_Acceleration",
    "key_cho", "key_aimiweight", "key_tdd7", "key_wcycle",
    "count_steps_watch", "AIMI_UAM", "oa_aimi_",
    "key_enable_basal", "key_enable_ML",
    # Also capture upstream keys used by AIMI
    "openapsama_", "openapsma_", "openapsmb_", "openaps_smb_",
    "autosens_", "absorption_", "bolussnooze_",
    "activity_scale_factor", "inactivity_scale_factor",
    "Overview", "overview_", "equil_",
    "ActionsFill", "treatmentssafety_",
    "bgAccel_", "bgBrake_", "lower_ISFrange", "higher_ISFrange",
    "pp_ISF", "dura_ISF", "autoISF_",
    "lgsThreshold", "half_basal_exercise_target",
    "low_mark", "high_mark", "eatingsoon_target",
    "activity_target", "hypo_target",
]

# Boolean-like values that represent gates
GATE_KEYS = {
    "key_use_Aimi_autoDrive",
    "key_use_aimi_autodrive_active",
    "key_aimi_autodrive_v3_authoritative",
    "key_aimi_t3c_brittle_mode",
    "key_use_aimi_t3c_adaptive_basal",
    "key_aimi_pkpd_enabled",
    "key_aimi_pkpd_pragmatic_relief_enabled",
    "key_aimi_straight_line_tube_enabled",
    "key_aimi_trajectory_guard_enabled",
    "key_aimi_recursive_belief_shadow",
    "key_aimi_recursive_belief_authority",
    "key_aimi_recursive_belief_wavelet",
    "key_aimi_iob_surveillance_guard",
    "key_oaps_aimi_ngr_enabled",
    "key_aimi_hyper_trajectory_release",
    "key_aimi_hyper_trajectory_release_aggressive",
    "key_aimi_peak_governor_enabled",
    "key_aimi_tpo_enabled",
    "key_aimi_tpo_llm_confirm_enabled",
    "key_aimi_tpo_notify_on_apply",
    "key_aimi_smb_comparator_enabled",
    "key_aimi_context_enabled",
    "key_aimi_context_llm_enabled",
    "key_aimi_loop_blackbox_file_enabled",
    "key_aimi_loop_exclusive_invocation",
    "key_aimi_thyroid_enabled",
    "key_aimi_thyroid_debug",
    "key_aimi_pkpd_setup_wizard_completed",
    "key_aimi_intratick_stall_seconds",
    "key_use_AimiForceLimits",
    "key_use_AimiPregnancy",
    "key_use_Aimi_honeymoon",
    "key_use_Aimi_wcycle",
    "key_use_Aimi_wcycle_require_confirm",
    "key_use_Aimi_wcycle_shadow",
    "key_use_Aimi_xdripOM",
    "key_use_aimi_autodrive_v3_enhanced_gater",
    "aimi_physio_assistant_enable",
    "aimi_physio_hrv_enable",
    "aimi_physio_sleep_enable",
    "aimi_physio_llm_enable",
    "aimi_physio_debug_logs",
    "aimi_emergency_sos_enable",
    "aimi_endo_enable",
    "aimi_endo_flare",
    "aimi_dyn_isf_trajectory_tuning_enabled",
    "aimi_dyn_isf_trajectory_shadow_only",
    "aimi_cosine_gate_enabled",
    "aimi_auditor_enabled",
    "aimi_meal_advisor_trigger",
    "key_aimi_advisor_llm_rich_oref",
    "key_aimi_advisor_personal_oref_ml",
}

# Known bugs to document in the context
KNOWN_BUGS = [
    {
        "id": "BUG_001",
        "name": "LastLegacyPrebolusTime Math.max loop",
        "affected_key": "aimi_last_legacy_prebolus_time",
        "symptom": "Prebolus system keeps finding old delivery confirmations, wasting CPU",
        "technical": "internalLastLegacyPrebolusMillis getter uses Math.max(memValue, storedValue). "
                      "A very old timestamp can never be reset — Math.max always returns "
                      "the larger value. Thus pendingLegacyPrebolusUnit > 0 && "
                      "internalLastLegacyPrebolusMillis > 0L is permanently true.",
        "workaround": "None (ADB reset fails on release builds). "
                      "Proposed fix: getter should ignore timestamps older than 24h (set to 0L)."
    },
    {
        "id": "BUG_002",
        "name": "OApsAIMIautoDrive is deprecated but still in code",
        "affected_key": "key_use_Aimi_autoDrive",
        "symptom": "Autodrive v3 does not activate even though key_use_aimi_autodrive_active=true",
        "technical": "BooleanKey.OApsAIMIautoDrive (key_use_Aimi_autoDrive) is the old Autodrive v2 gate. "
                      "Autodrive v3 uses OApsAIMIautoDriveActive. The old key is still read in "
                      "buildPreTherapyAutodriveByodaBootstrap() and blocks v3 when false.",
        "workaround": "Set key_use_Aimi_autoDrive=true even if you only want to use v3."
    },
    {
        "id": "BUG_003",
        "name": "Dead variables pbolusAS / pbolusA",
        "affected_keys": ["key_prebolus_autodrive_mode", "key_prebolussmall_autodrive_mode"],
        "symptom": "Changing autodrivePrebolus / autodrivesmallPrebolus has no effect",
        "technical": "In runSignalPreparationPkpdRuntimePhase(), DoubleKey.OApsAIMIautodrivesmallPrebolus "
                      "and DoubleKey.OApsAIMIautodrivePrebolus are read, but the results (pbolusAS, pbolusA) "
                      "are never used afterward — dead variables.",
        "workaround": "None — parameters are ineffective."
    },
]

# Verified parameters suppressed when T3c Brittle Mode is active.
# Confirmed by grepping executeT3cBrittleMode() in DetermineBasalAIMI2.kt —
# the function only sets TBR via PI controller and never touches SMB/ISF parameters.
T3C_SUPPRESSED_PARAMS = {
    "key_openapsaimi_max_smb",
    "key_openapsaimi_high_bg_max_smb",
    "aimi_smb_tail_damping",
    "aimi_smb_tail_threshold",
    "aimi_smb_exercise_damping",
    "aimi_smb_late_fat_damping",
    "aimi_isf_fusion_min_factor",
    "aimi_isf_fusion_max_factor",
    "aimi_isf_fusion_max_change_per_tick",
}

# Known settings path overrides for params not reachable by path-scanner
SETTINGS_PATH_OVERRIDES = {
    "aimi_pkpd_anchor_dia_h":             "AIMI → Preferences user → Adaptive PK/PD",
    "aimi_pkpd_anchor_peak_min":          "AIMI → Preferences user → Adaptive PK/PD",
    "key_oaps_aimi_lunch_factor":         "AIMI → Preferences user → Manual Mode → Lunch",
    "key_oaps_aimi_ngr_max_smb_clamp":    "AIMI → Preferences user → Night growth resistance",
    "key_aimi_t3c_aggressiveness":        "AIMI → Preferences user → T3C Brittle Mode Settings",
    "key_aimi_adaptive_basal_max_scaling": "AIMI → Preferences user → T3C Brittle Mode Settings",
    "key_oaps_aimi_highBG_interval":      "AIMI → Preferences user → T3C Brittle Mode Settings",
    "count_steps_watch":                  "AIMI → Preferences user → Physiological Assistant",
    "key_use_AimiForceLimits":            "AIMI → Preferences user → Core parameters",
    "key_use_Aimi_autoDrive":             "AIMI → Preferences user → Autodrive",
    "key_aimi_smb_comparator_enabled":    "AIMI → Advanced Settings",
    "aimi_tuning_context_selection":      "AIMI → Preferences user → Assistant AI",
}

# Keys that intentionally have no user-facing settings path (state vars, etc.)
NO_SETTINGS_PATH_KEYS = {
    "oa_aimi_last_prebolus_time_ms", "aimi_last_legacy_prebolus_time",
    "aimi_pkpd_state_last_peak_gov_log", "aimi_pkpd_state_last_peak_gov_console_echoed",
    "aimi_pkpd_state_dia_h", "aimi_pkpd_state_peak_min", "aimi_pkpd_state_prior_peak",
    "aimi_pkpd_state_physio_peak", "aimi_pkpd_state_site_peak",
    "aimi_pkpd_state_traj_peak", "aimi_pkpd_state_effective_peak",
    "OApsAIMILastEstimatedCarbs", "OApsAIMILastEstimatedCarbTime",
    "key_oaps_aimi_mode_state", "aimi_context_storage", "aimi_pkpd_state_dominant_branch",
}


# ─── Utility Functions ────────────────────────────────────────────────────────

def is_aimi_key(key: str) -> bool:
    """Check if a key is AIMI-related."""
    return any(key.startswith(p) or p.lower() in key.lower() for p in AIMI_PREFIXES)


def get_git_commit(source_root: str) -> str:
    """Get the current git commit hash."""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True, text=True, cwd=source_root, timeout=5
        )
        return result.stdout.strip()[:40]
    except Exception:
        return "unknown"


# ─── Kotlin Enum Parser ───────────────────────────────────────────────────────

class KotlinEnumParser:
    """Parses Kotlin enum classes to extract key definitions."""

    def __init__(self, source_root: str):
        self.source_root = Path(source_root)
        self.keys: Dict[str, dict] = {}

    def parse_all(self):
        """Parse all key definition files."""
        for kf in KEY_FILES:
            path = self.source_root / kf
            if path.exists():
                self._parse_file(path)
        return self.keys

    def _parse_file(self, filepath: Path):
        """Parse a single key definition file."""
        content = filepath.read_text(encoding="utf-8")

        # Determine the type from the class name
        type_map = {
            "DoubleKey": "Double",
            "BooleanKey": "Boolean",
            "IntKey": "Int",
            "UnitDoubleKey": "UnitDouble",
            "LongKey": "Long",
            "StringKey": "String",
        }

        # Find the enum class declaration
        class_match = re.search(
            r'enum\s+class\s+(\w+)\s*\(?\s*([\s\S]*?)\)?\s*:\s*(\w+)',
            content
        )
        if not class_match:
            return

        class_name = class_match.group(1)
        param_type = type_map.get(class_name, "Unknown")

        # Extract constructor parameter defaults
        defaults = self._extract_defaults(content)

        # Find all enum entries
        # Pattern: Name(key="xxx", ...) or Name("xxx", ...)
        entries = re.finditer(
            r'([A-Z][A-Za-z0-9]+)\s*\(\s*((?:[^()]|\([^)]*\))*?)\s*\)\s*,?\s*(?://[^\n]*)?\s*$',
            content, re.MULTILINE
        )

        for match in entries:
            name = match.group(1)
            args_str = match.group(2)

            # Skip if this is the class declaration itself
            if name in ("DoubleKey", "BooleanKey", "IntKey", "UnitDoubleKey",
                         "LongKey", "StringKey"):
                continue

            entry = self._parse_entry_args(name, args_str, param_type, defaults)
            if entry and is_aimi_key(entry.get("key", "")):
                self.keys[entry["key"]] = entry

    def _extract_defaults(self, content: str) -> dict:
        """Extract default values from constructor parameters."""
        defaults = {}
        for match in re.finditer(
            r'override\s+(?:val|var)\s+(\w+)\s*:\s*(\S+)\s*=\s*([^,\n)]+)',
            content
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

    def _parse_entry_args(self, name: str, args: str, ptype: str,
                          defaults: dict) -> Optional[dict]:
        """Parse the arguments of a single enum entry."""
        entry = {"name": name, "type": ptype}

        # Try key=value pattern first
        key_match = re.search(r'key\s*=\s*"([^"]+)"', args)
        if key_match:
            entry["key"] = key_match.group(1)
            named_args = dict(re.findall(r'(\w+)\s*=\s*([^,]+(?:\([^)]*\))?)', args))
        else:
            # Positional arguments
            # First match: quoted string or unquoted
            pos_args = re.findall(r'([^,]+(?:\([^)]*\))?)', args)
            # Try first quoted arg as key
            cleaned = [a.strip() for a in pos_args]
            if cleaned and cleaned[0].startswith('"'):
                entry["key"] = cleaned[0].strip('"')
            else:
                return None
            named_args = {}

        # Extract min/max/default for numeric types
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

        # Extract defaultValue for Boolean
        if ptype == "Boolean":
            if "defaultValue" in named_args:
                entry["default"] = named_args["defaultValue"].strip().lower() == "true"

        # Extract dependency
        dep_match = re.search(
            r'dependency\s*=\s*(?:BooleanKey\.)?(\w+)', args
        )
        if dep_match:
            entry["dependency"] = dep_match.group(1)

        # Extract negative dependency
        neg_dep_match = re.search(
            r'negativeDependency\s*=\s*(?:BooleanKey\.)?(\w+)', args
        )
        if neg_dep_match:
            entry["negative_dependency"] = neg_dep_match.group(1)

        # Extract unit type
        unit_match = re.search(r'unitType\s*=\s*UnitType\.(\w+)', args)
        if unit_match:
            entry["unit_type"] = unit_match.group(1)

        # Extract step
        step_match = re.search(r'step\s*=\s*([\d.]+)', args)
        if step_match:
            entry["step"] = self._parse_number(step_match.group(1))

        # Extract defaultedBySM / calculatedBySM
        entry["defaulted_by_sm"] = "defaultedBySM = true" in args
        entry["calculated_by_sm"] = "calculatedBySM = true" in args

        # Extract show flags
        entry["show_in_aps_mode"] = "showInApsMode = false" not in args
        entry["show_in_ns_client_mode"] = "showInNsClientMode = false" not in args

        # Extract title/summary resource IDs
        title_match = re.search(r'titleResId\s*=\s*R\.string\.(\w+)', args)
        if title_match:
            entry["title_res_id"] = title_match.group(1)

        summary_match = re.search(r'summaryResId\s*=\s*R\.string\.(\w+)', args)
        if summary_match:
            entry["summary_res_id"] = summary_match.group(1)

        # Extract preference type
        pref_match = re.search(r'preferenceType\s*=\s*PreferenceType\.(\w+)', args)
        if pref_match:
            entry["preference_type"] = pref_match.group(1)

        return entry

    @staticmethod
    def _parse_number(s: str) -> float:
        """Parse a number from a string, handling Kotlin suffixes."""
        s = s.strip().rstrip('f').rstrip('F').rstrip('L').rstrip('d').rstrip('D')
        try:
            if '.' in s:
                return float(s)
            return int(s)
        except ValueError:
            return 0.0


# ─── Source Code Scanner ─────────────────────────────────────────────────────

class SourceScanner:
    """Scans AIMI source code to find where each key is used."""

    def __init__(self, source_root: str):
        self.source_root = Path(source_root)
        self.aimi_dir = self.source_root / AIMI_SRC
        # Cache all file contents
        self.files: Dict[str, str] = {}

    def scan_all(self):
        """Scan all AIMI source files and cache contents."""
        if not self.aimi_dir.exists():
            print(f"WARNING: AIMI source directory not found: {self.aimi_dir}")
            return
        for kt_file in self.aimi_dir.rglob("*.kt"):
            relative = str(kt_file.relative_to(self.aimi_dir))
            self.files[relative] = kt_file.read_text(encoding="utf-8")

    def find_usages(self, key: str, enum_name: str = "") -> List[dict]:
        """Find all usages of a key in the AIMI source code.

        Searches for both the key string and the enum constant name.
        """
        usages = []
        patterns = [f'"{key}"']

        # Also search by enum constant name
        if enum_name:
            patterns.append(f".{enum_name}")  # e.g., DoubleKey.OApsAIMIHighBg
            patterns.append(f"DoubleKey.{enum_name}")
            patterns.append(f"BooleanKey.{enum_name}")
            patterns.append(f"IntKey.{enum_name}")
            patterns.append(f"UnitDoubleKey.{enum_name}")
            patterns.append(f"LongKey.{enum_name}")
            patterns.append(f"StringKey.{enum_name}")
            # Simple name reference in AIMI code
            patterns.append(enum_name)

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

        return usages[:5]  # Limit to 5 usages per key

    def _find_enclosing_function(self, content: str, pos: int) -> Optional[str]:
        """Find the function name that encloses a position."""
        # Search backwards for "fun functionName"
        before = content[:pos]
        func_matches = list(re.finditer(
            r'(?:private\s+|internal\s+|override\s+)*fun\s+(\w+)',
            before
        ))
        if func_matches:
            return func_matches[-1].group(1)
        return "unknown"


# ─── Feature Description Builder ──────────────────────────────────────────────

FEATURE_DESCRIPTIONS = {
    "autodrive_v3": {
        "description": (
            "MPC-like controller activated by the AutoDriveGater when BG>120 and rising or meal context. "
            "Gater checks: HR<140, step count, COB, UAM confidence. Engages in 5 states (DISENGAGED, ENGAGED, "
            "POST_HYPO_RECOVERY, SAFETY_HOLD, LEARNING). If engaged: builds AutoDriveState with physiological "
            "phase, HTR classification, and meal absorption phase. Computes optimal SMB via MPC with insulin "
            "cost, SMB fraction limits, and scenario projection. If authoritative: sets skipLegacySmbBlender=true, "
            "bypassing executeSmbInstruction entirely. V3 has priority lockout over V2."
        ),
        "gate_key": "key_use_aimi_autodrive_active",
        "key_params": [
            "key_aimi_autodrive_v3_authoritative",
            "key_aimi_hyper_trajectory_release",
            "aimi_mpc_insulin_u_per_kg_per_5min",
            "key_aimi_recursive_belief_authority",
            "key_use_aimi_autodrive_v3_enhanced_gater",
        ],
    },
    "legacy_meal_modes": {
        "description": (
            "P1/P2 prebolus windows with persistent lockout (30 min delivery TTL). "
            "P1 checks hypo credibility, P2 checks short-term dominance in the Recursive Belief Resolver. "
            "Enables pump-independent retries on BLE failures. Three-layer safety net."
        ),
        "gate_key": None,
        "key_params": [
            "key_prebolus_BF_mode", "key_prebolus2_BF_mode",
            "key_prebolus_meal_mode", "key_prebolus_lunch_mode",
            "key_prebolus2_lunch_mode", "key_prebolus_dinner_mode",
            "key_prebolus2_dinner_mode", "key_prebolus_snack_mode",
            "key_prebolus_highcarb_mode", "key_prebolus_highcarb_mode2",
            "key_prebolus_autodrive_mode", "key_prebolussmall_autodrive_mode",
            "oa_aimi_last_prebolus_time_ms", "aimi_last_legacy_prebolus_time",
        ],
    },
    "t3c_brittle_mode": {
        "description": (
            "Special mode for pancreatogenic type-3c diabetics. Bypasses standard AIMI algorithm "
            "and uses a dedicated PI controller with parabolic projection and resistance factor. "
            "Only activates at BG >= thresholds. Triple-layer prebolus protection. "
            "NO SMB delivery — TBR only via PI controller."
        ),
        "gate_key": "key_aimi_t3c_brittle_mode",
        "key_params": [
            "key_use_aimi_t3c_adaptive_basal",
            "key_aimi_t3c_activation_threshold",
            "key_aimi_t3c_anticipation_strength",
            "key_aimi_t3c_aggressiveness",
            "key_aimi_t3c_cfrd_lgs_floor",
            "key_aimi_t3c_cfrd_cob_delay_min",
            "key_aimi_adaptive_basal_max_scaling",
        ],
    },
    "ngr": {
        "description": (
            "Nocturnal growth resistance detection for paediatric patients (< 18 years). "
            "State machine (INACTIVE → SUSPECTED → CONFIRMED → DECAY) monitors delta, "
            "short/longAvgDelta and eventualBG in night window. "
            "Assigns SMB and basal multipliers plus extra IOB headroom."
        ),
        "gate_key": "key_oaps_aimi_ngr_enabled",
        "key_params": [
            "key_oaps_aimi_ngr_min_rise_slope",
            "key_oaps_aimi_ngr_smb_multiplier",
            "key_oaps_aimi_ngr_basal_multiplier",
            "key_oaps_aimi_ngr_max_smb_clamp",
            "key_oaps_aimi_ngr_max_iob_extra",
            "key_oaps_aimi_ngr_min_duration",
            "key_oaps_aimi_ngr_min_eventual_over_target",
            "key_oaps_aimi_ngr_decay_minutes",
            "key_oaps_aimi_ngr_age_years",
        ],
    },
    "pkpd": {
        "description": (
            "Real-time PKPD model with Weibull-based insulin action curves, adaptive estimator "
            "and InsulinActionProfiler. Computes fusedIsf from profile ISF, dynamic ISF and "
            "learned values. PkpdAbsorptionGuard dampens SMB during limited absorption. "
            "IOB consensus reconciles AAPS IOB with PKPD IOB."
        ),
        "gate_key": "key_aimi_pkpd_enabled",
        "key_params": [
            "aimi_pkpd_initial_dia_h", "aimi_pkpd_initial_peak_min",
            "aimi_pkpd_anchor_dia_h", "aimi_pkpd_anchor_peak_min",
            "aimi_pkpd_bounds_dia_min_h", "aimi_pkpd_bounds_dia_max_h",
            "aimi_pkpd_bounds_peak_min_min", "aimi_pkpd_bounds_peak_min_max",
            "aimi_pkpd_max_dia_change_per_day_h", "aimi_pkpd_max_peak_change_per_day_min",
            "aimi_pkpd_state_dia_h", "aimi_pkpd_state_peak_min",
            "aimi_pkpd_state_prior_peak", "aimi_pkpd_state_physio_peak",
            "aimi_pkpd_state_site_peak", "aimi_pkpd_state_traj_peak",
            "aimi_pkpd_state_effective_peak",
            "aimi_pkpd_pragmatic_relief_min_factor",
        ],
    },
    "unified_reactivity": {
        "description": (
            "ML-powered learner replacing old time-based bucket system. Analyses TIR 70-180, "
            "CV%, hypo count and oscillations. Combines globalFactor (24h, 60% weight) with "
            "shortTermFactor (2h, 40%) into total factor (0.5-1.5)."
        ),
        "gate_key": "key_use_Aimi_UnifiedReactivityLearner",
        "key_params": [],
    },
    "iob_surveillance": {
        "description": (
            "Monitors IOB utilisation and prevents insulin stacking. Red Carpet mechanism "
            "restores SMB doses reduced by safety mechanisms. capSmbDose() limits SMB based "
            "on IOB headroom."
        ),
        "gate_key": "key_aimi_iob_surveillance_guard",
        "key_params": [
            "aimi_red_carpet_restore_threshold",
            "aimi_priority_max_iob_factor",
            "aimi_priority_max_iob_extra_u",
        ],
    },
    "trajectory_guard": {
        "description": (
            "Analyses insulin-glucose dynamics as geometric trajectory in phase space. "
            "Generates modulation factors: OPEN_DIVERGING→aggressive, CLOSING_CONVERGING→less, "
            "TIGHT_SPIRAL→strong damping, STABLE_ORBIT→minimal intervention."
        ),
        "gate_key": "key_aimi_trajectory_guard_enabled",
        "key_params": [
            "key_aimi_straight_line_tube_enabled",
        ],
    },
    "basal_first_policy": {
        "description": (
            "Disables SMB completely and uses TBR only when BG < 110 and either "
            "isLearnerPrudent or isFragileBg. Not active during confirmedHighRise or BG >= 110."
        ),
        "gate_key": None,
        "key_params": [],
    },
    "recursive_belief": {
        "description": (
            "Multi-scale recursive belief tree (RBT) with MR-7 clause evaluation at 4 time "
            "scales (15/60/180/480 min). 12 active belief leaves. Resolves through credibility "
            "cascade and tension analysis."
        ),
        "gate_key": "key_aimi_recursive_belief_shadow",
        "key_params": [
            "key_aimi_recursive_belief_authority",
            "key_aimi_recursive_belief_wavelet",
        ],
    },
    "scenario_projection": {
        "description": (
            "Produces two authoritative prediction curves per tick: CLINICAL_FLOOR (pessimistic) "
            "and SCENARIO_BEST (realistic, fuses 7 layers). Maps to RT.predBGs."
        ),
        "gate_key": None,
        "key_params": [],
    },
    "risk_envelope": {
        "description": (
            "Two immutable risk snapshots per tick (EARLY and DECISION). IOB consensus resolves "
            "PKPD vs AAPS IOB. Composite min BG computes overall floor."
        ),
        "gate_key": None,
        "key_params": [],
    },
    "meal_absorption_phase": {
        "description": (
            "Single cross-tick state machine (NONE→FIRST_WAVE→PEAK_CORRECTION→INTER_WAVE→"
            "SECOND_WAVE→LATE_FAT) unifying meal absorption context with phase-aware "
            "IOB surveillance bypass and HTR modulation."
        ),
        "gate_key": None,
        "key_params": [],
    },
    "physiological_phase": {
        "description": (
            "Classifies current physiological phase from BG, delta, HR, steps, WCycle phase, "
            "HTR tier. Each phase carries a BehavioralRiskPolicy with maxHtrTier, "
            "smbFloorCapU, mpcInsulinCostMultiplier, mpcMaxSmbFraction."
        ),
        "gate_key": "aimi_physio_assistant_enable",
        "key_params": [
            "aimi_physio_hrv_enable", "aimi_physio_sleep_enable",
            "aimi_physio_llm_enable", "aimi_physio_debug_logs",
        ],
    },
    "governance": {
        "description": (
            "Adaptive basal governance system for T3c and universal adaptive basal. "
            "Manages hypo detection (enter/exit rates, BG thresholds), hold states "
            "(basal/agg floor rates and decay rates for standard and severe hypo), "
            "and anticipation logic with lookback windows and margin parameters."
        ),
        "gate_key": "key_use_aimi_t3c_adaptive_basal",
        "key_params": [
            "key_aimi_gov_hypo_rate_enter", "key_aimi_gov_hypo_rate_exit",
            "key_aimi_gov_hypo_bg_mgdl", "key_aimi_gov_severe_hypo_bg_mgdl",
            "key_aimi_gov_hold_basal_floor_rate", "key_aimi_gov_hold_basal_decay_rate",
            "key_aimi_gov_hold_agg_floor_rate", "key_aimi_gov_hold_agg_decay_rate",
            "key_aimi_gov_hold_basal_floor_severe", "key_aimi_gov_hold_basal_decay_severe",
            "key_aimi_gov_hold_agg_floor_severe", "key_aimi_gov_hold_agg_decay_severe",
            "key_aimi_gov_anticipation_lookback_samples",
            "key_aimi_gov_anticipation_margin_mgdl",
            "key_aimi_gov_anticipation_hypo_damp",
            "key_aimi_gov_anticipation_decay_blend_max",
        ],
    },
    "tube_advisor": {
        "description": (
            "MPC-lite SMB cap regulator computing a PKPD tube that limits SMBs falling "
            "outside safe bounds. Controls hypo floor, hyper band, aggressiveness, "
            "basal trim max, and kappa safety margin."
        ),
        "gate_key": "key_aimi_straight_line_tube_enabled",
        "key_params": [
            "key_aimi_tube_hypo_floor_mgdl", "key_aimi_tube_hyper_band_mgdl",
            "key_aimi_tube_aggressiveness", "key_aimi_tube_basal_trim_max",
            "key_aimi_tube_kappa_margin",
        ],
    },
    "endometriosis": {
        "description": (
            "Endometriosis & cycle management. Adjusts basal multiplier and SMB dampening "
            "during flare phases. Configurable flare duration and suppression window."
        ),
        "gate_key": "aimi_endo_enable",
        "key_params": [
            "aimi_endo_basal_mult", "aimi_endo_smb_dampen",
            "aimi_endo_flare_duration", "aimi_endo_suppression",
        ],
    },
    "cosine_gate": {
        "description": (
            "Adaptive Kernel Bank using cosine similarity gates to modulate sensitivity "
            "based on data quality and physiological context. "
            "Alpha controls gate sharpness, min/max sensitivity define bounds."
        ),
        "gate_key": "aimi_cosine_gate_enabled",
        "key_params": [
            "aimi_cosine_gate_alpha", "aimi_cosine_gate_min_dq",
            "aimi_cosine_gate_min_sens", "aimi_cosine_gate_max_sens",
            "aimi_cosine_gate_max_shift",
        ],
    },
    "dyn_isf_trajectory": {
        "description": (
            "Dynamic ISF trajectory tuning that adjusts ISF based on trajectory phase. "
            "Max fraction controls how much ISF can be modified per qualifying tick."
        ),
        "gate_key": "aimi_dyn_isf_trajectory_tuning_enabled",
        "key_params": [
            "aimi_dyn_isf_trajectory_max_fraction",
            "aimi_dyn_isf_trajectory_shadow_only",
        ],
    },
    "peak_governor": {
        "description": (
            "Peak Governor learns and adjusts insulin peak timing. "
            "Learned weight blends observed peak data with prior estimates."
        ),
        "gate_key": "key_aimi_peak_governor_enabled",
        "key_params": [
            "aimi_peak_governor_learned_weight",
        ],
    },
    "ism_fusion": {
        "description": (
            "ISF Fusion blends profile ISF, TDD ISF and PKPD scale into unified ISF "
            "with configurable min/max bounds and per-tick change limits."
        ),
        "gate_key": None,
        "key_params": [
            "aimi_isf_fusion_min_factor", "aimi_isf_fusion_max_factor",
            "aimi_isf_fusion_max_change_per_tick",
        ],
    },
    "tpo": {
        "description": (
            "Transient Preference Overlay (TPO) system. Allows temporary parameter overrides "
            "with optional LLM confirmation and notification on apply."
        ),
        "gate_key": "key_aimi_tpo_enabled",
        "key_params": [
            "key_aimi_tpo_llm_confirm_enabled", "key_aimi_tpo_notify_on_apply",
        ],
    },
    "thyroid": {
        "description": (
            "Thyroid module for hormone-aware insulin sensitivity adjustments. "
            "Debug mode enables additional logging."
        ),
        "gate_key": "key_aimi_thyroid_enabled",
        "key_params": ["key_aimi_thyroid_debug"],
    },
    "sos_emergency": {
        "description": (
            "Emergency SOS detection for critically stale or dangerous glucose conditions. "
            "Configurable thresholds for immediate, stale, and standard alerts."
        ),
        "gate_key": "aimi_emergency_sos_enable",
        "key_params": [
            "aimi_emergency_sos_immediate_threshold",
            "aimi_emergency_sos_stale_threshold",
            "aimi_emergency_sos_threshold",
        ],
    },
    "auditor": {
        "description": (
            "Auditor module monitors algorithm decisions and provides confidence scoring. "
            "Configurable max checks per hour, min confidence threshold, and timeout."
        ),
        "gate_key": "aimi_auditor_enabled",
        "key_params": [
            "aimi_auditor_max_per_hour", "aimi_auditor_min_confidence",
            "aimi_auditor_timeout_seconds",
        ],
    },
    "context_llm": {
        "description": (
            "Context-aware LLM integration for enriched decision making."
        ),
        "gate_key": "key_aimi_context_enabled",
        "key_params": ["key_aimi_context_llm_enabled"],
    },
    "loop_config": {
        "description": (
            "Loop execution configuration: exclusive invocation mode, "
            "blackbox file logging, intratick stall detection."
        ),
        "gate_key": None,
        "key_params": [
            "key_aimi_loop_exclusive_invocation",
            "key_aimi_loop_blackbox_file_enabled",
            "key_aimi_intratick_stall_seconds",
        ],
    },
    "adaptive_basal": {
        "description": (
            "AIMI adaptive basal system with plateau detection, kicker mechanism, "
            "anti-stall bias, and zero-resume logic."
        ),
        "gate_key": "key_use_aimi_t3c_adaptive_basal",
        "key_params": [
            "OApsAIMIPlateauBandAbs_NUMERIC",
            "OApsAIMIR2Confident_NUMERIC",
            "OApsAIMIMaxMultiplier_NUMERIC",
            "OApsAIMIKickerStep_NUMERIC",
            "OApsAIMIKickerMinUph_NUMERIC",
            "OApsAIMIZeroResumeFrac_NUMERIC",
            "OApsAIMIAntiStallBias_NUMERIC",
            "OApsAIMIDeltaPosRelease_NUMERIC",
        ],
    },
    "hyper_trajectory": {
        "description": (
            "Hyper trajectory release system for managing established and deep hyperglycemia. "
            "Controls when to release insulin constraints based on deviation magnitude."
        ),
        "gate_key": "key_aimi_hyper_trajectory_release",
        "key_params": [
            "key_aimi_hyper_established_dev_mgdl",
            "key_aimi_hyper_deep_dev_mgdl",
            "key_aimi_hyper_trajectory_release_aggressive",
        ],
    },
    "wcycle": {
        "description": (
            "Women's cycle awareness for insulin sensitivity adjustments."
        ),
        "gate_key": "key_use_Aimi_wcycle",
        "key_params": [
            "key_use_Aimi_wcycle_require_confirm",
            "key_use_Aimi_wcycle_shadow",
        ],
    },
}


# ─── Parameter Knowledge Generator ────────────────────────────────────────────

def classify_orphaned(key: str, info: dict) -> bool:
    """Determine if a parameter is orphaned (defined but never used in code)."""
    # If we found source usages, it's not orphaned
    if info.get("used_in"):
        return False
    # Gate keys are always referenced somewhere
    if key in GATE_KEYS:
        return False
    # State parameters might be read indirectly
    if "_state_" in key or "_last_" in key:
        return False
    # If no usage found and not a gate, consider orphaned
    if not info.get("used_in"):
        # Check key name patterns that suggest it IS used
        active_patterns = [
            "weight", "cho", "tdd7", "max_smb", "min_5m", "max_basal", "max_iob",
            "pkpd", "isf", "fusion", "smb_tail", "smb_exercise", "smb_late",
            "ngr", "governance", "tube", "t3c", "autodrive", "peak_governor",
            "dyn_isf", "cosine", "endo", "thyroid", "sos", "auditor",
        ]
        for p in active_patterns:
            if p in key.lower():
                return False
        return True
    return False


def classify_negative_gate(key: str, info: dict) -> Optional[dict]:
    """Determine if a parameter is bypassed by any negative gate.

    Only returns a negative gate for the 9 verified T3C-suppressed parameters.
    No heuristic guessing — must be confirmed by code analysis.
    """
    if key in T3C_SUPPRESSED_PARAMS:
        return {
            "negative_gate_key": "key_aimi_t3c_brittle_mode",
            "negative_gate_note": (
                "T3c Brittle Mode bypasses standard AIMI — only sets TBR via PI "
                "controller, never SMB. This parameter has no effect when T3c is active."
            ),
        }
    return None


def generate_logic_summary(key: str, info: dict) -> str:
    """Auto-generate an English logic summary from parameter name and type."""
    ptype = info.get("type", "")
    name = key.replace("key_openapsaimi_", "").replace("key_aimi_", "")\
              .replace("key_oaps_aimi_", "").replace("aimi_", "")\
              .replace("key_use_aimi_", "").replace("key_use_Aimi_", "")\
              .replace("OApsAIMI", "").replace("Aimi", "")\
              .replace("openapsama_", "").replace("openapsma_", "")\
              .replace("openapsmb_", "").replace("openaps_smb_", "")

    templates = {
        "max_smb": "Upper limit for each SMB bolus size (units). Higher = more insulin per correction, faster but riskier.",
        "high_bg_max_smb": "Allows larger SMBs when BG is high. Should be >= maxSMB. Auto-raised to maxSMB if set lower.",
        "weight": "Body weight in kg. Used for weight-based insulin calculations (TDD, MPC). Must match actual weight.",
        "mpc_insulin": "Base insulin rate for MPC controller (U/kg/5min). Multiplied by body weight. Higher = more aggressive Autodrive V3 corrections.",
        "cho": "Reference daily carbohydrate intake. Influences max carb limit for meal boluses.",
        "tdd7": "7-day total daily insulin dose. Central to hyperglycemia classification and correction strategy.",
        "pkpd_initial_dia": "Initial insulin action duration (DIA) in hours for the PKPD model. Starting value for Weibull-based estimator.",
        "pkpd_initial_peak": "Initial peak time (minutes) for the PKPD model. When maximum insulin action occurs.",
        "pkpd_anchor": "Anchor value for the PKPD learning algorithm. Reference point from which learning proceeds.",
        "pkpd_bounds": "Bounds (min/max) for PKPD parameters. Prevents the learning algorithm from adopting unrealistic values.",
        "pkpd_max_dia": "Maximum DIA change per day (hours). Limits how quickly the model can adapt.",
        "pkpd_max_peak": "Maximum peak time change per day (minutes).",
        "pkpd_state": "Stored PKPD state — read and written by the learning algorithm.",
        "isf_fusion_min": "Minimum factor for ISF fusion. Limits how far ISF can be scaled down.",
        "isf_fusion_max": "Maximum factor for ISF fusion. Limits how far ISF can be scaled up.",
        "isf_fusion_max_change": "Maximum ISF fusion change per tick (5min). Prevents abrupt ISF changes.",
        "smb_tail_threshold": "IOB level at which SMB tail damping activates. Higher = more SMB allowed. Lower if frequent hypos.",
        "smb_tail_damping": "0.0 = maximum damping (conservative), 1.0 = no damping. Increase for postprandial hypers.",
        "smb_exercise_damping": "SMB damping during physical activity. 0.0 = max damping (safe), 1.0 = no damping.",
        "smb_late_fat_damping": "SMB damping during late fat rise detection. 0.0 = max damping, 1.0 = none.",
        "pragmatic_relief": "Minimum factor for Pragmatic Relief — temporary maxIOB increase in critical phases.",
        "red_carpet": "Threshold for Red Carpet Restore — restores SMB doses reduced by safety mechanisms.",
        "priority_max_iob_factor": "Multiplier for priority MaxIOB. Raises IOB limit for prioritized actions.",
        "priority_max_iob_extra": "Extra units for priority MaxIOB. Absolute addition to IOB limit.",
        "prebolus_BF": "Prebolus window (minutes) for breakfast mode P1.",
        "prebolus2_BF": "Prebolus window (minutes) for breakfast mode P2.",
        "prebolus_meal": "Prebolus window (minutes) for meal mode P1.",
        "prebolus_lunch": "Prebolus window (minutes) for lunch mode P1.",
        "prebolus2_lunch": "Prebolus window (minutes) for lunch mode P2.",
        "prebolus_dinner": "Prebolus window (minutes) for dinner mode P1.",
        "prebolus2_dinner": "Prebolus window (minutes) for dinner mode P2.",
        "prebolus_snack": "Prebolus window (minutes) for snack mode.",
        "prebolus_highcarb": "Prebolus window (minutes) for high-carb mode.",
        "prebolus_autodrive": "Prebolus window (minutes) for autodrive mode.",
        "meal_factor": "Meal factor for insulin adjustment. Higher = more insulin during meals.",
        "BF_factor": "Breakfast factor for insulin adjustment.",
        "lunch_factor": "Lunch factor for insulin adjustment.",
        "dinner_factor": "Dinner factor for insulin adjustment.",
        "snack_factor": "Snack factor for insulin adjustment.",
        "sleep_factor": "Sleep factor — insulin adjustment during sleep phases.",
        "HC_factor": "High-carb factor for insulin adjustment during large meals.",
        "FCL_factor": "FCL factor for insulin adjustment.",
        "combinedDelta": "Combined delta value for autodrive condition.",
        "mindeviation": "Minimum deviation for autodrive activation.",
        "Acceleration": "Acceleration threshold for autodrive mode.",
        "autodrive_max_basal": "Maximum basal rate (U/h) in autodrive mode.",
        "ngr_min_rise": "Minimum rise rate (mg/dL/5min) for NGR detection.",
        "ngr_smb_multiplier": "SMB multiplier during NGR phase. 1.2 = 20% more insulin per SMB.",
        "ngr_basal_multiplier": "Basal rate multiplier during NGR. 1.1 = 10% more basal per hour.",
        "ngr_max_smb": "Maximum SMB clamp during NGR. Prevents excessively large nocturnal boluses.",
        "ngr_max_iob": "Extra IOB headroom for NGR phase (units).",
        "ngr_min_duration": "Minimum duration (minutes) NGR must remain active.",
        "ngr_min_eventual": "Minimum eventualBG over target for NGR detection.",
        "ngr_decay": "Decay duration (minutes) for NGR multipliers after phase ends.",
        "ngr_age": "Age in years for NGR calculation (pediatric patients).",
        "gov_hypo_rate_enter": "BG change rate (mg/dL/5min) at which governance enters hypo protection. Lower = more sensitive.",
        "gov_hypo_rate_exit": "BG change rate at which hypo mode is exited. Should be lower than enter (hysteresis).",
        "gov_hypo_bg": "BG threshold (mg/dL) at which governance detects hypo and reduces basal.",
        "gov_severe_hypo": "BG threshold (mg/dL) for SEVERE hypo — maximum basal reduction.",
        "gov_hold_basal_floor": "Minimum basal rate as fraction of profile during hypo hold.",
        "gov_hold_basal_decay": "Decay rate for basal hold (0.90-0.999). Higher = slower decay.",
        "gov_hold_agg_floor": "Minimum aggressiveness as fraction during hypo hold.",
        "gov_hold_agg_decay": "Decay rate for aggressiveness hold.",
        "gov_anticipation_lookback": "Number of 5-minute samples for anticipation lookback.",
        "gov_anticipation_margin": "Minimum margin (mg/dL) above hypo threshold for anticipation.",
        "gov_anticipation_hypo_damp": "Maximum hypo rate dampening at full anticipation (0-1).",
        "gov_anticipation_decay_blend": "Maximum softening of hold decay rates at full anticipation (0-1).",
        "tube_hypo_floor": "Absolute minimum BG for PKPD tube. SMBs blocked when prediction falls below.",
        "tube_hyper_band": "Hyper band (mg/dL) for tube advisor — defines upper target corridor.",
        "tube_aggressiveness": "Tube advisor aggressiveness. Higher = narrower tube = more restrictive.",
        "tube_basal_trim": "Maximum basal trim (0-0.25) the tube advisor may apply.",
        "tube_kappa_margin": "Kappa safety margin (0-0.35) for tube advisor SMB blocking.",
        "t3c_activation": "BG threshold (mg/dL) at which T3c brittle mode activates. 130 = moderate.",
        "t3c_anticipation": "Anticipation strength (0-1). 0 = reactive, 1 = maximum proactive.",
        "t3c_aggressiveness": "Overall T3c mode aggressiveness. 1.0 = neutral, >1.0 = more aggressive.",
        "t3c_adaptive_basal": "Enables T3c Adaptive Basal Governance.",
        "adaptive_basal_max_scaling": "Maximum scaling of adaptive basal rate.",
        "brittle_mode": "Enables T3c Brittle Mode — bypasses standard AIMI, PI controller only.",
        "autodrive_active": "Enables Autodrive (v2/v3). Main gate for autodrive mode.",
        "autodrive_v3_authoritative": "When active: Autodrive V3 completely overrides legacy SMB blender.",
        "autodrive_v3_enhanced": "Enhanced Autodrive V3 gater with more physiological inputs.",
        "pkpd_enabled": "Enables the PKPD module (Weibull-based insulin action).",
        "pkpd_pragmatic_relief_enabled": "Enables Pragmatic Relief — temporary IOB increase.",
        "trajectory_guard_enabled": "Enables Trajectory Guard — phase-space analysis for SMB modulation.",
        "tube_enabled": "Enables Straight-Line Tube Advisor — MPC-lite SMB cap regulator.",
        "recursive_belief_shadow": "Enables Recursive Belief Engine (shadow mode).",
        "recursive_belief_authority": "Grants Recursive Belief Engine authority over SMB decisions.",
        "recursive_belief_wavelet": "Enables wavelet-based signal analysis in RBT.",
        "iob_surveillance": "Enables IOB monitoring and insulin stacking prevention.",
        "ngr_enabled": "Enables Night Growth Resistance (nocturnal growth hormone detection).",
        "hyper_trajectory_release": "Enables Hyper-Trajectory Release — relaxes insulin limits during established hyperglycemia.",
        "hyper_established_dev": "Deviation threshold (mg/dL) for established hyperglycemia.",
        "hyper_deep_dev": "Deviation threshold (mg/dL) for deep/dangerous hyperglycemia.",
        "peak_governor_enabled": "Enables Peak Governor — learns and adjusts insulin peak timing.",
        "peak_governor_learned": "Weight (0-1) of learned peak value vs. prior.",
        "dyn_isf_trajectory_max": "Maximum relative ISF change per tick via trajectory tuning.",
        "dyn_isf_trajectory_tuning": "Enables Dynamic ISF Trajectory Tuning.",
        "dyn_isf_trajectory_shadow": "Shadow-only mode for DynISF — values computed but not applied.",
        "cosine_gate_alpha": "Sharpness of cosine gate (0.1-10). Higher = steeper transition.",
        "cosine_gate_min_dq": "Minimum data quality for cosine gate activation (0-1).",
        "cosine_gate_min_sens": "Minimum sensitivity for cosine gate (0.5-1).",
        "cosine_gate_max_sens": "Maximum sensitivity for cosine gate (1-2).",
        "cosine_gate_max_shift": "Maximum sensitivity shift via cosine gate.",
        "endo_basal_mult": "Basal multiplier during endometriosis flare. 1.3 = 30% more basal.",
        "endo_smb_dampen": "SMB dampening during endometriosis flare. 0.7 = 30% reduction.",
        "endo_enable": "Enables endometriosis detection and adjustment.",
        "endo_flare_duration": "Duration of an endometriosis flare (days).",
        "endo_suppression": "Suppression window (days) between flares.",
        "thyroid_enabled": "Enables thyroid module for hormone-aware insulin sensitivity.",
        "thyroid_debug": "Enables debug logging for the thyroid module.",
        "physio_assistant": "Enables Physio Assistant — reads Health Connect data (HRV, sleep, steps).",
        "physio_hrv": "Enables HRV analysis in Physio Assistant.",
        "physio_sleep": "Enables sleep detection in Physio Assistant.",
        "physio_llm": "Enables LLM integration in Physio Assistant.",
        "physio_debug": "Enables debug logging for Physio Assistant.",
        "sos_enable": "Enables Emergency SOS detection for critical BG conditions.",
        "sos_immediate": "Threshold for immediate SOS alarm (minutes without valid BG data).",
        "sos_stale": "Threshold for stale data warning (minutes).",
        "sos_threshold": "Standard SOS threshold (minutes without valid data).",
        "auditor_enabled": "Enables Auditor — monitors algorithm decisions.",
        "auditor_max": "Maximum auditor checks per hour.",
        "auditor_min_confidence": "Minimum confidence for auditor check (0-1).",
        "auditor_timeout": "Timeout (seconds) for auditor checks.",
        "tpo_enabled": "Enables Transient Preference Overlay (temporary parameter overrides).",
        "tpo_llm": "Enables LLM confirmation for TPO overrides.",
        "tpo_notify": "Enables notification on TPO apply.",
        "context_enabled": "Enables Context-Aware LLM integration.",
        "context_llm": "Enables LLM in context module.",
        "loop_blackbox": "Enables blackbox file logging for loop decisions.",
        "loop_exclusive": "Enables exclusive loop invocation (prevents parallel ticks).",
        "intratick_stall": "Maximum stall time (seconds) within a tick before abort.",
        "smb_comparator": "Enables SMB comparator — compares AIMI SMB with standard OpenAPS SMB.",
        "meal_advisor": "Enables Meal Advisor — meal recommendation without carb announcement.",
        "last_estimated_carbs": "Last estimated carbohydrates (g) from Meal Advisor.",
        "last_estimated_carb_time": "Timestamp of last carb estimation.",
        "wcycle": "Enables Women's Cycle detection for ISF adjustment.",
        "wcycle_shadow": "Shadow mode for WCycle — compute values but do not apply.",
        "wcycle_require_confirm": "Requires manual confirmation for WCycle phase changes.",
        "pregnancy": "Enables pregnancy mode with adjusted targets.",
        "honeymoon": "Enables honeymoon detection for reduced insulin doses.",
        "xdrip": "Enables xDrip+ integration for extended CGM data.",
        "force_limits": "Enforces safety limits even when other modules would relax them.",
        "high_bg": "Threshold for high blood glucose (mg/dL).",
        "plateau_band": "Plateau detection band (±mg/dL/5min). Values within = plateau detected.",
        "r2_confident": "R-squared threshold for quadratic fit confidence (0-1).",
        "max_multiplier": "Maximum basal multiplier (× profile). 1.6 = max 160% of profile basal rate.",
        "kicker_step": "Intensity of plateau kicker (incremental multiplier).",
        "kicker_min_uph": "Minimum absolute U/h for kicker at very low basal rates.",
        "zero_resume_frac": "Fraction of profile basal rate for micro-resume after zero-basal.",
        "anti_stall_bias": "Anti-stagnation bias (+%). Prevents getting stuck on a plateau.",
        "delta_pos_release": "Positive delta threshold above which intensification stops (mg/dL/5min).",
        "uam_confidence": "UAM confidence (0-1) for meal detection without carb announcement.",
        "autodrive_bg": "BG threshold for autodrive activation (mg/dL).",
        "autodrive_isf": "ISF value for autodrive calculations.",
        "autodrive_target": "Target BG for autodrive (mg/dL).",
        "highbg_interval": "Interval for high-BG detection (minutes).",
        "meal_interval": "Meal interval (minutes).",
        "BF_interval": "Breakfast interval (minutes).",
        "lunch_interval": "Lunch interval (minutes).",
        "dinner_interval": "Dinner interval (minutes).",
        "snack_interval": "Snack interval (minutes).",
        "sleep_interval": "Sleep interval (minutes).",
        "HC_interval": "High-carb interval (minutes).",
        "enable_night": "Enables night mode for reduced activity.",
        "logsize": "Maximum log size (entries) for AIMI logging.",
    }

    for pattern, desc in templates.items():
        if pattern.lower() in name.lower():
            return desc

    if ptype == "Boolean":
        if "enable" in name.lower() or name.lower().startswith("use_"):
            return f"Enables/disables {name}. Gate flag for feature control."
        return f"Boolean switch for {name}."
    elif ptype in ("Double", "Int"):
        return f"Used in AIMI internal calculations for {name}."
    return f"Parameter for {name}."


def generate_effect(key: str, info: dict, direction: str) -> str:
    """Auto-generate English effect description (high/low)."""
    name = key.replace("key_openapsaimi_", "").replace("key_aimi_", "")\
              .replace("key_oaps_aimi_", "").replace("aimi_", "")\
              .replace("OApsAIMI", "")

    ptype = info.get("type", "")
    if ptype == "Boolean":
        if direction == "high":
            return "Feature is active — associated functionality runs"
        return "Feature is inactive — associated functionality is disabled"

    effect_patterns = {
        "max_smb": ("Larger SMBs allowed — more aggressive corrections, higher hypo risk",
                     "SMBs heavily limited — more conservative dosing, slower correction"),
        "high_bg_max_smb": ("At high BG, larger correction boluses — faster return to target",
                             "Even at high BG, reduced corrections — slower but safer"),
        "weight": ("Lower calculated basal proportion (TDD/weight drops)",
                    "Higher calculated basal proportion"),
        "mpc": ("More insulin per step — more aggressive MPC corrections",
                "Less insulin — more conservative MPC corrections"),
        "cho": ("Higher carb limit — more room for meal boluses",
                "Lower carb limit — more restrictive for large meals"),
        "tdd7": ("Higher TDD — different hyper classification — altered correction strategy",
                 "Lower TDD — more conservative hyper detection"),
        "smb_tail_threshold": ("Tail damping only at higher IOB — more SMB possible",
                                "Tail damping starts at lower IOB — more conservative"),
        "smb_tail_damping": ("Less damping — more SMB possible",
                              "Stronger damping — less SMB — safer for frequent hypos"),
        "smb_exercise": ("Less damping during activity — more insulin despite movement (hypo risk!)",
                          "Stronger damping during activity — less insulin, safer"),
        "smb_late": ("Less damping during fat rise — more insulin for fat correction",
                      "Stronger damping — less insulin during fat rise, safer"),
        "governance_hypo": ("Governance intervenes earlier — more hypo protection",
                             "Governance intervenes later — less protection, hypo risk"),
        "governance_severe": ("Severe hypo detected earlier — more protection",
                               "Only at deeper BG — riskier"),
        "tube": ("Narrower tube — more restrictive — fewer SMBs",
                  "Wider tube — more SMBs allowed — potentially riskier"),
        "t3c_activation": ("T3c mode only activates at higher BG — fewer interventions",
                            "T3c mode activates at lower BG — more frequent interventions"),
        "t3c_anticipation": ("Stronger anticipation — more proactive corrections",
                              "Weaker anticipation — more reactive behavior"),
        "t3c_aggressiveness": ("More aggressive corrections — faster BG reduction, higher hypo risk",
                                "Gentler corrections — slower but safer"),
        "ngr_smb": ("Larger SMBs at night — more aggressive growth correction",
                     "Smaller SMBs — more conservative nocturnal correction"),
        "ngr_basal": ("Higher basal rate at night — preventive against growth surge",
                       "Lower basal rate — less preventive insulin"),
        "red_carpet": ("Higher threshold — more SMB restore — more insulin in meal context",
                        "Lower threshold — less restore — more conservative"),
        "endo_basal": ("More basal during endometriosis flare",
                        "Less additional basal"),
        "endo_smb": ("Less dampening — more insulin despite flare",
                      "Stronger dampening — more conservative during flare"),
        "peak_governor": ("Learned peak values have more weight — more adaptive",
                           "Prior values dominate — more conservative, less adaptive"),
        "dyn_isf": ("Larger ISF changes per tick allowed — more adaptive",
                     "Smaller ISF changes — more stable, conservative"),
        "cosine": ("Steeper gate transition — faster sensitivity changes",
                    "Shallower transition — slower, gentler adaptation"),
    }

    for pattern, (high_eff, low_eff) in effect_patterns.items():
        if pattern.lower() in name.lower():
            return high_eff if direction == "high" else low_eff

    if direction == "high":
        return "Higher value — more insulin/greater effect"
    return "Lower value — less insulin/reduced effect"


def classify_impact(key: str, param_type: str) -> str:
    """Classify parameter impact based on key name and type."""
    critical_patterns = [
        "max_smb", "smb_tail", "smb_damping", "smb_exercise",
        "hypo", "safety", "emergency", "red_carpet",
        "max_basal", "max_bolus", "max_iob",
        "gov_hypo", "gov_severe",
        "tube_hypo", "tube_aggressiveness",
    ]
    high_patterns = [
        "pkpd", "isf", "fusion", "peak", "ngr",
        "t3c", "brittle", "tube", "meal", "prebolus",
        "mpc", "autodrive", "trajectory", "governance",
        "endo", "cosine", "dyn_isf",
    ]
    for p in critical_patterns:
        if p in key.lower():
            return "critical"
    for p in high_patterns:
        if p in key.lower():
            return "high"
    return "medium"


def classify_feature_group(key: str) -> str:
    """Classify which feature group a key belongs to."""
    groups = {
        "max_smb": "core",
        "weight": "core",
        "cho": "core",
        "tdd7": "core",
        "wcycle": "wcycle",
        "ngr": "ngr",
        "night_growth": "ngr",
        "gov": "governance",
        "pkpd": "pkpd",
        "isf_fusion": "ism_fusion",
        "dyn_isf": "dyn_isf_trajectory",
        "tube": "tube_advisor",
        "t3c": "t3c_adaptive_basal",
        "brittle": "t3c_brittle",
        "autodrive": "autodrive_v3",
        "mpc": "autodrive_v3",
        "meal": "meal_modes",
        "prebolus": "meal_modes",
        "dinner": "meal_modes",
        "lunch": "meal_modes",
        "snack": "meal_modes",
        "sleep": "meal_modes",
        "BF": "meal_modes",
        "highcarb": "meal_modes",
        "FCL": "meal_modes",
        "HC": "meal_modes",
        "combinedDelta": "autodrive",
        "mindeviation": "autodrive",
        "Acceleration": "autodrive",
        "smb_tail": "smb_tail",
        "smb_exercise": "smb_tail",
        "smb_late": "smb_tail",
        "red_carpet": "iob_surveillance",
        "priority_max_iob": "iob_surveillance",
        "iob_surveillance": "iob_surveillance",
        "trajectory_guard": "trajectory_guard",
        "recursive_belief": "recursive_belief",
        "hyper": "hyper_trajectory",
        "endo": "endometriosis",
        "cosine": "cosine_gate",
        "peak_governor": "peak_governor",
        "plateau": "adaptive_basal",
        "kicker": "adaptive_basal",
        "antistall": "adaptive_basal",
        "zeroresume": "adaptive_basal",
        "delta_pos": "adaptive_basal",
        "maxmultiplier": "adaptive_basal",
        "r2confident": "adaptive_basal",
        "lastestimated": "meal_advisor",
        "uam": "uam",
        "tpo": "tpo",
        "thyroid": "thyroid",
        "sos": "sos_emergency",
        "auditor": "auditor",
        "context": "context_llm",
        "loop": "loop_config",
        "physio": "physiological_phase",
        "pregnancy": "core",
        "honeymoon": "core",
        "xdrip": "core",
        "forcelimits": "core",
        "meal_advisor": "meal_advisor",
    }
    k = key.lower()
    for pattern, group in groups.items():
        if pattern in k:
            return group
    return "core"


def build_parameter_knowledge(keys: Dict[str, dict]) -> List[dict]:
    """Build enriched parameter knowledge entries."""
    entries = []

    for key_str, info in sorted(keys.items()):
        ptype = info.get("type", "Unknown")
        entry = {
            "key": key_str,
            "name": info.get("name", key_str),
            "type": ptype,
        }

        if "default" in info:
            entry["default"] = info["default"]
        if "min" in info:
            entry["min"] = info["min"]
        if "max" in info:
            entry["max"] = info["max"]
        if "unit_type" in info:
            entry["unit_type"] = info["unit_type"]
        if "dependency" in info:
            entry["dependency"] = info["dependency"]
        if "negative_dependency" in info:
            entry["negative_dependency"] = info["negative_dependency"]

        # Source usage data
        if "used_in" in info and info["used_in"]:
            entry["used_in"] = info["used_in"]

        # Orphaned status
        entry["orphaned"] = classify_orphaned(key_str, info)

        # Negative gate
        neg_gate = classify_negative_gate(key_str, info)
        if neg_gate:
            entry.update(neg_gate)
        else:
            entry["negative_gate_key"] = None
            entry["negative_gate_note"] = None

        # Auto-generated descriptions (German)
        entry["logic_summary"] = generate_logic_summary(key_str, info)
        entry["effect_high"] = generate_effect(key_str, info, "high")
        entry["effect_low"] = generate_effect(key_str, info, "low")

        # Impact and feature group
        entry["impact"] = classify_impact(key_str, ptype)
        entry["feature_group"] = classify_feature_group(key_str)
        entry["is_gate"] = key_str in GATE_KEYS

        entries.append(entry)

    return entries


# ─── Context for AI Builder ──────────────────────────────────────────────────

def build_context_for_ai(keys: Dict[str, dict]) -> dict:
    """Build the aimi_context_for_ai.json structure, auto-populating key_params."""

    # Auto-populate key_params from scanned keys
    for fname, finfo in FEATURE_DESCRIPTIONS.items():
        # Find keys that belong to this feature group
        feature_keys = []
        for key_str, info in keys.items():
            if classify_feature_group(key_str) == fname:
                feature_keys.append(key_str)
            # Also check gate_key
            if finfo.get("gate_key") and key_str == finfo["gate_key"]:
                if key_str not in feature_keys:
                    feature_keys.append(key_str)
        # Merge with existing hardcoded params (deduplicate)
        existing = set(finfo.get("key_params", []))
        auto = set(feature_keys)
        finfo["key_params"] = sorted(existing | auto)

    return {
        "version": "2.2",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "algorithm_overview": {
            "name": "AIMI (AI-powered Modular Insulin)",
            "base": "OpenAPS oref1 with extensive extensions",
            "version": "v3",
            "tick_interval_seconds": 300,
            "decision_tree_stages": 46,
            "decision_flow": (
                "The AIMI algorithm runs a 46-stage decision pipeline per 5-minute tick.\n\n"
                "STAGES 1-8 (BOOTSTRAP + CONTEXT):\n"
                "1. runEarlyDetermineBasalStages\n"
                "2. bootstrapPhysiologyAfterEarlyTick\n"
                "3. buildDecisionContextInitRtSosAndFlatShadow\n"
                "4. runRealtimePhysioIobProfilerAndInsulinObserver\n"
                "5. ensureWCycleAndLoadGlucoseStatusOrAbort\n"
                "6. runT9PhysioEarlyPkpdAndTubeBootstrap\n"
                "7. runCombinedDeltaByodaAndDynamicPeak\n"
                "8. buildPreTherapyAutodriveByodaBootstrap\n\n"
                "STAGES 9-13 (CLOCK + THERAPY GATE + MODES):\n"
                "9. runTickClockMaxSmbTirCarbAndGlucoseCopy\n"
                "10. runTherapyHydrateClocksAndExerciseLockoutGate — THERAPY GATE\n"
                "11. runManualMealModesAfterTherapyGate\n"
                "12. runT3cBrittleBypassOrReturn — T3C BYPASS\n"
                "13. runSignalPreparationPkpdRuntimePhase\n\n"
                "STAGES 14-19 (TRAJECTORY + PREDICTIONS + SAFETY):\n"
                "14-19. Trajectory context, predictions, safety gates, meal advisor, "
                "hard brake, correction aggression\n\n"
                "STAGES 20-27 (AUTODRIVE + POST-HYPO + BASAL BOOTSTRAP):\n"
                "20-27. Autodrive V3/V2, post-hypo classification, drift terminator, "
                "basal schedule, IOB/TDD/carb limits, endo+activity adjustments\n\n"
                "STAGES 28-33 (PKPD + UAM + SMB EXECUTION):\n"
                "28-33. PKPD predictions, UAM model, SMB decision/execute, "
                "finalizeAndCapSMB, PKPD guard, snapshot\n\n"
                "STAGES 34-39 (MEAL BOOST + SAFETY + NGR + IOB GATE):\n"
                "34-39. Meal hyper basal boost, WCycle/ISF, carbs advisor safety, "
                "NGR headroom, MaxIOB gate, activity relax\n\n"
                "STAGES 40-46 (BASAL ENGINE + LEARNERS + EXPORT):\n"
                "40-46. Basal decision engine, learners, snapshot/JSONL export, "
                "final safety checks, basal floor, reason string, return RT"
            ),
            "key_differences_from_oref1": [
                "Multi-tier plateau/slope system (5 tiers) for SMB sizing",
                "Recursive Belief Engine (RBT) with 4 time scales",
                "PKPD model with Weibull curves and TAP Peak Shift",
                "Scenario Projection Engine (CLINICAL_FLOOR + SCENARIO_BEST)",
                "Trajectory Guard with phase-space analysis (5 classifications)",
                "Autodrive V3 as MPC-like controller",
                "UAM hypothesis competition for meal detection",
                "ApplyBasalFirstPolicy — disables SMB when BG<110 and fragile",
                "Unified Reactivity Learner — ML-powered time-based replacement",
                "T3c Brittle Mode with separate PI controller",
                "Night Growth Resistance monitor for paediatric patients",
                "StraightLineTubeAdvisor — MPC-lite SMB cap regulator",
                "Risk Envelope system — two immutable snapshots per tick",
                "Meal Absorption Phase state machine",
                "Physiological Phase Classification with BehavioralRiskPolicy",
                "Governance system — hypo detection, hold states, anticipation",
                "Adaptive Kernel Bank — cosine similarity gates",
                "Dynamic ISF trajectory tuning",
            ],
        },
        "features": FEATURE_DESCRIPTIONS,
        "known_bugs": KNOWN_BUGS,
    }


# ─── Context MD Generator ───────────────────────────────────────────────────

def build_context_md(context: dict, keys: Dict[str, dict]) -> str:
    """Build a human-readable Markdown version of the AI context."""
    lines = [
        "# AIMI Algorithm Context",
        f"Version: {context.get('version', '?')}",
        f"Source commit: {context.get('source_commit', '?')}",
        f"Generated: {context.get('generated_at', '?')}",
        "",
        "## Algorithm Overview",
        context.get("algorithm_overview", {}).get("name", "AIMI"),
        "",
        context.get("algorithm_overview", {}).get("decision_flow", ""),
        "",
        "## Key Differences from oref1",
    ]
    for diff in context.get("algorithm_overview", {}).get("key_differences_from_oref1", []):
        lines.append(f"- {diff}")

    lines.append("\n## Features")
    for fname, finfo in context.get("features", {}).items():
        gate = finfo.get("gate_key", "N/A")
        lines.append(f"\n### {fname}")
        lines.append(f"Gate: {gate}")
        lines.append(f"\n{finfo.get('description', '')}")
        params = finfo.get("key_params", [])
        if params:
            lines.append(f"\nKey parameters ({len(params)}):")
            for p in params[:15]:
                lines.append(f"- {p}")
            if len(params) > 15:
                lines.append(f"- ... and {len(params) - 15} more")

    lines.append("\n## Known Bugs")
    for bug in context.get("known_bugs", []):
        lines.append(f"\n### {bug['id']}: {bug['name']}")
        lines.append(f"- **Symptom:** {bug['symptom']}")
        lines.append(f"- **Technical:** {bug['technical']}")
        lines.append(f"- **Workaround:** {bug['workaround']}")

    lines.append("\n## Parameter Summary by Feature Group")
    groups: Dict[str, List[str]] = {}
    for key_str in sorted(keys):
        g = classify_feature_group(key_str)
        groups.setdefault(g, []).append(key_str)
    for g in sorted(groups):
        lines.append(f"\n### {g} ({len(groups[g])} params)")
        for p in groups[g][:10]:
            info = keys.get(p, {})
            default_str = f" [default: {info.get('default', '?')}]" if "default" in info else ""
            lines.append(f"- `{p}`{default_str}")

    return "\n".join(lines)


# ─── Compact Context Builder ─────────────────────────────────────────────────

def build_context_compact(keys: Dict[str, dict], commit: str) -> str:
    """Build the compact context text file."""
    lines = [
        f"# AIMI Algorithm Context (commit: {commit[:8]})",
        "",
        "## Overview",
        "The AIMI algorithm runs a 46-stage decision pipeline per 5-minute tick.",
        "",
        "STAGES 1-8 (BOOTSTRAP + CONTEXT):",
        "  Cache clear, TDD7, physiology bootstrap, decision context, IOB profiler,",
        "  glucose staleness check, PKPD bootstrap, combined delta, BYODA setup.",
        "",
        "STAGES 9-13 (CLOCK + THERAPY GATE + MODES):",
        "  Tick clock, TIR stats, THERAPY GATE (exercise lockout: TBR 0% if active+BG<=140),",
        "  manual meal modes (P1/P2 prebolus with 30min TTL), T3c brittle bypass,",
        "  PKPD runtime signal preparation.",
        "",
        "STAGES 14-19 (TRAJECTORY + PREDICTIONS + SAFETY):",
        "  ISF/target override, safety predictions, scenario projection",
        "  (CLINICAL_FLOOR + SCENARIO_BEST), safety halt, meal advisor, hard brake.",
        "",
        "STAGES 20-27 (AUTODRIVE + POST-HYPO + BASAL):",
        "  Autodrive V3 (MPC, authoritative may bypass SMB blender),",
        "  V2 fallback, post-hypo classification, drift terminator, basal schedule.",
        "",
        "STAGES 28-33 (PKPD + UAM + SMB EXECUTION):",
        "  PKPD predictions, BGI/deviation, UAM model, SMB decision & execute,",
        "  finalizeAndCapSMB, PKPD absorption guard, endo dampening.",
        "",
        "STAGES 34-39 (MEAL BOOST + SAFETY + NGR + IOB):",
        "  Meal hyper basal boost, WCycle/ISF/carb impact, carbs advisor safety,",
        "  NGR headroom, MaxIOB gate, activity relax, microbolus interval.",
        "",
        "STAGES 40-46 (BASAL ENGINE + LEARNERS + EXPORT):",
        "  Basal decision engine (DynamicBasalController, BasalPlanner),",
        "  learners (basalLearner, UnifiedReactivityLearner),",
        "  snapshot/JSONL export, final safety checks, reason string, return RT.",
        "",
        "## Key Features",
    ]

    # Auto-populate features from FEATURE_DESCRIPTIONS with actual param counts
    for fname, finfo in FEATURE_DESCRIPTIONS.items():
        gate = finfo.get("gate_key", "N/A")
        params = finfo.get("key_params", [])
        lines.append(f"\n### {fname} ({len(params)} params)")
        lines.append(f"Gate: {gate}")
        lines.append(f"  {finfo['description'][:300]}")
        if params:
            lines.append(f"  Key params: {', '.join(params[:10])}")
            if len(params) > 10:
                lines.append(f"  ... and {len(params) - 10} more")

    # Add parameter summary by feature group
    lines.append("\n\n## Parameter Summary by Feature Group")
    groups: Dict[str, List[str]] = {}
    for key_str in sorted(keys):
        g = classify_feature_group(key_str)
        groups.setdefault(g, []).append(key_str)

    for g in sorted(groups):
        lines.append(f"\n### {g} ({len(groups[g])} params)")
        for p in groups[g][:20]:
            info = keys.get(p, {})
            default_str = f" [default: {info['default']}]" if "default" in info else ""
            lines.append(f"  - {p}{default_str}")
        if len(groups[g]) > 20:
            lines.append(f"  ... and {len(groups[g]) - 20} more")

    return "\n".join(lines)


# ─── Settings Path Merger ───────────────────────────────────────────────────

def merge_settings_paths(params: list, source_root: str) -> list:
    """Merge settings_path and settings_gate into each parameter entry.

    Tries aimi_settings_paths.json first, falls back to hardcoded overrides
    and feature-group-based paths.
    """
    # Look next to this script first (analyzer/), then in source root
    paths_file = _SCRIPT_DIR / "aimi_settings_paths.json"
    if not paths_file.exists():
        paths_file = Path(source_root) / "aimi_settings_paths.json"
    paths_data = {}
    if paths_file.exists():
        try:
            with open(paths_file) as f:
                paths_data = json.load(f)
        except Exception:
            pass

    paths_map = paths_data.get("paths", {}) if isinstance(paths_data, dict) else {}

    def normalize(k):
        return k.lower().replace("key_", "").replace("_", "")

    paths_norm = {normalize(k): v for k, v in paths_map.items()}

    def gate_label(gate_key: str) -> str:
        if not gate_key:
            return None
        label = (gate_key
            .replace("key_use_aimi_", "").replace("key_use_Aimi_", "")
            .replace("key_aimi_", "").replace("key_oaps_aimi_", "")
            .replace("key_", "").replace("_", " ").title())
        return f"Requires: {label} = ON"

    matched = no_path = fallback = 0
    for p in params:
        key = p["key"]
        info = paths_map.get(key) or paths_norm.get(normalize(key))
        if info:
            p["settings_path"] = info.get("path", info) if isinstance(info, dict) else str(info)
            gate = info.get("gate_key") if isinstance(info, dict) else None
            p["settings_gate"] = gate_label(gate) if gate else None
            matched += 1
        elif key in SETTINGS_PATH_OVERRIDES:
            p["settings_path"] = SETTINGS_PATH_OVERRIDES[key]
            p["settings_gate"] = None
            matched += 1
        elif key in NO_SETTINGS_PATH_KEYS or p.get("orphaned"):
            p["settings_path"] = None
            p["settings_gate"] = None
            no_path += 1
        else:
            fg = p.get("feature_group", "core")
            # Format feature group as a readable path segment
            fg_readable = (fg.replace("_", " ").title())
            p["settings_path"] = f"AIMI → Preferences user → {fg_readable}"
            p["settings_gate"] = None
            fallback += 1

    print(f"  settings_path: matched={matched} no_path={no_path} fallback={fallback}")
    return params


# ─── Context Parameter Knowledge Builder ────────────────────────────────────

def build_context_parameter_knowledge(params: list) -> list:
    """Build the parameter_knowledge array for aimi_context_for_ai.json."""
    result = []
    for p in params:
        if p.get("orphaned"):
            continue
        if not p.get("logic_summary") and not p.get("used_in"):
            continue
        entry = {
            "key": p["key"],
            "name": p.get("name", p["key"]),
            "logic_summary": p.get("logic_summary", ""),
            "effect_high": p.get("effect_high", ""),
            "effect_low": p.get("effect_low", ""),
            "feature_group": p.get("feature_group", ""),
            "impact": p.get("impact", "medium"),
            "settings_path": p.get("settings_path", ""),
        }
        used_in = p.get("used_in") or []
        if used_in:
            entry["usage_type"] = used_in[0].get("usage_type", "")
            entry["function"] = used_in[0].get("function", "")
        result.append(entry)
    return result


# ─── Output Validator ───────────────────────────────────────────────────────

def validate_output(all_params: list, context: dict, lookup: dict) -> bool:
    """Validate generated output against quality standards. Returns True if OK."""
    import re
    errors = []

    # 1. No German characters in parameters or context
    params_text = json.dumps(all_params, ensure_ascii=False)
    ctx_text = json.dumps(context, ensure_ascii=False)
    all_text = params_text + ctx_text
    german = re.findall(r'[äöüÄÖÜß]', all_text)
    if german:
        errors.append(f"German chars found in output: {len(german)} instances")

    # 2. negative_gate_key must only reference verified gates
    APPROVED_NEG_GATES = {"key_aimi_t3c_brittle_mode"}
    for p in all_params:
        neg = p.get("negative_gate_key")
        if neg and neg not in APPROVED_NEG_GATES:
            errors.append(f"Unknown negative_gate_key on {p['key']}: {neg}")
        if neg == "key_aimi_t3c_brittle_mode" and p["key"] not in T3C_SUPPRESSED_PARAMS:
            errors.append(f"Wrong T3c suppression on {p['key']}")

    # 3. Active critical/high params should have settings_path (except state vars)
    state_prefixes = ("aimi_pkpd_state_", "oa_aimi_last_", "aimi_context_storage",
                       "aimi_tuning_")
    missing_path = [
        p["key"] for p in all_params
        if not p.get("orphaned")
        and not p.get("settings_path")
        and not any(p["key"].startswith(s) for s in state_prefixes)
        and p.get("impact") in ("critical", "high")
    ]
    if missing_path:
        errors.append(f"Active critical/high params missing settings_path "
                      f"({len(missing_path)}): {missing_path[:10]}")

    # 4. parameter_knowledge populated in context
    pk = context.get("parameter_knowledge", [])
    if len(pk) < 50:
        errors.append(f"parameter_knowledge too small: {len(pk)} entries (need >=50)")

    # 5. No orphaned params with used_in
    orphaned_with_usage = [p["key"] for p in all_params
                           if p.get("orphaned") and p.get("used_in")]
    if orphaned_with_usage:
        errors.append(f"Orphaned params have used_in: {orphaned_with_usage}")

    # 6. Compact text has reasonable size
    compact_path = DATA_DIR / "aimi_context_compact.txt"
    if compact_path.exists():
        compact_size = compact_path.stat().st_size
        if compact_size < 5000:
            errors.append(f"aimi_context_compact.txt too small: {compact_size} bytes")

    if errors:
        print(f"\n❌ Validation FAILED ({len(errors)} errors):")
        for e in errors:
            print(f"  - {e}")
        active = sum(1 for p in all_params if not p.get("orphaned"))
        print(f"   Stats: {len(all_params)} params ({active} active), "
              f"{len(pk)} parameter_knowledge, "
              f"{sum(1 for p in all_params if p.get('settings_path'))} with settings_path, "
              f"{sum(1 for p in all_params if p.get('negative_gate_key'))} with negative_gate")
        return False
    else:
        active = sum(1 for p in all_params if not p.get("orphaned"))
        print(f"\n✅ Validation passed")
        print(f"   {len(all_params)} params ({active} active)")
        print(f"   {len(pk)} parameter_knowledge entries")
        print(f"   {sum(1 for p in all_params if p.get('settings_path'))} with settings_path")
        print(f"   {sum(1 for p in all_params if p.get('negative_gate_key'))} with negative_gate_key")
        return True


# ─── Main Generator ──────────────────────────────────────────────────────────

def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="AIMI Analyzer Data File Generator"
    )
    parser.add_argument(
        "--source-root",
        default=DEFAULT_SOURCE_ROOT,
        help="Path to OpenApsAIMI_V4 repo root (auto-detected when running from <repo>/analyzer/)"
    )
    parser.add_argument(
        "--no-scan",
        action="store_true",
        help="Skip scanning AIMI source for key usages"
    )
    args = parser.parse_args()

    source_root = args.source_root

    # Validate source root early — fail clearly instead of silently generating empty files
    if not source_root:
        print()
        print("❌ AIMI source not found.")
        print()
        print("   generate_data.py must run on the laptop where the AIMI repo is checked out.")
        print("   On the NUC (analyzer-only), use deploy.sh from the laptop instead:")
        print()
        print("     bash analyzer/deploy.sh")
        print()
        print("   Or pass the source root explicitly:")
        print("     python3 generate_data.py --source-root /path/to/OpenApsAIMI_V4")
        print()
        sys.exit(1)

    if not Path(source_root, "plugins", "aps").exists():
        print()
        print(f"❌ Source root does not look like an AAPS repo: {source_root}")
        print(f"   Expected to find:  {source_root}/plugins/aps/")
        print()
        print("   Are you running from the right directory?")
        print("   Expected location: <repo>/analyzer/generate_data.py")
        print()
        sys.exit(1)

    print(f"Source root: {source_root}")
    print(f"Output dir:  {DATA_DIR}")

    # Get git commit
    commit = get_git_commit(source_root)
    print(f"Git commit:  {commit}")

    # Parse key definitions
    print("\n--- Parsing key definitions ---")
    parser_obj = KotlinEnumParser(source_root)
    keys = parser_obj.parse_all()
    print(f"Found {len(keys)} AIMI-related parameters")

    # Scan source code for usages (optional)
    if not args.no_scan:
        print("\n--- Scanning source code ---")
        scanner = SourceScanner(source_root)
        scanner.scan_all()
        print(f"Scanned {len(scanner.files)} source files")

        for key_str, info in keys.items():
            enum_name = info.get("name", "")
            usages = scanner.find_usages(key_str, enum_name)
            if usages:
                info["used_in"] = usages
    else:
        print("Skipping source scan (--no-scan)")

    # ─── Generate aimi_parameters.json ───────────────────────────────────
    print("\n--- Generating aimi_parameters.json ---")
    params_knowledge = build_parameter_knowledge(keys)

    # Merge settings paths into parameters
    params_knowledge = merge_settings_paths(params_knowledge, source_root)

    active_count = sum(1 for p in params_knowledge if not p.get("orphaned"))
    orphaned_count = sum(1 for p in params_knowledge if p.get("orphaned"))
    with_used = sum(1 for p in params_knowledge if p.get("used_in"))
    with_neg_gate = sum(1 for p in params_knowledge if p.get("negative_gate_key"))

    parameters_json = {
        "version": "3.0",
        "source_commit": commit,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total": len(params_knowledge),
        "active": active_count,
        "orphaned": orphaned_count,
        "with_used_in": with_used,
        "with_negative_gate": with_neg_gate,
        "with_settings_path": sum(1 for p in params_knowledge if p.get("settings_path")),
        "parameters": params_knowledge,
    }

    with open(DATA_DIR / "aimi_parameters.json", "w", encoding="utf-8") as f:
        json.dump(parameters_json, f, indent=2, ensure_ascii=False)
    print(f"  Written: aimi_parameters.json ({len(params_knowledge)} params, "
          f"{active_count} active, {orphaned_count} orphaned, "
          f"{with_used} with used_in, {with_neg_gate} with negative_gate)")

    # ─── Generate aimi_context_for_ai.json ─────────────────────────────
    print("\n--- Generating aimi_context_for_ai.json ---")
    context_json = build_context_for_ai(keys)
    context_json["source_commit"] = commit
    # Build parameter_knowledge from enriched params
    context_json["parameter_knowledge"] = build_context_parameter_knowledge(
        params_knowledge
    )

    with open(DATA_DIR / "aimi_context_for_ai.json", "w", encoding="utf-8") as f:
        json.dump(context_json, f, indent=2, ensure_ascii=False)
    print(f"  Written: aimi_context_for_ai.json ({len(context_json['features'])} features)")

    # ─── Generate aimi_context_compact.txt ──────────────────────────────
    print("\n--- Generating aimi_context_compact.txt ---")
    compact = build_context_compact(keys, commit)

    with open(DATA_DIR / "aimi_context_compact.txt", "w", encoding="utf-8") as f:
        f.write(compact)
    print(f"  Written: aimi_context_compact.txt ({len(compact)} chars)")

    # ─── Generate aimi_context_for_ai.md ──────────────────────────────
    print("\n--- Generating aimi_context_for_ai.md ---")
    md_content = build_context_md(context_json, keys)

    with open(DATA_DIR / "aimi_context_for_ai.md", "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"  Written: aimi_context_for_ai.md ({len(md_content)} chars)")

    # ─── Generate aimi_param_lookup.json ──────────────────────────────
    print("\n--- Generating aimi_param_lookup.json ---")
    lookup = {}
    for key_str, info in sorted(keys.items()):
        entry = {
            "key": key_str,
            "name": info.get("name", key_str),
            "type": info.get("type", "Unknown"),
            "feature_group": classify_feature_group(key_str),
            "summary": generate_logic_summary(key_str, info),
        }
        if "default" in info:
            entry["default"] = info["default"]
        if "min" in info:
            entry["min"] = info["min"]
        if "max" in info:
            entry["max"] = info["max"]
        if info.get("unit_type"):
            entry["unit_type"] = info["unit_type"]
        if info.get("dependency"):
            entry["dependency"] = info["dependency"]
        entry["orphaned"] = classify_orphaned(key_str, info)
        entry["is_gate"] = key_str in GATE_KEYS
        neg_gate = classify_negative_gate(key_str, info)
        entry["negative_gate_key"] = neg_gate["negative_gate_key"] if neg_gate else None
        entry["impact"] = classify_impact(key_str, info.get("type", ""))
        entry["effect_high"] = generate_effect(key_str, info, "high")
        entry["effect_low"] = generate_effect(key_str, info, "low")
        if "used_in" in info and info["used_in"]:
            entry["used_in"] = info["used_in"][:3]  # Up to 3 usage references

        lookup[key_str] = entry

    with open(DATA_DIR / "aimi_param_lookup.json", "w", encoding="utf-8") as f:
        json.dump(lookup, f, indent=2, ensure_ascii=False)
    active_count = sum(1 for v in lookup.values() if not v.get("orphaned"))
    orphaned_count = sum(1 for v in lookup.values() if v.get("orphaned"))
    with_gate = sum(1 for v in lookup.values() if v.get("is_gate"))
    print(f"  Written: aimi_param_lookup.json ({len(lookup)} entries: "
          f"{active_count} active, {orphaned_count} orphaned, "
          f"{with_gate} gates)")

    # ─── Validate ─────────────────────────────────────────────────────
    print("\n--- Validating output ---")
    validate_output(params_knowledge, context_json, lookup)

    print("\n✅ All data files regenerated successfully.")


if __name__ == "__main__":
    main()
