package com.xiaojiwei.fbng.dat

import com.xiaojiwei.fbng.importing.ZipArchives
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
    fun crcMismatchIsReported() {
        val file = zip(
            "kovsh.zip",
            "pgm_p0605_v104.u1" to bytes(9, 9, 9, 9),
            "kovsh_v100_china.asic" to asic,
        )
        val match = checkNotNull(RomSetMatcher.identify(ZipArchives.read(file), catalog, "whatever.zip").best)
        assertFalse(match.isClean)
        assertTrue(match.problems.any { it.kind == ProblemKind.CRC_MISMATCH && it.expectedName == "pgm_p0605_v104.u1" })
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
    fun renamedInnerFileIsReported() {
        val file = zip(
            "kovsh.zip",
            "wrong-program.u1" to program,
            "kovsh_v100_china.asic" to asic,
        )
        val match = checkNotNull(RomSetMatcher.identify(ZipArchives.read(file), catalog, file.name).best)
        assertFalse(match.isClean)
        assertTrue(
            match.problems.any {
                it.kind == ProblemKind.NAME_MISMATCH &&
                    it.expectedName == "pgm_p0605_v104.u1" &&
                    it.actualName == "wrong-program.u1"
            },
        )
    }

    @Test
    fun missingFileIsReported() {
        val file = zip("kovsh.zip", "kovsh_v100_china.asic" to asic)
        val match = RomSetMatcher.identify(ZipArchives.read(file), catalog).best
        assertEquals("kovsh", match?.setName)
        assertTrue(match!!.problems.any { it.kind == ProblemKind.MISSING && it.expectedName == "pgm_p0605_v104.u1" })
    }

    @Test
    fun extraFileStillMatchesTheCanonicalName() {
        val file = zip(
            "renamed.zip",
            "pgm_p0605_v104.u1" to program,
            "kovsh_v100_china.asic" to asic,
            "readme.txt" to bytes(1),
        )
        val match = checkNotNull(RomSetMatcher.identify(ZipArchives.read(file), catalog, file.name).best)
        assertEquals("kovsh", match.setName)
        assertTrue(match.isClean)
        assertTrue(match.problems.any { it.kind == ProblemKind.EXTRA })
    }

    @Test
    fun identicalSetsUseTheCanonicalArchiveNameToResolveAmbiguity() {
        val original = catalog.byName.getValue("other")
        val withTwin = DatCatalog(catalog.version, catalog.games + original.copy(name = "otherj"))
        val file = zip("renamed.zip", "other.bin" to bytes(1, 2))
        val members = ZipArchives.read(file)
        assertTrue(RomSetMatcher.identify(members, withTwin, file.name).ambiguous)
        assertEquals("otherj", RomSetMatcher.identify(members, withTwin, "otherj.ZIP").best?.setName)
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
