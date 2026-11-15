package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.usecase.Sha256
import org.junit.Test
import java.io.ByteArrayInputStream

class Sha256Test {

    @Test fun `matches the published test vectors`() {
        // FIPS 180-4 examples.
        assertThat(Sha256.of("abc".toByteArray())).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(Sha256.of(ByteArray(0))).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test fun `a stream larger than the buffer hashes the same as the bytes`() {
        val bytes = ByteArray(100_000) { (it % 251).toByte() } // spans many 8 KB blocks
        assertThat(Sha256.of(ByteArrayInputStream(bytes))).isEqualTo(Sha256.of(bytes))
    }

    @Test fun `changing one byte changes the fingerprint`() {
        val original = ByteArray(5_000) { 7 }
        val edited = original.copyOf().also { it[4_999] = 8 }
        val a = Sha256.of(original)
        val b = Sha256.of(edited)
        assertThat(a).hasLength(64)
        assertThat(a).isNotEqualTo(b)
        assertThat(a).isEqualTo(a.lowercase())
    }
}
