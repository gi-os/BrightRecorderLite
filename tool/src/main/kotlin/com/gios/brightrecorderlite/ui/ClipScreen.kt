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
import com.gios.brightrecorderlite.tape.Clip
import com.gios.brightrecorderlite.tape.Library
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Place
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

/** One moment: where, when, how long. Rename it, or delete it after a confirmation. */
class ClipScreen(
    sealedActivity: SealedLightActivity,
    private val tapeDirName: String,
    private val fileName: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val dir = remember { File(Tapes.root(lightContext.filesDir), tapeDirName) }
        var clip by remember { mutableStateOf(Library.scan(dir).firstOrNull { it.fileName == fileName }) }
        var confirming by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        Themed {
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(if (confirming) "Delete" else "Moment"),
                )
                val c = clip
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 2f.gridUnitsAsDp()),
                ) {
                    if (c == null) {
                        LightText("This moment is no longer on the tape.", LightTextVariant.Copy)
                    } else if (confirming) {
                        LightText(
                            "Delete ${c.place}? A recording cannot be made again.",
                            LightTextVariant.Copy,
                        )
                    } else {
                        Details(c)
                    }
                    error?.let {
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                        LightText(it, LightTextVariant.Fine)
                    }
                }
                if (c != null) {
                    LightBottomBar(
                        if (confirming) {
                            listOf(
                                LightBarButton.Text("CANCEL", onClick = { confirming = false }),
                                LightBarButton.Text("DELETE", onClick = {
                                    if (Library.delete(dir, c)) goBack() else error = "Could not delete it"
                                }),
                            )
                        } else {
                            listOf(
                                LightBarButton.Text("RENAME", onClick = {
                                    navigateTo({ TextEditScreen(it, "Name this moment", c.place) }) { name ->
                                        if (name.isNotBlank()) {
                                            val renamed = Library.rename(dir, c, name)
                                            if (renamed != null) clip = renamed else error = "Could not rename it"
                                        }
                                    }
                                }),
                                LightBarButton.Text("DELETE", onClick = { confirming = true }),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Details(c: Clip) {
    LightText(c.place, LightTextVariant.Heading)
    Spacer(Modifier.height(1f.gridUnitsAsDp()))
    LightText("WHEN", LightTextVariant.Superfine, lighten = true)
    LightText(Naming.whenOnly(c.startedAt), LightTextVariant.Copy)
    Spacer(Modifier.height(1f.gridUnitsAsDp()))
    LightText("LENGTH", LightTextVariant.Superfine, lighten = true)
    LightText(Naming.duration(c.durationMs), LightTextVariant.Copy)
    Spacer(Modifier.height(1f.gridUnitsAsDp()))
    LightText("WHERE", LightTextVariant.Superfine, lighten = true)
    LightText(
        if (c.latitude != null && c.longitude != null) Place.label(c.latitude, c.longitude) else "Not known",
        LightTextVariant.Copy,
    )
}
