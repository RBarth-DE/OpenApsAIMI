# Claude Code Task: Update AIMI Analyzer data files

## Working directory
Run from **anywhere inside the repo** — the scripts auto-detect the repo root.

```bash
# From repo root:
python3 analyzer/generate_data.py

# Or from analyzer/:
cd analyzer && python3 generate_data.py
```

Both work. All output goes to `analyzer/data/` (in git).

---

## When to run

| Change in repo | Action |
|----------------|--------|
| New Key enum entry | Step 1 → Step 2 |
| New screen in `OpenAPSAIMIPlugin.kt` | Step 0 → Step 1 → Step 2 |
| New `.kt` module/feature | Step 1 → Step 2 + update `analyzer/data/aimi_context_for_ai.md` |
| New feature gate (boolean, default OFF) | Edit `analyzer/generate_data.py` → Step 1 → Step 2 |
| Only comments changed | Nothing |

---

## Step 0: Update settings paths
*(only when `OpenAPSAIMIPlugin.kt` screens changed)*

```bash
python3 analyzer/generate_settings_paths.py
# Output: analyzer/aimi_settings_paths.json
```

---

## Step 1: Regenerate data files

```bash
python3 analyzer/generate_data.py
```

Expected:
```
Source root: /path/to/OpenApsAIMI_V4
Output dir:  /path/to/OpenApsAIMI_V4/analyzer/data
✅ Validation passed
   ~270 params (~220 active), 9 with negative_gate_key, 0 German chars
```

---

## Step 2: Commit

```bash
git add analyzer/data/
git add analyzer/aimi_settings_paths.json   # if Step 0 was run
git commit -m "chore(analyzer): update data files"
```

---

## Fixing errors

### "Wrong T3c suppression on key_xyz"
```bash
grep -n "key_xyz" plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt \
  | awk -F: '$2 > 14416 && $2 < 14600'
# Zero results → add to T3C_SUPPRESSED_PARAMS in analyzer/generate_data.py
```

### "Active critical/high params missing settings_path"
```bash
grep -n "key_new_param" plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/OpenAPSAIMIPlugin.kt
# Then add to SETTINGS_PATH_OVERRIDES in analyzer/generate_data.py:
# "key_new_param": "AIMI → Preferences user → Section Name",
```

### New gate key (default OFF)
Add to `GATES_OFF_BY_DEFAULT` in `analyzer/generate_data.py`:
```python
"key_new_feature_gate",   # ← add here
```

---

## Output files (all in git)

```
analyzer/
├── data/
│   ├── aimi_parameters.json          ← generate_data.py
│   ├── aimi_context_for_ai.json      ← generate_data.py
│   ├── aimi_context_compact.txt      ← generate_data.py
│   ├── aimi_param_lookup.json        ← generate_data.py
│   └── aimi_context_for_ai.md       ← manual (new features only)
└── aimi_settings_paths.json          ← generate_settings_paths.py
```
