package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.text.TextInput
import edu.bu.cs501.habitable.testdata.Gettysburg
import org.junit.Test

/**
 * Boundary tests for every text limit (one below, at, one above), plus the
 * professor's Gettysburg Address paste into each field.
 */
class TextInputTest {

    private fun chars(n: Int) = "a".repeat(n)

    @Test
    fun lengthBoundaries_belowAtAndAboveTheLimit() {
        val max = TextInput.TITLE
        assertThat(TextInput.singleLine(chars(max - 1), max)).hasLength(max - 1)
        assertThat(TextInput.singleLine(chars(max), max)).hasLength(max)
        assertThat(TextInput.singleLine(chars(max + 1), max)).hasLength(max)
    }

    @Test
    fun emptyInput_staysEmpty() {
        assertThat(TextInput.singleLine("", TextInput.TITLE)).isEmpty()
        assertThat(TextInput.multiLine("", TextInput.DESCRIPTION)).isEmpty()
        assertThat(TextInput.noSpaces("", TextInput.EMAIL)).isEmpty()
        assertThat(TextInput.counterText(0, TextInput.TITLE)).isNull()
    }

    @Test
    fun singleLine_turnsPastedLineBreaksAndTabsIntoOneSpace() {
        assertThat(TextInput.singleLine("No heat\n\nin the\tbedroom", 80)).isEqualTo("No heat in the bedroom")
        assertThat(TextInput.singleLine("12 Pratt St\r\nApt 3", 80)).isEqualTo("12 Pratt St Apt 3")
        assertThat(TextInput.singleLine("a \n b", 80)).isEqualTo("a b")
    }

    @Test
    fun singleLine_keepsSpacesTheUserTyped() {
        // Typing "No " must not lose the space before the next word arrives.
        assertThat(TextInput.singleLine("No ", 80)).isEqualTo("No ")
    }

    @Test
    fun multiLine_keepsParagraphsAndNormalisesWindowsLineEndings() {
        assertThat(TextInput.multiLine("one\r\ntwo\rthree\nfour", 100)).isEqualTo("one\ntwo\nthree\nfour")
    }

    @Test
    fun noSpaces_removesEveryKindOfWhitespace() {
        assertThat(TextInput.noSpaces(" landlord@example.com \n", TextInput.EMAIL)).isEqualTo("landlord@example.com")
    }

    @Test
    fun cutting_neverSplitsAnEmojiInHalf() {
        val text = "ab🔥" // "ab" + fire emoji (two UTF-16 chars)
        assertThat(TextInput.safeTake(text, 3)).isEqualTo("ab")
        assertThat(TextInput.safeTake(text, 4)).isEqualTo(text)
        assertThat(TextInput.safeTake(text, 0)).isEmpty()
    }

    @Test
    fun counter_appearsAt80Percent_andSaysWhenTheLimitIsReached() {
        assertThat(TextInput.counterText(79, 100)).isNull()
        assertThat(TextInput.counterText(80, 100)).isEqualTo("80 / 100")
        assertThat(TextInput.counterText(99, 100)).isEqualTo("99 / 100")
        assertThat(TextInput.counterText(100, 100)).isEqualTo("Limit reached: 100 characters")
    }

    @Test
    fun gettysburg_inEveryOneLineField_isCutToItsLimit_onOneLine() {
        val limits = listOf(
            TextInput.ADDRESS, TextInput.UNIT, TextInput.CITY, TextInput.NAME,
            TextInput.TITLE, TextInput.CASE_NUMBER, TextInput.SEARCH,
        )
        for (max in limits) {
            val result = TextInput.singleLine(Gettysburg.TEXT, max)
            assertThat(result).hasLength(max)
            assertThat(result).doesNotContain("\n")
            assertThat(Gettysburg.TEXT.replace("\n\n", " ")).startsWith(result)
        }
    }

    @Test
    fun gettysburg_inTheDescription_fitsWhole_withItsParagraphs() {
        val result = TextInput.multiLine(Gettysburg.TEXT, TextInput.DESCRIPTION)
        assertThat(Gettysburg.TEXT.length).isLessThan(TextInput.DESCRIPTION)
        assertThat(result).isEqualTo(Gettysburg.TEXT)
        assertThat(result).contains("\n\n")
    }

    @Test
    fun gettysburg_inANote_isCutTo500_andTheCounterSaysSo() {
        val result = TextInput.multiLine(Gettysburg.TEXT, TextInput.NOTE)
        assertThat(result).hasLength(TextInput.NOTE)
        assertThat(TextInput.counterText(result.length, TextInput.NOTE)).isEqualTo("Limit reached: 500 characters")
    }

    @Test
    fun gettysburg_inTheEmailField_becomesOneWordWithinTheLimit() {
        val result = TextInput.noSpaces(Gettysburg.TEXT, TextInput.EMAIL)
        assertThat(result).hasLength(TextInput.EMAIL)
        assertThat(result.any { it.isWhitespace() }).isFalse()
    }
}
