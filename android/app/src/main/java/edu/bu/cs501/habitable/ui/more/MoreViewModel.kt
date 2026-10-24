package edu.bu.cs501.habitable.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.SyncRepository
import edu.bu.cs501.habitable.data.settings.Settings
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoreViewModel(
    private val apartments: ApartmentRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val readings: ReadingsRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<MoreUiState> = combine(apartments.apartment, settings.settings, message, busy) { apt, st, msg, b ->
        MoreUiState(apartment = apt, settings = st, message = msg, busy = b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setSimulated(simulated: Boolean) = viewModelScope.launch { settings.setSimulatedSensor(simulated) }
    fun setBleAddress(address: String) = viewModelScope.launch { settings.setBleAddress(address.trim().ifBlank { null }) }
    fun clearMessage() { message.value = null }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = sync.signInOrRegister(email.trim(), password)) {
            is SyncRepository.Result.Synced -> "Signed in as ${email.trim()}"
            is SyncRepository.Result.Failed -> r.message
            else -> "Could not sign in"
        }
        busy.value = false
    }

    fun signOut() = viewModelScope.launch { settings.setAuth(null, null); message.value = "Signed out" }

    fun syncNow() = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = sync.syncReadings()) {
            is SyncRepository.Result.Synced -> "Uploaded ${r.count} readings"
            SyncRepository.Result.NothingToSync -> "Everything is already synced"
            SyncRepository.Result.NotSignedIn -> "Sign in to sync"
            is SyncRepository.Result.Failed -> r.message
        }
        busy.value = false
    }

    fun clearDemoData() = viewModelScope.launch {
        readings.clearAll()
        message.value = "Demo data cleared"
    }
}

data class MoreUiState(
    val apartment: Apartment? = null,
    val settings: Settings? = null,
    val message: String? = null,
    val busy: Boolean = false,
)
