package com.gios.brightrecorderlite.tape

import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NamingTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val at = 1_786_890_725_000L // 2026-08-16 14:32:05 UTC

    @Test
    fun `file name leads with a fixed width stamp and round trips`() {
        val name = Naming.fileName("Bastille, Paris", at, utc)
        assertEquals("2026-08-16 143205 Bastille, Paris.m4a", name)
        val clip = Naming.parse(name, utc)!!
        assertEquals("Bastille, Paris", clip.place)
        assertEquals(at, clip.startedAt)
    }

    @Test
    fun `place names with punctuation survive`() {
        val name = Naming.fileName("Washington, D.C.", at, utc)
        assertEquals("Washington, D.C.", Naming.parse(name, utc)!!.place)
    }

    @Test
    fun `slashes cannot escape into a directory`() {
        assertEquals("AC DC", Naming.clean("AC/DC"))
        assertEquals(Naming.NOWHERE, Naming.clean(" / "))
    }

    @Test
    fun `stray files are not clips`() {
        assertNull(Naming.parse("notes.txt"))
        assertNull(Naming.parse(".rec-123.m4a"))
        assertNull(Naming.parse("2026-13-40 999999 x.m4a"))
    }

    @Test
    fun `typed names get a capital per word and keep the rest`() {
        assertEquals("Trip To Rome", Naming.titleCase("trip to rome"))
        assertEquals("NYC Rehearsal", Naming.titleCase("NYC rehearsal"))
    }

    @Test
    fun `folder names round trip`() {
        val dir = Naming.folderName("Trip", at, utc)
        assertEquals("Trip" to at, Naming.parseFolder(dir, utc))
    }

    @Test
    fun `durations read like a tape counter`() {
        assertEquals("0:00", Naming.duration(0))
        assertEquals("1:05", Naming.duration(65_400))
        assertEquals("1:01:01", Naming.duration(3_661_000))
    }

    @Test
    fun `coordinates make a coarse label`() {
        assertEquals("48.86 N, 2.37 E", Place.label(48.8566, 2.3699))
        assertEquals("40.71 N, 74.01 W", Place.label(40.7128, -74.006))
        assertEquals(Naming.NOWHERE, Place.label(null, 2.0))
        assertEquals(Naming.NOWHERE, Place.label(120.0, 2.0))
    }
}
