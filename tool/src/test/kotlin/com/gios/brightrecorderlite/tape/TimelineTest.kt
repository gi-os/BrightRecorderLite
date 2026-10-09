package com.gios.brightrecorderlite.tape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineTest {

    private fun clip(name: String, ms: Long) = Clip(fileName = name, place = "x", startedAt = 0L, durationMs = ms)

    private val three = Timeline(listOf(clip("a", 10_000), clip("b", 5_000), clip("c", 20_000)))

    @Test
    fun `total is the sum of every clip`() {
        assertEquals(35_000L, three.durationMs)
    }

    @Test
    fun `the first moment of a clip belongs to it and not the one before`() {
        assertEquals(Spot(0, 9_999), three.locate(9_999))
        assertEquals(Spot(1, 0), three.locate(10_000))
        assertEquals(Spot(2, 0), three.locate(15_000))
    }

    @Test
    fun `off either end is nowhere`() {
        assertNull(three.locate(35_000))
        assertNull(three.locate(-1))
        assertNull(Timeline(emptyList()).locate(0))
    }

    @Test
    fun `a zero length clip is stepped over`() {
        val t = Timeline(listOf(clip("a", 1_000), clip("b", 0), clip("c", 1_000)))
        assertEquals(Spot(2, 0), t.locate(1_000))
    }

    @Test
    fun `global and local agree`() {
        assertEquals(12_000L, three.globalOf(1, 2_000))
        assertEquals(15_000L, three.globalOf(1, 99_000))
    }

    @Test
    fun `winding crosses clips and stops at the walls`() {
        assertEquals(11_000L, three.move(9_000, 2_000))
        assertEquals(8_000L, three.move(11_000, -3_000))
        assertEquals(0L, three.move(1_000, -5_000))
        assertEquals(34_999L, three.move(34_000, 5_000))
    }

    @Test
    fun `skipping back from deep in a clip goes to its start first`() {
        assertEquals(10_000L, three.seekByClip(13_000, -1))
        assertEquals(0L, three.seekByClip(10_500, -1))
        assertEquals(15_000L, three.seekByClip(11_000, 1))
        assertEquals(35_000L, three.seekByClip(20_000, 1))
    }
}
