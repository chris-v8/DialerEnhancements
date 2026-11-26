// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements (new in 1.1.0, not part of the CallRecording spec).
// Facts about Google Phone 175 (its string names) were found with aapt2/dexdump; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.features

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
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
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Putting a call on hold while it is being recorded:
 * - Google Phone asks "Hold and stop recording?" first. The module answers "Hold" for you,
 *   without showing the question. Google's own Hold action runs (the recording is saved).
 * - When that call is taken off hold, the module starts recording again by pressing Google's
 *   own Record button on the call screen.
 *
 * Only Android's public classes are hooked; Google's dialog and button are recognised by their
 * texts, looked up by Google's own string names so they work in every language.
 */
object HoldRecordingFeature : Feature {
    override val id = FeatureIds.HOLD_RECORDING
    override val phase = Phase.CRITICAL // the call service can start right after Google Phone does
    override val searchesCode = false

    private const val HOLD_DIALOG_TITLE = "call_recording_hold_warning_dialog_title"
    private const val RECORD_LABEL = "tidepods_incall_label_record_unchecked"
    private const val STOP_LABEL = "tidepods_incall_label_record_checked"

    /** Give the call screen time to show the call as active again before pressing Record. */
    private const val RESUME_DELAY_MS = 800L

    /** How long to keep trying to resume (for example while the call screen isn't open yet). */
    private const val RESUME_ATTEMPTS = 10

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var callService: WeakReference<InCallService>? = null

    @Volatile
    private var currentActivity: WeakReference<Activity>? = null

    /** Calls that were being recorded when they were put on hold. */
    private val resumeAfterHold = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Call, Boolean>()))

    private val watched = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Call, Boolean>()))

    override fun discover(finder: Finder) = Discovery(
        mapOf(
            "dialogStart" to Dialog::class.java.getDeclaredMethod("onStart"),
            "serviceBind" to InCallService::class.java.getMethod("onBind", Intent::class.java),
            "activityResume" to Activity::class.java.getDeclaredMethod("onResume"),
            "activityPause" to Activity::class.java.getDeclaredMethod("onPause"),
        ),
    )

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        hooks.intercept(discovery.targets.getValue("serviceBind")) { chain ->
            (chain.thisObject as? InCallService)?.let { callService = WeakReference(it) }
            chain.proceed()
        }
        hooks.intercept(discovery.targets.getValue("activityResume")) { chain ->
            val result = chain.proceed()
            (chain.thisObject as? Activity)?.let { currentActivity = WeakReference(it) }
            result
        }
        hooks.intercept(discovery.targets.getValue("activityPause")) { chain ->
            if (currentActivity?.get() === chain.thisObject) currentActivity = null
            chain.proceed()
        }
        hooks.intercept(discovery.targets.getValue("dialogStart")) { chain ->
            val result = chain.proceed()
            (chain.thisObject as? Dialog)?.let(::onDialogStart)
            result
        }
        return Installed(4)
    }

    // ---- The "Hold and stop recording?" question ----

    private fun onDialogStart(dialog: Dialog) {
        val window = dialog.window ?: return
        val title = stringNamed(dialog.context, HOLD_DIALOG_TITLE) ?: return
        val decor = window.decorView
        if (findText(decor, title) == null) return
        val hold = dialog.findViewById<View>(android.R.id.button1) ?: return // keep Google's question

        // Invisible, then pressed once Google has wired up its buttons (that happens right after showing).
        window.attributes = window.attributes.apply { alpha = 0f }
        window.setDimAmount(0f)
        rememberRecordedCall()
        decor.post {
            decor.post {
                hold.performClick()
                HookLog.info(id, "answered \"Hold\" to Google's hold-and-stop-recording question")
                BridgeClient.reportHit(id)
            }
        }
    }

    /** The active call is the one going on hold; it was being recorded (that's why Google asked). */
    private fun rememberRecordedCall() {
        val call = callService?.get()?.calls?.firstOrNull { stateOf(it) == Call.STATE_ACTIVE }
        if (call == null) {
            HookLog.warn(id, "can't tell which call is being held; recording won't restart by itself")
            return
        }
        resumeAfterHold += call
        if (watched.add(call)) call.registerCallback(stateWatcher, main)
    }

    // ---- Back from hold: record again ----

    private val stateWatcher = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> if (resumeAfterHold.remove(call)) {
                    main.postDelayed({ pressRecord(call, RESUME_ATTEMPTS) }, RESUME_DELAY_MS)
                }
                Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> {
                    resumeAfterHold -= call
                    watched -= call
                    call.unregisterCallback(this)
                }
            }
        }
    }

    private fun pressRecord(call: Call, attemptsLeft: Int) {
        if (stateOf(call) != Call.STATE_ACTIVE) return // held again or ended meanwhile
        val activity = currentActivity?.get()
        val root = activity?.window?.decorView
        val record = stringNamed(activity, RECORD_LABEL)
        val stop = stringNamed(activity, STOP_LABEL)
        if (root != null && record != null) {
            if (stop != null && findText(root, stop) != null) return // already recording
            val button = findText(root, record)?.let(::clickableAncestor)
            if (button != null) {
                button.performClick()
                HookLog.info(id, "recording restarted after hold")
                return
            }
        }
        if (attemptsLeft > 1) {
            main.postDelayed({ pressRecord(call, attemptsLeft - 1) }, RESUME_DELAY_MS)
        } else {
            HookLog.warn(id, "couldn't find the Record button to restart recording after hold")
        }
    }

    // ---- Helpers ----

    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else @Suppress("DEPRECATION") call.state

    private fun stringNamed(context: Context?, name: String): String? {
        context ?: return null
        val res = context.resources.getIdentifier(name, "string", context.packageName)
        return if (res == 0) null else context.getString(res)
    }

    /** The first visible view showing [text] (as its text or its spoken description). */
    private fun findText(view: View, text: String): View? {
        if (view.visibility != View.VISIBLE) return null
        if ((view as? TextView)?.text?.toString() == text || view.contentDescription?.toString() == text) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findText(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun clickableAncestor(view: View): View? {
        var current: View? = view
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent as? View
        }
        return null
    }
}
