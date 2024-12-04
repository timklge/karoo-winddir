package de.timklge.karoowinddir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoData(
    val time: Long, val interval: Int,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
    @SerialName("precipitation") val precipitation: Double,
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("surface_pressure") val surfacePressure: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Double,
    @SerialName("wind_gusts_10m") val windGusts: Double
)

@Serializable
data class OpenMeteoCurrentWeatherResponse(
    val current: OpenMeteoData,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevation: Double,
    @SerialName("utc_offset_seconds") val utfOffsetSeconds: Int
)