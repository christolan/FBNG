package com.xiaojiwei.fbng.library

import android.content.Context
import org.json.JSONArray

/** Most recently started first; a game has only one entry across launches. */
internal class RecentGames(context: Context) {
    private val preferences = context.getSharedPreferences("library-recent", Context.MODE_PRIVATE)

    fun read(): List<String> = runCatching {
        val names = JSONArray(preferences.getString("sets", "[]"))
        List(names.length()) { names.getString(it) }.distinct()
    }.getOrDefault(emptyList())

    fun record(name: String): List<String> {
        val updated = listOf(name) + read().filterNot { it == name }
        preferences.edit().putString("sets", JSONArray(updated).toString()).apply()
        return updated
    }
}
