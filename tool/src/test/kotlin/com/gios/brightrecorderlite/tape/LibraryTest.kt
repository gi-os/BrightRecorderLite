package com.gios.brightrecorderlite.tape

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {

    private val root: File = Files.createTempDirectory("tapes").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun record(dir: File, place: String, at: Long, ms: Long): Clip {
        val rec = Library.recordingFile(dir, at)
        rec.writeText("audio")
        return Library.file(dir, rec, place, at, Library.Meta(ms, 1.5, -2.5))!!
    }

    @Test
    fun `a filed recording comes back with its length and position`() {
        val tape = Tapes.create(root, "trip", 1_000_000L)!!
        val dir = Tapes.dirOf(root, tape)
        record(dir, "Here", 2_000_000L, 4_000)
        val clips = Library.scan(dir)
        assertEquals(1, clips.size)
        assertEquals(4_000L, clips[0].durationMs)
        assertEquals(1.5, clips[0].latitude)
        assertEquals(-2.5, clips[0].longitude)
        assertEquals("Here", clips[0].place)
    }

    @Test
    fun `clips are in recording order and recordings in progress are ignored`() {
        val dir = Tapes.dirOf(root, Tapes.create(root, "t", 0L)!!)
        record(dir, "Later", 5_000_000L, 1_000)
        record(dir, "Earlier", 4_000_000L, 1_000)
        Library.recordingFile(dir, 6_000_000L).writeText("partial")
        assertEquals(listOf("Earlier", "Later"), Library.scan(dir).map { it.place })
        Library.sweepAbandoned(dir)
        assertFalse(Library.recordingFile(dir, 6_000_000L).exists())
    }

    @Test
    fun `two clips in the same second do not overwrite each other`() {
        val dir = Tapes.dirOf(root, Tapes.create(root, "t", 0L)!!)
        record(dir, "Same", 7_000_000L, 1_000)
        record(dir, "Same", 7_000_000L, 2_000)
        assertEquals(2, Library.scan(dir).size)
    }

    @Test
    fun `rename keeps the time and the metadata`() {
        val dir = Tapes.dirOf(root, Tapes.create(root, "t", 0L)!!)
        val clip = record(dir, "Old", 8_000_000L, 3_000)
        val renamed = Library.rename(dir, clip, "kitchen radio")!!
        assertEquals("Kitchen Radio", renamed.place)
        assertEquals(clip.startedAt, renamed.startedAt)
        val scanned = Library.scan(dir).single()
        assertEquals(3_000L, scanned.durationMs)
        assertEquals(1.5, scanned.latitude)
    }

    @Test
    fun `a missing length is measured once and cached`() {
        val dir = Tapes.dirOf(root, Tapes.create(root, "t", 0L)!!)
        File(dir, Naming.fileName("Copied", 9_000_000L)).writeText("x")
        var measured = 0
        val first = Library.scan(dir) { measured++; 1234L }
        assertEquals(1234L, first.single().durationMs)
        Library.scan(dir) { measured++; 1234L }
        assertEquals(1, measured)
    }

    @Test
    fun `delete removes the clip and its row`() {
        val dir = Tapes.dirOf(root, Tapes.create(root, "t", 0L)!!)
        val clip = record(dir, "Gone", 10_000_000L, 1_000)
        assertTrue(Library.delete(dir, clip))
        assertTrue(Library.scan(dir).isEmpty())
        assertNull(Library.readMeta(dir)[clip.fileName])
    }

    @Test
    fun `a tape with clips cannot be deleted, an empty one can`() {
        val tape = Tapes.create(root, "keep", 0L)!!
        val dir = Tapes.dirOf(root, tape)
        val clip = record(dir, "x", 11_000_000L, 1_000)
        assertFalse(Tapes.delete(root, tape))
        Library.delete(dir, clip)
        assertTrue(Tapes.delete(root, tape))
    }

    @Test
    fun `tapes rename and the shelf is never empty`() {
        val tapes = Tapes.ensureOne(root, 0L)
        assertEquals(Tapes.DEFAULT_NAME, tapes.single().name)
        val renamed = Tapes.rename(root, tapes.single(), "the flat")
        assertNotNull(renamed)
        assertEquals("The Flat", renamed.name)
        assertEquals(1, Tapes.list(root).size)
    }
}
