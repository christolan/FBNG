package com.xiaojiwei.fbng.dat

import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

data class DatRom(
    val name: String,
    val size: Long,
    val crc: Long,
    val status: String?,
) {
    val required: Boolean
        get() = !status.equals("nodump", ignoreCase = true) && size > 0L && crc != 0L
}

data class DatGame(
    val name: String,
    val description: String,
    val romof: String?,
    val isBios: Boolean,
    val roms: List<DatRom>,
)

class DatCatalog(
    val version: String,
    val games: List<DatGame>,
) {
    val byName: Map<String, DatGame> = games.associateBy { it.name }

    private val ownByGame: Map<String, List<DatRom>>
    private val inheritedByGame: Map<String, List<DatRom>>
    val gamesByOwnName: Map<String, Set<String>>
    val gamesByOwnCrc: Map<Long, Set<String>>

    init {
        val own = HashMap<String, List<DatRom>>(games.size)
        val inherited = HashMap<String, List<DatRom>>(games.size)
        val nameIndex = HashMap<String, MutableSet<String>>()
        val crcIndex = HashMap<Long, MutableSet<String>>()
        for (game in games) {
            val chain = inheritedKeys(game)
            val inheritedRoms = ArrayList<DatRom>()
            val ownRoms = ArrayList<DatRom>()
            for (rom in game.roms) {
                if (!rom.required) continue
                if (romKey(rom) in chain) {
                    inheritedRoms += rom
                } else {
                    ownRoms += rom
                }
            }
            own[game.name] = ownRoms
            inherited[game.name] = inheritedRoms
            for (rom in ownRoms) {
                nameIndex.getOrPut(rom.name) { linkedSetOf() }.add(game.name)
                crcIndex.getOrPut(rom.crc) { linkedSetOf() }.add(game.name)
            }
        }
        ownByGame = own
        inheritedByGame = inherited
        gamesByOwnName = nameIndex
        gamesByOwnCrc = crcIndex
    }

    fun ownRoms(game: DatGame): List<DatRom> = ownByGame[game.name].orEmpty()

    fun inheritedRoms(game: DatGame): List<DatRom> = inheritedByGame[game.name].orEmpty()

    private fun inheritedKeys(game: DatGame): Set<RomKey> {
        val keys = HashSet<RomKey>()
        val seen = HashSet<String>()
        var parentName = game.romof
        while (parentName != null && seen.add(parentName)) {
            val parent = byName[parentName] ?: break
            parent.roms.forEach { rom ->
                if (rom.required) keys += romKey(rom)
            }
            parentName = parent.romof
        }
        return keys
    }
}

private data class RomKey(val name: String, val size: Long, val crc: Long)

private fun romKey(rom: DatRom) = RomKey(rom.name, rom.size, rom.crc)

object FbneoDat {
    fun parse(text: String): DatCatalog {
        val sanitized = DOCTYPE.replace(text, "")
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        factory.setProperty(XMLInputFactory.IS_COALESCING, true)
        val reader = factory.createXMLStreamReader(StringReader(sanitized))
        reader.use { stream ->
            var version = ""
            var inHeader = false
            val games = ArrayList<DatGame>(9000)
            while (stream.hasNext()) {
                when (stream.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (stream.localName) {
                        "header" -> inHeader = true
                        "version" -> if (inHeader) version = stream.readText().trim()
                        "game" -> games += readGame(stream)
                    }
                    XMLStreamConstants.END_ELEMENT -> if (stream.localName == "header") inHeader = false
                }
            }
            return DatCatalog(version = version, games = games)
        }
    }

    private fun readGame(stream: XMLStreamReader): DatGame {
        val name = stream.attr("name") ?: error("DAT game has no name")
        val romof = stream.attr("romof")
        val isBios = stream.attr("isbios") == "yes"
        var description = name
        val roms = ArrayList<DatRom>()
        while (stream.hasNext()) {
            when (stream.next()) {
                XMLStreamConstants.START_ELEMENT -> when (stream.localName) {
                    "description" -> description = stream.readText().trim().ifEmpty { name }
                    "rom" -> readRom(stream)?.let(roms::add)
                    else -> skipElement(stream)
                }
                XMLStreamConstants.END_ELEMENT -> if (stream.localName == "game") break
            }
        }
        return DatGame(name, description, romof, isBios, roms)
    }

    private fun readRom(stream: XMLStreamReader): DatRom? {
        val romName = stream.attr("name") ?: return null
        val size = stream.attr("size")?.toLongOrNull() ?: 0L
        val crc = stream.attr("crc")?.toLongOrNull(16)?.and(0xffff_ffffL) ?: 0L
        val status = stream.attr("status")
        return DatRom(romName, size, crc, status)
    }

    private fun skipElement(stream: XMLStreamReader) {
        var depth = 1
        while (depth > 0 && stream.hasNext()) {
            when (stream.next()) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }

    private fun XMLStreamReader.attr(localName: String): String? =
        getAttributeValue(null, localName)?.takeIf { it.isNotEmpty() }

    private fun XMLStreamReader.readText(): String {
        val text = StringBuilder()
        while (hasNext()) {
            when (next()) {
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(getText())
                XMLStreamConstants.END_ELEMENT -> break
                XMLStreamConstants.START_ELEMENT -> skipElement(this)
            }
        }
        return text.toString()
    }

    private inline fun <T : XMLStreamReader, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            close()
        }
    }

    private val DOCTYPE = Regex("<!DOCTYPE[^>]*>")
}
