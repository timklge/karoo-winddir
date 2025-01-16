package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.WeatherInterpretation
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.saveWidgetSettings
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.HeadwindWidgetSettings
import de.timklge.karooheadwind.screens.PrecipitationUnit
import de.timklge.karooheadwind.screens.TemperatureUnit
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.streamWidgetSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class CycleHoursAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooHeadwindExtension.TAG, "Cycling hours")

        val currentSettings = context.streamWidgetSettings().first()
        val data = context.streamCurrentWeatherData().first()

        var hourOffset = currentSettings.currentForecastHourOffset + 3
        if (data == null || hourOffset >= data.forecastData.weatherCode.size) {
            hourOffset = 0
        }

        val newSettings = currentSettings.copy(currentForecastHourOffset = hourOffset)
        saveWidgetSettings(context, newSettings)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WeatherForecastDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "weatherForecast") {
    private val glance = GlanceRemoteViews()

    companion object {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    // FIXME: Remove. Currently, the data field will permanently show "no sensor" if no data stream is provided
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val currentWeatherData = applicationContext.streamCurrentWeatherData()

            currentWeatherData
                .collect { data ->
                    Log.d(KarooHeadwindExtension.TAG, "Wind code: ${data?.current?.weatherCode}")
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to (data?.current?.weatherCode?.toDouble() ?: 0.0)))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting weather forecast view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            de.timklge.karooheadwind.R.drawable.arrow_0
        )

        data class StreamData(val data: OpenMeteoCurrentWeatherResponse?, val settings: HeadwindSettings,
                              val widgetSettings: HeadwindWidgetSettings? = null, val profile: UserProfile? = null, val headingResponse: HeadingResponse? = null)

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            context.streamCurrentWeatherData()
                .combine(context.streamSettings(karooSystem)) { data, settings -> StreamData(data, settings) }
                .combine(karooSystem.streamUserProfile()) { data, profile -> data.copy(profile = profile) }
                .combine(context.streamWidgetSettings()) { data, widgetSettings -> data.copy(widgetSettings = widgetSettings) }
                .combine(karooSystem.getHeadingFlow(context)) { data, headingResponse -> data.copy(headingResponse = headingResponse) }
                .collect { (data, settings, widgetSettings, userProfile, headingResponse) ->
                    Log.d(KarooHeadwindExtension.TAG, "Updating weather forecast view")

                    if (data == null){
                        emitter.updateView(getErrorWidget(glance, context, settings, headingResponse).remoteViews)

                        return@collect
                    }

                    val result = glance.compose(context, DpSize.Unspecified) {
                        Row(modifier = GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CycleHoursAction>()), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                            val hourOffset = widgetSettings?.currentForecastHourOffset ?: 0

                            var previousDate: String? = let {
                                val unixTime = data.forecastData.time.firstOrNull()
                                val formattedDate = unixTime?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }

                                formattedDate
                            }

                            for (index in hourOffset..hourOffset + 2){
                                if (index >= data.forecastData.weatherCode.size) {
                                    break
                                }

                                if (index > hourOffset) {
                                    Spacer(
                                        modifier = GlanceModifier.fillMaxHeight().background(
                                            ColorProvider(Color.Black, Color.White)
                                        ).width(1.dp)
                                    )
                                }

                                val interpretation = WeatherInterpretation.fromWeatherCode(data.forecastData.weatherCode[index])
                                val unixTime = data.forecastData.time[index]
                                val formattedTime = timeFormatter.format(Instant.ofEpochSecond(unixTime))
                                val formattedDate = Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                                val hasNewDate = formattedDate != previousDate || index == 0

                                Weather(baseBitmap,
                                    current = interpretation,
                                    windBearing = data.forecastData.windDirection[index].roundToInt(),
                                    windSpeed = data.forecastData.windSpeed[index].roundToInt(),
                                    windGusts = data.forecastData.windGusts[index].roundToInt(),
                                    windSpeedUnit = settings.windUnit,
                                    precipitation = data.forecastData.precipitation[index],
                                    precipitationProbability = data.forecastData.precipitationProbability[index],
                                    precipitationUnit = if (userProfile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH,
                                    temperature = data.forecastData.temperature[index].roundToInt(),
                                    temperatureUnit = if (userProfile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                                    timeLabel = formattedTime,
                                    dateLabel = if (hasNewDate) formattedDate else null
                                )

                                previousDate = formattedDate
                            }
                        }
                    }

                    emitter.updateView(result.remoteViews)
                }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping headwind weather forecast view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}