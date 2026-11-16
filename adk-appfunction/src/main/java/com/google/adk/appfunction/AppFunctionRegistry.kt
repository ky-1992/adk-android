// adk-appfunction/src/main/java/com/google/adk/appfunction/AppFunctionRegistry.kt
package com.google.adk.appfunction

import java.util.concurrent.ConcurrentHashMap

/**
 * [AppFunction] 注册表，线程安全：内部基于 [ConcurrentHashMap]，
 * 重复 id 的并发注册也只会有一个成功（putIfAbsent 原子操作）。
 */
class AppFunctionRegistry {

    private val functions = ConcurrentHashMap<String, AppFunction>()

    /**
     * 注册一个 [AppFunction]。
     *
     * @throws IllegalArgumentException id/name/description 为空白，或 id 已被注册时。
     */
    fun register(function: AppFunction) {
        require(function.id.isNotBlank()) { "AppFunction id 不能为空白" }
        require(function.name.isNotBlank()) { "AppFunction '${function.id}' 的 name 不能为空白" }
        require(function.description.isNotBlank()) {
            "AppFunction '${function.id}' 的 description 不能为空白"
        }
        val existing = functions.putIfAbsent(function.id, function)
        if (existing != null) {
            throw IllegalArgumentException(
                "AppFunction id 重复注册: '${function.id}'（已注册 name='${existing.name}'）",
            )
        }
    }

    /**
     * 按 id 注销。
     *
     * @return 注册表中确实存在该 id 并被移除时返回 true。
     */
    fun unregister(id: String): Boolean = functions.remove(id) != null

    /** 按 id 查找，不存在时返回 null。 */
    fun getById(id: String): AppFunction? = functions[id]

    /** 当前全部已注册函数，按 id 升序返回；返回的是快照列表，独立于注册表后续变化。 */
    fun getAll(): List<AppFunction> = functions.values.sortedBy { it.id }
}
