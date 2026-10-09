package com.gios.brightrecorderlite.hw

import android.view.KeyEvent

/** The wheel's two directions and its press. */
enum class LightKey { WheelUp, WheelDown, WheelClick }

/**
 * Recognising the Light Phone III's scroll wheel, ported from BrightRecorder.
 *
 * LightOS relabels three scancodes in its key layout: `WHEEL_CCW` (wheel up, scancode 19),
 * `WHEEL_CW` (wheel down, scancode 20) and `WHEEL_CLICK` (wheel press, scancode 66). They are not
 * AOSP keycodes, so the label is resolved at runtime; if this build lacks the labels the raw
 * scancode is trusted, but only from the device that physically owns it, so a paired keyboard's
 * `r` cannot wind the tape.
 */
object LightKeys {

    private const val SCAN_WHEEL_UP = 19
    private const val SCAN_WHEEL_DOWN = 20
    private const val SCAN_WHEEL_CLICK = 66

    private data class Control(val key: LightKey, val device: (String) -> Boolean)

    private val PIXART: (String) -> Boolean = { it == "Pixart pat9126ja" }
    private val GPIO: (String) -> Boolean = { it.startsWith("gpio", ignoreCase = true) }

    private val byScanCode = mapOf(
        SCAN_WHEEL_UP to Control(LightKey.WheelUp, PIXART),
        SCAN_WHEEL_DOWN to Control(LightKey.WheelDown, PIXART),
        SCAN_WHEEL_CLICK to Control(LightKey.WheelClick, GPIO),
    )

    private val byKeyCode: Map<Int, LightKey> by lazy {
        buildMap {
            putLabel("WHEEL_CCW", LightKey.WheelUp)
            putLabel("WHEEL_CW", LightKey.WheelDown)
            putLabel("WHEEL_CLICK", LightKey.WheelClick)
        }
    }

    private fun MutableMap<Int, LightKey>.putLabel(label: String, key: LightKey) {
        val code = runCatching { KeyEvent.keyCodeFromString(label) }.getOrDefault(KeyEvent.KEYCODE_UNKNOWN)
        if (code != KeyEvent.KEYCODE_UNKNOWN) put(code, key)
    }

    /** Which control produced [event], or null if it was not one of ours. */
    fun of(keyCode: Int, event: KeyEvent): LightKey? {
        byKeyCode[keyCode]?.let { return it }
        val device = event.device?.name ?: return null
        val control = byScanCode[event.scanCode] ?: return null
        return if (control.device(device)) control.key else null
    }
}
