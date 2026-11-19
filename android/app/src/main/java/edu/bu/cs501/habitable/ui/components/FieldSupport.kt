package edu.bu.cs501.habitable.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.bu.cs501.habitable.domain.text.TextInput

/**
 * The line under a text field: an error or hint on the left, and the character
 * counter on the right once the text is near its limit. Returns null when there is
 * nothing to show, so the field does not reserve an empty line.
 *
 * The counter is a polite live region, so TalkBack says "Limit reached" when a paste
 * is cut off instead of the text disappearing silently.
 */
fun fieldSupport(message: String?, length: Int, max: Int): (@Composable () -> Unit)? {
    val counter = TextInput.counterText(length, max)
    if (message == null && counter == null) return null
    return {
        Row(Modifier.fillMaxWidth()) {
            if (message != null) Text(message, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (counter != null) {
                Text(
                    counter,
                    modifier = Modifier.padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
