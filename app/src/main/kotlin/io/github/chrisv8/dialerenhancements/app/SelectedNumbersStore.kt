// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10.5–F10.6 and REWRITE_PLAN.md S9.

package io.github.chrisv8.dialerenhancements.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * The app's side of the Selected-numbers feature:
 * - a copy of Google's list, sent by Google Phone (the app can't read Google's files), and
 * - changes chosen in the app, waiting until Google's Selected-numbers screen next opens.
 *
 * The queue is only cleared once Google Phone confirms it applied exactly what it read.
 */
class SelectedNumbersStore(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Google's list as last seen, or null if Google Phone hasn't sent it yet. */
    fun snapshot(): Set<String>? = if (prefs.contains(SNAPSHOT)) prefs.getStringSet(SNAPSHOT, emptySet())!!.toSet() else null

    fun putSnapshot(numbers: Collection<String>) {
        prefs.edit().putStringSet(SNAPSHOT, numbers.toSet()).apply()
    }

    class Queue(val addUris: Set<String>, val removeNumbers: Set<String>, val token: String?) {
        val isEmpty get() = addUris.isEmpty() && removeNumbers.isEmpty()
    }

    @Synchronized
    fun queue(): Queue = Queue(
        prefs.getStringSet(PENDING_ADD, emptySet())!!.toSet(),
        prefs.getStringSet(PENDING_REMOVE, emptySet())!!.toSet(),
        prefs.getString(TOKEN, null),
    )

    /** Adds to the queue (merging with anything already waiting). */
    @Synchronized
    fun enqueue(addUris: Collection<String>, removeNumbers: Collection<String>) {
        val current = queue()
        prefs.edit()
            .putStringSet(PENDING_ADD, current.addUris + addUris)
            .putStringSet(PENDING_REMOVE, current.removeNumbers + removeNumbers)
            .putString(TOKEN, UUID.randomUUID().toString())
            .commit()
    }

    /** Clears the queue if it is still the one Google Phone read with [token]. */
    @Synchronized
    fun acknowledge(token: String) {
        if (prefs.getString(TOKEN, null) != token) return
        prefs.edit().remove(PENDING_ADD).remove(PENDING_REMOVE).remove(TOKEN).commit()
    }

    private companion object {
        const val FILE = "selected_numbers"
        const val SNAPSHOT = "snapshot"
        const val PENDING_ADD = "pendingAdd"
        const val PENDING_REMOVE = "pendingRemove"
        const val TOKEN = "queueToken"
    }
}
