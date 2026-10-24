package edu.bu.cs501.habitable.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Apartment setup form. Validation lives here so it is unit-testable and the
 * typed values survive rotation (ViewModel) without any saved-state plumbing.
 */
class SetupViewModel(
    private val apartments: ApartmentRepository,
    private val settings: SettingsRepository,
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

    private val _form = MutableStateFlow(Form())
    val form: StateFlow<Form> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            apartments.getApartment()?.let { a ->
                _form.update {
                    it.copy(address = a.address, unit = a.unit, city = a.city, landlordName = a.landlordName, landlordEmail = a.landlordEmail, loadedExisting = true)
                }
            }
        }
    }

    fun setAddress(v: String) = _form.update { it.copy(address = v.take(120), addressError = false) }
    fun setUnit(v: String) = _form.update { it.copy(unit = v.take(20)) }
    fun setCity(v: String) = _form.update { it.copy(city = v.take(60)) }
    fun setLandlordName(v: String) = _form.update { it.copy(landlordName = v.take(80)) }
    fun setLandlordEmail(v: String) = _form.update { it.copy(landlordEmail = v.trim().take(120), emailError = false) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val addressOk = f.address.trim().length >= 3
        val emailOk = f.landlordEmail.isBlank() || isValidEmail(f.landlordEmail)
        if (!addressOk || !emailOk) {
            _form.update { it.copy(addressError = !addressOk, emailError = !emailOk) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            apartments.save(
                Apartment(
                    address = f.address.trim(),
                    unit = f.unit.trim(),
                    city = f.city.trim().ifBlank { "Boston" },
                    // Geocoding is a later story; Boston's coordinates are close enough for hourly weather.
                    latitude = ApartmentRepository.DEFAULT_LAT,
                    longitude = ApartmentRepository.DEFAULT_LON,
                    landlordName = f.landlordName.trim(),
                    landlordEmail = f.landlordEmail.trim(),
                )
            )
            settings.setOnboarded(true)
            _form.update { it.copy(saving = false) }
            onDone()
        }
    }

    companion object {
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        fun isValidEmail(s: String): Boolean = EMAIL.matches(s)
    }
}
