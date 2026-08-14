# AIMI Parameter Analyzer

Web dashboard for AndroidAPS AIMI plugin parameter analysis, CGM metrics and AI-assisted optimization.

Branch: `dev_OAPSAIMI_RB` · Stack: Docker · FastAPI · Python · Single-page HTML

---

## Quick start

```bash
cd analyzer

# 1. Configure environment
cp .env.example .env
nano .env   # add your Nightscout URL and token

# 2. Generate data files from AIMI source
python3 generate_data.py
# Expected: ✅ Validation passed — 220+ active params

# 3. Start the container
docker compose up -d

# 4. Open in browser
open http://localhost:8765
```

---

## Features

| Tab | Description |
|-----|-------------|
| 📊 Dashboard | CGM metrics (TIR, CV, GMI, hypo/hyper), hourly chart, gate-aware recommendations |
| 📂 Import | Decrypt AAPS export — auto-detects active feature gates |
| 🤖 AI | Claude / DeepSeek / ChatGPT analysis with full AIMI algorithm context |
| More → Features | All 30 AIMI feature groups with gate status |
| More → Parameters | Browse all 220+ active parameters, filter by feature/impact/gate |
| More → Diff | Compare two snapshots — parameter changes + TIR/CV delta |

---

## Workflow

```
AAPS → ☰ → Maintenance → Export settings
  ↓
Import tab → decrypt → snapshot saved
  ↓
Dashboard → Run analysis (fetches Nightscout)
  ↓
AI tab → analysis with gate-aware context
  ↓
Change settings in AAPS → export → import → wait 2-5 days → Diff
```

---

## Updating data files

Run after any AIMI source change (new parameter, new feature, renamed key):

```bash
python3 generate_data.py
docker compose restart
```

The generator automatically finds the AIMI source at `../` (the parent repo root).
See `CLAUDE_CODE_TASK_update_data.md` for the full update workflow.

---

## Directory structure

```
analyzer/
├── backend/
│   └── app.py               FastAPI backend
├── frontend/
│   └── index.html           Single-page app
├── data/
│   ├── aimi_parameters.json          Parameter knowledge base (generated)
│   ├── aimi_context_for_ai.json      Feature context for AI prompt (generated)
│   ├── aimi_context_compact.txt      Compact AI prompt text (generated)
│   ├── aimi_param_lookup.json        Quick lookup for AI output annotation (generated)
│   └── aimi_context_for_ai.md       Algorithm documentation (manual + generated)
├── generate_data.py          Regenerates all data files from AIMI source
├── generate_settings_paths.py  Extracts settings paths from OpenAPSAIMIPlugin.kt
├── merge_settings_paths.py   Merges paths into aimi_parameters.json
├── docker-compose.yml
├── Dockerfile
├── .env.example             → copy to .env (not committed)
└── CLAUDE_CODE_TASK_update_data.md   Update workflow for Claude Code
```

---

## Docker commands

```bash
# Start
docker compose up -d

# Restart (after data file changes)
docker compose restart

# Rebuild (after backend/frontend changes)
docker compose up -d --build

# Logs
docker compose logs -f

# Stop
docker compose down
```

---

## AI providers

Configure API keys in the UI (⚙️ Settings) or in `.env`.

| Provider | Models | Best for |
|----------|--------|----------|
| Anthropic | Claude Sonnet 4.6, Opus 4.5 | Causal reasoning, "why" questions |
| DeepSeek | V3 (fast), R1 (reasoning) | Structured output, step-by-step |
| OpenAI | GPT-4o-mini, GPT-4o, o1-mini | General analysis |

All models receive the full AIMI algorithm context (30 features, 9 known bugs,
117+ parameter summaries) pre-loaded from source code analysis.

---

## AI history (memory between runs)

Every AI run is remembered on the server. After each successful analysis the
backend saves a compact entry to `analyzer/history/ai_history.json`:

- date and key metrics (TIR, CV, hypo) at that time
- the params that were active then
- the full AI proposal text

The next analysis automatically includes the **last 5 runs of the same plugin**
in the prompt: metrics, the previous proposal (cut to ~1000 chars) and the
parameter changes applied after each run. This stops the back-and-forth where
the AI proposes something it already proposed (or the opposite) because it does
not know the past.

The AI is also told: if a change was already applied and the metrics did not
improve, do not repeat it — propose a different direction or reverting.

- Show history: `GET /api/ai-history?plugin=aimi`
- Clear history: `DELETE /api/ai-history` (all plugins) or
  `DELETE /api/ai-history?plugin=aimi`
- The file lives in the writable `/history` Docker volume — it survives
  container restarts and rebuilds.

---

## Known issues

- **LastLegacyPrebolusTime**: stale 2020+ timestamp causes permanent prebolus state.
  Workaround: app reinstall (ADB not possible on release build).

---

## License

Part of [RBarth-DE/OpenApsAIMI](https://github.com/RBarth-DE/OpenApsAIMI),
branch `dev_OAPSAIMI_RB`. For personal/research use.
