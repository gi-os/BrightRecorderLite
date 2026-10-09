package com.gios.brightrecorderlite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Tapes
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.io.File

/**
 * Rename the tape, or take it off the shelf if it is empty. Reports both through callbacks as
 * they happen rather than as a screen result, because the system back gesture pops a screen
 * without delivering one.
 */
class TapeOptionsScreen(
    sealedActivity: SealedLightActivity,
    private val tapeDirName: String,
    private val onRenamed: (String) -> Unit,
    private val onDeleted: () -> Unit,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val root = remember { Tapes.root(lightContext.filesDir) }
        var tape by remember { mutableStateOf(Tapes.read(File(root, tapeDirName))) }
        var error by remember { mutableStateOf<String?>(null) }

        Themed {
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Tape"),
                )
                val t = tape
                Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 2f.gridUnitsAsDp())) {
                    if (t == null) {
                        LightText("This tape is no longer on the shelf.", LightTextVariant.Copy)
                    } else {
                        LightText(t.name, LightTextVariant.Heading)
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                        LightText("STARTED", LightTextVariant.Superfine, lighten = true)
                        LightText(Naming.whenOnly(t.createdAt), LightTextVariant.Copy)
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                        LightText("ON IT", LightTextVariant.Superfine, lighten = true)
                        LightText(
                            "${t.clips} moments, ${Naming.duration(t.durationMs)}",
                            LightTextVariant.Copy,
                        )
                        if (!t.isEmpty) {
                            Spacer(Modifier.height(1f.gridUnitsAsDp()))
                            LightText(
                                "A tape can only be removed once its moments have been deleted one by one.",
                                LightTextVariant.Fine,
                                lighten = true,
                            )
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                        LightText(it, LightTextVariant.Fine)
                    }
                }
                if (t != null) {
                    val buttons = buildList {
                        add(
                            LightBarButton.Text("RENAME", onClick = {
                                navigateTo({ TextEditScreen(it, "Name this tape", t.name) }) { name ->
                                    if (name.isNotBlank()) {
                                        val renamed = Tapes.rename(root, t, name)
                                        if (renamed != null) {
                                            tape = renamed
                                            onRenamed(renamed.dirName)
                                        } else {
                                            error = "That name is taken"
                                        }
                                    }
                                }
                            }),
                        )
                        if (t.isEmpty) {
                            add(
                                LightBarButton.Text("DELETE", onClick = {
                                    if (Tapes.delete(root, t)) {
                                        onDeleted()
                                        goBack()
                                    } else {
                                        error = "Could not remove it"
                                    }
                                }),
                            )
                        }
                    }
                    LightBottomBar(buttons)
                }
            }
        }
    }
}
