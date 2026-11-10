package com.xiaojiwei.fbng.library

import java.io.File
import java.security.MessageDigest

/**
 * App-private layout FBNeo searches:
 * roms/kovsh.zip is the game, and the core derives the driver name from that file name.
 * pgm.zip is written both beside the game (the first place the core looks) and at
 * system/fbneo/pgm.zip (the documented BIOS directory).
 */
class AppStorage(val root: File) {
    val romsDir = File(root, "roms")
    val systemDir = File(root, "system")
    val systemFbneoDir = File(systemDir, "fbneo")
    val savesDir = File(root, "saves")
    val stateDir = File(root, "states/kovsh")
    val cacheDir = File(root, "cache")
    val kovshZip = File(romsDir, "kovsh.zip")
    val pgmZip = File(systemFbneoDir, "pgm.zip")
    val pgmZipBesideRom = File(romsDir, "pgm.zip")

    fun ensure() {
        listOf(romsDir, systemFbneoDir, savesDir, stateDir, cacheDir).forEach { it.mkdirs() }
    }

    fun installKovsh(source: File) {
        ensure()
        replace(source, kovshZip)
    }

    fun installPgm(source: File) {
        ensure()
        replace(source, pgmZip)
        mirror(pgmZip, pgmZipBesideRom)
    }

    fun mirrorPgmBesideRom() {
        if (pgmZip.isFile) mirror(pgmZip, pgmZipBesideRom)
    }

    private fun replace(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        source.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun mirror(source: File, dest: File) {
        if (dest.isFile && sha256(source) == sha256(dest)) return
        replace(source, dest)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
