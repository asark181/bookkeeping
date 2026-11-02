package com.bookkeeping.app.ui.record

/**
 * 记账键盘的加减计算：表达式形如 "12.5+3-0.8"，金额精确到分，从左到右求值。
 */
object Calculator {

    private const val MAX_INTEGER_DIGITS = 8

    private fun isOperator(ch: Char) = ch == '+' || ch == '-'

    private fun currentOperand(expr: String): String = expr.substring(expr.indexOfLast { isOperator(it) } + 1)

    fun hasPendingOperator(expr: String): Boolean = expr.any { isOperator(it) }

    fun input(expr: String, key: String): String = when (key) {
        "C" -> ""
        "⌫" -> expr.dropLast(1)
        "+", "-" -> when {
            expr.isEmpty() -> expr
            isOperator(expr.last()) -> expr.dropLast(1) + key
            expr.last() == '.' -> expr.dropLast(1) + key
            else -> expr + key
        }
        "." -> {
            val operand = currentOperand(expr)
            when {
                operand.contains('.') -> expr
                operand.isEmpty() -> expr + "0."
                else -> "$expr."
            }
        }
        else -> {
            val operand = currentOperand(expr)
            val dot = operand.indexOf('.')
            when {
                key.length != 1 || !key[0].isDigit() -> expr
                dot >= 0 && operand.length - dot - 1 >= 2 -> expr
                dot < 0 && operand == "0" -> expr.dropLast(1) + key
                dot < 0 && operand.length >= MAX_INTEGER_DIGITS -> expr
                else -> expr + key
            }
        }
    }

    /** 求值为分；空表达式返回 null */
    fun evaluate(expr: String): Long? {
        if (expr.isBlank()) return null
        var total = 0L
        var sign = 1
        val number = StringBuilder()
        var any = false
        fun flush() {
            if (number.isNotEmpty()) {
                total += sign * toCents(number.toString())
                any = true
                number.clear()
            }
        }
        for (ch in expr) {
            if (isOperator(ch)) {
                flush()
                sign = if (ch == '+') 1 else -1
            } else {
                number.append(ch)
            }
        }
        flush()
        return if (any) total else null
    }

    private fun toCents(s: String): Long {
        val parts = s.split('.')
        val yuan = parts[0].ifEmpty { "0" }.toLongOrNull() ?: 0L
        val fenStr = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
        return yuan * 100 + (fenStr.toLongOrNull() ?: 0L)
    }
}
