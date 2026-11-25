// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F6 and REWRITE_PLAN.md S13.

package io.github.chrisv8.dialerenhancements.hook.features

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import io.github.chrisv8.dialerenhancements.shared.Contract.SetupProblem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SPEC F6: tells the user, once per Google Phone start, when this Google Phone can't record
 * at all whatever the module does. The result also goes to the module app's status page.
 */
object SetupCheck {
    private class Result(val problem: String?)

    @Volatile
    private var result: Result? = null

    /** The problem found (a Contract.SetupProblem value), or null if the setup looks fine. Checked once. */
    fun problem(context: Context): String? {
        result?.let { return it.problem }
        val problem = try {
            check(context)
        } catch (t: Throwable) {
            HookLog.warn(FeatureIds.SETUP_CHECK, "can't check Google Phone's setup", t)
            null
        }
        result = Result(problem)
        return problem
    }

    private fun check(context: Context): String? {
        // Google Phone's recorder needs this; only system (privileged) installs get it.
        if (context.checkSelfPermission(CAPTURE_AUDIO_OUTPUT) != PackageManager.PERMISSION_GRANTED) {
            HookLog.warn(FeatureIds.SETUP_CHECK, "Google Phone lacks the permission to capture call audio")
            return SetupProblem.NO_CAPTURE_PERMISSION
        }
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        if (versionName.endsWith("downloadable")) {
            HookLog.warn(FeatureIds.SETUP_CHECK, "Google Phone $versionName is a build that can't record")
            return SetupProblem.DOWNLOADABLE_BUILD
        }
        return null
    }

    fun message(problem: String): String = when (problem) {
        SetupProblem.NO_CAPTURE_PERMISSION ->
            "Dialer Enhancements: Phone can't record calls on this device. " +
                "It needs to be installed as a system app to capture call audio."
        SetupProblem.DOWNLOADABLE_BUILD ->
            "Dialer Enhancements: this Phone build can't record calls. Install the full Phone app."
        else -> "Dialer Enhancements: Phone can't record calls on this device."
    }

    private const val CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT"
}

/** Shows the setup check's result as a toast on the first screen Google Phone opens. */
object SetupCheckFeature : Feature {
    override val id = FeatureIds.SETUP_CHECK
    override val phase = Phase.DEFERRED
    override val searchesCode = false

    private val shown = AtomicBoolean(false)

    override fun discover(finder: Finder) =
        Discovery(mapOf("onResume" to Activity::class.java.getDeclaredMethod("onResume")))

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        hooks.intercept(discovery.targets.getValue("onResume")) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && shown.compareAndSet(false, true)) {
                SetupCheck.problem(activity)?.let {
                    Toast.makeText(activity, SetupCheck.message(it), Toast.LENGTH_LONG).show()
                }
            }
            result
        }
        return Installed(1)
    }
}
