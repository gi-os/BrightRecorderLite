package com.gios.brightrecorderlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightThemeTokens

/** One line of text from the keyboard. Returns the text, or nothing on back. */
class TextEditScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
    private val submitLabel: String = "SAVE",
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val keyboardOptions = rememberKeyboardOptions()
        val state = rememberTextFieldState(initial)
        Themed {
            LightTextInputEditor(
                title = title,
                state = state,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { text -> goBack(text.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = submitLabel,
                singleLine = true,
                initialCaps = true,
            )
        }
    }
}
