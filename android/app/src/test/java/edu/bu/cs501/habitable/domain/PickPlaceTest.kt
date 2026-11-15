package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.usecase.PickPlace
import edu.bu.cs501.habitable.domain.usecase.Place
import org.junit.Test

class PickPlaceTest {

    private val cambridgeMd = Place("Cambridge", "Maryland", 38.56, -76.08)
    private val cambridgeMa = Place("Cambridge", "Massachusetts", 42.37, -71.11)
    private val cambridgeOh = Place("Cambridge", "Ohio", 40.03, -81.59)

    @Test fun `a Massachusetts match wins over an earlier result elsewhere`() {
        assertThat(PickPlace(listOf(cambridgeMd, cambridgeMa, cambridgeOh))).isEqualTo(cambridgeMa)
    }

    @Test fun `without a Massachusetts match the first result is used`() {
        assertThat(PickPlace(listOf(cambridgeMd, cambridgeOh))).isEqualTo(cambridgeMd)
        assertThat(PickPlace(listOf(Place("Somewhere", null, 10.0, 20.0)))!!.name).isEqualTo("Somewhere")
    }

    @Test fun `no results or impossible coordinates give null`() {
        assertThat(PickPlace(emptyList())).isNull()
        assertThat(PickPlace(listOf(Place("Bad", "Massachusetts", 142.0, -71.0)))).isNull()
        assertThat(PickPlace(listOf(Place("Bad", "Massachusetts", 42.0, -271.0), cambridgeOh))).isEqualTo(cambridgeOh)
    }
}
