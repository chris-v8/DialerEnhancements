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
        val selected by lazy { SelectedNumbersStore(appContext()) }
        when (method) {
            Contract.Bridge.METHOD_REPORT_STARTUP -> StatusStore(appContext()).saveStartup(extras ?: Bundle.EMPTY)
            Contract.Bridge.METHOD_REPORT_HIT -> if (arg in Contract.FeatureIds.all) StatusStore(appContext()).saveHit(arg!!)
            Contract.Bridge.METHOD_PUT_SNAPSHOT ->
                extras?.getStringArrayList(Contract.Bridge.KEY_NUMBERS)?.let { selected.putSnapshot(it.filterNotNull()) }
            Contract.Bridge.METHOD_PEEK_QUEUE -> return selected.queue().let { queue ->
                Bundle().apply {
                    putString(Contract.Bridge.KEY_TOKEN, queue.token)
                    putStringArrayList(Contract.Bridge.KEY_ADD_URIS, ArrayList(queue.addUris))
                    putStringArrayList(Contract.Bridge.KEY_NUMBERS, ArrayList(queue.removeNumbers))
                }
            }
            Contract.Bridge.METHOD_ACK_QUEUE -> arg?.let(selected::acknowledge)
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
