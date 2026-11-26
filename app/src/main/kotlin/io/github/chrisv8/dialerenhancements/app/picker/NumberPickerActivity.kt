// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10.3, F10.5 and F10.8.
// The picker's behaviour is Chris's design from CallRecording; this is a new implementation.

package io.github.chrisv8.dialerenhancements.app.picker

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.chrisv8.dialerenhancements.R
import io.github.chrisv8.dialerenhancements.app.SelectedNumbersStore
import io.github.chrisv8.dialerenhancements.app.ui.AppTheme
import io.github.chrisv8.dialerenhancements.shared.Contract

/**
 * The multi-select list of the user's phone numbers.
 *
 * - Opened by Google Phone's Add button (for a result): the choice goes straight back to
 *   Google Phone, which applies it.
 * - Opened from the module app: the choice is queued and applied the next time Google's
 *   Selected-numbers screen opens.
 *
 * Any other app asking for a result is refused.
 */
class NumberPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caller = callingActivity?.packageName
        if (caller != null && caller != Contract.GOOGLE_PHONE && caller != packageName) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val forGooglePhone = caller != null
        val existing: Set<String>? = if (forGooglePhone) {
            intent.getStringArrayListExtra(Contract.Picker.EXTRA_EXISTING)?.filterNotNull()?.toSet()
        } else {
            SelectedNumbersStore(this).snapshot()
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                PickerScreen(
                    existing = existing,
                    forGooglePhone = forGooglePhone,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onSave = ::save,
                )
            }
        }
    }

    private fun save(addIds: List<Long>, removeNumbers: List<String>, forGooglePhone: Boolean) {
        val uris = addIds.map { ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, it) }
        if (forGooglePhone) returnToGooglePhone(uris, removeNumbers) else queueForGooglePhone(uris, removeNumbers)
    }

    /** SPEC F10.3: the three-extra result; its data is the first addition, which Google handles even without the hook. */
    private fun returnToGooglePhone(uris: List<Uri>, removeNumbers: List<String>) {
        val result = Intent().apply {
            data = uris.firstOrNull()
            putParcelableArrayListExtra(Contract.Picker.EXTRA_ADD, ArrayList(uris))
            putStringArrayListExtra(Contract.Picker.EXTRA_REMOVE, ArrayList(removeNumbers))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    /** SPEC F10.5: queue the changes and send the user to Google Phone to apply them. */
    private fun queueForGooglePhone(uris: List<Uri>, removeNumbers: List<String>) {
        SelectedNumbersStore(this).enqueue(uris.map(Uri::toString), removeNumbers)
        val launch = packageManager.getLaunchIntentForPackage(Contract.GOOGLE_PHONE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = launch != null && runCatching { startActivity(launch) }.isSuccess
        val message = if (opened) R.string.picker_queued else R.string.picker_queued_open_failed
        Toast.makeText(this, getString(message, uris.size, removeNumbers.size), Toast.LENGTH_LONG).show()
        finish()
    }
}
