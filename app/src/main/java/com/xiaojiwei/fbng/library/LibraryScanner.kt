package com.xiaojiwei.fbng.library

import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.RomSetMatcher
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.importing.ZipArchives
import java.io.File
import java.util.zip.ZipException

data class InstalledSet(
    val expected: String,
    val present: Boolean,
    val match: SetMatch?,
    val unreadable: Boolean,
)

data class LibrarySnapshot(
    val kovsh: InstalledSet,
    val pgm: InstalledSet,
) {
    val romReady: Boolean
        get() = kovsh.match?.let { it.isClean && it.setName == RomInstaller.KOVSH } == true

    val biosReady: Boolean
        get() = pgm.match?.let { it.isClean && it.setName == RomInstaller.PGM } == true

    val canLaunch: Boolean get() = romReady && biosReady
}

class LibraryScanner(
    private val storage: AppStorage,
    private val catalog: DatCatalog,
) {
    fun scan(): LibrarySnapshot {
        storage.ensure()
        val pgm = inspect(storage.pgmZip, RomInstaller.PGM)
        if (pgm.match?.let { it.isClean && it.setName == RomInstaller.PGM } == true) {
            storage.mirrorPgmBesideRom()
        }
        return LibrarySnapshot(
            kovsh = inspect(storage.kovshZip, RomInstaller.KOVSH),
            pgm = pgm,
        )
    }

    private fun inspect(file: File, expected: String): InstalledSet {
        if (!file.isFile) return InstalledSet(expected, present = false, match = null, unreadable = false)
        return try {
            val match = RomSetMatcher.identify(ZipArchives.read(file), catalog).best
            InstalledSet(expected, present = true, match = match, unreadable = false)
        } catch (_: ZipException) {
            InstalledSet(expected, present = true, match = null, unreadable = true)
        }
    }
}
