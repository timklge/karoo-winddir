package de.timklge.karooheadwind

import android.util.Log
import de.timklge.karooheadwind.datatypes.CloudCoverDataType
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.datatypes.PrecipitationDataType
import de.timklge.karooheadwind.datatypes.RelativeHumidityDataType
import de.timklge.karooheadwind.datatypes.SurfacePressureDataType
import de.timklge.karooheadwind.datatypes.WindDirectionDataType
import de.timklge.karooheadwind.datatypes.WindGustsDataType
import de.timklge.karooheadwind.datatypes.HeadwindSpeedDataType
import de.timklge.karooheadwind.datatypes.HeadwindDirectionDataType
import de.timklge.karooheadwind.datatypes.TemperatureDataType
import de.timklge.karooheadwind.datatypes.WeatherDataType
import de.timklge.karooheadwind.datatypes.WeatherForecastDataType
import de.timklge.karooheadwind.datatypes.WindSpeedDataType
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.HeadwindStats
import de.timklge.karooheadwind.screens.HeadwindWidgetSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class KarooHeadwindExtension : KarooExtension("karoo-headwind", "1.1.1") {
    companion object {
        const val TAG = "karoo-headwind"
    }

    lateinit var karooSystem: KarooSystemService

    private var serviceJob: Job? = null

    override val types by lazy {
        listOf(
            HeadwindDirectionDataType(karooSystem, applicationContext),
            HeadwindSpeedDataType(karooSystem, applicationContext),
            WeatherDataType(karooSystem, applicationContext),
            WeatherForecastDataType(karooSystem, applicationContext),
            HeadwindSpeedDataType(karooSystem, applicationContext),
            RelativeHumidityDataType(applicationContext),
            CloudCoverDataType(applicationContext),
            WindGustsDataType(applicationContext),
            WindSpeedDataType(applicationContext),
            TemperatureDataType(applicationContext),
            WindDirectionDataType(karooSystem, applicationContext),
            PrecipitationDataType(applicationContext),
            SurfacePressureDataType(applicationContext)
        )
    }

    data class StreamData(val settings: HeadwindSettings, val gps: GpsCoordinates,
                          val profile: UserProfile? = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystemService(applicationContext)

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    Log.d(TAG, "Connected to Karoo system")
                }
            }

            val gpsFlow = karooSystem
                .getGpsCoordinateFlow(this@KarooHeadwindExtension)
                .transformLatest { value: GpsCoordinates ->
                    while(true){
                        emit(value)
                        delay(1.hours)
                    }
                }

            streamSettings(karooSystem)
                .filter { it.welcomeDialogAccepted }
                .combine(gpsFlow) { settings, gps -> StreamData(settings, gps) }
                .combine(karooSystem.streamUserProfile()) { data, profile -> data.copy(profile = profile) }
                .map { (settings, gps, profile) ->
                    Log.d(TAG, "Acquired updated gps coordinates: $gps")

                    val lastKnownStats = try {
                        streamStats().first()
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to read stats", e)
                        HeadwindStats()
                    }

                    val response = karooSystem.makeOpenMeteoHttpRequest(gps, settings, profile)
                    if (response.error != null){
                        try {
                            val stats = lastKnownStats.copy(failedWeatherRequest = System.currentTimeMillis())
                            launch { saveStats(this@KarooHeadwindExtension, stats) }
                        } catch(e: Exception){
                            Log.e(TAG, "Failed to write stats", e)
                        }
                        error("HTTP request failed: ${response.error}")
                    } else {
                        try {
                            val stats = lastKnownStats.copy(
                                lastSuccessfulWeatherRequest = System.currentTimeMillis(),
                                lastSuccessfulWeatherPosition = gps
                            )
                            launch { saveStats(this@KarooHeadwindExtension, stats) }
                        } catch(e: Exception){
                            Log.e(TAG, "Failed to write stats", e)
                        }
                    }

                    response
                }
                .retry(Long.MAX_VALUE) { delay(1.minutes); true }
                .collect { response ->
                    try {
                        val responseString = String(response.body ?: ByteArray(0))
                        val data = jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(responseString)

                        saveCurrentData(applicationContext, data)
                        Log.d(TAG, "Got updated weather info: $data")
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to read current weather data", e)
                    }
                }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        karooSystem.disconnect()
        super.onDestroy()
    }
}