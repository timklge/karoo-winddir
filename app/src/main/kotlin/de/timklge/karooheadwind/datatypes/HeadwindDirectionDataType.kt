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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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
                .combine(applicationContext.streamCurrentWeatherData()) { headingResponse, data -> StreamData(headingResponse, data?.current?.windDirection, data?.current?.windSpeed) }
                .combine(applicationContext.streamSettings(karooSystem)) { data, settings -> data.copy(settings = settings) }
                .collect { streamData ->
                    val value = (streamData.headingResponse as? HeadingResponse.Value)?.diff

                    var returnValue = 0.0
                    if (value == null || streamData.absoluteWindDirection == null || streamData.settings == null || streamData.windSpeed == null){
                        var errorCode = 1.0
                        var headingResponse = streamData.headingResponse

                        if (headingResponse is HeadingResponse.Value && (streamData.absoluteWindDirection == null || streamData.windSpeed == null)){
                            headingResponse = HeadingResponse.NoWeatherData
                        }

                        if (streamData.settings?.welcomeDialogAccepted == false){
                            errorCode = ERROR_APP_NOT_SET_UP.toDouble()
                        } else if (headingResponse is HeadingResponse.NoGps){
                            errorCode = ERROR_NO_GPS.toDouble()
                        } else {
                            errorCode = ERROR_NO_WEATHER_DATA.toDouble()
                        }

                        returnValue = errorCode
                    } else {
                        var windDirection = when (streamData.settings.windDirectionIndicatorSetting){
                            WindDirectionIndicatorSetting.HEADWIND_DIRECTION -> value
                            WindDirectionIndicatorSetting.WIND_DIRECTION -> streamData.absoluteWindDirection + 180
                        }

                        if (windDirection < 0) windDirection += 360

                        returnValue = windDirection
                    }

                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to returnValue))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val headingResponse: HeadingResponse?, val absoluteWindDirection: Double?, val windSpeed: Double?, val settings: HeadwindSettings? = null)

    data class DirectionAndSpeed(val bearing: Double, val speed: Double?)

    private fun previewFlow(): Flow<DirectionAndSpeed> {
        return flow {
            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..20).random()

                emit(DirectionAndSpeed(bearing, windSpeed.toDouble()))

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
            val directionFlow = karooSystem.streamDataFlow(dataTypeId).mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            val speedFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "userwindSpeed")).map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }

            combine(directionFlow, speedFlow) { direction, speed ->
                DirectionAndSpeed(direction, speed)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            flow.collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")

                val errorCode = streamData.bearing.let { if(it < 0) it.toInt() else null }
                if (errorCode != null) {
                    emitter.updateView(getErrorWidget(glance, context, errorCode).remoteViews)
                    return@collect
                }

                val windDirection = streamData.bearing
                val windSpeed = streamData.speed

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(
                        baseBitmap,
                        windDirection.roundToInt(),
                        config.textSize,
                        windSpeed?.toInt()?.toString() ?: ""
                    )
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

    companion object {
        const val ERROR_NO_GPS = -1
        const val ERROR_NO_WEATHER_DATA = -2
        const val ERROR_APP_NOT_SET_UP = -3
    }
}