#!/usr/bin/env bash
# update.sh — Regenerate ALL plugin data files (local only, no NUC push)
# Run after any source change: bash analyzer/update.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON=""
if command -v python3 &>/dev/null; then PYTHON="python3"
elif command -v python &>/dev/null; then PYTHON="python"
else echo "❌ Python not found"; exit 1; fi

echo "🔄 Regenerating all plugin data..."
echo ""

run_gen() { local label="$1"; shift; echo "  ├─ ${label}..."; $PYTHON "$@" || echo "     ⚠️  ${label} failed"; }

# All three plugins
run_gen "AIMI"                scripts/generate_data.py
run_gen "AIMI paths"          scripts/generate_settings_paths.py
run_gen "BOOST"               scripts/generate_boost_data.py
run_gen "BOOST paths"         scripts/generate_boost_settings_paths.py
run_gen "AutoISF"             scripts/generate_autoisf_data.py
run_gen "AutoISF paths"       scripts/generate_autoisf_settings_paths.py

# Merge settings paths
echo "  ├─ Merging paths..."
$PYTHON -c "
import json; from pathlib import Path
DATA = Path('data')
ROOT = Path('.')
for p in ['aimi','boost','autoisf']:
    pf = DATA / f'{p}_parameters.json'
    sf = DATA / f'{p}_settings_paths.json'
    # AIMI writes to root, others to data/
    if not sf.exists(): sf = ROOT / f'{p}_settings_paths.json'
    if not pf.exists() or not sf.exists(): continue
    with open(sf) as f: paths = json.load(f).get('paths',{})
    with open(pf) as f: params = json.load(f)
    merged = 0
    for x in params['parameters']:
        if x['key'] in paths:
            x['settings_path'] = paths[x['key']]['path']
            merged += 1
    params['with_settings_path'] = merged
    with open(pf,'w') as f: json.dump(params,f,indent=2,ensure_ascii=False)
    print(f'     {p}: {merged} paths')
"

echo "  └─ Done"
echo ""
echo "🐳 Restarting container..."
docker compose restart 2>/dev/null || echo "  (Docker not running — skipping restart)"
echo ""
echo "✅ Done — http://localhost:8765"
