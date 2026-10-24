package edu.bu.cs501.habitable.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.HeatStatus
import edu.bu.cs501.habitable.domain.usecase.BuildChartData
import edu.bu.cs501.habitable.domain.usecase.ChartData
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Home = the vertical slice in one screen: latest reading (Room) -> rule
 * evaluation (domain) -> chart (UI), plus open issues and outdoor weather.
 *
 * One StateFlow<HomeUiState>; everything is derived with combine() from Room
 * flows, so the screen survives rotation and updates live as rows arrive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val apartments: ApartmentRepository,
    private val readings: ReadingsRepository,
    private val weather: WeatherRepository,
    private val issues: IssuesRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** The time window the chart shows: last 16 h, matching the demo night. */
    private val windowMillis = SimulatedSensorSource.REPLAY_HOURS * 3_600_000L

    // Re-evaluated whenever a reading lands; also ticks so "now" stays current.
    private val now = MutableStateFlow(clock())

    private val apartmentFlow = apartments.apartment

    private val sensorIdFlow = apartmentFlow.map { apt -> apt?.let { apartments.primarySensorId() } }

    private val readingsFlow = combine(sensorIdFlow, now) { id, n -> id to n }
        .flatMapLatest { (id, n) -> if (id == null) flowOf(emptyList()) else readings.observeReadings(id, n - windowMillis, n) }

    private val latestFlow = sensorIdFlow.flatMapLatest { id -> if (id == null) flowOf(null) else readings.observeLatest(id) }

    private val violationsFlow = combine(sensorIdFlow, now) { id, n -> id to n }
        .flatMapLatest { (id, n) -> if (id == null) flowOf(emptyList()) else readings.observeViolationsSince(id, n - windowMillis) }

    private val outdoorFlow = combine(apartmentFlow, now) { apt, n -> apt to n }
        .flatMapLatest { (apt, n) -> if (apt == null) flowOf(emptyList()) else weather.observeOutdoor(apt.id, n - windowMillis, n + 3_600_000L) }

    val uiState: StateFlow<HomeUiState> = combine(
        apartmentFlow, latestFlow, readingsFlow, outdoorFlow, violationsFlow,
    ) { apt, latest, list, outdoor, violations ->
        val n = now.value
        val chart = BuildChartData(list, outdoor, n - windowMillis, n)
        val status = latest?.let { HeatRules.evaluate(it.tempF, ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.recordedAtMillis), ZoneId.systemDefault())) }
        val belowMillis = readings.totalBelowMillis(violations, n)
        HomeUiState.Loaded(
            addressLine = apt?.let { listOf(it.address, it.unit).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "",
            latest = latest,
            status = status,
            chart = chart,
            hoursBelow = belowMillis / 3_600_000.0,
            outdoorNowF = outdoor.lastOrNull { it.timeMillis <= n }?.tempF,
        )
    }
        .combine(issues.open) { s, open -> s.copy(openIssues = open) }
        .combine(settings.settings) { s, st -> s.copy(monitoring = st.monitoringEnabled, simulated = st.simulatedSensor) }
        .combine(weather.status) { s, w -> s.copy(weather = w) }
        .map<HomeUiState.Loaded, HomeUiState> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        viewModelScope.launch {
            val apt = apartments.getApartment() ?: return@launch
            weather.refresh(apt.id, apt.latitude, apt.longitude)
        }
    }

    fun refreshWeather() = viewModelScope.launch {
        val apt = apartments.getApartment() ?: return@launch
        weather.refresh(apt.id, apt.latitude, apt.longitude)
    }

    /** Called when the screen resumes so the 16-hour window slides forward. */
    fun tick() { now.value = clock() }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(
        val addressLine: String,
        val latest: Reading?,
        val status: HeatStatus?,
        val chart: ChartData,
        val hoursBelow: Double,
        val outdoorNowF: Double?,
        val openIssues: List<Issue> = emptyList(),
        val monitoring: Boolean = false,
        val simulated: Boolean = true,
        val weather: WeatherStatus = WeatherStatus.Idle,
    ) : HomeUiState
}
