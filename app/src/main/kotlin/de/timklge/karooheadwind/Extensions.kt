package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.RemoteViewsCompositionResult
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
val lastKnownPositionKey = stringPreferencesKey("lastKnownPosition")

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

suspend fun saveLastKnownPosition(context: Context, gpsCoordinates: GpsCoordinates) {
    Log.i(KarooHeadwindExtension.TAG, "Saving last known position: $gpsCoordinates")

    try {
        context.dataStore.edit { t ->
            t[lastKnownPositionKey] = Json.encodeToString(gpsCoordinates)
        }
    } catch(e: Throwable){
        Log.e(KarooHeadwindExtension.TAG, "Failed to save last known position", e)
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

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun getErrorWidget(glance: GlanceRemoteViews, context: Context, settings: HeadwindSettings?, headingResponse: HeadingResponse?): RemoteViewsCompositionResult {
    return glance.compose(context, DpSize.Unspecified) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.Center) {
            val errorMessage = if (settings?.welcomeDialogAccepted == false) {
                "Headwind app not set up"
            } else if (headingResponse is HeadingResponse.NoGps){
                "No GPS signal"
            } else {
                "Weather data download failed"
            }

            Log.d(KarooHeadwindExtension.TAG, "Error widget: $errorMessage")

            Text(text = errorMessage, style = TextStyle(fontSize = TextUnit(16f, TextUnitType.Sp),
                textAlign = TextAlign.Center,
                color = ColorProvider(Color.Black, Color.White)))
        }
    }
}

fun Context.streamCurrentWeatherData(): Flow<OpenMeteoCurrentWeatherResponse?> {
    return dataStore.data.map { settingsJson ->
        try {
            val data = settingsJson[currentDataKey]
            data?.let { d -> jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(d) }
        } catch (e: Throwable) {
            Log.e(KarooHeadwindExtension.TAG, "Failed to read weather data", e)
            null
        }
    }.distinctUntilChanged().map { response ->
        if (response != null && response.current.time * 1000 >= System.currentTimeMillis() - (1000 * 60 * 60 * 12)){
            response
        } else {
            null
        }
    }
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
            Log.e(KarooHeadwindExtension.TAG, "Failed to read widget preferences", e)
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

                defaultSettings.copy(
                    windUnit = if (preferredUnits.distance == UserProfile.PreferredUnit.UnitType.METRIC) WindUnit.KILOMETERS_PER_HOUR else WindUnit.MILES_PER_HOUR,
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

suspend fun Context.getLastKnownPosition(): GpsCoordinates? {
    val settingsJson = dataStore.data.first()

    try {
        val lastKnownPositionString = settingsJson[lastKnownPositionKey] ?: return null
        val lastKnownPosition = jsonWithUnknownKeys.decodeFromString<GpsCoordinates>(
            lastKnownPositionString
        )

        return lastKnownPosition
    } catch(e: Throwable){
        Log.e(KarooHeadwindExtension.TAG, "Failed to read last known position", e)
        return null
    }
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
suspend fun KarooSystemService.makeOpenMeteoHttpRequest(gpsCoordinates: GpsCoordinates, settings: HeadwindSettings, profile: UserProfile?): HttpResponseState.Complete {
    val precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH
    val temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT

    return callbackFlow {
        // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=surface_pressure,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=1&forecast_days=1&forecast_hours=12
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${gpsCoordinates.lat}&longitude=${gpsCoordinates.lon}&current=surface_pressure,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${precipitationUnit.id}&temperature_unit=${temperatureUnit.id}"

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

                    Log.d(KarooHeadwindExtension.TAG, "Wind bearing: $bearing vs $windBearing => $diff")

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
        .scan(emptyList<HeadingResponse>()) { acc, value -> /* Average over 3 values */
            if (value !is HeadingResponse.Value) return@scan listOf(value)

            val newAcc = acc + value
            if (newAcc.size > 3) newAcc.drop(1) else newAcc
        }
        .map { data ->
            Log.i(KarooHeadwindExtension.TAG, "Heading value: $data")

            if (data.isEmpty()) return@map HeadingResponse.NoGps
            if (data.firstOrNull() !is HeadingResponse.Value) return@map data.first()

            val avgValues = data.mapNotNull { (it as? HeadingResponse.Value)?.diff }

            if (avgValues.isEmpty()) return@map HeadingResponse.NoGps

            val avg = avgValues.average()

            HeadingResponse.Value(avg)
        }
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

@OptIn(FlowPreview::class)
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

@OptIn(FlowPreview::class)
fun KarooSystemService.getGpsCoordinateFlow(context: Context): Flow<GpsCoordinates?> {
    // return flowOf(GpsCoordinates(52.5164069,13.3784))

    val initialFlow = flow {
        val lastKnownPosition = context.getLastKnownPosition()
        if (lastKnownPosition != null) emit(lastKnownPosition)
    }

    val gpsFlow = streamDataFlow(DataType.Type.LOCATION)
        .map { (it as? StreamState.Streaming)?.dataPoint?.values }
        .map { values ->
            val lat = values?.get(DataType.Field.LOC_LATITUDE)
            val lon = values?.get(DataType.Field.LOC_LONGITUDE)
            val bearing = values?.get(DataType.Field.LOC_BEARING)

            if (lat != null && lon != null){
                // Log.d(KarooHeadwindExtension.TAG, "Updated gps coordinates: $lat $lon")
                GpsCoordinates(lat, lon, bearing)
            } else {
                // Log.w(KarooHeadwindExtension.TAG, "Gps unavailable: $values")
                null
            }
        }

    val concatenatedFlow = concatenate(initialFlow, gpsFlow)

    return concatenatedFlow
        .combine(context.streamSettings(this)) { gps, settings -> gps to settings }
        .map { (gps, settings) ->
            gps?.round(settings.roundLocationTo.km.toDouble())
        }
        .dropNullsIfNullEncountered()
}