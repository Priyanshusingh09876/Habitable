package edu.bu.cs501.habitable.ui.issuedetail

import android.net.Uri
import edu.bu.cs501.habitable.data.export.PdfEvidenceWriter
import edu.bu.cs501.habitable.data.repository.PhotosRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Photo
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.usecase.BuildEvidenceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.NextAction
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.usecase.NoticeTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IssueDetailViewModel(
    private val issueId: Long,
    private val issues: IssuesRepository,
    apartments: ApartmentRepository,
    private val photos: PhotosRepository,
    private val readings: ReadingsRepository,
    private val weather: WeatherRepository,
    private val pdf: PdfEvidenceWriter,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val uiState: StateFlow<IssueDetailUiState> = combine(
        issues.observe(issueId),
        issues.observeTimeline(issueId),
        issues.observeViolations(issueId),
        apartments.apartment,
        photos.observeForIssue(issueId),
    ) { issue, events, violations, apartment, photoList ->
        if (issue == null) IssueDetailUiState.NotFound
        else IssueDetailUiState.Loaded(issue, events, violations, issue.status.nextAction, apartment, photoList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueDetailUiState.Loading)

    /** True while the PDF is being written, so the button can show progress and ignore a second tap. */
    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** One-shot results for the screen: a PDF ready to share, or a message. Cleared with [eventShown]. */
    private val _event = MutableStateFlow<IssueDetailEvent?>(null)
    val event: StateFlow<IssueDetailEvent?> = _event.asStateFlow()
    fun eventShown() { _event.value = null }

    // ---- Photos (US-4) ----

    /** An empty file for the camera to fill, and the address to give the camera app. */
    fun newCapture(): File = photos.newCapture()
    fun uriFor(file: File): Uri = photos.uriFor(file)
    fun photoFile(photo: Photo): File = photos.fileFor(photo)

    /** The camera came back. [saved] is false when the user backed out without taking a picture. */
    fun photoTaken(file: File, saved: Boolean) = viewModelScope.launch {
        if (!saved) {
            photos.discard(file)
            return@launch
        }
        val photo = photos.add(issueId, file)
        _event.value = IssueDetailEvent.Message(
            if (photo == null) "No photo was saved" else "Photo added · fingerprint ${photo.shortHash}…"
        )
    }

    // ---- PDF evidence packet (US-7) ----

    /** Builds the packet from what is in Room right now and hands the file to the screen to share. */
    fun exportPdf() = viewModelScope.launch {
        val state = uiState.value as? IssueDetailUiState.Loaded ?: return@launch
        if (_exporting.value) return@launch
        _exporting.value = true
        try {
            val now = clock()
            val violations = state.violations
            // Readings and weather around the cold periods, at most the week the chart can show.
            var readingList = emptyList<Reading>()
            var outdoorList = emptyList<OutdoorReading>()
            if (violations.isNotEmpty()) {
                val to = minOf(now, violations.maxOf { it.endedAtMillis ?: now } + PAD_MILLIS)
                val from = maxOf(violations.minOf { it.startedAtMillis } - PAD_MILLIS, to - BuildEvidenceReport.MAX_CHART_MILLIS - PAD_MILLIS)
                readingList = readings.readingsBetween(violations.first().sensorId, from, to)
                state.apartment?.let { outdoorList = weather.observeOutdoor(it.id, from, to).first() }
            }
            val report = BuildEvidenceReport(
                issue = state.issue,
                apartment = state.apartment,
                timeline = issues.timelineOf(issueId),
                violations = violations,
                photos = photos.forIssue(issueId),
                readings = readingList,
                outdoor = outdoorList,
                nowMillis = now,
            )
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US).withZone(HeatRules.BOSTON).format(Instant.ofEpochMilli(now))
            val out = photos.exportFile("Habitable_issue${state.issue.id}_$stamp.pdf")
            withContext(Dispatchers.IO) { pdf.write(report, out) { name -> photos.fileNamed(name) } }
            _event.value = IssueDetailEvent.PdfReady(out)
        } catch (e: Exception) {
            _event.value = IssueDetailEvent.Message("Could not create the PDF: ${e.message ?: "unknown error"}")
        } finally {
            _exporting.value = false
        }
    }

    /** The letter for the current step, or null if this step doesn't send one. */
    fun letterFor(state: IssueDetailUiState.Loaded): NoticeTemplate.Notice? {
        val kind = when (state.nextAction) {
            NextAction.SEND_NOTICE -> NoticeTemplate.Kind.NOTICE
            NextAction.SEND_REMINDER -> NoticeTemplate.Kind.REMINDER
            else -> return null
        }
        return NoticeTemplate.build(state.issue, state.apartment, state.violations, clock(), kind)
    }

    /** The Escalate / Send notice button: moves the issue one step and logs it. */
    fun advance(detail: String? = null) = viewModelScope.launch { issues.advance(issueId, detail) }
    fun resolve() = viewModelScope.launch { issues.resolve(issueId) }
    fun addNote(note: String) = viewModelScope.launch { issues.addNote(issueId, note) }
}

sealed interface IssueDetailUiState {
    data object Loading : IssueDetailUiState
    data object NotFound : IssueDetailUiState
    data class Loaded(
        val issue: Issue,
        val timeline: List<TimelineEvent>,
        val violations: List<Violation>,
        val nextAction: NextAction,
        val apartment: Apartment? = null,
        val photos: List<Photo> = emptyList(),
    ) : IssueDetailUiState
}

sealed interface IssueDetailEvent {
    /** The PDF packet is written and can be handed to the share sheet. */
    data class PdfReady(val file: File) : IssueDetailEvent
    data class Message(val text: String) : IssueDetailEvent
}

/** Readings are fetched this far either side of the cold periods, so the chart has context. */
private const val PAD_MILLIS = 3L * 60 * 60 * 1000
