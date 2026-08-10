#!/usr/bin/env bash
# setup.sh — Initial setup for AIMI Analyzer
# Run once after cloning: bash analyzer/setup.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "═══════════════════════════════════════"
echo "  AIMI Analyzer — Setup"
echo "═══════════════════════════════════════"
echo ""

# 1. Check Python (try python3, then python)
PYTHON=""
if command -v python3 &>/dev/null; then
    PYTHON="python3"
elif command -v python &>/dev/null && python --version 2>&1 | grep -q "Python 3"; then
    PYTHON="python"
fi

if [ -z "$PYTHON" ]; then
    echo "❌ Python 3 not found. Please install Python 3.9+"
    exit 1
fi
PYTHON_VERSION=$($PYTHON -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
echo "✅ Python $PYTHON_VERSION ($PYTHON)"

# 2. Check Docker
if ! command -v docker &>/dev/null; then
    echo "❌ Docker not found. Please install Docker Desktop or Docker Engine."
    exit 1
fi
echo "✅ Docker $(docker --version | cut -d' ' -f3 | tr -d ',')"

# 3. Create .env if missing
if [ ! -f .env ]; then
    if [ -f env.example ]; then
        cp env.example .env
    else
        # Create minimal .env from scratch
        cat > .env << 'ENVEOF'
# AIMI Analyzer configuration
# Edit this file and add your Nightscout URL and token

NIGHTSCOUT_URL=https://your-ns.example.com
NIGHTSCOUT_TOKEN=your-api-token
ENVEOF
    fi
    echo ""
    echo "📝 Created .env"
    echo "   → Edit .env and add your Nightscout URL and token before starting."
    echo ""
else
    echo "✅ .env exists"
fi

# 4. Generate all plugin data files
echo ""
echo "📊 Generating plugin data..."

run_gen() { local label="$1"; shift; echo "  ├─ ${label}..."; $PYTHON "$@" || echo "     ⚠️  ${label} failed"; }

run_gen "AIMI"                scripts/generate_data.py
run_gen "AIMI paths"          scripts/generate_settings_paths.py
run_gen "BOOST"               scripts/generate_boost_data.py
run_gen "BOOST paths"         scripts/generate_boost_settings_paths.py
run_gen "AutoISF"             scripts/generate_autoisf_data.py
run_gen "AutoISF paths"       scripts/generate_autoisf_settings_paths.py

# Merge settings paths into parameters
echo "  ├─ Merging paths..."
$PYTHON -c "
import json; from pathlib import Path
DATA = Path('data'); ROOT = Path('.')
for p in ['aimi','boost','autoisf']:
    pf = DATA / f'{p}_parameters.json'
    sf = DATA / f'{p}_settings_paths.json'
    if not sf.exists(): sf = ROOT / f'{p}_settings_paths.json'
    if not pf.exists() or not sf.exists(): continue
    with open(sf) as f: paths = json.load(f).get('paths',{})
    with open(pf) as f: params = json.load(f)
    merged = 0
    for x in params['parameters']:
        if x['key'] in paths:
            x['settings_path'] = paths[x['key']]['path']; merged += 1
    params['with_settings_path'] = merged
    with open(pf,'w') as f: json.dump(params,f,indent=2,ensure_ascii=False)
    print(f'     {p}: {merged} paths')
"

if [ -f data/aimi_parameters.json ] || [ -f data/boost_parameters.json ]; then
    echo "  └─ ✅ Data files ready"
else
    echo "  └─ ⚠️  No data generated — deploy.sh on laptop first, or check source"
fi

# 5. Build and start Docker
echo ""
echo "🐳 Building and starting Docker container..."
docker compose up -d --build

echo ""
echo "═══════════════════════════════════════"
echo "  ✅ Setup complete!"
echo ""
echo "  Open: http://localhost:8765"
echo ""
echo "  Next steps:"
echo "  1. Edit .env → add Nightscout URL + token"
echo "  2. Run: docker compose restart"
echo "  3. Open the browser, go to Import tab"
echo "  4. Export from AAPS → ☰ → Maintenance → Export settings"
echo "  5. Drop the file in Import, enter master password"
echo "═══════════════════════════════════════"
