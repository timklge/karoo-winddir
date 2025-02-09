package de.timklge.karooheadwind

import kotlinx.serialization.Serializable

@Serializable
data class UpcomingRoute(
    val routePolyline: String? = null,
    val sampledElevationData: SampledElevationData? = null
)

/**
 * Data class representing elevation profile with sampling interval and elevation values
 * @param interval Distance between elevation samples in meters
 * @param elevations Array of elevation values in meters
 */
@Serializable
data class SampledElevationData(
    val interval: Float,
    val elevations: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SampledElevationData

        if (interval != other.interval) return false
        if (!elevations.contentEquals(other.elevations)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = interval.hashCode()
        result = 31 * result + elevations.contentHashCode()
        return result
    }
}