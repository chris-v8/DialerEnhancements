// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §11 (S18).

package io.github.chrisv8.dialerenhancements.hook

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Catches Google Phone's Application object as Android creates it, so the background
 * thread can talk to the module app later. No hidden Android APIs needed.
 */
object AppContext {
    private val ready = CountDownLatch(1)

    @Volatile
    var application: Application? = null
        private set

    fun watch(hooks: Hooks) {
        val onCreate = Instrumentation::class.java.getMethod("callApplicationOnCreate", Application::class.java)
        hooks.intercept(onCreate) { chain ->
            val app = chain.getArg(0) as? Application
            if (app != null && application == null) {
                application = app
                ready.countDown()
            }
            chain.proceed()
        }
    }

    /** The application context, waiting up to [timeoutMs] for it; null if it never came. */
    fun await(timeoutMs: Long): Context? {
        ready.await(timeoutMs, TimeUnit.MILLISECONDS)
        return application
    }
}
