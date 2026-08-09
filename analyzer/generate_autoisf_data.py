#!/usr/bin/env python3
"""
AutoISF Parameter Analyzer — Data File Generator
================================================
Scans the AAPS source code for AutoISF-related parameters and generates:
  data/autoisf_parameters.json       — Full parameter knowledge base
  data/autoisf_context_compact.txt   — Compact text for AI prompts
  data/autoisf_param_lookup.json     — Quick lookup of parameter summaries
  data/autoisf_context_for_ai.json   — Feature descriptions for AI

Usage:
  python3 generate_autoisf_data.py [--source-root /path/to/OpenApsAIMI_V4]
"""

import json, re, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT = _SCRIPT_DIR.parent
DEFAULT_SOURCE_ROOT = str(_AUTO_ROOT) if (_AUTO_ROOT / "plugins" / "aps").exists() else None
DATA_DIR = _SCRIPT_DIR / "data"

AUTOISF_SRC = "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAutoISF"

KEY_FILES = [
    "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/LongKey.kt",
    "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
]

# Enum entry names that are AutoISF-specific
AUTOISF_ENUM_NAMES = {
    # Double keys — AutoISF weights & SMB delivery
    "ApsAutoIsfMin", "ApsAutoIsfMax",
    "ApsAutoIsfBgAccelWeight", "ApsAutoIsfBgBrakeWeight",
    "ApsAutoIsfLowBgWeight", "ApsAutoIsfHighBgWeight",
    "ApsAutoIsfPpWeight", "ApsAutoIsfDuraWeight",
    "ApsAutoIsfSmbDeliveryRatio", "ApsAutoIsfSmbDeliveryRatioMin",
    "ApsAutoIsfSmbDeliveryRatioMax", "ApsAutoIsfSmbDeliveryRatioBgRange",
    "ApsAutoIsfSmbMaxRangeExtension",
    # Boolean keys
    "ApsUseAutoIsfWeights", "ApsUseAutoIsf",
    "ApsAutoIsfHighTtRaisesSens", "ApsAutoIsfLowTtLowersSens",
    "ApsAutoIsfSmbOnEvenTarget",
    # Int keys
    "ApsAutoIsfIobThPercent", "ApsAutoIsfHalfBasalExerciseTarget",
}

# Shared keys that AutoISF's algorithm reads
SHARED_AUTOISF_KEYS = {
    "ApsUseSmb":                   "openapsama_use_smb",
    "ApsUseSmbAlways":             "openapsama_use_smb_always",
    "ApsUseSmbWithCob":            "openapsama_use_smb_with_cob",
    "ApsUseSmbAfterCarbs":         "openapsama_use_smb_after_carbs",
    "ApsUseSmbWithHighTt":         "openapsama_use_smb_with_high_tt",
    "ApsUseSmbWithLowTt":          "openapsama_use_smb_with_low_tt",
    "ApsUseUam":                   "openapsama_use_uam",
    "ApsUseAutosens":              "openapsama_use_autosens",
    "ApsSensitivityRaisesTarget":  "openapsama_sensitivity_raises_target",
    "ApsResistanceLowersTarget":   "openapsama_resistance_lowers_target",
    "ApsAlwaysUseShortDeltas":     "openapsama_always_use_short_deltas",
    "ApsMaxBasal":                 "openapsama_max_basal",
    "ApsMaxDailyMultiplier":       "openapsama_max_daily_multiplier",
    "ApsMaxCurrentBasalMultiplier":"openapsama_max_current_basal_multiplier",
    "ApsSmbMaxIob":                "openapsama_smb_max_iob",
    "ApsMaxSmbFrequency":          "openapsama_max_smb_frequency",
    "ApsMaxMinutesOfBasalToLimitSmb": "openapsama_max_minutes_of_basal_to_limit_smb",
    "ApsUamMaxMinutesOfBasalToLimitSmb": "openapsama_uam_max_minutes_of_basal_to_limit_smb",
    "ApsCarbsRequestThreshold":    "openapsama_carbs_request_threshold",
    "AutosensMax":                 "openapsama_autosens_max",
    "AutosensMin":                 "openapsama_autosens_min",
    "ApsLgsThreshold":             "openapsama_lgs_threshold",
}

GATE_KEYS = {
    "openapsama_use_autosens",
    "openapsama_use_autoisf_weights",
    "openapsama_use_smb",
    "openapsama_use_smb_always",
    "openapsama_use_smb_with_cob",
    "openapsama_use_smb_after_carbs",
    "openapsama_use_smb_with_high_tt",
    "openapsama_use_smb_with_low_tt",
    "openapsama_use_uam",
}


class AutoIsfKeyScanner:
    def __init__(self, source_root: str):
        self.source_root = Path(source_root)
        self.keys: Dict[str, dict] = {}

    def is_autoisf_key(self, key: str, enum_name: str = "") -> bool:
        if enum_name in AUTOISF_ENUM_NAMES or enum_name in SHARED_AUTOISF_KEYS:
            return True
        if key.startswith("autoisf_") or key.startswith("autosens_"):
            return True
        if enum_name.startswith("ApsAutoIsf") or enum_name.startswith("ApsUseAutoIsf"):
            return True
        return False

    def parse_all(self):
        for kf in KEY_FILES:
            path = self.source_root / kf
            if path.exists():
                self._parse_file(path)
        return self.keys

    def _parse_file(self, filepath: Path):
        content = filepath.read_text(encoding="utf-8")
        type_map = {"DoubleKey":"Double","BooleanKey":"Boolean","IntKey":"Int",
                     "UnitDoubleKey":"UnitDouble","LongKey":"Long","StringKey":"String"}
        class_match = re.search(r'enum\s+class\s+(\w+)\s*\(?\s*([\s\S]*?)\)?\s*:\s*(\w+)', content)
        if not class_match: return
        class_name = class_match.group(1)
        param_type = type_map.get(class_name, "Unknown")
        defaults = self._extract_defaults(content)

        entries = re.finditer(
            r'([A-Z][A-Za-z0-9]+)\s*\(\s*((?:[^()]|\([^)]*\))*?)\s*\)\s*,?\s*(?://[^\n]*)?\s*$',
            content, re.MULTILINE)
        for match in entries:
            name = match.group(1)
            if name in type_map: continue
            args_str = match.group(2)
            entry = self._parse_entry(name, args_str, param_type, defaults)
            if entry and self.is_autoisf_key(entry.get("key",""), name):
                self.keys[entry["key"]] = entry

    def _extract_defaults(self, content: str) -> dict:
        defaults = {}
        for m in re.finditer(r'override\s+(?:val|var)\s+(\w+)\s*:\s*(\S+)\s*=\s*([^,\n)]+)', content):
            v = m.group(3).strip()
            try:
                if v == "true": defaults[m.group(1)] = True
                elif v == "false": defaults[m.group(1)] = False
                elif v == "null": defaults[m.group(1)] = None
                else: defaults[m.group(1)] = v
            except: defaults[m.group(1)] = v
        return defaults

    def _parse_entry(self, name: str, args: str, ptype: str, defaults: dict) -> Optional[dict]:
        entry = {"name": name, "type": ptype}
        km = re.search(r'key\s*=\s*"([^"]+)"', args)
        if km: entry["key"] = km.group(1)
        else:
            pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
            if pos and pos[0].startswith('"'): entry["key"] = pos[0].strip('"')
            else: return None

        def _opt_num(pat: str, group: int = 1) -> Optional[float]:
            m = re.search(pat, args)
            return self._num(m.group(group)) if m else None

        if ptype in ("Double","Int","UnitDouble","Long"):
            pos = [a.strip() for a in re.findall(r'([^,]+(?:\([^)]*\))?)', args)]
            dv = _opt_num(r'defaultValue\s*=\s*([^,\n)]+)')
            if dv is not None:
                entry["default"] = dv
            elif ptype == "Double" and len(pos) >= 3:
                entry["default"] = self._num(pos[1])
            elif ptype == "Int" and len(pos) >= 3:
                entry["default"] = self._num(pos[1])

            mn = _opt_num(r'min\s*=\s*([^,\n)]+)')
            if mn is not None:
                entry["min"] = mn
            elif ptype == "Double" and len(pos) >= 4:
                entry["min"] = self._num(pos[2])

            mx = _opt_num(r'max\s*=\s*([^,\n)]+)')
            if mx is not None:
                entry["max"] = mx
            elif ptype == "Double" and len(pos) >= 5:
                entry["max"] = self._num(pos[3])

        if ptype == "Boolean":
            dm = re.search(r'defaultValue\s*=\s*(true|false)', args)
            if dm: entry["default"] = dm.group(1) == "true"
        dep = re.search(r'dependency\s*=\s*(?:BooleanKey\.)?(\w+)', args)
        if dep: entry["dependency"] = dep.group(1)
        unit = re.search(r'unitType\s*=\s*UnitType\.(\w+)', args)
        if unit: entry["unit_type"] = unit.group(1)
        step = re.search(r'step\s*=\s*([\d.]+)', args)
        if step: entry["step"] = self._num(step.group(1))
        entry["defaulted_by_sm"] = "defaultedBySM = true" in args
        entry["calculated_by_sm"] = "calculatedBySM = true" in args
        return entry

    @staticmethod
    def _num(s: str) -> float:
        s = s.strip().rstrip('fFLldD')
        try: return float(s) if '.' in s else int(s)
        except: return 0.0


class AutoIsfSourceScanner:
    def __init__(self, source_root: str):
        self.src_dir = Path(source_root) / AUTOISF_SRC
        self.files: Dict[str, str] = {}

    def scan_all(self):
        if not self.src_dir.exists(): return
        for kt in self.src_dir.rglob("*.kt"):
            self.files[str(kt.relative_to(self.src_dir))] = kt.read_text(encoding="utf-8")

    def find_usages(self, key: str, enum_name: str = "") -> List[dict]:
        usages = []
        patterns = [f'"{key}"']
        if enum_name:
            for cls in ("DoubleKey","BooleanKey","IntKey","UnitDoubleKey","LongKey","StringKey"):
                patterns.append(f"{cls}.{enum_name}")
        for fpath, content in self.files.items():
            for pat in patterns:
                if pat in content:
                    idx = content.index(pat)
                    funcs = list(re.finditer(r'(?:private\s+|internal\s+|override\s+)*fun\s+(\w+)', content[:idx]))
                    usages.append({"file": fpath.split("/")[-1],
                                   "function": funcs[-1].group(1) if funcs else "unknown"})
                    break
        return usages[:5]


# ─── Feature Groups ──────────────────────────────────────────────────────────

AUTOISF_FEATURE_GROUPS = {
    "isf_weights": {
        "id": "isf_weights", "name": "AutoISF Weights",
        "description": (
            "Core AutoISF weight system. Six weights control how different signals "
            "contribute to ISF adjustment: bgAccel (rising BG), bgBrake (falling BG), "
            "lowBg (hypo risk), highBg (hyper), pp (post-prandial), dura (duration). "
            "Weights are clamped by ApsAutoIsfMin/ApsAutoIsfMax and bounded by "
            "IobThPercent (IOB threshold % of basal)."
        ),
        "gate_key": "openapsama_use_autoisf_weights",
    },
    "smb_delivery": {
        "id": "smb_delivery", "name": "SMB Delivery Ratio",
        "description": (
            "Dynamic SMB delivery ratio that scales SMB size based on BG position. "
            "Ratio/slope with min/max bounds, BG range definition, and max range extension. "
            "SmbOnEvenTarget allows delivery even when BG equals target (no delta signal)."
        ),
        "gate_key": "openapsama_use_smb",
    },
    "temp_target_interaction": {
        "id": "temp_target_interaction", "name": "Temp Target Interaction",
        "description": (
            "How AutoISF interacts with temp targets. HighTtRaisesSens: high temp target → "
            "raise sensitivity (less insulin). LowTtLowersSens: low temp target → lower "
            "sensitivity (more insulin). HalfBasalExerciseTarget defines the BG for "
            "half-basal during exercise."
        ),
        "gate_key": None,
    },
    "shared_aaps": {
        "id": "shared_aaps", "name": "Shared AAPS Settings",
        "description": (
            "Standard OpenAPS settings that AutoISF also reads: max basal, max IOB, "
            "SMB gates, autosens, sensitivity/target interactions."
        ),
        "gate_key": None,
    },
}


# ─── Logic Summaries ─────────────────────────────────────────────────────────

def generate_summary(key: str, info: dict) -> str:
    ptype = info.get("type","")
    name = key.replace("openapsama_","").replace("autoisf_","").replace("autosens_","")

    templates = {
        "use_autoisf_weights": "Master gate: enables AutoISF dynamic ISF via weight-based signal blending.",
        "autoisf_min": "Minimum ISF multiplier (0.3-1.0). Lower bound for AutoISF adjustments. Lower = allows stronger ISF reduction (more insulin).",
        "autoisf_max": "Maximum ISF multiplier (1.0-3.0). Upper bound for AutoISF adjustments. Higher = allows stronger ISF increase (less insulin).",
        "bg_accel": "Weight for BG acceleration signal (rising BG). Higher = more ISF when BG rises = less insulin = slower correction.",
        "bg_brake": "Weight for BG braking signal (falling BG). Higher = ISF drops faster when BG falls = more insulin when needed.",
        "low_bg": "Weight for low BG risk. Higher = more ISF near hypo = less insulin = safer.",
        "high_bg": "Weight for high BG. Higher = less ISF when high = more insulin for hyper correction.",
        "pp": "Post-prandial weight. Higher = stronger ISF adjustment after meals.",
        "dura": "Duration weight. Higher = ISF adjustment persists longer.",
        "iob_th": "IOB threshold as % of basal. Below this IOB, AutoISF disengages (conservative).",
        "smb_delivery_ratio": "Base SMB delivery ratio (0.5-3.0). 1.0 = standard SMB. Higher = larger SMBs.",
        "smb_delivery_ratio_min": "Minimum SMB delivery ratio (0.3-1.0). Floor for BG-range-based scaling.",
        "smb_delivery_ratio_max": "Maximum SMB delivery ratio (1.0-5.0). Ceiling for aggressive SMB scaling.",
        "smb_bg_range": "BG range (mg/dL) over which SMB ratio scales from min to max.",
        "smb_max_range_extension": "Max range extension (mg/dL). Extends the BG window for SMB ratio scaling.",
        "smb_on_even": "When ON: SMB delivery allowed even when BG = target (no delta = neutral signal).",
        "high_tt_raises_sens": "When ON: high temp target raises ISF (less insulin during high TT).",
        "low_tt_lowers_sens": "When ON: low temp target lowers ISF (more insulin during low TT).",
        "half_basal_exercise": "BG target (mg/dL) at which basal is halved during exercise. Default 160.",
        "sensitivity_raises": "When ON: high sensitivity (autosens) raises BG target for safety.",
        "resistance_lowers": "When ON: insulin resistance (autosens) lowers BG target.",
        "always_use_short": "When ON: uses short delta (5min) instead of medium delta (15min) for all calculations.",
        "max_basal": "Maximum temporary basal rate (U/h). Shared safety limit.",
        "max_daily": "Maximum daily insulin multiplier × profile. Safety limit.",
        "max_current_basal": "Maximum current basal rate multiplier.",
        "smb_max_iob": "Maximum IOB for SMB delivery (U).",
        "use_smb": "Master SMB gate.",
        "use_autosens": "Enables autosens ratio for ISF adjustment.",
        "lgs_threshold": "Low Glucose Suspend threshold (mg/dL). Below this = suspend all insulin.",
        "autosens_max": "Maximum autosens ratio. 1.2 = 20% more ISF (less insulin).",
        "autosens_min": "Minimum autosens ratio. 0.7 = 30% less ISF (more insulin).",
    }
    for pat, desc in templates.items():
        if pat.lower() in name.lower(): return desc

    if ptype == "Boolean": return f"Enables/disables {name}. Gate flag for AutoISF."
    if ptype in ("Double","Int"): return f"Used in AutoISF algorithm for {name}."
    return f"Parameter for {name}."


def generate_effect(key: str, info: dict, direction: str) -> str:
    name = key.replace("openapsama_","").replace("autoisf_","")
    ptype = info.get("type","")
    if ptype == "Boolean":
        return "Feature is ON — associated functionality runs" if direction == "high" else "Feature is OFF — associated functionality is disabled"

    pats = {
        "autoisf_min": ("Allows stronger ISF reduction → more insulin → more aggressive",
                        "Limits ISF reduction → less insulin → more conservative"),
        "autoisf_max": ("Allows stronger ISF increase → less insulin → more conservative",
                        "Limits ISF increase → more insulin → more aggressive"),
        "bg_accel": ("ISF rises faster on BG rise → less insulin → slower correction",
                     "ISF rises slower → more insulin → faster correction"),
        "bg_brake": ("ISF drops faster on BG fall → more insulin → faster hypo stop",
                     "ISF drops slower → less insulin → safer"),
        "low_bg": ("Stronger ISF near hypo → less insulin → safer",
                   "Weaker ISF → more insulin near hypo → riskier"),
        "high_bg": ("Less ISF when high → more insulin for correction → faster fix",
                    "More ISF → less insulin → slower hyper correction"),
        "smb_delivery_ratio": ("Larger SMBs → more aggressive correction",
                               "Smaller SMBs → more conservative"),
        "iob_th": ("Higher threshold → AutoISF active at higher IOB → more responsive",
                   "Lower threshold → AutoISF only at low IOB → more conservative"),
    }
    for pat, (hi, lo) in pats.items():
        if pat.lower() in name.lower():
            return hi if direction == "high" else lo
    return "Higher value — more insulin/greater effect" if direction == "high" else "Lower value — less insulin/reduced effect"


def classify_impact(key: str, ptype: str) -> str:
    critical = ["smb_max_iob","use_smb","lgs_threshold","max_basal","max_daily","safety","hypo"]
    high = ["autoisf_min","autoisf_max","use_autoisf","bg_accel","bg_brake","low_bg","high_bg",
            "smb_delivery","iob_th","use_autosens","autosens_max","autosens_min"]
    for p in critical:
        if p in key.lower(): return "critical"
    for p in high:
        if p in key.lower(): return "high"
    return "medium"


def classify_fg(key: str) -> str:
    pats = {
        "autoisf_min":"isf_weights","autoisf_max":"isf_weights","bg_accel":"isf_weights",
        "bg_brake":"isf_weights","low_bg":"isf_weights","high_bg":"isf_weights",
        "pp":"isf_weights","dura":"isf_weights","iob_th":"isf_weights","use_autoisf_weights":"isf_weights",
        "smb_delivery":"smb_delivery","smb_bg_range":"smb_delivery","smb_max_range":"smb_delivery",
        "smb_on_even":"smb_delivery",
        "high_tt_raises":"temp_target_interaction","low_tt_lowers":"temp_target_interaction",
        "half_basal_exercise":"temp_target_interaction",
    }
    for pat, fg in pats.items():
        if pat.lower() in key.lower(): return fg
    return "shared_aaps"


# ─── Main ─────────────────────────────────────────────────────────────────────

def get_commit(root: str) -> str:
    try:
        return subprocess.run(["git","rev-parse","HEAD"], capture_output=True, text=True,
                             cwd=root, timeout=5).stdout.strip()[:40]
    except: return "unknown"


def generate():
    print("=== AutoISF Parameter Analyzer - Data Generator ===\n")
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-root", default=DEFAULT_SOURCE_ROOT)
    args = ap.parse_args()
    root = args.source_root
    if not root or not (Path(root) / "plugins/aps").exists():
        print("ERROR: Invalid source root. Pass --source-root.")
        sys.exit(1)

    commit = get_commit(root)
    print(f"Source: {root}  Commit: {commit[:8]}")

    print("\n1. Parsing key definitions...")
    scanner = AutoIsfKeyScanner(root)
    keys = scanner.parse_all()
    print(f"   Found {len(keys)} AutoISF-related keys")

    print("2. Scanning AutoISF source...")
    ss = AutoIsfSourceScanner(root)
    ss.scan_all()
    print(f"   Scanned {len(ss.files)} source files")

    print("3. Building parameter knowledge base...")
    params = []
    orphaned = 0
    for kstr, info in sorted(keys.items()):
        uname = info.get("name","")
        used = ss.find_usages(kstr, uname)
        info["used_in"] = used
        is_orph = not used and kstr not in GATE_KEYS and "_state_" not in kstr
        if is_orph: orphaned += 1
        is_gate = kstr in GATE_KEYS

        params.append({
            "key": kstr, "name": uname, "type": info.get("type","Unknown"),
            "default": info.get("default"), "min": info.get("min"), "max": info.get("max"),
            "used_in": used, "orphaned": is_orph,
            "negative_gate_key": None, "negative_gate_note": None,
            "logic_summary": generate_summary(kstr, info),
            "effect_high": generate_effect(kstr, info, "high"),
            "effect_low": generate_effect(kstr, info, "low"),
            "impact": classify_impact(kstr, info.get("type","")),
            "feature_group": classify_fg(kstr),
            "is_gate": is_gate,
            "settings_path": None, "settings_gate": info.get("dependency"),
        })

    active = len(params) - orphaned
    with_used = sum(1 for p in params if p["used_in"])
    gates = sum(1 for p in params if p["is_gate"])
    print(f"   Total: {len(params)} | Active: {active} | Orphaned: {orphaned} | With usages: {with_used} | Gates: {gates}")

    # Feature groups
    fg_list = []
    for fid, fgi in AUTOISF_FEATURE_GROUPS.items():
        fp = [p["key"] for p in params if p["feature_group"] == fid]
        fg_list.append({**fgi, "param_count": len(fp), "key_params": fp[:15]})

    # Output
    output = {
        "version": "1.0", "plugin": "autoisf",
        "source_commit": commit, "generated_at": datetime.now(timezone.utc).isoformat(),
        "total": len(params), "active": active, "orphaned": orphaned,
        "with_used_in": with_used, "with_negative_gate": 0, "with_settings_path": 0,
        "parameters": params,
        "feature_groups": {f["id"]: f for f in fg_list},
    }

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(DATA_DIR / "autoisf_parameters.json", "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n✅ autoisf_parameters.json ({len(params)} params)")

    # Context compact
    ctx = []
    ctx.append("AutoISF Plugin Algorithm Context\n" + "="*40)
    for f in fg_list:
        ctx.append(f"\n## {f['name']} (gate: {f.get('gate_key','none')})")
        ctx.append(f["description"])
        if f.get("key_params"):
            ctx.append(f"Key params: {', '.join(f['key_params'][:10])}")
    ctx.append("\n\n## Parameter Details")
    for p in params:
        if p.get("orphaned"): continue
        g = " [GATE]" if p.get("is_gate") else ""
        ctx.append(f"\n### {p['key']}{g} ({p['type']}, default={p.get('default','?')})")
        ctx.append(f"  {p['logic_summary']}")
        ctx.append(f"  High: {p['effect_high']} | Low: {p['effect_low']} | Impact: {p['impact']}")

    ctx_text = "\n".join(ctx)
    with open(DATA_DIR / "autoisf_context_compact.txt", "w", encoding="utf-8") as f:
        f.write(ctx_text)
    print(f"✅ autoisf_context_compact.txt ({len(ctx_text)} chars)")

    lookup = {p["key"]: {"summary": p["logic_summary"], "effect_high": p["effect_high"],
              "impact": p["impact"], "feature": p["feature_group"]}
              for p in params if not p.get("orphaned")}
    with open(DATA_DIR / "autoisf_param_lookup.json", "w", encoding="utf-8") as f:
        json.dump(lookup, f, indent=2)
    print(f"✅ autoisf_param_lookup.json ({len(lookup)} params)")

    ctx_json = {
        "plugin": "autoisf", "algorithm_overview": (
            "AutoISF (Automatic ISF) is a dynamic insulin sensitivity factor engine "
            "that blends 6 weighted signals (BG acceleration, BG brake, low BG risk, "
            "high BG, post-prandial, duration) to compute a real-time ISF multiplier. "
            "The multiplier is clamped between ApsAutoIsfMin and ApsAutoIsfMax and "
            "gated by an IOB threshold (% of basal). SMB delivery ratio scales SMB size "
            "based on BG position with configurable min/max, BG range, and extension. "
            "Temp target interaction controls how AutoISF responds to exercise/eating-soon "
            "targets. The existing AutoIsfAdvisorService provides heuristic recommendations "
            "that can complement AI analysis."
        ),
        "feature_groups": {f["id"]: f for f in fg_list},
        "parameter_summaries": lookup,
        "tuning_guidelines": (
            "1. Start with ApsUseAutoIsfWeights=ON, all weights at default (0.5). "
            "2. If hypos <3% and TIR >75%: tune individual weights, one at a time. "
            "3. If post-meal hypers: increase bgAccelWeight (0.5→0.7) to keep ISF higher during rise. "
            "4. If post-meal hypos: increase bgBrakeWeight (0.5→0.7) or raise lowBgWeight. "
            "5. If overnight hypos: increase lowBgWeight (0.5→0.8) and check halfBasalExerciseTarget. "
            "6. If too conservative overall: lower ApsAutoIsfMin (0.7→0.5) to allow stronger ISF reduction. "
            "7. If too aggressive: raise ApsAutoIsfMin (0.7→0.9) and lower ApsAutoIsfMax (1.3→1.1). "
            "8. SMB delivery ratio: adjust after ISF weights are stable. Increase for more SMB, decrease for less."
        ),
    }
    with open(DATA_DIR / "autoisf_context_for_ai.json", "w") as f:
        json.dump(ctx_json, f, indent=2)
    print(f"✅ autoisf_context_for_ai.json")

    print(f"\n=== Validation ===")
    print(f"✅ Active: {active} | Source usages: {with_used} | Feature groups: {len(fg_list)}")
    return 0


if __name__ == "__main__":
    sys.exit(generate())
