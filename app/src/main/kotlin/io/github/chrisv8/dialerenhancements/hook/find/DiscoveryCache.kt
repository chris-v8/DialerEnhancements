// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.3 (S4).

package io.github.chrisv8.dialerenhancements.hook.find

import io.github.chrisv8.dialerenhancements.hook.Discovery
import java.io.File

/**
 * Remembers what each feature found, for one exact Google Phone build and module build.
 * The slow code search then runs only on the first start after an update; later starts
 * load the same methods by name in a few milliseconds.
 *
 * The file holds class and method names only, never personal data.
 */
class DiscoveryCache private constructor(
    private val file: File?,
    private val key: String,
    private val entries: MutableMap<String, Entry>,
) {
    private class Entry(val note: String, val refs: Map<String, String>)

    @Volatile
    private var changed = false

    /** The cached result for [featureId], or null if there is none or it no longer loads. */
    @Synchronized
    fun get(featureId: String, classLoader: ClassLoader?): Discovery? {
        val entry = entries[featureId] ?: return null
        return try {
            Discovery(entry.refs.mapValues { (_, ref) -> MethodRef.read(ref, classLoader) }, entry.note)
        } catch (_: Throwable) {
            entries.remove(featureId)
            changed = true
            null
        }
    }

    @Synchronized
    fun put(featureId: String, discovery: Discovery) {
        entries[featureId] = Entry(discovery.note, discovery.targets.mapValues { MethodRef.write(it.value) })
        changed = true
    }

    /** Writes the file if anything changed. Failures are returned, not thrown. */
    @Synchronized
    fun save(): Throwable? {
        if (!changed || file == null) return null
        return try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(serialize())
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "rename failed" }
            }
            changed = false
            null
        } catch (t: Throwable) {
            t
        }
    }

    internal fun serialize(): String = buildString {
        append(HEADER).append('\n')
        append(key).append('\n')
        for ((id, entry) in entries) {
            append(id).append(TAB).append(entry.note.replace(TAB, ' ').replace('\n', ' '))
            for ((role, ref) in entry.refs) append(TAB).append(role).append('=').append(ref)
            append('\n')
        }
    }

    val size: Int @Synchronized get() = entries.size

    companion object {
        private const val HEADER = "dialerenhancements-discovery 1"
        private const val TAB = '\t'

        /** Loads the cache for [key]; a missing, broken or outdated file gives an empty cache. */
        fun load(file: File?, key: String): DiscoveryCache {
            val text = try {
                file?.takeIf { it.isFile }?.readText()
            } catch (_: Throwable) {
                null
            }
            return parse(file, key, text)
        }

        internal fun parse(file: File?, key: String, text: String?): DiscoveryCache {
            val entries = LinkedHashMap<String, Entry>()
            val lines = text?.lines().orEmpty()
            if (lines.size >= 2 && lines[0] == HEADER && lines[1] == key) {
                for (line in lines.drop(2)) {
                    if (line.isBlank()) continue
                    val parts = line.split(TAB)
                    if (parts.size < 2) continue
                    val refs = LinkedHashMap<String, String>()
                    for (part in parts.drop(2)) {
                        val eq = part.indexOf('=')
                        if (eq > 0) refs[part.substring(0, eq)] = part.substring(eq + 1)
                    }
                    entries[parts[0]] = Entry(parts[1], refs)
                }
            }
            return DiscoveryCache(file, key, entries)
        }
    }
}
