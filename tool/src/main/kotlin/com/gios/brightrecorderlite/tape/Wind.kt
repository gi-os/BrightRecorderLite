package com.gios.brightrecorderlite.tape

/**
 * The scroll wheel, turned into distance along the tape.
 *
 * In BrightRecorder the wheel contributed a playback *rate* to a hand-written audio loop. A Light
 * SDK tool plays through `LightAudioPlayer`, which seeks but cannot play backwards, so here the
 * wheel contributes *distance*: every notch is worth a fixed length of tape, exactly the quantity
 * the original derived its rate from, and turning twice as fast covers twice as much ground.
 *
 * Notches are banked and paid out in small steps (see [drain]) so the player hears a run of short
 * seeks, each followed by a fragment of real audio, which is what makes winding audible.
 *
 * Pure: the caller supplies the clock.
 */
class Wind(private val perNotchMs: Long = PER_NOTCH_MS) {

    private var owedMs = 0L
    private var lastNotchAt = Long.MIN_VALUE / 2

    /** One notch, forward if [direction] is positive. */
    fun notch(direction: Int, nowMs: Long) {
        owedMs += if (direction < 0) -perNotchMs else perNotchMs
        // A long wind should not keep spooling after the thumb stops: cap what can be owed.
        owedMs = owedMs.coerceIn(-MAX_OWED_MS, MAX_OWED_MS)
        lastNotchAt = nowMs
    }

    /** Everything owed since the last drain, which is then forgotten. */
    fun drain(): Long {
        val out = owedMs
        owedMs = 0L
        return out
    }

    /** True while the wheel is turning, or has been within [IDLE_MS]. */
    fun isTurning(nowMs: Long): Boolean = owedMs != 0L || nowMs - lastNotchAt < IDLE_MS

    /** Drop anything owed, for when recording takes the transport over. */
    fun still() {
        owedMs = 0L
        lastNotchAt = Long.MIN_VALUE / 2
    }

    companion object {
        /**
         * How much tape one notch is worth. The original's figure: at the sensor's fastest, a
         * notch every 35 ms, this winds at roughly 8x; an unhurried turn sits near 1x.
         */
        const val PER_NOTCH_MS = 300L

        /** Silence from the wheel that means the turn is over. */
        const val IDLE_MS = 420L

        /** The most tape a burst of notches can bank before it is paid out. */
        const val MAX_OWED_MS = 10_000L
    }
}
