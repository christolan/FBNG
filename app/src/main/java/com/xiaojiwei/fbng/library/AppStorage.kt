package com.xiaojiwei.fbng.library

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AppStorage(val root: File) {
    val romsDir = File(root, "roms")
    val systemDir = File(root, "system")
    val systemFbneoDir = File(systemDir, "fbneo")
    val savesDir = File(root, "saves")
    val cacheDir = File(root, "cache")
    internal val playDir = File(cacheDir, "play")

    fun stateDir(setName: String) = File(root, "states/${checkedName(setName)}")

    fun archive(setName: String, isBios: Boolean): File =
        File(if (isBios) systemFbneoDir else romsDir, "${checkedName(setName)}.zip")

    fun ensure() {
        listOf(romsDir, systemFbneoDir, savesDir, cacheDir).forEach { it.mkdirs() }
    }

    fun sourceName(setName: String, isBios: Boolean): String = runCatching {
        File(root, "source-names/${checkedName(setName)}.txt").takeIf { it.isFile }?.readText()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: archive(setName, isBios).name

    fun install(source: File, setName: String, isBios: Boolean, displayName: String? = null) {
        val destination = archive(setName, isBios)
        val parent = checkNotNull(destination.parentFile)
        parent.mkdirs()
        val temporary = File.createTempFile(destination.name, ".tmp", parent)
        try {
            source.copyTo(temporary, overwrite = true)
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            if (displayName != null) {
                val names = File(root, "source-names").apply { mkdirs() }
                val metadata = File.createTempFile("$setName.name", ".tmp", names)
                try {
                    metadata.writeText(displayName)
                    Files.move(metadata.toPath(), File(names, "$setName.txt").toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    metadata.delete()
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private fun checkedName(setName: String): String {
        require(setName.matches(SET_NAME)) { "Invalid FBNeo set name: $setName" }
        return setName
    }

    private companion object {
        val SET_NAME = Regex("[a-z0-9_]+")
    }
}
