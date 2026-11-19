package edu.bu.cs501.habitable.ui

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.text.TextInput
import edu.bu.cs501.habitable.testdata.Gettysburg
import edu.bu.cs501.habitable.ui.setup.SetupViewModel
import org.junit.Test

/** Boundary values for the setup form's rules: what is accepted and what is not. */
class SetupValidationTest {

    @Test
    fun address_needsAtLeastThreeCharacters_notCountingSpaces() {
        assertThat(SetupViewModel.isValidAddress("")).isFalse()
        assertThat(SetupViewModel.isValidAddress("   ")).isFalse()
        assertThat(SetupViewModel.isValidAddress("ab")).isFalse()      // one below
        assertThat(SetupViewModel.isValidAddress(" ab ")).isFalse()    // spaces do not count
        assertThat(SetupViewModel.isValidAddress("1 A")).isTrue()      // exactly three
        assertThat(SetupViewModel.isValidAddress("12 Pratt St")).isTrue()
    }

    @Test
    fun address_atTheMaximumLength_isStillValid() {
        val longest = TextInput.singleLine(Gettysburg.TEXT, TextInput.ADDRESS)
        assertThat(longest).hasLength(TextInput.ADDRESS)
        assertThat(SetupViewModel.isValidAddress(longest)).isTrue()
    }

    @Test
    fun email_boundaries() {
        assertThat(SetupViewModel.isValidEmail("landlord@example.com")).isTrue()
        assertThat(SetupViewModel.isValidEmail("a@b.co")).isTrue()          // shortest real form
        assertThat(SetupViewModel.isValidEmail("a@b.c")).isFalse()          // one-letter ending
        assertThat(SetupViewModel.isValidEmail("landlord@example")).isFalse()
        assertThat(SetupViewModel.isValidEmail("@example.com")).isFalse()
        assertThat(SetupViewModel.isValidEmail("")).isFalse()
    }

    @Test
    fun email_withAPastedSpeech_isRejected_notCrashing() {
        val pasted = TextInput.noSpaces(Gettysburg.TEXT, TextInput.EMAIL)
        assertThat(SetupViewModel.isValidEmail(pasted)).isFalse()
    }
}
