package com.xiaojiwei.fbng.dat

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDatTest {
    @Test
    fun bundledDatMatchesThePinnedCore() {
        val root = repoRoot()
        val pin = Properties().apply {
            File(root, "core/fbneo.pin.properties").inputStream().use(::load)
        }
        val datFile = File(root, "app/src/main/assets/fbneo/${pin.getProperty("fbneo.datFile")}")
        assertTrue(datFile.isFile)
        assertEquals(pin.getProperty("fbneo.datSha256"), sha256(datFile))

        val catalog = datFile.reader(Charsets.UTF_8).use { FbneoDat.parse(it.readText()) }
        assertEquals(pin.getProperty("fbneo.datVersion"), catalog.version)

        val pgm = catalog.byName.getValue("pgm")
        assertTrue(pgm.isBios)
        assertEquals(
            listOf(
                "pgm_t01s.rom",
                "pgm_m01s.rom",
                "pgm_p01s.u20",
                "pgm_p02s.u20",
                "ddp3_bios.u37",
                "bios.u42",
            ),
            pgm.roms.map { it.name },
        )

        val kovsh = catalog.byName.getValue("kovsh")
        val own = catalog.ownRoms(kovsh)
        assertEquals(12, own.size)
        assertTrue(kovsh.description.contains("Knights of Valour Super Heroes"))
        val program = own.single { it.name == "pgm_p0605_v104.u1" }
        assertEquals(4_194_304L, program.size)
        assertEquals(0x7c78e5f3L, program.crc)
        val asic = own.single { it.name == "kovsh_v100_china.asic" }
        assertEquals(16_384L, asic.size)
        assertEquals(0x0f09a5c1L, asic.crc)
        assertTrue(catalog.inheritedRoms(kovsh).map { it.name }.containsAll(pgm.roms.map { it.name }))
    }

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app") cwd.parentFile else cwd
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
