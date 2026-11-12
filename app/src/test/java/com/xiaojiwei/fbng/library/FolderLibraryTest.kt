package com.xiaojiwei.fbng.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import com.xiaojiwei.fbng.dat.crcHex
import com.xiaojiwei.fbng.dat.parseCatalog
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FolderLibraryTest {
    @get:Rule
    val files = TemporaryFolder()

    private val romData = byteArrayOf(1, 2, 3)
    private val biosData = byteArrayOf(4, 5, 6)
    private val cloneData = byteArrayOf(7, 8, 9)
    private val standaloneData = byteArrayOf(10, 11, 12)
    private val catalog = parseCatalog(
        """
        <datafile>
          <header><version>test</version></header>
          <game name="board" isbios="yes"><rom name="bios.bin" size="3" crc="${crc(biosData)}"/></game>
          <game name="arcade" romof="board">
            <rom name="game.bin" size="3" crc="${crc(romData)}"/>
            <rom name="bios.bin" size="3" crc="${crc(biosData)}" merge="bios.bin"/>
          </game>
          <game name="arcadej" romof="arcade">
            <rom name="variant.bin" size="3" crc="${crc(cloneData)}"/>
            <rom name="alias.bin" size="3" crc="${crc(romData)}" merge="game.bin"/>
            <rom name="bios.bin" size="3" crc="${crc(biosData)}" merge="bios.bin"/>
          </game>
          <game name="standalone"><rom name="solo.bin" size="3" crc="${crc(standaloneData)}"/></game>
        </datafile>
        """.trimIndent(),
    )

    @Test
    fun scansSubfoldersAndPersistsSourcesWithoutChangingManualFiles() = runBlocking {
        val provider = Robolectric.setupContentProvider(TestDocuments::class.java, AUTHORITY)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AppStorage(files.newFolder())
        val game = zip("renamed.ZIP", "game.bin", romData)
        val bios = zip("another-name.zip", "bios.bin", biosData)
        val clone = zip("arcadej.zip", "variant.bin", cloneData)
        val standalone = zip("another-game.zip", "solo.bin", standaloneData)
        val embedded = zip("arcade-full.zip", "game.bin", romData, "bios.bin" to biosData)
        provider.add("games", "街机")
        provider.add("nested", "子目录", parent = "games")
        provider.add("game", game.name, parent = "nested", file = game)
        provider.add("clone", clone.name, parent = "nested", file = clone)
        provider.add("standalone", standalone.name, parent = "nested", file = standalone)
        provider.add("bad", "bad.zip", parent = "nested", file = files.newFile())
        val skipped = zip("skipped.zip", "other.bin", byteArrayOf(0))
        provider.add("skipped", skipped.name, parent = "games", file = skipped)
        provider.add("bios-folder", "BIOS")
        provider.add("bios", bios.name, parent = "bios-folder", file = bios)
        val gamesFolder = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "games")
        val biosFolder = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "bios-folder")

        val library = FolderLibrary(context, storage)
        library.add(gamesFolder)
        library.add(gamesFolder)
        library.add(biosFolder)
        val reopened = FolderLibrary(context, storage)
        val refresh = reopened.refresh(catalog)
        assertEquals(listOf("arcade", "arcadej", "standalone"), refresh.snapshot.games.map { it.name })
        assertTrue(refresh.snapshot.games.all { it.canLaunch })
        assertEquals(2, refresh.folders.size)
        assertEquals(
            FolderScan(LibraryFolder(gamesFolder, "街机"), roms = 3, skipped = 1, unreadable = 1),
            refresh.folders.single { it.folder.uri == gamesFolder },
        )
        assertEquals(1, refresh.folders.single { it.folder.uri == biosFolder }.bios)
        assertFalse(storage.playDir.exists())
        val selected = refresh.snapshot.games.single { it.name == "arcadej" }
        val prepared = reopened.prepare(selected)
        assertEquals(storage.stateDir("arcadej"), prepared.stateDir)
        assertEquals(storage.savesDir, prepared.savesDir)
        assertEquals("arcadej.zip", prepared.rom.name)
        assertEquals(setOf("arcadej.zip", "arcade.zip", "board.zip"), prepared.rom.parentFile!!.list()!!.toSet())
        assertTrue(prepared.rom.readBytes().contentEquals(clone.readBytes()))
        assertFalse(storage.archive("arcadej", isBios = false).exists())

        // A changed source may no longer be launched with the old scan result.
        clone.writeText("replaced since scan")
        assertThrows(IOException::class.java) { runBlocking { reopened.prepare(selected) } }
        assertFalse(storage.playDir.exists())

        provider.unavailable += "games"
        val unavailable = reopened.refresh(catalog)
        assertTrue(unavailable.folders.single { it.folder.uri == gamesFolder }.unavailable)
        assertTrue(unavailable.snapshot.games.isEmpty())

        storage.install(game, "arcade", isBios = false)
        val mixed = reopened.refresh(catalog).snapshot.games.single()
        assertTrue(mixed.canLaunch)
        assertTrue(reopened.prepare(mixed).rom.readBytes().contentEquals(game.readBytes()))
        reopened.remove(biosFolder)
        val afterRemoval = reopened.refresh(catalog)
        assertEquals(listOf(MissingDependency("board", true)), afterRemoval.snapshot.games.single().missing)
        assertEquals(listOf(gamesFolder), afterRemoval.folders.map { it.folder.uri })
        assertTrue(bios.isFile)
        assertTrue(storage.archive("arcade", isBios = false).isFile)
        assertFalse(storage.playDir.exists())

        storage.install(embedded, "arcade", isBios = false)
        val nonMerged = reopened.refresh(catalog).snapshot.games.single()
        assertTrue(nonMerged.canLaunch)
        assertEquals(listOf("arcade"), nonMerged.archives.map { it.set.name })
    }

    private fun zip(
        name: String,
        entry: String,
        bytes: ByteArray,
        vararg more: Pair<String, ByteArray>,
    ): File = files.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((member, data) in listOf(entry to bytes) + more) {
                zip.putNextEntry(ZipEntry(member))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }

    private fun crc(bytes: ByteArray) = crcHex(CRC32().apply { update(bytes) }.value)

    class TestDocuments : ContentProvider() {
        private data class Entry(val name: String, val parent: String?, val file: File?)
        private val entries = mutableMapOf<String, Entry>()
        val unavailable = mutableSetOf<String>()

        fun add(id: String, name: String, parent: String? = null, file: File? = null) {
            entries[id] = Entry(name, parent, file)
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): MatrixCursor {
            val id = DocumentsContract.getDocumentId(uri)
            if (id in unavailable) throw SecurityException("Folder grant revoked")
            val columns = requireNotNull(projection)
            val matches = if (uri.lastPathSegment == "children") entries.filterValues { it.parent == id }
            else entries.filterKeys { it == id }
            return MatrixCursor(columns).apply {
                for ((key, entry) in matches) addRow(columns.map { column ->
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> key
                        Document.COLUMN_DISPLAY_NAME -> entry.name
                        Document.COLUMN_MIME_TYPE -> if (entry.file == null) Document.MIME_TYPE_DIR else "application/octet-stream"
                        else -> null
                    }
                })
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
            requireNotNull(entries.getValue(DocumentsContract.getDocumentId(uri)).file),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("Read only")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("Read only")
    }

    private companion object {
        const val AUTHORITY = "com.xiaojiwei.fbng.test.documents"
    }
}
