package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.WeatherInterpretation
import de.timklge.karooheadwind.screens.PrecipitationUnit
import de.timklge.karooheadwind.screens.TemperatureUnit
import de.timklge.karooheadwind.screens.WindUnit
import kotlin.math.ceil

fun getWeatherIcon(interpretation: WeatherInterpretation): Int {
    return when (interpretation){
        WeatherInterpretation.CLEAR -> R.drawable.bx_clear
        WeatherInterpretation.CLOUDY -> R.drawable.bx_cloud
        WeatherInterpretation.RAINY -> R.drawable.bx_cloud_rain
        WeatherInterpretation.SNOWY -> R.drawable.bx_cloud_snow
        WeatherInterpretation.DRIZZLE -> R.drawable.bx_cloud_drizzle
        WeatherInterpretation.THUNDERSTORM -> R.drawable.bx_cloud_lightning
        WeatherInterpretation.UNKNOWN -> R.drawable.question_mark_regular_240
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun Weather(baseBitmap: Bitmap, current: WeatherInterpretation, windBearing: Int, windSpeed: Int, windGusts: Int, windSpeedUnit: WindUnit,
            precipitation: Double, precipitationProbability: Int?, precipitationUnit: PrecipitationUnit,
            temperature: Int, temperatureUnit: TemperatureUnit, timeLabel: String? = null, rowAlignment: Alignment.Horizontal = Alignment.Horizontal.CenterHorizontally,
            dateLabel: String? = null) {

    val fontSize = 14f

    Column(modifier = GlanceModifier.fillMaxHeight().padding(1.dp).width(86.dp), horizontalAlignment = rowAlignment) {
        Row(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = rowAlignment, verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = GlanceModifier.defaultWeight(),
                provider = ImageProvider(getWeatherIcon(current)),
                contentDescription = "Current weather information",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )
        }

        if (dateLabel != null) {
            Text(
                text = dateLabel,
                modifier = GlanceModifier.padding(1.dp),
                style = TextStyle(color = ColorProvider(Color.Black, Color.White),
                    fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = rowAlignment) {
            if (timeLabel != null){
                Text(
                    text = timeLabel,
                    style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
                )

                Spacer(modifier = GlanceModifier.width(5.dp))
            }

            Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(R.drawable.thermometer),
                contentDescription = "Temperature",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )

            Text(
                text = "${temperature}${temperatureUnit.unitDisplay}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp), textAlign = TextAlign.Center)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = rowAlignment) {
            /* Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(R.drawable.water_regular),
                contentDescription = "Rain",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            ) */

            val precipitationProbabilityLabel = if (precipitationProbability != null) "${precipitationProbability}%," else ""
            Text(
                text = "${precipitationProbabilityLabel}${ceil(precipitation).toInt().coerceIn(0..9)}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
            )

            Spacer(modifier = GlanceModifier.width(5.dp))

            Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, windBearing + 180)),
                contentDescription = "Current wind direction",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )


            Text(
                text = "$windSpeed,${windGusts}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
            )
        }
    }
}