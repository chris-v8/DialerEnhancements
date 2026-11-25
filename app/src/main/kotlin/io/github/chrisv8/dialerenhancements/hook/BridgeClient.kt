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

/**
 * Google Phone's side of the channel to the module app. Only used off the main thread,
 * never on a hot path. If the app can't be reached the report is dropped; nothing in
 * Google Phone depends on it.
 */
object BridgeClient {
    private val uri: Uri = Uri.parse(Contract.Bridge.URI)

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
