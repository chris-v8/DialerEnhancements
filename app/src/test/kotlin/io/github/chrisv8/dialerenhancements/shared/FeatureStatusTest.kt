// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureStatusTest {
    @Test
    fun roundTrip() {
        val status = FeatureStatus("geofence", FeatureStatus.State.ACTIVE, 2, "note", 12, cached = true)
        assertEquals(status, FeatureStatus.decode(status.encode()))
    }

    @Test
    fun separatorsInTheDetailAreFlattened() {
        val status = FeatureStatus("x", FeatureStatus.State.FAILED, detail = "a\tb\nc")
        assertEquals("a b c", FeatureStatus.decode(status.encode())!!.detail)
    }

    @Test
    fun unknownLinesAreIgnored() {
        assertNull(FeatureStatus.decode(""))
        assertNull(FeatureStatus.decode("x\tNEW_STATE\t0\t\t0\t0"))
    }
}
