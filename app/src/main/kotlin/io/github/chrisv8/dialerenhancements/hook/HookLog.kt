// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.5.

package io.github.chrisv8.dialerenhancements.hook

/**
 * The one logger used inside Google Phone. Every line looks like `[scope] message`.
 *
 * Rules: never log phone numbers, contact names, audio, file paths or announcement text.
 * Debug lines appear only while "detailed logging" is on in the module app.
 */
object HookLog {
    // Same values as android.util.Log, repeated so this file also works in PC unit tests.
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    fun interface Sink {
        fun write(priority: Int, message: String, error: Throwable?)
    }

    /** Where lines go. Set by the module entry; unit tests leave it empty. */
    @Volatile
    var sink: Sink? = null

    /** Asked before every debug line, so switching diagnostics on takes effect at once. */
    @Volatile
    var debugEnabled: () -> Boolean = { false }

    fun debug(scope: String, message: () -> String) {
        if (debugEnabled()) write(DEBUG, scope, message(), null)
    }

    fun info(scope: String, message: String) = write(INFO, scope, message, null)

    fun warn(scope: String, message: String, error: Throwable? = null) = write(WARN, scope, message, error)

    fun error(scope: String, message: String, error: Throwable? = null) = write(ERROR, scope, message, error)

    private fun write(priority: Int, scope: String, message: String, error: Throwable?) {
        try {
            sink?.write(priority, "[$scope] $message", error)
        } catch (_: Throwable) {
            // Logging must never break Google Phone.
        }
    }
}
