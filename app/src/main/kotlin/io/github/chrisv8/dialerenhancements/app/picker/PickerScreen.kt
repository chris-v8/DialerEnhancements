// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10.8.

package io.github.chrisv8.dialerenhancements.app.picker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chrisv8.dialerenhancements.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** One phone number of one contact (a contacts "data row"). */
data class PhoneRow(val id: Long, val name: String, val number: String, val normalized: String?, val label: String)

private sealed interface Loaded {
    data class Rows(val rows: List<PhoneRow>) : Loaded
    data object Failed : Loaded
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(
    existing: Set<String>?,
    forGooglePhone: Boolean,
    onCancel: () -> Unit,
    onSave: (addIds: List<Long>, removeNumbers: List<String>, forGooglePhone: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.READ_CONTACTS) }

    val loaded by produceState<Loaded?>(null, granted) {
        if (granted) value = withContext(Dispatchers.IO) { loadPhones(context) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.picker_title)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !granted -> Message(stringResource(R.string.picker_needs_contacts)) {
                    Button(onClick = { askPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text(stringResource(R.string.picker_allow_contacts))
                    }
                }
                loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loaded is Loaded.Failed -> Message(stringResource(R.string.picker_needs_contacts)) {}
                else -> PickerList(
                    rows = (loaded as Loaded.Rows).rows,
                    existing = existing,
                    forGooglePhone = forGooglePhone,
                    onCancel = onCancel,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun PickerList(
    rows: List<PhoneRow>,
    existing: Set<String>?,
    forGooglePhone: Boolean,
    onCancel: () -> Unit,
    onSave: (List<Long>, List<String>, Boolean) -> Unit,
) {
    // Which stored number each row stands for, if it is already on Google's list.
    val storedOf = remember(rows, existing) {
        val match = PhoneMatch(existing.orEmpty())
        rows.mapNotNull { row -> match.storedFor(row.number, row.normalized)?.let { row.id to it } }.toMap()
    }
    val onList = remember(storedOf) { storedOf.values.toSet() }
    var keep by remember(onList) { mutableStateOf(onList) }
    var added by remember { mutableStateOf(emptySet<Long>()) }
    var query by remember { mutableStateOf("") }

    val removes = (onList - keep).toList()
    val adds = added.toList()
    val visible = remember(rows, query) { rows.filter { it.matches(query) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (existing == null) {
            Text(
                stringResource(if (forGooglePhone) R.string.picker_existing_unreadable else R.string.picker_existing_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (visible.isEmpty()) {
            Text(stringResource(R.string.picker_no_numbers), Modifier.padding(16.dp).weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.id }) { row ->
                    val stored = storedOf[row.id]
                    val checked = if (stored != null) stored in keep else row.id in added
                    val toggle = {
                        if (stored != null) {
                            // Every row for the same stored number follows the same state.
                            keep = if (stored in keep) keep - stored else keep + stored
                        } else {
                            added = if (row.id in added) added - row.id else added + row.id
                        }
                    }
                    PhoneRowItem(row, checked, stored, toggle)
                    HorizontalDivider()
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.picker_cancel)) }
                Button(
                    onClick = { onSave(adds, removes, forGooglePhone) },
                    enabled = adds.isNotEmpty() || removes.isNotEmpty(),
                ) { Text(stringResource(R.string.picker_save, adds.size, removes.size)) }
            }
        }
    }
}

@Composable
private fun PhoneRowItem(row: PhoneRow, checked: Boolean, stored: String?, toggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { toggle() })
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(row.number, row.label).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stored != null) {
                Text(
                    stringResource(if (checked) R.string.picker_on_list else R.string.picker_will_remove),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text)
        action()
    }
}

/** Name contains the query (any case), or the number contains the query's digits. */
private fun PhoneRow.matches(query: String): Boolean {
    if (query.isBlank()) return true
    if (name.contains(query.trim(), ignoreCase = true)) return true
    val digits = PhoneMatch.digits(query)
    return digits.isNotEmpty() && PhoneMatch.digits(number).contains(digits)
}

/** Every phone number in the user's contacts, sorted by name the way the user's language sorts. */
private fun loadPhones(context: Context): Loaded = try {
    val columns = arrayOf(
        Phone._ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.NORMALIZED_NUMBER,
    )
    val rows = mutableListOf<PhoneRow>()
    context.contentResolver.query(Phone.CONTENT_URI, columns, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val number = c.getString(2)?.trim().orEmpty()
            if (number.isEmpty()) continue
            val label = Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
            rows += PhoneRow(
                id = c.getLong(0),
                name = c.getString(1)?.takeIf { it.isNotBlank() } ?: number,
                number = number,
                normalized = c.getString(5),
                label = label,
            )
        }
    }
    val collator = Collator.getInstance()
    Loaded.Rows(rows.sortedWith { a, b -> collator.compare(a.name, b.name) })
} catch (_: Exception) {
    Loaded.Failed
}
