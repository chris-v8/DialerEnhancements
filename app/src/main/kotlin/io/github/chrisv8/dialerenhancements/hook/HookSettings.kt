// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §5.

package io.github.chrisv8.dialerenhancements.hook

import android.content.SharedPreferences
import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.libxposed.api.XposedInterface

/**
 * The user's settings as seen inside Google Phone. LSPosed keeps them in sync with the
 * module app and holds them in memory, so reading a value costs nothing (no call to the app).
 * If they can't be read, every setting keeps its safe default.
 */
object HookSettings {
    @Volatile
    private var prefs: SharedPreferences? = null

    fun connect(xposed: XposedInterface) {
        prefs = try {
            xposed.getRemotePreferences(Contract.SETTINGS_GROUP)
        } catch (t: Throwable) {
            HookLog.warn("settings", "can't read the module's settings; using defaults", t)
            null
        }
        HookLog.debugEnabled = { diagnostics }
    }

    val diagnostics: Boolean
        get() = read { it.getBoolean(Contract.Settings.DIAGNOSTICS, Contract.Settings.DIAGNOSTICS_DEFAULT) }
            ?: Contract.Settings.DIAGNOSTICS_DEFAULT

    /** Read on every call decision, so switching it in the app takes effect at once. */
    val recordAll: Boolean
        get() = read { it.getBoolean(Contract.Settings.RECORD_ALL, Contract.Settings.RECORD_ALL_DEFAULT) }
            ?: Contract.Settings.RECORD_ALL_DEFAULT

    /** Read on every Add click. Off when the settings can't be read at all, so the stock picker stays. */
    val multiSelect: Boolean
        get() = read { it.getBoolean(Contract.Settings.MULTI_SELECT, Contract.Settings.MULTI_SELECT_DEFAULT) }
            ?: false

    /** Read when the Contacts tab is built and on every list load. On when the settings can't be read. */
    val contactFilters: Boolean
        get() = read { it.getBoolean(Contract.Settings.CONTACT_FILTERS, Contract.Settings.CONTACT_FILTERS_DEFAULT) }
            ?: Contract.Settings.CONTACT_FILTERS_DEFAULT

    private fun <T> read(block: (SharedPreferences) -> T): T? = try {
        prefs?.let(block)
    } catch (_: Throwable) {
        null
    }
}
