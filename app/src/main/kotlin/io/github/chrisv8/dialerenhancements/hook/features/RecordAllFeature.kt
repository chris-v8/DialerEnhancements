// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F7 (markers are Google Phone's own strings).
// The two v88 markers and "hook every producer found" are Chris's findings from CallRecording.

package io.github.chrisv8.dialerenhancements.hook.features

import io.github.chrisv8.dialerenhancements.hook.BridgeClient
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.HookSettings
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.find.FutureFactory
import io.github.chrisv8.dialerenhancements.hook.find.Match
import io.github.chrisv8.dialerenhancements.hook.find.shape
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.lang.reflect.Method
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SPEC F7: with "Record all calls" switched on in the module app, Google Phone records every
 * call automatically (incoming and outgoing, contacts and unknown numbers), whatever its own
 * "unknown numbers / selected numbers" rules say. Switched off, Google's rules apply unchanged.
 *
 * Google Phone decides per call in a "producer" method that returns a Future of a yes/no
 * answer. Its name differs between releases, so every known name is tried and each one found
 * (with a working Future helper) is hooked. The switch is read on every call, from memory, so
 * changing it needs no restart.
 */
object RecordAllFeature : Feature {
    override val id = FeatureIds.RECORD_ALL
    override val phase = Phase.DEFERRED

    /** Google Phone's names for "should this call be recorded automatically?". */
    private val markers = listOf(
        "produceShouldStartAutomaticRecordingForContact", // 2xx releases
        "produceShouldStartAutomaticRecording", // v88, newer recording service
        "produceIsCrosbyEnabledForCall", // v88, older in-call screen
    )

    /** Producers take their inputs packed in one `Object` and return a Future. */
    private val producerShape = shape {
        params(Any::class.java)
        returnsSubtypeOf(Future::class.java)
    }

    private const val PRODUCER = "producer:"
    private const val FACTORY = "factory:"

    private val firstOverride = AtomicBoolean(true)

    override fun discover(finder: Finder): Discovery {
        val found = LinkedHashMap<String, Method>()
        val problems = mutableListOf<String>()
        for (marker in markers) {
            val producer = when (val match = finder.unique(marker, producerShape)) {
                is Match.Found -> match.method
                is Match.Ambiguous -> { problems += match.describe(); continue }
                is Match.NotFound -> continue // Each release has only some of these names.
            }
            when (val factory = FutureFactory.find(finder, producer)) {
                is Match.Found -> {
                    found[PRODUCER + marker] = producer
                    found[FACTORY + marker] = factory.method
                }
                else -> problems += "$marker: no Future helper (${factory.describe()})"
            }
        }
        val note = when {
            problems.isNotEmpty() -> problems.joinToString("; ")
            found.isEmpty() -> "no decision method found"
            else -> ""
        }
        return Discovery(found, note)
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        var installed = 0
        val problems = mutableListOf<String>()
        for (marker in markers) {
            val producer = discovery.targets[PRODUCER + marker] ?: continue
            val factory = discovery.targets[FACTORY + marker] ?: continue
            // Checked on every start, so a cached helper that changed behaviour is never trusted.
            val problem = FutureFactory.verify(factory, producer)
            if (problem != null) {
                HookLog.warn(id, "$marker: Future helper rejected, $problem")
                problems += "$marker: $problem"
                continue
            }
            hookProducer(marker, producer, factory, hooks)
            installed++
        }
        return Installed(installed, problems.joinToString("; "))
    }

    private fun hookProducer(marker: String, producer: Method, factory: Method, hooks: Hooks) {
        hooks.intercept(producer) { chain ->
            if (!HookSettings.recordAll) return@intercept chain.proceed()
            val answer = try {
                FutureFactory.wrap(factory, producer, true)
            } catch (t: Throwable) {
                HookLog.debug(id) { "$marker: wrapping the answer failed: $t" }
                null
            }
            if (answer == null) return@intercept chain.proceed() // Google decides as usual.

            if (firstOverride.compareAndSet(true, false)) {
                HookLog.info(id, "$marker: answered yes (first time in this process)")
                BridgeClient.reportHit(id)
            } else {
                HookLog.debug(id) { "$marker: answered yes" }
            }
            answer
        }
    }
}
