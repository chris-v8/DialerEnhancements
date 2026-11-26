// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10.8a and REWRITE_PLAN.md S12.

package io.github.chrisv8.dialerenhancements.app.picker

/**
 * Decides which of the user's contact numbers are already on Google's Selected-numbers list.
 *
 * Google stores numbers in international form (for example `+48…`), while contacts may hold
 * them in any local form. So two numbers match when:
 * 1. both are in international form and are identical, or
 * 2. their last [TAIL_DIGITS] digits are the same, unless both are known in international
 *    form and differ (then they are different numbers in different countries).
 */
class PhoneMatch(stored: Collection<String>) {
    private val byInternational = HashMap<String, String>()
    private val byTail = HashMap<String, String>()

    init {
        for (number in stored) {
            international(number)?.let { byInternational[it] = number }
            // If two stored numbers share an ending, the later one wins.
            tailKey(number)?.let { byTail[it] = number }
        }
    }

    /**
     * The stored number that [number] (as written in the contact) refers to, or null.
     * [normalized] is the contact's international form, when Android knows it.
     */
    fun storedFor(number: String?, normalized: String?): String? {
        val contactInternational = international(normalized) ?: international(number)
        contactInternational?.let { byInternational[it] }?.let { return it }

        val candidate = tailKey(number)?.let(byTail::get) ?: tailKey(normalized)?.let(byTail::get) ?: return null
        val storedInternational = international(candidate)
        if (contactInternational != null && storedInternational != null && contactInternational != storedInternational) {
            return null
        }
        return candidate
    }

    companion object {
        /** How many trailing digits identify a number when the country code is unknown. */
        const val TAIL_DIGITS = 9

        /** Shorter numbers (service codes and the like) are never matched. */
        const val MIN_DIGITS = 6

        fun digits(number: String?): String = number.orEmpty().filter { it in '0'..'9' }

        /** The digits that identify a number: its last 9, or all of them if it has 6 to 9. */
        fun tailKey(number: String?): String? {
            val d = digits(number)
            return when {
                d.length < MIN_DIGITS -> null
                d.length > TAIL_DIGITS -> d.takeLast(TAIL_DIGITS)
                else -> d
            }
        }

        /** `+` and digits, if [number] is written in international form; null otherwise. */
        fun international(number: String?): String? {
            val trimmed = number?.trim().orEmpty()
            if (!trimmed.startsWith("+")) return null
            val d = digits(trimmed)
            return if (d.length >= MIN_DIGITS) "+$d" else null
        }
    }
}
