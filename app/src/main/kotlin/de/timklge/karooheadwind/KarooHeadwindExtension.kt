package de.timklge.karooheadwind

import android.util.Log
import com.mapbox.geojson.LineString
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.datatypes.CloudCoverDataType
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.datatypes.HeadwindDirectionDataType
import de.timklge.karooheadwind.datatypes.HeadwindSpeedDataType
import de.timklge.karooheadwind.datatypes.PrecipitationDataType
import de.timklge.karooheadwind.datatypes.RelativeHumidityDataType
import de.timklge.karooheadwind.datatypes.SurfacePressureDataType
import de.timklge.karooheadwind.datatypes.TailwindAndRideSpeedDataType
import de.timklge.karooheadwind.datatypes.TemperatureDataType
import de.timklge.karooheadwind.datatypes.UserWindSpeedDataType
import de.timklge.karooheadwind.datatypes.WeatherDataType
import de.timklge.karooheadwind.datatypes.WeatherForecastDataType
import de.timklge.karooheadwind.datatypes.WindDirectionDataType
import de.timklge.karooheadwind.datatypes.WindGustsDataType
import de.timklge.karooheadwind.datatypes.WindSpeedDataType
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.HeadwindStats
import de.timklge.karooheadwind.screens.HeadwindWidgetSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.debounce
import java.time.Duration
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class KarooHeadwindExtension : KarooExtension("karoo-headwind", "1.2.3") {
    companion object {
        const val TAG = "karoo-headwind"
    }

    private lateinit var karooSystem: KarooSystemService

    private var updateLastKnownGpsJob: Job? = null
    private var serviceJob: Job? = null
    private var updateNavigationJob: Job? = null

    override val types by lazy {
        listOf(
            HeadwindDirectionDataType(karooSystem, applicationContext),
            TailwindAndRideSpeedDataType(karooSystem, applicationContext),
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
            SurfacePressureDataType(applicationContext),
            UserWindSpeedDataType(karooSystem, applicationContext)
        )
    }

    data class StreamData(val settings: HeadwindSettings, val gps: GpsCoordinates?,
                          val profile: UserProfile? = null, val navigationState: UpcomingRoute? = null)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystemService(applicationContext)

        updateLastKnownGpsJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.updateLastKnownGps(this@KarooHeadwindExtension)
        }

        updateNavigationJob = CoroutineScope(Dispatchers.IO).launch {
            var currentKnownRoute: String? = null

            karooSystem.streamNavigationState().combine(karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)) { navigationState, distanceToDestination ->
                navigationState to distanceToDestination
            }.collectLatest { (navigationState, distanceToDestination) ->
                Log.d(TAG, "Got updated navigation state: $navigationState")
                Log.d(TAG, "Got updated distance to destination: $distanceToDestination")

                if (navigationState.state is OnNavigationState.NavigationState.NavigatingRoute){
                    val route = (navigationState.state as OnNavigationState.NavigationState.NavigatingRoute).routePolyline
                    if (route != currentKnownRoute){
                        currentKnownRoute = route

                        val lineString = LineString.fromPolyline(route, 5)

                        var elevationProfile: SampledElevationData?
                        while(true){
                            try {
                                elevationProfile = ValhallaAPIElevationProvider(karooSystem).requestValhallaElevations(lineString)
                                break
                            } catch(e: CancellationException) {
                                throw e
                            } catch(e: Exception){
                                Log.e(TAG, "Failed to request elevation profile", e)
                                delay(1.minutes)
                                continue
                            }
                        }

                        val upcomingRoute = UpcomingRoute(
                            routePolyline = route,
                            sampledElevationData = elevationProfile
                        )

                        try {
                            saveUpcomingRouteData(applicationContext, upcomingRoute)
                            Log.i(TAG, "Saved upcoming route data")
                        } catch(e: Exception){
                            Log.e(TAG, "Failed to write upcoming route", e)
                        }
                    }
                } else {
                    currentKnownRoute = null

                    try {
                        saveUpcomingRouteData(applicationContext, UpcomingRoute())
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to write empty upcoming route", e)
                    }
                }
            }
        }

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    Log.d(TAG, "Connected to Karoo system")
                }
            }

            val gpsFlow = karooSystem
                .getGpsCoordinateFlow(this@KarooHeadwindExtension)
                .distinctUntilChanged { old, new ->
                    if (old != null && new != null) {
                        old.distanceTo(new).absoluteValue < 0.001
                    } else {
                        old == new
                    }
                }
                .debounce(Duration.ofSeconds(5))
                .transformLatest { value: GpsCoordinates? ->
                    while(true){
                        emit(value)
                        delay(1.hours)
                    }
                }

            var requestedGpsCoordinates: List<GpsCoordinates> = mutableListOf()

            streamSettings(karooSystem)
                .filter { it.welcomeDialogAccepted }
                .combine(gpsFlow) { settings, gps -> StreamData(settings, gps) }
                .combine(karooSystem.streamUserProfile()) { data, profile -> data.copy(profile = profile) }
                .combine(streamUpcomingRoute(karooSystem)) { data, navigationState -> data.copy(navigationState = navigationState) }
                .map { (settings, gps, profile, upcomingRoute) ->
                    Log.d(TAG, "Acquired updated gps coordinates: $gps")

                    val lastKnownStats = try {
                        streamStats().first()
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to read stats", e)
                        HeadwindStats()
                    }

                    if (gps == null){
                        error("No GPS coordinates available")
                    }

                    val distanceToDestination = karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION).firstOrNull()?.let { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
                    val routePolyline = upcomingRoute?.routePolyline?.let { str -> LineString.fromPolyline(str, 5) }
                    val routeDistance = routePolyline?.let { TurfMeasurement.length(it, TurfConstants.UNIT_METERS) }

                    if (upcomingRoute != null && routeDistance != null && distanceToDestination != null){
                        val positionOnRoute = routeDistance - distanceToDestination
                        Log.i(TAG, "Position on route: ${positionOnRoute}m")
                        var lastFullHour = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val currentTime = System.currentTimeMillis()
                        val ftp = profile?.ftp ?: 200
                        val weight = profile?.weight ?: 75.0f
                        //val currentElevationIndex = positionOnRoute / upcomingRoute.sampledElevationData?.interval.toFloat()

                        val travelDistanceToTargetHours = mutableMapOf<Long, Double>()
                        do {
                            val nextFullHour = lastFullHour + 60 * 60 * 1000
                            lastFullHour = nextFullHour

                            val timeToTargetHour = nextFullHour - currentTime
                            val travelDistanceToTargetHour = TravelTimeEstimator.estimateDistance((timeToTargetHour / 1000).toInt(), ftp, weight.roundToInt(), upcomingRoute.sampledElevationData)

                            travelDistanceToTargetHours[nextFullHour] = travelDistanceToTargetHour
                            Log.d(TAG, "Travel distance to target in ${java.time.Instant.ofEpochMilli(nextFullHour)}: $travelDistanceToTargetHour meters")
                        } while(travelDistanceToTargetHour < routeDistance - positionOnRoute)
                    }

                    requestedGpsCoordinates = mutableListOf(gps)
                    val response = karooSystem.makeOpenMeteoHttpRequest(requestedGpsCoordinates, settings, profile)
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

                        if (requestedGpsCoordinates.size == 1){
                            val data = jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(responseString)

                            saveCurrentData(applicationContext, listOf(data))

                            Log.d(TAG, "Got updated weather info: $data")
                        } else {
                            val data = jsonWithUnknownKeys.decodeFromString<List<OpenMeteoCurrentWeatherResponse>>(responseString)

                            saveCurrentData(applicationContext, data)

                            Log.d(TAG, "Got updated weather info: $data")
                        }

                        saveWidgetSettings(applicationContext, HeadwindWidgetSettings(currentForecastHourOffset = 0))
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to read current weather data", e)
                    }
                }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null

        updateLastKnownGpsJob?.cancel()
        updateLastKnownGpsJob = null

        updateNavigationJob?.cancel()
        updateNavigationJob = null

        karooSystem.disconnect()
        super.onDestroy()
    }
}