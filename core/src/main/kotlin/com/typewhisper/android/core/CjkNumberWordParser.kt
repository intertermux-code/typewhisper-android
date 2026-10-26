package com.typewhisper.android.core

/**
 * Han-character numbers for Chinese and Japanese.
 *
 * The tokenizer groups consecutive number characters into one token, so `二十三`
 * arrives as a single word and is walked character by character: a digit followed
 * by a scale multiplies into the current group, `万/億` closes the group, and `点`
 * switches to enumerating fractional digits.
 *
 * A lone digit (`一`, `三`) is deliberately not converted: in both languages these
 * characters open ordinary words — `一緒` (`together`), `一般` (`general`) — and
 * rewriting them would corrupt text. Anything with a scale or decimal marker is
 * unambiguous, so that is what converts.
 */
internal object CjkNumberWordParser {

    private val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '壹' to 1, '二' to 2, '两' to 2, '兩' to 2,
        '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    private val smallScales = mapOf('十' to 10L, '百' to 100L, '千' to 1_000L)
    private val bigScales = mapOf('万' to 10_000L, '萬' to 10_000L, '亿' to 100_000_000L, '億' to 100_000_000L)
    private val decimalMarks = setOf('点', '點')
    private val negativeMarks = setOf('负', '負')

    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? {
        val accumulator = Accumulator()

        var consumedWords = 0
        wordLoop@ for ((wordIndex, word) in words.withIndex()) {
            for (character in word) {
                if (!accumulator.feed(character)) break@wordLoop
            }
            consumedWords = wordIndex + 1
        }
        if (consumedWords == 0) return null

        val sign = if (accumulator.negative) "-" else ""
        val integer = accumulator.total + accumulator.group + accumulator.pending
        val fraction = accumulator.fraction

        if (fraction != null) {
            if (fraction.isEmpty()) return null
            return NumberWordNormalizer.ParsedWords(
                value = sign + integer + "." + fraction,
                consumedWords = consumedWords,
                kind = NumberWordNormalizer.Kind.Decimal,
            )
        }

        if (!accumulator.hasValue) return null
        // A bare single digit is ambiguous with ordinary vocabulary; leave it spelled out.
        if (!accumulator.sawScale && integer in 0..9) return null

        return NumberWordNormalizer.ParsedWords(
            value = sign + integer,
            consumedWords = consumedWords,
            kind = NumberWordNormalizer.Kind.Number,
        )
    }

    private class Accumulator {
        var negative = false
            private set
        var total = 0L
            private set
        var group = 0L
            private set
        var pending = 0L
            private set
        var sawScale = false
            private set
        var sawDigit = false
            private set
        var fraction: StringBuilder? = null
            private set

        val hasValue: Boolean get() = sawDigit || sawScale

        /** Returns false at the first character that cannot belong to a number. */
        fun feed(character: Char): Boolean {
            val currentFraction = fraction
            if (currentFraction != null) {
                val digit = digits[character] ?: return false
                currentFraction.append(digit)
                return true
            }

            if (character in digits) {
                pending = digits.getValue(character).toLong()
                sawDigit = true
                return true
            }

            smallScales[character]?.let { scale ->
                group += pending.coerceAtLeast(1L) * scale
                pending = 0
                sawScale = true
                return true
            }

            bigScales[character]?.let { scale ->
                total = (total + group + pending) * scale
                group = 0
                pending = 0
                sawScale = true
                return true
            }

            if (character in decimalMarks) {
                if (!hasValue) return false
                fraction = StringBuilder()
                return true
            }

            if (character in negativeMarks) {
                if (hasValue) return false
                negative = true
                return true
            }

            return false
        }
    }
}
