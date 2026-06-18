# Claude Code Task: Update AIMI Analyzer data files

## When to run this task

Run after any of these changes in the AIPS source repo:
- New parameter added to `BooleanKey.kt`, `DoubleKey.kt`, `IntKey.kt`, etc.
- Parameter removed or renamed
- New feature added to `DetermineBasalAIMI2.kt` or any AIMI module
- Settings screen restructured in `OpenAPSAIMIPlugin.kt`
- New module added (new `.kt` file in the AIMI directory)

## What this task does

1. Run `generate_data.py` to regenerate all four data files from source
2. Validate the output
3. Fix any validation errors
4. Copy files to the analyzer's data directory
5. Report what changed

---

## Step 1: Run the generator

```bash
cd ~/Tools/aimi-analyzer

python3 generate_data.py \
  --source-root ~/StudioProjects/OpenApsAIMI_V4

# Expected output:
# ✅ Validation passed
#    269 params (222 active)
#    117 parameter_knowledge entries
#    209 with settings_path
#    9 with negative_gate_key
```

If validation fails, go to **Step 2**. If it passes, go to **Step 3**.

---

## Step 2: Fix validation errors (only if Step 1 failed)

### Error: "German chars found"

The `generate_logic_summary()` or `generate_effect()` functions returned
German text. Find and fix in `generate_data.py`:

```python
# Search for German text sources
grep -n "äöüÄÖÜß\|Dämpfung\|Schwelle\|Erhöhung\|Senkung\|Basal\b" generate_data.py
```

Replace all German strings with English equivalents. Then re-run Step 1.

### Error: "Wrong T3c suppression on key_xyz"

The script incorrectly assigned `negative_gate_key` to a parameter that is
not in the verified T3C_SUPPRESSED_PARAMS set. Fix by adding the key to the
set **only if verified by grep**:

```bash
# Verify: is this param actually suppressed in executeT3cBrittleMode()?
grep -n "key_xyz\|OApsAIMIXyz" \
  ~/StudioProjects/OpenApsAIMI_V4/plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt \
  | awk -F: '$2 > 14416 && $2 < 14600'
# Zero results → confirmed suppressed → safe to add to T3C_SUPPRESSED_PARAMS
# Any results → NOT suppressed → do not add
```

### Error: "Active critical/high params missing settings_path"

A new critical/high parameter has no `settings_path`. Add it to the
`SETTINGS_PATH_OVERRIDES` dict in `generate_data.py`:

```python
SETTINGS_PATH_OVERRIDES = {
    # existing entries...
    "key_new_param": "AIMI → Preferences user → Section Name",
}
```

Find the correct path by checking `OpenAPSAIMIPlugin.kt` for where the
parameter appears in the compose screen functions.

### Error: "parameter_knowledge too small"

The `build_parameter_knowledge()` function produced fewer than 50 entries.
Check that `aimi_param_lookup.json` exists and has entries:

```bash
python3 -c "
import json
with open('data/aimi_param_lookup.json') as f: d = json.load(f)
print(f'Lookup entries: {len(d)}')
print(f'With summary: {sum(1 for v in d.values() if v.get(\"summary\"))}')
"
```

If the lookup is empty, the generator failed to produce summaries. Check
the `generate_logic_summary()` function for the new parameter type.

---

## Step 3: Verify output quality

```bash
python3 << 'EOF'
import json, re

files = {
    'aimi_parameters.json': 'data/aimi_parameters.json',
    'aimi_context_for_ai.json': 'data/aimi_context_for_ai.json',
    'aimi_param_lookup.json': 'data/aimi_param_lookup.json',
    'aimi_context_compact.txt': 'data/aimi_context_compact.txt',
}

for name, path in files.items():
    with open(path) as f:
        content = f.read()
    german = len(re.findall(r'[äöüÄÖÜß]', content))
    if name.endswith('.json'):
        d = json.loads(content)
        if name == 'aimi_parameters.json':
            params = d['parameters']
            active = sum(1 for p in params if not p.get('orphaned'))
            with_path = sum(1 for p in params if p.get('settings_path'))
            neg_gate = sum(1 for p in params if p.get('negative_gate_key'))
            print(f"{name}: {len(params)} total / {active} active / "
                  f"{with_path} with path / {neg_gate} neg_gate / "
                  f"{german} German chars")
        elif name == 'aimi_context_for_ai.json':
            features = len(d.get('features', {}))
            pk = len(d.get('parameter_knowledge', []))
            print(f"{name}: {features} features / {pk} param_knowledge / "
                  f"{german} German chars")
        elif name == 'aimi_param_lookup.json':
            with_path = sum(1 for v in d.values() if v.get('settings_path'))
            print(f"{name}: {len(d)} entries / {with_path} with path / "
                  f"{german} German chars")
    else:
        tokens = len(content) // 4
        print(f"{name}: {len(content)} chars (~{tokens} tokens) / "
              f"{german} German chars")
EOF
```

Expected output (all German chars = 0):
```
aimi_parameters.json:   ~270 total / ~220 active / >200 with path / 9 neg_gate / 0 German chars
aimi_context_for_ai.json: 30 features / >100 param_knowledge / 0 German chars
aimi_param_lookup.json: ~270 entries / >200 with path / 0 German chars
aimi_context_compact.txt: >20000 chars (~5000 tokens) / 0 German chars
```

---

## Step 4: Check what changed

```bash
# Compare with previous version if available
cd ~/Tools/aimi-analyzer

# How many params changed?
python3 << 'EOF'
import json

try:
    with open('data/aimi_parameters.json') as f:
        new = {p['key']: p for p in json.load(f)['parameters']}
    with open('data/aimi_parameters_backup.json') as f:
        old = {p['key']: p for p in json.load(f)['parameters']}

    added   = [k for k in new if k not in old]
    removed = [k for k in old if k not in new]
    changed = [k for k in new if k in old and
               new[k].get('orphaned') != old[k].get('orphaned')]

    print(f"Added:   {len(added)}   {added[:5]}")
    print(f"Removed: {len(removed)} {removed[:5]}")
    print(f"Changed orphan status: {len(changed)} {changed[:5]}")
except FileNotFoundError:
    print("No backup found — first run")
EOF
```

---

## Step 5: Deploy to analyzer

```bash
# Back up current files
cp data/aimi_parameters.json data/aimi_parameters_backup.json

# The generator already writes to data/ — just restart the container
docker compose restart

# Verify the API loaded the new data
curl -s http://localhost:8765/api/parameters | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(f'API: {d[\"active\"]} active / {d[\"total\"]} total parameters')
"
```

---

## Step 6: Update aimi_context_for_ai.md (manual, if needed)

The `.md` file is the **human-readable documentation** version of
`aimi_context_for_ai.json`. The script generates the JSON automatically,
but the MD needs manual updates when:

- A new feature is added with a meaningful description
- Algorithm logic changed significantly
- New "Key Differences from oref1" apply

To update the MD, read `data/aimi_context_for_ai.json` and:
1. For new features: add a `### feature_name` section with description
2. For changed params: update the key_params list
3. For new algorithm changes: add to "Key Differences" list

The MD is used for documentation only — the analyzer uses the JSON.

---

## Trigger summary

| Change in repo | Action needed |
|----------------|---------------|
| New `.kt` Key file entry | Run Step 1–5 |
| Parameter renamed | Run Step 1–5 |
| New feature module | Run Step 1–5 + Step 6 |
| Settings screen restructured | Run Step 1–5 only |
| Algorithm logic changed | Run Step 1–5 + Step 6 |
| Only comments changed | Skip — no action needed |

---

## Files managed by this task

| File | Generated by | Location |
|------|-------------|----------|
| `aimi_parameters.json` | `generate_data.py` | `~/Tools/aimi-analyzer/data/` |
| `aimi_context_for_ai.json` | `generate_data.py` | `~/Tools/aimi-analyzer/data/` |
| `aimi_context_compact.txt` | `generate_data.py` | `~/Tools/aimi-analyzer/data/` |
| `aimi_param_lookup.json` | `generate_data.py` | `~/Tools/aimi-analyzer/data/` |
| `aimi_context_for_ai.md` | Manual / Claude | `~/Tools/aimi-analyzer/data/` |
| `aimi_parameters_backup.json` | Step 5 | `~/Tools/aimi-analyzer/data/` |
