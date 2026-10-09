package com.gios.brightrecorderlite.hw

import kotlin.test.Test
import kotlin.test.assertEquals

class PressTest {

    @Test
    fun `a tap toggles`() {
        val p = Press()
        assertEquals(Press.Act.None, p.down(recording = false))
        assertEquals(Press.Act.Toggle, p.up())
    }

    @Test
    fun `a hold records and its release does nothing`() {
        val p = Press()
        p.down(recording = false)
        assertEquals(Press.Act.StartRecording, p.held())
        assertEquals(Press.Act.None, p.up())
    }

    @Test
    fun `a press while recording stops on the way down`() {
        val p = Press()
        assertEquals(Press.Act.StopRecording, p.down(recording = true))
        assertEquals(Press.Act.None, p.held())
        assertEquals(Press.Act.None, p.up())
    }

    @Test
    fun `a late hold timer after release does nothing`() {
        val p = Press()
        p.down(recording = false)
        p.up()
        assertEquals(Press.Act.None, p.held())
    }
}
