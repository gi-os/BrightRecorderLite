package com.gios.brightrecorderlite.ui

import android.Manifest
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.gios.brightrecorderlite.hw.LightKey
import com.gios.brightrecorderlite.hw.LightKeys
import com.gios.brightrecorderlite.hw.Press
import com.gios.brightrecorderlite.tape.Clip
import com.gios.brightrecorderlite.tape.Library
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Place
import com.gios.brightrecorderlite.tape.Spot
import com.gios.brightrecorderlite.tape.Timeline
import com.gios.brightrecorderlite.tape.Wind
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioException
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayback
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioRecorder
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightAudioUsage
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.audio.MicSource
import com.thelightphone.sdk.audio.NO_MEDIA_ITEM
import com.thelightphone.sdk.audio.RecorderConfig
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.shared.getOrNull
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the deck screen shows. Refreshed by a ticker, so it is always a whole picture. */
data class DeckUi(
    val ready: Boolean = false,
    val tapeName: String = "",
    val clipCount: Int = 0,
    val playing: Boolean = false,
    val recording: Boolean = false,
    val recordElapsedMs: Long = 0L,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val clipIndex: Int = -1,
    val clipTitle: String? = null,
    val winding: Int = 0,
    val message: String? = null,
)

/** A position fix: where the phone was. */
private data class Fix(val latitude: Double, val longitude: Double)

/**
 * Tape position the one played clip was last left at. Lives for the process, as the detached
 * player's queue does, so reopening a tape that is still playing reattaches instead of reloading.
 */
private object NowLoaded {
    @Volatile var tapeDir: String? = null
    @Volatile var files: List<String> = emptyList()
}

/**
 * The machine: one tape, one player, one recorder.
 *
 * Every clip on the tape is queued in recording order, so playing runs from one moment straight
 * into the next. The wheel winds through that queue by distance ([Wind]); [Timeline] turns a tape
 * position into a clip and an offset, and back.
 */
class DeckViewModel(
    private val audio: LightAudio,
    private val root: File,
    tapeDirName: String,
) : LightViewModel<Unit>() {

    var tapeDirName: String = tapeDirName
        private set

    val ui = MutableStateFlow(DeckUi(tapeName = tapeNameOf(tapeDirName)))
    val clips = MutableStateFlow<List<Clip>>(emptyList())

    /** A runtime permission the screen should ask for. Cleared by [permissionAsked]. */
    val ask = MutableStateFlow<String?>(null)

    private val dir: File get() = File(root, tapeDirName)
    private var timeline = Timeline(emptyList())

    private val player: LightAudioPlayer = openPlayer()
    private var recorder: LightAudioRecorder? = null

    private val wind = Wind()
    private val press = Press()
    private var holdJob: Job? = null
    private var windJob: Job? = null
    private var auditioning = false

    /** Where the head was last put, and when, for reading position between player updates. */
    private var head = 0L
    private var headAt = 0L
    private var pending: Spot? = null
    private var pendingSince = 0L

    private var micGranted = false
    private var locationGranted = false
    private var askedLocation = false

    private var recordingFile: File? = null
    private var recordStartedAt = 0L
    private var recordStartedClock = 0L
    private var locationJob: Job? = null
    private var lastFix: Fix? = null
    private var defaultFix: Fix? = null
    private var message: String? = null

    init {
        viewModelScope.launch {
            player.awaitReady()
            reload(parkAt = null)
        }
        viewModelScope.launch {
            while (isActive) {
                applyPending()
                publish()
                delay(TICK_MS)
            }
        }
    }

    private fun openPlayer(): LightAudioPlayer = try {
        audio.newPlayer(LightAudioUsage.Music, LightAudioPlayback.Detached)
    } catch (_: LightAudioException) {
        audio.newPlayer(LightAudioUsage.Music)
    } catch (_: IllegalStateException) {
        audio.newPlayer(LightAudioUsage.Music)
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch {
            micGranted = granted(Manifest.permission.RECORD_AUDIO)
            locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!locationGranted && !askedLocation) {
                askedLocation = true
                ask.value = Manifest.permission.ACCESS_FINE_LOCATION
            }
            if (locationGranted) warmLocation()
        }
        if (recordingFile == null) viewModelScope.launch { reload(parkAt = null) }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        if (recordingFile != null) stopRecording()
    }

    override fun onAppPause() {
        if (recordingFile != null) stopRecording()
    }

    override fun onCleared() {
        if (recordingFile != null) stopRecording()
        holdJob?.cancel()
        windJob?.cancel()
        if (auditioning) player.pause()
        recorder?.release()
        // Detached: playback carries on after the screen. Attached: this stops it.
        player.release()
        super.onCleared()
    }

    fun permissionAsked() {
        ask.value = null
    }

    /** The tape was renamed under us. */
    fun tapeRenamed(newDirName: String) {
        if (NowLoaded.tapeDir == tapeDirName) NowLoaded.tapeDir = newDirName
        tapeDirName = newDirName
        NowLoaded.files = emptyList()
        viewModelScope.launch { reload(parkAt = null) }
    }

    // ---- loading -------------------------------------------------------------------------

    private suspend fun reload(parkAt: Long?) {
        val d = dir
        val list = withContext(Dispatchers.IO) {
            Library.sweepAbandoned(d, except = recordingFile)
            Library.scan(d, ::measureDurationMs)
        }
        val wasAt = if (NowLoaded.tapeDir == tapeDirName) currentGlobal() else 0L
        clips.value = list
        timeline = Timeline(list)
        if (!player.awaitReady()) return
        val names = list.map { it.fileName }
        val same = NowLoaded.tapeDir == tapeDirName && NowLoaded.files == names &&
            player.currentMediaItemIndex.value != NO_MEDIA_ITEM
        if (!same || parkAt != null) {
            if (NowLoaded.tapeDir != tapeDirName) player.pause()
            NowLoaded.tapeDir = tapeDirName
            NowLoaded.files = names
            val target = parkAt ?: wasAt
            if (list.isEmpty()) {
                player.setMediaQueue(emptyList())
                head = 0L
            } else {
                val spot = timeline.locate(timeline.move(target, 0)) ?: Spot(0, 0)
                player.setMediaQueue(items(list), spot.index)
                setPending(spot)
                head = timeline.globalOf(spot.index, spot.offset)
                headAt = now()
            }
        }
        publish()
    }

    private fun items(list: List<Clip>): List<LightAudioItem> = list.map { clip ->
        LightAudioItem(
            source = LightAudioSource.FileSource(File(dir, clip.fileName)),
            metadata = LightMediaMetadata(
                title = clip.title,
                album = tapeNameOf(tapeDirName),
                durationMs = clip.durationMs,
            ),
        )
    }

    // ---- position --------------------------------------------------------------------------

    private fun now(): Long = SystemClock.elapsedRealtime()

    /** The head's position on the whole tape. */
    private fun currentGlobal(): Long {
        val t = now()
        val playing = player.isPlaying.value
        if (pending != null || t - headAt < HEAD_TRUST_MS) {
            val moved = if (playing) t - headAt else 0L
            return timeline.move(head, moved)
        }
        val index = player.currentMediaItemIndex.value
        if (index == NO_MEDIA_ITEM || index >= timeline.clips.size) return head
        return timeline.globalOf(index, player.positionMs.value)
    }

    private fun setPending(spot: Spot) {
        pending = if (spot.offset > 0L) spot else null
        pendingSince = now()
    }

    /**
     * Put the head at [global]. Inside the clip already loaded this is a plain seek; into another
     * clip it changes queue item first, and the offset is applied once that item has a length.
     */
    private fun seekGlobal(global: Long) {
        if (timeline.isEmpty) return
        val spot = timeline.locate(timeline.move(global, 0)) ?: return
        head = timeline.globalOf(spot.index, spot.offset)
        headAt = now()
        val current = player.currentMediaItemIndex.value
        when {
            current == spot.index && player.durationMs.value > 0L -> {
                pending = null
                player.seekTo(spot.offset)
            }
            current == spot.index -> {
                setPending(spot)
                player.seekTo(spot.offset)
            }
            current >= 0 && spot.index == current + 1 -> {
                setPending(spot)
                player.skipToNext()
            }
            spot.index == current - 1 && current > 0 -> {
                setPending(spot)
                player.skipToPrevious()
            }
            else -> {
                setPending(spot)
                player.setMediaQueue(items(timeline.clips), spot.index)
            }
        }
    }

    /**
     * Land a seek into a clip that was not ready when it was asked for. `seekTo` clamps to the
     * item's resolved length, so until the item is prepared it lands at zero; this repeats it
     * until the head is where it was sent, or gives up after a few seconds.
     */
    private fun applyPending() {
        val p = pending ?: return
        if (now() - pendingSince > PENDING_GIVE_UP_MS) {
            pending = null
            return
        }
        if (player.currentMediaItemIndex.value != p.index) return
        val duration = player.durationMs.value
        if (duration <= 0L) return
        val target = p.offset.coerceAtMost((duration - 50L).coerceAtLeast(0L))
        player.seekTo(target)
        if (abs(player.positionMs.value - target) < LANDED_MS) pending = null
    }

    // ---- transport -------------------------------------------------------------------------

    fun togglePlay() {
        if (recordingFile != null) {
            stopRecording()
            return
        }
        if (player.isPlaying.value) {
            player.pause()
            head = currentGlobal()
            headAt = now()
            return
        }
        if (timeline.isEmpty) return
        val at = currentGlobal()
        if (at >= timeline.durationMs - END_SLACK_MS) seekGlobal(0L)
        player.play()
    }

    /** Skip a whole moment, forwards or back. */
    fun skip(count: Int) {
        if (recordingFile != null || timeline.isEmpty) return
        val target = timeline.seekByClip(currentGlobal(), count)
        if (target >= timeline.durationMs) return
        seekGlobal(target)
    }

    /** Jump to a fraction of the whole tape, from a tap on the tape bar. */
    fun seekFraction(fraction: Float) {
        if (recordingFile != null || timeline.isEmpty) return
        seekGlobal((timeline.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun playClip(index: Int) {
        if (recordingFile != null || index !in timeline.clips.indices) return
        seekGlobal(timeline.startOf(index))
        player.play()
    }

    /**
     * One notch of the wheel. Notches are banked and paid out as short seeks; if the tape was
     * stopped it plays while the wheel turns, so winding is always audible, and stops again when
     * the wheel does.
     */
    private fun notch(direction: Int) {
        if (recordingFile != null || timeline.isEmpty) return
        wind.notch(direction, now())
        ui.value = ui.value.copy(winding = direction)
        if (windJob?.isActive == true) return
        windJob = viewModelScope.launch {
            while (isActive) {
                val owed = wind.drain()
                if (owed != 0L) {
                    if (!player.isPlaying.value && !auditioning) auditioning = true
                    seekGlobal(timeline.move(currentGlobal(), owed))
                    if (auditioning) player.play()
                }
                if (!wind.isTurning(now())) break
                delay(WIND_STEP_MS)
            }
            if (auditioning) {
                auditioning = false
                player.pause()
                head = currentGlobal()
                headAt = now()
            }
            ui.value = ui.value.copy(winding = 0)
        }
    }

    // ---- recording -------------------------------------------------------------------------

    fun record() {
        if (recordingFile != null) {
            stopRecording()
            return
        }
        if (!micGranted) {
            viewModelScope.launch {
                micGranted = granted(Manifest.permission.RECORD_AUDIO)
                if (micGranted) startRecording() else ask.value = Manifest.permission.RECORD_AUDIO
            }
            return
        }
        startRecording()
    }

    private fun startRecording() {
        if (recordingFile != null) return
        wind.still()
        windJob?.cancel()
        auditioning = false
        player.pause()
        val startedAt = System.currentTimeMillis()
        val file = Library.recordingFile(dir, startedAt)
        val started = tryStart(file, MicSource.Unprocessed) || tryStart(file, MicSource.Mic)
        if (!started) {
            message = "The microphone could not be opened"
            publish()
            return
        }
        message = null
        recordingFile = file
        recordStartedAt = startedAt
        recordStartedClock = now()
        if (locationGranted) startLocationLease()
        publish()
    }

    /**
     * Raw input first: the defaults are tuned for speech and a noise suppressor removes exactly
     * the rain, traffic and rooms this records. Not every device offers it, hence the fallback.
     */
    private fun tryStart(file: File, source: MicSource): Boolean {
        recorder?.release()
        val r = audio.newRecorder(RecorderConfig(source = source, sampleRate = 44_100))
        return try {
            r.start(file)
            recorder = r
            true
        } catch (_: LightAudioException) {
            r.release()
            recorder = null
            false
        } catch (_: RuntimeException) {
            r.release()
            recorder = null
            false
        }
    }

    /**
     * Stop and file the recording, at once and without suspending: this also runs from
     * `onCleared`, where no coroutine would get to finish. The place is the best fix already in
     * hand, which is why the fix is fetched when the screen opens and polled while recording.
     */
    private fun stopRecording() {
        val file = recordingFile ?: return
        recordingFile = null
        val durationMs = runCatching { recorder?.stop() ?: 0L }.getOrDefault(0L)
        locationJob?.cancel()
        locationJob = null
        if (durationMs <= 0L || !file.exists()) {
            file.delete()
            message = "Nothing was recorded"
            publish()
            return
        }
        val fix = lastFix ?: defaultFix
        val clip = Library.file(
            dir = dir,
            recording = file,
            place = Place.label(fix?.latitude, fix?.longitude),
            startedAt = recordStartedAt,
            meta = Library.Meta(durationMs, fix?.latitude, fix?.longitude),
        )
        if (clip == null) {
            message = "The recording could not be filed"
            publish()
            return
        }
        // Park the head at the start of the moment just recorded, so a tap plays it back.
        val list = Library.scan(dir)
        val index = list.indexOfFirst { it.fileName == clip.fileName }.coerceAtLeast(0)
        val start = Timeline(list).startOf(index)
        viewModelScope.launch { reload(parkAt = start) }
    }

    // ---- location --------------------------------------------------------------------------

    private suspend fun granted(permission: String): Boolean =
        checkPermission(permission).asKotlinResult
            .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
            .getOrDefault(false)

    private suspend fun currentFix(): Fix? {
        val r = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit).getOrNull()
        val lat = r?.latitude
        val lon = r?.longitude
        return if (lat != null && lon != null) Fix(lat, lon) else null
    }

    private suspend fun dashboardFix(): Fix? {
        val r = callRemoteServiceMethod(LightServiceMethod.GetDefaultLocation, Unit).getOrNull()
        val lat = r?.latitude
        val lon = r?.longitude
        return if (lat != null && lon != null) Fix(lat, lon) else null
    }

    /** Whatever LightOS already knows, fetched before anyone presses record. */
    private suspend fun warmLocation() {
        currentFix()?.let { lastFix = it }
        if (defaultFix == null) defaultFix = dashboardFix()
    }

    /** Hold a location lease for as long as the recording runs, polling the fix. */
    private fun startLocationLease() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            val lease = launch {
                while (isActive) {
                    callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
                    delay(LEASE_RENEW)
                }
            }
            try {
                while (isActive) {
                    currentFix()?.let { lastFix = it }
                    delay(FIX_POLL)
                }
            } finally {
                lease.cancel()
                withContext(NonCancellable) {
                    callRemoteServiceMethod(LightServiceMethod.ReleaseLocationUpdates, Unit)
                }
            }
        }
    }

    // ---- keys ------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (LightKeys.of(keyCode, event)) {
            LightKey.WheelUp -> {
                notch(1)
                true
            }
            LightKey.WheelDown -> {
                notch(-1)
                true
            }
            LightKey.WheelClick -> {
                if (event.repeatCount == 0) {
                    act(press.down(recording = recordingFile != null))
                    holdJob?.cancel()
                    holdJob = viewModelScope.launch {
                        delay(Press.HOLD_MS)
                        act(press.held())
                    }
                }
                true
            }
            null -> false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (LightKeys.of(keyCode, event)) {
            LightKey.WheelClick -> {
                holdJob?.cancel()
                act(press.up())
                true
            }
            LightKey.WheelUp, LightKey.WheelDown -> true
            null -> false
        }
    }

    private fun act(what: Press.Act) {
        when (what) {
            Press.Act.None -> Unit
            Press.Act.Toggle -> togglePlay()
            Press.Act.StartRecording -> record()
            Press.Act.StopRecording -> stopRecording()
        }
    }

    // ---- ui --------------------------------------------------------------------------------

    private fun publish() {
        val recording = recordingFile != null
        val position = if (timeline.isEmpty) 0L else currentGlobal()
        val spot = timeline.locate(position)
        val clip = spot?.let { timeline.clips.getOrNull(it.index) }
        ui.value = ui.value.copy(
            ready = true,
            tapeName = tapeNameOf(tapeDirName),
            clipCount = timeline.clips.size,
            playing = player.isPlaying.value && !auditioning,
            recording = recording,
            recordElapsedMs = if (recording) now() - recordStartedClock else 0L,
            positionMs = position,
            durationMs = timeline.durationMs,
            clipIndex = spot?.index ?: -1,
            clipTitle = clip?.title,
            message = message ?: player.error.value?.let { "This moment could not be played" },
        )
    }

    private companion object {
        const val TICK_MS = 100L
        const val WIND_STEP_MS = 110L
        const val HEAD_TRUST_MS = 700L
        const val PENDING_GIVE_UP_MS = 3_000L
        const val LANDED_MS = 400L
        const val END_SLACK_MS = 300L
        val LEASE_RENEW = 20.seconds
        val FIX_POLL = 2.seconds

        fun tapeNameOf(dirName: String): String =
            Naming.parseFolder(dirName)?.first ?: dirName
    }
}
