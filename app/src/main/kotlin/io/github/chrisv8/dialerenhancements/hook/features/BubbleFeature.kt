// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F9. The idea of bringing the
// bubble back by re-running Google's decision without its Android 12 check is Chris's, from CallRecording.

package io.github.chrisv8.dialerenhancements.hook.features

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.chrisv8.dialerenhancements.hook.BridgeClient
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
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
 * SPEC F9: brings back Google Phone's floating "return to call" bubble on Android 12 and later
 * (speaker, mute, hang up, back to the call), shown when you leave the call screen mid-call.
 *
 * The bubble code is still in Google Phone, but its yes/no decision refuses outright on
 * Android 12+. This feature makes the same decision again, minus that refusal: no bubble if
 * Phone may not draw over other apps, or if Google asked to hold it back; otherwise show it.
 *
 * Unlike Google's own decision, a bubble swiped away during a call comes back the next time
 * the call screen is left (the user's choice: it is too easy to swipe away by accident).
 *
 * Only Google Phone versions whose decision method contains that exact Android 12 refusal are
 * touched. Anything unfamiliar lets Google's own decision run.
 */
object BubbleFeature : Feature {
    override val id = FeatureIds.BUBBLE
    override val phase = Phase.DEFERRED

    private const val DECISION = "produceIsBubbleFeatureEnabledForCall"

    /** The log line Google's decision writes when it refuses on Android 12+. */
    private const val ANDROID_12_REFUSAL = "Not showing bubble in Android S+."

    private const val PRODUCER = "producer"
    private const val FACTORY = "factory"

    /** Producers take their inputs packed in one `Object` and return a Future. */
    private val producerShape = shape {
        params(Any::class.java)
        returnsSubtypeOf(Future::class.java)
    }

    private val firstOverride = AtomicBoolean(true)

    override fun discover(finder: Finder): Discovery {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Discovery.notNeeded("the bubble works by itself before Android 12")
        }
        val producer = when (val match = finder.uniqueUsingBoth(DECISION, ANDROID_12_REFUSAL, producerShape)) {
            is Match.Found -> match.method
            else -> return Discovery.nothing(match.describe())
        }
        return when (val factory = FutureFactory.find(finder, producer)) {
            is Match.Found -> Discovery(mapOf(PRODUCER to producer, FACTORY to factory.method))
            else -> Discovery.nothing("no Future helper (${factory.describe()})")
        }
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        val producer = discovery.targets.getValue(PRODUCER)
        val factory = discovery.targets.getValue(FACTORY)
        FutureFactory.verify(factory, producer)?.let { problem ->
            HookLog.warn(id, "Future helper rejected, $problem")
            return Installed(0, problem)
        }

        hooks.intercept(producer) { chain ->
            val show = try {
                decide(chain.getArg(0))
            } catch (t: Throwable) {
                HookLog.debug(id) { "unexpected input, Google decides: $t" }
                null
            }
            val answer = show?.let {
                try {
                    FutureFactory.wrap(factory, producer, it)
                } catch (t: Throwable) {
                    HookLog.debug(id) { "wrapping the answer failed: $t" }
                    null
                }
            } ?: return@intercept chain.proceed()

            if (firstOverride.compareAndSet(true, false)) {
                HookLog.info(id, "decided without the Android 12 block: show=$show (first time in this process)")
                BridgeClient.reportHit(id)
            } else {
                HookLog.debug(id) { "show=$show" }
            }
            answer
        }
        return Installed(1)
    }

    /**
     * Google's decision without the Android 12 refusal and without the "dismissed for this
     * call" rule. The producer's input is a list of [Context, Boolean "hold back", per-call
     * bubble state]. Returns null for anything else.
     */
    private fun decide(input: Any?): Boolean? {
        val inputs = input as? List<*> ?: return null
        if (inputs.size < 3 || inputs[2] == null) return null
        val context = inputs[0] as? Context ?: return null
        val holdBack = inputs[1] as? Boolean ?: return null
        return if (!Settings.canDrawOverlays(context)) false else !holdBack
    }
}
