# Dialer Enhancements

An LSPosed module for **Google Phone** (`com.google.android.dialer`) that unlocks its built-in call recorder where Google switched it off, and adds recording options.

> Recording calls is regulated differently in every country. Tell the other party when you record, and follow the laws where you are.

## What it does

- Shows the record button in countries and on phones where Google Phone hides it.
- Keeps Google Phone's own "record unknown numbers" and "record selected numbers" switches from turning themselves off (its daily region check is answered before any of its code runs).
- Lets recording start on phones without a working text-to-speech engine.
- Warns you once when your Google Phone can't record at all (for example when it isn't installed as a system app).
- **Record all calls** (off by default): Google Phone then records every call by itself, contacts included. The switch takes effect from the next call, without restarting Google Phone.

- Brings back Google Phone's floating **return-to-call bubble** on Android 12 and later (speaker, mute, hang up, back to the call). If you swipe it away by accident, it comes back the next time you leave the call screen.
- **Selected numbers, several at once:** Google Phone's Add button (Settings → Call recording → Selected numbers) opens a list where you tick numbers to add and untick numbers to remove, all in one go. You can also choose them in the Dialer Enhancements app; the changes are applied the next time you open that Google Phone screen. Every change goes through Google Phone's own code; the module never edits its settings file.

## Requirements

- Rooted Android 9 or newer with LSPosed (or a fork such as Vector) that supports the modern Xposed API (101 or newer).
- Google Phone installed as a **system** app, because only system installs may capture call audio.

## Install

1. Install the APK.
2. In LSPosed, enable **Dialer Enhancements**. Its scope is Google Phone.
3. Force-stop Google Phone (or reboot), then open it once.
4. In the LSPosed manager, tap Dialer Enhancements to open its settings (the app has no home-screen icon). The status page shows which features are active on your Google Phone version.

If you used the older CallRecording module, disable it in LSPosed first. Both change the same parts of Google Phone.

## How it works

Google renames its code in every release. The module finds what it needs by the fixed text that Google's methods use (for example `canRecordCall`) and by their shape. A feature only acts when **exactly one** method matches; otherwise it leaves Google Phone alone. What it found is remembered per Google Phone version, so the search runs only once after each update.

Design notes are in `docs/`: the behaviour spec (`docs/design/SPEC.md`), the plan (`docs/design/REWRITE_PLAN.md`) and the decisions taken since (`docs/DECISIONS.md`).

## Credits

The approach was first shown by the CallRecording module by vvb2060 and a fork by yagay. Dialer Enhancements is an independent implementation and contains none of their code.

## Licence

GPL-3.0-or-later, see `LICENSE`. Third-party libraries are listed in `THIRD_PARTY_NOTICES.md`.
