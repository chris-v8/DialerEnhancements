# Release checks

## 1.1.0 (2026-11-27)

### Similarity check against CallRecording

Same script and method as for 1.0.0. No new shared text strings. New identifier hits are only "CallRecording" in the origin comments of the two new feature files, and `MODULE_SETTINGS`, which is LSPosed's own intent category (the old project's preference file happened to have a similar name). Nothing to change.

### Other checks

- Unit tests and lint pass.
- Release APK signed with the personal-modules key (certificate SHA-256 `3c379dd2…ed097`), 16 KB page alignment checked.
- Every change in 1.1.0 was tested on the phone with the release build (see `docs/TEST_LOG.md`).

## 1.0.0 (2026-11-26)

### Similarity check against CallRecording (REWRITE_PLAN §12.9)

A script compared `C:\dev\CallRecording` with this repository and printed only what the two have in common: identifiers the old project used (list in `docs/design/CALLRECORDING_PROVENANCE.md` §6) and text strings of 8 or more characters in code and resources. The old files were not read otherwise. A token-based copy detector was not used: the old code is Java and C++, this project is Kotlin.

Findings and outcome:

| Found in both | Verdict |
|---|---|
| Google Phone's markers (`canRecordCall`, `produce…`, `uri received from system contact picker`, …), its file name `CallRecordingSettingsData.pb`, the bubble refusal sentence | Facts used to find Google's code; allowed. |
| Android names (`synthesizeToFile`, `dispatchOnInit`, `isLanguageAvailable`, `mUtteranceProgressListener`, `startActivityForResult`, `onResume`, intent actions, permissions, the phone-picker MIME type) | Android API facts; allowed. |
| Build and manifest boilerplate (`com.android.application`, `arm64-v8a`, `proguard-rules.pro`, `app_name`, XML header) | Generic; allowed. |
| Chris's own Selected-numbers texts and names (`Search name or number`, `Save (+%1$d / −%2$d)`, `picker_*` resource names, `ProtoNumbers`, `NumberPickerActivity`) | Chris's work; allowed (CALLRECORDING_PROVENANCE §5). |
| "CallRecording" in origin comments | Attribution; intended. |
| `Selected numbers`, `return to call`, `yyyy-MM-dd HH:mm`, `downloadable`, `SoonBlockedPrivateApi` | Google's own screen and feature names, a standard date format, Google's version suffix, a lint check id; allowed. |
| Settings key `diagnostic_logging`, headings "Automatic recording" and "Diagnostic logging" (Jie's names) | **Changed** before release to `detailed_logging`, "Auto-record" and "Detailed logging". |

### Other checks

- Unit tests and lint pass.
- Release APK signed with the personal-modules key (certificate SHA-256 `3c379dd2…ed097`), 16 KB page alignment checked.
- Release build tested on the phone (see `docs/TEST_LOG.md`).
