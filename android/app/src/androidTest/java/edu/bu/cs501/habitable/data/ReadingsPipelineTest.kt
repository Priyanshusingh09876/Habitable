package edu.bu.cs501.habitable.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.sensor.SensorSample
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import edu.bu.cs501.habitable.domain.rules.HeatRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

/**
 * End-to-end data path on a real (in-memory) Room database:
 * sample -> ReadingsRepository.ingest -> readings/violations/issues/timeline rows.
 * This is the "first end-to-end data path" for Project Update 1.
 */
@RunWith(AndroidJUnit4::class)
class ReadingsPipelineTest {

    private lateinit var db: HabitableDatabase
    private lateinit var apartments: ApartmentRepository
    private lateinit var readings: ReadingsRepository
    private lateinit var issues: IssuesRepository
    private var sensorId = 0L

    private val tenAm = ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, HeatRules.BOSTON).toInstant().toEpochMilli()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        apartments = ApartmentRepository(db)
        readings = ReadingsRepository(db, clock = { tenAm })
        issues = IssuesRepository(db, clock = { tenAm })
        val aptId = apartments.save(
            Apartment(address = "12 Pratt St", unit = "3", city = "Boston", latitude = 42.36, longitude = -71.06, landlordName = "M. Rivera", landlordEmail = "m@example.com")
        )
        sensorId = apartments.ensureSensor(aptId, SimulatedSensorSource.ADDRESS, "Simulated thermometer")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replayColdNight_storesReadings_opensOneIssue_withTimeline() = runTest {
        val summary = readings.replayColdNight(sensorId, tenAm)

        assertThat(summary.samples).isEqualTo(97)
        assertThat(summary.issuesOpened).isEqualTo(1)

        val stored = readings.observeReadings(sensorId, tenAm - 16 * 3_600_000L, tenAm).first()
        assertThat(stored).hasSize(97)
        assertThat(stored.all { ReadingFlag.SIMULATED in it.flags }).isTrue()
        assertThat(stored.none { ReadingFlag.GAP_BEFORE in it.flags }).isTrue()

        val open = issues.open.first()
        assertThat(open).hasSize(1)
        val issue = open.single()
        assertThat(issue.openedAutomatically).isTrue()
        assertThat(issue.status).isEqualTo(IssueStatus.OPEN)
        assertThat(issue.title).contains("No heat")

        val timeline = issues.observeTimeline(issue.id).first()
        assertThat(timeline).isNotEmpty()
        assertThat(timeline.first().note).contains("limit")

        // Two violation runs (the limit drops to 64 at 11 PM), both linked to the same issue.
        val violations = readings.observeViolationsSince(sensorId, tenAm - 16 * 3_600_000L).first()
        assertThat(violations.size).isAtLeast(2)
        assertThat(violations.mapNotNull { it.issueId }.distinct()).containsExactly(issue.id)
    }

    @Test
    fun ingest_flagsFridgeJump_andGap() = runTest {
        val t0 = tenAm - 2 * 3_600_000L
        readings.ingest(sensorId, SensorSample(70.0, 40.0, t0, simulated = false))
        val jump = readings.ingest(sensorId, SensorSample(40.0, 40.0, t0 + 10 * 60_000L, simulated = false))
        assertThat(jump.reading.flags).contains(ReadingFlag.IMPLAUSIBLE_JUMP)

        val gap = readings.ingest(sensorId, SensorSample(41.0, 40.0, t0 + 60 * 60_000L, simulated = false))
        assertThat(gap.reading.flags).contains(ReadingFlag.GAP_BEFORE)

        val q = readings.qualitySummary(sensorId, t0, tenAm)
        assertThat(q.jumps).isEqualTo(1)
        assertThat(q.gaps).isEqualTo(1)
        assertThat(q.allClear).isFalse()
    }

    @Test
    fun escalate_movesThroughTheLegalOrder() = runTest {
        val id = issues.create(edu.bu.cs501.habitable.domain.model.IssueCategory.HEAT, "No heat", "Radiator cold since Monday")
        assertThat(issues.observe(id).first()!!.status).isEqualTo(IssueStatus.OPEN)

        assertThat(issues.advance(id)).isEqualTo(IssueStatus.NOTICE_SENT)
        assertThat(issues.advance(id)).isEqualTo(IssueStatus.INSPECTION_REQUESTED)
        assertThat(issues.advance(id)).isEqualTo(IssueStatus.CERTIFIED)
        assertThat(issues.advance(id)).isEqualTo(IssueStatus.CERTIFIED) // reminder keeps the state

        issues.resolve(id)
        assertThat(issues.observe(id).first()!!.status).isEqualTo(IssueStatus.RESOLVED)
        assertThat(issues.advance(id)).isNull()

        val timeline = issues.observeTimeline(id).first()
        assertThat(timeline.map { it.type.name }).containsAtLeast("NOTE", "NOTICE_SENT", "INSPECTION_REQUESTED", "VIOLATION_CERTIFIED", "REMINDER_SENT", "RESOLVED")
    }
}
