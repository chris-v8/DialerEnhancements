// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chrisv8.dialerenhancements.hook.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@Suppress("unused", "UNUSED_PARAMETER")
class FutureFactoryTest {
    /** Stand-ins for Google Phone's producer and helpers. */
    object Samples {
        @JvmStatic fun producer(inputs: Any?): Future<*> = CompletableFuture.completedFuture(false)
        @JvmStatic fun completed(value: Any?): Future<Any?> = CompletableFuture.completedFuture(value)
        @JvmStatic fun pending(value: Any?): Future<Any?> = CompletableFuture()
        @JvmStatic fun alwaysFalse(value: Any?): Future<Any?> = CompletableFuture.completedFuture(false)
        @JvmStatic fun throws(value: Any?): Future<Any?> = error("boom")
        @JvmStatic fun notFuture(value: Any?): Any? = value
        fun instance(value: Any?): Future<Any?> = CompletableFuture.completedFuture(value)
    }

    private fun method(name: String): Method = Samples::class.java.declaredMethods.single { it.name == name }

    private val producer = method("producer")

    @Test
    fun aRealHelperPasses() {
        assertNull(FutureFactory.verify(method("completed"), producer))
        assertEquals(true, (FutureFactory.wrap(method("completed"), producer, true) as Future<*>).get())
    }

    @Test
    fun helpersThatMisbehaveAreRejected() {
        for (name in listOf("pending", "alwaysFalse", "throws", "notFuture")) {
            assertNotNull(name, FutureFactory.verify(method(name), producer))
        }
    }

    @Test
    fun shapeWantsAStaticObjectHelperReturningTheProducersType() {
        val shape = FutureFactory.shapeFor(producer)
        assertTrue(shape.matches(method("completed")))
        assertTrue(!shape.matches(method("instance"))) // not static
        assertTrue(!shape.matches(method("notFuture"))) // returns Object
    }
}
