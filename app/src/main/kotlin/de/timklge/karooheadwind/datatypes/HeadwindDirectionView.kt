package de.timklge.karooheadwind.datatypes

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.R
import kotlin.math.roundToInt

fun getArrowResourceByBearing(bearing: Int): Int {
    val oclock = ((bearing % 360) / 30.0).roundToInt()

    return when (oclock){
        0 -> R.drawable.arrow_0
        1 -> R.drawable.arrow_1
        2 -> R.drawable.arrow_2
        3 -> R.drawable.arrow_3
        4 -> R.drawable.arrow_4
        5 -> R.drawable.arrow_5
        6 -> R.drawable.arrow_6
        7 -> R.drawable.arrow_7
        8 -> R.drawable.arrow_8
        9 -> R.drawable.arrow_9
        10 -> R.drawable.arrow_10
        11 -> R.drawable.arrow_11
        12 -> R.drawable.arrow_0
        else -> error("Bearing $bearing out of range")
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun HeadwindDirection(bearing: Int, fontSize: Int, overlayText: String? = null) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(5.dp),
        contentAlignment = Alignment(
            vertical = Alignment.Vertical.CenterVertically,
            horizontal = Alignment.Horizontal.CenterHorizontally,
        ),
    ) {
        Image(
                modifier = GlanceModifier.fillMaxSize(),
                provider = ImageProvider(getArrowResourceByBearing(bearing)),
                contentDescription = "Relative wind direction indicator",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
        )

        overlayText?.let {
            Text(
                overlayText,
                style = TextStyle(ColorProvider(Color.White, Color.White), fontSize = TextUnit(fontSize.toFloat()*0.7f, TextUnitType.Sp)),
                modifier = GlanceModifier.background(Color(0f, 0f, 0f, 0.5f)).padding(5.dp)
            )
        }
    }
}