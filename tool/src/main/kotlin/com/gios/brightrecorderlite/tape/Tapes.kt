package com.gios.brightrecorderlite.tape

import java.io.File

/** One tape on the shelf: a folder of clips with a name. */
data class Tape(
    val dirName: String,
    val name: String,
    val createdAt: Long,
    val clips: Int = 0,
    val durationMs: Long = 0L,
) {
    val isEmpty: Boolean get() = clips == 0
}

/**
 * The shelf of tapes. Ported from BrightRecorder, minus the label pattern.
 *
 * A tape is a directory, `tapes/2026-08-17 143205 Trip to Rome/`, filed like a clip so the shelf
 * sorts by when each tape was started. Renaming is renaming the folder.
 *
 * Nothing here deletes a directory tree: [delete] refuses a tape that still has clips in it,
 * because a recursive delete of recordings that cannot be made again is the one unrecoverable
 * mistake this tool could make.
 */
object Tapes {

    const val DEFAULT_NAME = "Tape"

    fun root(filesDir: File): File = File(filesDir, "tapes").apply { mkdirs() }

    fun dirOf(root: File, tape: Tape): File = File(root, tape.dirName)

    fun list(root: File): List<Tape> {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { read(it) }.sortedWith(compareBy({ it.createdAt }, { it.dirName }))
    }

    fun read(dir: File): Tape? {
        val (name, createdAt) = Naming.parseFolder(dir.name) ?: return null
        val clips = Library.scan(dir)
        return Tape(
            dirName = dir.name,
            name = name,
            createdAt = createdAt,
            clips = clips.size,
            durationMs = clips.sumOf { it.durationMs },
        )
    }

    /** Put a new tape on the shelf, or return the one already filed under that name and second. */
    fun create(root: File, name: String, now: Long): Tape? {
        val clean = Naming.clean(Naming.titleCase(name.trim())).let {
            if (it == Naming.NOWHERE && name.isBlank()) DEFAULT_NAME else it
        }
        val dir = File(root, Naming.folderName(clean, now))
        if (!dir.exists() && !dir.mkdirs()) return null
        return read(dir)
    }

    /** Rename a tape, keeping its creation stamp so the shelf does not reshuffle. */
    fun rename(root: File, tape: Tape, newName: String): Tape? {
        if (newName.isBlank()) return null
        val clean = Naming.clean(Naming.titleCase(newName.trim()))
        if (clean == tape.name) return tape
        val from = dirOf(root, tape)
        val to = File(root, Naming.folderName(clean, tape.createdAt))
        if (to.exists()) return null
        if (!from.renameTo(to)) return null
        return read(to)
    }

    /** Take an empty tape off the shelf. Refuses one with clips still on it. */
    fun delete(root: File, tape: Tape): Boolean {
        val dir = dirOf(root, tape)
        if (!dir.isDirectory) return false
        if (Library.scan(dir).isNotEmpty()) return false
        dir.listFiles()?.forEach { it.delete() }
        return dir.delete()
    }

    /** The shelf is never empty: a first launch gets one tape to record onto. */
    fun ensureOne(root: File, now: Long): List<Tape> {
        val tapes = list(root)
        if (tapes.isNotEmpty()) return tapes
        create(root, DEFAULT_NAME, now)
        return list(root)
    }
}
