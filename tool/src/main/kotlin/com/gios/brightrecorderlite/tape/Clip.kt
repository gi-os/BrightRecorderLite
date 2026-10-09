package com.gios.brightrecorderlite.tape

/**
 * One recording: where, when, and how long.
 *
 * Where (as a label) and when come from the filename. The length and the raw coordinates come
 * from the tape's small sidecar file, see [Library].
 */
data class Clip(
    val fileName: String,
    val place: String,
    val startedAt: Long,
    val durationMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val title: String get() = Naming.title(place, startedAt)
}
