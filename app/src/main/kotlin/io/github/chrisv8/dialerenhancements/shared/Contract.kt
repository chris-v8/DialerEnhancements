// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §5.

package io.github.chrisv8.dialerenhancements.shared

import io.github.chrisv8.dialerenhancements.BuildConfig

/**
 * Names shared by the module app and the hooks inside Google Phone.
 * Both sides read them from here, so they can never drift apart.
 */
object Contract {
    const val GOOGLE_PHONE = "com.google.android.dialer"

    /** Remote-preferences group (managed by LSPosed) that holds the user's settings. */
    const val SETTINGS_GROUP = "settings"

    /** Settings keys and their defaults. */
    object Settings {
        const val DIAGNOSTICS = "detailed_logging"
        const val DIAGNOSTICS_DEFAULT = false

        const val RECORD_ALL = "record_all_calls"
        const val RECORD_ALL_DEFAULT = false

        /** Google Phone's "Add" button under Selected numbers opens the module's multi-select list. */
        const val MULTI_SELECT = "multi_select_add_button"
        const val MULTI_SELECT_DEFAULT = true

        /** Account and label filters on Google Phone's Contacts tab. */
        const val CONTACT_FILTERS = "contact_filters"
        const val CONTACT_FILTERS_DEFAULT = true
    }

    /** The module app's content provider that Google Phone reports to. */
    object Bridge {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.bridge"
        const val URI = "content://$AUTHORITY"

        /** Google Phone → app: results of one start of Google Phone. */
        const val METHOD_REPORT_STARTUP = "reportStartup"

        /** Google Phone → app: a feature changed something for the first time since Google Phone started. `arg` = feature id. */
        const val METHOD_REPORT_HIT = "reportHit"

        /** Google Phone → app: Google's current Selected-numbers list. Extras: [KEY_NUMBERS]. */
        const val METHOD_PUT_SNAPSHOT = "putSnapshot"

        /** Google Phone → app: changes waiting to be applied. Reply: [KEY_ADD_URIS], [KEY_NUMBERS], [KEY_TOKEN]. */
        const val METHOD_PEEK_QUEUE = "peekQueue"

        /** Google Phone → app: the queue read with token `arg` was applied; forget it. */
        const val METHOD_ACK_QUEUE = "ackQueue"

        const val KEY_NUMBERS = "numbers"
        const val KEY_ADD_URIS = "addUris"
        const val KEY_TOKEN = "token"

        const val KEY_PHONE_VERSION_NAME = "phoneVersionName"
        const val KEY_PHONE_VERSION_CODE = "phoneVersionCode"
        const val KEY_MODULE_VERSION_CODE = "moduleVersionCode"
        const val KEY_FEATURES = "features"
        const val KEY_SETUP_PROBLEM = "setupProblem"
    }

    /** Stable ids of every feature. Used in logs, the status page and the discovery cache. */
    object FeatureIds {
        const val CAN_RECORD = "can_record"
        const val GEOFENCE = "geofence"
        const val COUNTRY = "country"
        const val TTS_FALLBACK = "tts_fallback"
        const val SETUP_CHECK = "setup_check"
        const val RECORD_ALL = "record_all"
        const val BUBBLE = "bubble"
        const val SELECTED_NUMBERS = "selected_numbers"
        const val RECORDING_SAVED_MESSAGE = "recording_saved_message"
        const val HOLD_RECORDING = "hold_recording"
        const val CONTACT_FILTERS = "contact_filters"

        /** Every id above, so the app can refuse anything else. */
        val all = setOf(
            CAN_RECORD, GEOFENCE, COUNTRY, TTS_FALLBACK, SETUP_CHECK, RECORD_ALL, BUBBLE,
            SELECTED_NUMBERS, RECORDING_SAVED_MESSAGE, HOLD_RECORDING, CONTACT_FILTERS,
        )
    }

    /** The module's number picker and the intent extras it shares with the hooks (SPEC F10.3). */
    object Picker {
        const val ACTIVITY = "${BuildConfig.APPLICATION_ID}.app.picker.NumberPickerActivity"

        /** Picker → Google Phone: `ArrayList<Uri>` of contact data rows to add. */
        const val EXTRA_ADD = "${BuildConfig.APPLICATION_ID}.extra.ADD"

        /** Picker → Google Phone: `ArrayList<String>` of numbers to remove, exactly as Google stored them. */
        const val EXTRA_REMOVE = "${BuildConfig.APPLICATION_ID}.extra.REMOVE"

        /** Google Phone → picker: `ArrayList<String>` of Google's current list, as stored. */
        const val EXTRA_EXISTING = "${BuildConfig.APPLICATION_ID}.extra.EXISTING"
    }

    /** Problems the setup check can find (SPEC F6). */
    object SetupProblem {
        const val NO_CAPTURE_PERMISSION = "no_capture_permission"
        const val DOWNLOADABLE_BUILD = "downloadable_build"
    }
}
