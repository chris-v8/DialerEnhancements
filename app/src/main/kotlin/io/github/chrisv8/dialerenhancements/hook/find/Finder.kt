// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §4.2 (S1, S2).

package io.github.chrisv8.dialerenhancements.hook.find

import io.github.chrisv8.dialerenhancements.hook.HookLog
import java.lang.reflect.Method

/** Result of a lookup under the "exactly one match" rule. */
sealed interface Match {
    data class Found(val method: Method) : Match
    data class NotFound(val what: String) : Match
    data class Ambiguous(val what: String, val count: Int) : Match

    val methodOrNull: Method? get() = (this as? Found)?.method

    /** Short text for logs and the status page. */
    fun describe(): String = when (this) {
        is Found -> "found ${method.declaringClass.name}.${method.name}"
        is NotFound -> "no match for $what"
        is Ambiguous -> "$count matches for $what"
    }
}

/**
 * Every lookup in the module goes through here, with one rule:
 * **exactly one** method matching the marker and shape is used; zero or several means "skip",
 * and Google Phone keeps its normal behaviour.
 *
 * The code search is opened only when a lookup actually needs it (cache misses), and only once.
 */
class Finder(private val openSearch: () -> CodeSearch) : AutoCloseable {
    private var search: CodeSearch? = null
    private var openError: Throwable? = null

    private fun search(): CodeSearch {
        search?.let { return it }
        openError?.let { throw IllegalStateException("code search unavailable", it) }
        return try {
            openSearch().also { search = it }
        } catch (t: Throwable) {
            openError = t
            throw t
        }
    }

    /** The one method that uses [marker] and has [shape]. */
    fun unique(marker: String, shape: MethodShape): Match =
        pick("\"$marker\" with $shape", search().methodsUsing(marker, shape.paramCount).filter(shape::matches))

    /** The one method that uses both texts and has [shape] (SPEC F9). */
    fun uniqueUsingBoth(marker: String, otherMarker: String, shape: MethodShape): Match {
        val others = search().methodsUsing(otherMarker, shape.paramCount).toSet()
        val candidates = search().methodsUsing(marker, shape.paramCount).filter { it in others && shape.matches(it) }
        return pick("\"$marker\" + \"$otherMarker\" with $shape", candidates)
    }

    /** The one method called by any of [callers] that has [shape]. */
    fun uniqueCallee(callers: Collection<Method>, shape: MethodShape): Match {
        val candidates = callers.flatMap { search().calledBy(it, shape.paramCount) }.filter(shape::matches)
        return pick("callee with $shape", candidates)
    }

    /** The one method declared directly in [owner] that has [shape]. Plain reflection, no code search. */
    fun uniqueDeclared(owner: Class<*>, shape: MethodShape): Match =
        pick("method of ${owner.name} with $shape", owner.declaredMethods.filter(shape::matches))

    private fun pick(what: String, candidates: List<Method>): Match {
        val distinct = candidates.distinct()
        return when (distinct.size) {
            1 -> Match.Found(distinct.single())
            0 -> Match.NotFound(what)
            else -> {
                HookLog.debug("finder") {
                    "${distinct.size} candidates for $what: " +
                        distinct.joinToString { "${it.declaringClass.name}.${it.name}" }
                }
                Match.Ambiguous(what, distinct.size)
            }
        }
    }

    val wasOpened: Boolean get() = search != null

    override fun close() {
        search?.close()
        search = null
    }
}
