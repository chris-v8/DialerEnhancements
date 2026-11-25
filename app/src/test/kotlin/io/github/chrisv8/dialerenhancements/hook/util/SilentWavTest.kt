// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SilentWavTest {
    @Test
    fun headerMatchesTheWaveFormat() {
        val bytes = SilentWav.header()
        assertEquals(44, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun text(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)

        assertEquals("RIFF", text(0))
        assertEquals(36, b.getInt(4))
        assertEquals("WAVE", text(8))
        assertEquals("fmt ", text(12))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28)) // bytes per second
        assertEquals(2, b.getShort(32).toInt()) // block align
        assertEquals(16, b.getShort(34).toInt()) // bits per sample
        assertEquals("data", text(36))
        assertEquals(0, b.getInt(40))
    }
}
