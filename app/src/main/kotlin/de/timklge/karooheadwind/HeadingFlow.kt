package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map


sealed class HeadingResponse {
    data object NoGps: HeadingResponse()
    data object NoWeatherData: HeadingResponse()
    data class Value(val diff: Double): HeadingResponse()
}

fun KarooSystemService.getRelativeHeadingFlow(context: Context): Flow<HeadingResponse> {
    val currentWeatherData = context.streamCurrentWeatherData()

    return getHeadingFlow(context)
        .combine(currentWeatherData) { bearing, data -> bearing to data }
        .map { (bearing, data) ->
            when {
                bearing is HeadingResponse.Value && data != null -> {
                    val windBearing = data.current.windDirection + 180
                    val diff = signedAngleDifference(bearing.diff, windBearing)

                    Log.d(KarooHeadwindExtension.TAG, "Wind bearing: Heading $bearing vs wind $windBearing => $diff")

                    HeadingResponse.Value(diff)
                }
                bearing is HeadingResponse.NoGps -> HeadingResponse.NoGps
                bearing is HeadingResponse.NoWeatherData || data == null -> HeadingResponse.NoWeatherData
                else -> bearing
            }
        }
}

fun KarooSystemService.getHeadingFlow(context: Context): Flow<HeadingResponse> {
    // return flowOf(HeadingResponse.Value(20.0))

    return getGpsCoordinateFlow(context)
        .map { coords ->
            val heading = coords?.bearing
            Log.d(KarooHeadwindExtension.TAG, "Updated gps bearing: $heading")
            val headingValue = heading?.let { HeadingResponse.Value(it) }

            headingValue ?: HeadingResponse.NoGps
        }
        .distinctUntilChanged()
}

fun <T> concatenate(vararg flows: Flow<T>) = flow {
    for (flow in flows) {
        emitAll(flow)
    }
}

fun<T> Flow<T>.dropNullsIfNullEncountered(): Flow<T?> = flow {
    var hadValue = false

    collect { value ->
        if (!hadValue) {
            emit(value)
            if (value != null) hadValue = true
        } else {
            if (value != null) emit(value)
        }
    }
}

suspend fun KarooSystemService.updateLastKnownGps(context: Context) {
    while (true) {
        getGpsCoordinateFlow(context)
            .filterNotNull()
            .throttle(60 * 1_000) // Only update last known gps position once every minute
            .collect { gps ->
                saveLastKnownPosition(context, gps)
            }
        delay(1_000)
    }
}

fun KarooSystemService.getGpsCoordinateFlow(context: Context): Flow<GpsCoordinates?> {
    // return flowOf(GpsCoordinates(52.5164069,13.3784))

    val initialFlow = flow {
        val lastKnownPosition = context.getLastKnownPosition()

        emit(lastKnownPosition)
    }

    val gpsFlow = streamLocation()
        .filter { it.orientation != null }
        .map { GpsCoordinates(it.lat, it.lng, it.orientation) }

    val concatenatedFlow = concatenate(initialFlow, gpsFlow)

    return concatenatedFlow
        .combine(context.streamSettings(this)) { gps, settings -> gps to settings }
        .map { (gps, settings) ->
            gps?.round(settings.roundLocationTo.km.toDouble())
        }
        .dropNullsIfNullEncountered()
}