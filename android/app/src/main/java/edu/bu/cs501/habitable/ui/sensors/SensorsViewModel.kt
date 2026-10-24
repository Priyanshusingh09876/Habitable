package edu.bu.cs501.habitable.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.quality.QualitySummary
import edu.bu.cs501.habitable.domain.quality.WeatherCrossCheck
import edu.bu.cs501.habitable.domain.usecase.BuildChartData
import edu.bu.cs501.habitable.domain.usecase.ChartData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChartRange(val label: String, val hours: Int) {
    NIGHT("Night", 16), DAY("24 h", 24), WEEK("7 days", 24 * 7), MONTH("30 days", 24 * 30),
}

@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModel(
    private val apartments: ApartmentRepository,
    private val readings: ReadingsRepository,
    private val weather: WeatherRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val crossCheck = WeatherCrossCheck()
    private val range = MutableStateFlow(ChartRange.NIGHT)
    private val now = MutableStateFlow(clock())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val window = combine(range, now) { r, n -> (n - r.hours * 3_600_000L) to n }

    private val sensorId = apartments.apartment.map { it?.let { apartments.primarySensorId() } }

    private val readingsFlow = combine(sensorId, window) { id, w -> id to w }
        .flatMapLatest { (id, w) -> if (id == null) flowOf(emptyList()) else readings.observeReadings(id, w.first, w.second) }

    private val outdoorFlow = combine(apartments.apartment, window) { apt, w -> apt to w }
        .flatMapLatest { (apt, w) -> if (apt == null) flowOf(emptyList()) else weather.observeOutdoor(apt.id, w.first, w.second + 3_600_000L) }

    private val violationsFlow = combine(sensorId, window) { id, w -> id to w }
        .flatMapLatest { (id, w) -> if (id == null) flowOf(emptyList()) else readings.observeViolationsSince(id, w.first) }

    val uiState: StateFlow<SensorsUiState> = combine(readingsFlow, outdoorFlow, violationsFlow, window, range) { list, outdoor, violations, w, r ->
        val chart = BuildChartData(list, outdoor, w.first, w.second)
        val lowest = list.minByOrNull { it.tempF }
        SensorsUiState(
            range = r,
            chart = chart,
            readingCount = list.size,
            lowest = lowest,
            hoursBelow = readings.totalBelowMillis(violations, w.second) / 3_600_000.0,
            quality = summarize(list),
            weatherMismatchSpans = crossCheck.spans(list, outdoor).size,
        )
    }
        .combine(settings.settings) { s, st -> s.copy(monitoring = st.monitoringEnabled, simulated = st.simulatedSensor) }
        .combine(weather.status) { s, w -> s.copy(weather = w) }
        .combine(busy) { s, b -> s.copy(busy = b) }
        .combine(message) { s, m -> s.copy(message = m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SensorsUiState())

    private fun summarize(list: List<Reading>): QualitySummary =
        edu.bu.cs501.habitable.domain.quality.DataQualityChecker().summarize(list)

    fun setRange(r: ChartRange) { range.value = r; now.value = clock() }
    fun tick() { now.value = clock() }
    fun clearMessage() { message.value = null }

    /** Demo path: ingest the proposal's cold night through the real pipeline. */
    fun replayColdNight() = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try {
            val apt = apartments.getApartment() ?: run { message.value = "Set up your apartment first"; return@launch }
            val id = apartments.ensureSensor(apt.id, SimulatedSensorSource.ADDRESS, "Simulated thermometer")
            val summary = readings.replayColdNight(id, clock())
            now.value = clock()
            message.value = "Replayed ${summary.samples} readings" + if (summary.issuesOpened > 0) " · opened ${summary.issuesOpened} issue" else ""
            weather.refresh(apt.id, apt.latitude, apt.longitude)
        } finally {
            busy.value = false
        }
    }

    fun refreshWeather() = viewModelScope.launch {
        val apt = apartments.getApartment() ?: return@launch
        weather.refresh(apt.id, apt.latitude, apt.longitude)
    }
}

data class SensorsUiState(
    val range: ChartRange = ChartRange.NIGHT,
    val chart: ChartData = ChartData(0, 1, emptyList(), emptyList(), emptyList(), emptyList(), 60.0, 72.0),
    val readingCount: Int = 0,
    val lowest: Reading? = null,
    val hoursBelow: Double = 0.0,
    val quality: QualitySummary = QualitySummary(0, 0, 0, 0),
    /** Periods where the indoor reading tracked a cold outdoor temperature (sensor outside?). */
    val weatherMismatchSpans: Int = 0,
    val monitoring: Boolean = false,
    val simulated: Boolean = true,
    val weather: WeatherStatus = WeatherStatus.Idle,
    val busy: Boolean = false,
    val message: String? = null,
)
