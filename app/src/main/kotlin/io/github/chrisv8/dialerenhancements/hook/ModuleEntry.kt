// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md §1.1–1.2.

package io.github.chrisv8.dialerenhancements.hook

import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Where LSPosed enters the module (listed in META-INF/xposed/java_init.list).
 * Only Google Phone's main process is touched; everything else is ignored.
 */
class ModuleEntry : XposedModule() {
    private var processName: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        // No tag of our own: Vector then saves the lines in the module log shown in the manager.
        HookLog.sink = HookLog.Sink { priority, message, error ->
            if (error == null) log(priority, null, message) else log(priority, null, message, error)
        }
    }

    /** Called before Google Phone's Application is created, so none of its code has run yet. */
    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != Contract.GOOGLE_PHONE || !param.isFirstPackage) return
        if (processName != Contract.GOOGLE_PHONE) {
            HookLog.debug("start") { "secondary process, nothing to do" }
            return
        }
        try {
            Startup(this, param).run()
        } catch (t: Throwable) {
            // Never let anything reach Google Phone.
            HookLog.error("start", "startup failed; Google Phone keeps its normal behaviour", t)
        }
    }
}
