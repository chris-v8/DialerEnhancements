// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10.7 and the public protobuf
// wire format. The idea of reading the list without knowing Google's schema is Chris's, from CallRecording.

package io.github.chrisv8.dialerenhancements.hook.util

import java.io.File

/**
 * Reads the phone numbers on Google Phone's Selected-numbers list straight from its settings
 * file, without knowing how Google laid the file out. The file is a protobuf message; every
 * text field that looks like a phone number is collected, looking into nested messages too.
 * The file is only ever read, never written.
 */
object ProtoNumbers {
    /** How deep nested messages are followed. */
    const val MAX_DEPTH = 4

    /** A stored number has an optional leading `+` and this many ASCII digits. */
    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 18

    /** A varint never needs more than this many bytes. */
    private const val MAX_VARINT_BYTES = 10

    /**
     * The numbers in [file], in the order they first appear. A missing file means an empty
     * list; a file that can't be read gives null ("unknown").
     */
    fun read(file: File): List<String>? = try {
        if (!file.exists()) emptyList() else parse(file.readBytes())
    } catch (_: Exception) {
        null
    }

    /** Numbers found before any damaged part of the data are kept. */
    fun parse(bytes: ByteArray): List<String> {
        val found = LinkedHashSet<String>()
        Parser(bytes).message(0, bytes.size, 0, found)
        return found.toList()
    }

    private class Parser(private val bytes: ByteArray) {
        private var pos = 0

        /** Parses [start, end); returns true only if the whole range was well formed. */
        fun message(start: Int, end: Int, depth: Int, out: MutableCollection<String>): Boolean {
            pos = start
            while (pos < end) {
                val key = varint(end) ?: return false
                if (key ushr 3 == 0L) return false
                when ((key and 7).toInt()) {
                    0 -> varint(end) ?: return false
                    1 -> if (!skip(8, end)) return false
                    5 -> if (!skip(4, end)) return false
                    2 -> {
                        val length = varint(end) ?: return false
                        if (length < 0 || length > end - pos) return false
                        val valueStart = pos
                        val valueEnd = pos + length.toInt()
                        if (looksLikeNumber(valueStart, valueEnd)) {
                            out += String(bytes, valueStart, valueEnd - valueStart, Charsets.US_ASCII)
                        } else if (depth < MAX_DEPTH && valueEnd > valueStart) {
                            // Maybe a nested message; keep its numbers only if it parses cleanly.
                            val nested = LinkedHashSet<String>()
                            if (message(valueStart, valueEnd, depth + 1, nested)) out += nested
                        }
                        pos = valueEnd
                    }
                    else -> return false // includes the old group markers (3, 4)
                }
            }
            return true
        }

        private fun varint(end: Int): Long? {
            var result = 0L
            for (i in 0 until MAX_VARINT_BYTES) {
                if (pos >= end) return null
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl (7 * i))
                if (b and 0x80 == 0) return result
            }
            return null
        }

        private fun skip(count: Int, end: Int): Boolean {
            if (end - pos < count) return false
            pos += count
            return true
        }

        private fun looksLikeNumber(start: Int, end: Int): Boolean {
            var i = start
            if (i < end && bytes[i] == '+'.code.toByte()) i++
            val digits = end - i
            if (digits < MIN_DIGITS || digits > MAX_DIGITS) return false
            while (i < end) {
                if (bytes[i] !in '0'.code.toByte()..'9'.code.toByte()) return false
                i++
            }
            return true
        }
    }
}
