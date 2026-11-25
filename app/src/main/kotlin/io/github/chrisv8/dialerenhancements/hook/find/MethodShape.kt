// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.2 (S1).

package io.github.chrisv8.dialerenhancements.hook.find

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The "shape" of a method: its parameters, return type and whether it is static.
 * Google renames methods in every release, but a method's shape and the text it uses stay put.
 *
 * Build one with [shape], for example `shape { params(Object::class.java); returnsSubtypeOf(Future::class.java) }`.
 */
class MethodShape private constructor(
    /** Exact parameter count, when the shape fixes it. Lets the code search pre-filter. */
    val paramCount: Int?,
    private val rules: List<Rule>,
) {
    private class Rule(val text: String, val test: (Method) -> Boolean)

    fun matches(method: Method): Boolean = rules.all { it.test(method) }

    override fun toString(): String = rules.joinToString(", ") { it.text }.ifEmpty { "any method" }

    class Builder internal constructor() {
        private var paramCount: Int? = null
        private val rules = mutableListOf<Rule>()

        /** Exactly these parameter types, in this order. */
        fun params(vararg types: Class<*>) {
            paramCount = types.size
            val text = types.joinToString(", ", "(", ")") { it.simpleName }
            rules += Rule("params $text") { it.parameterTypes.contentEquals(types) }
        }

        /** Exactly this many parameters, of any type. */
        fun paramCount(count: Int) {
            paramCount = count
            rules += Rule("$count params") { it.parameterCount == count }
        }

        /** Returns exactly this type (use `Boolean::class.javaPrimitiveType` for `boolean`). */
        fun returns(type: Class<*>) {
            rules += Rule("returns ${type.simpleName}") { it.returnType == type }
        }

        /** Returns this type or a class/interface that extends or implements it. */
        fun returnsSubtypeOf(type: Class<*>) {
            rules += Rule("returns a ${type.simpleName}") { type.isAssignableFrom(it.returnType) }
        }

        fun isStatic() {
            rules += Rule("static") { Modifier.isStatic(it.modifiers) }
        }

        /** Any extra rule that the helpers above can't express. */
        fun where(text: String, test: (Method) -> Boolean) {
            rules += Rule(text, test)
        }

        internal fun build() = MethodShape(paramCount, rules.toList())
    }
}

fun shape(build: MethodShape.Builder.() -> Unit): MethodShape = MethodShape.Builder().apply(build).build()
