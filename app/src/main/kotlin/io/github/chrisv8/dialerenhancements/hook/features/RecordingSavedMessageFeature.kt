// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements (new in 1.1.0, not part of the CallRecording spec).
// Facts about Google Phone 175 were found by reading its code with dexdump; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.features

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.chrisv8.dialerenhancements.hook.BridgeClient
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.find.Match
import io.github.chrisv8.dialerenhancements.hook.find.shape
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stops the "Recording saved." message that slides up at the bottom of the call screen when a
 * recording ends during a call.
 *
 * Google Phone builds all its call-screen messages in one helper (recognised by its own error
 * text) and shows the result right away. When the message built is "Recording saved.", the
 * module skips showing it. Every other message is shown as usual. Nothing else changes: the
 * recording is saved exactly as before.
 */
object RecordingSavedMessageFeature : Feature {
    override val id = FeatureIds.RECORDING_SAVED_MESSAGE
    override val phase = Phase.DEFERRED

    /** Error text in Google's call-screen message helper. */
    private const val HELPER_MARKER = "Message data should not be null."

    /** Google Phone's own name for the message text (a string resource). */
    private const val SAVED_MESSAGE = "call_recording_audio_saved_snackbar"

    /** The message's text view inside every Material snackbar. */
    private const val SNACKBAR_TEXT = "snackbar_text"
    private const val SNACKBAR_CONTENT = "com.google.android.material.snackbar.SnackbarContentLayout"

    private const val HELPER = "messageHelper"
    private const val SHOW = "show"

    /** Snackbars built for the "Recording saved." message, not shown. Weak so they can be freed. */
    private val suppressed = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))

    /** Per snackbar class: its method returning the content layout. */
    private val contentGetters = ConcurrentHashMap<Class<*>, Method>()

    private val firstHide = AtomicBoolean(true)

    override fun discover(finder: Finder): Discovery {
        // The helper: (view, key, message) → snackbar.
        val helper = when (
            val match = finder.unique(HELPER_MARKER, shape {
                paramCount(3)
                where("(View, String, …) → object") {
                    it.parameterTypes[0] == View::class.java && it.parameterTypes[1] == String::class.java &&
                        !it.returnType.isPrimitive
                }
            })
        ) {
            is Match.Found -> match.method
            else -> return Discovery.nothing(match.describe())
        }
        // "Show": the one no-argument snackbar method that the helper's callers invoke.
        val snackbar = helper.returnType
        val show = when (
            val match = finder.uniqueCallee(finder.callersOf(helper), shape {
                paramCount(0)
                returns(Void.TYPE)
                where("declared by the snackbar") { it.declaringClass.isAssignableFrom(snackbar) }
            })
        ) {
            is Match.Found -> match.method
            else -> return Discovery.nothing("show method: ${match.describe()}")
        }
        return Discovery(mapOf(HELPER to helper, SHOW to show))
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        val helper = discovery.targets.getValue(HELPER)
        val show = discovery.targets.getValue(SHOW)

        hooks.intercept(helper) { chain ->
            val snackbar = chain.proceed()
            if (snackbar != null && isRecordingSaved(snackbar)) suppressed += snackbar
            snackbar
        }
        hooks.intercept(show) { chain ->
            val snackbar = chain.thisObject
            if (snackbar == null || !suppressed.remove(snackbar)) return@intercept chain.proceed()
            if (firstHide.compareAndSet(true, false)) {
                HookLog.info(id, "\"Recording saved\" message not shown (first time in this process)")
                BridgeClient.reportHit(id)
            } else {
                HookLog.debug(id) { "\"Recording saved\" message not shown" }
            }
            null
        }
        return Installed(2)
    }

    /** True if [snackbar] shows Google's "Recording saved." text. Anything unexpected → false. */
    private fun isRecordingSaved(snackbar: Any): Boolean = try {
        val content = contentLayoutOf(snackbar)
        val context = content?.context
        if (content == null || context == null) {
            false
        } else {
            val resources = context.resources
            val textId = resources.getIdentifier(SNACKBAR_TEXT, "id", context.packageName)
            val savedId = resources.getIdentifier(SAVED_MESSAGE, "string", context.packageName)
            val text = (content.findViewById<View>(textId) as? TextView)?.text?.toString()
            textId != 0 && savedId != 0 && text != null && text == context.getString(savedId)
        }
    } catch (t: Throwable) {
        HookLog.debug(id) { "can't read the message: $t" }
        false
    }

    private fun contentLayoutOf(snackbar: Any): ViewGroup? {
        val type = snackbar.javaClass
        val getter = contentGetters[type] ?: generateSequence<Class<*>>(type) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.parameterCount == 0 && it.returnType.name == SNACKBAR_CONTENT }
            ?.also {
                it.isAccessible = true
                contentGetters[type] = it
            }
            ?: return null
        return getter.invoke(snackbar) as? ViewGroup
    }
}
