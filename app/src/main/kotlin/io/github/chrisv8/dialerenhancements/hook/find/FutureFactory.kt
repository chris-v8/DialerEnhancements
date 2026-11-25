// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F7.1 and REWRITE_PLAN.md S3.

package io.github.chrisv8.dialerenhancements.hook.find

import java.lang.reflect.Method
import java.util.concurrent.Future

/**
 * Google Phone answers many questions with a Future (a "promise of an answer"). Its code has a
 * small static helper that wraps a plain value into an already-finished Future. Features that
 * replace one of Google's answers (record all calls, the bubble) find that helper among the
 * methods the answering method calls, check it, and use it to wrap their own answer.
 */
object FutureFactory {

    /** Static, takes one `Object`, and returns something the producer may return. */
    fun shapeFor(producer: Method): MethodShape = shape {
        params(Any::class.java)
        isStatic()
        where("returns a ${producer.returnType.simpleName}") { producer.returnType.isAssignableFrom(it.returnType) }
    }

    /** The one helper called by [producer] that has the right shape. Not yet [verify]-ed. */
    fun find(finder: Finder, producer: Method): Match = finder.uniqueCallee(listOf(producer), shapeFor(producer))

    /**
     * Calls [factory] once with `true` and checks that it really gives back a finished Future
     * holding `true` that [producer] could return. Returns null if so, else the reason.
     */
    fun verify(factory: Method, producer: Method): String? {
        val result = try {
            factory.isAccessible = true
            factory.invoke(null, true)
        } catch (t: Throwable) {
            return "test call failed (${t.javaClass.simpleName})"
        }
        return when {
            result !is Future<*> -> "test result is not a Future"
            !producer.returnType.isInstance(result) -> "test result is not a ${producer.returnType.simpleName}"
            !result.isDone -> "test result is not finished"
            result.get() != true -> "test result holds the wrong value"
            else -> null
        }
    }

    /** Wraps [value] into a finished Future that [producer] may return, or null if that fails. */
    fun wrap(factory: Method, producer: Method, value: Any?): Any? =
        factory.invoke(null, value)?.takeIf { producer.returnType.isInstance(it) }
}
