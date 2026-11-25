// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §5.3.

package io.github.chrisv8.dialerenhancements.app

import android.content.Context
import android.util.Log
import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.libxposed.service.XposedService

/**
 * The user's settings. The app keeps its own copy and mirrors every value into LSPosed's
 * remote preferences, which is where the hooks inside Google Phone read them.
 */
class ModuleSettings(context: Context) {
    private val local = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var diagnostics: Boolean
        get() = local.getBoolean(Contract.Settings.DIAGNOSTICS, Contract.Settings.DIAGNOSTICS_DEFAULT)
        set(value) {
            local.edit().putBoolean(Contract.Settings.DIAGNOSTICS, value).apply()
            Framework.service.value?.let(::copyTo)
        }

    /** Writes every setting to LSPosed. Called whenever LSPosed connects, and after each change. */
    fun copyTo(service: XposedService) {
        try {
            service.getRemotePreferences(Contract.SETTINGS_GROUP).edit()
                .putBoolean(Contract.Settings.DIAGNOSTICS, diagnostics)
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "can't save settings to LSPosed", t)
        }
    }

    private companion object {
        const val FILE = "settings"
        const val TAG = "DialerEnhancements"
    }
}
