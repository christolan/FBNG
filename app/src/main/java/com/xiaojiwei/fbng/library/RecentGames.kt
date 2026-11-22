package com.xiaojiwei.fbng.library

import android.content.Context
import org.json.JSONArray

internal class RecentGames(context: Context) {
    private val preferences = context.getSharedPreferences("library-recent", Context.MODE_PRIVATE)

    fun read(): List<String> = runCatching {
        val names = JSONArray(preferences.getString("sets", "[]"))
        List(names.length()) { names.getString(it) }.distinct()
    }.getOrDefault(emptyList())

    fun playedAt(name: String): Long = preferences.getLong("time:$name", 0)

    fun record(name: String): List<String> {
        val updated = listOf(name) + read().filterNot { it == name }
        preferences.edit().putString("sets", JSONArray(updated).toString())
            .putLong("time:$name", System.currentTimeMillis()).apply()
        return updated
    }
}
