package com.gios.brightrecorderlite.tape

/**
 * Every clip laid end to end as one continuous length of tape.
 *
 * Ported from BrightRecorder. The original addressed the tape by sample number because it drove
 * its own audio loop; the Lite version drives `LightAudioPlayer`, which speaks milliseconds, so
 * positions here are milliseconds. Everything else is the same idea: a single position runs from
 * the first moment of the first clip to the last moment of the last, and playing or winding past
 * the end of a clip is simply a position that falls inside the next one.
 */
class Timeline(val clips: List<Clip>) {

    private val starts: LongArray = LongArray(clips.size + 1).also { acc ->
        var at = 0L
        clips.forEachIndexed { i, clip ->
            acc[i] = at
            at += clip.durationMs.coerceAtLeast(0L)
        }
        acc[clips.size] = at
    }

    /** Length of the whole tape in milliseconds. */
    val durationMs: Long get() = starts[clips.size]

    val isEmpty: Boolean get() = durationMs == 0L

    /** First millisecond of clip [index]. */
    fun startOf(index: Int): Long = starts[index.coerceIn(0, clips.size)]

    /** The global position of [offsetMs] into clip [index]. */
    fun globalOf(index: Int, offsetMs: Long): Long {
        if (clips.isEmpty()) return 0L
        val i = index.coerceIn(0, clips.size - 1)
        return startOf(i) + offsetMs.coerceIn(0L, clips[i].durationMs.coerceAtLeast(0L))
    }

    /** Which clip [global] falls in, and how far into it. Null past either end. */
    fun locate(global: Long): Spot? {
        if (global < 0 || global >= durationMs) return null
        var lo = 0
        var hi = clips.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= global) lo = mid else hi = mid - 1
        }
        var i = lo
        while (i < clips.size && clips[i].durationMs <= 0L) i++
        if (i >= clips.size) return null
        return Spot(i, global - starts[i])
    }

    /**
     * Where the head lands after moving [deltaMs] from [global]: clamped to the tape, and kept
     * inside it (the very end is parked on the last moment so the head always names a clip).
     */
    fun move(global: Long, deltaMs: Long): Long {
        if (isEmpty) return 0L
        return (global + deltaMs).coerceIn(0L, durationMs - 1)
    }

    /**
     * The start of the clip [count] clips away from the one at [global]. Skipping back from
     * partway through a clip goes to the start of that clip first, like every back button.
     */
    fun seekByClip(global: Long, count: Int): Long {
        if (isEmpty) return 0L
        val here = locate(global) ?: return if (count < 0) startOf(clips.size - 1) else durationMs
        val target = if (count < 0 && here.offset > BACK_GRACE_MS) here.index + count + 1 else here.index + count
        if (target >= clips.size) return durationMs
        return startOf(target.coerceAtLeast(0))
    }

    private companion object {
        /** A back skip in the first moment of a clip goes to the one before, not to this start. */
        const val BACK_GRACE_MS = 1500L
    }
}

/** A position on the tape, resolved to a clip and an offset inside it. */
data class Spot(val index: Int, val offset: Long)
