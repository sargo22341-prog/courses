package org.opensources.courses.feature.voice.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/** Converts only a leading quantity; product names and unrecognized words remain reviewable. */
object SpokenQuantityParser {
    fun vocabulary(language: AppLanguage): Set<String> = lexicon(language).keys

    fun replaceLeading(text: String, language: AppLanguage): String {
        val normalized = text.replace(Regex("(?<=\\p{L})[-‑](?=\\p{L})"), " ")
        val lexicon = lexicon(language)
        val tokens = Regex("\\S+").findAll(normalized).take(12).toList()
        val words = tokens.map { it.value }
        var count = 0
        var value = 0
        var thousands = 0
        while (count < minOf(words.size - 1, 8)) {
            if (words[count] == "et" && count > 0 && lexicon.containsKey(words[count + 1])) { count++; continue }
            val number = lexicon[words[count]] ?: break
            if (language == AppLanguage.FRENCH && number == 4 && words.getOrNull(count + 1) in listOf("vingt", "vingts")) {
                value += 80
                count += 2
                continue
            }
            when (number) {
                100 -> value = maxOf(value, 1) * 100
                1_000 -> { thousands += maxOf(value, 1) * 1_000; value = 0 }
                else -> value += number
            }
            count++
        }
        value += thousands
        if (count == 0 || count >= tokens.size || value !in 0..10_000) return text
        return replaceDecimal(text, tokens, count, value, language)
            ?: if (value > 0) "$value ${text.substring(tokens[count].range.first)}" else text
    }

    private fun replaceDecimal(
        text: String, tokens: List<MatchResult>, count: Int, value: Int, language: AppLanguage,
    ): String? {
        val decimalWord = when (language) {
            AppLanguage.FRENCH -> "virgule"
            AppLanguage.ENGLISH -> "point"
            AppLanguage.GERMAN -> "komma"
            AppLanguage.SPANISH -> "coma"
            AppLanguage.ITALIAN -> "virgola"
            AppLanguage.PORTUGUESE -> "vírgula"
        }
        if (tokens.getOrNull(count)?.value != decimalWord) return null
        val lexicon = lexicon(language)
        val digits = StringBuilder()
        var end = count + 1
        while (end < tokens.size - 1 && digits.length < 3) {
            val digit = lexicon[tokens[end].value] ?: tokens[end].value.toIntOrNull() ?: break
            if (digit !in 0..9) break
            digits.append(digit)
            end++
        }
        if (digits.isEmpty()) return null
        return "$value.$digits ${text.substring(tokens[end].range.first)}"
    }

    private fun lexicon(language: AppLanguage): Map<String, Int> = when (language) {
        AppLanguage.FRENCH -> FRENCH
        AppLanguage.ENGLISH -> ENGLISH
        AppLanguage.GERMAN -> GERMAN
        AppLanguage.SPANISH -> SPANISH
        AppLanguage.ITALIAN -> ITALIAN
        AppLanguage.PORTUGUESE -> PORTUGUESE
    }

    private fun numbers(vararg groups: Pair<Int, String>): Map<String, Int> = buildMap {
        for ((value, spellings) in groups) for (word in spellings.split(' ')) put(word, value)
    }

    private val FRENCH = numbers(
        0 to "zéro", 1 to "un une", 2 to "deux", 3 to "trois", 4 to "quatre", 5 to "cinq", 6 to "six",
        7 to "sept", 8 to "huit", 9 to "neuf", 10 to "dix", 11 to "onze", 12 to "douze", 13 to "treize",
        14 to "quatorze", 15 to "quinze", 16 to "seize", 20 to "vingt vingts", 30 to "trente", 40 to "quarante",
        50 to "cinquante", 60 to "soixante", 100 to "cent cents", 1_000 to "mille",
    )
    private val ENGLISH = numbers(1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five", 6 to "six",
        7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten", 20 to "twenty", 30 to "thirty", 50 to "fifty", 100 to "hundred", 1_000 to "thousand")
    private val GERMAN = numbers(1 to "ein eins eine", 2 to "zwei", 3 to "drei", 4 to "vier", 5 to "fünf", 6 to "sechs",
        7 to "sieben", 8 to "acht", 9 to "neun", 10 to "zehn", 100 to "hundert einhundert", 200 to "zweihundert", 500 to "fünfhundert", 1_000 to "tausend")
    private val SPANISH = numbers(1 to "uno un una", 2 to "dos", 3 to "tres", 4 to "cuatro", 5 to "cinco", 6 to "seis",
        7 to "siete", 8 to "ocho", 9 to "nueve", 10 to "diez", 100 to "cien ciento", 200 to "doscientos", 500 to "quinientos", 1_000 to "mil")
    private val ITALIAN = numbers(1 to "uno un una", 2 to "due", 3 to "tre", 4 to "quattro", 5 to "cinque", 6 to "sei",
        7 to "sette", 8 to "otto", 9 to "nove", 10 to "dieci", 100 to "cento", 200 to "duecento", 500 to "cinquecento", 1_000 to "mille")
    private val PORTUGUESE = numbers(1 to "um uma", 2 to "dois duas", 3 to "três", 4 to "quatro", 5 to "cinco", 6 to "seis",
        7 to "sete", 8 to "oito", 9 to "nove", 10 to "dez", 100 to "cem cento", 200 to "duzentos", 500 to "quinhentos", 1_000 to "mil")
}
