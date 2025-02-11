package de.timklge.karooheadwind

import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.PrecipitationUnit
import de.timklge.karooheadwind.screens.TemperatureUnit
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.makeOpenMeteoHttpRequest(gpsCoordinates: GpsCoordinates, settings: HeadwindSettings, profile: UserProfile?): HttpResponseState.Complete {
    val precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH
    val temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT

    return callbackFlow {
        // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=1&forecast_days=1&forecast_hours=12
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${gpsCoordinates.lat}&longitude=${gpsCoordinates.lon}&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${precipitationUnit.id}&temperature_unit=${temperatureUnit.id}"

        Log.d(KarooHeadwindExtension.TAG, "Http request to ${url}...")

        val listenerId = addConsumer(
            OnHttpResponse.MakeHttpRequest(
                "GET",
                url,
                waitForConnection = false,
            ),
        ) { event: OnHttpResponse ->
            Log.d(KarooHeadwindExtension.TAG, "Http response event $event")
            if (event.state is HttpResponseState.Complete){
                trySend(event.state as HttpResponseState.Complete)
                close()
            }
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }.timeout(20.seconds).catch { e: Throwable ->
        if (e is TimeoutCancellationException){
            emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
        } else {
            throw e
        }
    }.single()
}
