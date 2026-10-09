package com.gios.brightrecorderlite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.gios.brightrecorderlite.tape.Naming
import com.gios.brightrecorderlite.tape.Tape
import com.gios.brightrecorderlite.tape.Tapes
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShelfViewModel(private val root: File) : LightViewModel<Unit>() {

    val tapes = MutableStateFlow<List<Tape>>(emptyList())

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            tapes.value = withContext(Dispatchers.IO) { Tapes.ensureOne(root, System.currentTimeMillis()) }
        }
    }

    fun create(name: String, then: (Tape) -> Unit) {
        viewModelScope.launch {
            val tape = withContext(Dispatchers.IO) {
                Tapes.create(root, name, System.currentTimeMillis())
            }
            refresh()
            if (tape != null) then(tape)
        }
    }
}

/** The shelf: every tape, oldest first. Tap one to put it on the machine. */
@InitialScreen
class ShelfScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ShelfViewModel>(sealedActivity) {

    override val viewModelClass = ShelfViewModel::class.java
    override fun createViewModel() = ShelfViewModel(Tapes.root(lightContext.filesDir))

    private fun open(tape: Tape) {
        navigateTo({ TapeScreen(it, tape.dirName) })
    }

    @Composable
    override fun Content() {
        val tapes by viewModel.tapes.collectAsState()
        Themed {
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Tapes"),
                    rightButton = LightBarButton.LightIcon(LightIcons.ADD, onClick = {
                        navigateTo({ TextEditScreen(it, "New tape", "", "CREATE") }) { name ->
                            if (name.isNotBlank()) viewModel.create(name) { open(it) }
                        }
                    }),
                )
                LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                    for (tape in tapes) {
                        ListRow(
                            title = tape.name,
                            detail = detailOf(tape),
                            onClick = { open(tape) },
                        )
                    }
                    if (tapes.isEmpty()) {
                        LightText(
                            "Loading",
                            LightTextVariant.Copy,
                            modifier = Modifier.padding(1f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}

private fun detailOf(tape: Tape): String = when (tape.clips) {
    0 -> "Empty"
    1 -> "1 moment, ${Naming.duration(tape.durationMs)}"
    else -> "${tape.clips} moments, ${Naming.duration(tape.durationMs)}"
}
