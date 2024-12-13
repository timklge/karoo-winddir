package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.OpenMeteoData
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamSettings
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class HeadwindDirectionDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "headwind") {
    private val glance = GlanceRemoteViews()

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.getRelativeHeadingFlow(applicationContext)
                .collect { diff ->
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to diff))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val value: Double, val windSpeed: Double, val settings: HeadwindSettings)

    private fun previewFlow(): Flow<StreamData> {
        return flow {
            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (-20..20).random()

                emit(StreamData(bearing, windSpeed.toDouble(), HeadwindSettings()))
                delay(2_000)
            }
        }
    }


    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting headwind direction view with $emitter")

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            de.timklge.karooheadwind.R.drawable.arrow
        )

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val flow = if (config.preview) {
            previewFlow()
        } else {
            karooSystem.streamDataFlow(dataTypeId)
                .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings(karooSystem)) { (value, data), settings ->
                    StreamData(value, data.current.windSpeed, settings)
                }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            flow.onCompletion {
                    // Clear view on completion
                    val result = glance.compose(context, DpSize.Unspecified) { }
                    emitter.updateView(result.remoteViews)
                }
                .collect { streamData ->
                    Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")
                    val windSpeed = streamData.windSpeed
                    val windDirection = streamData.value
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                    val windSpeedText = headwindSpeed.roundToInt().toString()

                    val result = glance.compose(context, DpSize.Unspecified) {
                        HeadwindDirection(baseBitmap, windDirection.roundToInt(), config.textSize, windSpeedText)
                    }

                    emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping headwind view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}