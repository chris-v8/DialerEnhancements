// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.app.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneMatchTest {
    private val match = PhoneMatch(listOf("+48600700800", "+48512345678", "112233"))

    @Test
    fun localFormMatchesTheStoredInternationalNumber() {
        assertEquals("+48600700800", match.storedFor("600 700 800", null))
        assertEquals("+48600700800", match.storedFor("(0) 600-700-800", null))
    }

    @Test
    fun normalizedFormIsUsedFirst() {
        assertEquals("+48512345678", match.storedFor("whatever", "+48512345678"))
    }

    @Test
    fun sameEndingInAnotherCountryIsNotAMatch() {
        assertNull(match.storedFor("+44 600 700 800", null))
        assertNull(match.storedFor("600700800", "+44600700800"))
    }

    @Test
    fun shortNumbersMatchOnlyInFull() {
        assertEquals("112233", match.storedFor("112233", null))
        assertNull(match.storedFor("12345", null))
        assertNull(PhoneMatch.tailKey("12345"))
    }

    @Test
    fun keys() {
        assertEquals("600700800", PhoneMatch.tailKey("+48 600 700 800"))
        assertEquals("+48600700800", PhoneMatch.international(" +48 600-700-800"))
        assertNull(PhoneMatch.international("600700800"))
    }
}
