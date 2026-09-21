# Merge log: `mtr/dev_OAPSAIMI` → `dev_OAPSAIMI_RB` (2026-09-21)

- **Source branch**: `mtr/dev_OAPSAIMI` @ `468cf7a034` (3 commits since the merge base)
- **Target branch**: `dev_OAPSAIMI_RB` (pre-merge tag `premerge-mtr-2026-09-21` = `222605347d`)
- **Merge base**: `a546722609` (2026-09-18)
- **Size**: 62 files changed against the pre-merge tree (8 752 insertions, 293 deletions)
- **Conflicts**: 5 content conflicts, 2 "added into a directory that was renamed here" conflicts,
  and 22 files that arrived at pre-flip paths with no conflict reported at all

This is the second of the two merges done on the same day. The first one, `upstream/dev`, is
[MERGE_DEV_2026-09-21.md](MERGE_DEV_2026-09-21.md). The fork branch `mtr/dev_OAPSAIMI` is still on
the **pre-KMP layout** (`src/main`, `src/test`), while this branch is flipped
(`src/commonMain`, `src/androidMain`, `src/androidHostTest`). That difference is where most of the
work in this merge was, and it is the subject of the section after the next one.

## What the fork branch brought

1. **A fingerstick can now go to the sensor itself** (`c653fc4485`). New
   `SensorCalibrationResult` (`NotSupported` / `Queued` / `Refused`) in `:core:interfaces`
   commonMain, two new `BgSource` methods with safe defaults (`calibratesInSensor() = false`,
   `calibrateSensor() = NotSupported`), the ONE+ implementation behind the engineering-only
   `DexcomOnePlusBooleanKey.SendCalibrationToSensor`, and the packet / queue / BLE write / reply
   work in `:plugins:dexcom_oneplus`. The calibration dialog now says "sent to the sensor, check it
   yourself" instead of pretending to know, and warns when the entered value is far from the sensor
   value. The design note `docs/DEXCOM_ONEPLUS_CALIBRATION_TO_SENSOR.md` records why the app cannot
   confirm that the sensor took the value.
   - The rule that matters for safety is **never both**: while this switch is on, no calibration
     entry is stored at all, so the software fit has nothing to fit. The same rule is applied in the
     graph path by the second half of the next point.
2. **AIMI telemetry retention** (`505b848fb6`): a new `openAPSAIMI.retention` package - 9 production
   files and 13 tests - that trims, archives and deletes the growing telemetry files once a day.
   `AimiAppendGuard` is called from the two writers (`AuditorJsonlExport`,
   `AimiHormonitorStudyExporterMTR`), `AimiRetentionWorker` does the daily pass, and
   `AimiMlTrainingScheduler` registers it. The worker is deliberately **not** cancelled in
   `AimiMlTrainingScheduler.cancel()`: the gigabytes already on disk still need the janitor after
   AIMI itself is switched off. The same commit carries comparison-CSV changes and a format test.
3. **Two documents** (`468cf7a034`): the retention plan and design note under
   `docs/superpowers/`.

### The calibration guard the first merge could not carry

The first merge left a follow-up: `PrepareGraphDataRunner.smoothData()` called
`activeCalibration.calibrate(...)` on every bucket with no check on the source, so a follower or a
sensor that calibrates itself would have been corrected twice. `mtr/dev_OAPSAIMI` carries that
guard. It went back in, with the two reasons written out at the call site.

## The layout trap: 22 files that merged "cleanly" into a place nothing compiles

The retention package is new on `mtr`'s side, so it was added at
`plugins/aps/src/main/kotlin/...` and `plugins/aps/src/test/kotlin/...`. On this branch those paths
are **not source sets**: the module declares `commonMain`, `androidMain`, `iosMain`,
`androidHostTest`, `commonTest`, `iosTest`. Git reported the add as a clean success and printed no
conflict, and nothing in the diff looks wrong. The files would simply never have been compiled,
never have run, and the daily worker would have been "scheduled" by a scheduler pointing at a class
that does not exist.

They were moved with `git mv` to `androidMain` / `androidHostTest`, next to the rest of the fork's
AIMI code. The `:plugins:aps` build file already documents this trap for the tests that an earlier
merge moved the same way.

Git only reports this shape as a conflict when it can match the file against a rename
("added in a directory that was renamed"). Two of the merged files did get that report -
`SensorCalibration.kt` and `ComparisonCsvParserFormatTest.kt` - and they were placed by hand at
`commonMain` and `androidHostTest`. The other 22 did not, so the check has to be a separate pass,
not a reading of git's output.

## Conflicts (7)

| File | Resolution |
|---|---|
| `workflow/.../PrepareGraphDataRunner.kt` | This branch rewrote `PrepareGraphDataWorker` into a Runner; `mtr`'s only change to the Worker was the extended calibration guard, so a diff against the merge base was taken to be sure. The guard was applied to `smoothData()`. The fork's own dropped `smoothingContext` / `iobCobCalculator` parameters stay dropped - a merge is not the place to restore them. |
| `workflow/.../PrepareGraphDataRunnerTest.kt` | Import conflict; both imports kept (`Calibration`, `AapsLock`). `mtr`'s two new tests are in the file, but their body no longer compiled here - see item 6 below. |
| `ui/.../CalibrationDialogViewModel.kt` | Kept `mtr`'s `gapWarningFor`, `sendToSensor` and the `companion object` with the two gap constants. Strings went to `TextRef`s (`UiStrings.cal_sent_to_sensor`), the injection annotation stays Metro's `@Inject` (this branch has no Hilt), and `kotlin.math.abs` / `roundToInt` were added. |
| `ui/.../CalibrationDialogViewModelTest.kt` | Kept `mtr`'s new tests and both matcher styles (`anyOrNull` and `anyVararg`). The `R` alias import was dropped, and the string stubs use `CoreUiStrings` / `InterfacesStrings` refs. Two of `mtr`'s new tests build the view model positionally and were missing this branch's `decimalFormatter` argument - see item 7. |
| `app/src/main/res/values/strings.xml` | Took `mtr`'s side of both hunks. This side still had French comparator strings in the **default** file, which is wrong; `mtr`'s English block is a key superset. French lives in `values-fr-rFR/` only. |
| `core/.../source/SensorCalibration.kt` | New file; placed at `commonMain` (git offered the move because the containing directory was renamed here). |
| `plugins/aps/.../comparison/ComparisonCsvParserFormatTest.kt` | New file; placed at `androidHostTest`. |

## Merge fallout fixed by hand (no conflict, it just stopped compiling)

1. **The 22 relocated files** described above.
2. **`AimiRetentionWorker` arrived with Hilt** (`@HiltWorker`, `dagger.assisted.*`). This branch
   moved to Metro, so it was ported to `@AssistedInject` with a nested
   `@AssistedFactory fun interface Factory : MetroWorkerCreator`, and registered in
   `AppWorkersGraph` (`@Provides @IntoMap @WorkerKey(AimiRetentionWorker::class)`) with the
   matching entry in `AppWorkersGraphTest`, which lists every worker with `containsExactly`.
   Without that registration `MetroWorkerFactory` finds nothing and the daily pass silently never
   runs - a build-green, runtime-dead worker.
3. **Metro's assisted-injection check compares parameter names**, not only types. The worker had
   Hilt's `@Assisted appContext` / `@Assisted workerParams`, while `Factory.create()` takes
   `context` / `params`; the build failed with `ASSISTED_INJECTION_ERROR`. Renamed to match, which
   is also the shape `RunningModeExpiryWorker` in the same module uses.
4. **Android string ids in flipped code.** Three places:
   - `CalibrationDialogViewModel`, `CalibrationDialogScreen` and their test used
     `R.string.*` / a `R` alias; commonMain and androidHostTest cannot see Android resource ids
     here. They now use `UiStrings` / `CoreUiStrings` / `InterfacesStrings`.
   - `DexcomOnePlusPlugin.calibrateSensor()` calls `rh.gs(R.string...)` from a **member function**,
     where `PluginBase.rh` is the `TextResolver` type and only the `TextRef` overload exists. The
     class now overrides `rh` with the Android `ResourceHelper` type, exactly as
     `EversensePlugin` in the same package already does, and with the same reason written down.
   - `DexcomOnePlusBooleanKey` is commonMain and used `titleResId` / `summaryResId`, which is the
     pre-flip key API. Turned into `title` / `summary` with the generated `SourceStrings` refs.
5. **An empty `plugins/aps/src/main` and `src/test` tree** was left behind by the moves and removed.
6. **`mtr`'s two calibration-guard tests were written against the Worker that this branch turned into
   a Runner.** They called `worker().doWorkAndLog()` and asserted on `ListenableWorker.Result.Success`;
   neither exists here, so `:workflow:compileAndroidHostTest` failed with `UNRESOLVED_REFERENCE
   'worker'` (line 174). The conflict was reported as import-only because the two sides changed
   different parts of the file - `mtr` replaced the imports and appended tests, this branch had
   replaced the helpers the tests call. Adapted to this branch's shape: `run()` and
   `assertIs<WorkOutcome.Success>(run())`, and the now-unused `ListenableWorker` import dropped. The
   guard they cover is untouched (`smoothData()` still skips the software fit for a follower or a
   sensor that calibrates itself, and still smooths).
   - This is the same trap as the 22 misplaced files, one step further in: a **test** source set that
     does not compile is caught by the test gate, but only if that gate is run to completion. The
     first test run stopped here, so everything after it in the task graph was never run.
7. **Same shape in `CalibrationDialogViewModelTest`**: `mtr`'s two gap-warning tests construct the
   view model with a positional argument list, and this branch's constructor ends in
   `rh, decimalFormatter`. `NO_VALUE_FOR_PARAMETER 'decimalFormatter'`. Both call sites got the
   argument; nothing else in the file changed.
   - Both 6 and 7 are the price of a constructor or entry point that moved on one side while a test
     was added on the other. They compile only in the union, and the compiler is the only thing that
     finds them - neither shows up as a conflict, because the two sides touched different lines.
8. **Two of this branch's own dialog tests went red, and `mtr`'s change is why.**
   `confirmAndSave()` now asks the active source first (`sendToSensor`), but the test's `setUp` only
   stubbed `activePlugin.activeCalibration`. `activePlugin.activeBgSource` came back null from the
   mock, the null check inside Kotlin threw, and `confirmAndSave()` has no `catch` - only `finally` -
   so the coroutine died before emitting anything and `sideEffect.replayCache` stayed empty
   (`NoSuchElementException` at `.last()`). Fixed in the test, not in the code: `activeBgSource` is
   non-null in production, so the fault was the missing stub. `setUp` now supplies a plain source
   that answers `calibratesInSensor() = false`, which is the path those two tests are about.
   - Worth noting for the next merge: a stub missing from `setUp` is invisible until some new call
     path reads the unstubbed property. The failing assertion (`sideEffect` empty) says nothing about
     the missing stub; only the stack, or reading the code, does.

The compiler found 2, 3 and 4 in three separate runs. A sweep for the same shapes was run over every
file the merge touched: `ResId`-style arguments, `R.string` / `R.drawable` references, `android.*` /
`java.*` imports in commonMain, and stale references to the constant `CalibrationMath` no longer
declares (`CORRECTION_AT_CENTER_MIN`). Nothing else was left.

## Invariant diff (`scripts/merge_invariants.sh premerge-mtr-2026-09-21` vs `post`)

Four lines changed, all explained:

- `plugins/dexcom_oneplus`: 81 → 84 files (the new outcome type and its two tests).
- `openAPSAIMI files`: 746 → 769 (9 production + 13 test files of the retention package, and the
  comparison format test).
- `plugins/source/src/androidMain/res/values/strings.xml`: 437 → 451 keys (the calibration-to-sensor
  strings).
- The `adaptive smoothing calibration source (calibratedOrValue)` count is **unchanged at 15**, which
  is the point of the merge being complete on the safety side: the "never both" rule is still in
  place in the graph path.

`mtr`'s side has no renames and no deletions, so the "a moved file can silently eat the other side"
check does not apply to this merge. No Gradle file or version-catalog entry was involved either, so
the alias check does not apply.

## Gates

- Compile: `./gradlew :app:compileFullDebugKotlin :wear:compileFullDebugKotlin` - green.
  It took four runs. The first three each stopped on one of the shapes in items 2, 3 and 4 above, so
  the file is not proof of anything on its own - the sweeps for the same shapes were what made it
  trustworthy.
- Build: `./gradlew :app:assembleFullDebug :wear:assembleFullDebug` - green.
- Tests: `./gradlew testFullDebugUnitTest testDebugUnitTest testAndroidHostTest` - see below.
- Duplicate resource / manifest scan: no duplicate resource names in any `strings.xml`, no duplicate
  manifest components in `:app` or `:wear`.
- **Nothing was waived as "pre-existing".** The test gate went red three times and all three were
  merge regressions, each with a cause visible in the diff: items 6, 7 and 8 above. None of them was
  accepted as a red test, so the pre-merge worktree comparison the checklist asks for was not needed.
- Lost-setup-line sweep (`injectMetroMembers`): nothing lost, against both `upstream/dev` and
  `mtr/dev_OAPSAIMI`.

## Follow-ups

- **`tools/aimi_viewer` is not covered by any gate here.** Its `MainActivity` grew by ~190 lines in
  this merge (it reads the retention archives and reports coverage of a requested window). It is a
  Flutter project's Android module, not a Gradle subproject of this build, so no task in this repo
  compiles or tests it.
- **The retention worker has never run on a device.** Registration, key and scheduler are in place
  and the unit tests cover the pass itself, but the daily WorkManager run and the storage behaviour
  on a real phone still need a look before this is called done.
- **The calibration-to-sensor path is device-pending.** It needs the engineering marker file, a
  switched-on `DexcomOnePlusBooleanKey.SendCalibrationToSensor` and a real sensor to say anything,
  none of which a build or a unit test can stand in for. The post-merge smoke list in
  [MERGE_CONSTRAINT_DEXCOM_ONEPLUS.md](MERGE_CONSTRAINT_DEXCOM_ONEPLUS.md) is the place to work
  through it. Only the checks that the merge could break were done here, and they pass: the plugin
  still registers with `@IntKey(446)`, its assets are at the flipped path, and no Gradle file was
  touched.
- **The two new ONE+ strings are English-only.** `dexcom_oneplus_send_calibration_to_sensor` and its
  summary have no translations yet, on purpose.
