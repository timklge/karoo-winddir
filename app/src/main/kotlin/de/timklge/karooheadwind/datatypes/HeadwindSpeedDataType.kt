package de.timklge.karooheadwind.datatypes

import android.content.Context
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
import kotlinx.coroutines.launch
import kotlin.math.cos

class HeadwindSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context) : DataTypeImpl("karoo-headwind", "headwindSpeed"){

    data class StreamData(val value: Double, val data: OpenMeteoCurrentWeatherResponse, val settings: HeadwindSettings)

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings()) { (value, data), settings -> StreamData(value, data, settings) }
                .collect { streamData ->
                    val windSpeed = streamData.data.current.windSpeed
                    val windDirection = streamData.value
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed

                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to headwindSpeed))))
                }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }
}