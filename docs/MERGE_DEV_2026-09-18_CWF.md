# Merge log: `dev` → `dev_OAPSAIMI_RB` (2026-09-18, wear / CWF)

- **Upstream `dev`**: `025c4163b7` (54 commits since `aaa069fab385`)
- **Target branch**: `dev_OAPSAIMI_RB` (pre-merge tag `premerge-dev-2026-09-18-cwf`)
- **Merge base**: `aaa069fab385` (the first 2026-09-18 merge point)
- **Size**: 110 files changed against the fork tip (6 158 insertions, 2 980 deletions)

This is the **second** merge of the day. The first one
([MERGE_DEV_2026-09-18.md](MERGE_DEV_2026-09-18.md)) brought CareLevo and the pump module
restructuring. This one brings upstream's finished Wear CWF / Watch Face Format work.

## What upstream brought

1. **Wear CWF, finished** (`d3fe8d35f2` merges the `wear/cwf_wff` branch - the bulk of the 54
   commits):
   - Frames are no longer drawn from scratch on every change. They are built from layers that are
     cached by how often each layer changes, and prepared before the system asks for them:
     `CwfFramePipeline`, `CwfFrameQueue`, `CwfMinuteFrame`, `CwfCachePolicy`, `CwfRenderTarget`,
     `ServedFrameWarning`.
   - `RenderBlock { ALL, LOWER, UPPER }` became
     `RenderLayer { DATA_BASE, CLOCK_TEXT, MIDDLE, HANDS, SECOND_HAND }`, so the fork's three-block
     split is covered by a finer split upstream.
   - The complication code moved into `wear/.../complications/cwf/` (`CwfFaceComplication`,
     `CwfAmbientFaceComplication`, `CwfComplicationUpdater`), with `AmbientReadoutTap` and
     `SecondVisibility` as small helpers.
   - The Watch Face Format documents are now **per face flavor**: `wear/watchfacepush/src/wfs/`
     (the Watch Face Studio design) and `wear/watchfacepush/src/cwf/` (the picture document), each
     with its own preview images, `watch_face_info.xml`, strings and `watchface.xml` template.
   - `PushedWatchfaceId` (`:core:keys`) and `StringKey.WearPushedWatchface`: the watch tells the
     phone what it did with the pushed face, and `WatchFacePushHelper` holds one install lock per
     process.
   - Nine new test classes under `wear/src/test` (CWF pipeline, cache policy, frame queue, minute
     frame, pacing, served-frame warning, second visibility, pushed-face manifest). The fork had 299
     `@Test` methods in `wear/src/test` before this merge, 382 after.
2. **Exact-alarm permission** (`6add9992e6` "Ask every user for exact alarms and guard
   setAlarmClock"): `permission_schedule_exact_alarm_*` strings, `PluginPermissionsImpl`,
   `AlarmScreenWakeReceiver`, `AlarmNotificationManagerScreenWakeTest`.
3. **Automation** (`025c4163b7`): run automation on every finished calculation again, one run at a
   time - `AutomationRuntime`, `AutomationRuntimeProcessingTest`.
4. **The Crowdin move is finished**: the temporary placeholder string files are gone
   (`pump/dana`, `pump/danar`, `pump/omnipod-dash`, `pump/omnipod-eros` `strings.xml` deleted).
5. Smaller items: the Medtrum "the patch restarted by itself" message, `SamsungWatchFaceEditor`
   removed (no references left), and the wear test notes rewritten for another contributor.

## Conflicts (25 files - `.git/MERGE_MSG`)

| File | Decision |
|---|---|
| `_docs/CWF_WFF_Prompt.md` | **theirs** — the fork's 1 830-line working log is replaced by upstream's 476-line design doc (user decision). The fork's text stays in git history. |
| `_docs/Complication_Libraries.md` | **theirs**. |
| `gradle/libs.versions.toml` | **theirs first, then corrected** - see below. |
| `core/interfaces/.../weardata/CwfMetaDataKey.kt` | **theirs**. |
| `plugins/sync/.../wear/compose/WearViewModelTest.kt` | **theirs** (upstream's version is a superset: +72/-1). |
| `wear/build.gradle.kts` | **theirs**. |
| `wear/src/main/AndroidManifest.xml` | **combine by hand** - see below. |
| `wear/src/main/kotlin/app/aaps/wear/WearApp.kt` | **theirs**. |
| `wear/.../complications/CwfAmbientBgComplication.kt` | **theirs** (add/add). |
| `wear/.../complications/CwfAmbientStatusComplication.kt` | **theirs** (add/add). |
| `wear/.../complications/CwfImageComplication.kt` | **theirs** (add/add). |
| `wear/.../interaction/ComplicationPickerSupport.kt` | **theirs** (add/add). |
| `wear/.../interaction/ConfigurationActivity.kt` | **theirs**. |
| `wear/.../interaction/CustomWatchfaceConfigurationFragment.kt` | **theirs** (add/add). |
| `wear/.../interaction/WatchfaceConfigurationActivity.kt` | **theirs**. |
| `wear/.../interaction/activities/CwfRenderPreviewActivity.kt` | **theirs** (add/add). |
| `wear/.../watchfaces/CustomWatchface.kt` | **theirs**. |
| `wear/.../watchfaces/WatchFacePushHelper.kt` | **theirs**. |
| `wear/.../watchfaces/utils/BaseWatchFace.kt` | **theirs**. |
| `wear/.../watchfaces/utils/WatchFaceSettings.kt` | **theirs**. |
| `wear/watchfacepush/src/main/res/xml/watch_face_info.xml` | **deleted** - the face data moved to `src/wfs/` and `src/cwf/`. |
| `.gitignore` | **combine** - kept the fork's `aimi-corpus/`, `.tmp_eversense_patches/`, `build-libkeks*` lines and added upstream's per-flavor `wear/watchfacepush/*Wfs/*`, `*Cwf/*`. |
| `implementation/src/androidMain/res/values/strings.xml` | **combine** - the fork's Do-Not-Disturb strings plus upstream's `permission_schedule_exact_alarm_*`. The first attempt also kept `permission_fsi_title` / `permission_fsi_description`, which upstream deleted in this merge; they have no reference left anywhere, so they were removed too. |
| `AlarmNotificationManager.kt` | **fork features on upstream's structure** (kept the DND-bypass channels and `AlarmAction.HYPO_TREATED`). |
| `AutomationRuntime.kt` | **upstream only** - the fork's own change here (a `synchronized` around `btConnects.clear()`) is replaced by upstream's guard. The first attempt kept both, see below. |

**ASYNC IMPACT** (`AutomationRuntime.kt`): upstream subscribes to `EventAutosensCalculationFinished`
again, so a run is now started by **every finished calculation** and not only by the timer and the
odd charging/network/location/BT event. Two triggers can therefore arrive together. Upstream guards
this with `processActionsMutex` - one `processActions()` run at a time - because a rule's `lastRun`
is written only after its actions finish and the actions suspend, so two runs would both see the
rule as due and execute it twice. The BT collector adds under `btConnectsLock` on one thread while a
run reads and trims the list on another. `addStatusToLog` drops a repeated status line, so a
long-running state does not fill the log now that runs are this frequent.

### A conflict where both sides looked right and the test caught it

The fork's only functional change to `AutomationRuntime.kt` was one line - `btConnects.clear()`
wrapped in `synchronized` - to stop the BT collector and a run from touching the list at the same
time. Upstream replaced that whole tail of `processActions()` with:

```kotlin
btConnectsLock.withLock { btConnects.subList(0, seenBtConnects).clear() }
```

That is not only a lock. It clears **only the events that were there when the run started**, so a
connect that arrives while a run is in progress is left for the run waiting behind it on
`processActionsMutex`. Clearing the whole list - what the fork's line did - throws that event away
and the rule never fires.

The first resolution kept upstream's new line **and** the fork's two old lines under it, which is
the one combination that must not happen. It compiles, it builds, and

`AutomationRuntimeProcessingTest.a BT connect that arrives during a run still fires its rule`
fails with `expected: 1 but was: 0` - upstream added that test in this same merge, so it is the
merge that broke it, not an older failure. Confirmed on the merge and fixed by dropping the two
fork lines; the fork's `requestPersist()` went with them, for the reason upstream wrote next to the
line: `lastRun` is not part of the stored JSON, so a write after every run only moved the synced
key's "last modified" stamp, and a client's automation edit lost to the master more often.

**Lesson for the next merge:** when both sides changed the same line, the fork's line is usually
*replaced*, not *added to*. Keeping both is the one resolution that never shows up in a build. Pick
the side that does the whole job; here upstream's line already holds the lock the fork wanted.

### Why the wear/CWF conflicts are all "theirs"

Both sides carried the **same** CWF feature - the fork's copy was an earlier snapshot of the work
that upstream finished. The evidence: identical file paths, the newer files differing only by the
`RenderBlock` → `RenderLayer` refactor and small line changes (16-27 lines per file), and upstream's
own `watch_face_info.xml` comment answering the fork's `Editable = false` Samsung workaround by name
("It was false for a good reason ... That reason has expired"). So there was no fork fix to lose.

The fork's own CWF helper classes are therefore gone, superseded by upstream's `complications/cwf/`
package: `CwfBlockComplication.kt`, `CwfLowerComplication.kt`, `CwfUpperComplication.kt`,
`CwfComplicationUpdater.kt` (the old one at `complications/`, not the new one in `cwf/`). No
reference to them is left in the repository.

### `wear/src/main/AndroidManifest.xml`

The text merge did the wrong thing twice, and the manifest merger would have failed on the result:

- It **kept both sides'** CWF service blocks, so five services were declared twice, and the fork's
  copies still pointed at `${applicationId}.watchfacepush.aapsv4`, an id that no longer exists.
- It **dropped** upstream's new `CwfFaceComplication` and `CwfAmbientFaceComplication` services and
  upstream's `CwfImageComplication` block, because the fork's block with the same name won the merge.

Fix: the fork's five blocks were removed and upstream's three inserted, so the whole CWF region is
now byte-identical to upstream's manifest. The rest of the file keeps the fork's Afrezza activity and
the fork's deliberate removal of the `.heartrate.HeartRateListener` and
`.wearStepCount.StepCountListener` services (replaced by a foreground service in `dc57f64c52`).

### `gradle/libs.versions.toml`: a resolution that had to be corrected

The catalog was first taken wholesale from upstream, like the other files. That broke the build:

```
e: plugins/main/build.gradle.kts:92: Unresolved reference 'fragment'.
e: plugins/main/build.gradle.kts:94: Unresolved reference 'gridlayout'.
e: plugins/main/build.gradle.kts:96: Unresolved reference 'livedata'.
e: plugins/main/build.gradle.kts:98: Unresolved reference 'flexbox'.
```

Upstream moved its overview and dashboard screens to Compose, so it dropped the aliases for the
Fragment-based ones. The fork's `:plugins:main` and `:plugins:sync` still use them. Five aliases were
put back: `androidx-fragment`, `androidx-gridlayout`, `androidx-compose-runtime-livedata`,
`com-google-android-flexbox`, `androidx-security-crypto`. Each carries a short comment saying which
fork screen needs it.

**Lesson for the next merge:** a catalog is not a file to take from one side - check it against the
build files that use it. The check is simple: read every alias out of `gradle/libs.versions.toml`
(`[libraries]` is `libs.x`, `[plugins]` is `libs.plugins.x`, `[versions]` is `libs.versions.x`),
collect every `libs.` reference in the `*.gradle.kts` files, and compare the two sets. Gradle only
reports the loss when it configures the module that uses the alias, and a failed configuration stops
the build before any compilation, which is what happened here.

The only remaining unresolved references are `libs.com.google.dagger.compiler` and
`libs.com.google.dagger.android.processor` in `pump/apex/build.gradle.kts`. That module is commented
out in `settings.gradle` ("needs upstream Pump.kt interface update"), so its build file is never
evaluated. That is older than this merge.

### A path move that silently ate upstream's file

`wear/watchfacepush/template/watchface.xml` (fork, modified) had to become
`wear/watchfacepush/src/wfs/template/watchface.xml` (upstream). Git applied the fork's modification
at upstream's new path, which discarded upstream's version of that file - the merge result looked
clean, but the file still named the deleted `CwfLowerComplication` / `CwfUpperComplication`
providers. Restored with `git checkout MERGE_HEAD -- <path>` and verified byte-identical to
upstream. Watch for this whenever a file moves in a merge: the fork's change wins silently.

## Fork preserved

- **AIMI / Boost plugins**: 742 `openAPSAIMI` files, unchanged in count.
- **Fork CGMs** (Eversense, Dexcom ONE+, Libre 3, LibKeKs), **Apex pump types**, the Afrezza
  activity and the fork's wear `WearMemberInjectors` entry: untouched by this merge.
- **Database freeze gate** (see the checklist): `KeepAliveWorker` still calls
  `cleanupDatabase(6 * 31, deleteTrackedChanges = false, runVacuum = false)`, no `VACUUM` text in the
  automatic cleanup path, and the `drain()` call sites (xDrip, Dexcom, SMS) are unchanged - this
  merge does not touch those files.
- **Fork docs and notes**: `_docs/OpenAPS_AIMI_User_Manual.md` is fork-only (upstream does not have
  the file), so it stays.

## Verification

- [x] Safety tag on the pre-merge commit - `premerge-dev-2026-09-18-cwf` (`3d3984036f`).
- [x] No unmerged paths, no conflict markers anywhere in the tree.
- [x] Duplicate scan - **PASS**: no duplicate resource name in any `strings.xml`, no duplicate
      component in any `AndroidManifest.xml`.
- [x] `injectMetroMembers` sweep against `upstream/dev` - **PASS** (no lost call sites).
- [x] Version catalog against the build files - **PASS** after the fix above (only the inactive
      `:pump:apex` module still names two dagger aliases).
- [x] Deleted fork classes have no remaining references - **PASS** (also for upstream's
      `SamsungWatchFaceEditor`).
- [x] Every conflict file read against upstream, not only against the fork - **PASS after two
      corrections**: `AutomationRuntime.kt` (a line upstream replaced, kept twice) and
      `implementation/.../strings.xml` (two strings upstream deleted, kept alive). Both are
      described above.
- [x] No upstream string key lost - **PASS**: each merged `strings.xml` is a superset of upstream's
      (`core/keys` 331 `->` 844, `implementation` 84 `->` 88, `plugins/sync` 249 `->` 253, `wear`
      417 `->` 422, `medtrum` 130 `->` 142).
- [x] `:app:assembleFullDebug :wear:assembleFullDebug` - **BUILD SUCCESSFUL** on the final tree (see
      below).
- [x] Host unit tests (`testFullDebugUnitTest testDebugUnitTest testAndroidHostTest`) - **BUILD
      SUCCESSFUL**, 14 306 tests, 0 failures, on the final tree (see below).
- [x] The 21 files where the fork had *also* changed content and the merge took upstream's version
      are all in the wear/CWF family, and each fork change is present in upstream's newer code
      (spot-checked: the complication list in `DataHandlerWear.kt`, `cwfComplicationUpdater.start()`
      in `WearApp.kt`). Nothing of the fork's own was dropped outside the agreed "theirs" decision.
- [ ] Device smoke - pending (user).

### Build gate

```
./gradlew :app:assembleFullDebug :wear:assembleFullDebug --no-daemon
```

Result: **BUILD SUCCESSFUL in 8m 1s** (exit 0), 0 Kotlin errors. Both APKs written:
`app/build/outputs/apk/full/debug/app-full-debug.apk` and
`wear/build/outputs/apk/full/debug/wear-full-debug.apk`. The first attempt failed on the version
catalog (see above); this is the run after the aliases were restored.

This first run was made **before** the two resolution corrections described above. The gate was run
again on the final tree afterwards: **BUILD SUCCESSFUL in 34s** (exit 0), everything up to date
except `:app:packageFullDebug` and `:app:assembleFullDebug`, which re-ran with the corrected
`implementation` resources. Checked in the built APK: `permission_fsi_title` is gone from
`resources.arsc` and `permission_dnd_title` is still there.

### Host unit tests

```
./gradlew testFullDebugUnitTest testDebugUnitTest testAndroidHostTest --no-daemon
```

Result: **BUILD SUCCESSFUL in 15m 55s** (exit 0) - 14 306 tests, 0 failures, 0 errors, 14 skipped
(`wear`, `automation`, `aps`, `carelevo`, `sync`, `implementation` and the rest).

The **first** run of this command failed, and that is what found the `AutomationRuntime` resolution
error above: `:plugins:automation:testAndroidHostTest` stopped with
`expected: 1 but was: 0` in `AutomationRuntimeProcessingTest`. Because Gradle stops at the first
failing task, the modules after `:plugins:automation` never ran in that pass - the numbers above are
from the complete run after the fix.

## Known follow-ups

1. **`scripts/merge_invariants.sh` does not exist.** The checklist names it in two places
   (invariant baseline, and the "Latest merge log" list). It has never been in this repository's
   history. The values were checked by hand for this merge (catalog, duplicates, `injectMetroMembers`,
   the DB gate, the AIMI file count). Either add the script or point the checklist at a note.
2. **`complication_cwf_lower` / `complication_cwf_upper`** are unused on **both** sides after this
   merge. They were left alone on purpose: the file is a hot upstream file, and removing strings
   that upstream still ships would create a difference to carry in every future merge. Delete them
   upstream first.
3. **iOS/desktop compilation**: unchanged from the 2026-09-14 follow-up. Fork files in `commonMain`
   with `android.*` / `java.*` imports still stop `:ui:compileKotlinIosArm64`.
