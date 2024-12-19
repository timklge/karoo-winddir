package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.HeadwindStats
import de.timklge.karooheadwind.screens.HeadwindWidgetSettings
import de.timklge.karooheadwind.screens.PrecipitationUnit
import de.timklge.karooheadwind.screens.TemperatureUnit
import de.timklge.karooheadwind.screens.WindUnit
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.time.debounce
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

val settingsKey = stringPreferencesKey("settings")
val widgetSettingsKey = stringPreferencesKey("widgetSettings")
val currentDataKey = stringPreferencesKey("current")
val statsKey = stringPreferencesKey("stats")

suspend fun saveSettings(context: Context, settings: HeadwindSettings) {
    context.dataStore.edit { t ->
        t[settingsKey] = Json.encodeToString(settings)
    }
}

suspend fun saveWidgetSettings(context: Context, settings: HeadwindWidgetSettings) {
    context.dataStore.edit { t ->
        t[widgetSettingsKey] = Json.encodeToString(settings)
    }
}

suspend fun saveStats(context: Context, stats: HeadwindStats) {
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
            Log.e(KarooHeadwindExtension.TAG, "Failed to read preferences", e)
            null
        }
    }.filterNotNull().distinctUntilChanged().filter { it.current.time * 1000 >= System.currentTimeMillis() - (1000 * 60 * 60 * 12) }
}

fun Context.streamWidgetSettings(): Flow<HeadwindWidgetSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(widgetSettingsKey)){
                jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(settingsJson[widgetSettingsKey]!!)
            } else {
                jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(HeadwindWidgetSettings.defaultWidgetSettings)
            }
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read preferences", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(HeadwindWidgetSettings.defaultWidgetSettings)
        }
    }.distinctUntilChanged()
}

fun Context.streamSettings(karooSystemService: KarooSystemService): Flow<HeadwindSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(settingsKey)){
                jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(settingsJson[settingsKey]!!)
            } else {
                val defaultSettings = jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(HeadwindSettings.defaultSettings)

                val preferredUnits = karooSystemService.streamUserProfile().first().preferredUnit
                val preferredMetric = preferredUnits.distance == UserProfile.PreferredUnit.UnitType.METRIC

                defaultSettings.copy(
                    windUnit = if (preferredUnits.distance == UserProfile.PreferredUnit.UnitType.METRIC) WindUnit.KILOMETERS_PER_HOUR else WindUnit.MILES_PER_HOUR,
                    precipitationUnit = if (preferredMetric) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH,
                    temperatureUnit = if (preferredUnits.temperature == UserProfile.PreferredUnit.UnitType.METRIC) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT
                )
            }
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read preferences", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(HeadwindSettings.defaultSettings)
        }
    }.distinctUntilChanged()
}

fun Context.streamStats(): Flow<HeadwindStats> {
    return dataStore.data.map { statsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<HeadwindStats>(
                statsJson[statsKey] ?: HeadwindStats.defaultStats
            )
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read stats", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindStats>(HeadwindStats.defaultStats)
        }
    }.distinctUntilChanged()
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> {
    return callbackFlow {
        val listenerId = addConsumer { userProfile: UserProfile ->
            trySendBlocking(userProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.makeOpenMeteoHttpRequest(gpsCoordinates: GpsCoordinates, settings: HeadwindSettings): HttpResponseState.Complete {
    return callbackFlow {
        // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=surface_pressure,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=1&forecast_days=1&forecast_hours=12
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${gpsCoordinates.lat}&longitude=${gpsCoordinates.lon}&current=surface_pressure,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${settings.precipitationUnit.id}&temperature_unit=${settings.temperatureUnit.id}"

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

fun signedAngleDifference(angle1: Double, angle2: Double): Double {
    val a1 = angle1 % 360
    val a2 = angle2 % 360
    var diff = abs(a1 - a2)

    val sign = if (a1 < a2) {
        if (diff > 180.0) -1 else 1
    } else {
        if (diff > 180.0) 1 else -1
    }

    if (diff > 180.0) {
        diff = 360.0 - diff
    }

    return sign * diff
}

fun KarooSystemService.getRelativeHeadingFlow(context: Context): Flow<Double> {
    val currentWeatherData = context.streamCurrentWeatherData()

    return getHeadingFlow()
        .filter { it >= 0 }
        .combine(currentWeatherData) { bearing, data -> bearing to data }
        .map { (bearing, data) ->
            val windBearing = data.current.windDirection + 180
            val diff = signedAngleDifference(bearing, windBearing)
            Log.d(KarooHeadwindExtension.TAG, "Wind bearing: $bearing vs $windBearing => $diff")

            diff
        }
}

fun KarooSystemService.getHeadingFlow(): Flow<Double> {
    // return flowOf(20.0)

    return streamDataFlow(DataType.Type.LOCATION)
        .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
        .map { values ->
            val heading = values[DataType.Field.LOC_BEARING]
            Log.d(KarooHeadwindExtension.TAG, "Updated gps bearing: $heading")
            heading ?: 0.0
        }
        .distinctUntilChanged()
        .scan(emptyList<Double>()) { acc, value -> /* Average over 3 values */
            val newAcc = acc + value
            if (newAcc.size > 3) newAcc.drop(1) else newAcc
        }
        .map { it.average() }
}

@OptIn(FlowPreview::class)
fun KarooSystemService.getGpsCoordinateFlow(): Flow<GpsCoordinates> {
    // return flowOf(GpsCoordinates(52.5164069,13.3784))

    return streamDataFlow(DataType.Type.LOCATION)
        .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
        .mapNotNull { values ->
            val lat = values[DataType.Field.LOC_LATITUDE]
            val lon = values[DataType.Field.LOC_LONGITUDE]

            if (lat != null && lon != null){
                Log.d(KarooHeadwindExtension.TAG, "Updated gps coords: $lat $lon")
                GpsCoordinates(lat, lon)
            } else {
                Log.e(KarooHeadwindExtension.TAG, "Missing gps values: $values")
                null
            }
        }
        .map { it.round() }
        .distinctUntilChanged { old, new -> old.distanceTo(new).absoluteValue < 0.001 }
        .debounce(Duration.ofSeconds(10))
}