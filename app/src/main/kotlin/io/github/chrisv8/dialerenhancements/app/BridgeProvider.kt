// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F12 and REWRITE_PLAN.md §5.3.

package io.github.chrisv8.dialerenhancements.app

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import io.github.chrisv8.dialerenhancements.shared.Contract

/**
 * The app's end of the channel from Google Phone. Only this app and Google Phone may call it;
 * only [call] is used, the table operations are refused.
 */
class BridgeProvider : ContentProvider() {

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        checkCaller()
        when (method) {
            Contract.Bridge.METHOD_REPORT_STARTUP -> StatusStore(appContext()).saveStartup(extras ?: Bundle.EMPTY)
            else -> throw IllegalArgumentException("unknown method")
        }
        return null
    }

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val packages = appContext().packageManager.getPackagesForUid(uid).orEmpty()
        if (Contract.GOOGLE_PHONE !in packages) throw SecurityException("caller not allowed")
    }

    private fun appContext() = checkNotNull(context) { "provider not attached" }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
        throw UnsupportedOperationException()

    override fun insert(uri: Uri, values: ContentValues?) = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    override fun getType(uri: Uri): String? = null
}
