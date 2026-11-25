# Provenance

DialerEnhancements is an independent implementation. It was written from the behavioural specification in `docs/design/SPEC.md` and the plan in `docs/design/REWRITE_PLAN.md`, without opening the source code of the CallRecording module by vvb2060 and Jie. It contains none of their code, comments, strings or build scripts.

Google Phone's text markers (for example `canRecordCall`) and Android's API names are used as facts to find and hook methods.

Files carried over from Chris's own work in CallRecording are marked "Carried over" below, with the CallRecording commit they came from. Third-party code only enters through Gradle dependencies (see `THIRD_PARTY_NOTICES.md`).

| File (under `app/src/main/kotlin/io/github/chrisv8/dialerenhancements/` unless a path is given) | Origin | SPEC section | Added |
|---|---|---|---|
| `shared/Contract.kt` | Written from the plan | REWRITE_PLAN §5 | 2026-11-25 |
| `shared/FeatureStatus.kt` | Written from the plan | REWRITE_PLAN §4.1, §6 | 2026-11-25 |
| `hook/ModuleEntry.kt` | Written from the spec | §1.1–1.2 | 2026-11-25 |
| `hook/Startup.kt` | Written from the spec; critical/deferred split is Chris's design | §1.2–1.3 | 2026-11-25 |
| `hook/Feature.kt`, `hook/FeatureRegistry.kt` | Written from the plan | REWRITE_PLAN §3, §4.1 | 2026-11-25 |
| `hook/Hooks.kt` | Written from the plan | REWRITE_PLAN §4.4 | 2026-11-25 |
| `hook/HookLog.kt` | Written from the plan | REWRITE_PLAN §4.5 | 2026-11-25 |
| `hook/HookSettings.kt`, `hook/BridgeClient.kt`, `hook/AppContext.kt` | Written from the spec and plan | F12, REWRITE_PLAN §5, S18 | 2026-11-25 |
| `hook/find/*` | Written from the spec and plan | §1.4, REWRITE_PLAN §4.2–4.3 | 2026-11-25 |
| `hook/features/FixedAnswerFeature.kt`, `hook/features/RecordingEligibility.kt` | Written from the spec; both geofence names are Chris's finding | F1–F4 | 2026-11-25 |
| `hook/features/TtsFallbackFeature.kt`, `hook/util/SilentWav.kt` | Written from the spec | F5 | 2026-11-25 |
| `hook/features/SetupCheck.kt` | Written from the spec | F6 | 2026-11-25 |
| `hook/find/FutureFactory.kt` | Written from the spec | F7.1 | 2026-11-25 |
| `hook/features/RecordAllFeature.kt` | Written from the spec; the two v88 markers and "hook every producer found" are Chris's findings | F7 | 2026-11-25 |
| `hook/features/BubbleFeature.kt` | Written from the spec; the approach is Chris's (CallRecording BubbleHook), reimplemented without looking at that file | F9 | 2026-11-25 |
| `app/*` (module app: settings, status page, bridge provider) | Written from the spec and plan | F11, F12, REWRITE_PLAN §6 | 2026-11-25 |
| `app/src/main/res/**`, `AndroidManifest.xml`, `META-INF/xposed/*` | Written for this project | — | 2026-11-25 |
| Build files, `.github/workflows/build.yml` | Written for this project | REWRITE_PLAN §10 | 2026-11-25 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle wrapper (Apache-2.0), taken from the user's own Remove-service-numbers project | — | 2026-11-25 |
| `docs/design/*` | Behavioural spec, plan and the CallRecording provenance analysis (no upstream code) | — | 2026-11-25 |
