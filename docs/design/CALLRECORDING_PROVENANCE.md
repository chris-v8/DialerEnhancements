# PROVENANCE.md: who wrote what in CallRecording v1.5

Analysis of `C:\dev\CallRecording` at commit `a7d9c1f` (tag `v1.5`, branch `main`), made on 2026-11-25 for the clean rewrite as **DialerEnhancements**.

Method: `git log --follow` for every tracked file, `git blame -w` for line ranges (and `git blame -C -C -C` on Chris's files to catch lines copied from other files), the diff of every commit by Jie and Chris, and checks of file headers and upstream projects for the third-party code. Sections 4 and 5 say which licence facts were checked online and which are from memory.

> This is an engineering inventory, not legal advice.

---

## 1. The people (and one company) behind the code

| Label used below | Git author(s) | Commits | Dates | Licence they gave |
|---|---|---|---|---|
| **vvb2060** | `vvb2060 <vvb2060@gmail.com>`, `南宫雪珊 <vvb2060@gmail.com>` (same e-mail, same person) | 14 (`c47c614` initial … `f394f5d` v1.3) | 2022-07-09 → 2026-04-14 | **None** (all rights reserved; explicitly refused) |
| **Jie** | `Jie <1614094+yagay@users.noreply.github.com>` (GitHub `yagay`) | 6 (`ecb000e` … `c469774`) | 2026-11-03 | **None** (all rights reserved; explicitly refused) |
| **Chris** (the user) | `Chris <chris.laboratories@proton.me>` | 12 (`622bd3c` … `a7d9c1f`) | 2026-11-24 | His own, free to relicense |
| **Third party** | not in git history as authors; code was copied in by vvb2060's first commit | — | — | See section 4 |

Note: Jie's `c469774` is titled "Merge …" but is a single-parent (squashed) commit. Its content counts as Jie's.

Legend used in the tables:

- **Reuse?** **Yes**: may be carried into DialerEnhancements (Chris's own code, or third-party code under a real licence, with that licence's conditions). **No**: all-rights-reserved code by vvb2060 or Jie; re-implement from SPEC.md instead. **Partly**: mixed file; only the listed parts are Chris's.
- "Trivial idiom": a handful of lines that match lines in someone else's file only because they are standard Android/Java boilerplate (for example an `@Override` line or a dp-to-pixel helper). These are not meaningful copying, but a clean rewrite types them fresh anyway.

---

## 2. File-by-file inventory (every tracked file)

### 2.1 Repository root, build and CI

| File | Authors (by lines) | Third-party? | Reuse? | Notes |
|---|---|---|---|---|
| `.gitattributes` | vvb2060 | — | No (write your own; 3 lines of standard settings) | |
| `.gitignore` | vvb2060 | — | No (write your own; standard list) | |
| `app/.gitignore` | vvb2060 | — | No (1 line) | |
| `build.gradle.kts` (root) | vvb2060 | — | No (1 line) | |
| `settings.gradle.kts` | vvb2060 | — | No | Uses the AGP "settings" plugin to set minSdk 28 / compile+target 37 / NDK / build-tools centrally; repos incl. `api.xposed.info`. |
| `gradle.properties` | vvb2060 | — | No | Two lines (configure-on-demand, configuration cache). |
| `app/build.gradle.kts` | vvb2060 (all except 2 lines); Chris lines 21–22 (versionCode 6 / versionName 1.5) | Imports internal AGP classes | No | Custom tasks `optimizeReleaseRes` and `delMetadata` are vvb2060's. Jie's version bump to `1.4-auto` was replaced by Chris's. |
| `app/proguard-rules.pro` | vvb2060 | — | No | |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | committed by vvb2060 | **Gradle project**, Apache-2.0 (header in `gradlew` checked) | Yes, but **regenerate** with `gradle wrapper` instead of copying | The `.properties` file only pins the Gradle version. |
| `.github/workflows/android.yml` | vvb2060 (lines 1–11, 13–40); Chris line 12 (matrix without macOS) | — | No | "Build" workflow: Ubuntu + Windows matrix, publishes Gradle build scans. |
| `.github/workflows/debug.yml` | Jie (lines 1–5, 7–56); Chris line 6 (`main` instead of `master`) | — | No | "Debug APK" workflow. |

### 2.2 Documentation

| File | Authors | Reuse? | Notes |
|---|---|---|---|
| `README.md` | Chris (all 656 lines) | **Yes, with edits** | Chris's prose. But it **quotes or closely paraphrases upstream code** in places: §4.1 (a shortened version of vvb2060's TTS hook), §5 "Anatomy of a hook" (vvb2060's `hookCanRecordCall`, almost verbatim with added comments), plus single lines flagged by blame at 154, 157, 213, 468. Remove those snippets or replace them with your own pseudo-code before reuse. §4.4 also contains pseudo-code of **Google's** decompiled method; keep that as a plain-language description rather than code. |
| `CHANGELOG.md` | Chris | Yes (as history) | Describes CallRecording releases; a new project normally starts a fresh changelog. |
| `AUTO_RECORDING.md` | Jie (lines 1–7, 9–32, 34–38, 40–47); Chris lines 8, 33, 39 | Partly (Chris's 3 lines only) | Chris's lines are facts: marker names and obfuscated names per Google Phone version. Facts can be restated freely in your own words. |
| `RECORDING_START_TIMING.md` | Jie | No | The facts it reports (Google Phone 244 call/recording sequence) are restated in SPEC.md §F8 in new words. |

### 2.3 Android manifest, assets, resources

| File | Authors (by lines) | Reuse? | Notes |
|---|---|---|---|
| `app/src/main/AndroidManifest.xml` | vvb2060 1–3, 12–15, 38–49 (application element, Xposed meta-data); Chris 4–11 (READ_CONTACTS, `<queries>` for Google Phone), 26–32 (picker activity); Jie 16–25 (launcher activity), 33–37 (provider) | Partly | Manifest entries are largely functional boilerplate, but write a fresh one (new package, new class names). |
| `app/src/main/assets/xposed_init` | vvb2060 | No (1 line naming the entry class; new class anyway) | |
| `res/values/strings.xml` | vvb2060 | No | App name and Xposed description (2 strings). |
| `res/values-zh-rCN/strings.xml` | vvb2060 | No | Chinese versions of the above. |
| `res/values/auto_settings.xml` | Jie (incl. his own apostrophe fix `f27a639`) | No | 17 strings for the settings screen and status texts. Write new wording. |
| `res/values-zh-rCN/auto_settings.xml` | Jie | No | |
| `res/values/selected_numbers.xml` | Chris | **Yes** | 16 strings for the Selected-numbers feature and picker. |
| `res/values-zh-rCN/selected_numbers.xml` | Chris | **Yes** | Chinese translation by Chris. |

### 2.4 Java: hook side (`app/src/main/java/io/github/vvb2060/callrecording/xposed/`)

| File | Created by | Line authorship (blame) | Reuse? |
|---|---|---|---|
| `Init.java` (368 lines) | vvb2060 | vvb2060 1–64, 69, 74–85, 89–91, 93–95, 100–281, 359–361, 366–368 · Chris 65–68, 70–73, 86–88, 92, 96–99, 305–315, 318, 320–324, 332–353, 364–365 · Jie 282–304, 316–317, 319, 325–331, 354–358, 362–363 | **No** (mixed; see 3.1) |
| `DexHelper.java` (87 lines) | vvb2060 | vvb2060 all | **No** (Java front-end for the native search tool; see section 4) |
| `AutoRecordingHook.java` (149 lines) | Jie | Jie 1–25, 36–41, 46, 49–50, 65, 68, 72–94, 96, 98–111, 114–149 · Chris 26–35, 42–45, 47–48, 51–64, 66–67, 69–71, 95, 97, 112–113 | **No** (mixed; majority Jie; see 3.2) |
| `RecordingTimingHook.java` (178 lines) | Jie | Jie all | **No** |
| `SettingsBridge.java` (102 lines) | Jie | Jie 1–25, 63–102 · Chris 26–62 | **Partly** (see 3.6) |
| `BubbleHook.java` (130 lines) | **Chris** | Chris all (copy-detection flags only trivial idioms: an `@Override`/hook-method header pair and the "exactly one result" return line) | **Yes** (adapt: calls Jie's factory finder and vvb2060's DexHelper) |
| `SelectedNumbersHook.java` (362 lines) | **Chris** | Chris all (copy-detection flags only trivial idioms: imports, hook-method headers, a 3-line "decode index → is it a Method → cast" loop body) | **Yes** (adapt: uses DexHelper and SettingsBridge) |
| `ProtoNumbers.java` (87 lines) | **Chris** | Chris all | **Yes, as-is** (self-contained, no upstream dependencies) |

### 2.5 Java: app side (`.../callrecording/settings/`)

| File | Created by | Line authorship (blame) | Reuse? |
|---|---|---|---|
| `SettingsActivity.java` (174 lines) | Jie | Jie 1–4, 6–45, 51–68, 104–174 · Chris 5, 46–50, 69–103 | **Partly** (see 3.7) |
| `SettingsProvider.java` (133 lines) | Jie | Jie 1–11, 15–16, 21–28, 36–51, 62–63, 70–133 · Chris 12–14, 17–20, 29–35, 52–61, 64–69 | **Partly** (see 3.8) |
| `NumberPickerActivity.java` (379 lines) | **Chris** | Chris all (copy-detection flags trivial idioms: `onCreate` header, creating a vertical LinearLayout, and the 4-line dp-to-pixel helper that is identical to Jie's) | **Yes** (adapt: refers to Jie's provider constants) |

### 2.6 Native code (`app/src/main/jni/`)

| File | Committed by | Real origin | Licence | Reuse? |
|---|---|---|---|---|
| `Android.mk` | vvb2060 | vvb2060 | none | No |
| `Application.mk` | vvb2060 | vvb2060 | none | No |
| `logging.h` | vvb2060 | log-macro boilerplate; exact origin not identified | none stated | No (not needed) |
| `wrapper.cc` (959 lines) | vvb2060 | JNI glue: maps the app's DEX files (or unzips them from the APK), feeds them to DexHelper, converts results to Java reflection objects, registers natives for `io/github/vvb2060/callrecording/xposed/DexHelper`. **No licence header.** The structure (memory-map helper, hand-written ZIP local-header reader, a tuple of helper + mappings as the handle) closely resembles the JNI wrapper of the BiliRoaming module (GPL-3.0). **This origin is from memory and could not be verified**: BiliRoaming's GitHub repository has been archived and emptied after a cease-and-desist letter. | Unknown: vvb2060's (all rights reserved) or a GPL-3.0 derivative | **No** |
| `dex_helper/Android.mk` | vvb2060 | vvb2060 (build glue) | none | No |
| `dex_helper/dex_helper.cc` (907 lines), `dex_helper/include/dex_helper.h` (152 lines) | vvb2060 | **LSPosed/DexBuilder** (`dex_helper.cc`, `include/dex_helper.h`). **Verified**: same class, same public API; this copy is an older or modified revision (an opcode-length table, `std::unordered_map` instead of `phmap`, extra `on_dex` parameters, about 255 diff lines against today's master). | **LGPL-3.0** (DexBuilder's LICENSE file, checked) | Usable under LGPL-3.0, but **take it from LSPosed/DexBuilder at a pinned commit**, not from this copy. Not recommended anyway (see REWRITE_PLAN §7). |
| `dex_helper/slicer/*.cc` (7 files), `dex_helper/include/slicer/*.h` (16 files) | vvb2060 | **AOSP `slicer`** (platform/tools/dexter), as vendored by LSPosed/DexBuilder | **Apache-2.0**: every file carries "Copyright (C) 2017 (or 2019) The Android Open Source Project" and the Apache-2.0 notice (checked) | Usable under Apache-2.0 (keep headers), but take it from AOSP or DexBuilder if ever needed. Not needed with the recommended design. |

The 23 slicer files, each Apache-2.0 / AOSP:
`slicer/common.cc`, `dex_bytecode.cc`, `dex_format.cc`, `dex_ir.cc`, `dex_utf8.cc`, `reader.cc`, `writer.cc`; `include/slicer/arrayview.h`, `buffer.h`, `chronometer.h`, `common.h`, `dex_bytecode.h`, `dex_format.h`, `dex_instruction_list.h` (2019), `dex_ir.h`, `dex_leb128.h`, `dex_utf8.h`, `hash_table.h`, `index_map.h`, `memview.h`, `reader.h`, `scopeguard.h`, `writer.h`.

---

## 3. Function-level attribution for mixed and significant files

### 3.1 `Init.java`

| Member | Author | Introduced in | Notes |
|---|---|---|---|
| `TAG`, the 44-byte silent-WAV byte array | vvb2060 | `c47c614` | |
| `hookCanRecordCall` | vvb2060 | `c47c614` | |
| `GEOFENCE_MARKERS` (+ comment) | **Chris** | `0eb149e` | The idea "try both names" is Chris's. |
| `hookWithinCrosbyGeoFence` | **Mixed**: vvb2060's search call and replacement body (`c47c614`); Chris's loop over the two markers, "hooked" flag and combined warning (`0eb149e`) | | |
| `hookGetSupportedLocaleFromCountryCode` | vvb2060 | `1febab0`, removed `3a93c0d`, re-added `1aa5f7d` | |
| `hookIsCallRecordingCountry` | vvb2060 | `1aa5f7d` | |
| `hookDispatchOnInit`, `hookIsLanguageAvailable` | vvb2060 | `1febab0` | |
| `hookSynthesizeToFile` | vvb2060 | `c47c614`; failure fallback and `onDone` call added in `1febab0` | |
| `hookActivityOnResume`, `checkUnsupportedVersion` | vvb2060 | `3a93c0d`; permission check `1aa5f7d` | |
| `HookStep` interface, `runHookStep` | Jie | `c469774` | |
| `installEligibilityHooks` | **Mixed**: Chris's method and split (`622bd3c`): synchronous phase, helper kept open and returned, duration log. Jie's lines kept from his `installHooks` (open-begin/opened log lines, catch block, `finally` with the three TTS steps). | | |
| `installDeferredHooks` | **Mixed**: Chris's method (`622bd3c`) plus his bubble (`0eb149e`) and selected-numbers (`df66d99`) steps. Jie's lines: the record-all and timing step calls, the activity-resume step, the worker log lines. | | |
| `handleLoadPackage` | **Mixed**: vvb2060 (package filter, final debug log); Jie (timing "process entry" + call-state hook); Chris (synchronous eligibility call, worker thread taking the open helper) | | |

### 3.2 `AutoRecordingHook.java`

| Member | Author | Notes |
|---|---|---|
| Class comment, `TAG`, `INSTALLED`, `FIRST_HIT` | Jie | |
| `POLICY_MARKERS` (three markers + comment) | **Chris** (`0ae1445`) | The v88 markers were found by Chris. |
| `install` | **Mixed**: Jie's original single-marker flow; Chris rewrote it as a loop over markers that hooks every unique match and reports one combined status (`0ae1445`). | |
| `hookPolicy` | Jie's hook body, **moved** into its own method by Chris (lines 72–94 blame to Jie) | |
| `discoverPolicy` | Jie; Chris made the marker a parameter | |
| `discoverCompletedFutureFactory` | Jie; Chris only widened visibility and added a one-line comment (`0eb149e`) | Used by Chris's `BubbleHook`. |
| `status` | Jie | |

### 3.3 `RecordingTimingHook.java`: all Jie (`c469774`)

`installCallState`, `markAutoPolicy`, `installNativePipeline`, `uniqueRecorderMethod`, `uniqueDisclosureMethod`, `markModuleLoaded`, `event`. Not reusable.

### 3.4 `BubbleHook.java`: all Chris (`0eb149e`)

`install`, `evaluate`, `uniqueAtomicBoolean`, `discoverProducer`. Depends on vvb2060's `DexHelper` API and Jie's `discoverCompletedFutureFactory`; both calls are replaced during the port.

### 3.5 `SelectedNumbersHook.java` and `ProtoNumbers.java`: all Chris (`df66d99`, `328b467`, `51fe7a5`, `ba07e94`)

`SelectedNumbersHook`: `install`, `hookResult`, `hookPendingQueue`, `apply`, `hookRemoveListener`, `singleMethod`, `hookAddClick`, `hookPickerLaunch`, `currentNumbers`, `reportSnapshot`, `viewCreatedOf`, `findUnique`, inner class `Remover`. Uses `DexHelper` and `SettingsBridge`.
`ProtoNumbers`: `collect`, `parse`, `varint`, `looksLikeNumber`. No dependencies.

### 3.6 `SettingsBridge.java`

| Member | Author |
|---|---|
| `URI`, `allCallsEnabled`, `diagnosticsEnabled`, `report`, `read` | Jie (`ecb000e`) |
| `currentApplication` (reflection via `AndroidAppHelper`, then `ActivityThread`) | Jie (`87482af`) |
| `multiPickEnabled`, `takePending`, `application`, `reportSelectedNumbers` | **Chris** (`df66d99`, `51fe7a5`, `ba07e94`), written in the same style as Jie's `read()` |

### 3.7 `SettingsActivity.java`

| Member | Author |
|---|---|
| `onCreate` (all except lines 46–50), `setting`, `refreshStatus`, `label`, `add`, `dp`, the status-code mapping | Jie |
| Lines 46–50 in `onCreate` (Selected-numbers section), the extra refresh in `onResume`, `selectNumbers`, `refreshPending`, the `pending` field, the `Intent` import | **Chris** |

### 3.8 `SettingsProvider.java`

| Member | Author |
|---|---|
| `PREFS`, `AUTO`, `DEBUG`, `LAST_STATUS`, `LAST_CHECK`, `LAST_HIT`; `onCreate`; the "read" branch (first two values); the "report" branch with its whitelist; `verifyCaller`; the unsupported CRUD methods | Jie |
| `MULTI_PICK`, `PENDING`, `PENDING_REMOVALS`, `SNAPSHOT`; the third value in "read"; the "take_pending" and "selected_snapshot" branches; the class-comment changes | **Chris** |

### 3.9 `NumberPickerActivity.java`: all Chris

`onCreate`, `onRequestPermissionsResult`, `load`, `filter`, `syncChecks`, `updateButton`, `finishWithChanges`, `existingNumbers`, `matchKey`, `showMessage`, `digits`, `dp` (a trivial idiom identical to Jie's helper), inner class `Entry`.

### 3.10 `DexHelper.java` and `wrapper.cc`: vvb2060 (see 2.6)

`DexHelper.java` declares the native methods `findMethodUsingString`, `findMethodInvoking`, `findMethodInvoked`, `findMethodSettingField`, `findMethodGettingField`, `findField`, the encode/decode methods for class/method/field indices, `createFullCache`, `close`, `load`, plus a `finalize()` that closes. `wrapper.cc` implements them over DexBuilder's `DexHelper` C++ class. The project only uses `findMethodUsingString`, `findMethodInvoking`, `decodeMethodIndex`, `encodeMethodIndex`, `encodeClassIndex` and `close`.

### 3.11 `app/build.gradle.kts` (vvb2060)

| Part | What it does |
|---|---|
| `android { … }` | Java 21, minify + resource shrinking in release, release **signed with the debug key**, VCS info excluded, ndk-build for 4 ABIs with parallel jobs, **all Java resources excluded from the APK** (`excludes += "**"`), native libs stored uncompressed. |
| `optimizeReleaseRes` | Runs `aapt2 optimize` on the release resource bundle (collapse resource names, sparse encoding) at a hard-coded intermediate path, then swaps the file in. |
| `delMetadata` | After `packageRelease`: reopens the APK with AGP's internal `apkzlib`, aligns `.so` files to 16 KB, removes timestamps, sorts entries, **re-signs with the debug key** (v2 only, v1 off) and deletes AGP's app-metadata entry. |
| `tasks.configureEach { … }` | Hooks both tasks into the release build. |

Chris changed only `versionCode`/`versionName`.

---

## 4. Third-party code and licences

| Component | Where | Origin | Licence | How checked |
|---|---|---|---|---|
| AOSP slicer | `jni/dex_helper/slicer`, `include/slicer` | Android Open Source Project (`platform/tools/dexter/slicer`), vendored via LSPosed/DexBuilder | Apache-2.0 | **Verified**: headers in all 23 files |
| DexHelper C++ class | `jni/dex_helper/dex_helper.cc`, `include/dex_helper.h` | LSPosed/DexBuilder | LGPL-3.0 | **Verified**: GitHub sidebar and the repository's LICENSE file ("GNU LESSER GENERAL PUBLIC LICENSE Version 3"); local copy diffed against DexBuilder master (same class and API, older/modified revision) |
| JNI wrapper | `jni/wrapper.cc` | Possibly derived from BiliRoaming's JNI wrapper | BiliRoaming: GPL-3.0 per its SourceForge mirror listing; the file itself has no header | **Not verified** (from memory). BiliRoaming's GitHub repo is archived and contains only a README and a cease-and-desist letter. Treat `wrapper.cc` as not reusable either way. |
| Gradle wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle | Apache-2.0 | **Verified**: SPDX header in `gradlew` |
| Xposed API 82 | `compileOnly("de.robv.android.xposed:api:82")` (not shipped in the APK) | rovo89/XposedBridge | Apache-2.0 | **Verified**: the Maven POM at api.xposed.info names "The Apache Software License, Version 2.0". The archived GitHub repo shows no licence badge. |
| AndroidX Annotation 1.3.0 | `compileOnly` (not shipped) | Google/AndroidX | Apache-2.0 | From memory |

Google Phone itself (`com.google.android.dialer`) is Google's copyrighted code. Its method names, log strings and behaviour can be used as facts and search keys. Decompiled Google code should not be pasted into the new repository or its docs; describe it instead.

---

## 5. What Chris can carry over

**Reusable as Chris's own work** (all need adapting to the new search engine, settings channel and names):

1. `ProtoNumbers.java`: as-is (pure Java, no dependencies).
2. `SelectedNumbersHook.java`: logic and design; rewire to the new finder, bridge and constants.
3. `BubbleHook.java`: logic and design; replace the call to Jie's factory finder with the new shared one.
4. `NumberPickerActivity.java`: logic; the UI gets redone for Material 3 anyway.
5. `res/values/selected_numbers.xml` and `res/values-zh-rCN/selected_numbers.xml`.
6. `README.md`: prose, the problem write-ups (§4.2 race condition, §4.3–4.6), the test checklist, the log guide and the backup instructions, **after removing** the upstream code quotes (§4.1 snippet, §5 "Anatomy of a hook") and the decompiled-Google pseudo-code in §4.4.
7. `CHANGELOG.md`: as historical reference.
8. Ideas and small pieces of Chris's in mixed files, to re-type in the new structure:
   - both geofence marker names and the "hook whichever exists" approach;
   - the three record-all markers and the "hook every unique match, report one combined status" approach;
   - the critical (synchronous) versus deferred (worker) startup split;
   - the Selected-numbers settings, pending queue and snapshot additions to the bridge, provider and settings screen;
   - the manifest additions (contacts permission, Google Phone package visibility, exported picker).
   Ideas are free in any case; what matters is that the new text is newly written.

**Must be re-implemented from SPEC.md** (vvb2060 / Jie): `Init.java` (except Chris's ideas above), `DexHelper.java`, `wrapper.cc`, the native build files, `AutoRecordingHook.java`, `RecordingTimingHook.java`, most of `SettingsBridge.java`, `SettingsProvider.java` and `SettingsActivity.java`, `auto_settings.xml` (both languages), `strings.xml` (both languages), the manifest skeleton, Gradle build and settings scripts, ProGuard rules, `xposed_init`, both workflows, `AUTO_RECORDING.md`, `RECORDING_START_TIMING.md`.

**Third-party, if ever needed**: fetch from the original upstream at a pinned version, keep its licence header and add a NOTICE entry. Do not copy from this repository.

---

## 6. Identifiers in the old code that must not be carried over

These carry the old project's identity or are vvb2060's and Jie's names. The new project needs its own:

- Package and applicationId `io.github.vvb2060.callrecording`; provider authority `io.github.vvb2060.callrecording.settings`; intent-extra keys built on the old package; JNI registration path `io/github/vvb2060/callrecording/xposed/DexHelper`.
- Log tags `CallRecording`, `CallRecording/Init`, `CallRecording/AutoAll`, `CallRecording/Timing`, `CallRecording/Bubble`, `CallRecording/Selected`, and the step names (`eligibility-start`, `worker-start`, `native-recorder-timing-and-early`, …).
- Preference file and keys (`module_settings`, `auto_record_all`, `diagnostic_logging`, `last_status`, …) and provider method names (`read`, `report`, `take_pending`, `selected_snapshot`). They are short functional names, but choosing new ones costs nothing and keeps the projects clearly apart.
- Class names (`Init`, `AutoRecordingHook`, `RecordingTimingHook`, `SettingsBridge`, …).

Keep as they are (they are **Google Phone's** strings and Android's names, used as facts): the text markers (`canRecordCall`, `withinCrosbyGeoFence`, `produceIsBubbleFeatureEnabledForCall`, …), Android framework method names (`synthesizeToFile`, `dispatchOnInit`, …), Google Phone's file name `CallRecordingSettingsData.pb`, the MIME type `vnd.android.cursor.dir/phone_v2`.
