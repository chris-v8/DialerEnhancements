// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class FinderTest {
    @Suppress("unused")
    private class Sample {
        fun yes(): Boolean = true
        fun no(): Boolean = false
        fun text(): String = ""
        fun produce(input: Any?): CompletableFuture<Boolean> = CompletableFuture.completedFuture(true)
        fun produceString(input: String?): Future<Boolean> = CompletableFuture.completedFuture(true)

        companion object {
            @JvmStatic
            fun wrap(value: Any?): Future<Any?> = CompletableFuture.completedFuture(value)
        }
    }

    private fun method(name: String): Method = Sample::class.java.declaredMethods.single { it.name == name }

    private val wrap: Method = method("wrap") // the static copy made by @JvmStatic

    /** A fake code search: marker → methods using it; caller → callees. */
    private class FakeSearch(
        val byMarker: Map<String, List<Method>>,
        val calls: Map<Method, List<Method>> = emptyMap(),
    ) : CodeSearch {
        var closed = false
        override fun methodsUsing(marker: String, paramCount: Int?) =
            byMarker[marker].orEmpty().filter { paramCount == null || it.parameterCount == paramCount }

        override fun calledBy(caller: Method, paramCount: Int?) =
            calls[caller].orEmpty().filter { paramCount == null || it.parameterCount == paramCount }

        override fun close() {
            closed = true
        }
    }

    private val yesNo = shape {
        paramCount(0)
        returns(Boolean::class.javaPrimitiveType!!)
    }

    @Test
    fun exactlyOneMatchIsFound() {
        val finder = Finder { FakeSearch(mapOf("canRecordCall" to listOf(method("yes"), method("text")))) }
        assertEquals(Match.Found(method("yes")), finder.unique("canRecordCall", yesNo))
    }

    @Test
    fun twoMatchesAreAmbiguous() {
        val finder = Finder { FakeSearch(mapOf("m" to listOf(method("yes"), method("no")))) }
        val match = finder.unique("m", yesNo)
        assertTrue(match is Match.Ambiguous)
        assertEquals(2, (match as Match.Ambiguous).count)
    }

    @Test
    fun theSameMethodListedTwiceCountsOnce() {
        val finder = Finder { FakeSearch(mapOf("m" to listOf(method("yes"), method("yes")))) }
        assertEquals(Match.Found(method("yes")), finder.unique("m", yesNo))
    }

    @Test
    fun noMatchIsNotFound() {
        val finder = Finder { FakeSearch(emptyMap()) }
        assertTrue(finder.unique("m", yesNo) is Match.NotFound)
    }

    @Test
    fun methodMustUseBothMarkers() {
        val finder = Finder {
            FakeSearch(
                mapOf(
                    "a" to listOf(method("yes"), method("no")),
                    "b" to listOf(method("no")),
                ),
            )
        }
        assertEquals(Match.Found(method("no")), finder.uniqueUsingBoth("a", "b", yesNo))
    }

    @Test
    fun calleeShapeIsChecked() {
        val producer = method("produce")
        val finder = Finder { FakeSearch(emptyMap(), mapOf(producer to listOf(wrap, method("produceString")))) }
        val factoryShape = shape {
            params(Any::class.java)
            isStatic()
            returnsSubtypeOf(Future::class.java)
        }
        assertEquals(Match.Found(wrap), finder.uniqueCallee(listOf(producer), factoryShape))
    }

    @Test
    fun searchOpensOnlyWhenNeededAndCloses() {
        var opened = 0
        val search = FakeSearch(emptyMap())
        val finder = Finder { opened++; search }
        assertFalse(finder.wasOpened)
        finder.uniqueDeclared(Sample::class.java, yesNo) // reflection only
        assertEquals(0, opened)
        finder.unique("x", yesNo)
        finder.unique("y", yesNo)
        assertEquals(1, opened)
        finder.close()
        assertTrue(search.closed)
    }

    @Test
    fun aFailedOpenIsNotRetried() {
        var attempts = 0
        val finder = Finder { attempts++; error("no engine") }
        repeat(2) { runCatching { finder.unique("x", yesNo) } }
        assertEquals(1, attempts)
    }
}
