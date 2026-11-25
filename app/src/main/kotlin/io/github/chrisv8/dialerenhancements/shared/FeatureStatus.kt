// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.1 and §6.

package io.github.chrisv8.dialerenhancements.shared

/**
 * What happened to one feature during one start of Google Phone.
 * Travels from the hooks to the app as a single line of text.
 */
data class FeatureStatus(
    val id: String,
    val state: State,
    /** How many Google Phone methods the feature hooked. */
    val targets: Int = 0,
    /** Short technical note for the status page, never personal data. */
    val detail: String = "",
    val millis: Long = 0,
    /** True when the methods came from the per-version cache instead of a fresh search. */
    val cached: Boolean = false,
) {
    enum class State {
        /** Hooks installed. */
        ACTIVE,

        /** No unique target on this Google Phone version; Google's behaviour is unchanged. */
        NOT_FOUND,

        /** Something threw; Google's behaviour is unchanged. */
        FAILED,

        /** Not needed on this phone, or turned off. */
        SKIPPED,
    }

    fun encode(): String =
        listOf(id, state.name, targets, clean(detail), millis, if (cached) 1 else 0).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\t"

        private fun clean(text: String) = text.replace(SEPARATOR, " ").replace('\n', ' ')

        /** Returns null for a line this version of the app doesn't understand. */
        fun decode(line: String): FeatureStatus? {
            val parts = line.split(SEPARATOR)
            if (parts.size != 6) return null
            return FeatureStatus(
                id = parts[0],
                state = State.entries.firstOrNull { it.name == parts[1] } ?: return null,
                targets = parts[2].toIntOrNull() ?: return null,
                detail = parts[3],
                millis = parts[4].toLongOrNull() ?: return null,
                cached = parts[5] == "1",
            )
        }
    }
}
