// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md §1.2–1.3 and
// docs/design/REWRITE_PLAN.md §3. The critical/deferred split is Chris's design from CallRecording.

package io.github.chrisv8.dialerenhancements.hook

import io.github.chrisv8.dialerenhancements.hook.features.SetupCheck
import io.github.chrisv8.dialerenhancements.hook.find.DexKitSearch
import io.github.chrisv8.dialerenhancements.hook.find.DiscoveryCache
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus
import io.github.chrisv8.dialerenhancements.shared.FeatureStatus.State
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Sets up every feature in two phases (SPEC §1.2):
 *
 * - **Critical**: synchronously, before Google Phone's own code can run. This is what keeps
 *   Google's daily geofence job from switching auto-recording off right after a cold start.
 * - **Deferred**: on one background thread, for everything that can wait.
 *
 * Each feature runs inside a guard: whatever it does wrong is logged and reported,
 * and the next feature carries on.
 */
class Startup(private val module: XposedModule, private val param: PackageReadyParam) {
    private val hooks = Hooks(module)
    private val classLoader = param.classLoader
    private val statuses = Collections.synchronizedList(mutableListOf<FeatureStatus>())

    fun run() {
        val started = System.nanoTime()
        HookLog.info(SCOPE, "entered Google Phone, critical phase starts")
        HookSettings.connect(module)

        try {
            AppContext.watch(hooks)
        } catch (t: Throwable) {
            HookLog.warn(SCOPE, "can't watch for the application context; no status report this time", t)
        }

        val cache = DiscoveryCache.load(cacheFile(), cacheKey())
        val finder = Finder {
            DexKitSearch.open(param.applicationInfo.sourceDir, classLoader, module.moduleApplicationInfo.nativeLibraryDir)
        }

        for (feature in FeatureRegistry.features.filter { it.phase == Phase.CRITICAL }) {
            statuses += runGuarded(feature, finder, cache)
        }
        HookLog.info(SCOPE, "critical phase done in ${elapsedMs(started)} ms (code search ${if (finder.wasOpened) "used" else "not needed"})")

        thread(name = "DialerEnhancements", isDaemon = true) { deferred(finder, cache) }
    }

    private fun deferred(finder: Finder, cache: DiscoveryCache) {
        val started = System.nanoTime()
        try {
            for (feature in FeatureRegistry.features.filter { it.phase == Phase.DEFERRED }) {
                statuses += runGuarded(feature, finder, cache)
            }
        } finally {
            try {
                finder.close()
            } catch (t: Throwable) {
                HookLog.warn(SCOPE, "closing the code search failed", t)
            }
        }
        cache.save()?.let { HookLog.warn(SCOPE, "can't save the discovery cache; the next start searches again", it) }
        HookLog.info(SCOPE, "deferred phase done in ${elapsedMs(started)} ms")

        val context = AppContext.await(CONTEXT_WAIT_MS)
        if (context == null) {
            HookLog.warn(SCOPE, "Google Phone didn't finish starting; no status report this time")
            return
        }
        BridgeClient.reportStartup(context, statuses.toList(), SetupCheck.problem(context))
    }

    /** Discovers (from the cache if possible) and installs one feature, never throwing. */
    private fun runGuarded(feature: Feature, finder: Finder, cache: DiscoveryCache): FeatureStatus {
        val started = System.nanoTime()
        val status = try {
            val cached = if (feature.searchesCode) cache.get(feature.id, classLoader) else null
            val discovery = cached ?: feature.discover(finder).also {
                if (feature.searchesCode) cache.put(feature.id, it)
            }
            for ((role, method) in discovery.targets) {
                HookLog.debug(feature.id) { "$role -> ${method.declaringClass.name}.${method.name}" }
            }
            if (discovery.targets.isEmpty()) {
                FeatureStatus(feature.id, State.NOT_FOUND, detail = discovery.note, cached = cached != null)
            } else {
                val installed = feature.install(discovery, hooks)
                val state = if (installed > 0) State.ACTIVE else State.NOT_FOUND
                FeatureStatus(feature.id, state, installed, discovery.note, cached = cached != null)
            }
        } catch (t: Throwable) {
            HookLog.error(feature.id, "failed; Google Phone keeps its normal behaviour here", t)
            FeatureStatus(feature.id, State.FAILED, detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        }.copy(millis = elapsedMs(started))

        HookLog.info(feature.id, describe(status))
        return status
    }

    private fun describe(status: FeatureStatus): String = buildString {
        append(status.state.name.lowercase().replace('_', ' '))
        if (status.state == State.ACTIVE) append(" (${status.targets} hook${if (status.targets == 1) "" else "s"})")
        append(" in ${status.millis} ms")
        if (status.cached) append(", from cache")
        if (status.detail.isNotEmpty()) append(": ").append(status.detail)
    }

    /** Lives in Google Phone's device-protected storage, which is readable before the first unlock. */
    private fun cacheFile(): File? =
        param.applicationInfo.deviceProtectedDataDir?.let { File(it, "dialerenhancements/discovery.txt") }

    /** Changes whenever Google Phone or the module is installed again, so stale results are never used. */
    private fun cacheKey(): String {
        val phone = File(param.applicationInfo.sourceDir)
        val moduleApk = runCatching { File(module.moduleApplicationInfo.sourceDir) }.getOrNull()
        return listOf(
            phone.path, phone.length(), phone.lastModified(),
            moduleApk?.path, moduleApk?.lastModified(),
        ).joinToString("|")
    }

    private fun elapsedMs(since: Long) = (System.nanoTime() - since) / 1_000_000

    private companion object {
        const val SCOPE = "start"
        const val CONTEXT_WAIT_MS = 60_000L
    }
}
