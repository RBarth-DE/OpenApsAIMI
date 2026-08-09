#!/usr/bin/env bash
# deploy.sh — Regenerate ALL plugin data files and push to NUC
#
# Usage:
#   bash analyzer/deploy.sh                 # data + restart (all you normally need)
#   bash analyzer/deploy.sh --full          # data + backend/frontend + restart
#   bash analyzer/deploy.sh --full --build  # data + backend/frontend + full rebuild
#
# Config: set NUC_HOST / NUC_USER / NUC_DIR env vars or edit below

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── NUC connection ───────────────────────────────────────────────────────────
NUC_HOST="${NUC_HOST:-NUC}"
NUC_USER="${NUC_USER:-nucuser}"
NUC_DIR="${NUC_DIR:-~/Tools/aimi-analyzer}"
NUC="${NUC_USER}@${NUC_HOST}"

FULL=false; REBUILD=false
for arg in "$@"; do
    case $arg in --full) FULL=true ;; --build) REBUILD=true; FULL=true ;; esac
done

PYTHON=""
if command -v python3 &>/dev/null; then PYTHON="python3"
elif command -v python &>/dev/null; then PYTHON="python"
else echo "❌ Python not found"; exit 1; fi

echo "════════════════════════════════════════"
echo "  AAPS Analyzer — Deploy to ${NUC_HOST}"
echo "  Target: ${NUC}:${NUC_DIR}"
echo "════════════════════════════════════════"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Step 1: Regenerate all plugin data files
# ──────────────────────────────────────────────────────────────────────────────
echo "📊 Regenerating data files..."

run_gen() {
    local label="$1"; shift
    echo "  ├─ ${label}..."
    $PYTHON "$@" || echo "     ⚠️  ${label} failed — continuing"
}

# AIMI
run_gen "AIMI parameters"      generate_data.py
run_gen "AIMI settings paths"  generate_settings_paths.py

# BOOST
run_gen "BOOST parameters"     generate_boost_data.py
run_gen "BOOST settings paths" generate_boost_settings_paths.py

# AutoISF
run_gen "AutoISF parameters"     generate_autoisf_data.py
run_gen "AutoISF settings paths" generate_autoisf_settings_paths.py

# Merge settings paths into each plugin's parameters JSON
echo "  ├─ Merging settings paths..."
$PYTHON -c "
import json
from pathlib import Path
DATA = Path('data')
ROOT = Path('.')
for plugin in ['aimi', 'boost', 'autoisf']:
    pf = DATA / f'{plugin}_parameters.json'
    sf = DATA / f'{plugin}_settings_paths.json'
    if not sf.exists(): sf = ROOT / f'{plugin}_settings_paths.json'
    if not pf.exists() or not sf.exists():
        print(f'     ⚠️  {plugin}: missing files, skipping')
        continue
    with open(sf) as f: paths = json.load(f).get('paths', {})
    with open(pf) as f: params = json.load(f)
    merged = 0
    for p in params['parameters']:
        if p['key'] in paths:
            p['settings_path'] = paths[p['key']]['path']
            merged += 1
    params['with_settings_path'] = merged
    with open(pf, 'w') as f: json.dump(params, f, indent=2, ensure_ascii=False)
    print(f'     ✅ {plugin}: {merged} paths merged')
"

echo "  └─ Done"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Step 2: Push data files to NUC
# ──────────────────────────────────────────────────────────────────────────────
echo "📤 Pushing data files to NUC..."
for plugin in aimi boost autoisf; do
    for suffix in parameters.json settings_paths.json context_compact.txt \
                  param_lookup.json context_for_ai.json context_for_ai.md; do
        f="data/${plugin}_${suffix}"
        if [ -f "$f" ]; then
            rsync -a "$f" "${NUC}:${NUC_DIR}/data/" 2>/dev/null || true
        fi
    done
done
# Also push aimi_context_for_ai.md (hand-written, not generated)
[ -f data/aimi_context_for_ai.md ] && rsync -a data/aimi_context_for_ai.md "${NUC}:${NUC_DIR}/data/" 2>/dev/null || true
# AIMI settings paths lives at root
[ -f aimi_settings_paths.json ] && rsync -a aimi_settings_paths.json "${NUC}:${NUC_DIR}/" 2>/dev/null || true
echo "  └─ Done"

# ──────────────────────────────────────────────────────────────────────────────
# Step 3: Push backend + frontend (only with --full)
# ──────────────────────────────────────────────────────────────────────────────
if $FULL; then
    echo ""
    echo "📤 Pushing backend + frontend..."
    rsync -a backend/app.py "${NUC}:${NUC_DIR}/backend/" 2>/dev/null || true
    rsync -a frontend/index.html "${NUC}:${NUC_DIR}/frontend/" 2>/dev/null || true
    echo "  └─ Done"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 4: Restart or rebuild on NUC
# ──────────────────────────────────────────────────────────────────────────────
echo ""
if $REBUILD; then
    echo "🐳 Rebuilding Docker on NUC..."
    ssh "${NUC}" "cd ${NUC_DIR} && docker compose up -d --build"
else
    echo "🐳 Restarting Docker on NUC..."
    ssh "${NUC}" "cd ${NUC_DIR} && docker compose restart"
fi

echo ""
echo "════════════════════════════════════════"
echo "  ✅ Deploy complete"
echo "  http://${NUC_HOST}:8765"
echo "════════════════════════════════════════"
