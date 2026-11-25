// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §6.

package io.github.chrisv8.dialerenhancements.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import io.github.chrisv8.dialerenhancements.shared.Contract.Bridge
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus

/** What Google Phone reported the last time it started with the module. */
data class StartupReport(
    val reportedAt: Long,
    val phoneVersionName: String?,
    val phoneVersionCode: Long,
    val moduleVersionCode: Int,
    val features: List<FeatureStatus>,
    val setupProblem: String?,
)

/** Keeps the latest [StartupReport] in the app's private storage. */
class StatusStore(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Kept apart from [prefs], which is replaced on every start of Google Phone. */
    val hits: SharedPreferences = context.applicationContext.getSharedPreferences(HITS_FILE, Context.MODE_PRIVATE)

    /** Remembers when [featureId] last changed something in Google Phone. */
    fun saveHit(featureId: String) {
        hits.edit().putLong(featureId, System.currentTimeMillis()).apply()
    }

    /** Last time [featureId] changed something, or 0 if never. */
    fun lastHit(featureId: String): Long = hits.getLong(featureId, 0)

    fun saveStartup(extras: Bundle) {
        prefs.edit()
            .clear()
            .putLong(REPORTED_AT, System.currentTimeMillis())
            .putString(PHONE_VERSION_NAME, extras.getString(Bridge.KEY_PHONE_VERSION_NAME))
            .putLong(PHONE_VERSION_CODE, extras.getLong(Bridge.KEY_PHONE_VERSION_CODE))
            .putInt(MODULE_VERSION_CODE, extras.getInt(Bridge.KEY_MODULE_VERSION_CODE))
            .putString(FEATURES, extras.getStringArrayList(Bridge.KEY_FEATURES).orEmpty().joinToString("\n"))
            .putString(SETUP_PROBLEM, extras.getString(Bridge.KEY_SETUP_PROBLEM))
            .apply()
    }

    fun loadStartup(): StartupReport? {
        val at = prefs.getLong(REPORTED_AT, 0)
        if (at == 0L) return null
        return StartupReport(
            reportedAt = at,
            phoneVersionName = prefs.getString(PHONE_VERSION_NAME, null),
            phoneVersionCode = prefs.getLong(PHONE_VERSION_CODE, 0),
            moduleVersionCode = prefs.getInt(MODULE_VERSION_CODE, 0),
            features = prefs.getString(FEATURES, "").orEmpty().lines().mapNotNull(FeatureStatus::decode),
            setupProblem = prefs.getString(SETUP_PROBLEM, null),
        )
    }

    private companion object {
        const val FILE = "startup_report"
        const val HITS_FILE = "feature_hits"
        const val REPORTED_AT = "reportedAt"
        const val PHONE_VERSION_NAME = "phoneVersionName"
        const val PHONE_VERSION_CODE = "phoneVersionCode"
        const val MODULE_VERSION_CODE = "moduleVersionCode"
        const val FEATURES = "features"
        const val SETUP_PROBLEM = "setupProblem"
    }
}
