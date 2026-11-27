# Changelog

## Unreleased (branch `contacts-filters`)

**New**
- Account and label filters on Google Phone's Contacts tab, like those of Google Contacts: an account chip ("All contacts ▾") and a label button under the search bar, each opening a sheet with contact counts. The list, the A–Z letters and the fast scroller follow the filter; the choice is kept across restarts. Switch: *Contacts tab → Account and label filters* in the module's settings (on by default, takes effect after Google Phone restarts). Moved here from the standalone Contacts-tab-filters module.

## 1.1.0 (2026-11-27)

**New**
- Putting a recorded call on hold no longer asks "Hold and stop recording?": the call goes on hold straight away (the recording so far is saved), and recording starts again by itself when the call is taken off hold (while the call screen is open).
- No "Recording saved." message sliding up on the call screen when a recording ends during a call. Other call-screen messages are unchanged.

**Changed**
- No spoken announcement on phones with a text-to-speech engine either: the module no longer asks the engine at all and hands Google Phone a silent sound file directly. This also avoids depending on how an engine handles empty text.

**Removed**
- The module no longer switches Google Phone to its built-in English recording announcement. On phones where Google Phone has no built-in announcement for the country and no text-to-speech engine is working, recording now starts without any announcement.

## 1.0.0 (2026-11-26)

First release. Dialer Enhancements is a new, independent LSPosed module for Google Phone, written from a behaviour description. It does what the CallRecording module 1.5 did, and a little more.

**Recording**
- Shows the record button in countries and on phones where Google Phone hides it (record permission, region and country checks).
- Google Phone's daily region check can no longer switch its "record unknown numbers" and "record selected numbers" settings off: the module is in place before any of Google Phone's code runs.
- Uses Google's built-in English recording announcement where the Google Phone version supports it.
- Recording starts on phones without a working text-to-speech engine. The announcement text is always blanked.
- A one-time warning when Google Phone can't record at all on the phone (not a system app, or a "downloadable" build).

**Record all calls** (off by default)
- Google Phone records every call by itself, contacts included. The switch works from the next call, without restarting Google Phone.

**Return-to-call bubble on Android 12+**
- Brings back Google Phone's floating bubble (speaker, mute, hang up, back to the call).
- If it is swiped away, it comes back the next time you leave the call screen.

**Selected numbers, several at once**
- Google Phone's Add button opens a list where numbers already on Google's list are ticked: tick to add, untick to remove, then save once.
- Numbers can also be chosen in the Dialer Enhancements app; the changes are applied the next time Google Phone's Selected-numbers screen opens.
- Every change goes through Google Phone's own code. The module only reads Google's settings file, never writes it.

**Status page**
- Opened from the LSPosed manager (tap the module); the app has no home-screen icon.
- Shows whether LSPosed is active, which Google Phone version was last seen, and for every feature whether it found what it needs in that version.

**Under the hood**
- Every feature acts only when exactly one matching method is found in Google Phone; otherwise Google Phone keeps its normal behaviour.
- What was found is remembered per Google Phone version, so the slow search runs only once after each update.
- Uses the modern Xposed API (101 or newer).

Tested on a realme RMX3363 (Android 12, ColorOS 12, Vector 2.2) with Google Phone 175.0.763896600. Older versions such as Google Phone 88 are not tested yet.
