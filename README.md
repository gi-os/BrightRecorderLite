# BrightRecorderLite

**Recorder Lite** is a tape recorder for moments, built as an official Light SDK tool for the
Light Phone III. It is the Tool Library version of
[BrightRecorder](https://github.com/gi-os/BrightRecorder), cut down to what a Light SDK tool is
allowed to do.

Record a moment and it is filed by when and where it happened. Keep several tapes, one for a trip,
one for the flat, one for the year. Play a tape back as one continuous length of tape, and wind
through it with the scroll wheel.

Tool id `com.gios.brightrecorderlite`, label "Recorder Lite".

## Using it

- **Shelf.** The first screen lists your tapes, oldest first. `+` starts a new one. Tap a tape to
  put it on the machine.
- **The machine.** One tape at a time.
  - Turn the wheel to wind forwards or backwards. Each notch is 0.3 seconds of tape, so turning
    faster covers more ground. Winding crosses from one moment into the next with no gap. If the
    tape was stopped it plays while the wheel turns, so you hear where you are, and stops again
    when you let go. If it was playing it keeps playing from where you land.
  - Tap the wheel to play or stop. Hold it (0.4 s) to record. Press it again to stop recording.
  - The bottom bar does the same by touch: previous moment, play/pause, record, next moment, and
    the list of moments. Tap the tape bar to jump to that point on the tape.
- **Moments.** The list shows every clip in tape order. Tap one to play from it. The pencil opens
  it: rename it (each word gets a capital, the rest stays as typed) or delete it after a
  confirmation.
- **Tape options** (the `...` button): rename the tape, or delete it once it is empty. A tape with
  moments on it cannot be deleted in one step; delete the moments first. A recursive delete of
  recordings that cannot be made again is the one mistake this tool refuses to make easy.
- **Leaving the screen.** Playback is detached (`detached-audio`), so the tape keeps playing after
  you go back to the shelf or leave the tool. Opening the same tape again reattaches to it.
  Recording stops and is filed when you leave the screen.

## What it keeps from BrightRecorder

| Feature | Notes |
|---|---|
| Tapes as folders, clips as files named by time and place | `tapes/2026-08-17 143205 Trip/2026-08-17 143912 48.86 N, 2.37 E.m4a`. Ported `Naming` and `Tapes`. |
| One continuous tape | Ported `Timeline`, in milliseconds instead of samples. Every clip is queued in recording order on one `LightAudioPlayer`. |
| Wheel winding, audible | Rebuilt as short seeks while playing (`Wind`), see below. |
| Tap the wheel to play/stop, hold to record | Ported `Press` and the `LightKeys` keycode/scancode mapping (`WHEEL_CCW`, `WHEEL_CW`, `WHEEL_CLICK`; scancodes 19, 20, 66). |
| Where and when | Location from `GetCurrentLocation`, with a `RequestLocationUpdates` lease held while recording and `GetDefaultLocation` as the fallback. Latitude and longitude are stored with every clip. |
| Rename and delete moments, rename and delete empty tapes | Same rules as the original. |
| Raw microphone input | `MicSource.Unprocessed` first, falling back to the processed mic where the device does not offer it. |

## What it drops, and why

| Dropped | Why |
|---|---|
| Place names ("Trastevere, Rome") | The original used Android's `Geocoder` (needs an Android `Context`, which tools cannot get) or OpenStreetMap's Nominatim (needs `INTERNET`, which this tool does not request; it asks for the microphone and location only). There is no offline gazetteer. A clip is labeled with its coordinates rounded to two decimals, about a kilometer, or "Somewhere" with no fix. Rename it to give it a real name. |
| Hearing the tape played backwards, pitch-shifted winding at 8x, anti-aliased resampling | The original ran its own PCM audio loop over uncompressed WAV. A tool records through `LightAudioRecorder`, which writes AAC in MPEG-4, and plays through `LightAudioPlayer`, which seeks but cannot play in reverse. Winding is therefore a run of short seeks with a fragment of real audio between them. |
| Uncompressed 22.05 kHz WAV, makeup gain and limiter on the record path | `LightAudioRecorder` only writes AAC/M4A and exposes no gain stage. |
| Loudness normalization (BS.1770) on playback | Needs per-sample access on the playback path; `LightAudioPlayer` has none. |
| Crash-safe recording (rebuilding a WAV header after the process dies) | An MPEG-4 file is only playable once the recorder writes its index on stop. A recording interrupted by the process dying cannot be recovered, and the tool cleans up the partial file on the next open. Leaving the screen or the tool stops and files the recording normally. |
| Renaming a clip later when a better place name arrives, the offline rename queue | No reverse geocoding (see above). |
| Hand-drawn cassette labels, photos on labels, label patterns, "starred" photos from Roll | Read DCIM/Pictures and another app's content provider; tools have no storage or provider access. |
| Sending a moment to BrightChat | `FileProvider` URIs and `Intent`s with read grants; tools cannot start other apps. |
| Shake to report | Needs the accelerometer (`SensorManager` via `getSystemService`) and network access. |
| Foreground recording service, recording notification, keeping the screen on | Tools cannot run Android services or post notifications. Detached playback is the SDK's sanctioned way to keep audio going. |
| Momentary rewind and fast-forward keys that latch and resume | Rebuilt as "previous moment" and "next moment", because `LightBarButton` only reports taps, not press and release. The wheel covers winding. |
| Arbitration with LightControl | A sideload-only concern. Under LightOS the wheel keys reach the focused tool directly. |

## Permissions

- `android.permission.RECORD_AUDIO`, asked the first time you record.
- `android.permission.ACCESS_FINE_LOCATION`, asked once when a tape is first opened. Optional:
  without it, moments are filed under "Somewhere".

Capability: `detached-audio`.

## Storage

Everything lives in the tool's private `filesDir`:

```
tapes/<yyyy-MM-dd HHmmss> <tape name>/
    <yyyy-MM-dd HHmmss> <place or name>.m4a
    .clips          one line per clip: file name, length in ms, latitude, longitude
```

`.clips` is a cache. A clip without a row still plays; its length is read from the file and
written back.

## Build

Needs JDK 17 and the Android SDK (platform 36).

```bash
./gradlew -DlightSdk.toolOnly=true :tool:assembleDebug
./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:lintDebug
```

CI (`.github/workflows/build-tool.yml`) runs tests, lint and assemble on every push.

Unit tests cover the Android-free parts: `Naming`, `Place`, `Timeline`, `Wind`, `Press`,
`Library` and `Tapes`.

## Tool Library submission notes

- All tool code is in `tool/`. The SDK modules (`sdk/`, `plugin/`, `lint-rules/`) are untouched
  upstream code, so the repo can be rebased on `lightphone/light-sdk`.
- No hand-written manifest, no Java sources, no dependencies beyond `:sdk:client`.
- Permissions are limited to the microphone and location. No network access.
- Things to check on a device before submitting: the wheel keycodes under the shipping LightOS
  build, detached playback continuing after the tool closes, and the location lease being released
  after recording.

## Credit

Built on [lightphone/light-sdk](https://github.com/lightphone/light-sdk) (MIT). See
[LICENSE](LICENSE).
