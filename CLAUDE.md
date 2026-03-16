# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build
./gradlew buildFullDebug                        # Standard debug build
./gradlew buildFullRelease                      # Release build
./gradlew clean build                           # Clean build

# Unit tests
./gradlew :plugins:aps:testFullDebug            # APS plugin unit tests (most relevant)
./gradlew :plugins:aps:test                     # All APS unit tests
./gradlew test                                  # All module unit tests

# Single test class
./gradlew :plugins:aps:testFullDebug --tests "app.aaps.plugins.aps.openAPSAIMI.basal.BasalDecisionEngineTest"

# Instrumentation tests (requires connected device or emulator)
./gradlew :plugins:aps:connectedFullDebugAndroidTest

# Code quality
./gradlew ktlintFormat                          # Auto-format Kotlin code
./gradlew check                                 # ktlint + tests
./gradlew :plugins:aps:jacocoFullDebugTestReport  # Coverage report
```

## Module Structure

Multi-module Android/Gradle project. The primary work area is `plugins/aps/`, which contains all APS algorithm implementations including OpenAPS AMA, SMB, AutoISF, and the main AIMI system.

```
app/                    # Main app entry point
core/                   # Shared: data, interfaces, keys, utils, ui, validators
database/               # Room-based persistence
plugins/
  aps/                  # APS algorithms — primary work area
  main/                 # Dashboard, overview, general UI
  constraints/          # Safety constraint checks
  insulin/, sensitivity/, smoothing/  # Profile computation
pump/                   # Pump drivers
buildSrc/               # Shared build logic (Versions, dependencies)
```

Build flavors: `full` (default), `pumpcontrol`, `aapsclient`, `aapsclient2`. Use `Full` flavor for development.

## OpenAPSAIMI Architecture

The AIMI plugin lives in `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/` and is a closed-loop insulin delivery system with AI-driven adaptation.

### Decision Flow

Every loop cycle follows this pipeline:

1. **`OpenAPSAIMIPlugin`** — entry point, registered with the AAPS framework via Dagger `@Singleton`
2. **`DetermineBasalAIMI2`** — 400+ KB core engine; computes basal, SMB, and ISF decisions
3. **`AIMIPhysioManagerMTR`** — applies physiological adaptations from Health Connect data (sleep, HRV, steps)
4. **`AuditorOrchestrator`** — AI safety gate; can confirm, modify, or reject decisions via Gemini LLM
5. Execution → pump delivery

### Key Subsystems

| Package | Responsibility |
|---------|---------------|
| `physio/` | Physiological context (Health Connect: sleep, HRV, steps); updates every 4 hours |
| `advisor/` | AI coaching UI, clinical recommendations, LLM prompts |
| `advisor/auditor/` | Decision auditing with Gemini; `AuditorOrchestrator`, `AuditorNotificationManager` |
| `basal/` | Basal rate planning: `BasalDecisionEngine`, `BasalPlanner`, `DynamicBasalController` |
| `smb/` | Small Meal Bolus logic and dampening |
| `ISF/` | Insulin Sensitivity Factor blending and adjustment |
| `pkpd/` | PK/PD absorption modeling; `AdvancedPredictionEngine`, `PkpdAbsorptionGuard` |
| `wcycle/` | Women's hormonal cycle learning and per-phase ISF/basal multipliers |
| `steps/` | Activity integration via Health Connect and `UnifiedActivityProviderMTR` |
| `trajectory/` | Glucose trajectory prediction; `PhaseSpaceModels`, `StableOrbit` |
| `safety/` | Hypo/hyper guards; `HypoTools`, `SafetyDecision` |
| `learning/` | Reactivity learning; `UnifiedReactivityLearner`, neural trainer worker |
| `autodrive/` | MPC/PI controllers for advanced closed-loop control |
| `context/` | Context mode detection and switching |
| `model/` | Sealed class data models: `AimiAction`, `AimiDomain`, `AimiPriority`, `AimiVerdict` |
| `di/` | Dagger modules: `ApsModule`, `AIMIPhysioModuleMTR`, `WCycleModule` |

### Core Data Models

- **`AimiPluginContext`** — snapshot passed into every decision: glucose, profile, IOB, COB, preferences
- **`LoopContext`** — BG, IOB, COB, profile, pump capabilities, active modes
- **`DecisionResult`** — outcome: Applied / Rejected / Modified, with rollback support
- **`AimiAction`** — sealed class: `TemporaryBasal`, `SMB`, `Bolus`, `PreferenceUpdate`, `Notification`
- **`AimiState`** — `Manual`, `AutoDrive`, `SafetyIntervention`

### Physiological Adaptation States (MTR)

Hard caps applied to insulin parameters based on physiological state:

| State | ISF | Basal | SMB |
|-------|-----|-------|-----|
| OPTIMAL | 0% | 0% | 0% |
| RECOVERY_NEEDED | +8% | 0% | -5% |
| STRESS_DETECTED | +10% | -5% | -8% |
| INFECTION_RISK | +15% | -10% | -10% |

Max caps: ±15% ISF/Basal, ±10% SMB.

### Dependency Injection

All components use `@Inject constructor` with `@Singleton`. No manual `@Provides` — constructor injection throughout. DI modules are in `openAPSAIMI/di/`.

### Neural Network / ML

- `aimiNeuralNetwork.kt` — TensorFlow Lite inference
- `AimiModelHandler.kt` — model loading and lifecycle
- `llm/gemini/` — Gemini API integration for auditor decisions

## Tests

Unit tests live in `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/`. Test frameworks: JUnit 4/5, Mockito, Google Truth. Key test areas: `basal/`, `wcycle/`, `utils/`, `KalmanFilterTest`, `aimiNeuralNetworkTest`.

## Project Documentation

Architecture and design decisions are documented in root-level markdown files:

- `00_EXEC_SUMMARY.md` — overall architecture status
- `01_DECISION_MAP_DETERMINEBASAL.md` — DetermineBasal decision flow diagram
- `02_PACKAGE_BY_PACKAGE_AUDIT.md` — per-module analysis
- `03_MEDICAL_SAFETY_FMEA.md` — failure mode analysis
- `AIMI_PHYSIO_LOGIC.md` — physiological adaptation deep-dive
- `CONTRIBUTING.md` — development guidelines
- `docs/` — additional feature-specific documentation
