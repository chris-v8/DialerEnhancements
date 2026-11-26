// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md §1.4.

package io.github.chrisv8.dialerenhancements.hook.find

import java.lang.reflect.Method

/**
 * The raw questions the module asks about Google Phone's compiled code (SPEC §1.4).
 * Results are already turned into real methods; ones that can't be loaded are left out.
 * [Finder] adds the shape rules and the "exactly one" rule on top.
 */
interface CodeSearch : AutoCloseable {
    /** Methods whose code loads exactly this text constant (whole-string equality). */
    fun methodsUsing(marker: String, paramCount: Int? = null): List<Method>

    /** Methods that [caller]'s code invokes. */
    fun calledBy(caller: Method, paramCount: Int? = null): List<Method>

    /** Methods whose code invokes [callee]. */
    fun callersOf(callee: Method): List<Method>
}
