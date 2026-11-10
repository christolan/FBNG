package com.xiaojiwei.fbng.dat

import java.io.StringReader
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

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

sealed interface DatParse {
    data class Ok(val catalog: DatCatalog) : DatParse
    data class Error(val message: String) : DatParse
}

object FbneoDat {
    // Android has no StAX. SAX is on the platform runtime and on the JVM that runs unit tests.
    // A bad document comes back as DatParse.Error so startup can show it instead of dying.
    fun parse(text: String): DatParse {
        return try {
            val sanitized = DOCTYPE.replace(text, "")
            val handler = DatHandler()
            newParser().parse(InputSource(StringReader(sanitized)), handler)
            if (handler.games.isEmpty()) {
                throw SAXParseException("DAT 里没有 game 条目", handler.locator)
            }
            DatParse.Ok(DatCatalog(version = handler.version, games = handler.games))
        } catch (error: Exception) {
            DatParse.Error(describe(error))
        }
    }

    private fun newParser(): SAXParser {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.feature("http://javax.xml.XMLConstants/feature/secure-processing", true)
        factory.feature("http://xml.org/sax/features/external-general-entities", false)
        factory.feature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.feature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.feature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newSAXParser()
    }

    private fun SAXParserFactory.feature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (ignored: Exception) {
            // Android's SAX parser does not implement every JDK feature flag.
        }
    }

    private fun describe(error: Exception): String {
        val detail = when (error) {
            is SAXParseException -> {
                val where = if (error.lineNumber > 0) "第 ${error.lineNumber} 行：" else ""
                where + (error.message?.line() ?: "XML 格式不正确")
            }
            else -> error.message?.line()?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        }
        return "FBNeo DAT 无法解析。$detail".take(MAX_ERROR_CHARS)
    }

    private fun String.line(): String = substringBefore('\n').trim()

    private val DOCTYPE = Regex("<!DOCTYPE[^>]*>")
    private const val MAX_ERROR_CHARS = 300
}

private enum class Capture { NONE, VERSION, DESCRIPTION }

private class DatHandler : DefaultHandler() {
    var version: String = ""
    val games = ArrayList<DatGame>(9000)
    var locator: Locator? = null

    private var depth = 0
    private var inHeader = false
    private var gameDepth = -1
    private var gameName: String? = null
    private var romof: String? = null
    private var isBios = false
    private var description: String? = null
    private val roms = ArrayList<DatRom>()

    private var capture = Capture.NONE
    private var captureDepth = -1
    private val text = StringBuilder()

    override fun setDocumentLocator(locator: Locator?) {
        this.locator = locator
    }

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        depth++
        if (capture != Capture.NONE && depth > captureDepth) return
        when (elementName(localName, qName)) {
            "header" -> if (gameDepth < 0) inHeader = true
            "version" -> if (inHeader && gameDepth < 0) beginCapture(Capture.VERSION)
            "game" -> if (gameDepth < 0) beginGame(attributes)
            "description" -> if (gameDepth > 0 && depth == gameDepth + 1) beginCapture(Capture.DESCRIPTION)
            "rom" -> if (gameDepth > 0 && depth == gameDepth + 1) readRom(attributes)?.let(roms::add)
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (capture != Capture.NONE && depth == captureDepth) {
            text.append(ch, start, length)
        }
    }

    override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {
        characters(ch, start, length)
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        val name = elementName(localName, qName)
        if (capture != Capture.NONE && depth == captureDepth) {
            val value = text.toString().trim()
            when (capture) {
                Capture.VERSION -> version = value
                Capture.DESCRIPTION -> description = value
                Capture.NONE -> Unit
            }
            capture = Capture.NONE
            captureDepth = -1
        }
        if (name == "header") inHeader = false
        if (name == "game" && depth == gameDepth) finishGame()
        depth--
    }

    private fun beginGame(attributes: Attributes) {
        gameDepth = depth
        gameName = attributes.value("name") ?: fail("DAT game 缺少 name")
        romof = attributes.value("romof")
        isBios = attributes.value("isbios") == "yes"
        description = null
        roms.clear()
    }

    private fun finishGame() {
        val resolved = gameName ?: fail("DAT game 缺少 name")
        games += DatGame(
            name = resolved,
            description = description?.takeIf { it.isNotEmpty() } ?: resolved,
            romof = romof,
            isBios = isBios,
            roms = ArrayList(roms),
        )
        gameDepth = -1
        gameName = null
        romof = null
        isBios = false
        description = null
        roms.clear()
    }

    private fun beginCapture(target: Capture) {
        capture = target
        captureDepth = depth
        text.setLength(0)
    }

    private fun readRom(attributes: Attributes): DatRom? {
        val romName = attributes.value("name") ?: return null
        val size = attributes.value("size")?.toLongOrNull() ?: 0L
        val crc = attributes.value("crc")?.toLongOrNull(16)?.and(0xffff_ffffL) ?: 0L
        val status = attributes.value("status")
        return DatRom(romName, size, crc, status)
    }

    private fun fail(message: String): Nothing = throw SAXParseException(message, locator)

    private fun elementName(localName: String, qName: String): String =
        qName.ifEmpty { localName }

    private fun Attributes.value(name: String): String? =
        getValue(name)?.takeIf { it.isNotEmpty() }
}
