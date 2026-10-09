package com.gios.brightrecorderlite.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Tapes
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * The machine with one tape on it. Turn the wheel to wind, tap it to play or stop, hold it to
 * record. The bottom bar does the same by touch.
 */
class TapeScreen(
    private val sealedActivity: SealedLightActivity,
    private val tapeDirName: String,
) : LightScreen<Unit, DeckViewModel>(sealedActivity) {

    override val viewModelClass = DeckViewModel::class.java
    override fun createViewModel() = DeckViewModel(
        audio = DefaultLightAudio(sealedActivity),
        root = Tapes.root(lightContext.filesDir),
        tapeDirName = tapeDirName,
    )

    @Composable
    override fun Content() {
        val ui by viewModel.ui.collectAsState()
        val ask by viewModel.ask.collectAsState()
        val micLauncher = rememberPermissionRequestLauncher(Manifest.permission.RECORD_AUDIO)
        val locationLauncher = rememberPermissionRequestLauncher(Manifest.permission.ACCESS_FINE_LOCATION)

        LaunchedEffect(gone) {
            if (gone) goBack()
        }

        LaunchedEffect(ask) {
            when (ask) {
                Manifest.permission.RECORD_AUDIO -> micLauncher?.launch()
                Manifest.permission.ACCESS_FINE_LOCATION -> locationLauncher?.launch()
                else -> return@LaunchedEffect
            }
            viewModel.permissionAsked()
        }

        Themed {
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(ui.tapeName),
                    rightButton = if (ui.recording) null else LightBarButton.LightIcon(
                        LightIcons.ELLIPSES,
                        onClick = { openOptions() },
                    ),
                )
                Deck(ui, Modifier.fillMaxWidth().weight(1f), onSeek = viewModel::seekFraction)
                LightBottomBar(bottomBar(ui))
            }
        }
    }

    private fun bottomBar(ui: DeckUi): List<LightBarButton> =
        if (ui.recording) {
            listOf(LightBarButton.LightIcon(LightIcons.STOP, onClick = viewModel::record))
        } else {
            listOf(
                LightBarButton.LightIcon(LightIcons.REWIND, onClick = { viewModel.skip(-1) }),
                LightBarButton.LightIcon(
                    if (ui.playing) LightIcons.PAUSE else LightIcons.PLAY,
                    onClick = viewModel::togglePlay,
                ),
                LightBarButton.LightIcon(LightIcons.MICROPHONE, onClick = viewModel::record),
                LightBarButton.LightIcon(LightIcons.FAST_FORWARD, onClick = { viewModel.skip(1) }),
                LightBarButton.LightIcon(LightIcons.LIST, onClick = { openClips() }),
            )
        }

    private fun openClips() {
        val dirName = viewModel.tapeDirName
        navigateTo({ ClipsScreen(it, dirName) }) { index -> viewModel.playClip(index) }
    }

    /** Set when the tape was taken off the shelf from the options screen. */
    private var gone by mutableStateOf(false)

    private fun openOptions() {
        val dirName = viewModel.tapeDirName
        navigateTo({
            TapeOptionsScreen(
                it,
                dirName,
                onRenamed = { renamed -> viewModel.tapeRenamed(renamed) },
                onDeleted = { gone = true },
            )
        })
    }
}

@Composable
private fun Deck(ui: DeckUi, modifier: Modifier, onSeek: (Float) -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 2f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val status = when {
            ui.recording -> "RECORDING"
            ui.winding > 0 -> "WINDING >>"
            ui.winding < 0 -> "<< WINDING"
            ui.playing -> "PLAYING"
            ui.clipCount == 0 -> "EMPTY TAPE"
            else -> "STOPPED"
        }
        LightText(status, LightTextVariant.Detail, align = TextAlign.Center)
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        val counter = if (ui.recording) {
            Naming.duration(ui.recordElapsedMs)
        } else {
            "${Naming.duration(ui.positionMs)} / ${Naming.duration(ui.durationMs)}"
        }
        LightText(counter, LightTextVariant.Heading, align = TextAlign.Center, monospace = true)
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        if (!ui.recording && ui.durationMs > 0L) {
            TapeBar(
                fraction = ui.positionMs.toFloat() / ui.durationMs.toFloat(),
                onSeek = onSeek,
            )
            Spacer(Modifier.height(1f.gridUnitsAsDp()))
        }
        val title = when {
            ui.recording -> "Filed under where and when you are"
            ui.clipTitle != null -> ui.clipTitle
            ui.clipCount == 0 -> "Nothing on this tape yet"
            else -> ""
        }
        LightText(
            title,
            LightTextVariant.Copy,
            align = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!ui.recording && ui.clipIndex >= 0) {
            LightText(
                "Moment ${ui.clipIndex + 1} of ${ui.clipCount}",
                LightTextVariant.Fine,
                align = TextAlign.Center,
                lighten = true,
            )
        }
        ui.message?.let {
            Spacer(Modifier.height(1f.gridUnitsAsDp()))
            LightText(it, LightTextVariant.Fine, align = TextAlign.Center)
        }
        Spacer(Modifier.height(2f.gridUnitsAsDp()))
        LightText(
            if (ui.recording) "Press the wheel to stop." else "Turn the wheel to wind. Tap it to play, hold it to record.",
            LightTextVariant.Fine,
            align = TextAlign.Center,
            lighten = true,
        )
    }
}

/** The whole tape as one bar. Tap anywhere on it to put the head there. */
@Composable
private fun TapeBar(fraction: Float, onSeek: (Float) -> Unit) {
    val colors = LightThemeTokens.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(1f.gridUnitsAsDp())
            .border(1.dp, colors.content)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) onSeek(offset.x / size.width.toFloat())
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(colors.content),
        )
    }
}
