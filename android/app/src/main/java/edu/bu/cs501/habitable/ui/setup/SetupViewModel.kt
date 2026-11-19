package edu.bu.cs501.habitable.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.text.TextInput
import kotlinx.coroutines.launch

/**
 * Apartment setup form. Validation lives here so it is unit-testable and the
 * typed values survive rotation (ViewModel) without any saved-state plumbing.
 */
class SetupViewModel(
    private val apartments: ApartmentRepository,
    private val settings: SettingsRepository,
    private val weather: WeatherRepository,
) : ViewModel() {

    data class Form(
        val address: String = "",
        val unit: String = "",
        val city: String = "Boston",
        val landlordName: String = "",
        val landlordEmail: String = "",
        val addressError: Boolean = false,
        val emailError: Boolean = false,
        val saving: Boolean = false,
        val loadedExisting: Boolean = false,
    )

    /**
     * Held in Compose state, not a StateFlow: text fields must see each keystroke in the same
     * frame, or fast typing can drop characters and jump the cursor. Still survives rotation
     * because it lives in the ViewModel.
     */
    var form by mutableStateOf(Form())
        private set

    private inline fun update(change: (Form) -> Form) {
        form = change(form)
    }

    init {
        viewModelScope.launch {
            apartments.getApartment()?.let { a ->
                if (form != Form()) return@let // the user started typing before it loaded; keep their text
                update {
                    it.copy(address = a.address, unit = a.unit, city = a.city, landlordName = a.landlordName, landlordEmail = a.landlordEmail, loadedExisting = true)
                }
            }
        }
    }

    // Limits and paste clean-up: see TextInput.
    fun setAddress(v: String) = update { it.copy(address = TextInput.singleLine(v, TextInput.ADDRESS), addressError = false) }
    fun setUnit(v: String) = update { it.copy(unit = TextInput.singleLine(v, TextInput.UNIT)) }
    fun setCity(v: String) = update { it.copy(city = TextInput.singleLine(v, TextInput.CITY)) }
    fun setLandlordName(v: String) = update { it.copy(landlordName = TextInput.singleLine(v, TextInput.NAME)) }
    fun setLandlordEmail(v: String) = update { it.copy(landlordEmail = TextInput.noSpaces(v, TextInput.EMAIL), emailError = false) }

    fun save(onDone: () -> Unit) {
        val f = form
        val addressOk = isValidAddress(f.address)
        val emailOk = f.landlordEmail.isBlank() || isValidEmail(f.landlordEmail)
        if (!addressOk || !emailOk) {
            update { it.copy(addressError = !addressOk, emailError = !emailOk) }
            return
        }
        update { it.copy(saving = true) }
        viewModelScope.launch {
            val city = f.city.trim().ifBlank { "Boston" }
            // Look the city up so the outdoor weather is for the right place. If that fails
            // (offline, unknown name) keep what we had for the same city, else fall back to Boston.
            val existing = apartments.getApartment()
            val place = weather.findCity(city)
            val sameCity = existing != null && existing.city.equals(city, ignoreCase = true)
            apartments.save(
                Apartment(
                    address = f.address.trim(),
                    unit = f.unit.trim(),
                    city = city,
                    latitude = place?.latitude ?: existing?.latitude?.takeIf { sameCity } ?: ApartmentRepository.DEFAULT_LAT,
                    longitude = place?.longitude ?: existing?.longitude?.takeIf { sameCity } ?: ApartmentRepository.DEFAULT_LON,
                    landlordName = f.landlordName.trim(),
                    landlordEmail = f.landlordEmail.trim(),
                )
            )
            settings.setOnboarded(true)
            update { it.copy(saving = false) }
            onDone()
        }
    }

    companion object {
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        fun isValidEmail(s: String): Boolean = EMAIL.matches(s)

        /** At least 3 characters besides spaces, e.g. "1 A". */
        fun isValidAddress(s: String): Boolean = s.trim().length >= MIN_ADDRESS

        const val MIN_ADDRESS = 3
    }
}
