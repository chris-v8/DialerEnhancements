// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F5.

package io.github.chrisv8.dialerenhancements.hook.features

import android.annotation.SuppressLint
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.util.SilentWav
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * SPEC F5: no spoken recording announcement, and recording starts whether or not a
 * text-to-speech engine is installed or working.
 *
 * Before recording, Google Phone writes its announcement to a sound file with text-to-speech
 * and waits for the "done" callback. Without a working engine that never happens and
 * recording fails. The module never asks the engine at all (the user's decision): it writes a
 * silent sound file itself and sends the "done" callback, so nothing is announced and how an
 * engine behaves doesn't matter. Where Google Phone has built-in audio for the country, that
 * audio still plays.
 *
 * This applies to every sound file Google Phone makes with text-to-speech, not only the
 * recording announcement.
 *
 * The hooks are on Android's own TextToSpeech class, so no code search is needed.
 *
 * Two of them use private parts of TextToSpeech. They run inside Google Phone, so Google
 * Phone's own target version decides whether Android allows that, not this app's.
 */
@SuppressLint("SoonBlockedPrivateApi")
object TtsFallbackFeature : Feature {
    override val id = FeatureIds.TTS_FALLBACK
    override val phase = Phase.CRITICAL
    override val searchesCode = false

    private const val SYNTHESIZE = "synthesizeToFile"
    private const val INIT_RESULT = "dispatchOnInit"
    private const val LANGUAGE_CHECK = "isLanguageAvailable"

    /** Events already reported once at INFO level in this process. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    override fun discover(finder: Finder): Discovery {
        val tts = TextToSpeech::class.java
        val found = LinkedHashMap<String, Method>()
        val missing = mutableListOf<String>()
        fun add(role: String, lookup: () -> Method) {
            try {
                found[role] = lookup()
            } catch (t: Throwable) {
                HookLog.error(id, "TextToSpeech.$role not found", t)
                missing += role
            }
        }
        add(SYNTHESIZE) {
            tts.getMethod(SYNTHESIZE, CharSequence::class.java, Bundle::class.java, File::class.java, String::class.java)
        }
        add(INIT_RESULT) { tts.getDeclaredMethod(INIT_RESULT, Int::class.javaPrimitiveType) }
        add(LANGUAGE_CHECK) { tts.getMethod(LANGUAGE_CHECK, Locale::class.java) }
        return Discovery(found, if (missing.isEmpty()) "" else "missing: ${missing.joinToString()}")
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        var installed = 0
        for ((role, method) in discovery.targets) {
            try {
                when (role) {
                    SYNTHESIZE -> hookSynthesize(method, hooks)
                    INIT_RESULT -> hookInitResult(method, hooks)
                    LANGUAGE_CHECK -> hookLanguageCheck(method, hooks)
                    else -> continue
                }
                installed++
            } catch (t: Throwable) {
                // One hook failing must not stop the others.
                HookLog.error(id, "can't hook TextToSpeech.$role", t)
            }
        }
        return Installed(installed)
    }

    /** The engine is never asked: a silent file and the "done" callback go back to Google Phone. */
    private fun hookSynthesize(method: Method, hooks: Hooks) {
        hooks.intercept(method) { chain ->
            val file = chain.getArg(2) as? File
            val written = file != null && try {
                SilentWav.writeTo(file)
                true
            } catch (t: Throwable) {
                HookLog.error(id, "can't write the silent announcement file", t)
                false
            }
            if (!written) {
                // Last resort: let the engine make the file, with the text blanked.
                val args = chain.args.toTypedArray()
                args[0] = ""
                return@intercept chain.proceed(args)
            }
            // Google Phone waits for this callback before it starts the recorder.
            notifyDone(chain.thisObject as? TextToSpeech, chain.getArg(3) as? String)
            once("synthesize", "announcement replaced by a silent sound file")
            TextToSpeech.SUCCESS
        }
    }

    private fun notifyDone(tts: TextToSpeech?, utteranceId: String?) {
        if (tts == null) return
        try {
            val field = TextToSpeech::class.java.getDeclaredField("mUtteranceProgressListener")
            field.isAccessible = true
            (field.get(tts) as? UtteranceProgressListener)?.onDone(utteranceId)
        } catch (t: Throwable) {
            HookLog.error(id, "can't send the announcement-done callback", t)
        }
    }

    /** A failed engine start is reported to Google Phone as a successful one. */
    private fun hookInitResult(method: Method, hooks: Hooks) {
        hooks.intercept(method) { chain ->
            val status = chain.getArg(0) as? Int
            if (status == null || status == TextToSpeech.SUCCESS) {
                chain.proceed()
            } else {
                once("init", "text-to-speech engine failed to start ($status); reported success")
                chain.proceed(arrayOf<Any?>(TextToSpeech.SUCCESS))
            }
        }
    }

    /** "Language missing or not supported" becomes "available". */
    private fun hookLanguageCheck(method: Method, hooks: Hooks) {
        hooks.intercept(method) { chain ->
            val result = chain.proceed() as? Int
            if (result == null || result >= TextToSpeech.LANG_AVAILABLE) {
                result
            } else {
                once("language", "text-to-speech language unavailable ($result); reported available")
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            }
        }
    }

    private fun once(event: String, message: String) {
        if (reported.add(event)) HookLog.warn(id, "$message (first time in this process)")
        else HookLog.debug(id) { message }
    }
}
