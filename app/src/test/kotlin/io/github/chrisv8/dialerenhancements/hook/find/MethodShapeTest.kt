// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MethodShapeTest {
    private val getDisplayName = Locale::class.java.getMethod("getDisplayName", Locale::class.java)
    private val isEmpty = String::class.java.getMethod("isEmpty")
    private val valueOf = String::class.java.getMethod("valueOf", Any::class.java)

    @Test
    fun exactParams() {
        val s = shape { params(Locale::class.java) }
        assertTrue(s.matches(getDisplayName))
        assertFalse(s.matches(isEmpty))
        assertEquals(1, s.paramCount)
    }

    @Test
    fun primitiveReturn() {
        val s = shape {
            paramCount(0)
            returns(Boolean::class.javaPrimitiveType!!)
        }
        assertTrue(s.matches(isEmpty))
        assertFalse(s.matches(getDisplayName))
    }

    @Test
    fun subtypeReturnAndStatic() {
        val s = shape {
            returnsSubtypeOf(CharSequence::class.java)
            isStatic()
        }
        assertTrue(s.matches(valueOf))
        assertFalse(s.matches(getDisplayName)) // not static
        assertNull(s.paramCount)
    }

    @Test
    fun describesItself() {
        val s = shape {
            params(Any::class.java)
            returns(String::class.java)
        }
        assertEquals("params (Object), returns String", s.toString())
    }
}
