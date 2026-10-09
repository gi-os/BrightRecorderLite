package com.gios.brightrecorderlite.hw

/**
 * What a press of the wheel means: tap to play or stop, hold to record. Ported from
 * BrightRecorder unchanged. The caller supplies the events and the hold timer, so every branch is
 * unit-tested without a clock.
 */
class Press {

    enum class Act { None, Toggle, StartRecording, StopRecording }

    private var down = false
    private var answered = false

    /** The wheel went in. A press while recording stops it at once, on the way down. */
    fun down(recording: Boolean): Act {
        down = true
        answered = recording
        return if (recording) Act.StopRecording else Act.None
    }

    /** [HOLD_MS] has passed with the wheel still in. */
    fun held(): Act {
        if (!down || answered) return Act.None
        answered = true
        return Act.StartRecording
    }

    /** The wheel came back out. Only an unanswered press is a tap. */
    fun up(): Act {
        val tap = down && !answered
        down = false
        answered = false
        return if (tap) Act.Toggle else Act.None
    }

    fun cancel() {
        down = false
        answered = false
    }

    companion object {
        const val HOLD_MS = 400L
    }
}
