// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §7.
// Uses DexKit (Apache-2.0 / LGPL-3.0 core), see THIRD_PARTY_NOTICES.md.

package io.github.chrisv8.dialerenhancements.hook.find

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method

/** [CodeSearch] backed by DexKit, reading Google Phone's APK file directly. */
class DexKitSearch private constructor(
    private val bridge: DexKitBridge,
    private val classLoader: ClassLoader,
) : CodeSearch {

    override fun methodsUsing(marker: String, paramCount: Int?): List<Method> =
        bridge.findMethod {
            matcher {
                usingEqStrings(marker)
                if (paramCount != null) paramCount(paramCount)
            }
        }.mapNotNull(::resolve)

    override fun calledBy(caller: Method, paramCount: Int?): List<Method> {
        val data = bridge.getMethodData(caller) ?: return emptyList()
        return data.invokes
            .filter { paramCount == null || it.paramCount == paramCount }
            .mapNotNull(::resolve)
    }

    override fun callersOf(callee: Method): List<Method> =
        bridge.getMethodData(callee)?.callers.orEmpty().mapNotNull(::resolve)

    private fun resolve(data: MethodData): Method? =
        if (!data.isMethod) null else try {
            data.getMethodInstance(classLoader)
        } catch (_: Throwable) {
            null // A method the runtime can't load is simply not a candidate.
        }

    override fun close() = bridge.close()

    companion object {
        @Volatile
        private var nativeLoaded = false

        /**
         * Opens Google Phone's APK at [apkPath]. [nativeLibraryDir] is the module app's own
         * native library folder, tried if the normal library lookup fails.
         */
        fun open(apkPath: String, classLoader: ClassLoader, nativeLibraryDir: String?): DexKitSearch {
            loadNative(nativeLibraryDir)
            return DexKitSearch(DexKitBridge.create(apkPath), classLoader)
        }

        @Synchronized
        private fun loadNative(nativeLibraryDir: String?) {
            if (nativeLoaded) return
            try {
                System.loadLibrary("dexkit")
            } catch (e: UnsatisfiedLinkError) {
                if (nativeLibraryDir == null) throw e
                System.load("$nativeLibraryDir/libdexkit.so")
            }
            nativeLoaded = true
        }
    }
}
