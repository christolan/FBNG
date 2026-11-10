package com.xiaojiwei.fbng.importing

import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.RomSetMatcher
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.library.AppStorage
import java.io.File
import java.util.zip.ZipException

enum class InstallOutcome {
    INSTALLED,
    REJECTED_OTHER_SET,
    REJECTED_MISMATCH,
    UNRECOGNIZED,
    UNREADABLE,
}

data class ImportReport(
    val displayName: String,
    val outcome: InstallOutcome,
    val match: SetMatch?,
    val installedFileName: String?,
)

class RomInstaller(
    private val storage: AppStorage,
    private val catalog: DatCatalog,
) {
    fun importFile(file: File, displayName: String): ImportReport {
        val match = try {
            RomSetMatcher.identify(ZipArchives.read(file), catalog).best
        } catch (_: ZipException) {
            return ImportReport(displayName, InstallOutcome.UNREADABLE, null, null)
        }
        if (match == null) {
            return ImportReport(displayName, InstallOutcome.UNRECOGNIZED, null, null)
        }
        if (!match.isClean) {
            return ImportReport(displayName, InstallOutcome.REJECTED_MISMATCH, match, null)
        }
        return when (match.setName) {
            KOVSH -> {
                storage.installKovsh(file)
                ImportReport(displayName, InstallOutcome.INSTALLED, match, "kovsh.zip")
            }
            PGM -> {
                storage.installPgm(file)
                ImportReport(displayName, InstallOutcome.INSTALLED, match, "pgm.zip")
            }
            else -> ImportReport(displayName, InstallOutcome.REJECTED_OTHER_SET, match, null)
        }
    }

    companion object {
        const val KOVSH = "kovsh"
        const val PGM = "pgm"
        val ACCEPTED = setOf(KOVSH, PGM)
    }
}
