// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F5 and the public WAVE format.

package io.github.chrisv8.dialerenhancements.hook.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A valid WAV file with no sound in it, used when text-to-speech can't produce the announcement. */
object SilentWav {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val HEADER_SIZE = 44

    private const val PCM_FORMAT: Short = 1
    private const val FMT_CHUNK_SIZE = 16

    /** The standard 44-byte RIFF/WAVE header for 16-bit mono PCM with [dataSize] bytes of audio. */
    fun header(dataSize: Int = 0): ByteArray {
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(HEADER_SIZE - 8 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(FMT_CHUNK_SIZE)
            putShort(PCM_FORMAT)
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * blockAlign) // bytes per second
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
    }

    fun writeTo(file: File) = file.writeBytes(header())
}
