// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class ProtoNumbersTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** Tiny protobuf writer for test data. */
    private class Proto {
        val out = ByteArrayOutputStream()
        fun varint(value: Long) = apply {
            var v = value
            while (v and 0x7fL.inv() != 0L) {
                out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7
            }
            out.write(v.toInt())
        }
        fun key(field: Int, type: Int) = varint((field.toLong() shl 3) or type.toLong())
        fun bytes(field: Int, value: ByteArray) = apply { key(field, 2); varint(value.size.toLong()); out.write(value) }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray())
        fun message(field: Int, inner: Proto) = bytes(field, inner.out.toByteArray())
        fun int(field: Int, value: Long) = apply { key(field, 0); varint(value) }
        fun fixed64(field: Int) = apply { key(field, 1); out.write(ByteArray(8)) }
        fun fixed32(field: Int) = apply { key(field, 5); out.write(ByteArray(4)) }
        fun build(): ByteArray = out.toByteArray()
    }

    @Test
    fun findsNumbersAtTheTopAndNested() {
        val data = Proto()
            .int(1, 1) // a switch
            .fixed64(2).fixed32(3)
            .message(4, Proto().text(1, "+48123456789").int(2, 5))
            .message(4, Proto().text(1, "600700800"))
            .text(5, "not a number")
            .build()
        assertEquals(listOf("+48123456789", "600700800"), ProtoNumbers.parse(data))
    }

    @Test
    fun keepsOrderAndDropsDuplicates() {
        val data = Proto().text(1, "+111111111").text(1, "+222222222").text(1, "+111111111").build()
        assertEquals(listOf("+111111111", "+222222222"), ProtoNumbers.parse(data))
    }

    @Test
    fun digitLimits() {
        val data = Proto().text(1, "12345").text(1, "123456").text(1, "1".repeat(18)).text(1, "1".repeat(19)).text(1, "+").build()
        assertEquals(listOf("123456", "1".repeat(18)), ProtoNumbers.parse(data))
    }

    @Test
    fun nestedNumbersCountOnlyIfTheNestedMessageIsClean() {
        // Inner bytes: a valid number field followed by a field with wire type 7 (invalid).
        val broken = Proto().text(1, "+48999888777").key(2, 7).build()
        val data = Proto().bytes(1, broken).text(2, "+48111222333").build()
        assertEquals(listOf("+48111222333"), ProtoNumbers.parse(data))
    }

    @Test
    fun topLevelNumbersBeforeDamageAreKept() {
        val good = Proto().text(1, "+48111222333").build()
        val data = good + byteArrayOf(0x0a, 0x7f) // length past the end
        assertEquals(listOf("+48111222333"), ProtoNumbers.parse(data))
    }

    @Test
    fun fieldNumberZeroIsDamage() {
        val data = Proto().text(1, "+48111222333").key(0, 0).varint(1).text(1, "+48444555666").build()
        assertEquals(listOf("+48111222333"), ProtoNumbers.parse(data))
    }

    @Test
    fun nestingStopsAtTheDepthLimit() {
        var inner = Proto().text(1, "+48123123123")
        repeat(ProtoNumbers.MAX_DEPTH + 1) { inner = Proto().message(1, inner) }
        assertEquals(emptyList<String>(), ProtoNumbers.parse(inner.build()))

        var shallow = Proto().text(1, "+48123123123")
        repeat(ProtoNumbers.MAX_DEPTH) { shallow = Proto().message(1, shallow) }
        assertEquals(listOf("+48123123123"), ProtoNumbers.parse(shallow.build()))
    }

    @Test
    fun missingFileIsEmptyUnreadableIsUnknown() {
        assertEquals(emptyList<String>(), ProtoNumbers.read(temp.root.resolve("missing.pb")))
        assertNull(ProtoNumbers.read(temp.newFolder("a-folder")))
    }
}
