package de.timklge.karoowinddir

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.timklge.karoowinddir.datatypes.GpsCoordinates
import de.timklge.karoowinddir.screens.WinddirSettings
import de.timklge.karoowinddir.screens.WinddirStats
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.time.debounce
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

val settingsKey = stringPreferencesKey("settings")
val currentDataKey = stringPreferencesKey("current")
val statsKey = stringPreferencesKey("stats")

suspend fun saveSettings(context: Context, settings: WinddirSettings) {
    context.dataStore.edit { t ->
        t[settingsKey] = Json.encodeToString(settings)
    }
}

suspend fun saveStats(context: Context, stats: WinddirStats) {
    context.dataStore.edit { t ->
        t[statsKey] = Json.encodeToString(stats)
    }
}

suspend fun saveCurrentData(context: Context, forecast: OpenMeteoCurrentWeatherResponse) {
    context.dataStore.edit { t ->
        t[currentDataKey] = Json.encodeToString(forecast)
    }
}

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun Context.streamCurrentWeatherData(): Flow<OpenMeteoCurrentWeatherResponse> {
    return dataStore.data.map { settingsJson ->
        try {
            val data = settingsJson[currentDataKey]
            data?.let { d -> jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(d) }
        } catch (e: Throwable) {
            Log.e(KarooWinddirExtension.TAG, "Failed to read preferences", e)
            null
        }
    }.filterNotNull().distinctUntilChanged().filter { it.current.time * 1000 >= System.currentTimeMillis() - (1000 * 60 * 60 * 3) }
}

fun Context.streamSettings(): Flow<WinddirSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<WinddirSettings>(
                settingsJson[settingsKey] ?: WinddirSettings.defaultSettings
            )
        } catch(e: Throwable){
            Log.e(KarooWinddirExtension.TAG, "Failed to read preferences", e)
            jsonWithUnknownKeys.decodeFromString<WinddirSettings>(WinddirSettings.defaultSettings)
        }
    }.distinctUntilChanged()
}

fun Context.streamStats(): Flow<WinddirStats> {
    return dataStore.data.map { statsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<WinddirStats>(
                statsJson[statsKey] ?: WinddirStats.defaultStats
            )
        } catch(e: Throwable){
            Log.e(KarooWinddirExtension.TAG, "Failed to read stats", e)
            jsonWithUnknownKeys.decodeFromString<WinddirStats>(WinddirStats.defaultStats)
        }
    }.distinctUntilChanged()
}

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.makeOpenMeteoHttpRequest(gpsCoordinates: GpsCoordinates, settings: WinddirSettings): HttpResponseState.Complete {
    return callbackFlow {
        // https://open-meteo.com/en/docs#current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=&daily=&location_mode=csv_coordinates&timeformat=unixtime&forecast_days=3
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${gpsCoordinates.lat}&longitude=${gpsCoordinates.lon}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure&timeformat=unixtime&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${settings.precipitationUnit.id}"

        Log.d(KarooWinddirExtension.TAG, "Http request to ${url}...")

        val listenerId = addConsumer(
            OnHttpResponse.MakeHttpRequest(
                "GET",
                url,
                waitForConnection = false,
            ),
        ) { event: OnHttpResponse ->
            Log.d(KarooWinddirExtension.TAG, "Http response event $event")
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

fun KarooSystemService.getHeadingFlow(): Flow<Int> {
    // return flowOf(2)

    return streamDataFlow(DataType.Type.HEADING)
        .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
        .map { it.roundToInt() }
        .distinctUntilChanged()
}

@OptIn(FlowPreview::class)
fun KarooSystemService.getGpsCoordinateFlow(): Flow<GpsCoordinates> {
    // return flowOf(GpsCoordinates(52.5164069,13.3784))

    return streamDataFlow("TYPE_LOCATION_ID")
        .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
        .mapNotNull { values ->
            val lat = values[DataType.Field.LOC_LATITUDE]
            val lon = values[DataType.Field.LOC_LONGITUDE]

            if (lat != null && lon != null){
                Log.d(KarooWinddirExtension.TAG, "Updated gps coords: $lat $lon")
                GpsCoordinates(lat, lon)
            } else {
                Log.e(KarooWinddirExtension.TAG, "Missing gps values: $values")
                null
            }
        }
        .map { it.round() }
        .distinctUntilChanged { old, new -> old.distanceTo(new).absoluteValue < 0.001 }
        .debounce(Duration.ofSeconds(10))
}