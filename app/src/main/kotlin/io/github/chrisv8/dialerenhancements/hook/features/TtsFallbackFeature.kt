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
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.util.SilentWav
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * SPEC F5: lets recording start on phones where text-to-speech is missing or broken.
 *
 * Before recording, Google Phone writes its announcement to a file with text-to-speech and
 * waits for the "done" callback. Without a working engine that never happens and recording
 * fails. These hooks hand Google Phone a silent sound file and the callback instead.
 *
 * The announcement text is always blanked (the user's decision), so any announcement made
 * with text-to-speech is silent. Google's built-in audio (see AnnouncementLocaleFeature) is
 * not affected.
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

    override fun install(discovery: Discovery, hooks: Hooks): Int {
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
        return installed
    }

    /** Blank text in; on failure, a silent file plus the "done" callback out. */
    private fun hookSynthesize(method: Method, hooks: Hooks) {
        hooks.intercept(method) { chain ->
            val args = chain.args.toTypedArray()
            args[0] = ""
            val result = chain.proceed(args)
            if (result == TextToSpeech.SUCCESS) return@intercept result

            var answer = result
            val file = args[2] as? File
            if (file != null) {
                try {
                    SilentWav.writeTo(file)
                    answer = TextToSpeech.SUCCESS
                } catch (t: Throwable) {
                    HookLog.error(id, "can't write the silent announcement file", t)
                }
            }
            // Google Phone waits for this callback before it starts the recorder.
            notifyDone(chain.thisObject as? TextToSpeech, args[3] as? String)
            once("synthesize", "text-to-speech failed ($result); used a silent announcement instead")
            answer
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
