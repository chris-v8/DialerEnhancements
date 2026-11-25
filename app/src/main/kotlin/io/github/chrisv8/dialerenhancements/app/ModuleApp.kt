// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements.

package io.github.chrisv8.dialerenhancements.app

import android.app.Application

class ModuleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = ModuleSettings(this)
        Framework.listen(settings::copyTo)
    }
}
