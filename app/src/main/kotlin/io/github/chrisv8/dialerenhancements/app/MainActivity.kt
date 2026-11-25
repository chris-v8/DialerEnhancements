// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §6.

package io.github.chrisv8.dialerenhancements.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.github.chrisv8.dialerenhancements.app.ui.AppTheme
import io.github.chrisv8.dialerenhancements.app.ui.StatusScreen
import io.github.chrisv8.dialerenhancements.shared.Contract

class MainActivity : ComponentActivity() {
    /** Bumped on every resume, so the screen re-reads what may have changed meanwhile. */
    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                StatusScreen(refreshKey = resumed.intValue, onOpenPhone = ::openGooglePhone)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }

    private fun openGooglePhone() {
        packageManager.getLaunchIntentForPackage(Contract.GOOGLE_PHONE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(::startActivity)
    }
}
