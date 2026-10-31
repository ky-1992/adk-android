package com.google.adk.tools

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Evaluates a mathematical expression and returns the result."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "The mathematical expression to evaluate, e.g. '2 + 3 * 4'")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("expression"))))
    }

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        val expression = args["expression"]?.toString() ?: return "Error: no expression provided"
        return try {
            evaluate(expression).toString()
        } catch (e: Exception) {
            "Error evaluating expression: ${e.message}"
        }
    }

    /**
     * Recursive descent expression evaluator.
     * Supports: +, -, *, /, parentheses, decimal numbers.
     */
    private fun evaluate(expr: String): Double {
        val s = expr.replace("\\s+".toRegex(), "")

        // Object expression to support mutual recursion (local funs cannot forward-reference)
        return object {
            var pos = 0

            fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
            fun consume(): Char = if (pos < s.length) s[pos++] else '\u0000'

            fun number(): Double {
                val start = pos
                if (peek() == '+' || peek() == '-') consume()
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                require(pos > start) { "Expected number at $start in '$s'" }
                return s.substring(start, pos).toDouble()
            }

            fun expression(): Double {
                var v = factor()
                while (pos < s.length && (peek() == '+' || peek() == '-')) {
                    val op = consume()
                    val rhs = factor()
                    v = if (op == '+') v + rhs else v - rhs
                }
                return v
            }

            fun factor(): Double {
                var v = atom()
                while (pos < s.length && (peek() == '*' || peek() == '/')) {
                    val op = consume()
                    val rhs = atom()
                    v = if (op == '*') v * rhs else v / rhs
                }
                return v
            }

            fun atom(): Double {
                return if (peek() == '(') {
                    consume()
                    val v = expression()
                    if (peek() == ')') consume()
                    v
                } else {
                    number()
                }
            }

            fun parse(): Double = expression()
        }.parse()
    }
}