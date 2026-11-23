package edu.bu.cs501.habitable.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.PhotosRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.SyncRepository
import edu.bu.cs501.habitable.data.sensor.BleDeviceScanner
import edu.bu.cs501.habitable.data.sensor.BleSensorSource
import edu.bu.cs501.habitable.data.sensor.BleSupport
import edu.bu.cs501.habitable.data.sensor.FoundSensor
import edu.bu.cs501.habitable.data.settings.Settings
import edu.bu.cs501.habitable.data.settings.SensorInterval
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MoreViewModel(
    private val apartments: ApartmentRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val readings: ReadingsRepository,
    private val scanner: BleDeviceScanner,
    private val photos: PhotosRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    // Thermometer picker: what the scan has found so far, and whether it is still looking.
    private val scanning = MutableStateFlow(false)
    private val found = MutableStateFlow<List<FoundSensor>>(emptyList())
    private var scanJob: Job? = null
    private val scan = combine(scanning, found) { s, f -> s to f }

    val uiState: StateFlow<MoreUiState> = combine(apartments.apartment, settings.settings, message, busy, scan) { apt, st, msg, b, sc ->
        MoreUiState(apartment = apt, settings = st, message = msg, busy = b, scanning = sc.first, found = sc.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setSimulated(simulated: Boolean) = viewModelScope.launch { settings.setSimulatedSensor(simulated) }
    fun setReadingInterval(interval: SensorInterval) = viewModelScope.launch { settings.setReadingInterval(interval) }
    fun clearMessage() { message.value = null }
    fun showMessage(text: String) { message.value = text }

    /** Looks for thermometers nearby for up to [SCAN_SECONDS] seconds. Call after the permissions are granted. */
    fun startScan() {
        scanner.problem()?.let { message.value = it; return }
        scanJob?.cancel()
        found.value = emptyList()
        scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_SECONDS * 1000L) {
                    scanner.nearby()
                        .catch { e -> message.value = e.message ?: "Could not scan for thermometers" }
                        .collect { found.value = it }
                }
            } finally {
                scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    /** Pair the thermometer picked from the list and switch to it. */
    fun chooseSensor(sensor: FoundSensor) = viewModelScope.launch {
        stopScan()
        pair(sensor.address, sensor.name)
    }

    /** Fallback for a thermometer that does not show up in the list: type the address printed on it. */
    fun setBleAddress(address: String) = viewModelScope.launch {
        val cleaned = address.trim().uppercase()
        if (!BleSupport.isValidAddress(cleaned)) {
            message.value = "That is not a Bluetooth address. It looks like A4:C1:38:12:34:56."
            return@launch
        }
        pair(cleaned, null)
    }

    private suspend fun pair(address: String, name: String?) {
        settings.setBleAddress(address, name)
        settings.setSimulatedSensor(false)
        // Create the sensor row now, so Home and Sensors switch to it straight away.
        apartments.getApartment()?.let { apartments.ensureSensor(it.id, address.trim().uppercase(), name ?: BleSensorSource.MODEL) }
        message.value = "Paired ${name ?: address}. Go to Sensors and tap Start monitoring."
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = sync.signInOrRegister(email.trim(), password)) {
            is SyncRepository.Result.Synced -> "Signed in as ${email.trim()}"
            is SyncRepository.Result.Failed -> r.message
            else -> "Could not sign in"
        }
        busy.value = false
    }

    fun signOut() = viewModelScope.launch { settings.setAuth(null, null); message.value = "Logged out. Your data stays on this phone." }

    fun syncNow() = viewModelScope.launch {
        busy.value = true
        val r = sync.syncReadings()
        // Issues go up after readings; skip them if we're offline or signed out.
        val issuesPart = when (r) {
            is SyncRepository.Result.Failed, SyncRepository.Result.NotSignedIn -> ""
            else -> when (val i = sync.syncIssues()) {
                is SyncRepository.Result.Synced -> " · ${i.count} ${if (i.count == 1) "issue" else "issues"} backed up"
                is SyncRepository.Result.Failed -> " · issues not backed up (${i.message})"
                else -> ""
            } + when (val p = sync.syncPhotoAnchors()) {
                // Only the fingerprints go up; the photos stay on the phone.
                is SyncRepository.Result.Synced -> " · ${p.count} photo ${if (p.count == 1) "fingerprint" else "fingerprints"} anchored"
                is SyncRepository.Result.Failed -> " · photo fingerprints not anchored (${p.message})"
                else -> ""
            }
        }
        message.value = when (r) {
            is SyncRepository.Result.Synced -> "Uploaded ${r.count} readings"
            SyncRepository.Result.NothingToSync -> "Everything is already synced"
            SyncRepository.Result.NotSignedIn -> "Sign in to sync"
            is SyncRepository.Result.Failed -> r.message
        } + issuesPart
        busy.value = false
    }

    fun clearDemoData() = viewModelScope.launch {
        readings.clearAll()
        photos.deleteAllFiles() // the photo rows went with their issues; remove the files too
        message.value = "Demo data cleared"
    }
}

data class MoreUiState(
    val apartment: Apartment? = null,
    val settings: Settings? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val found: List<FoundSensor> = emptyList(),
)

/** How long the thermometer picker keeps scanning before it stops by itself. */
private const val SCAN_SECONDS = 30
