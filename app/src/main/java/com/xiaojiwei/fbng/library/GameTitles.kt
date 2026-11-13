package com.xiaojiwei.fbng.library

import java.io.BufferedReader

internal fun readGameTitles(reader: BufferedReader): Map<String, String> = buildMap {
    reader.lineSequence().forEach { line ->
        val separator = line.indexOf('\t')
        if (separator > 0 && !line.startsWith('#')) {
            val title = line.substring(separator + 1).trim()
            if (title.isNotEmpty()) put(line.substring(0, separator), title)
        }
    }
}
