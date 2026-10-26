package com.typewhisper.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Expectations transliterated from the macOS `NumberWordNormalizerTests`, so the two
 * implementations can be diffed behaviour for behaviour.
 */
class NumberWordNormalizerTest {

    private fun normalize(text: String, language: String, minimumValue: Int = 0): String =
        NumberWordNormalizer.normalize(text, language, minimumValue)

    @Test
    fun minimumTenPreservesSmallNumbersAndConvertsWholeExpressions() {
        assertEquals(
            "I was talking to one of my clients about 497 files",
            normalize(
                "I was talking to one of my clients about four hundred ninety seven files",
                "en",
                10,
            ),
        )
        assertEquals(
            "Zero, Three, nine, 10, 11",
            normalize("Zero, Three, nine, ten, eleven", "en", 10),
        )
    }

    @Test
    fun minimumHundredPreservesEntireExpressionAndOriginalSeparators() {
        assertEquals(
            "Twenty-Three, ninety\nnine, 100, 101",
            normalize("Twenty-Three, ninety\nnine, one hundred, one hundred and one", "en", 100),
        )
    }

    @Test
    fun thresholdAppliesToMagnitudeAndOrdinals() {
        assertEquals(
            "minus nine, -10, -100",
            normalize("minus nine, minus ten, negative one hundred", "en", 10),
        )
        assertEquals(
            "fourth, ninth, 10th, 21st",
            normalize("fourth, ninth, tenth, twenty-first", "en", 10),
        )
        assertEquals(
            "twenty-first, 101st",
            normalize("twenty-first, one hundred first", "en", 100),
        )
    }

    @Test
    fun thresholdPreservesDigitSequencesAndTheirLeadingZeros() {
        listOf(
            "zero zero zero one" to "0001",
            "zero zero zero zero" to "0000",
            "four oh oh two" to "4002",
            "one two three" to "one two three",
        ).forEach { (spoken, expected) ->
            assertEquals(expected, normalize(spoken, "en", 100))
        }
    }

    @Test
    fun thresholdKeepsDecimalsNormalizedAcrossLanguages() {
        listOf(
            Triple("en", "minus two point five", "-2.5"),
            Triple("de", "minus zwei komma fünf", "-2,5"),
            Triple("fr", "deux virgule cinq", "2,5"),
            Triple("fr", "3 point 14", "3.14"),
            Triple("es", "dos coma cinco", "2,5"),
            Triple("nl", "twee komma vijf", "2,5"),
            Triple("zh", "二点五", "2.5"),
            Triple("ja", "二点五", "2.5"),
        ).forEach { (language, spoken, expected) ->
            assertEquals("$language: $spoken", expected, normalize(spoken, language, 100))
        }
    }

    @Test
    fun thresholdAcrossSupportedLanguages() {
        listOf(
            Triple("de", "drei, neun, zehn, siebenundneunzig", "drei, neun, 10, 97"),
            Triple("fr", "trois, neuf, dix", "trois, neuf, 10"),
            Triple("es", "tres, nueve, diez", "tres, nueve, 10"),
            Triple("nl", "drie, negen, tien", "drie, negen, 10"),
            Triple("zh", "三，九，十", "三，九，10"),
            Triple("ja", "三、九、十", "三、九、10"),
        ).forEach { (language, spoken, expected) ->
            assertEquals(language, expected, normalize(spoken, language, 10))
        }
    }

    @Test
    fun alwaysConvertsSmallNumbersAndThresholdLeavesExistingDigitsAlone() {
        assertEquals("0, 3, -9", normalize("zero, three, minus nine", "en", 0))
        assertEquals(
            "1 of my clients, 3rd, -2.5, 0001",
            normalize("1 of my clients, 3rd, -2.5, 0001", "en", 100),
        )
    }

    @Test
    fun englishSimpleNumbersNormalizeToDigits() {
        assertEquals("I have 2 questions", normalize("I have two questions", "en"))
    }

    @Test
    fun germanSimpleNumbersNormalizeToDigits() {
        assertEquals("ich habe 2 Fragen", normalize("ich habe zwei Fragen", "de"))
    }

    @Test
    fun englishCompoundNumberNormalizesToDigits() {
        assertEquals("23 files", normalize("twenty three files", "en"))
    }

    @Test
    fun germanCompoundNumberNormalizesToDigits() {
        assertEquals("23 Dateien", normalize("dreiundzwanzig Dateien", "de"))
    }

    @Test
    fun englishScaleNumberNormalizesToDigits() {
        assertEquals("1234", normalize("one thousand two hundred thirty four", "en"))
    }

    @Test
    fun germanScaleNumberNormalizesToDigits() {
        assertEquals("1234", normalize("eintausendzweihundertvierunddreißig", "de"))
    }

    @Test
    fun englishNegativeDecimalNormalizesToDigits() {
        assertEquals("-2.5", normalize("minus two point five", "en"))
    }

    @Test
    fun englishAndSeparatorDoesNotMergeIndependentNumbers() {
        assertEquals("2 and 3", normalize("two and three", "en"))
        assertEquals("between 2 and 3 minutes", normalize("between two and three minutes", "en"))
    }

    @Test
    fun englishHundredAndScaleAndStillNormalize() {
        assertEquals("123", normalize("one hundred and twenty three", "en"))
        assertEquals("1005", normalize("one thousand and five", "en"))
    }

    @Test
    fun englishCompoundOrdinalNormalizesToDigits() {
        assertEquals("28th", normalize("twenty eighth", "en"))
        assertEquals("31st", normalize("thirty first", "en"))
        assertEquals("42nd", normalize("forty second", "en"))
        assertEquals("23rd", normalize("twenty third", "en"))
        assertEquals("23rd", normalize("twenty-third", "en"))
        assertEquals("101st", normalize("one hundred first", "en"))
        assertEquals("121st", normalize("one hundred twenty first", "en"))
    }

    @Test
    fun englishStandaloneOrdinalNormalizesToDigits() {
        assertEquals("8th", normalize("eighth", "en"))
        assertEquals("12th", normalize("twelfth", "en"))
        assertEquals("20th", normalize("twentieth", "en"))
        assertEquals("30th", normalize("thirtieth", "en"))
    }

    @Test
    fun englishOrdinalSuffixSelection() {
        assertEquals("21st", normalize("twenty first", "en"))
        assertEquals("22nd", normalize("twenty second", "en"))
        assertEquals("23rd", normalize("twenty third", "en"))
        assertEquals("4th", normalize("fourth", "en"))
        assertEquals("11th", normalize("eleventh", "en"))
        assertEquals("12th", normalize("twelfth", "en"))
        assertEquals("13th", normalize("thirteenth", "en"))
    }

    @Test
    fun englishBareFirstSecondThirdAreLeftUnchanged() {
        assertEquals("first", normalize("first", "en"))
        assertEquals("second", normalize("second", "en"))
        assertEquals("third", normalize("third", "en"))
        assertEquals("first, let's start", normalize("first, let's start", "en"))
        assertEquals("wait a second", normalize("wait a second", "en"))
    }

    @Test
    fun englishOrdinalWithinSentenceNormalizesToDigits() {
        assertEquals("the 31st of May", normalize("the thirty first of May", "en"))
        assertEquals("every 24th hour", normalize("every twenty fourth hour", "en"))
    }

    @Test
    fun englishOrdinalDigitOutputIsIdempotent() {
        assertEquals("28th", normalize("28th", "en"))
        assertEquals("the 31st of May", normalize("the 31st of May", "en"))
    }

    @Test
    fun englishDigitSequenceJoinsBareSingleDigits() {
        assertEquals("1984", normalize("one nine eight four", "en"))
        assertEquals("my pin is 1234", normalize("my pin is one two three four", "en"))
        assertEquals("call 5551212 now", normalize("call five five five one two one two now", "en"))
        assertEquals("1234 dogs", normalize("one two three four dogs", "en"))
    }

    @Test
    fun englishDigitSequenceReadsOhAsZeroWhenFlanked() {
        assertEquals("5550123", normalize("five five five oh one two three", "en"))
        assertEquals("4002", normalize("four oh oh two", "en"))
        assertEquals("0123", normalize("zero one two three", "en"))
    }

    @Test
    fun englishShortDigitRunsStaySplit() {
        assertEquals("1 2 3", normalize("one two three", "en"))
        assertEquals("4 oh 2", normalize("four oh two", "en"))
    }

    @Test
    fun englishDigitSequenceDoesNotDisturbCardinalsOrOrdinals() {
        assertEquals("100", normalize("one hundred", "en"))
        assertEquals("2000", normalize("two thousand", "en"))
        assertEquals("23rd", normalize("twenty third", "en"))
        assertEquals("2.5", normalize("two point five", "en"))
    }

    @Test
    fun englishDigitSequenceOutputIsIdempotent() {
        assertEquals("1984", normalize("1984", "en"))
        assertEquals("5550123", normalize("5550123", "en"))
    }

    @Test
    fun germanNegativeDecimalNormalizesToDigits() {
        assertEquals("-2,5", normalize("minus zwei komma fünf", "de"))
    }

    @Test
    fun frenchNumbersNormalizeToDigits() {
        assertEquals("J'ai 2 questions", normalize("J'ai deux questions", "fr"))
        assertEquals("23 fichiers", normalize("vingt trois fichiers", "fr"))
        assertEquals("1234", normalize("mille deux cent trente quatre", "fr"))
        assertEquals("-2,5", normalize("moins deux virgule cinq", "fr"))
        assertEquals("3.6", normalize("trois point six", "fr"))
        assertEquals("-3.6", normalize("moins trois point six", "fr"))
    }

    @Test
    fun frenchDigitPointDecimalsNormalizeToDigits() {
        assertEquals("3.6", normalize("3 point 6", "fr"))
        assertEquals("2.75", normalize("2 point 75", "fr"))
        assertEquals(
            "la valeur est 3.6 aujourd'hui",
            normalize("la valeur est 3 point 6 aujourd'hui", "fr"),
        )
        assertEquals("3.6", normalize("3 Point 6", "fr"))
    }

    @Test
    fun frenchPointIsPreservedOutsideDigitDecimals() {
        assertEquals("point final", normalize("point final", "fr"))
        assertEquals("au point du jour", normalize("au point du jour", "fr"))
        assertEquals("3 point final", normalize("3 point final", "fr"))
        assertEquals("3 point 6", normalize("3 point 6", "en"))
    }

    @Test
    fun frenchArticleOneIsPreservedOutsideClearNumberConstructs() {
        assertEquals("j'ai un problème", normalize("j'ai un problème", "fr"))
        assertEquals("1000000 de lignes", normalize("un million de lignes", "fr"))
    }

    @Test
    fun spanishNumbersNormalizeToDigits() {
        assertEquals("tengo 2 preguntas", normalize("tengo dos preguntas", "es"))
        assertEquals("23 archivos", normalize("veintitrés archivos", "es"))
        assertEquals("23 archivos", normalize("veinte y tres archivos", "es"))
        assertEquals("1234", normalize("mil doscientos treinta y cuatro", "es"))
        assertEquals("-2,5", normalize("menos dos coma cinco", "es"))
    }

    @Test
    fun spanishArticleOneIsPreservedOutsideClearNumberConstructs() {
        assertEquals("tengo un problema", normalize("tengo un problema", "es"))
        assertEquals("1000000 de filas", normalize("un millón de filas", "es"))
    }

    @Test
    fun dutchNumbersNormalizeToDigits() {
        assertEquals("ik heb 2 vragen", normalize("ik heb twee vragen", "nl"))
        assertEquals("23 bestanden", normalize("drieëntwintig bestanden", "nl"))
        assertEquals("22 bestanden", normalize("twee en twintig bestanden", "nl"))
        assertEquals("1234", normalize("duizend tweehonderd vierendertig", "nl"))
        assertEquals("335", normalize("driehonderdvijfendertig", "nl"))
        assertEquals("-2,5", normalize("min twee komma vijf", "nl"))
        assertEquals("-1,5", normalize("minus een komma vijf", "nl"))
    }

    @Test
    fun dutchArticleOneIsPreservedOutsideClearNumberConstructs() {
        assertEquals("ik heb een probleem", normalize("ik heb een probleem", "nl"))
        assertEquals("1000000 regels", normalize("een miljoen regels", "nl"))
        assertEquals("1,5 seconden", normalize("een komma vijf seconden", "nl"))
    }

    @Test
    fun chineseHanNumbersNormalizeToDigits() {
        assertEquals("我有23个文件", normalize("我有二十三个文件", "zh"))
        assertEquals("1234", normalize("一千二百三十四", "zh"))
        assertEquals("-2.5", normalize("负二点五", "zh"))
    }

    @Test
    fun japaneseHanNumbersNormalizeToDigits() {
        assertEquals("23個のファイル", normalize("二十三個のファイル", "ja"))
        assertEquals("1234", normalize("千二百三十四", "ja"))
        assertEquals("-2.5", normalize("負二点五", "ja"))
    }

    @Test
    fun japaneseSingleKanjiInWordsIsPreserved() {
        assertEquals("一緒に行く", normalize("一緒に行く", "ja"))
    }

    @Test
    fun unsupportedLanguageIsNoOp() {
        assertEquals("twenty three", normalize("twenty three", "it"))
    }

    @Test
    fun alreadyDigitTextIsNoOp() {
        assertEquals("I have 23 files", normalize("I have 23 files", "en"))
    }

    @Test
    fun germanArticleOneIsPreservedOutsideClearNumberConstructs() {
        assertEquals("ich habe ein Problem", normalize("ich habe ein Problem", "de"))
        assertEquals("100 Euro", normalize("ein hundert Euro", "de"))
    }
}
