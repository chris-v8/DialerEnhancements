// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.4.

package io.github.chrisv8.dialerenhancements.hook

import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * The few hook operations features use, on top of the Xposed framework.
 *
 * Every hook is "protective": if the module's own hook code throws, the framework runs
 * Google Phone's original method as if the hook weren't there.
 */
class Hooks(private val xposed: XposedInterface) {

    /** Runs [hooker] around [target]. Call `chain.proceed()` inside to run Google's code. */
    fun intercept(target: Executable, hooker: Hooker): HookHandle =
        xposed.hook(target).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(hooker)

    /** Google's method never runs; every call returns what [value] gives. */
    fun replace(target: Method, value: () -> Any?): HookHandle = intercept(target) { value() }

    /** The module's own APK, for its strings and icons inside Google Phone. */
    val moduleApplicationInfo: ApplicationInfo get() = xposed.moduleApplicationInfo

    /** Calls Google's original method, skipping every hook on it (including ours). */
    fun callOriginal(target: Method, thisObject: Any?, vararg args: Any?): Any? =
        xposed.getInvoker(target).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(thisObject, *args)
}
