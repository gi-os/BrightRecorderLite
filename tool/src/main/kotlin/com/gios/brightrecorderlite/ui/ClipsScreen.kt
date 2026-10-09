package com.gios.brightrecorderlite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gios.brightrecorderlite.tape.Clip
import com.gios.brightrecorderlite.tape.Library
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Tapes
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every moment on the tape, in the order the tape plays them. Tap one to play from it; the pencil
 * opens it to rename or delete. Returns the index to play.
 */
class ClipsScreen(
    sealedActivity: SealedLightActivity,
    private val tapeDirName: String,
) : SimpleLightScreen<Int>(sealedActivity) {

    private var shown by mutableIntStateOf(0)

    override fun willShow() {
        shown++
    }

    @Composable
    override fun Content() {
        val dir = remember { File(Tapes.root(lightContext.filesDir), tapeDirName) }
        var clips by remember { mutableStateOf<List<Clip>?>(null) }
        LaunchedEffect(shown) {
            clips = withContext(Dispatchers.IO) { Library.scan(dir, ::measureDurationMs) }
        }
        Themed {
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }),
                    center = LightTopBarCenter.Text("Moments"),
                )
                LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                    val list = clips
                    when {
                        list == null -> Unit
                        list.isEmpty() -> LightText(
                            "Nothing on this tape yet. Hold the wheel, or press the microphone, to record.",
                            LightTextVariant.Copy,
                            modifier = Modifier.padding(1f.gridUnitsAsDp()),
                        )
                        else -> list.forEachIndexed { index, clip ->
                            ListRow(
                                title = clip.place,
                                detail = "${Naming.whenOnly(clip.startedAt)}  ${Naming.duration(clip.durationMs)}",
                                onClick = { goBack(index) },
                                trailing = {
                                    LightIcon(
                                        LightIcons.PENCIL,
                                        modifier = Modifier
                                            .padding(start = 1f.gridUnitsAsDp())
                                            .lightClickable {
                                                navigateTo({ ClipScreen(it, tapeDirName, clip.fileName) })
                                            },
                                        size = 2f,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
