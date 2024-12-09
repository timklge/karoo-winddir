package de.timklge.karooheadwind.datatypes

import android.R
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import kotlin.math.roundToInt


val bitmapsByBearing = mutableMapOf<Int, Bitmap>()

fun getArrowBitmapByBearing(bearing: Int): Bitmap {
    synchronized(bitmapsByBearing) {
        val bearingRounded = (((bearing + 360) / 5.0).roundToInt() * 5) % 360

        val storedBitmap = bitmapsByBearing[bearingRounded]
        if (storedBitmap != null) return storedBitmap

        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 15f
            isAntiAlias = true
        }

        val path = Path().apply {
            moveTo(64f, 0f) // Top point of the arrow
            lineTo(128f, 128f) // Bottom right point of the arrow
            lineTo(64f, 96f) // Middle bottom point of the arrow
            lineTo(0f, 128f) // Bottom left point of the arrow
            close() // Close the path to form the arrow shape
        }

        canvas.save()
        canvas.rotate(bearing.toFloat(), 64f, 64f) // Rotate the canvas based on the bearing
        canvas.scale(0.75f, 0.75f, 64f, 64f) // Scale the arrow down to fit the canvas
        canvas.drawPath(path, paint)
        canvas.restore()

        bitmapsByBearing[bearingRounded] = bitmap

        return bitmap
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
                provider = ImageProvider(getArrowBitmapByBearing(bearing)),
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