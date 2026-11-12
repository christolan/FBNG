package com.xiaojiwei.fbng.importing

import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.RomSetMatcher
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.library.AppStorage
import java.io.File
import java.io.IOException

enum class InstallOutcome {
    INSTALLED,
    REJECTED_MISMATCH,
    AMBIGUOUS,
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
        val identification = try {
            RomSetMatcher.identify(ZipArchives.read(file), catalog, displayName)
        } catch (_: IOException) {
            return ImportReport(displayName, InstallOutcome.UNREADABLE, null, null)
        }
        val match = identification.best ?: return ImportReport(
            displayName,
            if (identification.ambiguous) InstallOutcome.AMBIGUOUS else InstallOutcome.UNRECOGNIZED,
            null,
            null,
        )
        if (!match.isClean) {
            return ImportReport(displayName, InstallOutcome.REJECTED_MISMATCH, match, null)
        }
        storage.install(file, match.setName, match.isBios)
        return ImportReport(displayName, InstallOutcome.INSTALLED, match, "${match.setName}.zip")
    }
}
