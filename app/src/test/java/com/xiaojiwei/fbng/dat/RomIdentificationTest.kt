package com.xiaojiwei.fbng.dat

import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.importing.ZipArchives
import com.xiaojiwei.fbng.library.AppStorage
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RomIdentificationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val program = bytes(0x10, 0x20, 0x30, 0x40)
    private val asic = bytes(0x01, 0x02, 0x03, 0x04)
    private val bios = bytes(0xAA, 0xBB, 0xCC, 0xDD)

    private val catalog = parseCatalog(
        """
        <datafile>
          <header><version>test</version></header>
          <game isbios="yes" name="pgm">
            <description>PGM BIOS</description>
            <rom name="bios.u42" size="${bios.size}" crc="${crcHex(crc(bios))}"/>
          </game>
          <game name="kovsh" romof="pgm">
            <description>Knights of Valour Super Heroes</description>
            <rom name="pgm_p0605_v104.u1" size="${program.size}" crc="${crcHex(crc(program))}"/>
            <rom name="kovsh_v100_china.asic" size="${asic.size}" crc="${crcHex(crc(asic))}"/>
            <rom name="bios.u42" size="${bios.size}" crc="${crcHex(crc(bios))}"/>
          </game>
          <game name="other">
            <description>Some Other Game</description>
            <rom name="other.bin" size="2" crc="${crcHex(crc(bytes(1, 2)))}"/>
          </game>
        </datafile>
        """.trimIndent(),
    )

    @Test
    fun kovshOwnsOnlyTheGameRoms() {
        val kovsh = catalog.byName.getValue("kovsh")
        assertEquals(listOf("pgm_p0605_v104.u1", "kovsh_v100_china.asic"), catalog.ownRoms(kovsh).map { it.name })
        assertEquals(listOf("bios.u42"), catalog.inheritedRoms(kovsh).map { it.name })
    }

    @Test
    fun misnamedZipStillMatchesKovshByInnerFiles() {
        val file = zip(
            "not-the-right-name.zip",
            "pgm_p0605_v104.u1" to program,
            "kovsh_v100_china.asic" to asic,
        )
        val match = RomSetMatcher.identify(ZipArchives.read(file), catalog).best
        assertEquals("kovsh", match?.setName)
        assertTrue(match!!.isClean)
    }

    @Test
    fun crcMismatchIsReportedAndNotInstalled() {
        val file = zip(
            "kovsh.zip",
            "pgm_p0605_v104.u1" to bytes(9, 9, 9, 9),
            "kovsh_v100_china.asic" to asic,
        )
        val storage = AppStorage(folder.newFolder("storage"))
        val report = RomInstaller(storage, catalog).importFile(file, "whatever.zip")
        assertEquals(InstallOutcome.REJECTED_MISMATCH, report.outcome)
        assertTrue(report.match!!.problems.any { it.kind == ProblemKind.CRC_MISMATCH && it.expectedName == "pgm_p0605_v104.u1" })
        assertFalse(storage.kovshZip.exists())
    }

    @Test
    fun sizeMismatchIsReported() {
        val file = zip(
            "kovsh.zip",
            "pgm_p0605_v104.u1" to program + byteArrayOf(0),
            "kovsh_v100_china.asic" to asic,
        )
        val match = RomSetMatcher.identify(ZipArchives.read(file), catalog).best
        assertEquals("kovsh", match?.setName)
        assertTrue(match!!.problems.any { it.kind == ProblemKind.SIZE_MISMATCH })
        assertFalse(match.isClean)
    }

    @Test
    fun renamedInnerFileIsReportedAndNotInstalled() {
        val file = zip(
            "kovsh.zip",
            "wrong-program.u1" to program,
            "kovsh_v100_china.asic" to asic,
        )
        val storage = AppStorage(folder.newFolder("storage"))
        val report = RomInstaller(storage, catalog).importFile(file, "kovsh.zip")
        assertEquals(InstallOutcome.REJECTED_MISMATCH, report.outcome)
        assertTrue(
            report.match!!.problems.any {
                it.kind == ProblemKind.NAME_MISMATCH &&
                    it.expectedName == "pgm_p0605_v104.u1" &&
                    it.actualName == "wrong-program.u1"
            },
        )
        assertFalse(storage.kovshZip.exists())
    }

    @Test
    fun missingFileIsReported() {
        val file = zip("kovsh.zip", "kovsh_v100_china.asic" to asic)
        val match = RomSetMatcher.identify(ZipArchives.read(file), catalog).best
        assertEquals("kovsh", match?.setName)
        assertTrue(match!!.problems.any { it.kind == ProblemKind.MISSING && it.expectedName == "pgm_p0605_v104.u1" })
    }

    @Test
    fun extraFileStillInstallsUnderTheCanonicalName() {
        val file = zip(
            "renamed.zip",
            "pgm_p0605_v104.u1" to program,
            "kovsh_v100_china.asic" to asic,
            "readme.txt" to bytes(1),
        )
        val storage = AppStorage(folder.newFolder("storage"))
        val report = RomInstaller(storage, catalog).importFile(file, "renamed.zip")
        assertEquals(InstallOutcome.INSTALLED, report.outcome)
        assertEquals("kovsh.zip", report.installedFileName)
        assertTrue(storage.kovshZip.isFile)
        assertTrue(report.match!!.problems.any { it.kind == ProblemKind.EXTRA })
    }

    @Test
    fun pgmIsInstalledWhereTheCoreLooks() {
        val file = zip("bios-dump.zip", "bios.u42" to bios)
        val storage = AppStorage(folder.newFolder("storage"))
        val report = RomInstaller(storage, catalog).importFile(file, "bios-dump.zip")
        assertEquals(InstallOutcome.INSTALLED, report.outcome)
        assertTrue(storage.pgmZip.isFile)
        assertTrue(storage.pgmZipBesideRom.isFile)
        assertEquals(storage.pgmZip.length(), storage.pgmZipBesideRom.length())
    }

    @Test
    fun otherCleanSetIsIdentifiedButNotInstalled() {
        val file = zip("other.zip", "other.bin" to bytes(1, 2))
        val storage = AppStorage(folder.newFolder("storage"))
        val report = RomInstaller(storage, catalog).importFile(file, "other.zip")
        assertEquals(InstallOutcome.REJECTED_OTHER_SET, report.outcome)
        assertEquals("other", report.match?.setName)
        assertFalse(storage.kovshZip.exists())
        assertFalse(storage.pgmZip.exists())
    }

    @Test
    fun randomBytesAreUnrecognized() {
        val file = zip("noise.zip", "nope.bin" to bytes(7, 7, 7, 7))
        assertNull(RomSetMatcher.identify(ZipArchives.read(file), catalog).best)
    }

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(folder.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, data) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

    private fun crc(data: ByteArray): Long = CRC32().apply { update(data) }.value
}
