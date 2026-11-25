// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.3.

package io.github.chrisv8.dialerenhancements.hook.find

import java.lang.reflect.Method

/**
 * A method written down as text, so it can be saved in the discovery cache and loaded
 * again with plain reflection: `declaring.Class#name#paramType1,paramType2`.
 */
object MethodRef {
    private const val SEP = '#'

    private val primitives = listOf(
        Boolean::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!,
        Char::class.javaPrimitiveType!!, Short::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!,
        Float::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!,
    ).associateBy { it.name }

    fun write(method: Method): String =
        listOf(
            method.declaringClass.name,
            method.name,
            method.parameterTypes.joinToString(",") { it.name },
        ).joinToString(SEP.toString())

    /** Loads the method again; throws if it no longer exists. */
    fun read(text: String, classLoader: ClassLoader?): Method {
        val parts = text.split(SEP)
        require(parts.size == 3) { "bad method reference" }
        val owner = Class.forName(parts[0], false, classLoader)
        val params = if (parts[2].isEmpty()) emptyArray() else
            parts[2].split(',').map { primitives[it] ?: Class.forName(it, false, classLoader) }.toTypedArray()
        return owner.getDeclaredMethod(parts[1], *params)
    }
}
