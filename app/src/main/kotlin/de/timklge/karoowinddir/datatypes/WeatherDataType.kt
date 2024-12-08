package de.timklge.karoowinddir.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karoowinddir.KarooWinddirExtension
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse
import de.timklge.karoowinddir.WeatherInterpretation
import de.timklge.karoowinddir.getHeadingFlow
import de.timklge.karoowinddir.screens.WinddirSettings
import de.timklge.karoowinddir.streamCurrentWeatherData
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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WeatherDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-winddir", "weather") {
    private val glance = GlanceRemoteViews()

    // FIXME: Remove. Currently, the data field will permanently show "no sensor" if no data stream is provided
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val currentWeatherData = applicationContext.streamCurrentWeatherData()

            currentWeatherData
                .collect { data ->
                    Log.d(KarooWinddirExtension.TAG, "Wind code: ${data.current.weatherCode}")
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to data.current.weatherCode.toDouble()))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooWinddirExtension.TAG, "Starting weather view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        data class StreamData(val data: OpenMeteoCurrentWeatherResponse, val settings: WinddirSettings)

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            context.streamCurrentWeatherData()
                .combine(context.streamSettings()) { data, settings -> StreamData(data, settings) }
                .onCompletion {
                    // Clear view on completion
                    val result = glance.compose(context, DpSize.Unspecified) { }
                    emitter.updateView(result.remoteViews)
                }
                .collect { (data, settings) ->
                    Log.d(KarooWinddirExtension.TAG, "Updating weather view")
                    val interpretation = WeatherInterpretation.fromWeatherCode(data.current.weatherCode)

                    val result = glance.compose(context, DpSize.Unspecified) {
                        Weather(interpretation, data.current.windDirection.roundToInt(), data.current.windSpeed.roundToInt(), data.current.windGusts.roundToInt())
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