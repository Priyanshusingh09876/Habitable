package edu.bu.cs501.habitable.domain.usecase

/** A place returned by the geocoder: enough to fetch weather for it. */
data class Place(
    val name: String,
    /** State or region, e.g. "Massachusetts"; null when the service does not say. */
    val region: String?,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Chooses which geocoder result to use for the apartment's city.
 *
 * City names repeat ("Cambridge" is in Massachusetts, Maryland, Ohio…), and
 * Habitable's rules are Massachusetts rules, so a result in [preferRegion]
 * wins; otherwise the service's first (most relevant) result is used.
 * Coordinates outside the valid range are ignored. Pure Kotlin, unit-tested.
 */
object PickPlace {
    const val DEFAULT_REGION = "Massachusetts"

    operator fun invoke(results: List<Place>, preferRegion: String = DEFAULT_REGION): Place? {
        val valid = results.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        return valid.firstOrNull { it.region.equals(preferRegion, ignoreCase = true) } ?: valid.firstOrNull()
    }
}
