#!/usr/bin/env bash
# update.sh — Regenerate data files after AIMI source changes
# Usage: bash analyzer/update.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON=""
if command -v python3 &>/dev/null; then PYTHON="python3"
elif command -v python &>/dev/null; then PYTHON="python"
else echo "❌ Python not found"; exit 1
fi

echo "🔄 Regenerating AIMI Analyzer data files..."
echo ""

if [ -f data/aimi_parameters.json ]; then
    cp data/aimi_parameters.json data/aimi_parameters_backup.json
fi

$PYTHON generate_data.py

echo ""
echo "🐳 Restarting analyzer..."
docker compose restart

echo ""
echo "✅ Done — http://localhost:8765"
