// adk-appfunction/src/test/java/com/google/adk/appfunction/AppFunctionRegistryTest.kt
package com.google.adk.appfunction

import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppFunctionRegistryTest {

    private fun function(
        id: String,
        name: String = "函数$id",
        description: String = "测试函数：$id",
        executor: suspend (JsonObject) -> JsonObject = { AppFunctionResults.success() },
    ) = AppFunction(
        id = id,
        name = name,
        description = description,
        executor = executor,
    )

    @Test
    fun `register 后可以通过 getById 查到`() {
        val registry = AppFunctionRegistry()
        val fn = function("car.open_window", name = "打开车窗")

        registry.register(fn)

        assertSame(fn, registry.getById("car.open_window"))
    }

    @Test
    fun `getById 不存在的 id 返回 null`() {
        val registry = AppFunctionRegistry()

        assertNull(registry.getById("missing"))
    }

    @Test
    fun `getAll 返回按 id 升序的全部函数`() {
        val registry = AppFunctionRegistry()
        registry.register(function("c"))
        registry.register(function("a"))
        registry.register(function("b"))

        assertEquals(listOf("a", "b", "c"), registry.getAll().map { it.id })
    }

    @Test
    fun `getAll 返回的列表是独立快照`() {
        val registry = AppFunctionRegistry()
        registry.register(function("a"))

        val snapshot = registry.getAll()
        assertEquals(1, snapshot.size)

        registry.unregister("a")
        assertEquals(1, snapshot.size)
        assertEquals(0, registry.getAll().size)
    }

    @Test
    fun `重复 id 注册抛出 IllegalArgumentException`() {
        val registry = AppFunctionRegistry()
        registry.register(function("car.open_window"))

        val ex = assertFailsWith<IllegalArgumentException> {
            registry.register(function("car.open_window", name = "再次注册"))
        }

        assertTrue(ex.message!!.contains("car.open_window"))
    }

    @Test
    fun `unregister 移除已注册函数并返回 true`() {
        val registry = AppFunctionRegistry()
        registry.register(function("a"))

        assertTrue(registry.unregister("a"))
        assertNull(registry.getById("a"))
    }

    @Test
    fun `unregister 未注册的 id 返回 false`() {
        val registry = AppFunctionRegistry()

        assertFalse(registry.unregister("not_registered"))
    }

    @Test
    fun `空白 id name description 被拒绝`() {
        val registry = AppFunctionRegistry()

        assertFailsWith<IllegalArgumentException> {
            registry.register(function(id = "  "))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.register(function(id = "a", name = " "))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.register(function(id = "a", description = " "))
        }
    }

    @Test
    fun `并发注册同一 id 只允许一次成功`() {
        val registry = AppFunctionRegistry()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val task: Callable<Boolean> = Callable {
                try {
                    registry.register(function("same"))
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
            val successes = (1..16).map { pool.submit(task) }.count { it.get() }

            assertEquals(1, successes)
            assertEquals(1, registry.getAll().size)
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `并发注册不同 id 全部成功且都能查到`() {
        val registry = AppFunctionRegistry()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..32).map { index ->
                val id = "f$index"
                pool.submit(Callable {
                    registry.register(function(id))
                    id
                })
            }
            val registeredIds = tasks.map { it.get() }

            assertEquals((1..32).map { "f$it" }.toSet(), registeredIds.toSet())
            assertEquals(32, registry.getAll().size)
            assertEquals("函数f17", registry.getById("f17")?.name)
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
