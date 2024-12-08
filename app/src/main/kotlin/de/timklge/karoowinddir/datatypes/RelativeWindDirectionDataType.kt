package de.timklge.karoowinddir.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karoowinddir.KarooWinddirExtension
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse
import de.timklge.karoowinddir.getHeadingFlow
import de.timklge.karoowinddir.screens.WinddirSettings
import de.timklge.karoowinddir.streamCurrentWeatherData
import de.timklge.karoowinddir.streamDataFlow
import de.timklge.karoowinddir.streamSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class RelativeWindDirectionDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-winddir", "winddir") {
    private val glance = GlanceRemoteViews()

    private fun signedAngleDifference(angle1: Double, angle2: Double): Double {
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

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val currentWeatherData = applicationContext.streamCurrentWeatherData()

            karooSystem
                .getHeadingFlow()
                .filter { it >= 0 }
                .combine(currentWeatherData) { cardinalDirection, data -> cardinalDirection to data }
                .collect { (cardinalDirection, data) ->
                    val bearing = cardinalDirection * 45.0
                    val windBearing = data.current.windDirection + 180

                    val diff = (signedAngleDifference(bearing, windBearing) + 360) % 360
                    Log.d(KarooWinddirExtension.TAG, "Wind bearing: $bearing vs $windBearing => $diff")
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to diff))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooWinddirExtension.TAG, "Starting relative wind direction view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        data class StreamData(val value: Double, val data: OpenMeteoCurrentWeatherResponse, val settings: WinddirSettings)

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.streamDataFlow(dataTypeId)
                .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings()) { (value, data), settings -> StreamData(value, data, settings) }
                .onCompletion {
                    // Clear view on completion
                    val result = glance.compose(context, DpSize.Unspecified) { }
                    emitter.updateView(result.remoteViews)
                }
                .collect { streamData ->
                    Log.d(KarooWinddirExtension.TAG, "Updating relative wind direction view")
                    val windSpeed = streamData.data.current.windSpeed
                    val windDirection = streamData.value
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                    val windSpeedText = if(streamData.settings.showWindspeedOverlay) "${headwindSpeed.roundToInt()}" else null

                    val result = glance.compose(context, DpSize.Unspecified) {
                        RelativeWindDirection(windDirection.roundToInt(), config.textSize, windSpeedText)
                    }

                    emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Log.d(KarooWinddirExtension.TAG, "Stopping winddir view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}