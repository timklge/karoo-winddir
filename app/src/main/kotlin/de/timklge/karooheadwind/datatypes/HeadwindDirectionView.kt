package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.KarooHeadwindExtension
import kotlin.math.roundToInt

data class BitmapWithBearing(val bitmap: Bitmap, val bearing: Int)

val bitmapsByBearing = mutableMapOf<BitmapWithBearing, Bitmap>()

fun getArrowBitmapByBearing(baseBitmap: Bitmap, bearing: Int): Bitmap {
    synchronized(bitmapsByBearing) {
        val bearingRounded = (((bearing + 360) / 15.0).roundToInt() * 15) % 360

        val bitmapWithBearing = BitmapWithBearing(baseBitmap, bearingRounded)
        val storedBitmap = bitmapsByBearing[bitmapWithBearing]
        if (storedBitmap != null) return storedBitmap

        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
//            strokeWidth = 15f
            isAntiAlias = true
        }

        canvas.save()
        canvas.scale((bitmap.width / baseBitmap.width.toFloat()), (bitmap.height / baseBitmap.height.toFloat()), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
        Log.d(KarooHeadwindExtension.TAG, "Drawing arrow at $bearingRounded")
        canvas.rotate(bearingRounded.toFloat(), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
        canvas.drawBitmap(baseBitmap, ((bitmap.width - baseBitmap.width) / 2).toFloat(), ((bitmap.height - baseBitmap.height) / 2).toFloat(), paint)
        canvas.restore()

        bitmapsByBearing[bitmapWithBearing] = bitmap

        return bitmap
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
fun HeadwindDirection(baseBitmap: Bitmap, bearing: Int, fontSize: Int,
                      overlayText: String, overlaySubText: String? = null,
                      dayColor: Color = Color.Black, nightColor: Color = Color.White,
                      viewSize: Pair<Int, Int>) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(5.dp),
        contentAlignment = Alignment(
            vertical = Alignment.Vertical.CenterVertically,
            horizontal = Alignment.Horizontal.CenterHorizontally,
        ),
    ) {
        if (overlayText.isNotEmpty()){
            if (overlaySubText == null){
                Image(
                    modifier = GlanceModifier.fillMaxSize(),
                    provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
                    contentDescription = "Relative wind direction indicator",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(ColorProvider(dayColor, nightColor))
                )

                Text(
                    overlayText,
                    maxLines = 1,
                    style = TextStyle(ColorProvider(dayColor, nightColor), fontSize = (0.6 * fontSize).sp, fontFamily = FontFamily.Monospace),
                    modifier = GlanceModifier.background(Color(1f, 1f, 1f, 0.4f), Color(0f, 0f, 0f, 0.4f)).padding(1.dp)
                )
            } else {
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = GlanceModifier.size(40.dp)) {
                            Image(
                                provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
                                contentDescription = "Relative wind direction indicator",
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(ColorProvider(dayColor, nightColor))
                            )
                        }
                    }

                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Text(
                            overlayText,
                            maxLines = 1,
                            style = TextStyle(ColorProvider(dayColor, nightColor), fontSize = (0.65 * fontSize).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.background(Color(1f, 1f, 1f, 0.4f), Color(0f, 0f, 0f, 0.4f)).padding(1.dp)
                        )

                        Row(){
                            Text(
                                overlaySubText,
                                maxLines = 1,
                                style = TextStyle(ColorProvider(dayColor, nightColor), fontSize = (0.4 * fontSize).sp, fontFamily = FontFamily.Monospace),
                                modifier = GlanceModifier.background(Color(1f, 1f, 1f, 0.4f), Color(0f, 0f, 0f, 0.4f)).padding(1.dp)
                            )
                        }
                    }

                }
            }
        }
    }
}