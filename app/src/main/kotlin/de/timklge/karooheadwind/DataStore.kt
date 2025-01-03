package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.HeadwindStats
import de.timklge.karooheadwind.screens.HeadwindWidgetSettings
import de.timklge.karooheadwind.screens.WindUnit
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


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
