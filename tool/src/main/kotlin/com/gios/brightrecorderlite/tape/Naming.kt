package com.gios.brightrecorderlite.tape

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What a clip is called, on disk and on screen. Ported from BrightRecorder with the container
 * changed from WAV to the M4A that `LightAudioRecorder` writes.
 *
 * On disk the timestamp leads, so the directory sorts in recording order and the tape plays in
 * that order:
 *
 *     2026-08-17 143205 48.86 N, 2.37 E.m4a
 *
 * On screen the place leads, because that is what you scan a list for:
 *
 *     48.86 N, 2.37 E at 17 Aug 2026, 14:32
 *
 * The prefix is a fixed 17 characters, so parsing is a substring and never a search for a
 * separator that a place name might also contain.
 */
object Naming {

    private const val PREFIX = "yyyy-MM-dd HHmmss"
    private const val PREFIX_LENGTH = 17

    const val EXTENSION = ".m4a"

    /** Stands in for a place when there is none. */
    const val NOWHERE = "Somewhere"

    private val FORBIDDEN = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private const val MAX_PLACE = 60

    /** A place or typed name reduced to something that can be part of a filename. */
    fun clean(raw: String): String {
        val stripped = raw.map { c -> if (c in FORBIDDEN || c.isISOControl()) ' ' else c }.joinToString("")
        val collapsed = stripped.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        val trimmed = collapsed.trim()
        return if (trimmed.isEmpty()) NOWHERE else trimmed.take(MAX_PLACE).trim()
    }

    /**
     * A name someone typed, with every word beginning in a capital. The rest of each word is left
     * as typed, so "NYC" survives.
     */
    fun titleCase(raw: String): String =
        raw.split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() }
        }

    /** The filename for a clip started at [epochMillis] in [place]. */
    fun fileName(place: String, epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "${stamp(PREFIX, epochMillis, zone)} ${clean(place)}$EXTENSION"

    /** The place and start time back out of a filename, or null if it is not one of ours. */
    fun parse(fileName: String, zone: TimeZone = TimeZone.getDefault()): Clip? {
        if (!fileName.endsWith(EXTENSION, ignoreCase = true)) return null
        val body = fileName.dropLast(EXTENSION.length)
        if (body.length < PREFIX_LENGTH + 1) return null
        val startedAt = parseStamp(body.take(PREFIX_LENGTH), zone) ?: return null
        val place = body.substring(PREFIX_LENGTH).trim()
        return Clip(
            fileName = fileName,
            place = if (place.isEmpty()) NOWHERE else place,
            startedAt = startedAt,
        )
    }

    /** The folder a tape lives in: the same timestamp-then-name shape as a clip. */
    fun folderName(name: String, epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "${stamp(PREFIX, epochMillis, zone)} ${clean(name)}"

    /** The name and creation time back out of a tape folder, or null if it is not one of ours. */
    fun parseFolder(dirName: String, zone: TimeZone = TimeZone.getDefault()): Pair<String, Long>? {
        if (dirName.length < PREFIX_LENGTH + 1) return null
        val createdAt = parseStamp(dirName.take(PREFIX_LENGTH), zone) ?: return null
        val name = dirName.substring(PREFIX_LENGTH).trim()
        return if (name.isEmpty()) null else name to createdAt
    }

    /** "Bastille, Paris at 17 Aug 2026, 14:32". */
    fun title(place: String, epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "$place at ${whenOnly(epochMillis, zone)}"

    /** "17 Aug 2026, 14:32". */
    fun whenOnly(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        stamp("d MMM yyyy, HH:mm", epochMillis, zone)

    /** "m:ss", or "h:mm:ss" past an hour. */
    fun duration(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun stamp(pattern: String, epochMillis: Long, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(epochMillis))

    private fun parseStamp(text: String, zone: TimeZone): Long? = runCatching {
        SimpleDateFormat(PREFIX, Locale.US).apply {
            isLenient = false
            timeZone = zone
        }.parse(text)?.time
    }.getOrNull()
}
