@file:Suppress("MagicNumber") // fattori di conversione e formule delle temperature: sono dati, non scelte

package it.manlio.nova.feature.home

import java.util.Locale

/**
 * Calcolatrice e conversione di unità per la ricerca (SPEC §6.6): «12*(3+4)», «15% di 80», «10 km in mi».
 * Numeri con virgola o punto. Nessuna eval: un piccolo parser a discesa ricorsiva, quindi nessun rischio dal testo scritto.
 */
internal object Calc {
    /** La risposta già scritta ("84"), o null se il testo non è un calcolo né una conversione. */
    fun answer(query: String): String? = convert(query) ?: calculate(query)

    fun calculate(query: String): String? {
        val text = query.trim().lowercase(Locale.ROOT).replace(',', '.').replace("×", "*").replace("÷", "/")
        if (!looksLikeCalculation(text)) return null
        val percentOf = Regex("^([0-9.]+)\\s*%\\s*di\\s*([0-9.]+)$").find(text)
        val value =
            if (percentOf != null) {
                val (p, base) = percentOf.destructured
                p.toDoubleOrNull()?.let { a -> base.toDoubleOrNull()?.let { b -> a * b / PERCENT } }
            } else {
                Parser(text).parse()
            }
        return value?.takeIf { it.isFinite() }?.let(::format)
    }

    /** Serve almeno una cifra, e un numero da solo non è un calcolo. */
    private fun looksLikeCalculation(text: String) = text.any { it.isDigit() } && !text.all { it.isDigit() || it == '.' }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double? =
            runCatching {
                val v = expr()
                skip()
                if (i != s.length) null else v
            }.getOrNull()

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun peek(): Char? = skip().let { s.getOrNull(i) }

        private fun expr(): Double {
            var v = term()
            while (true) {
                v =
                    when (peek()) {
                        '+' -> {
                            i++
                            v + term()
                        }
                        '-' -> {
                            i++
                            v - term()
                        }
                        else -> return v
                    }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                v =
                    when (peek()) {
                        '*' -> {
                            i++
                            v * power()
                        }
                        '/' -> {
                            i++
                            v / power()
                        }
                        else -> return v
                    }
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (peek() == '^') {
                i++
                Math.pow(base, power())
            } else {
                base
            }
        }

        private fun unary(): Double =
            when (peek()) {
                '-' -> {
                    i++
                    -unary()
                }
                '+' -> {
                    i++
                    unary()
                }
                else -> atom()
            }

        private fun atom(): Double {
            if (peek() == '(') {
                i++
                val v = expr()
                check(peek() == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            check(i > start)
            val n = s.substring(start, i).toDouble()
            return if (peek() == '%') {
                i++
                n / PERCENT
            } else {
                n
            }
        }
    }

    private enum class Group { LENGTH, MASS, TEMPERATURE }

    private class Unit(val group: Group, val factor: Double)

    private val UNITS =
        mapOf(
            "mm" to Unit(Group.LENGTH, 0.001), "cm" to Unit(Group.LENGTH, 0.01), "m" to Unit(Group.LENGTH, 1.0),
            "km" to Unit(Group.LENGTH, 1000.0), "in" to Unit(Group.LENGTH, 0.0254), "ft" to Unit(Group.LENGTH, 0.3048),
            "mi" to Unit(Group.LENGTH, 1609.344), "g" to Unit(Group.MASS, 0.001), "kg" to Unit(Group.MASS, 1.0),
            "lb" to Unit(Group.MASS, 0.45359237), "oz" to Unit(Group.MASS, 0.028349523125),
            "c" to Unit(Group.TEMPERATURE, 1.0), "f" to Unit(Group.TEMPERATURE, 1.0), "k" to Unit(Group.TEMPERATURE, 1.0),
        )

    private val CONVERSION = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*°?\\s*([a-z]+)\\s+(?:in|a|to)\\s+°?([a-z]+)$")

    @Suppress("ReturnCount") // una uscita per ogni motivo per cui non è una conversione
    fun convert(query: String): String? {
        val m = CONVERSION.find(query.trim().lowercase(Locale.ROOT)) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = UNITS[m.groupValues[2]] ?: return null
        val to = UNITS[m.groupValues[3]] ?: return null
        if (from.group != to.group) return null
        val result =
            if (from.group == Group.TEMPERATURE) {
                fromCelsius(toCelsius(n, m.groupValues[2]), m.groupValues[3])
            } else {
                n * from.factor / to.factor
            }
        return "${format(result)} ${m.groupValues[3]}"
    }

    @Suppress("MagicNumber") // formule delle temperature
    private fun toCelsius(
        n: Double,
        unit: String,
    ) = when (unit) {
        "f" -> (n - 32) * 5 / 9
        "k" -> n - 273.15
        else -> n
    }

    @Suppress("MagicNumber")
    private fun fromCelsius(
        c: Double,
        unit: String,
    ) = when (unit) {
        "f" -> c * 9 / 5 + 32
        "k" -> c + 273.15
        else -> c
    }

    /** Senza zeri inutili e con al massimo 6 decimali: 84, 0.5, 3.141593. */
    private fun format(v: Double): String =
        if (v == Math.rint(v) && Math.abs(v) < MAX_PLAIN) v.toLong().toString() else "%.6f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')

    private const val PERCENT = 100.0
    private const val MAX_PLAIN = 1e15
}
