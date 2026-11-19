package tw.tripshare.core

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The expense form's calculator: + − × ÷ on plain numbers, × and ÷ first.
 * Accepts both the keypad's symbols and ASCII (+ - * /); commas are ignored.
 */
object Calc {
    const val OPS = "+−×÷"

    /** The value of [expr], or null when it's empty, unfinished or divides by zero. */
    fun evaluate(expr: String): Double? {
        val tokens = tokenize(expr) ?: return null
        if (tokens.isEmpty() || tokens.size % 2 == 0) return null
        // Fold × and ÷ into terms first, then add the terms up.
        var sum = 0.0
        var sign = 1.0
        var term = tokens[0] as? Double ?: return null
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i] as? Char ?: return null
            val n = tokens[i + 1] as? Double ?: return null
            when (op) {
                '×' -> term *= n
                '÷' -> { if (n == 0.0) return null; term /= n }
                else -> { sum += sign * term; sign = if (op == '+') 1.0 else -1.0; term = n }
            }
            i += 2
        }
        return sum + sign * term
    }

    /** [expr] in [c]'s minor units, rounded half up; null when invalid, zero or negative. */
    fun minor(expr: String, c: Currency): Long? {
        val v = evaluate(expr) ?: return null
        if (v.isNaN() || v.isInfinite() || v <= 0) return null
        return (v * 10.0.pow(c.decimals)).roundToLong().takeIf { it > 0 }
    }

    /** True when [expr] has an operator, i.e. it's worth showing its result separately. */
    fun hasOperator(expr: String): Boolean = expr.drop(1).any { normal(it) in OPS }

    /**
     * [expr] after pressing [key] (a digit, ".", an operator, "⌫" or "C"),
     * keeping it well-formed: no two operators in a row, one dot per number,
     * at most [maxDecimals] digits after a dot.
     */
    fun press(expr: String, key: String, maxDecimals: Int): String {
        val e = expr.replace(",", "")
        val last = e.lastOrNull()
        val number = e.takeLastWhile { it.isDigit() || it == '.' }
        return when {
            key == "C" -> ""
            key == "⌫" -> e.dropLast(1)
            key.length == 1 && normal(key[0]) in OPS -> when {
                e.isEmpty() -> e
                normal(last!!) in OPS -> e.dropLast(1) + normal(key[0])
                last == '.' -> e.dropLast(1) + normal(key[0])
                else -> e + normal(key[0])
            }
            key == "." -> when {
                maxDecimals == 0 || '.' in number -> e
                number.isEmpty() -> e + "0."
                else -> e + "."
            }
            key.length == 1 && key[0].isDigit() -> when {
                '.' in number && number.substringAfter('.').length >= maxDecimals -> e
                number == "0" -> e.dropLast(1) + key
                number.length >= 12 -> e
                else -> e + key
            }
            else -> e
        }
    }

    private fun normal(c: Char): Char = when (c) {
        '*', 'x', 'X' -> '×'
        '/' -> '÷'
        '-', '－' -> '−'
        '＋' -> '+'
        else -> c
    }

    private fun tokenize(expr: String): List<Any>? {
        val out = mutableListOf<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            val v = num.toString().toDoubleOrNull() ?: return false
            out.add(v); num.clear(); return true
        }
        for (raw in expr) {
            val c = normal(raw)
            when {
                c == ',' || c == ' ' -> {}
                c.isDigit() || c == '.' -> num.append(c)
                c in OPS -> { if (!flush() || num.isNotEmpty()) return null; out.add(c) }
                else -> return null
            }
        }
        if (!flush()) return null
        return out
    }
}
