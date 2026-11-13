package com.xiaojiwei.fbng.library

import android.icu.text.AlphabeticIndex
import android.icu.text.Transliterator
import android.icu.util.ULocale
import java.util.Locale

internal const val LibraryLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

internal data class LibraryEntry(val game: LibraryGame, val title: String, val letter: Char) {
    val initials: String by lazy { titleInitials(title) }

    fun matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            game.description.contains(query, ignoreCase = true) ||
            "${game.name}.zip".contains(query, ignoreCase = true) ||
            game.fileNames.any { it.contains(query, ignoreCase = true) } ||
            query.filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() }
                ?.let { initials.contains(it, ignoreCase = true) } == true
}

private val pinyin by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
private val nameParts = Regex("\\p{IsHan}+|[A-Za-z0-9]+")
private val pinyinParts = Regex("[A-Za-z]+|[0-9]+")

/** Keep ASCII names/numbers, and reduce each Chinese syllable to its initial. */
private fun titleInitials(title: String): String = buildString {
    for (part in nameParts.findAll(title)) {
        if (Character.UnicodeScript.of(part.value.codePointAt(0)) == Character.UnicodeScript.HAN) {
            val latin = synchronized(pinyin) { pinyin.transliterate(part.value) }
            for (syllable in pinyinParts.findAll(latin)) {
                append(if (syllable.value.first().isDigit()) syllable.value else syllable.value.take(1))
            }
        } else append(part.value)
    }
}.uppercase(Locale.ROOT)

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
