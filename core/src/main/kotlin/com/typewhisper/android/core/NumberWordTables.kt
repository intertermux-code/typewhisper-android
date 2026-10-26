package com.typewhisper.android.core

/**
 * Per-language vocabularies for [EuropeanNumberWords]. Only word lists live here;
 * the arithmetic is shared.
 *
 * Accents are folded before matching, so `fünf` is looked up as `funf` and
 * `veintitrés` as `veintitres`. `ß` is not an accent and survives folding, which
 * is why `dreißig` also carries the `dreissig` spelling.
 */
internal val EnglishNumberWords = NumberTable(
    units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    ),
    additives = mapOf(
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    ),
    hundreds = setOf("hundred"),
    scales = mapOf(
        "thousand" to 1_000L, "million" to 1_000_000L,
        "billion" to 1_000_000_000L, "trillion" to 1_000_000_000_000L,
    ),
    skipWords = setOf("and"),
    negativeWords = setOf("minus", "negative"),
    decimalWords = mapOf("point" to "."),
    ordinals = mapOf(
        "first" to OrdinalValue(1, "st"), "second" to OrdinalValue(2, "nd"),
        "third" to OrdinalValue(3, "rd"), "fourth" to OrdinalValue(4, "th"),
        "fifth" to OrdinalValue(5, "th"), "sixth" to OrdinalValue(6, "th"),
        "seventh" to OrdinalValue(7, "th"), "eighth" to OrdinalValue(8, "th"),
        "ninth" to OrdinalValue(9, "th"), "tenth" to OrdinalValue(10, "th"),
        "eleventh" to OrdinalValue(11, "th"), "twelfth" to OrdinalValue(12, "th"),
        "thirteenth" to OrdinalValue(13, "th"), "fourteenth" to OrdinalValue(14, "th"),
        "fifteenth" to OrdinalValue(15, "th"), "sixteenth" to OrdinalValue(16, "th"),
        "seventeenth" to OrdinalValue(17, "th"), "eighteenth" to OrdinalValue(18, "th"),
        "nineteenth" to OrdinalValue(19, "th"), "twentieth" to OrdinalValue(20, "th"),
        "thirtieth" to OrdinalValue(30, "th"), "fortieth" to OrdinalValue(40, "th"),
        "fiftieth" to OrdinalValue(50, "th"), "sixtieth" to OrdinalValue(60, "th"),
        "seventieth" to OrdinalValue(70, "th"), "eightieth" to OrdinalValue(80, "th"),
        "ninetieth" to OrdinalValue(90, "th"),
    ),
    digitWords = mapOf(
        "zero" to "0", "oh" to "0", "one" to "1", "two" to "2", "three" to "3",
        "four" to "4", "five" to "5", "six" to "6", "seven" to "7", "eight" to "8",
        "nine" to "9",
    ),
    supportsDigitSequences = true,
)

internal val GermanNumberWords = NumberTable(
    units = mapOf(
        "null" to 0, "eins" to 1, "ein" to 1, "eine" to 1, "zwei" to 2, "drei" to 3,
        "vier" to 4, "funf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9,
    ),
    additives = mapOf(
        "zehn" to 10, "elf" to 11, "zwolf" to 12, "dreizehn" to 13, "vierzehn" to 14,
        "funfzehn" to 15, "sechzehn" to 16, "siebzehn" to 17, "achtzehn" to 18, "neunzehn" to 19,
        "zwanzig" to 20, "dreißig" to 30, "dreissig" to 30, "vierzig" to 40, "funfzig" to 50,
        "sechzig" to 60, "siebzig" to 70, "achtzig" to 80, "neunzig" to 90,
    ),
    hundreds = setOf("hundert"),
    scales = mapOf(
        "tausend" to 1_000L, "million" to 1_000_000L, "millionen" to 1_000_000L,
        "milliarde" to 1_000_000_000L, "milliarden" to 1_000_000_000L,
    ),
    skipWords = setOf("und"),
    negativeWords = setOf("minus", "negativ"),
    decimalWords = mapOf("komma" to ","),
    bareArticleWords = setOf("ein", "eine"),
    splitsCompounds = true,
)

internal val FrenchNumberWords = NumberTable(
    units = mapOf(
        "zero" to 0, "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4,
        "cinq" to 5, "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9,
    ),
    additives = mapOf(
        "dix" to 10, "onze" to 11, "douze" to 12, "treize" to 13, "quatorze" to 14,
        "quinze" to 15, "seize" to 16, "vingt" to 20, "trente" to 30, "quarante" to 40,
        "cinquante" to 50, "soixante" to 60, "septante" to 70, "huitante" to 80, "nonante" to 80,
    ),
    hundreds = setOf("cent", "cents"),
    scales = mapOf(
        "mille" to 1_000L, "million" to 1_000_000L, "millions" to 1_000_000L,
        "milliard" to 1_000_000_000L, "milliards" to 1_000_000_000L,
    ),
    skipWords = setOf("et"),
    negativeWords = setOf("moins"),
    decimalWords = mapOf("virgule" to ",", "point" to "."),
    bareArticleWords = setOf("un", "une"),
    allowsUnitAfterTeen = true,
)

internal val SpanishNumberWords = NumberTable(
    units = mapOf(
        "cero" to 0, "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3,
        "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
    ),
    additives = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18,
        "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30,
        "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
        "ochenta" to 80, "noventa" to 90, "ciento" to 100, "cien" to 100,
        "doscientos" to 200, "doscientas" to 200, "trescientos" to 300, "trescientas" to 300,
        "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500,
        "quinientas" to 500, "seiscientos" to 600, "seiscientas" to 600,
        "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900,
    ),
    hundreds = emptySet(),
    scales = mapOf(
        "mil" to 1_000L, "millon" to 1_000_000L, "millones" to 1_000_000L,
        "billon" to 1_000_000_000_000L,
    ),
    skipWords = setOf("y"),
    negativeWords = setOf("menos"),
    decimalWords = mapOf("coma" to ","),
    bareArticleWords = setOf("un", "una"),
)

internal val DutchNumberWords = NumberTable(
    units = mapOf(
        "nul" to 0, "een" to 1, "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5,
        "zes" to 6, "zeven" to 7, "acht" to 8, "negen" to 9,
    ),
    additives = mapOf(
        "tien" to 10, "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14,
        "vijftien" to 15, "zestien" to 16, "zeventien" to 17, "achttien" to 18,
        "negentien" to 19, "twintig" to 20, "dertig" to 30, "veertig" to 40, "vijftig" to 50,
        "zestig" to 60, "zeventig" to 70, "tachtig" to 80, "negentig" to 90,
    ),
    hundreds = setOf("honderd"),
    scales = mapOf(
        "duizend" to 1_000L, "miljoen" to 1_000_000L, "miljard" to 1_000_000_000L,
    ),
    skipWords = setOf("en"),
    negativeWords = setOf("min", "minus"),
    decimalWords = mapOf("komma" to ","),
    bareArticleWords = setOf("een"),
    splitsCompounds = true,
)

internal object EnglishNumberWordParser {
    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? =
        EuropeanNumberWords.parse(words, EnglishNumberWords)
}

internal object GermanNumberWordParser {
    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? =
        EuropeanNumberWords.parse(words, GermanNumberWords)
}

internal object FrenchNumberWordParser {
    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? =
        EuropeanNumberWords.parse(words, FrenchNumberWords)
}

internal object SpanishNumberWordParser {
    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? =
        EuropeanNumberWords.parse(words, SpanishNumberWords)
}

internal object DutchNumberWordParser {
    fun parse(words: List<String>): NumberWordNormalizer.ParsedWords? =
        EuropeanNumberWords.parse(words, DutchNumberWords)
}
