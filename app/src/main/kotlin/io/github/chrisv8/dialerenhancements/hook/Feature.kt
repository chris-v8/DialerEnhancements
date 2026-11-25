// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.1.

package io.github.chrisv8.dialerenhancements.hook

import io.github.chrisv8.dialerenhancements.hook.find.Finder
import java.lang.reflect.Method

/** When a feature is set up inside Google Phone (SPEC §1.2). */
enum class Phase {
    /** Synchronously while the module loads, before any of Google Phone's own code runs. */
    CRITICAL,

    /** Afterwards, on the module's background thread. */
    DEFERRED,
}

/**
 * What a feature found: its target methods, each under a role name of the feature's choosing,
 * plus an optional note for the status page (for example why something was skipped).
 */
class Discovery(val targets: Map<String, Method>, val note: String = "") {
    companion object {
        fun nothing(note: String) = Discovery(emptyMap(), note)
    }
}

/**
 * One feature of the module. Adding a feature means writing one class and listing it in
 * [FeatureRegistry]; [Startup] does the rest (timing, error handling, caching, status).
 */
interface Feature {
    /** Stable id from Contract.FeatureIds. */
    val id: String

    val phase: Phase

    /**
     * True if [discover] searches Google Phone's code. Its result is then cached per
     * Google Phone version. Features that only hook Android's own classes say false.
     */
    val searchesCode: Boolean get() = true

    /** Finds the targets. Pure lookup, no hooking. */
    fun discover(finder: Finder): Discovery

    /**
     * Hooks the targets and returns how many hooks were installed.
     * Must leave Google Phone's behaviour alone for any input it doesn't recognise.
     */
    fun install(discovery: Discovery, hooks: Hooks): Int
}
