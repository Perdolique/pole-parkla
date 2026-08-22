package com.perdolique.poleparkla.domain

import java.util.Locale

object PlateCandidateParser {
    private val commonEstonianPattern =
        Regex("(?<![A-Z0-9])(\\d{3})[\\s-]*([A-Z]{3})(?![A-Z0-9])")
    private val ambiguousEstonianPattern =
        Regex("(?<![A-Z0-9])([0-9O]{3})[\\s-]*([A-Z0-9]{3})(?![A-Z0-9])")
    private val separatedCandidatePattern =
        Regex("(?<![A-Z0-9])([A-Z0-9]{1,4}(?:[\\s-]+[A-Z0-9]{1,4}){1,3})(?![A-Z0-9])")
    private val compactCandidatePattern =
        Regex("(?<![A-Z0-9])([A-Z0-9]{4,8})(?![A-Z0-9])")

    fun parse(text: String, limit: Int = 5): List<String> {
        val upper = text.uppercase(Locale.ROOT)
        val candidates = buildList {
            commonEstonianPattern.findAll(upper).forEach { match ->
                add("${match.groupValues[1]} ${match.groupValues[2]}")
            }
            ambiguousEstonianPattern.findAll(upper).forEach { match ->
                val digits = match.groupValues[1].replace('O', '0')
                val letters = match.groupValues[2].replace('0', 'O')
                if (digits.all(Char::isDigit) && letters.all(Char::isLetter)) {
                    add("$digits $letters")
                }
            }
            separatedCandidatePattern.findAll(upper).forEach { match ->
                normalize(match.value)?.let(::add)
            }
            compactCandidatePattern.findAll(upper).forEach { match ->
                normalize(match.value)?.let(::add)
            }
        }

        return candidates
            .distinct()
            .sortedWith(compareBy<String>({ rank(it) }, { it.length }, { it }))
            .take(limit)
    }

    fun normalize(raw: String): String? {
        val compact = raw.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        if (compact.length !in 4..8 || compact.all(Char::isLetter)) {
            return null
        }

        val common = Regex("^(\\d{3})([A-Z]{3})$").matchEntire(compact)
        return common?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: compact
    }

    private fun rank(candidate: String): Int = when {
        Regex("^\\d{3} [A-Z]{3}$").matches(candidate) -> 0
        Regex("^\\d{2,4}[A-Z]{2,4}$").matches(candidate.replace(" ", "")) -> 1
        else -> 2
    }
}
