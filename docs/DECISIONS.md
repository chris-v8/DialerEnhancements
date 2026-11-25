# Decisions

The design documents in `docs/design/` were written before these choices were made. Where they differ, **this file wins**.

| # | Topic | Decision | Date |
|---|---|---|---|
| 1 | Licence | GPL-3.0-or-later. SPDX line at the top of every source file. | 2026-11-25 |
| 2 | App id and package | `io.github.chrisv8.dialerenhancements` | 2026-11-25 |
| 3 | Xposed API | **Modern libxposed API 102** (`minApiVersion=101`). The Vector build on the test phone supports it, and the user's other modules use it. Supersedes REWRITE_PLAN D3 (legacy API 82). | 2026-11-25 |
| 4 | Settings channel | LSPosed remote preferences (app writes, hooks read in memory). A content provider in the app is used only for Google Phone → app reports. Supersedes REWRITE_PLAN §5.3 option A for settings. | 2026-11-25 |
| 5 | Language and UI | Kotlin; Jetpack Compose with Material 3. Supersedes REWRITE_PLAN D5. | 2026-11-25 |
| 6 | DEX search | DexKit 2.3.0 behind `CodeSearch`/`Finder`, with a per-version discovery cache. | 2026-11-25 |
| 7 | "Record all calls" default | Off. | 2026-11-25 |
| 8 | Announcement text (SPEC F5) | Always blanked, as in CallRecording 1.5. Supersedes REWRITE_PLAN D7. | 2026-11-25 |
| 9 | v88 locale shape (SPEC F4) | Not matched; v88 keeps the text-to-speech path, as in 1.5. | 2026-11-25 |
| 10 | Timing diagnostics (SPEC F8) | Optional feature, off by default, added before parity. | 2026-11-25 |
| 11 | Languages | English only. | 2026-11-25 |
| 12 | Matching rule | "Exactly one match" for every lookup, including F1–F4 (1.5 took the first match there). | 2026-11-25 |
| 13 | Processes | Hooks are installed only in Google Phone's main process. | 2026-11-25 |
| 14 | ABIs | arm64-v8a only. | 2026-11-25 |
| 15 | Signing | A fresh release key created by the user; CI reads it from GitHub secrets (`DE_KEYSTORE_*`). | 2026-11-25 |
| 16 | Versions | 1.0 = milestone M1 (recording works). Then parity with CallRecording 1.5, then Material 3 polish and new features toward 2.0. | 2026-11-25 |
| 17 | Out of scope for now | Old in-call layout; SIM service numbers (separate module); power-button idea (separate project). | 2026-11-25 |
