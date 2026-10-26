package com.typewhisper.android.core

/**
 * Spoken numbers to digits, ported from TypeWhisper (macOS) `NumberWordNormalizer`.
 *
 * The token model and the entry point are kept identical to the Swift original so
 * the two implementations can be diffed behaviour for behaviour: the pipeline
 * tokenises into word / digit / CJK-number runs, tries a digit-word decimal in
 * front of a `point`-style connector, and otherwise asks the language parser for
 * the longest number expression starting at each word.
 */
public object NumberWordNormalizer {

    private val supportedLanguageCodes = setOf("en", "de", "fr", "es", "nl", "zh", "ja")
    private val cjkNumberCharacters = "零〇一二两兩三四五六七八九十百千万萬亿億点點負负".toSet()

    public enum class Kind { Number, Decimal, DigitSequence }

    /**
     * @param value the digits to emit, already signed and punctuated.
     * @param consumedWords how many word candidates the parse took, counted from
     *   the start of the candidate list.
     */
    public data class ParsedWords(
        val value: String,
        val consumedWords: Int,
        val kind: Kind = Kind.Number,
    ) {
        public fun shouldNormalize(minimumValue: Int): Boolean {
            if (minimumValue <= 0 || kind != Kind.Number) return true
            // Parsers emit ASCII digits, optionally signed and with an ordinal suffix.
            val magnitude = value.trimStart('-').takeWhile { it in '0'..'9' }
            val number = magnitude.toULongOrNull() ?: return false
            return number >= minimumValue.toULong()
        }
    }

    internal enum class TokenKind {
        Word, Digit, CjkNumber, Other;

        val isWord: Boolean get() = this == Word || this == CjkNumber
        val isDigit: Boolean get() = this == Digit
    }

    internal data class Token(val text: String, val kind: TokenKind)

    private data class WordCandidate(val tokenIndex: Int, val text: String)

    private data class ParsedNumber(val words: ParsedWords, val endIndex: Int)

    public fun normalize(text: String, language: String?, minimumValue: Int = 0): String {
        val languageCode = PunctuationLanguageNormalizer.normalize(language)
            ?: return text
        if (languageCode !in supportedLanguageCodes || text.isEmpty()) return text

        val tokens = tokenize(text)
        if (tokens.none { it.kind.isWord }) return text

        val result = StringBuilder(text.length)
        var index = 0
        while (index < tokens.size) {
            val decimal = parseDigitDecimal(index, tokens, languageCode)
            if (decimal != null) {
                result.append(decimal.words.value)
                index = decimal.endIndex
                continue
            }
            if (tokens[index].kind.isWord) {
                val parsed = parseNumber(index, tokens, languageCode)
                if (parsed != null) {
                    if (parsed.words.shouldNormalize(minimumValue)) {
                        result.append(parsed.words.value)
                    } else {
                        // Consume the entire expression even when keeping its original spelling.
                        for (token in tokens.subList(index, parsed.endIndex)) {
                            result.append(token.text)
                        }
                    }
                    index = parsed.endIndex
                    continue
                }
            }
            result.append(tokens[index].text)
            index += 1
        }

        return result.toString()
    }

    /** `3 point 6` in French: digit tokens joined by a spoken decimal word. */
    private fun parseDigitDecimal(index: Int, tokens: List<Token>, languageCode: String): ParsedNumber? {
        val decimalSeparator = digitDecimalSeparator(languageCode) ?: return null
        if (index + 4 >= tokens.size) return null
        if (!tokens[index].kind.isDigit) return null
        if (!isWordConnector(tokens[index + 1].text)) return null
        if (!tokens[index + 2].kind.isWord) return null
        if (normalizedDecimalWord(tokens[index + 2].text) != "point") return null
        if (!isWordConnector(tokens[index + 3].text)) return null
        if (!tokens[index + 4].kind.isDigit) return null

        return ParsedNumber(
            words = ParsedWords(
                value = tokens[index].text + decimalSeparator + tokens[index + 4].text,
                consumedWords = 1,
                kind = Kind.Decimal,
            ),
            endIndex = index + 5,
        )
    }

    private fun digitDecimalSeparator(languageCode: String): String? = when (languageCode) {
        "fr" -> "."
        else -> null
    }

    private fun normalizedDecimalWord(word: String): String = PunctuationLanguageNormalizer.fold(word)

    private fun parseNumber(index: Int, tokens: List<Token>, languageCode: String): ParsedNumber? {
        val words = wordCandidates(index, tokens)
        if (words.isEmpty()) return null

        val wordTexts = words.map { it.text }
        val parsed: ParsedWords? = when (languageCode) {
            "en" -> EnglishNumberWordParser.parse(wordTexts)
            "de" -> GermanNumberWordParser.parse(wordTexts)
            "fr" -> FrenchNumberWordParser.parse(wordTexts)
            "es" -> SpanishNumberWordParser.parse(wordTexts)
            "nl" -> DutchNumberWordParser.parse(wordTexts)
            "zh" -> CjkNumberWordParser.parse(wordTexts)
            "ja" -> CjkNumberWordParser.parse(wordTexts)
            else -> null
        }

        if (parsed == null || parsed.consumedWords <= 0 || parsed.consumedWords > words.size) return null

        val finalTokenIndex = words[parsed.consumedWords - 1].tokenIndex
        return ParsedNumber(words = parsed, endIndex = finalTokenIndex + 1)
    }

    /**
     * The word run starting at [index], walking across word connectors (spaces,
     * hyphens) so `twenty-first` and `four hundred` both arrive as one candidate list.
     */
    private fun wordCandidates(index: Int, tokens: List<Token>): List<WordCandidate> {
        val words = mutableListOf<WordCandidate>()
        var current = index

        while (current < tokens.size && tokens[current].kind.isWord) {
            words.add(WordCandidate(tokenIndex = current, text = tokens[current].text))

            val separatorIndex = current + 1
            val nextWordIndex = current + 2
            if (separatorIndex >= tokens.size ||
                nextWordIndex >= tokens.size ||
                !tokens[nextWordIndex].kind.isWord ||
                !isWordConnector(tokens[separatorIndex].text)
            ) {
                break
            }

            current = nextWordIndex
        }

        return words
    }

    internal fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var current = StringBuilder()
        var currentKind: TokenKind? = null

        for (character in text) {
            val kind = tokenKind(character)
            if (currentKind == kind) {
                current.append(character)
            } else {
                if (current.isNotEmpty()) currentKind?.let { tokens.add(Token(current.toString(), it)) }
                current = StringBuilder(character.toString())
                currentKind = kind
            }
        }
        if (current.isNotEmpty()) currentKind?.let { tokens.add(Token(current.toString(), it)) }

        return tokens
    }

    private fun tokenKind(character: Char): TokenKind = when {
        cjkNumberCharacters.contains(character) -> TokenKind.CjkNumber
        character.isDigit() -> TokenKind.Digit
        character.isLetter() -> TokenKind.Word
        else -> TokenKind.Other
    }

    /** Spaces, newlines and hyphens (incl. non-breaking) glue words into one expression. */
    internal fun isWordConnector(text: String): Boolean =
        text.isNotEmpty() && text.all { it.isWhitespace() || it == '-' || it == '‑' }
}
