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

# 4. Generate data files (only if AIMI source is available)
echo ""
if $PYTHON generate_data.py 2>/dev/null | grep -q "Found 0 AIMI-related"; then
    echo "ℹ️  No AIMI source found — skipping data generation."
    echo "   Run deploy.sh on the laptop to push data files."
    if [ ! -f data/aimi_parameters.json ]; then
        echo "   ⚠️  data/ is empty — analyzer will start with no parameter data."
    else
        echo "   ✅ Using existing data files."
    fi
else
    if $PYTHON generate_data.py; then
        echo "✅ Data files generated"
    else
        echo "⚠️  Data generation had issues — see CLAUDE_CODE_TASK_update_data.md"
    fi
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
