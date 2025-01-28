package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.WindDirectionIndicatorSetting
import de.timklge.karooheadwind.screens.WindDirectionIndicatorTextSetting
import de.timklge.karooheadwind.streamCurrentWeatherData
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
                    val value = (diff as? HeadingResponse.Value)?.diff ?: 0.0
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val headingResponse: HeadingResponse?, val absoluteWindDirection: Double?, val windSpeed: Double?, val settings: HeadwindSettings? = null)

    private fun previewFlow(): Flow<StreamData> {
        return flow {
            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..20).random()

                emit(StreamData(HeadingResponse.Value(bearing), bearing, windSpeed.toDouble(), HeadwindSettings()))
                delay(2_000)
            }
        }
    }


    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting headwind direction view with $emitter")

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            de.timklge.karooheadwind.R.drawable.circle
        )

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val flow = if (config.preview) {
            previewFlow()
        } else {
            karooSystem.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData()) { headingResponse, data -> StreamData(headingResponse, data?.current?.windDirection, data?.current?.windSpeed) }
                .combine(context.streamSettings(karooSystem)) { data, settings -> data.copy(settings = settings) }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            flow.collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")

                val value = (streamData.headingResponse as? HeadingResponse.Value)?.diff
                if (value == null || streamData.absoluteWindDirection == null || streamData.settings == null || streamData.windSpeed == null){
                    var headingResponse = streamData.headingResponse

                    if (headingResponse is HeadingResponse.Value && (streamData.absoluteWindDirection == null || streamData.windSpeed == null)){
                        headingResponse = HeadingResponse.NoWeatherData
                    }

                    emitter.updateView(getErrorWidget(glance, context, streamData.settings, headingResponse).remoteViews)

                    return@collect
                }

                val windSpeed = streamData.windSpeed
                val windDirection = when (streamData.settings.windDirectionIndicatorSetting){
                    WindDirectionIndicatorSetting.HEADWIND_DIRECTION -> value
                    WindDirectionIndicatorSetting.WIND_DIRECTION -> streamData.absoluteWindDirection + 180
                }
                val text = when (streamData.settings.windDirectionIndicatorTextSetting) {
                    WindDirectionIndicatorTextSetting.HEADWIND_SPEED -> {
                        val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                        headwindSpeed.roundToInt().toString()
                    }
                    WindDirectionIndicatorTextSetting.WIND_SPEED -> windSpeed.roundToInt().toString()
                    WindDirectionIndicatorTextSetting.NONE -> ""
                }

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(baseBitmap, windDirection.roundToInt(), config.textSize, text, viewSize = config.viewSize)
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