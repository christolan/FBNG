package com.xiaojiwei.fbng.dat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FbneoDatParseTest {
    @Test(timeout = 15_000)
    fun smallDatKeepsCrcStatusAndEntities() {
        val catalog = parseCatalog(
            """
            <?xml version="1.0"?>
            <!DOCTYPE datafile PUBLIC "-//x//EN" "file:///does/not/exist.dtd">
            <datafile>
              <header>
                <version>1.2.3</version>
              </header>
              <game name="kovsh" romof="pgm">
                <comment>Bootleg</comment>
                <description>Demo &amp; Co</description>
                <sample name="shoot" />
                <rom name="a.bin" size="4" crc="7C78E5F3" status="nodump"/>
                <rom name="" size="4" crc="1"/>
                <driver status="good"/>
              </game>
              <game isbios="yes" name="pgm">
                <description>PGM BIOS</description>
                <rom name="bios.u42" size="2" crc="00000001"/>
              </game>
            </datafile>
            """.trimIndent(),
        )
        assertEquals("1.2.3", catalog.version)
        val kovsh = catalog.byName.getValue("kovsh")
        assertEquals("Demo & Co", kovsh.description)
        assertEquals("pgm", kovsh.romof)
        val dumped = kovsh.roms.single()
        assertEquals("a.bin", dumped.name)
        assertEquals(4L, dumped.size)
        assertEquals(0x7c78e5f3L, dumped.crc)
        assertEquals("nodump", dumped.status)
        assertFalse(dumped.required)
        val pgm = catalog.byName.getValue("pgm")
        assertTrue(pgm.isBios)
        assertTrue(pgm.roms.single().required)
    }

    @Test(timeout = 15_000)
    fun badDatDoesNotThrow() {
        val samples = listOf(
            "",
            "this is not xml",
            "<datafile><game name=\"kovsh\"><rom name=\"a\" size=\"1\" crc=\"1\"",
            "<datafile><game><description>missing name</description></game></datafile>",
            "<not-a-dat/>",
            "<datafile><header><version>1</version></header></datafile>",
            "<datafile>\u0000</datafile>",
        )
        samples.forEach { sample ->
            val parsed = FbneoDat.parse(sample)
            assertTrue(parsed.javaClass.name, parsed is DatParse.Error)
            assertTrue((parsed as DatParse.Error).message.isNotBlank())
        }
    }

    @Test
    fun parserSourceDoesNotUseStax() {
        val source = repoRoot().resolve("app/src/main/java/com/xiaojiwei/fbng/dat/FbneoDat.kt").readText()
        assertFalse(source.contains("javax.xml.stream"))
        assertFalse(source.contains("XMLInputFactory"))
        assertFalse(source.contains("XMLStreamReader"))
    }
}
