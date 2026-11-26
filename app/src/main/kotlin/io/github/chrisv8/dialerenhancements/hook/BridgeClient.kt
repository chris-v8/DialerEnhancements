// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F12 and REWRITE_PLAN.md §5.3.

package io.github.chrisv8.dialerenhancements.hook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.chrisv8.dialerenhancements.BuildConfig
import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus
import java.util.concurrent.Executors

/**
 * Google Phone's side of the channel to the module app. Only used off the main thread,
 * never on a hot path. If the app can't be reached the report is dropped; nothing in
 * Google Phone depends on it.
 */
object BridgeClient {
    private val uri: Uri = Uri.parse(Contract.Bridge.URI)

    /** Reports from hooks are sent from here, never from the thread Google Phone called us on. */
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "DialerEnhancements-bridge").apply { isDaemon = true } }

    /** Tells the app that [featureId] changed something; the status page shows the time. */
    fun reportHit(featureId: String) {
        sender.execute {
            val context = AppContext.application ?: return@execute
            try {
                context.contentResolver.call(uri, Contract.Bridge.METHOD_REPORT_HIT, featureId, null)
            } catch (t: Throwable) {
                HookLog.debug("bridge") { "can't report a hit (${t.javaClass.simpleName})" }
            }
        }
    }

    /** Runs [block] on the bridge thread, off whatever thread Google Phone called us on. */
    fun inBackground(block: () -> Unit) = sender.execute {
        try {
            block()
        } catch (t: Throwable) {
            HookLog.warn("bridge", "background task failed", t)
        }
    }

    /** Sends Google's current Selected-numbers list to the app (for its checkmarks). Never blocks. */
    fun putSnapshot(numbers: List<String>) = inBackground {
        val context = AppContext.application ?: return@inBackground
        call(context, Contract.Bridge.METHOD_PUT_SNAPSHOT, null, Bundle().apply {
            putStringArrayList(Contract.Bridge.KEY_NUMBERS, ArrayList(numbers))
        })
    }

    /** Selected-numbers changes queued in the app. Blocking; call it from [inBackground]. */
    class Queue(val addUris: List<Uri>, val removeNumbers: List<String>, val token: String) {
        val isEmpty get() = addUris.isEmpty() && removeNumbers.isEmpty()
    }

    fun peekQueue(context: Context): Queue? {
        val reply = call(context, Contract.Bridge.METHOD_PEEK_QUEUE, null, null) ?: return null
        val token = reply.getString(Contract.Bridge.KEY_TOKEN) ?: return null
        return Queue(
            reply.getStringArrayList(Contract.Bridge.KEY_ADD_URIS).orEmpty().map(Uri::parse),
            reply.getStringArrayList(Contract.Bridge.KEY_NUMBERS).orEmpty(),
            token,
        )
    }

    /** Tells the app the queue read with [token] has been applied. Blocking. */
    fun ackQueue(context: Context, token: String) {
        call(context, Contract.Bridge.METHOD_ACK_QUEUE, token, null)
    }

    private fun call(context: Context, method: String, arg: String?, extras: Bundle?): Bundle? = try {
        context.contentResolver.call(uri, method, arg, extras)
    } catch (t: Throwable) {
        HookLog.warn("bridge", "can't reach the module app for $method (${t.javaClass.simpleName})")
        null
    }

    fun reportStartup(context: Context, statuses: List<FeatureStatus>, setupProblem: String?) {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val extras = Bundle().apply {
                putString(Contract.Bridge.KEY_PHONE_VERSION_NAME, info.versionName)
                putLong(Contract.Bridge.KEY_PHONE_VERSION_CODE, info.longVersionCode)
                putInt(Contract.Bridge.KEY_MODULE_VERSION_CODE, BuildConfig.VERSION_CODE)
                putStringArrayList(Contract.Bridge.KEY_FEATURES, ArrayList(statuses.map { it.encode() }))
                putString(Contract.Bridge.KEY_SETUP_PROBLEM, setupProblem)
            }
            context.contentResolver.call(uri, Contract.Bridge.METHOD_REPORT_STARTUP, null, extras)
            HookLog.info("bridge", "status sent to the module app")
        } catch (t: Throwable) {
            HookLog.warn("bridge", "can't reach the module app; its status page won't update (${t.javaClass.simpleName})")
            HookLog.debug("bridge") { t.stackTraceToString() }
        }
    }
}
