package com.xiaojiwei.fbng.ui

import android.content.Context
import com.xiaojiwei.fbng.ui.theme.CornerRadiusOption

internal class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun readCornerRadius(): CornerRadiusOption {
        val name = preferences.getString("corner-radius", null)
        return CornerRadiusOption.entries.firstOrNull { it.name == name } ?: CornerRadiusOption.Default
    }

    fun writeCornerRadius(option: CornerRadiusOption) {
        preferences.edit().putString("corner-radius", option.name).apply()
    }
}
