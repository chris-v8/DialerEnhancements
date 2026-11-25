// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §5 and §8.

package io.github.chrisv8.dialerenhancements.app

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The connection from the module app to LSPosed. LSPosed connects to the app by itself
 * when the module is enabled; until then (or if it never is) [service] stays null.
 */
object Framework {
    private val current = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = current.asStateFlow()

    private var listening = false

    @Synchronized
    fun listen(onConnected: (XposedService) -> Unit) {
        if (listening) return
        listening = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                current.value = service
                onConnected(service)
            }

            override fun onServiceDied(service: XposedService) {
                if (current.value === service) current.value = null
            }
        })
    }
}
