package edu.bu.cs501.habitable.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Keyboard behaviour shared by every form, so typing feels the same everywhere.
 *
 * The app draws edge to edge, and in that mode Android does not shrink the
 * window when the keyboard opens. HabitableNavHost therefore pads the content
 * by the keyboard's height (`imePadding`), which animates frame by frame with
 * the keyboard on Android 11+. Scrollable screens then keep the focused field
 * in view on their own.
 */

/**
 * Tapping anywhere that is not a field, button or other control closes the
 * keyboard. Controls consume their own taps first, so they are unaffected.
 */
fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) { detectTapGestures(onTap = { focusManager.clearFocus() }) }

/** Hides the keyboard and drops focus, e.g. right after Save, so it does not linger over the next screen. */
class KeyboardDismisser(private val focus: FocusManager, private val keyboard: SoftwareKeyboardController?) {
    operator fun invoke() {
        keyboard?.hide()
        focus.clearFocus()
    }
}

@Composable
fun rememberKeyboardDismisser(): KeyboardDismisser =
    KeyboardDismisser(LocalFocusManager.current, LocalSoftwareKeyboardController.current)
