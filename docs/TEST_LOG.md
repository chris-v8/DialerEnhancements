# Device test log

Results of tests on a real phone, newest first. "Spec D-n" refers to the acceptance checklist in `docs/design/SPEC.md`, Appendix D.

## 2026-11-26: same phone, Google Phone 175.0.763896600

| Test | Result |
|---|---|
| Selected numbers: all five hooks found (result handler, Add button + launch, remove listener, screen opened) | Pass. |
| Route A: Add opens the module's list; two numbers added at once (Spec D-7, adds) | Pass: "picker: added 2 of 2". |
| Route A: existing numbers shown ticked; two unticked and removed at once (Spec D-7, removes) | Pass: "picker: added 0 of 0, removed 2 of 2". |
| Route A with exactly one new number uses Google's own path (Spec D-8) | Pass: no module involvement, number added. |
| Route B: two numbers queued in the app, applied when the Selected-numbers screen opened; queue cleared only after Google Phone confirmed; app's copy of the list updated (Spec D-9) | Pass: "queued in the app: added 2 of 2", then the same for "removed 2 of 2". |
| Google's own Remove button still works with the hooks installed | Pass. |
| Multi-select switched off: Add opens the system picker again (Spec D-10) | Pass, and without restarting Google Phone. |
| Bubble swiped away during a call comes back the next time the call screen is left | Pass (user). |

The list was empty before the tests and empty afterwards.

Release build 1.0.0 (minified, signed with the personal-modules key), installed in place of the debug build:

| Test | Result |
|---|---|
| All nine features active after a fresh install (code search) | Pass: critical phase 273 ms. |
| Status report reaches the app with no launcher activity | Pass. |
| No launcher icon; settings registered for the LSPosed manager | Pass (`MODULE_SETTINGS` activity resolves, no `LAUNCHER` entry). |
| Opening the settings from the LSPosed manager | Not yet confirmed. |

## 2026-11-25: realme RMX3363, ColorOS 12 / Android 12, Vector 2.2 (API 102), Google Phone 175.0.763896600

| Test | Result |
|---|---|
| All features found on first start (code search) | Pass: can_record, geofence (`withinCrosbyGeoFence`), announcement_locale, country, tts_fallback (3), setup_check, record_all (`produceShouldStartAutomaticRecordingForContact`), bubble. Critical phase 110–140 ms. |
| Later starts use the discovery cache | Pass: critical phase 32–41 ms, "code search not needed". |
| Status page receives Google Phone's report (Android 11+ package visibility) | Pass. |
| Manual recording (Spec D-2) | Pass (user). |
| Record all calls: contact recorded automatically, switch works without restarting Google Phone (Spec D-4) | Pass (user; log: "answered yes" in a process started before the switch). |
| Android 12 bubble appears after leaving the call screen (Spec D-6, first part) | Pass (user). Dismissal and no-overlay cases not yet tested. |
| Daily region check from a cold start (Spec D-5 / §1.2) | Pass: job cold-started Google Phone; module's critical phase finished (34 ms) before WorkManager started; `recording_geofence_check_worker` ran with result SUCCESS; region check answered true; `CallRecordingSettingsData.pb` unchanged; no "unsupported region" notification. |

Notes for Google Phone 175:
- The text `withinCallRecordingGeoFence` appears only as a log label inside a wrapper that itself calls the hooked region check and country check, so no further hook is needed.
- Not yet tested: Google Phone v88; the "settings still on after two days" check.

## How to force the daily region check (Spec §1.2)

WorkManager will not run a periodic job before its time, and a force-stop removes all of Google Phone's jobs from Android's scheduler until Google Phone starts again. So:

1. Open Google Phone once (this schedules the job), then go to the home screen.
2. Find the job: in Google Phone's `no_backup/androidx.work.workdb`, table `WorkName` has `recording_geofence_check_worker`; its Android job id is in `SystemIdInfo.system_id`. The id changes each time the job runs.
3. End the process without a force-stop: `su -c "kill -9 <pid of com.google.android.dialer>"`.
4. To make the job due now, back up the database, then move that job's `WorkSpec.last_enqueue_time` back by three days (edit a copy on the PC, checkpoint it, write it back with `cat` as root and delete the `-wal`/`-shm` files). WorkManager overwrites this value itself when the job runs.
5. Run `cmd jobscheduler run -f com.google.android.dialer <job id>` and read the module log and `WM-WorkerWrapper` lines.
6. Compare the MD5 of `files/CallRecordingSettingsData.pb` before and after.
