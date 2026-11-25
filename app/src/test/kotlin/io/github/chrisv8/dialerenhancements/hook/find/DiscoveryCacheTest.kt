// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.find

import io.github.chrisv8.dialerenhancements.hook.Discovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

class DiscoveryCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val loader = javaClass.classLoader

    private val isEmpty = String::class.java.getMethod("isEmpty")
    private val regionMatches = String::class.java.getMethod(
        "regionMatches", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    )
    private val forLanguageTag = Locale::class.java.getMethod("forLanguageTag", String::class.java)

    @Test
    fun methodRefsRoundTrip() {
        for (method in listOf(isEmpty, regionMatches, forLanguageTag)) {
            assertEquals(method, MethodRef.read(MethodRef.write(method), loader))
        }
    }

    @Test
    fun savedResultsComeBackForTheSameKey() {
        val file = temp.root.resolve("cache/discovery.txt")
        DiscoveryCache.load(file, "key-1").apply {
            put("one", Discovery(mapOf("a" to isEmpty, "b" to regionMatches)))
            put("none", Discovery.nothing("no match for x"))
            assertNull(save())
        }

        val again = DiscoveryCache.load(file, "key-1")
        val one = again.get("one", loader)!!
        assertEquals(mapOf("a" to isEmpty, "b" to regionMatches), one.targets)
        val none = again.get("none", loader)!!
        assertEquals(emptyMap<String, Any>(), none.targets)
        assertEquals("no match for x", none.note)
    }

    @Test
    fun aDifferentKeyStartsEmpty() {
        val file = temp.root.resolve("discovery.txt")
        DiscoveryCache.load(file, "old").apply {
            put("one", Discovery(mapOf("a" to isEmpty)))
            save()
        }
        assertEquals(0, DiscoveryCache.load(file, "new").size)
    }

    @Test
    fun anEntryThatNoLongerLoadsIsDropped() {
        val text = "dialerenhancements-discovery 1\nk\ngone\t\ta=com.example.Missing#run#\n"
        val cache = DiscoveryCache.parse(null, "k", text)
        assertNull(cache.get("gone", loader))
        assertEquals(0, cache.size)
    }

    @Test
    fun garbageGivesAnEmptyCache() {
        assertEquals(0, DiscoveryCache.parse(null, "k", "something else\n").size)
        assertEquals(0, DiscoveryCache.parse(null, "k", null).size)
    }
}
