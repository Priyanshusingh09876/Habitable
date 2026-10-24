package edu.bu.cs501.habitable.data.repository

import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.entity.SensorEntity
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.local.toEntity
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.model.Apartment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One apartment per install for Update 1 (multi-apartment is stretch item 15).
 * Also owns the sensor row so every reading has a sensor_id to hang off.
 */
class ApartmentRepository(
    private val db: HabitableDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val apartments = db.apartmentDao()
    private val sensors = db.sensorDao()

    val apartment: Flow<Apartment?> = apartments.observeFirst().map { it?.toDomain() }

    suspend fun getApartment(): Apartment? = apartments.getFirst()?.toDomain()

    /** Save (or replace) the apartment and make sure it has a simulated sensor row. */
    suspend fun save(apartment: Apartment): Long {
        val existing = apartments.getFirst()
        val id = apartments.upsert(apartment.copy(id = existing?.id ?: 0).toEntity(createdAt = existing?.createdAt ?: clock()))
        ensureSensor(id, SimulatedSensorSource.ADDRESS, "Simulated thermometer")
        return id
    }

    /** Returns the sensor id for [address], creating the row if needed. */
    suspend fun ensureSensor(apartmentId: Long, address: String, model: String, room: String = "Living room"): Long {
        sensors.findByAddress(address)?.let { return it.id }
        return sensors.upsert(
            SensorEntity(
                apartmentId = apartmentId,
                bleAddress = address,
                model = model,
                room = room,
                placementPhotoPath = null,
                addedAt = clock(),
            )
        )
    }

    suspend fun primarySensorId(): Long? = sensors.getFirst()?.id

    companion object {
        /** Boston City Hall; used when the user hasn't geocoded an address yet. */
        const val DEFAULT_LAT = 42.3601
        const val DEFAULT_LON = -71.0589
    }
}
