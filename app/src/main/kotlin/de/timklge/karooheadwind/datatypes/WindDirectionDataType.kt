package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WindDirectionDataType(val karooSystem: KarooSystemService, context: Context) : BaseDataType(context, "windDirection"){
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.windDirection
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = true))
            awaitCancellation()
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.streamDataFlow(dataTypeId)
                .onCompletion {
                    val result = glance.compose(context, DpSize.Unspecified) { }
                    emitter.updateView(result.remoteViews)
                }
                .collect {
                val windBearing = (it as? StreamState.Streaming)?.dataPoint?.singleValue?.toInt() ?: 0
                val windCardinalDirection = ((windBearing % 360) / 45.0).roundToInt() % 8
                val text = when(windCardinalDirection){
                    0 -> "N"
                    1 -> "NE"
                    2 -> "E"
                    3 -> "SE"
                    4 -> "S"
                    5 -> "SW"
                    6 -> "W"
                    7 -> "NW"
                    else -> "N/A"
                }
                Log.d( KarooHeadwindExtension.TAG,"Updating wind direction view")
                val result = glance.compose(context, DpSize.Unspecified) {
                    Box(modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment(
                            vertical = Alignment.Vertical.Top,
                            horizontal = Alignment.Horizontal.End,
                        )) {
                        Text(text, style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(
                            config.textSize.toFloat(), TextUnitType.Sp) /* FIXME: Read data field alignment config, karoo-ext #10 */))
                    }
                }
                emitter.updateView(result.remoteViews)
            }
        }

        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
        }
    }
}