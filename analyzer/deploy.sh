#!/usr/bin/env bash
# deploy.sh — Regenerate data files and push to NUC
# Run on the LAPTOP after any AIMI source change
#
# Usage:
#   bash analyzer/deploy.sh                  # data files only
#   bash analyzer/deploy.sh --full           # data + backend/frontend
#   bash analyzer/deploy.sh --full --build   # data + backend/frontend + docker rebuild
#
# Config: edit the NUC_* variables below, or set them as env vars before running

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── NUC connection config ──────────────────────────────────────────────────
NUC_HOST="${NUC_HOST:-NUC}"           # hostname or IP
NUC_USER="${NUC_USER:-nucuser}"
NUC_DIR="${NUC_DIR:-~/Tools/aimi-analyzer}"
NUC="${NUC_USER}@${NUC_HOST}"
# ──────────────────────────────────────────────────────────────────────────

FULL=false
REBUILD=false
for arg in "$@"; do
    case $arg in
        --full)   FULL=true ;;
        --build)  REBUILD=true; FULL=true ;;
    esac
done

PYTHON=""
if command -v python3 &>/dev/null; then PYTHON="python3"
elif command -v python &>/dev/null; then PYTHON="python"
else echo "❌ Python not found"; exit 1; fi

echo "════════════════════════════════════════"
echo "  AIMI Analyzer — Deploy to NUC"
echo "  Target: ${NUC}:${NUC_DIR}"
echo "════════════════════════════════════════"
echo ""

# 1. Regenerate data files from local AIMI source
echo "📊 Regenerating data files..."
if [ -f data/aimi_parameters.json ]; then
    cp data/aimi_parameters.json data/aimi_parameters_backup.json
fi

$PYTHON generate_data.py

echo ""

# 2. Push data files to NUC (always)
echo "📤 Pushing data files to NUC..."
rsync -av --progress \
    data/aimi_parameters.json \
    data/aimi_context_for_ai.json \
    data/aimi_context_compact.txt \
    data/aimi_param_lookup.json \
    data/aimi_context_for_ai.md \
    "${NUC}:${NUC_DIR}/data/"

# 3. Push backend/frontend if --full
if $FULL; then
    echo ""
    echo "📤 Pushing backend + frontend..."
    rsync -av --progress \
        backend/app.py \
        "${NUC}:${NUC_DIR}/backend/"
    rsync -av --progress \
        frontend/index.html \
        "${NUC}:${NUC_DIR}/frontend/"
fi

# 4. Restart or rebuild on NUC
echo ""
if $REBUILD; then
    echo "🐳 Rebuilding and restarting Docker on NUC..."
    ssh "${NUC}" "cd ${NUC_DIR} && docker compose up -d --build"
else
    echo "🐳 Restarting Docker on NUC..."
    ssh "${NUC}" "cd ${NUC_DIR} && docker compose restart"
fi

echo ""
echo "════════════════════════════════════════"
echo "  ✅ Deploy complete!"
echo "  http://${NUC_HOST}:8765"
echo "════════════════════════════════════════"
