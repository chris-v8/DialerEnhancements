# SPEC.md: behavioural specification for DialerEnhancements (CallRecording 1.5 parity)

**What this is.** A description, in new words, of everything the CallRecording 1.5 module does, so that DialerEnhancements can be written from this document alone. It records behaviour, Google Phone facts and design intent. It does not reproduce the old code, comments, log messages or UI texts.

**Rule for whoever implements it.** Write from this spec and REWRITE_PLAN.md. Do not open the CallRecording sources while writing. The exceptions are files by Chris (the user), listed in PROVENANCE.md §5.

**Keywords.** *MUST* = needed for parity. *SHOULD* = what 1.5 does, but the rewrite plan suggests an improvement (see REWRITE_PLAN.md §11). *Observed* = what 1.5 actually does today, including quirks.

---

## 0. Terms

| Term | Meaning |
|---|---|
| **Google Phone** | The app `com.google.android.dialer`. |
| **Marker** | A text constant that a Google Phone method loads in its code (usually for a log line or trace name). Obfuscation renames classes and methods but leaves these strings intact, so they are used to find methods. The match is always **exact equality with the whole string constant** (not a substring, not a prefix). |
| **Shape** | The rest of a method's signature: number of parameters, parameter types, return type, whether it is static. |
| **Unique-match rule** | Find all candidates with the marker and shape. Exactly one: use it. Zero or more than one: skip the feature on this Google Phone version and leave Google's behaviour alone. |
| **First-match rule** | Take the first candidate the search returns (no ambiguity check). The four oldest eligibility features (F1–F4) use this in 1.5. The rewrite SHOULD switch them to the unique-match rule. |
| **Fail-safe** | Anything unexpected (method not found, odd argument, exception) means "do nothing and let Google Phone decide as it normally would". The module must never crash Google Phone. |
| **Critical phase** | Work done synchronously while LSPosed loads the module into Google Phone, before any of Google Phone's own code can run. |
| **Deferred phase** | Work done on one background thread that starts at the end of the critical phase. |
| **Completed Future** | Google Phone often returns its answers as a `java.util.concurrent.Future` (a "promise of an answer"). A *completed* Future already holds its value. Google's code has a static helper that wraps a value into such a Future; F7 and F9 find and reuse it. |
| **Producer** | Google Phone's naming for methods that compute one value of a dependency graph (their markers start with `produce…`). Producers get their inputs packed into **one** argument of declared type `Object` (at runtime a `List` of inputs) and return a Future. |
| **Bridge** | The communication channel between hooks running inside Google Phone and the module's own app (F12). |

## 0.1 Environment 1.5 was built and tested on

- Android 12 (SDK 31), Oplus/ColorOS phone, rooted (Magisk), LSPosed (the "Vector" fork), legacy Xposed API 82. Country/SIM: Poland. **No text-to-speech engine installed.**
- Google Phone **88.0.472595733** (pre-installed system app, so it holds privileged permissions such as `CAPTURE_AUDIO_OUTPUT`) and **247.0.1025958192** (installed as an update over it). Jie's static analysis used **244.0.1001930441**.
- Module: minSdk 28, target/compile SDK 37, Java 21.

---

## 1. Global behaviour

### 1.1 Module identity and scope

- LSPosed metadata: a human-readable description, minimum Xposed API 82, default scope = `com.google.android.dialer` only. Entry class listed in the legacy entry file.
- The entry point ignores every package except `com.google.android.dialer`.
- *Observed:* no check of the process name. If Google Phone runs more than one process, every one of them gets all hooks and runs the DEX search. The rewrite SHOULD install hooks only in the main process (or once per process, with the search result cache from REWRITE_PLAN §5.3).

### 1.2 Startup sequence (MUST keep this order and split)

**Critical phase** (synchronous, inside the load callback):

1. Diagnostics: log a "module entered Google Phone process" event (F8).
2. Install the call-state observer (F8). This hooks an Android framework class, so no DEX search is needed.
3. Open the DEX search engine on Google Phone's class loader.
4. F1 canRecordCall → true.
5. F2 geofence → true (both names).
6. F4 announcement locale → US English.
7. F3 isCallRecordingCountry → true.
8. F5 TTS: the three hooks (synthesize, init result, language check). **These run even if step 3 failed.**
9. Log the total duration of the critical phase.

Then start **one** background thread (named after the module) for the **deferred phase**:

10. F7 "record all calls" policy (only if the engine opened).
11. F8 recorder-start and disclosure timing hooks (only if the engine opened).
12. F9 Android 12 bubble gate (only if the engine opened).
13. F10 Selected-numbers multi-select (only if the engine opened).
14. Close the DEX search engine.
15. F6 version/permission check on activity resume (always).
16. Log "deferred phase complete".

**Why the split matters (Chris's fix, keep it).** Google Phone has a background WorkManager job, `recording_geofence_check_worker`, that Android runs about once a day, sometimes by cold-starting Google Phone just for it. The job asks "may I record?" (F1) and "inside the geofence?" (F2). If either says no, it turns off Google Phone's automatic-recording settings ("record unknown numbers", "record selected numbers") and posts a notification about an unsupported region. Before 1.5 all hooks were installed on a background thread, so on a cold start the job could ask before the hooks existed. That race made the settings switch themselves off at random. Installing F1–F5 before the load callback returns closes the race, because Google Phone's own code cannot run until the callback returns.

**Measured cost:** the critical phase took 54–105 ms per cold start of Google Phone, and about 600 ms on the first start after installing a new Google Phone version (the new, larger APK has to be opened). The rewrite SHOULD cache discovery results per Google Phone version so later starts cost a few milliseconds (REWRITE_PLAN §5.3).

**Test that proves the fix:** force-stop Google Phone, look up the geofence job's id in its WorkManager database (`no_backup/androidx.work.workdb`; it was id 20 on the test phone), force it to run with `cmd jobscheduler run -f com.google.android.dialer <id>`, confirm the module log shows the critical phase finishing before any recording code runs, and that both auto-record settings stay on.

### 1.3 Step isolation (MUST)

Every numbered step runs inside a guard. The guard:

- logs that the step started;
- runs it;
- on success logs the step name and how long it took, in milliseconds;
- on any `Throwable` logs the step name, duration, the error and its stack trace, then carries on with the next step.

A failure in one feature must never stop the others or reach Google Phone. Note: "step succeeded" only means "didn't throw". A feature that found nothing reports that separately (see each feature's *Logging*).

### 1.4 DEX search engine: what the features need

All of Google Phone's DEX files (multidex: `classes.dex`, `classes2.dex`, …) must be searchable. The 1.5 engine (native, see PROVENANCE §2.6) does this:

- It reads the DEX images the runtime already has in memory, via the class loader's internal path list.
- When the runtime holds a *compact* DEX (a format produced by on-device optimisation, which the engine can't parse), it falls back to opening the APK as a ZIP and unpacking `classes*.dex`.

Queries the features use (any replacement engine MUST support them):

| Query | Inputs | Output |
|---|---|---|
| **Q1 Methods using a string** | Exact marker string; optional filters: parameter count, return type, exact list of parameter types, "returns boolean with no parameters" | Methods whose bytecode loads that exact string constant |
| **Q2 Methods called by a method** | A method; optional filter: parameter count of the callee | Methods invoked from that method's bytecode |
| **Q3 Resolve** | A search result | A `java.lang.reflect.Method` in Google Phone's class loader (a result that can't be resolved is dropped) |

The engine is opened once in the critical phase, passed to the deferred thread and closed at the end of it. After that, no feature needs it.

### 1.5 Hook kinds used

- **Replace**: Google's method never runs; the hook returns a value (F1–F4).
- **Before**: runs before Google's method; can change arguments, or set a result and skip the original (F5 text, F5 init, F7, F9, F10).
- **After**: runs after Google's method; can inspect or change the result (F5 synthesize and language, F6, F8, F10 screen-open).
- **Call original**: call Google's real method from inside a hook, bypassing the hooks (F10 replays the result handler once per number).

### 1.6 Logging (observed in 1.5)

- Two outputs: Android **logcat** and the **LSPosed module log**. Most lifecycle and feature lines go to the LSPosed log; the four oldest eligibility features (F1–F4) and the F5 hooks log only to logcat.
- The release build **removes all debug/verbose logcat calls** (an R8 "no side effects" rule). In release builds, "found method X" and per-call lines of F1–F5 disappear, while "not found" errors remain.
- Tags: one base tag, plus a sub-tag per feature (init, record-all, timing, bubble, selected numbers).
- Privacy rules the rewrite MUST keep: never log phone numbers, contact names, audio data or file paths. Exceptions in 1.5: F4's debug line logs the country-code arguments, and F5's debug line logs the TTS arguments (including the disclosure text and file). Both are debug-only and stripped in release; the rewrite SHOULD drop them.
- The "diagnostic logging" setting only controls **one** extra log line (F7, when wrapping the Future fails). Timing events (F8) are always logged.

### 1.7 Behaviour when the bridge can't be reached

When the hooks can't reach the module's app (not installed, process can't start, call rejected), every read returns a safe default:

| Setting | Default when unreachable |
|---|---|
| Record all calls | **off** |
| Diagnostic logging | off |
| Multi-select in the Add button | **off** (the stock picker stays) |
| Pending queue | nothing |

Reports from the hooks to the app are dropped silently.

---

## 2. Features

Every feature is described with: **What the user sees**, **Target and discovery**, **What it changes**, **Fail-safe**, **Timing**, **Settings**, **Logging**, **Tested**, **Notes**.

### F1. "May this call be recorded?" → yes

- **What the user sees:** the record button exists and works in countries or on devices where Google switched recording off.
- **Target and discovery:** marker `canRecordCall`; shape: no parameters, returns `boolean`. 1.5 takes the **first** match.
- **What it changes:** replaces the method; it always returns `true` without running Google's code.
- **Fail-safe:** not found → error in logcat; nothing else changes.
- **Timing:** critical phase.
- **Logging:** debug: the method found; debug on every call: "returned true". Error if not found.
- **Tested:** v88, v247.
- **Notes:** in v247 the daily geofence job calls only this method, so F1 alone protects the auto-record settings there.

### F2. Geofence check → inside

- **What the user sees:** Google Phone believes the phone is inside the area where recording is allowed.
- **Target and discovery:** two possible markers, tried in turn: `withinCrosbyGeoFence` (older releases, including v88) and `withinCallRecordingGeoFence` (2xx releases, including v247). Shape: no parameters, returns `boolean`. First match per marker. **Every marker that matches gets hooked** (both, if a version had both).
- **What it changes:** replaced to always return `true`.
- **Fail-safe:** neither found → one warning in logcat; nothing else changes.
- **Timing:** critical phase.
- **Logging:** debug per marker found; debug per call; one warning if none found.
- **Tested:** v88 (old name), v247 (new name).
- **Notes:** "Crosby" is Google's internal code name for automatic call recording.

### F3. "Is this a call-recording country?" → yes

- **Target and discovery:** marker `isCallRecordingCountry`; no parameters, returns `boolean`; first match.
- **What it changes:** replaced to always return `true`.
- **Fail-safe, timing, logging:** as F1.
- **Tested:** v88, v247.

### F4. Recording announcement in built-in English

- **What the user sees:** where it works, Google Phone plays its **built-in English** recorded announcement ("this call is being recorded") instead of trying to generate it with text-to-speech.
- **Google Phone background:** before recording starts, Google Phone plays a disclosure. For countries it supports it has pre-recorded audio files; for others it maps the country to a locale and synthesises the sentence with TTS (see F5). Poland is not supported for built-in audio.
- **Target and discovery:** marker `getSupportedLocaleFromCountryCode`; shape: returns `java.util.Locale`, exactly two parameters typed `java.util.Map` then `java.lang.String`. First match.
- **What it changes:** replaced to always return the US-English locale (`en_US`).
- **Fail-safe:** not found → error in logcat. Google Phone then uses its normal path, which leads to TTS and therefore to F5.
- **Timing:** critical phase.
- **Logging:** debug line with the found method; debug line per call with the arguments and the forced answer. Error if not found.
- **Tested:** matches on v247. **Does not match on v88**: there the method returns an `Optional<Locale>` and takes three parameters. Harmless: v88 goes through TTS, and F5 keeps recording working.
- **Notes:** the rewrite MAY add the v88 shape as a second accepted shape. The user decides (it changes v88 from a silent to a spoken English announcement).

### F5. Text-to-speech fallback for phones without a working TTS engine

- **What the user sees:** recording starts even when no TTS engine is installed or it fails. Without F5, Google Phone showed "Couldn't start recording due to a system problem" and stayed idle, because it generates the disclosure audio with TTS first and waits for it.
- **Google Phone background:** Google Phone calls the Android API `TextToSpeech.synthesizeToFile(CharSequence text, Bundle params, File file, String utteranceId)` to write the disclosure to a file. It then waits for the **utterance-progress listener's `onDone(utteranceId)` callback** before it starts the recorder. A failed result alone isn't enough; the callback must arrive.
- **Targets:** Android framework methods on `android.speech.tts.TextToSpeech`, looked up by reflection (no DEX search):
  1. **synthesizeToFile(CharSequence, Bundle, File, String)**
     - *Before:* **always** replaces the text with an empty string. *Observed:* this happens even when TTS works, so any TTS-generated disclosure is silent. (When F4 works, Google Phone uses its built-in audio file instead and the announcement still plays.)
     - *After:* if the result is not `SUCCESS`:
       a. Write a **silent WAV file** to the requested `File`: a standard 44-byte RIFF/WAVE header for 16-bit PCM, mono, 16,000 Hz (byte rate 32,000, block align 2) with an **empty data chunk** (RIFF size 36, data size 0). If the write works, change the result to `SUCCESS`. If it fails, log an error and keep the failure result.
       b. Separately, and even if (a) failed: read `TextToSpeech`'s private listener field (`mUtteranceProgressListener`) by reflection. If a listener is set, call its `onDone` with the utterance id. If reflection fails, log an error.
  2. **dispatchOnInit(int status)** (private framework method that delivers the engine-initialisation result to the app's init listener). *Before:* if the status isn't `SUCCESS`, change it to `SUCCESS` and log a warning.
  3. **isLanguageAvailable(Locale)**. *After:* if the result is below `LANG_AVAILABLE` (it's one of the "missing data / not supported" codes), change it to `LANG_COUNTRY_VAR_AVAILABLE` and log a warning.
- **Fail-safe:** a framework method that can't be found → error with stack trace; the other TTS hooks still install.
- **Timing:** critical phase, after F1–F4, even if the DEX engine failed to open.
- **Logging:** debug lines with arguments (stripped in release); warnings when a failure is overridden; errors when a reflective step fails.
- **Tested:** v88 and v247 with no TTS engine installed. On v88, F4 doesn't match, so this path is what makes recording work.
- **Notes for the rewrite:**
  - The WAV header SHOULD be built in code from the format values above, not stored as a byte list.
  - The three hooks are **process-wide**. They affect every TTS use inside Google Phone, not only the recording disclosure. Other Google Phone features that synthesise speech could be silenced or see fake success. Not verified; worth a test on a phone with a working TTS engine.
  - Blanking the text is a **legal/consent-sensitive** behaviour. The user should decide whether it stays unconditional, becomes "only when TTS has failed", or becomes a clearly labelled setting (REWRITE_PLAN §2 open decisions).

### F6. Version and permission check

- **What the user sees:** a long toast inside Google Phone if the setup can't work:
  - Google Phone doesn't hold `android.permission.CAPTURE_AUDIO_OUTPUT` (needed for its recorder; only privileged system installs get it). The toast says the permission is missing, and the check stops there.
  - Otherwise, if Google Phone's own `versionName` **ends with `downloadable`** (a build flavour that can't record), the toast says the version is unsupported and the full version is needed.
- **Target:** the framework method `android.app.Activity.onResume()`, hooked *after*. It fires for every activity of Google Phone, because subclasses call the base method.
- **What it changes:** nothing in Google Phone; it only shows toasts and logs warnings.
- **Fail-safe:** package lookup failure → warning; nothing shown.
- **Timing:** installed at the very end of the deferred phase.
- **Logging:** a warning per detected problem (with the version name in the second case).
- **Notes:** *Observed:* the check runs (and the toast repeats) on **every** activity resume, and queries the package manager each time. The rewrite SHOULD check once per process start and also show the result on the module's status page. The 1.5 toast text is upstream's own wording; write new texts.

### F7. "Record all calls" policy

- **What the user sees:** with the module setting **Automatically record all calls** on, every call (incoming, outgoing, contacts and unknown numbers) is recorded automatically, regardless of Google Phone's own "unknown numbers / selected numbers" rules. With it off, Google's rules apply unchanged.
- **Google Phone background:** Google Phone decides per call whether to auto-record in a producer that returns a Future of `Boolean`. Names by version:
  - `produceShouldStartAutomaticRecordingForContact`: 2xx releases (v244, v247).
  - `produceShouldStartAutomaticRecording`: v88 (the newer recording service).
  - `produceIsCrosbyEnabledForCall`: v88 (the older in-call UI; the one v88 used on the test phone).
- **Discovery (per marker, all three tried):**
  1. Q1 with the exact marker, one parameter. Keep methods whose only parameter type is exactly `java.lang.Object` and whose return type implements `java.util.concurrent.Future`. Unique-match rule.
  2. Find the **completed-Future factory** for that producer (F7.1). If none qualifies, skip this marker.
  3. Hook the producer.
  - **Every** marker that yields a unique producer plus factory gets hooked: two on v88, one on v247.
- **What it changes (hook *before* the producer):**
  1. Ask the bridge whether record-all is enabled. *Observed:* 1.5 does this on **every** call of the producer, which costs one cross-process call each time. If disabled or unreachable → do nothing.
  2. Call the factory with `Boolean.TRUE`. If the result is an instance of the producer's declared return type, use it as the producer's result and skip Google's code.
  3. Tell F8 that the policy was accepted (timestamp).
  4. The **first** time per process: report a "hit" to the app (it stores the time) and log it.
  5. Any exception → leave Google's decision intact. The error is logged only if "diagnostic logging" is on (which costs another bridge read).
- **Status reported to the app once per process**, after all markers were tried:
  - at least one producer hooked → "installed" (the log line includes how many);
  - none hooked, but at least one producer lacked a verified factory → "factory missing";
  - otherwise → "not found".
  The app also accepts an "error" status, but since 1.5 nothing sends it.
- **Fail-safe:** unique-match rule at both levels; factory verified by a test call; runtime type check on every use.
- **Timing:** deferred phase. *Observed consequence:* a call that arrives in the first moments after a cold start may be decided before the hook exists. Rare.
- **Settings:** record-all (**default on** in 1.5), diagnostic logging (default off).
- **Logging:** one line per hooked producer (the method); a line per marker whose factory wasn't found; one summary line; "first override" once per process; caught errors only when diagnostics are on.
- **Tested:** v88 (two producers hooked) and v247 (one).
- **Limits:** hooking the decision doesn't guarantee a recording: earlier flags, permissions or the recorder itself may still block it. Check the saved audio, not just the log.

#### F7.1 Completed-Future factory discovery (shared by F7 and F9)

Given a producer method P:

1. Q2: the methods P calls that have exactly one parameter.
2. Keep those that are `static`, whose single parameter type is exactly `java.lang.Object`, and whose return type can be assigned to P's return type.
3. Unique-match rule.
4. Verify by calling it once with `Boolean.TRUE` (after making it accessible). The result must be an instance of P's return type and a `Future`; it must already be done; and its value must equal `Boolean.TRUE`. Otherwise reject (log the reason).

### F8. Recording-start timing diagnostics (observation only)

- **Purpose:** measure when, after a call connects, Google Phone gets round to starting its recorder. It was built to investigate recordings that miss the first seconds. It **never changes** Google Phone's behaviour.
- **Google Phone background (v244, static analysis):** order of events: call becomes active → auto-record policy accepted → Google Phone requests the start disclosure (a callback related to `playStartDisclosure`) → only after the disclosure completes, the native recorder start is requested.
- **Parts:**
  1. **Process entry event:** logged first thing in the critical phase.
  2. **Call-state observer:** hook *after* every overload of the framework method `android.telecom.Call.internalUpdate`. When a call becomes `STATE_ACTIVE` and it's not the call already tracked, record "connected at" (elapsed realtime since boot), reset the other timestamps and log *connected*. When the tracked call becomes `STATE_DISCONNECTING` or `STATE_DISCONNECTED`, log *disconnected* and stop tracking it. If no overload exists → log *unavailable*; on error → log *failed* (with the error class name).
  3. **Policy accepted:** F7 reports each time it forces a "yes" (timestamp + event).
  4. **Recorder start:** Q1 with marker `TidepodsCallAudioImpl_createDownlinkAndUplinkTee`, one parameter. Keep methods whose parameter type is `long` and whose return type implements `Future`. Unique-match rule. Hook *before* (log *recorder start requested*, store the time) and *after* (log *recorder start returned its Future*; audio capture may still be initialising).
  5. **Disclosure request:** Q1 with marker `playStartDisclosure`, one parameter, gives the "caller" methods. For each caller, Q2 for callees with zero parameters. Keep those declared in the **same class** as the recorder-start method and returning a `Future`. Unique-match rule across all callers. Hook *before*: log *disclosure requested* and store the time. Missing → log *unavailable*; the recorder hook is still installed.
- **Event line format (content, not wording):** event name; current elapsed realtime in ms; ms since connected; ms since policy accepted; ms since disclosure (each "n/a" when unknown). No numbers, contacts, audio or paths.
- **Sinks:** **always** both logcat (info) and the LSPosed log, regardless of the diagnostics setting. Gating these on the bridge produced empty logs, because the bridge can be unavailable during early startup.
- **Timing:** parts 1–2 in the critical phase; parts 4–5 in the deferred phase.
- **Observed quirks:** the "recorder start requested" timestamp is stored but never printed. The step name mentions an "early" recording experiment that was deliberately left out. Only one call is tracked at a time (call waiting or conferences aren't modelled).
- **Rewrite advice:** an optional diagnostics feature, off by default, not needed for 1.0 (REWRITE_PLAN §13).

### F9. Android 12+ "return to call" bubble

- **What the user sees:** after leaving the in-call screen during a call, Google Phone's floating bubble appears again on Android 12 and later (speaker, mute, hang up, back to call), as it did in v88.
- **Google Phone background:** the bubble code is still in v247. One producer, `produceIsBubbleFeatureEnabledForCall`, decides whether to show it. Its inputs are [`Context`, `Boolean` *inhibit*, a per-call bubble-state object]. Its logic in v247, as facts:
  1. If the SDK is 31 or higher, refuse, and log the exact sentence `Not showing bubble in Android S+.`
  2. else if the state's "dismissed this call" flag (an `AtomicBoolean`) is set, refuse;
  3. else if the app can't draw overlays (`Settings.canDrawOverlays`), refuse;
  4. else show unless *inhibit*.
  The result goes through the completed-Future helper. In v88, step 1 depends on a server flag (`enable_android_s_notifications`) that was off, so v88 shows the bubble anyway. In v247 the check is unconditional.
- **Discovery:**
  1. Only on SDK 31+. Below that: log "not needed" and stop.
  2. Q1 with the exact sentence above (one parameter) gives set G.
  3. Q1 with marker `produceIsBubbleFeatureEnabledForCall` (one parameter). Keep methods that are **also in G**, whose only parameter type is `Object` and whose return type implements `Future`. Unique-match rule. (v88 logs a different sentence, so it never matches and its own bubble behaviour stays untouched.)
  4. F7.1 factory discovery for that producer. None → log and stop.
- **What it changes (hook *before*):** re-run Google's decision **without step 1**:
  - The argument must be a `List` with at least 3 elements: element 0 a `Context`, element 1 a `Boolean`, element 2 non-null.
  - In element 2's class, find **exactly one** non-static field of type `AtomicBoolean` (the dismissed flag). None or several → unfamiliar.
  - dismissed → false; else no overlay permission → false; else → not *inhibit*.
  - Wrap the answer with the factory. If the result is an instance of the producer's return type, use it and skip the original.
  - Unfamiliar inputs or any exception → let the original run.
- **Logging:** hooked (with the method); not present on this version; no factory; the first bypass per process (with the answer); errors (the original then decides).
- **Timing:** deferred phase.
- **Tested:** v247 on Android 12 (bubble shows); v88 (not matched, its own bubble still works).
- **Notes:** a second, flag-based switch can hide the bubble on other versions: server flags `G__enable_return_to_call_bubble` and `G__enable_return_to_call_bubble_v2`. Symptom in logcat: a message that the bubble feature isn't present. Not handled by the module.

### F10. Selected numbers: multi-select add and remove

**What the user sees.** Google Phone's *Settings → Call recording → Selected numbers* lets you add one contact at a time (and remove them one by one). With the module:

- **Route A, from Google Phone:** tapping **Add** opens the module's multi-select list of phone numbers instead of the system one-number picker. Numbers already on Google's list start ticked. Untick to remove, tick to add, then **Save (+added / −removed)**. Google Phone applies all changes at once.
- **Route B, from the module app:** "Select numbers to always record" opens the same list. Changes are **queued**. The app then opens Google Phone's main screen and tells the user to go to *Settings → Call recording → Selected numbers*. When that screen opens, the queued changes are applied.

Google's *Selected numbers* switch still has to be on for those numbers to be recorded; the module only edits the list.

**Safety principle (MUST).** The module never writes Google Phone's settings file. Every add and remove goes through Google Phone's **own** code paths, exactly as if the user had done them by hand. Google Phone stores the list in a DataStore file and applies each change as a small transform ("append this number unless present", "remove these numbers") one after another, so several changes in a row can't overwrite each other.

**Google Phone background (v88; same markers in v247):**

- The Add button's click handler builds an `ACTION_PICK` intent with MIME type `vnd.android.cursor.dir/phone_v2` (the system "pick one phone number" picker) and starts it **for result with request code 9**.
- The fragment's result handler logs the marker `uri received from system contact picker`, **ignores any request code other than 9**, reads the number (`data1`) from the returned contact-data URI, normalises it (for example `+48…`) and appends it to the DataStore.
- Each row's remove action sends an **event object carrying the stored number string** to a listener. The screen registered that listener when its view was created, through a static "register(fragment, event class, listener)" helper. The listener asks the DataStore to remove the number.
- The list lives in Google Phone's private file `files/CallRecordingSettingsData.pb` (protobuf; same name on v88 and v247), a few hundred bytes, together with the recording switches.
- Google Phone's settings activity isn't exported. Its exported `…settings.DialerSettingsActivityCompat` alias has no class behind it in v88, so launching it crashes Google Phone. Only the main launcher screen can be opened from outside.
- A daily Google job (`recording_stale_contact_number_cleanup_worker`) may remove numbers whose contact was deleted.

#### F10.1 Discovery (deferred phase)

1. **Result handler**: Q1 with marker `uri received from system contact picker`, three parameters. Keep methods returning `void` whose parameter types are exactly (`int`, `int`, `android.content.Intent`). Unique-match rule. Missing → log "multi-select disabled" and stop the whole feature.
2. **Fragment class** = the declaring class of the result handler.
3. **View-created method**: among the fragment class's *declared* methods, exactly one returning `void` with parameters exactly (`android.view.View`, `android.os.Bundle`). Missing → removing and the queue are unavailable (log); adding via route A still works.
4. **Event registration helper**: Q2 on the view-created method, three-parameter callees. Keep those that are `static`, whose first parameter type accepts the fragment class, whose second is `java.lang.Class` and whose third is an interface. Unique-match rule. Missing → removing unavailable (log).
5. **Listener method**: on that interface, exactly one abstract method, with exactly one parameter. If any abstract method has a different parameter count, or there are several → removing unavailable (log).
6. **Add click handler**: Q1 with marker `invoke the system contact picker`, one parameter. Keep methods returning `void` with exactly one `android.view.View` parameter. Unique-match rule. Missing → the Add button keeps the system picker (log); the queue still works.

#### F10.2 Add-button redirect (route A)

- Hook the click handler: *before* sets a per-thread "inside an Add click" flag; *after* clears it.
- Hook the framework method `Activity.startActivityForResult(Intent, int, Bundle)` *before*. Redirect only if **all** of these hold:
  1. the flag is set on this thread;
  2. the intent is non-null and has no explicit component;
  3. action is `ACTION_PICK` and type is `vnd.android.cursor.dir/phone_v2`;
  4. the bridge says multi-select is enabled (a bridge read during the click, on the main thread);
  5. the module's picker activity resolves through the package manager.
- Redirecting means: copy the intent, point it at the module's picker activity, read the current list (F10.7) and, if readable, attach it as the "existing numbers" extra and send it to the app as a snapshot (F10.6). Replace the intent argument; the request code and options stay unchanged.
- Fail-safe: any condition false → the normal picker opens.
- Log once at install time that the Add button will open the multi-select picker.

#### F10.3 Picker result and replay

**Data contract between picker and hook** (three intent extras; names are the implementer's choice, namespaced by the new package):

| Extra | Direction | Content |
|---|---|---|
| Add list | picker → Google Phone | `ArrayList<Uri>`: contact **data-row URIs** (`content://com.android.contacts/data/<id>`), the same kind of URI the system phone picker returns |
| Remove list | picker → Google Phone | `ArrayList<String>`: numbers **exactly as Google Phone stored them** |
| Existing list | Google Phone → picker | `ArrayList<String>`: Google's current list, as stored |

The picker's result intent also sets its **data URI to the first added URI**. Even if the hook is missing, Google Phone then adds at least that one number on its own.

Hook *before* the result handler:

1. Read the add and remove lists from the result intent (any unparcelling error → act as if absent).
2. If there's nothing to remove and fewer than two URIs to add → **do nothing** (Google's stock path handles the zero- or one-number case, identical to the system picker).
3. Otherwise run **apply** with the handler's own request and result codes, then skip the original call.

**Apply(fragment, requestCode, resultCode, addUris, removeNumbers, sourceLabel):**

1. Removals first: if a remover is known for this fragment instance (F10.4), for each number build the event from the number and call the listener method. Count successes; log each failure (without the number). No remover known → log how many removals were skipped.
2. Additions: for each URI call Google's **original** result handler (bypassing the hook) with the same request and result codes and a fresh intent whose data is that single URI. Count successes; log each failure.
3. Log one summary: the source (picker result or queued from the app) and "added X of Y, removed X of Y".
4. About 3 seconds later, on the main thread: refresh the snapshot (F10.6). Google Phone saves asynchronously, so an immediate read would be stale.

#### F10.4 Remover capture

Hook *before* the event registration helper (F10.1 step 4). Only when argument 1's runtime class **is exactly** the fragment class, argument 2 is a `Class` and argument 3 is non-null:

- Look up a constructor of that event class taking a single `String`. Make it accessible. If none exists, ignore the call: this isn't the remove event (for example a "turn on" event that carries no number).
- Check that the listener method's parameter type accepts the event class.
- Remember (listener object, listener method, event constructor) for that fragment instance, in a weakly-keyed map so closed screens can be garbage-collected.
- *Observed:* if several registrations qualify, the last one wins. The rewrite SHOULD require exactly one per fragment and otherwise disable removal for that screen.
- Log once at install time that the remove event is hooked (with the helper method).

#### F10.5 Pending queue (route B)

- **App side:** when the picker wasn't started by Google Phone, Save merges the new data-row URIs into a stored *pending additions* set and the numbers to remove into a *pending removals* set (both in the app's private preferences). It shows a toast with both counts and where to go in Google Phone, launches Google Phone's launcher intent in a new task (on failure: a different toast) and closes.
- **Settings screen:** shows "waiting: N to add, M to remove" with the same instructions while either set is non-empty.
- **Google Phone side:** hook *after* the fragment's view-created method. On the view's message queue (so it runs after the fragment has finished starting):
  1. send a snapshot (F10.6);
  2. ask the bridge to **take** the queue: it returns both sets and clears them;
  3. if anything came back, run **apply** with request code **9**, result `RESULT_OK` and source "queued in the app".
- *Observed risks:* the queue is cleared when taken, before Google Phone has applied it, so a crash at that moment loses it. Request code 9 is hard-coded; a version using another code would silently ignore queued additions (route A is unaffected, because it reuses the code Google passed in). See REWRITE_PLAN §11 for fixes.

#### F10.6 Snapshot of Google's list (for the app's checkmarks)

- The app can't read Google Phone's private files, so the hook sends it a copy of the list: the numbers as stored. The app stores them as a set in its private preferences and replaces the previous copy each time.
- Sent: when the Selected-numbers screen opens, during an Add-button redirect, and about 3 seconds after each apply.
- The route-B picker uses this copy. If no copy exists yet, it shows a note that existing numbers appear once Google's Selected-numbers screen has been opened. The copy can be **stale**, for example after Google's cleanup job; unticking a number that's already gone does nothing, and re-adding a duplicate is ignored by Google.

#### F10.7 Reading Google's list from the protobuf file

- Read `<Google Phone files dir>/CallRecordingSettingsData.pb` **read-only**, from inside Google Phone's process. Missing file → empty list. Read error → "unknown" (no existing-numbers extra, no snapshot).
- Parse generically, without knowing Google's message schema (protobuf wire format):
  - each field starts with a varint key: field number = key >> 3 (field number 0 means malformed), wire type = key & 7;
  - wire type 0 (varint): skip it; 1: skip 8 bytes; 5: skip 4 bytes; 2 (length-delimited): see below; any other wire type (including the old start/end-group types) → malformed;
  - length-delimited value: if its bytes **look like a phone number** (optional leading `+`, then only ASCII digits, 6 to 18 of them), collect it as an ASCII string. Otherwise, if the nesting depth is below **4** and the value isn't empty, try to parse it as a nested message, and keep what it found **only if the whole nested range parsed cleanly**;
  - a varint longer than 10 bytes, or a length past the end, → malformed.
- At the top level, numbers found before a malformed point are kept. Order of first appearance is preserved; duplicates are dropped.
- Verified on the real v88 file: 21 of 21 numbers, and later 25 of 25.

#### F10.8 The picker activity (module app)

- **Who may use it:** exported, so Google Phone can open it. If started *for result* by any package other than Google Phone or the module itself → return "cancelled" and close. Started for result by Google Phone → route A (result goes back to Google Phone). Not started for result → route B (queue).
- **Permission:** needs `READ_CONTACTS` (requested at runtime). Denied → a message explaining it's needed to list numbers.
- **Loading** (background thread): query every phone data row (data-row id, primary display name, number, type and custom label, normalised E.164 number), sorted by display name with locale-aware collation. Skip empty numbers. A missing name falls back to the number. Each row shows name, number and the type label. A query error shows the permission message.
- **Existing numbers:** route A from the existing-numbers extra; route B from the snapshot. Unknown → a note (see F10.6).
- **Matching (F10.8a):** strip a number to its digits. Its *match key* is the last 9 digits, or all digits if there are 6 to 9; fewer than 6 → no key. Build a map from key to the number as stored (if two stored numbers share a key, the later one wins). A contact row is **already on the list** if the key of its number, or else of its normalised number, is in the map. Two numbers differing only in country code count as the same (known limitation).
- **List behaviour:** search box filters by case-insensitive name substring, or by digit substring when the query has digits. Rows already on the list start ticked and carry a second line "on the list (untick to remove)". Unticked, that line changes to "will be removed", and **every row with the same key follows the same state**. Other rows tick or untick by data-row id. No rows → "no numbers found".
- **Save button** label shows "+added / −removed" counts. Enabled only when there is at least one change.
- **Save:** builds the add list as data-row URIs and the remove list by mapping each unticked key back to the number **as stored**. Nothing to do → stays open. Route A → result `RESULT_OK` with the three-extra contract (F10.3), close. Route B → queue (F10.5).

**Logging (whole feature):** found / not found for each discovery piece; the summary per apply; individual failures; "can't read the current list" with the error. Never the numbers themselves.

**Tested (1.5):** on v88 everything except "untick to remove from the module app" was tested. On v247 the markers were found (result handler and click handler), but the feature wasn't exercised.

### F11. Settings app (module UI)

- One launcher activity, title = app name, using the device-default platform theme with programmatic layout (no XML layouts, no Material library). Languages: English and Simplified Chinese.
- Content, top to bottom:
  1. Title and a one-line subtitle (it's a Google Phone LSPosed module reusing the phone app's own recorder).
  2. Section **Automatic recording**:
     - Switch **Automatically record all calls** (default **on**) with a description (all calls, contacts and unknown numbers, no list needed).
     - Switch **Diagnostic logging** (default off) with a description (logs detection and errors, no phone numbers).
  3. Section **Selected numbers**:
     - A tappable row that opens the picker (route B), with a description, plus a pending-count line hidden when nothing is queued.
     - Switch **Multi-select in Phone's Add button** (default **on**), noting that Google Phone must be restarted after changing it.
  4. Section **Hook status**: one of *not checked yet (restart Google Phone, then refresh)*, *policy hooked (not proof of a recording)*, *no unique policy found*, *factory not verified*, *installation failed*. Below it, when known, *last detection* and *last recording decision* as `yyyy-MM-dd HH:mm`. A **Refresh** button. Status and pending counts also refresh when the screen resumes.
  5. A notice: a hook hit isn't proof that a call was recorded; Google Phone still needs its own audio support and permissions; inform the other party and follow local recording laws.
- The labels above name each control's function; they are not texts to copy. Write new wording (only Chris's Selected-numbers strings may be reused).
- Each switch writes its value immediately. Tapping the row toggles the switch.
- Changes take effect in Google Phone only after its process restarts, except where a hook reads the setting per call (record-all) or per click (multi-select).

### F12. Settings storage and the bridge (app ↔ Google Phone)

**Storage (app side).** One private preferences file with: record-all (bool), diagnostics (bool), multi-select (bool), last status (string), last detection time (ms), last hit time (ms), pending additions (string set of URIs), pending removals (string set of numbers), snapshot (string set of numbers). No call audio or call history is stored.

**Provider (app side).** An exported `ContentProvider` whose authority is the app's package name plus `.settings`. Only its `call(method, arg, extras)` entry is used; the query/insert/update/delete operations throw "unsupported" and the type lookup returns nothing.

- **Access control on every call:** allowed if the calling UID is the app's own UID, or a UID whose packages include `com.google.android.dialer`. Anyone else → `SecurityException`.
- **Methods:**

| Method | Called by | Effect / reply |
|---|---|---|
| read | hooks | Reply bundle with record-all (default **true**), diagnostics (default false), multi-select (default true) |
| take queue | hooks (F10.5) | Reply bundle with both pending lists; then clears both (asynchronous write) |
| store snapshot | hooks (F10.6) | Extras carry the number list; stored as a set; no list → no-op |
| report | hooks (F7) | `arg` is a status code from a fixed whitelist (installed, not found, factory missing, error, hit). *hit* → store "last hit" time. Others → store status and "last detection" time. Unknown codes are ignored. |
| anything else | — | `IllegalArgumentException` |

**Bridge (Google Phone side).**

- Finds the current `Application` by reflection: first Xposed's app-helper class if present at runtime (the API 82 artifact doesn't expose it at compile time), then the framework's activity-thread "current application". Neither → treat as unreachable.
- Wraps each provider method; **every** exception is swallowed and turned into the defaults of §1.7.
- *Observed costs:* a full provider round-trip on every record-all decision, on every Add-button start, and on the main thread when the Selected-numbers screen opens. The first call may also have to start the app's process.
- *Observed open question:* on Android 11+, a package can only see another app's provider if it is "visible" to it. It worked on the test phone (Google Phone may hold the query-all-packages permission, or the app's launch of Google Phone made the app visible). Not analysed further; the rewrite should test it.

### F13. Build and CI (as in 1.5)

- **Toolchain:** Android Gradle Plugin 9.1.1 with the AGP "settings" plugin used to set minSdk 28, compile and target SDK 37, build-tools 37.0.0 and NDK 29.0.14206865 centrally; Gradle 9.4.1; Java 21; built-in Kotlin support turned off.
- **Native part:** ndk-build for arm64-v8a, armeabi-v7a, x86, x86_64, with parallel jobs = CPU count; C++20, static libc++, LTO, hidden symbols, stripped in release.
- **Release build:** R8 minify and resource shrinking; debug/verbose logcat calls removed by R8; the entry class and the native bridge class kept; classes repackaged; VCS info left out of the APK; **all Java resources excluded from the APK**; native libraries stored uncompressed; **signed with the debug key**.
- **Custom task 1 (after resource optimisation, release only):** run `aapt2 optimize` on the processed resources (collapse resource names, sparse encoding) at a hard-coded intermediate path, and replace the original file.
- **Custom task 2 (after packaging the release APK):** reopen the APK with AGP's internal ZIP library; align `.so` files to 16 KB pages; drop timestamps; sort entries; **re-sign with the debug key** (v2 only, v1 off); delete AGP's app-metadata entry.
- **Dependencies:** compile-only AndroidX annotations and the Xposed API 82 (from `api.xposed.info`). Nothing is bundled.
- **CI workflow "Build":** on every push and pull request; matrix Ubuntu + Windows (macOS removed in 1.5); JDK 21 Temurin; Gradle setup that **publishes build scans and auto-accepts Gradle's terms**; appends caching, parallelism, JVM and verbose-native-output settings and "no dependency info in APK" to `gradle.properties`; runs the full assemble; uploads all outputs per OS.
- **CI workflow "Debug APK":** on pushes to `main` that touch build-relevant paths, on pull requests and manually; Ubuntu; checks the runner's preinstalled Android SDK (the community setup-android action was failing); builds the debug APK and uploads it (kept 14 days).

---

## Appendix A. Marker and shape table

| Feature | Marker (exact string) | Shape | Rule (1.5) | v88 | v247 |
|---|---|---|---|---|---|
| F1 | `canRecordCall` | () → boolean | first | yes | yes |
| F2 | `withinCrosbyGeoFence` | () → boolean | first | yes | — |
| F2 | `withinCallRecordingGeoFence` | () → boolean | first | — | yes |
| F3 | `isCallRecordingCountry` | () → boolean | first | yes | yes |
| F4 | `getSupportedLocaleFromCountryCode` | (Map, String) → Locale | first | shape differs (3 params → Optional) | yes |
| F7 | `produceShouldStartAutomaticRecordingForContact` | (Object) → Future subtype | unique | — | yes |
| F7 | `produceShouldStartAutomaticRecording` | (Object) → Future subtype | unique | yes | not hooked (absent or ambiguous) |
| F7 | `produceIsCrosbyEnabledForCall` | (Object) → Future subtype | unique | yes | not hooked (absent or ambiguous) |
| F7.1 | (callee of producer) | static (Object) → assignable to producer's return type; verified with TRUE | unique | yes | yes |
| F8 | `TidepodsCallAudioImpl_createDownlinkAndUplinkTee` | (long) → Future | unique | ? | ? (found in v244) |
| F8 | `playStartDisclosure` | callers with 1 param → their 0-param callees in the recorder's class → Future | unique | ? | ? (found in v244) |
| F9 | `produceIsBubbleFeatureEnabledForCall` **and** `Not showing bubble in Android S+.` in the same method | (Object) → Future | unique | not matched (by design) | yes |
| F10 | `uri received from system contact picker` | (int, int, Intent) → void | unique | yes | yes |
| F10 | `invoke the system contact picker` | (View) → void | unique | yes | yes |

"?" = not recorded in the 1.5 notes.

## Appendix B. Obfuscated names seen (for testing and log reading only; never put these in code)

- **v88.0.472595733:** auto-record producers `dew.b` (`produceShouldStartAutomaticRecording`) and `kup.b` (`produceIsCrosbyEnabledForCall`), factory `uds.k`, Future type `sby`; Selected numbers: result handler `dic.Z`, click handler `cym.onClick`, registration helper `tbo.r`, remove event `dii(String)`, listener `dhf`, DataStore remove call `eej.x(Set)`; related classes `dbt`, `csf`.
- **v244.0.1001930441:** producer `kyy.b`, factory `aiql.z`, Future type `ajcq`.
- **v247.0.1025958192:** producer `kzj.b`; bubble producer `pix.b` (Future type `ajii`); Selected numbers result handler `lbb.ag`, click handler `jmz.onClick`.

## Appendix C. Other Google Phone facts used

- Background jobs: `recording_geofence_check_worker` (about daily; can switch auto-record off, see §1.2), `recording_stale_contact_number_cleanup_worker` (removes Selected numbers whose contact is gone).
- Settings screen: `multibindingsettings.impl.DialerSettingsActivity` (not exported); `settings.DialerSettingsActivityCompat` (exported alias, no class in v88).
- Built-in announcement audio isn't available for Poland (logcat shows Google Phone saying the country isn't supported for built-in audio).
- v247 writes far less to logcat than v88; the LSPosed log is the better source there.
- Downgrading Google Phone (v247 → v88) once emptied the Selected-numbers list; the other settings survived.
- After installing a new module build, Google Phone must be force-stopped. A running process keeps the old hooks.

## Appendix D. Acceptance checklist for parity

1. The module log shows the critical phase finishing before the deferred phase starts, and before any Google recording code runs (§1.2 test).
2. Manual recording works on v88 and v247 with no TTS engine installed; a file is saved containing both sides.
3. Google's "record unknown numbers" auto-records an unknown caller.
4. With record-all on, a saved contact is recorded; with it off, only Google's rules apply.
5. Google's auto-record settings are still on after two days, and after a forced geofence job run.
6. Android 12+ on v247: the bubble appears after leaving the call screen, disappears after being dismissed, and doesn't appear without overlay permission.
7. Selected numbers, route A: Add opens the multi-select list with existing numbers ticked; ticking 3 new numbers and unticking 2 existing ones updates Google's list accordingly (log: added 3 of 3, removed 2 of 2).
8. Route A with exactly one new number and nothing unticked behaves exactly like the stock picker.
9. Route B: queued changes are applied when Google's Selected-numbers screen opens; the app's pending line disappears; the app's checkmarks match afterwards.
10. Turning off "multi-select in Add button" (and restarting Google Phone) brings back the system picker.
11. Removing the module app (or its provider failing) leaves Google Phone fully working, with record-all off and the stock picker.
12. With the timing diagnostics on, one call produces connected → policy → disclosure → recorder-start lines.
