package edu.bu.cs501.habitable.domain.text

/**
 * One place for what each text field accepts, so every form behaves the same
 * when someone types or pastes far more than expected (for example a whole
 * speech into a three-word field).
 *
 *  - Every field has a maximum length; extra text is cut off, never crashes or
 *    breaks the layout, the PDF or the email letter.
 *  - One-line fields turn pasted line breaks and tabs into a single space.
 *  - Cutting never splits an emoji (a surrogate pair) in half.
 *  - The screen shows a counter near the limit and says when the limit is reached,
 *    so text is never shortened silently.
 *
 * Pure Kotlin so the boundaries are unit-tested (TextInputTest).
 */
object TextInput {
    const val ADDRESS = 120
    const val UNIT = 20
    const val CITY = 60
    const val NAME = 80
    const val EMAIL = 120
    const val PASSWORD = 128
    const val TITLE = 80
    const val DESCRIPTION = 2000
    const val NOTE = 500
    const val CASE_NUMBER = 40
    const val SEARCH = 60

    private val LINE_BREAKS = Regex("[ ]*[\\r\\n\\t\\u2028\\u2029]+[ ]*")

    /** For one-line fields: line breaks and tabs become one space, then at most [max] characters. */
    fun singleLine(raw: String, max: Int): String = safeTake(raw.replace(LINE_BREAKS, " "), max)

    /** For multi-line fields: Windows and old Mac line endings become `\n`, then at most [max] characters. */
    fun multiLine(raw: String, max: Int): String =
        safeTake(raw.replace("\r\n", "\n").replace('\r', '\n'), max)

    /** For emails and similar: no spaces or line breaks at all, then at most [max] characters. */
    fun noSpaces(raw: String, max: Int): String = safeTake(raw.filterNot { it.isWhitespace() }, max)

    /** First [max] characters, without leaving half of an emoji at the end. */
    fun safeTake(s: String, max: Int): String {
        if (max <= 0) return ""
        if (s.length <= max) return s
        val cut = s.take(max)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /**
     * What to show under a field: nothing until 80 % of [max], then "n / max",
     * and a clear message once the limit is reached.
     */
    fun counterText(length: Int, max: Int): String? = when {
        max <= 0 -> null
        length >= max -> "Limit reached: $max characters"
        length * 10 >= max * 8 -> "$length / $max"
        else -> null
    }
}
