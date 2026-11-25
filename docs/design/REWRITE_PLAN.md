# REWRITE_PLAN.md: DialerEnhancements, a clean rewrite

Companion to **SPEC.md** (what the module must do) and **PROVENANCE.md** (who wrote what in CallRecording). This document covers how to build it fresh: architecture, technology choices with their licences, simplifications, the clean-rewrite process and a roadmap from 1.0 to parity and on to 2.0.

> Not legal advice. For anything commercial, or if an upstream author objects, ask a lawyer.

---

## 1. Goals and ground rules

- **Same features as CallRecording 1.5** (SPEC F1–F13), written independently, under a licence of your choosing.
- **Safer by design:** every feature finds its target by marker + shape and must find **exactly one** match, or it does nothing. Google Phone must never crash because of the module.
- **Easy to grow:** adding a feature means adding one class and registering it; no editing of a long startup method.
- **Easy to read for a non-programmer:** small files, one feature per file, plain-language comments, one log format.
- **Not goals for 1.x:** Material Design 3 UI (that's 2.0), new features beyond parity, supporting dialers other than Google Phone.

---

## 2. Decisions for you (with a recommendation for each)

| # | Decision | Options | Recommendation |
|---|---|---|---|
| D1 | **Project licence** | GPL-3.0 · Apache-2.0 · MIT | **GPL-3.0** if you want every fork to stay open source; it's the norm in the LSPosed world (LSPosed itself is GPL-3.0) and works with every dependency below. **Apache-2.0** if you'd rather let anyone reuse your code, including in closed apps; it includes a patent grant. MIT is like Apache-2.0 without the patent clause. All three work with DexKit and the Xposed APIs (§9). |
| D2 | **App id / package** (permanent once published: changing it later makes a new app that LSPosed must re-enable and re-scope, and settings are lost) | `io.github.chrisv8.dialerenhancements` · `io.github.chris_v8.dialerenhancements` · `dev.chrisv8.dialerenhancements` (only if you own `chrisv8.dev`) · `com.chrislaboratories.dialerenhancements` (only if you own that domain) | **`io.github.chrisv8.dialerenhancements`**. It ties to your GitHub account `chris-v8` (Java package names can't contain `-`, so it's dropped). Use the same value for the code package and the applicationId. |
| D3 | **Xposed API** | legacy API 82 · modern libxposed API (102) | **Legacy API 82 for 1.x**, behind a thin "Hooks" layer so switching later touches one file. It's what you've tested on your phone and works with every LSPosed variant. Re-evaluate for 2.0 (§8). Go modern from day one only if you confirm your LSPosed (Vector) build supports libxposed API 101/102. |
| D4 | **DEX search engine** | DexKit · own small Java searcher · keep DexBuilder's dex_helper · dexlib2 | **DexKit 2.3.0** behind a small "Finder" interface, plus a per-version cache (§7). |
| D5 | **Language and UI toolkit** | Java + Android Views (+ Material Components for 2.0) · Kotlin + Jetpack Compose (Material 3) | **Java + Views** for 1.x: one language, no extra plugins, and closest to what you've read so far. For 2.0, Material Components for Android gives Material 3 in Java. Pick Kotlin + Compose only if you want the more modern stack and accept learning two languages. |
| D6 | **Default of "record all calls"** | on (as in 1.5) · off | **Off.** Recording everyone, including contacts, is the riskier default legally and the more surprising one. Turning it on is one tap. |
| D7 | **TTS text blanking** (SPEC F5: 1.5 always replaces the announcement text with empty text) | always (as in 1.5) · only when TTS failed · a separate setting, off by default | **A separate, clearly labelled setting**, with the silent-WAV fallback kept unconditionally (that's what makes recording start). Telling the other side is a legal duty in many places. |
| D8 | **v88 locale shape** (SPEC F4 doesn't match v88) | leave it (v88 uses TTS → silent fallback) · also accept v88's shape (then v88 plays the built-in English announcement) | Your call. It changes what the other party hears on v88. |
| D9 | **Timing diagnostics (F8)** | keep for parity · keep as optional "diagnostics" · drop | **Optional, off by default**, added last (1.5 milestone). |
| D10 | **Languages** | English · + Polish · + Simplified Chinese | English first. Your own Chinese strings for Selected numbers can be carried over; other Chinese strings must be newly translated. Polish is a natural addition for you. |
| D11 | **Release signing** | debug key (as in 1.5) · your own release key | **Your own release key**, kept outside the repo; CI gets it from GitHub Secrets. |

---

## 3. Architecture at a glance

```
LSPosed starts Google Phone
        │
        ▼
 Entry (legacy entry point)            ── ignores everything but com.google.android.dialer (main process)
        │
        ├─ Startup.critical()            synchronous, before Google Phone code runs
        │     ├─ Settings.loadLocalCopy()        (no IPC: last known settings, kept in Google Phone's own storage)
        │     ├─ Finder.open()  → cache hit? resolve by name : search with DexKit
        │     └─ for each CRITICAL feature (registry order): guard( discover → install )
        │
        └─ Startup.deferred()            one background thread
              ├─ Settings.refreshFromApp()       (one IPC; also re-saves the local copy)
              ├─ for each DEFERRED feature: guard( discover → install )
              ├─ Finder.close(); DiscoveryCache.save()
              └─ Bridge.reportStatus(all feature results)   (one IPC)

Feature registry (one class per feature)
  F1 can-record · F2 geofence · F3 country · F4 locale · F5 tts · F6 setup-check
  F7 record-all · F9 bubble · F10 selected-numbers · F8 timing (diagnostics)

Shared helpers
  Finder (marker + shape → exactly one) · FutureFactory · Hooks facade · Log · Guard · Contract

Module app (separate process)
  MainActivity (settings + per-feature status) · NumberPickerActivity · BridgeProvider · SettingsStore
```

---

## 4. Source layout

One Gradle module `app`, a handful of packages. Pure-logic code doesn't touch Android or Xposed, so it can be unit-tested on a PC.

```
io.github.chrisv8.dialerenhancements
├─ shared/                 used by both processes
│   Contract              authority, provider method names, intent-extra keys, setting keys, feature ids, Google Phone package
│   FeatureId             enum of all features with default on/off and phase
├─ hook/                   runs inside Google Phone
│   Entry                 Xposed entry point; process filter; calls Startup
│   Startup               critical + deferred phases; walks the registry
│   Feature               the contract every feature implements
│   FeatureRegistry       ordered list of all features
│   FeatureResult         ACTIVE(n targets) / NOT_FOUND / AMBIGUOUS(n) / DISABLED / FAILED(reason)
│   Guard                 runs a step, times it, catches everything, returns a FeatureResult
│   Hooks                 facade: before / after / replace / callOriginal / during(method) flag
│   Log                   one logger → logcat + LSPosed log, consistent format
│   HookSettings          in-memory snapshot of settings; local copy; refresh
│   Bridge                client side of the app channel (status, hits, queue, snapshot)
│   find/
│     Finder              "find by marker + shape, exactly one or skip"
│     MethodShape         small builder: params(...), returns(...), returnsSubtypeOf(...), isStatic()
│     DexKitFinder        Finder implemented with DexKit
│     DiscoveryCache      per Google Phone version: feature → method descriptors
│     FutureFactory       SPEC F7.1, shared by record-all and bubble
│   features/
│     CanRecordFeature, GeofenceFeature, CountryFeature, AnnouncementLocaleFeature,
│     TtsFallbackFeature, SetupCheckFeature, RecordAllFeature, BubbleFeature,
│     TimingDiagnosticsFeature, selected/SelectedNumbersFeature (+ helpers)
│   util/
│     SilentWav           builds the 44-byte header from format values (SPEC F5)
│     ProtoNumbers        Chris's reader, carried over (SPEC F10.7)
│     PhoneKeys           number matching (SPEC F10.8a, improved, see S12)
└─ app/                    the module's own app
    MainActivity, NumberPickerActivity, BridgeProvider, SettingsStore, StatusStore
```

### 4.1 The feature contract (illustrative; own design)

Every feature answers five questions:

| Question | Meaning |
|---|---|
| `id()` | Stable id, e.g. `recording.record_all`. Used for the switch, logs, status and cache. |
| `phase()` | `CRITICAL` (before Google Phone runs) or `DEFERRED` (background). |
| `enabledByDefault()` | Default of its on/off switch. |
| `discover(finder)` | Finds its targets. Returns them, or NOT_FOUND / AMBIGUOUS with a reason. Pure lookup, no hooking. |
| `install(targets, hooks, settings)` | Installs the hooks. Must leave Google's behaviour untouched for any input it doesn't recognise. |

`Startup` calls `discover` then `install` for each enabled feature inside `Guard`, collects one `FeatureResult` per feature and reports them all at the end in one call. The app shows one status line per feature (for example "Bubble: active (1 target)", "Record all: not found on this Google Phone version").

### 4.2 The one finder helper

All marker lookups go through **one** function with one rule:

> Given a marker (exact string) and a shape, return the method if exactly one method in Google Phone matches. Otherwise return "not found" or "ambiguous (n)", with the candidates listed in the debug log.

Variants, all built on the same rule:

- `unique(marker, shape)`: SPEC F1–F4, F7, F8, F9, F10.
- `uniqueEach(markers[], shape)`: tries every marker and returns each unique hit (SPEC F2 with two geofence names, F7 with three producers).
- `uniqueCallee(method, shape)`: SPEC F7.1, F8 disclosure, F10 registration helper.
- `uniqueInBoth(markerA, markerB, shape)`: methods that use both strings (SPEC F9).
- `uniqueDeclared(class, shape)`: reflection-only lookup inside a known class (SPEC F10 view-created method).

The shape builder keeps rules readable, e.g. "one parameter of exactly `Object`, returns something that is a `Future`". In 1.5 each of these was written out separately, about 10 times, with 10-argument calls (S1).

### 4.3 Discovery cache

- **Key:** Google Phone `versionCode` + `lastUpdateTime` (from the package info) + the module's own `versionCode`. Any change means a fresh search.
- **Value:** for each feature, its targets as descriptors (declaring class name, method name, parameter type names). DexKit's `MethodData.descriptor` and `DexKitBridge.getMethodData(descriptor)` are designed for this (both verified in DexKit's source).
- **Where:** a small file in Google Phone's **device-protected** storage (`appInfo.deviceProtectedDataDir`). Google Phone can start before the phone is unlocked after a reboot, when normal storage is still locked. If the file is missing or unreadable, simply search again.
- **On load:** resolve each descriptor with plain reflection. If any fails, discard the whole cache and search again.
- **Effect:** the slow search (≈55–105 ms per cold start today, ≈600 ms on the first start of a new version) happens only once per Google Phone update. Other starts take a few milliseconds of reflection.

### 4.4 Hooks facade

A tiny class with five operations: hook *before*, hook *after*, *replace*, *call the original*, and a scoped flag "while method X is running on this thread" (used by SPEC F10.2). Only this class imports the Xposed API. Switching to libxposed later (§8) means rewriting one file.

### 4.5 Logging

- One `Log` helper with levels and one format: `[feature-id] message key=value …`. Each line goes to logcat (tag = app short name) **and** to the LSPosed log for INFO and above.
- What gets logged at INFO: each feature's result at startup (one line: status, number of targets, milliseconds) and the first time each feature actually changes something in a process.
- DEBUG (only when "diagnostic logging" is on): the methods found, the candidates when ambiguous, per-call details.
- Never: phone numbers, names, audio, file paths, announcement text.
- No reliance on R8 stripping debug calls: the logger checks the setting itself, so release and debug builds log the same things.

---

## 5. Settings and the app ↔ Google Phone channel

### 5.1 What has to flow

| Data | Direction | When | Size |
|---|---|---|---|
| Feature switches, record-all, multi-select, diagnostics | app → Google Phone | at start; when changed | tiny |
| Feature results (status page) | Google Phone → app | once per Google Phone start | tiny |
| "First hit" events (record-all, bubble) | Google Phone → app | once per process per feature | tiny |
| Pending Selected-numbers changes | app → Google Phone, then acknowledged | when the Selected-numbers screen opens | small |
| Snapshot of Google's Selected-numbers list | Google Phone → app | screen open, Add redirect, after changes | small |

### 5.2 Options

| Option | How | Pros | Cons |
|---|---|---|---|
| **A. ContentProvider in the module app** (1.5 approach, redesigned) | Hooks call `call(method, …)` on the app's provider; the provider checks the caller's UID | Works with legacy API; proven on your phone; two-way | Cross-process call (can wake the app's process); Android 11+ package visibility needs testing; unavailable before first unlock unless the app is direct-boot aware |
| **B. Legacy API + LSPosed's "new XSharedPreferences"** (from memory) | App writes a "world-readable" prefs file, which LSPosed redirects to a shared place; hooks read it with `XSharedPreferences` | Settings read without IPC | One-way only (still need A for reports, queue, snapshot); LSPosed-specific behaviour |
| **C. libxposed remote preferences + service** (verified to exist; details from memory) | App writes preferences through the libxposed service; hooks read them locally | No IPC for reads; framework-managed | Needs modern API and framework support; read-only on the hook side (still need A for the Google Phone → app direction) |

### 5.3 Recommendation for 1.x: Option A, redesigned

1. **One `Contract` class** shared by both sides: authority (`<package>.bridge`), method names, keys, extra names. Nothing is duplicated (1.5 spelled the same strings in two places and hard-coded the old package in hook code).
2. **Hooks never call the app on a hot path.** `HookSettings` keeps an in-memory snapshot:
   - The critical phase uses a **local copy** saved in Google Phone's device-protected storage by the previous run (no IPC on the startup path). On the very first run there is no copy, so built-in defaults apply.
   - The deferred phase fetches fresh values once, updates the copy, and registers a content observer. The app's provider sends a change notification whenever a setting changes, and the snapshot refreshes in the background.
   - Record-all and the multi-select click read the in-memory snapshot, with no IPC per call or per click (S5).
3. **Provider methods:** `getSettings`, `reportResults(batch)`, `reportHit(feature)`, `putSnapshot(list)`, `peekQueue` → items + token, `ackQueue(token)`. The last two replace take-and-clear (S9).
4. **Security, kept from 1.5:** allow only the app's own UID and Google Phone's UID; refuse everything else; no `query/insert/update/delete`.
5. **Test package visibility on Android 11+:** Google Phone, not the module, is the one asking, and you can't change Google Phone's manifest. So it must already be able to see the module's provider and picker. On the test phone this worked, either because Google Phone holds the "see all packages" permission or because Android makes an app visible to apps it has interacted with (the module app launches Google Phone). Add an explicit check at startup (does the provider resolve?) and show the result on the status page, so a phone where it doesn't work is easy to spot.
6. **Before first unlock:** the hooks use the local copy, so behaviour doesn't change after a reboot even when the app's provider is unavailable.

Plan to move settings to Option C in 2.0 if you adopt libxposed; keep A (slimmed down) for reports, queue and snapshot.

---

## 6. Status page (module app)

One row per feature, with: on/off switch (where it makes sense), last result from Google Phone (active / not found / ambiguous / failed / disabled), number of targets, Google Phone version seen, time of last report, last "hit" time. Plus a "Google Phone not restarted since the settings changed" hint. This replaces 1.5's single record-all status text and makes support questions ("which parts work on my version?") answerable without logcat.

---

## 7. DEX search engine: options and recommendation

**In plain words:** Google scrambles the names of its methods in every release. The search engine reads Google Phone's compiled code and finds methods by the text inside them and by their shape. CallRecording used a C++ tool for this. The rewrite needs a tool that can do the same three queries (SPEC §1.4: methods using a string, methods called by a method, resolve to a real method).

| Option | Licence (status) | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. DexKit** (`org.luckypray:dexkit:2.3.0`, latest on Maven Central, verified) | Apache-2.0 for the library; **LGPL-3.0 for its native `Core/`** (verified in DexKit's README) | Fast native engine; maintained; covers every query: methods by strings plus parameter and return types, callees (`MethodData.invokes`), callers, batch search by strings (verified in source); can open from the APK path or the class loader (`create(apkPath)`, `create(ClassLoader, useMemoryDexFile)`, verified); descriptors for caching (verified) | Adds native libraries for each ABI you ship (APK size); an outside dependency whose API changed between major versions; LGPL notice and source link required | **Recommended for 1.0** |
| **B. Own small Java searcher** | Yours | No native code; smallest APK; fully under your licence; only needs the three queries | ~600–900 lines of careful format-parsing code (DEX header, string and method tables, class data, bytecode walk with instruction sizes from the public Dalvik bytecode spec) plus tests; slower first search (estimate, unmeasured), softened by the cache and by first checking which DEX file contains the marker | Good **later** project if you want zero dependencies |
| **C. Keep DexBuilder's `dex_helper`** (LSPosed/DexBuilder) | LGPL-3.0 (verified); its bundled AOSP slicer is Apache-2.0 | Proven in 1.5 | The JNI wrapper and Java front-end from 1.5 **can't be reused** (PROVENANCE §2.6), so you'd write new C++ glue; NDK build for 4 ABIs; it reads private runtime internals (class-loader path list, DEX "cookie") that can break on future Android versions; older code | Not recommended |
| **D. dexlib2** (from smali/baksmali) | BSD-3-Clause (from memory) | Pure Java, complete DEX model | Heavy and slow for use at app startup; much more than needed | Not recommended |

**Recommendation:** use DexKit through the `Finder` interface (§4.2) with the cache (§4.3). DexKit is then only touched on the first start after a Google Phone update, and replacing it with option B later changes one class.

Integration notes (DexKit):

- Load its native library from the module (the DexKit README shows a `System.loadLibrary("dexkit")` call in its Xposed example).
- Open it once per search session; DexKit's README warns that creating the bridge is expensive. Close it at the end of the deferred phase.
- Prefer opening from Google Phone's APK path(s) (`appInfo.sourceDir`, plus any split APKs that contain code) so behaviour doesn't depend on runtime internals.
- Ship ABIs deliberately: `arm64-v8a` is what your phone needs; add `x86_64` only for emulator testing; `armeabi-v7a` only if you want old 32-bit phones.

---

## 8. Xposed API: legacy 82 vs modern libxposed

| | Legacy API 82 (`de.robv.android.xposed:api:82`) | libxposed API (`io.github.libxposed:api:102.0.0`) |
|---|---|---|
| Licence | Apache-2.0 (verified: Maven POM) | Apache-2.0 (verified: repository) |
| Shipped in your APK? | No (compile-only) | No (compile-only); the app-side `io.github.libxposed:service:102.0.1` is shipped (Apache-2.0, verified) |
| Entry | `assets/xposed_init` + manifest meta-data | `META-INF/xposed/java_init.list`, `module.prop` (minApiVersion, targetApiVersion, staticScope), `scope.list` (verified, LSPosed wiki) |
| Timing callbacks | One "package loaded" callback | `onModuleLoaded`, `onPackageLoaded` (class loader ready, before the app component factory), `onPackageReady` (just before `Application` is created) (verified, javadoc) |
| Hook style | Before/after callbacks with a mutable parameter object | Interceptor chain (`Hooker`, `intercept(chain)`), priorities, deoptimisation, an invoker for calling originals (verified, wiki) |
| Settings channel | Bring your own (provider, or LSPosed's XSharedPreferences) | Remote preferences and remote files managed by the framework (verified that they exist; usage details from memory) |
| Framework support | Every LSPosed variant | Needs a framework implementing that API version. API 100 → 101 → 102 changed the entry style (no constructor parameters; an attach call instead). Check your LSPosed/Vector build first. |
| Pitfall | — | 1.5's build excluded **all** Java resources from the APK; that would silently drop `META-INF/xposed/*`. Don't carry that rule over. |

**Recommendation:** legacy API 82 behind the `Hooks` facade for 1.x. At the start of the 2.0 cycle, check your framework version. If it supports API 101/102, migrate (rewrite `Hooks`, `Entry`, the settings read path) and drop most of the provider's settings role.

---

## 9. Licences and compatibility

| Component | Licence | In a GPL-3.0 project | In an Apache-2.0 project | In an MIT project |
|---|---|---|---|---|
| Xposed API 82 (compile-only) | Apache-2.0 | ✅ | ✅ | ✅ |
| libxposed api / service | Apache-2.0 | ✅ | ✅ | ✅ |
| DexKit Java/Kotlin library | Apache-2.0 | ✅ | ✅ | ✅ |
| DexKit native `Core/` | LGPL-3.0 | ✅ (LGPL-3.0 code may be combined with GPL-3.0) | ✅ as a separately linked library: keep its licence text, say which version you use and where its source is, and let users replace it (dynamic linking plus your published source covers this) | ✅ same as Apache-2.0 |
| DexBuilder `dex_helper` (if option C) | LGPL-3.0 | ✅ | ✅ with LGPL duties; static linking means users must be able to relink, which your published source satisfies | ✅ same |
| AOSP slicer (if option C) | Apache-2.0 | ✅ | ✅ | ✅ |
| AndroidX, Material Components | Apache-2.0 (from memory) | ✅ | ✅ | ✅ |
| Gradle wrapper | Apache-2.0 (verified) | ✅ | ✅ | ✅ |
| CallRecording code by vvb2060 / Jie | none (all rights reserved) | ❌ | ❌ | ❌ |

Housekeeping, whatever you choose:

- `LICENSE` at the repository root; an SPDX line at the top of every source file (`SPDX-License-Identifier: GPL-3.0-or-later`, or `Apache-2.0`, or `MIT`).
- `THIRD_PARTY_NOTICES.md` listing DexKit (both licences, version, source URL) and any AndroidX/Material libraries.
- An "open-source licences" entry in the app's about box, for 2.0.

---

## 10. Build and CI plan

- **Gradle:** generate a fresh wrapper with `gradle wrapper` (don't copy the old files). Current stable AGP; Java 17 or 21; minSdk 28 (as in 1.5), target/compile SDK = latest stable.
- **No NDK** in your own build (DexKit's AAR ships its native libraries). Set `abiFilters` deliberately (§7).
- **Release build:** R8 on with keep rules for the entry class and whatever DexKit documents; resource shrinking on; **your own release key** from environment variables or GitHub Secrets (the 1.5 build signed releases with the debug key); dependency metadata off (`dependenciesInfo { includeInApk = false }`); VCS info off if you prefer. Leave out both 1.5 custom tasks:
  - The resource "optimize" step and the APK re-signing and metadata-deleting step relied on internal AGP classes and hard-coded build paths, and break across AGP versions.
  - Current AGP already aligns uncompressed native libraries for 16 KB pages (from memory: AGP 8.5.1+). Check this once with `zipalign -c -P 16 -v 4` on a built APK.
- **Do not** exclude all Java resources (see §8 pitfall).
- **Unit tests** (plain JVM): ProtoNumbers, number matching, SilentWav, MethodShape rules, the cache key and serialisation, the queue's peek/ack logic.
- **One GitHub Actions workflow:**
  - on push and pull request: Ubuntu only, JDK, Gradle cache, `assembleDebug assembleRelease test lint`, upload the APKs;
  - on a `v*` tag: build the signed release and attach it to a GitHub Release with the changelog section.
  - No public Gradle build scans: the 1.5 workflow auto-accepted Gradle's terms and published scans publicly.

---

## 11. Simplifications and refactors noticed in 1.5

| # | Issue in 1.5 | Change | Why |
|---|---|---|---|
| S1 | About 10 hand-written copies of "search for marker → turn results into methods → filter → pick", with 10-argument calls full of "-1" and "null" | One `Finder` with a shape builder (§4.2) | Less code, one place to fix, readable rules |
| S2 | Two different matching rules: F1–F4 take the **first** match, newer features require **exactly one** | Exactly one everywhere; `uniqueEach` for alternative markers | A future Google build with two candidates would otherwise hook a random one |
| S3 | The completed-Future factory finder lives in the record-all class and is borrowed by the bubble | Own `FutureFactory` component | Shared logic deserves a shared home |
| S4 | The DEX search runs on every cold start inside the critical phase (55–105 ms; ~600 ms first time) | Per-version discovery cache (§4.3) | Faster Google Phone start; less work in a sensitive moment |
| S5 | A cross-process call on **every** record-all decision and every Add click (plus a second one in the error path) | In-memory settings snapshot refreshed on change (§5.3) | No IPC on hot paths; nothing slow on the main thread |
| S6 | Main-thread work in Google Phone: reading the settings file, taking the queue and sending the snapshot when the screen opens, and the click-time bridge read | Background executor; post back to the main thread only to call Google's handler | Avoids UI stutter and ANR risk |
| S7 | **Request code 9 hard-coded** for queued changes | Named constant (documented as observed on v88/v247), **plus learning**: when the Add-button route delivers a result, remember the request code Google used and persist it | A version that uses another code wouldn't silently ignore the queue |
| S8 | Per-thread flag "inside Add click" spread over two hooks | Keep the idea, wrapped in the facade's `during(method)` helper | Same behaviour, reusable, fewer moving parts |
| S9 | The queue is cleared when taken, before Google Phone applies it | `peekQueue` + `ackQueue(token)` after apply | Nothing lost if Google Phone crashes mid-way |
| S10 | Remove-event capture: if several registrations qualify, the last one wins | Require exactly one per screen; otherwise disable removal and log | Same fail-safe rule as everywhere else |
| S11 | Unnamed numbers: 3000 ms snapshot delay, 9 match digits, 6/18 digit limits, protobuf depth 4, WAV values, dp sizes, a separator colour | Named constants with a one-line reason each. For the 3 s delay, consider a `FileObserver` on Google's settings file (refresh the snapshot when it's written) | Readable; removes a timing guess |
| S12 | Number matching on the last 9 digits (country codes collide) | First compare E.164 forms (Android's `PhoneNumberUtils.formatNumberToE164` with the SIM/network country), fall back to last-N digits | Fewer false matches; still works offline |
| S13 | The setup check runs on every activity resume (repeated toasts, a package lookup each time) and uses upstream's sarcastic wording | Check once per process; one neutral toast at most; show it on the status page | Less noise |
| S14 | TTS hooks are process-wide and always blank the text | Setting for blanking (D7); keep the failure fallback; check whether other Google Phone features use TTS (for example call screening) and limit the hooks if needed | Fewer side effects; legal clarity |
| S15 | Logging inconsistent: two sinks used ad hoc, three tag styles, "found" lines vanish in release while "not found" stays, the diagnostics switch affects one line only | One logger and format (§4.5); the diagnostics switch governs debug lines for **all** features | Logs you can rely on |
| S16 | Dead or misleading bits: an "error" status that's never sent, a stored but unused timestamp, a step named after an excluded "early recording" experiment, install-once guards that the orchestrator makes unnecessary | Remove | Less to read |
| S17 | Catch-all blocks everywhere, some completely silent | One `Guard` at the feature boundary returning a typed `FeatureResult`; no empty catch blocks (at least a debug log) | Errors become visible on the status page |
| S18 | The `Application` object found through reflection on Xposed's helper and a hidden framework class | Capture the context once from a hook on application creation (legacy) or from the callback parameters (libxposed) | No hidden-API reliance |
| S19 | Strings shared by both processes written twice (intent extras) and the old package hard-coded in hook code | `Contract` class | One source of truth |
| S20 | Build-script complexity: two custom tasks using internal AGP APIs, the release signed with the debug key, every Java resource excluded, a 4-ABI NDK build with a parallel-jobs flag, two overlapping CI workflows, public build scans | Standard AGP configuration and one workflow (§10) | Fewer things to break with every AGP update |
| S21 | Hooks installed in every Google Phone process | Main process only (or cache-only in others) | Saves startup time in secondary processes |
| S22 | Timing diagnostics always on and always logging | Optional feature, off by default (D9) | Quieter logs; less code in 1.0 |
| S23 | Finalizer-based cleanup in the search-engine front-end | Explicit open/close (try-with-resources) | Finalizers are deprecated and unpredictable |
| S24 | Settings UI hand-built in code | XML layouts in 1.x; Material 3 in 2.0 | Easier to change; ready for MD3 |
| S25 | The record-all hook is installed in the deferred phase, so the first call right after a cold start may miss it | Consider moving it to the critical phase once the cache makes discovery cheap (its factory check calls one of Google's static helpers, so test that this is safe so early) | Consistent behaviour |

Things that looked over-engineered: the 10-parameter native search API used with almost every argument empty; two custom build tasks to save a few kilobytes; a 4-ABI native build for a single arm64 phone; a begin/success log pair for each of ~13 steps (one result line per feature is enough).

---

## 12. Clean-rewrite process (how to keep the new code independent)

**In plain words:** you may re-create the *same features*; you may not *copy their text*. The safest way to show that is to write the new code from a description (SPEC.md) without looking at the old code, keep notes on where every file came from, and check before release that nothing slipped through.

1. **Start from the empty repository** `C:\dev\DialerEnhancements` (it is empty, good). Never add CallRecording as a remote, subtree or submodule; never import its history.
2. **Separate the roles.** This analysis (by someone who read the old code) produced SPEC.md. The **implementation sessions** should be fresh sessions whose working directory is **only** `C:\dev\DialerEnhancements`, given SPEC.md, REWRITE_PLAN.md and Chris's carry-over files, and told not to open `C:\dev\CallRecording`. If you use Claude for the coding, say so explicitly in the first message of each session.
3. **New names and structure** for everything (PROVENANCE.md §6): package, classes, methods, log tags and messages, preference keys, provider methods, intent extras, UI texts. Google Phone markers and Android API names stay; they're facts.
4. **No copy-paste** from vvb2060's or Jie's code, comments, strings or docs, and no "port this file" instructions for their files.
5. **Carry over only Chris's code** (PROVENANCE.md §5): `ProtoNumbers` as-is; the Selected-numbers hook, the bubble logic and the picker adapted to the new structure; your two `selected_numbers.xml` files; your README prose without the upstream code quotes. Copy them from `CallRecording` at commit `a7d9c1f` and note that commit.
6. **Provenance notes:**
   - an SPDX line and a one-line origin comment at the top of every file ("Written from SPEC F9" / "Carried over from CallRecording a7d9c1f, author Chris" / "Third-party: …");
   - `docs/PROVENANCE.md` in the new repo: a table of file, origin, SPEC section and date, updated in the same commit as the file;
   - a commit-message trailer such as `Spec: F10.3`.
7. **Third-party code only through Gradle dependencies** (DexKit, AndroidX, the Xposed APIs), listed in `THIRD_PARTY_NOTICES.md`. No vendored copies.
8. **No decompiled Google code** in the repo or docs; describe behaviour in words (as SPEC.md does).
9. **Before each release, a similarity check:**
   - run a copy-paste detector across both trees, e.g. PMD's CPD (permissive licence, from memory), with a low token threshold, and look at every hit;
   - search the new tree for the old identifiers listed in PROVENANCE.md §6 (old package, tags, keys, step names, status codes);
   - compare the string resources;
   - write the result into the release checklist.
10. **Credits (optional, recommended):** a README line such as "The approach was first shown by the CallRecording module (vvb2060) and a fork by yagay; this project is an independent implementation and contains none of their code." State facts only; don't imply endorsement.
11. Keep the old CallRecording checkout locally, read-only, as evidence of what was and wasn't reused.

*This is not legal advice.*

---

## 13. Roadmap: 1.0 to 1.5-parity to 2.0

Version numbers are your own; DialerEnhancements 1.5 doesn't have to mean the same as CallRecording 1.5. The plan below happens to reach parity at 1.5.

| Milestone | Version | Contents | Done when (SPEC Appendix D items) |
|---|---|---|---|
| **M0 Skeleton** | 0.x (not released) | Repo, LICENSE, wrapper, Gradle, CI; `Entry` with process filter; `Log`, `Guard`, `FeatureRegistry`, `Hooks` facade; `Finder` on DexKit + `DiscoveryCache`; `Contract`; provider with settings + result reporting; a minimal app screen showing "module active in Google Phone version X" | The status page shows Google Phone's version after a restart |
| **M1 Recording works** | **1.0** | F1 can-record, F2 geofence (both names), F3 country, F4 announcement locale, F5 TTS fallback (with the D7 decision), F6 setup check (once per process); critical-phase ordering; per-feature status page; local settings copy | 1, 2, 3, 5, 11 on v88 and v247 |
| **M2 Record all calls** | 1.1 | F7 with `FutureFactory` (F7.1); setting (default per D6); hit reporting; in-memory settings snapshot with change notifications | 4 |
| **M3 Bubble** | 1.2 | F9 on Android 12+ (reuses `FutureFactory`) | 6 |
| **M4 Selected numbers: Add button** | 1.3 | Picker activity (adds), F10.1 discovery, F10.2 redirect, F10.3 replay of adds, F10.7 reader (carried over), F10.8 matching, existing numbers shown ticked; your `selected_numbers` strings | 7 (adds), 8, 10 |
| **M5 Selected numbers: remove and queue** | 1.4 | F10.4 remover; unticking in the picker; F10.5 queue with peek/ack; F10.6 snapshot (file observer or delay); request-code learning (S7) | 7 (removes), 9 |
| **M6 Parity** | **1.5** | F8 timing diagnostics as an optional feature (D9); the diagnostics switch covers all features; README, CHANGELOG, troubleshooting and log guide (your prose); translations (D10); full test pass on v88 and v247; similarity check (§12.9) | All of Appendix D |
| **M7 Toward 2.0** | 1.6–1.9 | Material 3 groundwork (Material Components or Compose, D5); settings and status screens redesigned; decide on the libxposed migration (§8) and spike it on a branch; collect new-feature ideas | — |
| **M8** | **2.0** | Material 3 UI, new features, possibly the modern Xposed API with remote preferences | Your own release criteria |

Tip: each milestone is a small branch → pull request → CI build → test on the phone → merge → tag. Keep the device test log (which Google Phone version, what passed) in the pull request.

---

## 14. Testing approach

- **On the phone, per release:** SPEC Appendix D on Google Phone v88 (system) and v247 (update), with force-stops between module installs. Also once on a fresh boot before unlocking (the local settings copy and the cache in device-protected storage).
- **Logs:** each feature's one-line startup result in the LSPosed log; the status page should agree with it.
- **Unit tests on the PC** for the pure logic (§10).
- **Unknown Google Phone versions:** the status page tells which features found their targets; nothing should crash.

---

## 15. What was verified online and what is from memory

| Fact | Status |
|---|---|
| LSPosed/DexBuilder is LGPL-3.0 and contains `dex_helper.cc`, `include/dex_helper.h`, `slicer/`; the 1.5 copy matches its API | Verified (GitHub page, LICENSE file, diff) |
| AOSP slicer files are Apache-2.0 | Verified (file headers) |
| Gradle wrapper is Apache-2.0 | Verified (header) |
| Xposed API 82 is Apache-2.0 | Verified (Maven POM at api.xposed.info) |
| libxposed api is Apache-2.0, current 102.0.0; service 102.0.1, Apache-2.0; entry files, callbacks, interceptor hooks, remote preferences exist | Verified (GitHub READMEs, javadoc, LSPosed wiki) |
| DexKit: Apache-2.0 except `Core/` = LGPL-3.0; latest 2.3.0; `create(apkPath)`, `create(ClassLoader, Boolean)`, `findMethod`, `batchFindMethodUsingStrings`, `getMethodData(descriptor)`; `MethodData.invokes/callers/descriptor/getMethodInstance` | Verified (README, Maven metadata, source files) |
| BiliRoaming is GPL-3.0 and its GitHub repo is archived and emptied | Verified (GitHub page, SourceForge listing) |
| 1.5's `wrapper.cc` derives from BiliRoaming's JNI wrapper | **From memory, unverified** |
| LSPosed "new XSharedPreferences"; usage details of libxposed remote preferences | From memory |
| AGP ≥ 8.5.1 aligns native libraries for 16 KB pages by default | From memory; check with `zipalign` |
| dexlib2 is BSD-3-Clause; PMD/CPD has a permissive licence; AndroidX/Material are Apache-2.0 | From memory |
| `PhoneNumberUtils.formatNumberToE164` available since API 21 | From memory |
