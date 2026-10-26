package com.typewhisper.android.core

/**
 * European number words. One accumulation algorithm serves English, German,
 * French, Spanish and Dutch; the languages differ only in their tables, so the
 * differences live in [NumberTable] rather than in five near-identical parsers.
 *
 * Compound spellings (`dreiundzwanzig`, `driehonderdvijfendertig`) are split into
 * vocabulary atoms before accumulating, because to the tokenizer the spoken form
 * of those numbers is a single word.
 */
internal class NumberTable(
    /** `one`, `deux`, `vijf` -> 0..9. */
    val units: Map<String, Long>,
    /** Everything added onto the current group: teens, tens, Spanish `doscientos`. */
    val additives: Map<String, Long>,
    /** `hundred`, `hundert`, `cent`, `honderd` -> multiplies the current group by 100. */
    val hundreds: Set<String>,
    /** `thousand`, `million`, `mille`, `miljoen` -> moves the current group into the total. */
    val scales: Map<String, Long>,
    val skipWords: Set<String> = emptySet(),
    val negativeWords: Set<String> = emptySet(),
    /** Spoken decimal separator mapped to the character to emit. */
    val decimalWords: Map<String, String> = emptyMap(),
    /** Words that read as "one" but are usually an article: `ein`, `un`, `een`. */
    val bareArticleWords: Set<String> = emptySet(),
    val ordinals: Map<String, OrdinalValue> = emptyMap(),
    /** Spoken single digits, used for dictated digit runs. Only English opts in. */
    val digitWords: Map<String, String> = emptyMap(),
    val supportsDigitSequences: Boolean = false,
    /** French builds 17-19 as `dix` + unit instead of naming them. */
    val allowsUnitAfterTeen: Boolean = false,
    val splitsCompounds: Boolean = false,
) {
    val vocabulary: Set<String> by lazy {
        units.keys + additives.keys + hundreds + scales.keys + skipWords +
            negativeWords + decimalWords.keys + bareArticleWords + ordinals.keys + digitWords.keys
    }

    /** The digit a spoken atom contributes to a fractional part, or null if it is not a single digit. */
    fun fractionDigit(atom: String): Char? {
        digitWords[atom]?.let { return it.singleOrNull() }
        val unit = units[atom] ?: return null
        return if (unit in 0..9) unit.toString().single() else null
    }
}

internal data class OrdinalValue(val number: Long, val suffix: String)

internal object EuropeanNumberWords {

    /** `first`, `second`, `third` double as ordinary nouns, so alone they stay words. */
    private val BARE_ORDINAL_NOUNS = setOf("first", "second", "third")

    private const val MAX_COMPOUND_PART = 12

    private enum class Kind { None, Unit, Teen, Additive, Hundred, Scale }

    fun parse(words: List<String>, table: NumberTable): NumberWordNormalizer.ParsedWords? {
        // Each atom remembers the word it came from, so consumption is counted in words.
        val atoms = mutableListOf<Pair<Int, String>>()
        for ((wordIndex, raw) in words.withIndex()) {
            val resolved = resolve(PunctuationLanguageNormalizer.fold(raw), table) ?: break
            resolved.forEach { atoms.add(wordIndex to it) }
        }
        if (atoms.isEmpty()) return null

        var cursor = 0
        var negative = false
        if (atoms[0].second in table.negativeWords) {
            negative = true
            cursor = 1
        }
        if (cursor >= atoms.size) return null
        val sign = if (negative) "-" else ""

        if (table.supportsDigitSequences) {
            digitSequence(atoms, cursor, table)?.let {
                return it.copy(value = if (negative) "-${it.value}" else it.value)
            }
        }

        var total = 0L
        var current = 0L
        var lastKind = Kind.None
        var lastValueAtom = -1

        while (cursor < atoms.size) {
            val atom = atoms[cursor].second

            if (lastValueAtom >= 0 && atom in table.decimalWords) {
                val fraction = StringBuilder()
                var next = cursor + 1
                while (next < atoms.size) {
                    val digit = table.fractionDigit(atoms[next].second) ?: break
                    fraction.append(digit)
                    next += 1
                }
                if (fraction.isNotEmpty()) {
                    return NumberWordNormalizer.ParsedWords(
                        value = sign + (total + current) + table.decimalWords.getValue(atom) + fraction,
                        consumedWords = atoms[next - 1].first + 1,
                        kind = NumberWordNormalizer.Kind.Decimal,
                    )
                }
                break
            }

            if (lastValueAtom >= 0 && atom in table.skipWords && cursor + 1 < atoms.size && isValueAtom(atoms[cursor + 1].second, table)) {
                cursor += 1
                continue
            }

            table.ordinals[atom]?.let { ordinal ->
                if (lastValueAtom < 0 && atom in BARE_ORDINAL_NOUNS) return null
                return NumberWordNormalizer.ParsedWords(
                    value = sign + (total + current + ordinal.number) + ordinal.suffix,
                    consumedWords = atoms[cursor].first + 1,
                    kind = NumberWordNormalizer.Kind.Number,
                )
            }

            when {
                atom in table.units -> {
                    val allowed = when (lastKind) {
                        Kind.Unit -> false
                        Kind.Teen -> table.allowsUnitAfterTeen
                        else -> true
                    }
                    if (!allowed) break
                    val unit = table.units.getValue(atom)
                    current = if (lastKind == Kind.None || lastKind == Kind.Scale) unit else current + unit
                    lastKind = Kind.Unit
                    lastValueAtom = cursor
                    cursor += 1
                }

                atom in table.additives -> {
                    val value = table.additives.getValue(atom)
                    current += value
                    lastKind = if (value in 10..19) Kind.Teen else Kind.Additive
                    lastValueAtom = cursor
                    cursor += 1
                }

                atom in table.hundreds -> {
                    if (lastKind == Kind.Hundred) break
                    current = current.coerceAtLeast(1L) * 100L
                    lastKind = Kind.Hundred
                    lastValueAtom = cursor
                    cursor += 1
                }

                atom in table.scales -> {
                    total += current.coerceAtLeast(1L) * table.scales.getValue(atom)
                    current = 0
                    lastKind = Kind.Scale
                    lastValueAtom = cursor
                    cursor += 1
                }

                else -> break
            }
        }

        if (lastValueAtom < 0) return null

        // A lone article ("ein Problem", "un problème", "een probleem") is not a dictated number.
        // Counting atoms rather than words matters because a glued compound such as
        // `eintausendzweihundertvierunddreißig` is one word but clearly not an article.
        val consumedWords = atoms[lastValueAtom].first + 1
        if (consumedWords == 1 && atoms.size == 1 && atoms[0].second in table.bareArticleWords) return null

        return NumberWordNormalizer.ParsedWords(
            value = sign + (total + current),
            consumedWords = consumedWords,
            kind = NumberWordNormalizer.Kind.Number,
        )
    }

    private fun isValueAtom(atom: String, table: NumberTable): Boolean =
        atom in table.units || atom in table.additives || atom in table.hundreds || atom in table.scales

    /** Whole-word match first, then compound decomposition for languages that glue numbers. */
    private fun resolve(word: String, table: NumberTable): List<String>? {
        if (word in table.vocabulary) return listOf(word)
        if (!table.splitsCompounds) return null
        val parts = mutableListOf<String>()
        return if (splitFrom(word, table, parts)) parts else null
    }

    /** Longest match with backtracking: `sechzig` must not degrade into `sechs` plus junk. */
    private fun splitFrom(remaining: String, table: NumberTable, parts: MutableList<String>): Boolean {
        if (remaining.isEmpty()) return true
        for (end in minOf(remaining.length, MAX_COMPOUND_PART) downTo 1) {
            val candidate = remaining.substring(0, end)
            if (candidate in table.vocabulary) {
                parts.add(candidate)
                if (splitFrom(remaining.substring(end), table, parts)) return true
                parts.removeAt(parts.size - 1)
            }
        }
        return false
    }

    /** A run of four or more spoken digits is one sequence: phone numbers, PINs, codes. */
    private fun digitSequence(
        atoms: List<Pair<Int, String>>,
        from: Int,
        table: NumberTable,
    ): NumberWordNormalizer.ParsedWords? {
        if (atoms[from].second == "oh") return null
        var end = from
        while (end < atoms.size && atoms[end].second in table.digitWords) end += 1
        if (end - from < 4) return null
        val digits = buildString(end - from) {
            for (index in from until end) append(table.digitWords.getValue(atoms[index].second))
        }
        return NumberWordNormalizer.ParsedWords(
            value = digits,
            consumedWords = atoms[end - 1].first + 1,
            kind = NumberWordNormalizer.Kind.DigitSequence,
        )
    }
}
