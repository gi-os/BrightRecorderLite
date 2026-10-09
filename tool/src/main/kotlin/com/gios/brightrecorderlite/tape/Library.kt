package com.gios.brightrecorderlite.tape

import java.io.File

/**
 * The clips on one tape.
 *
 * A tape is a directory. Each clip is a file named by [Naming]; its length and the raw
 * coordinates it was recorded at live in one small tab-separated sidecar, [META_FILE], keyed by
 * filename. The sidecar is a cache, not an index: a clip without a row still plays, and its
 * length is measured from the file by the caller-supplied [durationOf].
 */
object Library {

    const val META_FILE = ".clips"

    /** Prefix of a recording in progress. Never parsed as a clip. */
    const val RECORDING_PREFIX = ".rec-"

    data class Meta(val durationMs: Long, val latitude: Double?, val longitude: Double?)

    /** Every clip on the tape in [dir], in recording order. */
    fun scan(dir: File, durationOf: (File) -> Long = { 0L }): List<Clip> {
        val files = dir.listFiles()?.filter { it.isFile } ?: return emptyList()
        val meta = readMeta(dir)
        var metaChanged = false
        val clips = files.mapNotNull { f ->
            val parsed = Naming.parse(f.name) ?: return@mapNotNull null
            var m = meta[f.name]
            if (m == null || m.durationMs <= 0L) {
                val measured = durationOf(f)
                if (measured > 0L) {
                    m = Meta(measured, m?.latitude, m?.longitude)
                    meta[f.name] = m
                    metaChanged = true
                }
            }
            parsed.copy(
                durationMs = m?.durationMs ?: 0L,
                latitude = m?.latitude,
                longitude = m?.longitude,
            )
        }.sortedWith(compareBy({ it.fileName }))
        if (metaChanged) writeMeta(dir, meta)
        return clips
    }

    fun fileOf(dir: File, clip: Clip): File = File(dir, clip.fileName)

    /** A fresh, unparseable name to record into until the clip can be filed. */
    fun recordingFile(dir: File, startedAt: Long): File =
        File(dir, "$RECORDING_PREFIX$startedAt${Naming.EXTENSION}")

    /**
     * File a finished recording under its place and time. Never overwrites: a name already taken
     * in the same second gets a counter.
     */
    fun file(
        dir: File,
        recording: File,
        place: String,
        startedAt: Long,
        meta: Meta,
    ): Clip? {
        val target = freeName(dir, place, startedAt) ?: return null
        if (!recording.renameTo(target)) return null
        val all = readMeta(dir)
        all[target.name] = meta
        writeMeta(dir, all)
        return Naming.parse(target.name)?.copy(
            durationMs = meta.durationMs,
            latitude = meta.latitude,
            longitude = meta.longitude,
        )
    }

    /** Give a clip a typed name, keeping its timestamp so it stays in place on the tape. */
    fun rename(dir: File, clip: Clip, newName: String): Clip? {
        val clean = Naming.clean(Naming.titleCase(newName.trim()))
        if (clean == clip.place) return clip
        val from = fileOf(dir, clip)
        val to = freeName(dir, clean, clip.startedAt) ?: return null
        if (!from.renameTo(to)) return null
        val all = readMeta(dir)
        all.remove(clip.fileName)?.let { all[to.name] = it }
        writeMeta(dir, all)
        return Naming.parse(to.name)?.copy(
            durationMs = clip.durationMs,
            latitude = clip.latitude,
            longitude = clip.longitude,
        )
    }

    fun delete(dir: File, clip: Clip): Boolean {
        val ok = fileOf(dir, clip).delete()
        if (ok) {
            val all = readMeta(dir)
            if (all.remove(clip.fileName) != null) writeMeta(dir, all)
        }
        return ok
    }

    /**
     * Remove recordings that never finished. An MPEG-4 file is only playable once the recorder
     * has written its index on stop, so one left behind by a process that died mid-recording
     * cannot be recovered and would otherwise sit on the disk forever.
     */
    fun sweepAbandoned(dir: File, except: File? = null) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(RECORDING_PREFIX) && it != except }
            ?.forEach { it.delete() }
    }

    private fun freeName(dir: File, place: String, startedAt: Long): File? {
        val base = Naming.fileName(place, startedAt)
        var candidate = File(dir, base)
        var n = 2
        while (candidate.exists()) {
            if (n > 99) return null
            val stem = base.removeSuffix(Naming.EXTENSION)
            candidate = File(dir, "$stem $n${Naming.EXTENSION}")
            n++
        }
        return candidate
    }

    internal fun readMeta(dir: File): MutableMap<String, Meta> {
        val out = linkedMapOf<String, Meta>()
        val f = File(dir, META_FILE)
        if (!f.isFile) return out
        runCatching {
            f.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@forEach
                val duration = parts[1].toLongOrNull() ?: return@forEach
                val lat = parts.getOrNull(2)?.toDoubleOrNull()
                val lon = parts.getOrNull(3)?.toDoubleOrNull()
                out[parts[0]] = Meta(duration, lat, lon)
            }
        }
        return out
    }

    private fun writeMeta(dir: File, meta: Map<String, Meta>) {
        runCatching {
            val tmp = File(dir, "$META_FILE.tmp")
            tmp.writeText(
                meta.entries.joinToString("") { (name, m) ->
                    "$name\t${m.durationMs}\t${m.latitude ?: ""}\t${m.longitude ?: ""}\n"
                },
            )
            if (!tmp.renameTo(File(dir, META_FILE))) {
                File(dir, META_FILE).writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
