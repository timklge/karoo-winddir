package de.timklge.karooheadwind

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
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("weather_code") val weatherCode: Int,
)

enum class WeatherInterpretation {
    CLEAR, CLOUDY, RAINY, SNOWY, DRIZZLE, THUNDERSTORM, UNKNOWN;

    companion object {
        // WMO weather interpretation codes (WW)
        fun fromWeatherCode(code: Int): WeatherInterpretation {
            return when(code){
                0 -> CLEAR
                1, 2, 3 -> CLOUDY
                45, 48, 61, 63, 65, 66, 67, 80, 81, 82 -> RAINY
                71, 73, 75, 77, 85, 86 -> SNOWY
                51, 53, 55, 56, 57 -> DRIZZLE
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
data class OpenMeteoCurrentWeatherResponse(
    val current: OpenMeteoData,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevation: Double,
    @SerialName("utc_offset_seconds") val utfOffsetSeconds: Int
)