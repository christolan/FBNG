package com.xiaojiwei.fbng.library

import android.icu.text.AlphabeticIndex
import android.icu.util.ULocale

internal const val LibraryLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

internal data class LibraryEntry(val game: LibraryGame, val title: String, val letter: Char) {
    fun matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            game.description.contains(query, ignoreCase = true) ||
            game.name.contains(query, ignoreCase = true)
}

internal class LibraryResults(val entries: List<LibraryEntry>) {
    val byName = entries.associateBy { it.game.name }
    val groups = entries.groupBy { it.letter }
    val rowByName = buildMap {
        var row = 0
        for (group in groups.values) {
            row++ // Section heading
            for (entry in group) put(entry.game.name, row++)
        }
    }
}

private val alphabet = AlphabeticIndex<Unit>(ULocale("zh@collation=pinyin"))
private val letterIndex = alphabet.buildImmutableIndex()
private val titleOrder = alphabet.collator.apply { numericCollation = true }.freeze()
private val entryOrder = compareBy<LibraryEntry> { LibraryLetters.indexOf(it.letter) }
    .thenComparator { first, second -> titleOrder.compare(first.title, second.title) }
    .thenBy { it.game.name }

internal fun indexLibrary(games: List<LibraryGame>, titles: Map<String, String>): List<LibraryEntry> =
    games.map { game ->
        val title = titles[game.name] ?: game.description
        LibraryEntry(game, title, indexLetter(title))
    }.sortedWith(entryOrder)

private val leadingMarks = Regex("""^[\p{P}\p{S}\s\p{Z}]+""")

private fun indexLetter(title: String): Char {
    val name = title.replaceFirst(leadingMarks, "")
    if (name.isEmpty() || Character.isDigit(name.codePointAt(0))) return '#'
    val bucket = letterIndex.getBucket(letterIndex.getBucketIndex(name))
    return bucket?.label?.singleOrNull()?.takeIf { it in 'A'..'Z' } ?: '#'
}
