package com.gios.brightrecorderlite.tape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindTest {

    @Test
    fun `each notch is a fixed length of tape`() {
        val w = Wind()
        repeat(3) { w.notch(1, it * 35L) }
        assertEquals(3 * Wind.PER_NOTCH_MS, w.drain())
        assertEquals(0L, w.drain())
    }

    @Test
    fun `back notches cancel forward ones`() {
        val w = Wind()
        w.notch(1, 0)
        w.notch(-1, 10)
        w.notch(-1, 20)
        assertEquals(-Wind.PER_NOTCH_MS, w.drain())
    }

    @Test
    fun `a frantic spin cannot bank unbounded tape`() {
        val w = Wind()
        repeat(1000) { w.notch(1, it.toLong()) }
        assertEquals(Wind.MAX_OWED_MS, w.drain())
    }

    @Test
    fun `turning ends after the idle gap`() {
        val w = Wind()
        w.notch(1, 1_000)
        w.drain()
        assertTrue(w.isTurning(1_000 + Wind.IDLE_MS - 1))
        assertFalse(w.isTurning(1_000 + Wind.IDLE_MS))
    }

    @Test
    fun `still forgets everything`() {
        val w = Wind()
        w.notch(1, 0)
        w.still()
        assertEquals(0L, w.drain())
        assertFalse(w.isTurning(1))
    }
}
