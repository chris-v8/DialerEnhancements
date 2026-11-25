// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §6.

package io.github.chrisv8.dialerenhancements.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chrisv8.dialerenhancements.BuildConfig
import io.github.chrisv8.dialerenhancements.R
import io.github.chrisv8.dialerenhancements.app.Framework
import io.github.chrisv8.dialerenhancements.app.ModuleSettings
import io.github.chrisv8.dialerenhancements.app.StartupReport
import io.github.chrisv8.dialerenhancements.app.StatusStore
import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import io.github.chrisv8.dialerenhancements.shared.Contract.SetupProblem
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus.State
import io.github.libxposed.service.XposedService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(refreshKey: Int, onOpenPhone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { StatusStore(context) }
    val settings = remember { ModuleSettings(context) }
    val service by Framework.service.collectAsState()

    // Re-read when the activity resumes and whenever Google Phone sends a new report.
    var reportVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reportVersion++ }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        store.hits.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
            store.hits.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val report = remember(refreshKey, reportVersion) { store.loadStartup() }
    val installed = remember(refreshKey) { installedPhone(context) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleCard(service)
            PhoneCard(installed, report, onOpenPhone)
            AutomaticRecordingCard(settings, connected = service != null)
            report?.let { FeaturesCard(it.features, lastHit = { id -> store.lastHit(id) }) }
            SettingsCard(settings, connected = service != null)
            Text(
                stringResource(R.string.notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun ModuleCard(service: XposedService?) {
    SectionCard(R.string.section_module) {
        if (service == null) {
            Text(stringResource(R.string.module_not_connected), color = MaterialTheme.colorScheme.error)
            return@SectionCard
        }
        Text(
            stringResource(
                R.string.module_connected,
                service.frameworkName, service.frameworkVersion, service.apiVersion,
            ),
        )
        val inScope = remember(service) { runCatching { Contract.GOOGLE_PHONE in service.scope }.getOrNull() }
        when (inScope) {
            true -> Secondary(stringResource(R.string.scope_ok))
            false -> Text(stringResource(R.string.scope_missing), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
        Secondary(stringResource(R.string.module_version, BuildConfig.VERSION_NAME))
    }
}

private class InstalledPhone(val versionName: String?, val versionCode: Long)

private fun installedPhone(context: Context): InstalledPhone? = try {
    val info = context.packageManager.getPackageInfo(Contract.GOOGLE_PHONE, 0)
    InstalledPhone(info.versionName, info.longVersionCode)
} catch (_: Exception) {
    null
}

@Composable
private fun PhoneCard(installed: InstalledPhone?, report: StartupReport?, onOpenPhone: () -> Unit) {
    SectionCard(R.string.section_phone) {
        if (installed == null) {
            Text(stringResource(R.string.phone_not_installed), color = MaterialTheme.colorScheme.error)
            return@SectionCard
        }
        Text(stringResource(R.string.phone_installed, installed.versionName.orEmpty()))
        if (report == null) {
            Secondary(stringResource(R.string.report_none))
        } else {
            Secondary(stringResource(R.string.report_last, formatTime(report.reportedAt), report.phoneVersionName.orEmpty()))
            val outdated = report.phoneVersionCode != installed.versionCode ||
                report.moduleVersionCode != BuildConfig.VERSION_CODE
            if (outdated) Text(stringResource(R.string.report_outdated), color = MaterialTheme.colorScheme.error)
            report.setupProblem?.let { Text(stringResource(setupProblemText(it)), color = MaterialTheme.colorScheme.error) }
        }
        Secondary(stringResource(R.string.restart_hint))
        FilledTonalButton(onClick = onOpenPhone) { Text(stringResource(R.string.open_phone)) }
    }
}

@Composable
private fun FeaturesCard(features: List<FeatureStatus>, lastHit: (String) -> Long) {
    SectionCard(R.string.section_features) {
        for (feature in features) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(featureName(feature.id)), Modifier.weight(1f))
                    Text(stateText(feature), color = stateColor(feature.state), style = MaterialTheme.typography.labelLarge)
                }
                if (feature.detail.isNotEmpty()) Secondary(feature.detail)
                val hit = lastHit(feature.id)
                if (hit != 0L) Secondary(stringResource(R.string.feature_last_used, formatTime(hit)))
            }
        }
    }
}

@Composable
private fun AutomaticRecordingCard(settings: ModuleSettings, connected: Boolean) {
    SectionCard(R.string.section_automatic_recording) {
        var recordAll by remember { mutableStateOf(settings.recordAll) }
        SwitchRow(
            title = stringResource(R.string.setting_record_all),
            description = stringResource(R.string.setting_record_all_description),
            checked = recordAll,
            onChange = {
                recordAll = it
                settings.recordAll = it
            },
        )
        if (!connected) Secondary(stringResource(R.string.settings_offline))
    }
}

@Composable
private fun SettingsCard(settings: ModuleSettings, connected: Boolean) {
    SectionCard(R.string.section_settings) {
        var diagnostics by remember { mutableStateOf(settings.diagnostics) }
        SwitchRow(
            title = stringResource(R.string.setting_diagnostics),
            description = stringResource(R.string.setting_diagnostics_description),
            checked = diagnostics,
            onChange = {
                diagnostics = it
                settings.diagnostics = it
            },
        )
        if (!connected) Secondary(stringResource(R.string.settings_offline))
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Secondary(description)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Secondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun stateText(feature: FeatureStatus): String = when (feature.state) {
    State.ACTIVE -> if (feature.targets > 1) pluralStringResource(R.plurals.state_active_count, feature.targets, feature.targets)
    else stringResource(R.string.state_active)
    State.NOT_FOUND -> stringResource(R.string.state_not_found)
    State.FAILED -> stringResource(R.string.state_failed)
    State.SKIPPED -> stringResource(R.string.state_skipped)
}

@Composable
private fun stateColor(state: State): Color = when (state) {
    State.ACTIVE -> MaterialTheme.colorScheme.primary
    State.NOT_FOUND, State.FAILED -> MaterialTheme.colorScheme.error
    State.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
private fun featureName(id: String): Int = when (id) {
    FeatureIds.CAN_RECORD -> R.string.feature_can_record
    FeatureIds.GEOFENCE -> R.string.feature_geofence
    FeatureIds.COUNTRY -> R.string.feature_country
    FeatureIds.ANNOUNCEMENT_LOCALE -> R.string.feature_announcement_locale
    FeatureIds.TTS_FALLBACK -> R.string.feature_tts_fallback
    FeatureIds.SETUP_CHECK -> R.string.feature_setup_check
    FeatureIds.RECORD_ALL -> R.string.feature_record_all
    else -> R.string.feature_unknown
}

@StringRes
private fun setupProblemText(problem: String): Int = when (problem) {
    SetupProblem.NO_CAPTURE_PERMISSION -> R.string.setup_problem_permission
    SetupProblem.DOWNLOADABLE_BUILD -> R.string.setup_problem_downloadable
    else -> R.string.setup_problem_unknown
}

private fun formatTime(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
