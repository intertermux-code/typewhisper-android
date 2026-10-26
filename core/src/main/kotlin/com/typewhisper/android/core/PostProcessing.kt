package com.typewhisper.android.core

/**
 * Dictionary and snippet substitution, the step that runs after number normalization
 * and before the text reaches the field.
 *
 * Both are the same mechanism: replace a spoken/trigger phrase with its expansion at
 * word boundaries. Doing it in a single left-to-right pass instead of one regex per
 * entry means replacement text is never re-scanned, so an entry pair that would feed
 * itself (`a -> b`, `b -> c`) still produces the text the user wrote down.
 */
public object PostProcessing {

    public data class DictionaryEntry(val spoken: String, val written: String)

    public data class Snippet(val trigger: String, val expansion: String)

    public fun applyDictionary(text: String, entries: List<DictionaryEntry>): String =
        replacePhrases(text, entries.map { it.spoken to it.written })

    public fun expandSnippets(text: String, snippets: List<Snippet>): String =
        replacePhrases(text, snippets.map { it.trigger to it.expansion })

    private fun replacePhrases(text: String, phrases: List<Pair<String, String>>): String {
        // Longest source first, so "san francisco" wins over "san".
        val usable = phrases
            .filter { it.first.isNotEmpty() && it.first != it.second }
            .sortedByDescending { it.first.length }
        if (usable.isEmpty() || text.isEmpty()) return text

        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val phrase = usable.firstOrNull { matchesAt(text, index, it.first) }
            if (phrase != null) {
                result.append(phrase.second)
                index += phrase.first.length
            } else {
                result.append(text[index])
                index += 1
            }
        }
        return result.toString()
    }

    private fun matchesAt(text: String, index: Int, phrase: String): Boolean {
        val end = index + phrase.length
        if (end > text.length) return false
        if (!text.regionMatches(index, phrase, 0, phrase.length, ignoreCase = true)) return false
        if (index > 0 && isWordCharacter(text[index - 1])) return false
        if (end < text.length && isWordCharacter(text[end])) return false
        return true
    }

    private fun isWordCharacter(character: Char): Boolean = character.isLetterOrDigit()
}
