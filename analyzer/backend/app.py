#!/usr/bin/env python3
"""AIMI Parameter Analyzer - Backend v2 (dependency-map aware)"""

import json, os, math, statistics, base64, re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional, List

import httpx
from fastapi import FastAPI, HTTPException, Query, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel

try:
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.backends import default_backend
    from cryptography.exceptions import InvalidTag
    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False

app = FastAPI(title="AIMI Parameter Analyzer v2")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# ─── Load multi-plugin data ────────────────────────────────────────────────

PLUGIN_CONFIG = {
    "aimi": {
        "params_file": "aimi_parameters.json",
        "context_file": "aimi_context_compact.txt",
        "lookup_file": "aimi_param_lookup.json",
        "context_json": "aimi_context_for_ai.json",
        "label": "AIMI",
    },
    "boost": {
        "params_file": "boost_parameters.json",
        "context_file": "boost_context_compact.txt",
        "lookup_file": "boost_param_lookup.json",
        "context_json": "boost_context_for_ai.json",
        "label": "BOOST",
    },
    "autoisf": {
        "params_file": "autoisf_parameters.json",
        "context_file": "autoisf_context_compact.txt",
        "lookup_file": "autoisf_param_lookup.json",
        "context_json": "autoisf_context_for_ai.json",
        "label": "AutoISF",
    },
}

PLUGIN_DATA: dict[str, dict] = {}
for plug_id, cfg in PLUGIN_CONFIG.items():
    params_file = Path(f"/data/{cfg['params_file']}")
    if not params_file.exists():
        print(f"[{plug_id}] No data file: {params_file} — skipping")
        continue

    with open(params_file) as f:
        raw = json.load(f)

    parameters = raw.get("parameters", [])
    feature_groups_raw = raw.get("feature_groups", {})
    if isinstance(feature_groups_raw, list):
        feature_groups = {fg["id"]: fg for fg in feature_groups_raw}
    else:
        feature_groups = feature_groups_raw  # It's already a dict
    params_by_key = {p["key"]: p for p in parameters}
    active_params = [p for p in parameters if not p.get("orphaned")]

    context_compact = ""
    param_lookup = {}
    context_json = {}
    try:
        context_compact = Path(f"/data/{cfg['context_file']}").read_text(encoding="utf-8")
        with open(f"/data/{cfg['lookup_file']}", encoding="utf-8") as f:
            param_lookup = json.load(f)
        ctx_json_path = Path(f"/data/{cfg['context_json']}")
        if ctx_json_path.exists():
            with open(ctx_json_path, encoding="utf-8") as f:
                context_json = json.load(f)
    except Exception as e:
        print(f"[{plug_id}] Context not found: {e}")

    PLUGIN_DATA[plug_id] = {
        "parameters": parameters,
        "active_params": active_params,
        "params_by_key": params_by_key,
        "feature_groups": feature_groups,
        "context_compact": context_compact,
        "param_lookup": param_lookup,
        "context_json": context_json,
        "label": cfg["label"],
    }
    print(f"[{plug_id}] {len(parameters)} params, {len(active_params)} active, "
          f"{len(feature_groups)} feature groups, {len(context_compact)} ctx chars")

# Backward-compatible aliases
_default_plugin = "aimi"
PARAMETERS     = PLUGIN_DATA.get(_default_plugin, {}).get("parameters", [])
FEATURE_GROUPS = PLUGIN_DATA.get(_default_plugin, {}).get("feature_groups", {})
PARAMS_BY_KEY  = PLUGIN_DATA.get(_default_plugin, {}).get("params_by_key", {})
ACTIVE_PARAMS  = PLUGIN_DATA.get(_default_plugin, {}).get("active_params", [])
AIMI_CONTEXT_COMPACT = PLUGIN_DATA.get("aimi", {}).get("context_compact", "")
AIMI_PARAM_LOOKUP    = PLUGIN_DATA.get("aimi", {}).get("param_lookup", {})

print(f"Plugins loaded: {list(PLUGIN_DATA.keys())}")


def get_plugin_data(plugin: str = "aimi") -> dict:
    """Get plugin data, falling back to aimi if requested plugin not available."""
    return PLUGIN_DATA.get(plugin, PLUGIN_DATA.get("aimi", {}))

# ─── Models ───────────────────────────────────────────────────────────────────
class AnalysisRequest(BaseModel):
    nightscout_url: str
    nightscout_token: str = ""
    days: int = 7
    current_params: list = []   # list of param objects from import
    active_gates: dict = {}
    overrides: dict = {}
    plugin: str = "aimi"

class DiffAnalysisRequest(BaseModel):
    snapshot_a: dict
    snapshot_b: dict
    nightscout_url: str = ""
    nightscout_token: str = ""

class AIAnalysisRequest(BaseModel):
    provider: str
    api_key: str
    cgm_metrics: dict
    treatment_metrics: dict
    profile_data: dict = {}
    diff: list = []
    current_params: list = []
    nightscout_days: int = 7
    active_gates: dict = {}
    user_observations: str | None = None
    model: str | None = None
    plugin: str = "aimi"
    lang: str = "de"

# ─── AI History ───────────────────────────────────────────────────────────────
# Persistent memory for the AI: the last runs (proposal + metrics + params at
# that time) are stored server-side and fed back into the next prompt, so the
# model knows what it proposed before and what the user applied since.
HISTORY_FILE = Path("/history/ai_history.json")
HISTORY_MAX_ENTRIES = 30      # kept per file (all plugins together)
HISTORY_PROMPT_ENTRIES = 5    # shown in the prompt per plugin
HISTORY_ANALYSIS_TRIM = 1000  # chars of each old analysis shown in the prompt

def load_history() -> list:
    try:
        if not HISTORY_FILE.exists(): return []
        with open(HISTORY_FILE, encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except Exception as e:
        print(f"[history] load failed: {e}")
        return []

def write_history(entries: list) -> None:
    try:
        HISTORY_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(entries, f, indent=2, ensure_ascii=False)
    except Exception as e:
        print(f"[history] write failed: {e}")

def append_history_entry(entry: dict) -> None:
    entries = load_history()
    entries.append(entry)
    write_history(entries[-HISTORY_MAX_ENTRIES:])

def params_diff(prev: dict, cur: dict, lookup: dict) -> list:
    """Return list of {key, name, from, to} for params that changed between two snapshots."""
    changed = []
    for key in sorted(set(prev) | set(cur)):
        a, b = prev.get(key, "(absent)"), cur.get(key, "(absent)")
        if str(a) == str(b): continue
        changed.append({"key": key, "name": lookup.get(key, {}).get("name", key),
                        "from": a, "to": b})
    return changed

# ─── Nightscout ───────────────────────────────────────────────────────────────
async def ns_fetch(url, token, path, params={}):
    """Fetch from Nightscout. Tries token as query parameter first (broadest compatibility),
    falls back to api-secret header if no token provided."""
    query = {"count": 99999, **params}
    headers = {}
    if token:
        token = token.strip()
        query["token"] = token  # try ?token= first — works on most NS configs
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.get(f"{url.rstrip('/')}{path}", headers=headers, params=query)
        if r.status_code == 401 and token:
            # Fallback: try api-secret header without token query param
            query2 = {k: v for k, v in query.items() if k != "token"}
            r = await c.get(f"{url.rstrip('/')}{path}",
                            headers={"api-secret": token}, params=query2)
        r.raise_for_status()
        return r.json()

def to_ts(dt): return int(dt.timestamp() * 1000)
def from_ms(ms): return datetime.fromtimestamp(ms/1000, tz=timezone.utc)

# ─── CGM Analysis ─────────────────────────────────────────────────────────────
def analyze_cgm(entries, days):
    sgvs = [e.get("sgv",0) for e in entries if e.get("sgv",0) > 0]
    if not sgvs: return {}
    total = len(sgvs)
    mean_val = statistics.mean(sgvs)
    std_val  = statistics.stdev(sgvs) if len(sgvs)>1 else 0
    cv = std_val/mean_val*100 if mean_val else 0
    def pct(n): return round(n/total*100,1) if total else 0
    hourly = {h:[] for h in range(24)}
    for e in entries:
        sgv = e.get("sgv",0)
        if sgv<=0: continue
        ts = e.get("date") or e.get("dateString")
        try:
            dt = from_ms(ts) if isinstance(ts,(int,float)) else datetime.fromisoformat(str(ts).replace("Z","+00:00"))
            hourly[dt.hour].append(sgv)
        except: pass
    hourly_avg = {h: round(statistics.mean(v),1) if v else None for h,v in hourly.items()}
    night_vals = [v for h in [0,1,2,3,4,5] for v in hourly[h]]
    night_avg  = round(statistics.mean(night_vals),1) if night_vals else None
    hypo_events, in_hypo = 0, False
    for s in sgvs:
        if s<70 and not in_hypo: hypo_events+=1; in_hypo=True
        elif s>=80: in_hypo=False
    return {
        "total_readings": total, "days": days,
        "mean_glucose": round(mean_val,1), "std_glucose": round(std_val,1),
        "cv": round(cv,1), "gmi": round(3.31+0.02392*mean_val,2),
        "tir_pct": pct(sum(1 for v in sgvs if 70<=v<=180)),
        "hypo_l1_pct": pct(sum(1 for v in sgvs if v<70)),
        "hypo_l2_pct": pct(sum(1 for v in sgvs if v<54)),
        "hyper_l1_pct": pct(sum(1 for v in sgvs if v>180)),
        "hyper_l2_pct": pct(sum(1 for v in sgvs if v>250)),
        "hypo_events": hypo_events, "night_avg": night_avg,
        "hourly_avg": hourly_avg,
        "nocturnal_hyper": night_avg is not None and night_avg>150,
        "nocturnal_hypo":  night_avg is not None and night_avg<80,
        "high_cv": cv>36,
    }

def analyze_treatments(treatments):
    boluses = [t for t in treatments if t.get("eventType") in
               ("Meal Bolus","Correction Bolus","SMB","Bolus") or t.get("insulin")]
    carbs  = [t for t in treatments if t.get("carbs")]

    # SMB detection — AAPS/Nightscout is inconsistent here:
    #  - eventType == "SMB" (older / some loops)
    #  - eventType in ("Correction Bolus","Bolus") with isSMB == true
    #  - isSMB / is_smb / smb flag at top level (case variants)
    #  - notes/enteredBy containing "SMB" as last resort
    def is_smb(t):
        if t.get("eventType") == "SMB":
            return True
        if t.get("type") == "SMB":
            return True
        if t.get("isSMB") is True or t.get("is_smb") is True or t.get("smb") is True:
            return True
        for field in ("notes", "enteredBy", "reason"):
            v = t.get(field)
            if isinstance(v, str) and "smb" in v.lower():
                return True
        return False

    smbs = [t for t in treatments if is_smb(t) and t.get("insulin")]
    smb_sizes = [t.get("insulin",0) for t in smbs if t.get("insulin")]

    # TDD: group by calendar day and average
    from collections import defaultdict
    daily_totals = defaultdict(float)
    for t in boluses:
        try:
            day = t.get("created_at","")[:10] or from_ms(t.get("timestamp",0)).strftime("%Y-%m-%d")
            daily_totals[day] += float(t.get("insulin") or 0)
        except: pass
    tdd_avg = round(statistics.mean(daily_totals.values()), 1) if daily_totals else None

    return {
        "bolus_count": len(boluses),
        "total_insulin": round(sum(t.get("insulin",0) or 0 for t in boluses),1),
        "carb_events": len(carbs),
        "total_carbs": round(sum(t.get("carbs",0) or 0 for t in carbs),1),
        "smb_count": len(smbs),
        "smb_avg_size": round(statistics.mean(smb_sizes),3) if smb_sizes else None,
        "smb_max_size": round(max(smb_sizes),3) if smb_sizes else None,
        "tdd_avg": tdd_avg,
    }

# ─── Gate-aware recommendations ───────────────────────────────────────────────
def generate_recommendations(cgm, treatments, current_params, active_gates={}):
    """Rule-based recs filtered by active gates. current_params is a list of param dicts."""
    recs = []
    tir      = cgm.get("tir_pct",0)
    hypo_l1  = cgm.get("hypo_l1_pct",0)
    hypo_l2  = cgm.get("hypo_l2_pct",0)
    hyper_l1 = cgm.get("hyper_l1_pct",0)
    cv       = cgm.get("cv",0)
    night_avg= cgm.get("night_avg")
    hypo_ev  = cgm.get("hypo_events",0)

    # Build a key→value lookup from the list
    params_by_key = {}
    if isinstance(current_params, list):
        for p in current_params:
            if isinstance(p, dict) and p.get("key"):
                params_by_key[p["key"]] = p.get("value")
    elif isinstance(current_params, dict):
        params_by_key = current_params

    def cur(key):
        v = params_by_key.get(key)
        if v is None:
            p = PARAMS_BY_KEY.get(key,{})
            try: return float(p.get("default",0))
            except: return p.get("default")
        try: return float(v)
        except: return v

    # Gates that are OFF by default — unknown = inactive
    GATES_OFF_BY_DEFAULT = {
        "key_use_aimi_t3c_adaptive_basal", "key_use_aimi_t3c_brittle_mode",
        "key_aimi_pkpd_enabled", "key_use_Aimi_autoDrive",
        "key_use_aimi_autodrive_active", "key_use_Aimi_wcycle",
        "key_oaps_aimi_ngr_enabled", "key_use_AimiPregnancy",
        "key_use_Aimi_honeymoon", "aimi_physio_assistant_enable",
        "aimi_auditor_enabled", "key_aimi_trajectory_guard_enabled",
        "key_aimi_straight_line_tube_enabled", "key_aimi_context_enabled",
        "aimi_endo_enable", "key_aimi_thyroid_enabled",
        "aimi_emergency_sos_enable", "key_enable_ML_training",
        "key_use_Aimi_wcycle_shadow",
    }

    def gate_active(gate_key):
        if gate_key is None: return True
        v = active_gates.get(gate_key)
        if v is None: v = params_by_key.get(gate_key)
        if v is None:
            return gate_key not in GATES_OFF_BY_DEFAULT
        if isinstance(v, str): return v.lower() == "true"
        return bool(v)

    def is_suppressed_by_mode(p):
        """True if a negative gate is active — parameter has no effect in current mode."""
        neg = p.get("negative_gate_key")
        if not neg: return False
        return gate_active(neg)

    def add(key, suggested, reason, confidence="medium"):
        p = PARAMS_BY_KEY.get(key,{})
        if not p: return
        gate = p.get("gate_key")
        if gate and not gate_active(gate): return
        if is_suppressed_by_mode(p): return  # e.g. SMB params when T3c is active
        current_val = cur(key)
        recs.append({
            "key": key, "name": p.get("name", key),
            "category": p.get("feature_group","?"),
            "feature_name": p.get("feature_name",""),
            "impact": p.get("impact","medium"),
            "type": p.get("type","Double"),
            "current": current_val, "suggested": suggested,
            "default": p.get("default"), "min": p.get("min"), "max": p.get("max"),
            "reason": reason, "confidence": confidence,
            "gate_key": gate,
            "gate_active": gate_active(gate) if gate else True,
            "settings_path": p.get("settings_path",""),
            "settings_gate": p.get("settings_gate"),
            "logic_summary": p.get("logic_summary",""),
            "direction": "decrease" if isinstance(suggested,(int,float)) and isinstance(current_val,(int,float)) and suggested<current_val
                    else "increase" if isinstance(suggested,(int,float)) and isinstance(current_val,(int,float)) and suggested>current_val
                    else "toggle",
        })

    # ── Safety: too many hypos ─────────────────────────────────────────────
    if hypo_l1 > 4 or hypo_ev > 5:
        cur_smb = cur("key_openapsaimi_max_smb")
        if isinstance(cur_smb,(int,float)) and cur_smb > 0.5:
            add("key_openapsaimi_max_smb", round(max(0.3, cur_smb*0.8),2),
                f"Hypo-Rate {hypo_l1}% (Ziel <4%) — Max-SMB reduzieren", "high")
        if gate_active("key_use_aimi_t3c_adaptive_basal"):
            add("key_aimi_gov_hypo_bg_mgdl", 85.0,
                f"Raise Governance hypo threshold — {hypo_ev} hypo events", "high")

    # ── TIR too low, no hypo ──────────────────────────────────────────────
    if tir < 70 and hyper_l1 > 25 and hypo_l1 < 3:
        cur_smb = cur("key_openapsaimi_max_smb")
        if isinstance(cur_smb,(int,float)) and cur_smb < 2.0:
            add("key_openapsaimi_max_smb", round(min(2.0, cur_smb*1.2),2),
                f"TIR {tir}% — increase MaxSMB (hyper load: {hyper_l1}%)", "medium")
        if gate_active("key_use_aimi_t3c_adaptive_basal"):
            add("key_openapsaimi_high_bg_max_smb", round(min(3.0, cur("key_openapsaimi_high_bg_max_smb")*1.3),2),
                f"Raise HighBG MaxSMB — {hyper_l1}% time >180 mg/dL", "medium")

    # ── Nocturnal hyper → NGR ─────────────────────────────────────────────
    if cgm.get("nocturnal_hyper") and night_avg:
        if gate_active("key_oaps_aimi_ngr_enabled"):
            add("key_oaps_aimi_ngr_smb_multiplier",
                round(min(1.5, cur("key_oaps_aimi_ngr_smb_multiplier")+0.1),2),
                f"Nocturnal BG {night_avg} mg/dL — increase NGR SMB multiplier", "medium")
        else:
            add("key_oaps_aimi_ngr_enabled", True,
                f"Nocturnal avg {night_avg} mg/dL — consider enabling Night Growth Resistance", "high")

    # ── High CV ───────────────────────────────────────────────────────────
    if cv > 40:
        if gate_active("key_aimi_pkpd_enabled"):
            add("OApsAIMISmbTailDamping", round(min(0.9, cur("OApsAIMISmbTailDamping")+0.15),2),
                f"CV {cv}% — raise SMB Tail Damping", "medium")

    # ── T3c brittle: high CV + both hypo + hyper ──────────────────────────
    if cv > 45 and hypo_l1 > 3 and hyper_l1 > 30:
        if not gate_active("key_aimi_t3c_brittle_mode"):
            add("key_aimi_t3c_brittle_mode", True,
                f"CV {cv}%, Hypo {hypo_l1}%, Hyper {hyper_l1}% — consider T3c Brittle Mode", "low")

    # ── Feature recommendations (enable/disable) ──────────────────────────
    feature_recs = []

    def fadd(gate_key, suggested_state, reason, confidence="medium", risk="mittel",
             feature_name=None, settings_path=None):
        p = PARAMS_BY_KEY.get(gate_key, {})
        feature_recs.append({
            "type": "feature",
            "key": gate_key,
            "name": feature_name or p.get("name", gate_key),
            "feature_group": p.get("feature_group","?"),
            "suggested_state": suggested_state,
            "current_state": gate_active(gate_key),
            "direction": "enable" if suggested_state else "disable",
            "reason": reason,
            "confidence": confidence,
            "risk": risk,
            "settings_path": p.get("settings_path") or settings_path or "",
            "impact": "high",
        })

    # PKPD Pragmatic Relief — safety buffer, should be on with PKPD active
    if gate_active("key_aimi_pkpd_enabled") and not gate_active("key_aimi_pkpd_pragmatic_relief_enabled"):
        fadd("key_aimi_pkpd_pragmatic_relief_enabled", True,
             "PKPD is active but Pragmatic Relief is disabled — this safety buffer "
             "dampens PKPD corrections when the model is uncertain. Should be active.",
             confidence="high", risk="niedrig")

    # Straight Line Tube — useful when Trajectory Guard active but Tube not
    if gate_active("key_aimi_trajectory_guard_enabled") and not gate_active("key_aimi_straight_line_tube_enabled"):
        fadd("key_aimi_straight_line_tube_enabled", True,
             "Trajectory Guard is active without Straight Line Tube — Tube gives the Guard a "
             "concrete target curve and can reduce CV.",
             confidence="medium", risk="niedrig")

    # NGR — for nocturnal hyper without NGR active
    if cgm.get("nocturnal_hyper") and not gate_active("key_oaps_aimi_ngr_enabled"):
        if night_avg and night_avg > 150:
            fadd("key_oaps_aimi_ngr_enabled", True,
                 f"Nocturnal avg {night_avg} mg/dL without NGR — Night Growth Resistance "
                 f"may reduce nocturnal hypers.",
                 confidence="medium", risk="niedrig")

    # Autodrive v3 — wenn viele Hypers aber kein Autodrive
    if hyper_l1 > 30 and tir < 65 and hypo_l1 < 4:
        if not gate_active("key_use_aimi_autodrive_active"):
            fadd("key_use_aimi_autodrive_active", True,
                 f"High hyper load ({hyper_l1}%) with acceptable hypo rate ({hypo_l1}%) — "
                 "Autodrive v3 may improve correction efficiency.",
                 confidence="low", risk="mittel")

    # T3c Adaptive Basal — unabhängiges Feature, kein T3c Brittle Mode nötig
    if hypo_l1 > 5 and not gate_active("key_use_aimi_t3c_adaptive_basal"):
        fadd("key_use_aimi_t3c_adaptive_basal", True,
             f"Hypo rate {hypo_l1}% — T3c Adaptive Basal activates the Neural Learner for "
             "dynamic basal scaling (independent of T3c Brittle Mode). "
             "Proactively reduces basal rate on BG drop.",
             confidence="medium", risk="niedrig")

    # Warnung: MaxSMB hoch + Autodrive v3 aktiv
    max_smb_val = cur("key_openapsaimi_max_smb")
    if gate_active("key_use_aimi_autodrive_active") and isinstance(max_smb_val, (int,float)) and max_smb_val > 1.5:
        fadd("key_use_aimi_autodrive_active", True,
             f"MaxSMB={max_smb_val} U with Autodrive v3 active — Autodrive can fully "
             "can fully utilise MaxSMB. For new setup recommended: MaxSMB ≤1.5 U for first week.",
             confidence="high", risk="mittel",
             feature_name="⚠️ MaxSMB + Autodrive v3 Kombination")

    # Pragmatic Relief min_factor zu niedrig
    pragmatic_factor = cur("aimi_pkpd_pragmatic_relief_min_factor")
    if isinstance(pragmatic_factor, (int,float)) and pragmatic_factor < 0.65:
        fadd("key_aimi_pkpd_pragmatic_relief_enabled", True,
             f"PKPD Pragmatic Relief Min-Factor={pragmatic_factor} (default 0.75) is very low — "
             "re-enable and raise Min-Factor to ≥0.65.",
             confidence="medium", risk="niedrig",
             feature_name="⚠️ PKPD Pragmatic Relief Min-Factor")

    recs.extend(feature_recs)

    # Sort
    iord = {"critical":0,"high":1,"medium":2,"low":3}
    cord = {"high":0,"medium":1,"low":2}
    recs.sort(key=lambda r: (cord.get(r["confidence"],2), iord.get(r["impact"],2)))
    return recs

# ─── API Endpoints ─────────────────────────────────────────────────────────────
@app.get("/api/parameters")
def get_parameters(
    plugin: str = "aimi",
    category: Optional[str] = None,
    impact: Optional[str] = None,
    gate_key: Optional[str] = None,
    always_active: Optional[bool] = None,
    include_orphaned: bool = False,
):
    pd = get_plugin_data(plugin)
    params = pd.get("active_params", []) if not include_orphaned else pd.get("parameters", [])
    if category:    params = [p for p in params if p.get("feature_group","").lower() == category.lower()]
    if impact:      params = [p for p in params if p.get("impact","").lower() == impact.lower()]
    if gate_key:    params = [p for p in params if p.get("gate_key") == gate_key]
    if always_active is not None:
        params = [p for p in params if p.get("always_active") == always_active]
    return {"total": len(params), "active_total": len(pd.get("active_params", [])), "parameters": params, "plugin": plugin}

@app.get("/api/feature-groups")
def get_feature_groups(plugin: str = "aimi"):
    pd = get_plugin_data(plugin)
    fg_map = pd.get("feature_groups", {})
    active_params = pd.get("active_params", [])
    result = []
    for fg_id, fg in fg_map.items():
        params_in_group = [p for p in active_params if p.get("feature_group") == fg_id]
        result.append({
            "id": fg_id,
            "name": fg.get("name", fg_id),
            "description": fg.get("description",""),
            "gate_key": fg.get("gate_key"),
            "gate_type": fg.get("gate_type","boolean"),
            "param_count": len(params_in_group),
            "always_active": fg.get("gate_key") is None,
        })
    result.sort(key=lambda x: (0 if x["always_active"] else 1, x["name"]))
    return result

def analyze_profile(ns_profile, trt={}):
    """Extract key profile values from Nightscout /api/v1/profile.json"""
    try:
        # NS returns list or single object
        if isinstance(ns_profile, list):
            profile = ns_profile[0] if ns_profile else {}
        else:
            profile = ns_profile

        # Get the default/active store
        default_store = profile.get("defaultProfile", "Default")
        stores = profile.get("store", {})
        store = stores.get(default_store) or (list(stores.values())[0] if stores else {})

        def avg_schedule(schedule):
            """Average a time-based schedule [{time, value, timeAsSeconds}]"""
            if not schedule: return None
            try: return round(sum(float(e.get("value",0)) for e in schedule) / len(schedule), 1)
            except: return None

        def first_value(schedule):
            if not schedule: return None
            try: return float(schedule[0].get("value", 0))
            except: return None

        isf_schedule  = store.get("sens", [])
        basal_schedule = store.get("basal", [])
        ic_schedule   = store.get("carbratio", [])
        target_low    = store.get("target_low", [])
        target_high   = store.get("target_high", [])

        avg_isf   = avg_schedule(isf_schedule)
        avg_basal = avg_schedule(basal_schedule)
        avg_ic    = avg_schedule(ic_schedule)
        daily_basal = round(avg_basal * 24, 1) if avg_basal else None

        tgt_lo = avg_schedule(target_low)
        tgt_hi = avg_schedule(target_high)
        target = round((tgt_lo + tgt_hi) / 2, 0) if tgt_lo and tgt_hi else (tgt_lo or tgt_hi)

        # Insulin type from dia or insulin field
        dia   = store.get("dia") or profile.get("dia")
        units = store.get("units", "mg/dl")

        # Recommended ISF based on TDD (1700 rule for rapid insulin, ~1600 for Lyumjev)
        tdd = trt.get("tdd_avg")
        recommended_isf = round(1700 / tdd, 0) if tdd and tdd > 0 else None
        isf_delta_pct   = round((avg_isf - recommended_isf) / recommended_isf * 100, 0) \
                          if avg_isf and recommended_isf else None

        # Recommended basal: 40-50% of TDD
        recommended_basal_daily = round(tdd * 0.45, 1) if tdd else None
        basal_pct_tdd = round(daily_basal / tdd * 100, 0) if daily_basal and tdd else None

        return {
            "profile_name": default_store,
            "units": units,
            "dia": dia,
            "avg_isf": avg_isf,
            "avg_basal_per_hour": avg_basal,
            "daily_basal": daily_basal,
            "avg_ic": avg_ic,
            "target": target,
            "recommended_isf": recommended_isf,
            "isf_delta_pct": isf_delta_pct,
            "recommended_basal_daily": recommended_basal_daily,
            "basal_pct_tdd": basal_pct_tdd,
            "tdd_used": tdd,
            "isf_schedule": isf_schedule[:6],   # first 6 entries for display
            "basal_schedule": basal_schedule[:6],
        }
    except Exception as e:
        return {"error": str(e)}


def generate_profile_recommendations(profile, cgm, trt):
    """Generate profile-level recommendations — AIMI-aware.

    NOTE: Standard 1700/TDD ISF rule and 40-50% basal rule do NOT apply to AIMI:
    - AIMI dynamically multiplies basal in meal modes and high-BG situations,
      so basal% of TDD is naturally 55-75% — not a sign of miscalibration.
    - AIMI scales ISF internally via ISF Fusion and DynISF Trajectory.
      The profile ISF is only an anchor; the algorithm adapts it per tick.

    We still show profile values as context but only flag genuine outliers.
    """
    recs = []
    if not profile or profile.get("error"): return recs

    avg_isf   = profile.get("avg_isf")
    rec_isf   = profile.get("recommended_isf")
    delta     = profile.get("isf_delta_pct")
    tdd       = profile.get("tdd_used", 0) or 0
    daily_b   = profile.get("daily_basal")
    basal_pct = profile.get("basal_pct_tdd")
    hypo      = cgm.get("hypo_l1_pct", 0)
    tir       = cgm.get("tir_pct", 0)

    # ISF: only flag extreme outliers (>40% off), with AIMI context
    if avg_isf and rec_isf and delta is not None:
        if delta < -40:
            recs.append({
                "type": "profile", "impact": "medium",
                "name": "Profile ISF very aggressive (anchor value)",
                "key": "Profile → Insulin Sensitivity Factor",
                "current": f"{avg_isf} mg/dL/U",
                "suggested": f"{rec_isf} mg/dL/U (1700/TDD reference)",
                "direction": "increase",
                "reason": f"Profile ISF {avg_isf} mg/dL/U is {abs(int(delta))}% below the 1700/TDD reference "
                          f"({rec_isf} mg/dL/U for TDD {tdd:.0f}U). "
                          f"Note: AIMI scales ISF dynamically via ISF Fusion — the profile value is only the "
                          f"anchor. A very low anchor may cause overcorrection before AIMI adapts. "
                          f"Consider raising gradually if post-correction hypos are frequent.",
                "settings_path": "AAPS → Profile → ISF",
                "category": "Profile", "feature_name": "Profile",
                "gate_key": None, "gate_active": True,
            })
        elif delta > 50:
            recs.append({
                "type": "profile", "impact": "low",
                "name": "Profile ISF very conservative (anchor value)",
                "key": "Profile → Insulin Sensitivity Factor",
                "current": f"{avg_isf} mg/dL/U",
                "suggested": f"{rec_isf} mg/dL/U (1700/TDD reference)",
                "direction": "decrease",
                "reason": f"Profile ISF {avg_isf} mg/dL/U is {int(delta)}% above the 1700/TDD reference. "
                          f"AIMI will adapt via ISF Fusion, but a very high anchor may slow initial corrections. "
                          f"Low priority since AIMI compensates dynamically.",
                "settings_path": "AAPS → Profile → ISF",
                "category": "Profile", "feature_name": "Profile",
                "gate_key": None, "gate_active": True,
            })

    # Basal: AIMI uses basal as a meal-coverage tool — 55-75% is normal.
    # Only flag if basal is extremely low (algorithm can't cover meals via basal scaling)
    if daily_b and basal_pct is not None:
        if basal_pct < 30:
            recs.append({
                "type": "profile", "impact": "medium",
                "name": "Profile basal very low for AIMI",
                "key": "Profile → Basal Rate",
                "current": f"{daily_b}U/day ({basal_pct:.0f}% of TDD)",
                "suggested": f"≥35% of TDD ({round(tdd*0.35,1)}U/day minimum)",
                "direction": "increase",
                "reason": f"Basal is only {basal_pct:.0f}% of TDD. AIMI uses basal scaling in meal modes "
                          f"and high-BG situations — a very low profile basal limits the algorithm's "
                          f"ability to cover meals without relying entirely on SMBs. "
                          f"Note: 55-70% basal-of-TDD is normal with AIMI (unlike standard APS).",
                "settings_path": "AAPS → Profile → Basal Rate",
                "category": "Profile", "feature_name": "Profile",
                "gate_key": None, "gate_active": True,
            })
        # Do NOT flag high basal% — 60-75% is expected with AIMI meal mode scaling

    return recs


@app.post("/api/analyze")
async def analyze(req: AnalysisRequest):
    try:
        now   = datetime.now(timezone.utc)
        since = now - timedelta(days=req.days)
        entries = await ns_fetch(req.nightscout_url, req.nightscout_token,
            "/api/v1/entries.json", {"find[date][$gte]": to_ts(since), "count": 99999})
        treatments = await ns_fetch(req.nightscout_url, req.nightscout_token,
            "/api/v1/treatments.json", {"find[created_at][$gte]": since.isoformat(), "count": 99999})
        cgm  = analyze_cgm(entries, req.days)
        trt  = analyze_treatments(treatments)

        # Fetch current profile from Nightscout
        profile_data = {}
        try:
            ns_profile = await ns_fetch(req.nightscout_url, req.nightscout_token,
                "/api/v1/profile.json", {})
            profile_data = analyze_profile(ns_profile, trt)
        except Exception:
            pass  # profile optional

        # Determine active gates from current_params list + explicit active_gates dict
        active_gates = dict(req.active_gates)
        for p in req.current_params:
            if isinstance(p, dict):
                k, v = p.get("key"), p.get("value")
                if k and (isinstance(v, bool) or str(v).lower() in ("true","false")):
                    active_gates[k] = v if isinstance(v, bool) else str(v).lower() == "true"
        for k, v in req.overrides.items():
            active_gates[k] = v
        recs = generate_recommendations(cgm, trt, req.current_params, active_gates)

        # Profile-based recommendations
        profile_recs = generate_profile_recommendations(profile_data, cgm, trt)
        recs = profile_recs + recs  # profile recs first

        recent_cutoff = to_ts(now - timedelta(hours=24))
        recent = sorted([e for e in entries if e.get("date",0)>=recent_cutoff],
                        key=lambda e: e.get("date",0))
        glucose_chart = [{"ts": e.get("date"), "sgv": e.get("sgv"),
                          "time": from_ms(e["date"]).strftime("%H:%M") if e.get("date") else ""}
                         for e in recent[-288:]]
        hourly_chart = [{"hour": h, "avg": cgm.get("hourly_avg",{}).get(h)} for h in range(24)]

        return {"cgm": cgm, "treatments": trt, "recommendations": recs,
                "profile": profile_data,
                "hourly_chart": hourly_chart, "glucose_chart": glucose_chart,
                "fetched_at": now.isoformat(), "entry_count": len(entries),
                "active_gates": active_gates}
    except httpx.HTTPStatusError as e:
        raise HTTPException(502, f"Nightscout HTTP error: {e.response.status_code}")
    except httpx.RequestError as e:
        raise HTTPException(502, f"Nightscout connection error: {e}")

@app.post("/api/decrypt-export")
async def decrypt_export(file: UploadFile = File(...), password: str = Form(...)):
    if not CRYPTO_AVAILABLE:
        raise HTTPException(500, "cryptography library fehlt")
    content = await file.read()
    try: outer = json.loads(content.decode("utf-8"))
    except: raise HTTPException(400, "Invalid JSON file")
    if "content" not in outer:
        raise HTTPException(400, "Kein 'content' Feld — ist das eine AAPS-Export-Datei?")
    security = outer.get("security",{})
    salt_hex = security.get("salt")
    iterations = security.get("iterations", 50000)
    if not salt_hex: raise HTTPException(400, "Kein 'salt' Feld")
    if security.get("algorithm") != "v1": raise HTTPException(400, f"Unbekannter Algorithmus: {security.get('algorithm')}")
    try:
        salt = bytes.fromhex(salt_hex)
        kdf = PBKDF2HMAC(algorithm=hashes.SHA1(), length=32, salt=salt,
                         iterations=iterations, backend=default_backend())
        key = kdf.derive(password.encode("utf-8"))
        raw2 = base64.b64decode(outer["content"])
        iv_len = raw2[0]; iv = raw2[1:1+iv_len]; ct = raw2[1+iv_len:]
        plaintext = AESGCM(key).decrypt(iv, ct, None)
    except InvalidTag:
        raise HTTPException(401, "Wrong master password or corrupted file")
    except Exception as e:
        raise HTTPException(500, f"Decryption error: {e}")
    try: inner = json.loads(plaintext.decode("utf-8"))
    except: raise HTTPException(500, "Decrypted but not valid JSON")

    def is_sensitive(k): return any(s in k.lower() for s in ('_key','_token','_pin','_secret','_password'))

    # Build a unified param lookup across ALL plugins
    all_params_by_key = {}
    for plug_id, pd in PLUGIN_DATA.items():
        all_params_by_key.update(pd.get("params_by_key", {}))

    all_prefs = inner if isinstance(inner, dict) else {}
    enriched = []
    for k, v in all_prefs.items():
        # Try the unified lookup first, fall back to AIMI-only for backward compat
        param_def = all_params_by_key.get(k) or PARAMS_BY_KEY.get(k, {})
        enriched.append({
            "key": k, "value": "••••••••" if is_sensitive(k) and v else v,
            "value_length": len(str(v)) if is_sensitive(k) and v else None,
            "name": param_def.get("name", k),
            "feature_group": param_def.get("feature_group","unknown"),
            "feature_name": param_def.get("feature_name",""),
            "gate_key": param_def.get("gate_key"),
            "always_active": param_def.get("always_active", True),
            "impact": param_def.get("impact","medium"),
            "type": param_def.get("type","String"),
            "default": param_def.get("default"),
            "min": param_def.get("min"), "max": param_def.get("max"),
            "is_default": str(v) == str(param_def.get("default")) if param_def.get("default") is not None else None,
            "settings_path": param_def.get("settings_path"),
            "has_definition": bool(param_def),
            "is_sensitive": is_sensitive(k),
            "orphaned": param_def.get("orphaned", not bool(param_def)),
        })

    # Determine active gates from decrypted prefs
    active_gates = {}
    for k, v in all_prefs.items():
        if isinstance(v, bool): active_gates[k] = v
        elif str(v).lower() in ("true","false"): active_gates[k] = str(v).lower() == "true"

    enriched.sort(key=lambda x: (
        0 if x["always_active"] else 1,
        {"critical":0,"high":1,"medium":2,"low":3}.get(x["impact"],2),
        x["feature_group"], x["key"]
    ))
    orphaned_count = sum(1 for p in enriched if p["orphaned"])
    non_default_count = sum(1 for p in enriched if p.get("is_default") is False and not p["orphaned"])

    # Calculate export age from metadata
    meta = outer.get("metadata", {})
    export_ts = meta.get("created_at") or meta.get("exportDate") or meta.get("date")
    export_age_hours = None
    if export_ts:
        try:
            from datetime import datetime, timezone
            if isinstance(export_ts, (int, float)):
                # Unix timestamp in ms
                export_dt = datetime.fromtimestamp(export_ts / 1000, tz=timezone.utc)
            else:
                export_dt = datetime.fromisoformat(str(export_ts).replace("Z", "+00:00"))
            export_age_hours = round((datetime.now(timezone.utc) - export_dt).total_seconds() / 3600, 1)
        except Exception:
            pass

    return {
        "metadata": meta,
        "export_age_hours": export_age_hours,
        "security_info": {"iterations": iterations, "algorithm": "PBKDF2WithHmacSHA1+AES/GCM"},
        "total_preferences": len(all_prefs),
        "aimi_count": len([p for p in enriched if p["has_definition"]]),
        "orphaned_count": orphaned_count,
        "non_default_count": non_default_count,
        "active_gates": active_gates,
        "aimi_parameters": enriched,
    }

# ─── Diff ──────────────────────────────────────────────────────────────────────
def _diff_direction(a, b):
    try:
        fa, fb = float(str(a)), float(str(b))
        return "increase" if fb>fa else "decrease" if fb<fa else "same"
    except:
        if a is None and b is not None: return "added"
        if a is not None and b is None: return "removed"
        return "changed"

@app.post("/api/diff")
async def diff_snapshots(req: DiffAnalysisRequest):
    def to_dict(params):
        """Accept either {key:value} dict or [{key, value}] list."""
        if isinstance(params, dict): return params
        if isinstance(params, list):
            return {p["key"]: p.get("value") for p in params if isinstance(p, dict) and p.get("key")}
        return {}

    params_a = to_dict(req.snapshot_a.get("parameters", req.snapshot_a.get("params", {})))
    params_b = to_dict(req.snapshot_b.get("parameters", req.snapshot_b.get("params", {})))
    ts_a = req.snapshot_a.get("timestamp","")
    ts_b = req.snapshot_b.get("timestamp","")
    # Also accept importedParams key (from new frontend snapshot format)
    if not params_a: params_a = to_dict(req.snapshot_a.get("importedParams",[]))
    if not params_b: params_b = to_dict(req.snapshot_b.get("importedParams",[]))
    def is_sensitive(k): return any(s in k.lower() for s in ('_key','_token','_pin','_secret','_password'))

    all_keys = set(params_a)|set(params_b)
    diff = []
    for key in sorted(all_keys):
        val_a, val_b = params_a.get(key), params_b.get(key)
        if str(val_a)==str(val_b): continue
        p = PARAMS_BY_KEY.get(key,{})
        diff.append({
            "key": key, "name": p.get("name",key),
            "feature_group": p.get("feature_group","unknown"),
            "feature_name": p.get("feature_name",""),
            "gate_key": p.get("gate_key"),
            "always_active": p.get("always_active",True),
            "impact": p.get("impact","medium"),
            "type": p.get("type","String"),
            "value_a": "••••••••" if is_sensitive(key) else val_a,
            "value_b": "••••••••" if is_sensitive(key) else val_b,
            "default": p.get("default"), "min": p.get("min"), "max": p.get("max"),
            "direction": _diff_direction(val_a, val_b),
            "is_sensitive": is_sensitive(key),
            "orphaned": p.get("orphaned", not bool(p)),
        })
    iord = {"critical":0,"high":1,"medium":2,"low":3}
    diff.sort(key=lambda x: (iord.get(x["impact"],2), x["feature_group"], x["key"]))

    metrics_a, metrics_b, deltas = {}, {}, {}
    if req.nightscout_url and ts_a and ts_b:
        try:
            dt_a = datetime.fromisoformat(ts_a.replace("Z","+00:00"))
            dt_b = datetime.fromisoformat(ts_b.replace("Z","+00:00"))
            now  = datetime.now(timezone.utc)
            pd   = min(max(1,(dt_b-dt_a).days), 14)
            ea = await ns_fetch(req.nightscout_url, req.nightscout_token, "/api/v1/entries.json",
                {"find[date][$gte]": to_ts(dt_a-timedelta(days=pd)), "find[date][$lt]": to_ts(dt_b), "count":99999})
            metrics_a = analyze_cgm(ea, pd)
            eb = await ns_fetch(req.nightscout_url, req.nightscout_token, "/api/v1/entries.json",
                {"find[date][$gte]": to_ts(dt_b), "find[date][$lt]": to_ts(min(dt_b+timedelta(days=pd),now)), "count":99999})
            metrics_b = analyze_cgm(eb, pd)
            for m in ("tir_pct","hypo_l1_pct","hypo_l2_pct","hyper_l1_pct","cv","gmi","mean_glucose"):
                va, vb = metrics_a.get(m), metrics_b.get(m)
                if va is not None and vb is not None: deltas[m] = round(vb-va,2)
        except Exception as e:
            metrics_a = {"error": str(e)}

    return {"timestamp_a": ts_a, "timestamp_b": ts_b,
            "metadata_a": req.snapshot_a.get("metadata",{}),
            "metadata_b": req.snapshot_b.get("metadata",{}),
            "diff": diff, "changed_count": len(diff),
            "changed_active": sum(1 for d in diff if not d.get("orphaned")),
            "changed_orphaned": sum(1 for d in diff if d.get("orphaned")),
            "metrics_a": metrics_a, "metrics_b": metrics_b, "deltas": deltas}

# ─── AI Analysis ───────────────────────────────────────────────────────────────
@app.post("/api/ai-analysis")
async def ai_analysis(req: AIAnalysisRequest):
    plugin = req.plugin or "aimi"
    pd = get_plugin_data(plugin)
    plugin_label = pd.get("label", plugin.upper())
    plugin_ctx = pd.get("context_compact", "")
    plugin_lookup = pd.get("param_lookup", {})
    plugin_fg = pd.get("feature_groups", {})
    plugin_params = pd.get("params_by_key", {})

    cgm, trt = req.cgm_metrics, req.treatment_metrics
    has_cgm    = bool(cgm and cgm.get("tir_pct") is not None)
    has_params = bool(req.current_params)
    if not has_cgm and not has_params:
        raise HTTPException(422, "No data. Please run the Dashboard analysis and import your AAPS settings first.")

    lang = req.lang or "de"
    is_en = lang == "en"

    # Build unified gate state from all sources
    # Priority: req.active_gates (from import) > current_params gate_active field > param value > default off
    # Collect all gate keys from the selected plugin's feature groups
    GATES_OFF_BY_DEFAULT = {
        "key_use_aimi_t3c_adaptive_basal", "key_aimi_t3c_brittle_mode",
        "key_aimi_pkpd_enabled", "key_use_Aimi_autoDrive",
        "key_use_aimi_autodrive_active", "key_use_Aimi_wcycle",
        "key_oaps_aimi_ngr_enabled", "key_use_AimiPregnancy",
        "key_use_Aimi_honeymoon", "aimi_physio_assistant_enable",
        "aimi_auditor_enabled", "key_aimi_trajectory_guard_enabled",
        "key_aimi_straight_line_tube_enabled", "key_aimi_context_enabled",
        "aimi_endo_enable", "key_aimi_thyroid_enabled",
        "aimi_emergency_sos_enable", "key_enable_ML_training",
    }
    # Add the selected plugin's feature group gate keys (they are off-by-default)
    for fg_id, fg in plugin_fg.items():
        gk = fg.get("gate_key")
        if gk:
            GATES_OFF_BY_DEFAULT.add(gk)

    # Merge all gate sources into one dict
    effective_gates = {}
    # 1. Start with GATES_OFF_BY_DEFAULT as baseline
    for k in GATES_OFF_BY_DEFAULT:
        effective_gates[k] = False
    # 2. Override with values from current_params list
    for p in req.current_params:
        k = p.get("key")
        if not k: continue
        # Use gate_active field if present (set by frontend from activeGates)
        if "gate_active" in p and p.get("gate_key") == k:
            effective_gates[k] = bool(p["gate_active"])
        # Use value for boolean params
        v = p.get("value")
        if isinstance(v, bool):
            effective_gates[k] = v
        elif str(v).lower() in ("true","false"):
            effective_gates[k] = str(v).lower() == "true"
    # 3. Override with explicit active_gates (highest priority — set by import)
    for k, v in req.active_gates.items():
        if isinstance(v, bool):
            effective_gates[k] = v
        elif str(v).lower() in ("true","false"):
            effective_gates[k] = str(v).lower() == "true"

    def eff_gate(gate_key):
        if gate_key is None: return True
        return effective_gates.get(gate_key, gate_key not in GATES_OFF_BY_DEFAULT)

    # Active features from gates
    active_features = []
    inactive_features = []
    for fg_id, fg in plugin_fg.items():
        gate = fg.get("gate_key")
        if gate is None:
            active_features.append(fg.get("name", fg_id))
        else:
            if eff_gate(gate):
                active_features.append(fg.get("name", fg_id))
            else:
                inactive_features.append(fg.get("name", fg_id))

    # Params to show: non-default, not orphaned, not sensitive, active gate
    def gate_ok(p):
        gate = p.get("gate_key")
        if gate is None: return True
        if "gate_active" in p: return bool(p["gate_active"])
        return eff_gate(gate)

    def neg_gate_suppressed(p):
        neg = plugin_params.get(p.get("key",""), {}).get("negative_gate_key")
        if not neg: return False
        return eff_gate(neg)

    # AAPS core params that exist in any AAPS settings export but have NO effect
    # when AIMI is active (they belong to AutoISF, SMB-standard, or oref1 modes)
    AAPS_NOT_AIMI = {
        "ApsAutoIsfMin", "ApsAutoIsfMax", "ApsDynIsfAdjustmentFactor",
        "ApsDynIsfAdjustSensitivity", "ApsUseAutosens", "ApsAutosensMax",
        "ApsAutosensMin", "openapsma_max_iob", "openapsma_min_bg",
        "openapsma_max_bg", "openapsama_current_basal",
    }

    def fmt(p):
        v, d = p.get("value","?"), p.get("default","?")
        mn, mx = p.get("min"), p.get("max")
        rng = f" [{mn}–{mx}]" if mn else ""
        try:
            delta = f" (Δ{float(v)-float(d):+.3g})" if float(str(v)) != float(str(d)) else ""
        except: delta=""
        # For boolean params: show [ACTIVE] / [INACTIVE] clearly so AI doesn't suggest changing
        if isinstance(v, bool) or str(v).lower() in ("true","false"):
            is_on = v is True or str(v).lower() == "true"
            delta = f" [{'✅ CURRENTLY ACTIVE' if is_on else '❌ CURRENTLY INACTIVE'}]"
        ctx_info = plugin_lookup.get(p.get("key",""), {})
        summary = ctx_info.get("summary","")
        path = p.get("settings_path") or ctx_info.get("settings_path","")
        gate_key = p.get("gate_key")
        gate_state = f" [GATE: {'ACTIVE' if eff_gate(gate_key) else 'INACTIVE — this parameter has NO effect in current mode'}]" if gate_key else ""
        path_line = f"\n    📍 {path}" if path else ""
        summary_line = f"\n    → {summary}" if summary else ""
        return f"  [{p.get('impact','?').upper()}] {p.get('name',p.get('key'))}: {v} (default:{d}{rng}){delta}{gate_state}{path_line}{summary_line}"

    active_non_default = [p for p in req.current_params
        if p.get("is_default") is False and not p.get("is_sensitive")
        and not p.get("orphaned") and gate_ok(p) and not neg_gate_suppressed(p)
        and p.get("key","") not in AAPS_NOT_AIMI          # exclude non-AIMI AAPS params
        and p.get("name","") not in AAPS_NOT_AIMI]        # also check by name

    # Filter to only show params belonging to the selected plugin
    if plugin != "aimi":
        active_non_default = [p for p in active_non_default
            if p.get("key","") in plugin_params]
        # Re-split critical/high after filtering
        critical_high = [p for p in active_non_default if p.get("impact") in ("critical","high")]
        other = [p for p in active_non_default if p.get("impact") not in ("critical","high")]
    else:
        critical_high = [p for p in active_non_default if p.get("impact") in ("critical","high")]
        other = [p for p in active_non_default if p.get("impact") not in ("critical","high")]

    # Parameters that exist in settings but are suppressed by active mode
    suppressed = [p for p in req.current_params
        if p.get("is_default") is False and not p.get("is_sensitive")
        and not p.get("orphaned") and gate_ok(p) and neg_gate_suppressed(p)]
    suppressed_note = ""
    if suppressed:
        names = ", ".join(p.get("name", p.get("key","")) for p in suppressed[:8])
        mode = PARAMS_BY_KEY.get(suppressed[0].get("key",""), {}).get("negative_gate_key","active mode")
        suppressed_note = (
            f"\n⚠️ Suppressed by active mode "
            f"({mode}): {names}\n"
            f"These parameters are set but have NO effect while this mode is active — do NOT recommend changing them.\n"
        )

    # Also include T3c-specific params when T3c is active (even if at default)
    t3c_extra = ""
    if eff_gate("key_aimi_t3c_brittle_mode"):
        t3c_keys = ["key_aimi_t3c_aggressiveness", "key_aimi_t3c_activation_threshold",
                    "key_aimi_adaptive_basal_max_scaling"]
        t3c_params = [p for p in req.current_params if p.get("key") in t3c_keys]
        if t3c_params:
            t3c_extra = f"\n\nT3c Brittle Mode active — T3c-specific params:\n" + \
                "\n".join(fmt(p) for p in t3c_params)

    params_block = ""
    if critical_high:
        params_block += f"Critical/High (deviating from default, feature active):\n" + "\n".join(fmt(p) for p in critical_high[:30])
    if other:
        params_block += f"\n\nMedium/Low:\n" + "\n".join(fmt(p) for p in other[:15])
    if t3c_extra:
        params_block += t3c_extra
    if suppressed_note:
        params_block += suppressed_note
    if not params_block:
        params_block = "All active parameters at default values."

    diff_block = "\n".join(
        f"  [{p['impact'].upper()}] {p['name']}: {p['value_a']} → {p['value_b']} ({p['direction']})"
        for p in req.diff[:30] if not p.get("is_sensitive") and not p.get("orphaned")
    ) or "No diff available"

    # AI history: last runs of this plugin with automatic diffs.
    # For each past run the prompt shows its metrics, its proposal, and which
    # params changed after it (diff vs. the next run, or vs. current params for
    # the newest entry). This breaks the proposal back-and-forth between runs.
    history_entries = [h for h in load_history() if h.get("plugin") == plugin][-HISTORY_PROMPT_ENTRIES:]
    params_snapshot = {p.get("key"): p.get("value") for p in active_non_default if p.get("key")}
    history_block = ""
    if history_entries:
        def fnum(v): return "?" if v is None else v
        lines = [f"## Analysis history (last {len(history_entries)} runs of {plugin_label}, oldest first)"]
        for i, h in enumerate(history_entries):
            m = h.get("metrics", {})
            lines.append(f"- {h.get('ts','?')[:10]} · TIR {fnum(m.get('tir_pct'))}% · CV {fnum(m.get('cv'))}% · Hypo {fnum(m.get('hypo_l1_pct'))}%")
            analysis = (h.get("analysis") or "").strip()
            if analysis:
                cut = analysis[:HISTORY_ANALYSIS_TRIM] + ("…" if len(analysis) > HISTORY_ANALYSIS_TRIM else "")
                lines.append("  Previous proposal: " + cut)
            next_params = history_entries[i + 1].get("params", {}) if i + 1 < len(history_entries) else params_snapshot
            changes = params_diff(h.get("params", {}), next_params, plugin_params)
            if changes:
                lines.append("  Changes applied after this run: " + "; ".join(
                    f"{c['name']} (`{c['key']}`) {c['from']} → {c['to']}" for c in changes))
        lines.append("")
        lines.append("Rules when using the history:")
        lines.append("- If a previous proposal was already applied (see 'Changes applied after this run') and the metrics did not improve, do NOT repeat it — propose a different direction or reverting the change.")
        lines.append("- Briefly state where you agree or disagree with the previous analysis.")
        history_block = "\n".join(lines) + "\n"

    # User observations — optional free-text from the user
    if req.user_observations:
        user_obs_block = f"\n## User Observations\n\"{req.user_observations}\"\n\n"
        user_obs_task = """4. Verify or refute user observations:
   - Does the description match the CGM data? (which metrics confirm/refute)
   - What is the likely cause (parameter, timing, physiology)?
   - Which parameter change would most directly address the described problem?
"""
    else:
        user_obs_block = ""
        user_obs_task = ""

    # AIMI algorithm context (from source code analysis)
    plugin_ctx_block = f"\n{plugin_ctx}\n" if plugin_ctx else ""

    # Plugin-specific AI instructions
    if plugin == "aimi":
        plugin_instructions = """## AIMI-specific knowledge (apply when analysing these settings)
- Autodrive V2 vs V3: `key_use_Aimi_autoDrive=false` + `key_use_aimi_autodrive_active=true` is the CORRECT and INTENDED configuration for Autodrive V3. The old V2 key is deprecated and should remain false. Do NOT flag this as a contradiction or bug.
- `OApsAIMIMLtraining=true` means ML training is ALREADY ACTIVE. Do not recommend enabling it if it is already true.
- AutoISF parameters (ApsAutoIsfMin, ApsAutoIsfMax, ApsDynIsfAdjustmentFactor) have NO effect when AIMI is active. Do not recommend changing these.
- Parameters marked [✅ CURRENTLY ACTIVE] are boolean features that are already enabled — do not recommend enabling them.
- Parameters marked [❌ CURRENTLY INACTIVE] are boolean features that are currently off.
"""
    elif plugin == "boost":
        plugin_instructions = """## BOOST-specific knowledge (apply when analysing these settings)
- `boost_use_tdd=ON` is the main gate for TDD-based dynamic ISF. If OFF, Boost uses static profile ISF (less adaptive).
- `boost_insulin_req_pct` is the PRIMARY aggressiveness control: >100% = more insulin, <100% = more conservative.
- `boost_insulin_req_pct` is a PERCENTAGE (range 30–100), NOT insulin units. A proposal like "reduce to 3.0 U" is invalid — a conservative value is e.g. 40%.
- `boost_dynisf_velocity` controls how FAST ISF adapts to changing conditions — high values can cause oscillation.
- `boost_night_mode_enabled` is critical for overnight safety — recommend enabling if overnight hypos.
- ISF Shadow (V4.4.2 EMA) is DIAGNOSTIC ONLY — it does NOT affect dosing. No action needed on shadow values.
- Meal model V5/V6/V7 parameters only apply when the corresponding meal hypothesis gates are enabled.
- Activity settings (boost_activity_pct, boost_inactivity_pct) work together: activity reduces insulin, inactivity increases it.
- Parameters marked [✅ CURRENTLY ACTIVE] are boolean features that are already enabled.
- Parameters marked [❌ CURRENTLY INACTIVE] are boolean features that are currently off.
"""
    else:
        plugin_instructions = """## Parameters marked [✅ CURRENTLY ACTIVE] are boolean features that are already enabled.
- Parameters marked [❌ CURRENTLY INACTIVE] are boolean features that are currently off.
"""

    LABELS = {
        "active_features":   "Active Features",
        "inactive_features": "Inactive Features (ignore their parameters)",
        "cgm_metrics":       "CGM Metrics",
        "treatment":         "Treatment",
        "params_header":     "Active Parameters (deviating from default)",
        "changes":           "Recent Parameter Changes",
        "observations":      "User Observations",
        "task":              "Task",
        "lang_instruction":  "English. No generic disclaimers.",
        "task_verify":       "4. Verify or refute user observations:\n   - Does the description match the CGM data? (which metrics confirm/refute)\n   - What is the likely cause (parameter, timing, physiology)?\n   - Which parameter change would most directly address the described problem?",
    }

    l = LABELS
    plugin_note = (
        f"\n⚠️ IMPORTANT: You are analyzing the **{plugin_label}** plugin ONLY. "
        f"The parameters listed below are exclusively {plugin_label}-specific settings. "
        f"Do NOT recommend changes to parameters from other plugins (AIMI, AutoISF, standard OpenAPS SMB) "
        f"unless they appear in the list below. Only use parameter keys and settings paths "
        f"that are explicitly provided in this prompt.\n"
    ) if plugin != "aimi" else ""

    prompt = f"""You are an expert on AndroidAPS and the {plugin_label} plugin (branch dev_OAPSAIMI_RB).
{plugin_note}
{plugin_ctx_block}
{plugin_instructions}

## {l['active_features']}
{', '.join(active_features[:20]) or '—'}

## {l['inactive_features']}
{', '.join(inactive_features[:15]) or '—'}

## {l['cgm_metrics']} ({req.nightscout_days} days)
- TIR 70–180: {cgm.get('tir_pct','?')}% (target ≥70%)
- Hypo <70: {cgm.get('hypo_l1_pct','?')}% (target <4%)
- Hypo <54: {cgm.get('hypo_l2_pct','?')}%
- Hyper >180: {cgm.get('hyper_l1_pct','?')}%
- Mean glucose: {cgm.get('mean_glucose','?')} mg/dL
- CV: {cgm.get('cv','?')}% (target <36%)
- GMI: {cgm.get('gmi','?')}%
- Night avg: {cgm.get('night_avg','?')} mg/dL
- Hypo events: {cgm.get('hypo_events','?')}

## {l['treatment']}
- SMBs: {trt.get('smb_count','?')} · Ø {trt.get('smb_avg_size','?')} U · Max {trt.get('smb_max_size','?')} U
- Carbs: {trt.get('total_carbs','?')} g · Insulin: {trt.get('total_insulin','?')} U
- TDD avg: {trt.get('tdd_avg','?')} U/day

## Profile
{f"""- Profile ISF: {req.profile_data.get('avg_isf','?')} mg/dL/U  (1700/TDD reference: {req.profile_data.get('recommended_isf','?')} mg/dL/U, delta: {req.profile_data.get('isf_delta_pct','?')}%)
- Basal: {req.profile_data.get('avg_basal_per_hour','?')} U/h = {req.profile_data.get('daily_basal','?')} U/day ({req.profile_data.get('basal_pct_tdd','?')}% of TDD)
- IC ratio: {req.profile_data.get('avg_ic','?')} g/U
- BG target: {req.profile_data.get('target','?')} mg/dL
- DIA: {req.profile_data.get('dia','?')} h
- Profile: {req.profile_data.get('profile_name','?')}
IMPORTANT AIMI-SPECIFIC CONTEXT:
- Basal% of TDD: AIMI dynamically multiplies basal in meal modes and high-BG situations.
  55-75% basal-of-TDD is NORMAL with AIMI — do NOT flag this as too high.
  Only flag if basal < 30% of TDD (algorithm cannot scale sufficiently).
- ISF: AIMI scales ISF internally via ISF Fusion and DynISF Trajectory per tick.
  The profile ISF is only an anchor point. Do NOT apply the 1700/TDD rule directly.
  Only flag extreme outliers (>40% off) as potentially affecting the anchor point.
- Do NOT recommend ISF or basal changes based on standard APS rules."""
if req.profile_data and not req.profile_data.get('error') else '(not available)'}

## {l['params_header']}
{params_block}

## {l['changes']}
{diff_block}
{history_block}
{user_obs_block}
## {l['task']}
1. Evaluate metrics (3–4 sentences, specific)
2. 3–8 optimizations — only for ACTIVE features:
   - Exact key (in backticks, e.g. `key_openapsaimi_max_smb`)
   - Settings path: EVERY recommendation MUST include the 📍 path exactly as shown next to the parameter above. Copy it verbatim — do NOT invent, shorten, or translate. This is mandatory for all sections (critical, secondary, feature recommendations).
   - Current → Recommendation (with value)
   - Value: SAME unit as the current value (percent stays percent, U stays U, mg/dL stays mg/dL) and inside the [min–max] range shown next to the parameter. Never mix units — do NOT propose U for a % parameter or % for a U parameter.
   - Reason
   - Risk: low/medium/high
3. Feature recommendations (enable/disable):
   - Which features should be enabled given the CGM data and current config?
   - Which active features might be causing problems or are redundant?
   - Format: ✅ ENABLE key_xxx / 🚫 DISABLE key_xxx — Reason — Risk
4. Dangerous parameter combinations?
{user_obs_task}{'5' if not user_obs_task else '6'}. Next steps (priority: safety > TIR > CV)

{l['lang_instruction']}"""

    if req.provider == "anthropic": result = await _call_anthropic(req.api_key, prompt, req.model)
    elif req.provider == "deepseek":  result = await _call_deepseek(req.api_key, prompt, req.model)
    elif req.provider == "openai":    result = await _call_openai(req.api_key, prompt, req.model)
    else: raise HTTPException(400, f"Unknown provider: {req.provider}")

    # Remember this run so the next analysis knows the previous proposals
    append_history_entry({
        "ts": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "plugin": plugin,
        "metrics": {k: cgm.get(k) for k in ("tir_pct", "cv", "hypo_l1_pct", "hyper_l1_pct")},
        "params": params_snapshot,
        "analysis": result.get("analysis", ""),
    })
    return result

async def _call_anthropic(api_key, prompt, model=None):
    model = model or "claude-sonnet-4-6"
    async with httpx.AsyncClient(timeout=120) as c:
        r = await c.post("https://api.anthropic.com/v1/messages",
            headers={"x-api-key": api_key, "anthropic-version":"2023-06-01",
                     "content-type":"application/json"},
            json={"model": model, "max_tokens":4000,
                  "messages":[{"role":"user","content":prompt}]})
        if not r.is_success: raise HTTPException(502, f"Anthropic: {r.status_code} {r.text[:200]}")
        d = r.json()
        return {"provider":"anthropic","model":d.get("model","?"),
                "analysis":d["content"][0]["text"],"tokens":d.get("usage",{})}

async def _call_deepseek(api_key, prompt, model=None):
    model = model or "deepseek-chat"
    # R1 (deepseek-reasoner) returns reasoning_content + content separately
    is_r1 = "reasoner" in model
    async with httpx.AsyncClient(timeout=120) as c:
        r = await c.post("https://api.deepseek.com/v1/chat/completions",
            headers={"Authorization":f"Bearer {api_key}","Content-Type":"application/json"},
            json={"model": model, "max_tokens":6000,
                  "messages":[{"role":"user","content":prompt}]})
        if not r.is_success: raise HTTPException(502, f"DeepSeek: {r.status_code} {r.text[:200]}")
        d = r.json()
        msg = d["choices"][0]["message"]
        # R1: prepend reasoning summary if present
        analysis = msg.get("content","")
        if is_r1 and msg.get("reasoning_content"):
            reasoning = msg["reasoning_content"]
            # Show first 300 chars of reasoning as collapsed context
            analysis = f"**Reasoning (R1):** _{reasoning[:300]}{'…' if len(reasoning)>300 else ''}_\n\n---\n\n{analysis}"
        return {"provider":"deepseek","model":d.get("model","?"),
                "analysis":analysis,"tokens":d.get("usage",{})}

async def _call_openai(api_key, prompt, model=None):
    model = model or "gpt-4o-mini"
    async with httpx.AsyncClient(timeout=120) as c:
        r = await c.post("https://api.openai.com/v1/chat/completions",
            headers={"Authorization":f"Bearer {api_key}","Content-Type":"application/json"},
            json={"model": model, "max_tokens":4000,
                  "messages":[{"role":"user","content":prompt}]})
        if not r.is_success: raise HTTPException(502, f"OpenAI: {r.status_code} {r.text[:200]}")
        d = r.json()
        return {"provider":"openai","model":d.get("model","?"),
                "analysis":d["choices"][0]["message"]["content"],
                "tokens":d.get("usage",{})}

@app.get("/api/ai-history")
def get_ai_history(plugin: Optional[str] = None):
    entries = load_history()
    if plugin: entries = [h for h in entries if h.get("plugin") == plugin]
    return {"entries": entries}

@app.delete("/api/ai-history")
def clear_ai_history(plugin: Optional[str] = None):
    if plugin:
        write_history([h for h in load_history() if h.get("plugin") != plugin])
    else:
        write_history([])
    return {"status": "ok"}

@app.get("/api/plugins")
def list_plugins():
    return {
        "plugins": {plug_id: {"label": pd["label"],
                              "total_params": len(pd.get("parameters",[])),
                              "active_params": len(pd.get("active_params",[])),
                              "feature_groups": len(pd.get("feature_groups",{})),
                              "has_context": bool(pd.get("context_compact"))}
                    for plug_id, pd in PLUGIN_DATA.items()}
    }

@app.get("/api/health")
def health():
    d = PLUGIN_DATA.get("aimi", {})
    return {"status":"ok","parameters":len(d.get("parameters",[])),"active":len(d.get("active_params",[])),
            "feature_groups":len(d.get("feature_groups",{})),"plugins": list(PLUGIN_DATA.keys())}

@app.get("/api/debug/treatments")
async def debug_treatments(nightscout_url: str, nightscout_token: str = "", hours: int = 6):
    """Inspect raw Nightscout treatment fields to diagnose SMB detection."""
    now = datetime.now(timezone.utc)
    since = now - timedelta(hours=hours)
    treatments = await ns_fetch(nightscout_url, nightscout_token,
        "/api/v1/treatments.json", {"find[created_at][$gte]": since.isoformat(), "count": 200})
    boluses = [t for t in treatments if t.get("insulin")]
    # Show all keys present + a few full samples
    all_keys = set()
    for t in boluses: all_keys.update(t.keys())
    return {
        "count": len(treatments),
        "bolus_count": len(boluses),
        "all_keys_seen_on_boluses": sorted(all_keys),
        "samples": boluses[:10],
    }

@app.get("/")
def root(): return FileResponse("/frontend/index.html")
app.mount("/", StaticFiles(directory="/frontend", html=True), name="static")
