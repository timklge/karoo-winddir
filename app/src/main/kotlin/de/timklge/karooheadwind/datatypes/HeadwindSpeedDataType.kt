package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.cos

class HeadwindSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context) : DataTypeImpl("karoo-headwind", "headwindSpeed"){

    data class StreamData(val headingResponse: HeadingResponse, val weatherResponse: OpenMeteoCurrentWeatherResponse?, val settings: HeadwindSettings)

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings(karooSystem)) { (value, data), settings -> StreamData(value, data, settings) }
                .filter { it.weatherResponse != null }
                .collect { streamData ->
                    val windSpeed = streamData.weatherResponse?.current?.windSpeed ?: 0.0
                    val windDirection = (streamData.headingResponse as? HeadingResponse.Value)?.diff ?: 0.0
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed

                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to headwindSpeed))))
                }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }
}