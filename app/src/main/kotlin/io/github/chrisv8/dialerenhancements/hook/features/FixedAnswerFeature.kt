// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F1–F4 and REWRITE_PLAN.md S2.
// "Try every marker and hook each one found" is Chris's approach from CallRecording.

package io.github.chrisv8.dialerenhancements.hook.features

import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.find.Match
import io.github.chrisv8.dialerenhancements.hook.find.MethodShape
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A feature that makes one of Google Phone's yes/no questions always give the same answer.
 *
 * Google Phone's method is found by a text marker it uses (several alternative markers may
 * be listed, for different Google Phone releases). Every marker with exactly one matching
 * method is hooked; Google's own code for it then never runs.
 */
abstract class FixedAnswerFeature(
    final override val id: String,
    private val markers: List<String>,
    private val shape: MethodShape,
) : Feature {
    final override val phase = Phase.CRITICAL

    /** The answer Google Phone gets instead of computing its own. */
    protected abstract val answer: Any

    private val firstUse = AtomicBoolean(true)

    override fun discover(finder: Finder): Discovery {
        val found = LinkedHashMap<String, Method>()
        val problems = mutableListOf<String>()
        for (marker in markers) {
            when (val match = finder.unique(marker, shape)) {
                is Match.Found -> found[marker] = match.method
                is Match.Ambiguous -> problems += match.describe()
                // Expected when listing alternative names: each release has only one of them.
                is Match.NotFound -> if (markers.size == 1) problems += match.describe()
            }
        }
        val note = when {
            problems.isNotEmpty() -> problems.joinToString("; ")
            found.isEmpty() -> "none of ${markers.joinToString()} found"
            else -> ""
        }
        return Discovery(found, note)
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        for ((marker, method) in discovery.targets) {
            hooks.replace(method) {
                if (firstUse.compareAndSet(true, false)) {
                    HookLog.info(id, "$marker answered $answer (first time in this process)")
                } else {
                    HookLog.debug(id) { "$marker answered $answer" }
                }
                answer
            }
        }
        return Installed(discovery.targets.size)
    }
}
