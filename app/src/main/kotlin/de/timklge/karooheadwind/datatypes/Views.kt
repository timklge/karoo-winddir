package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.RemoteViewsCompositionResult
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.screens.HeadwindSettings

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun getErrorWidget(glance: GlanceRemoteViews, context: Context, settings: HeadwindSettings?, headingResponse: HeadingResponse?): RemoteViewsCompositionResult {
    return glance.compose(context, DpSize.Unspecified) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.Center) {
            val errorMessage = if (settings?.welcomeDialogAccepted == false) {
                "Headwind app not set up"
            } else if (headingResponse is HeadingResponse.NoGps){
                "No GPS signal"
            } else {
                "Weather data download failed"
            }

            Log.d(KarooHeadwindExtension.TAG, "Error widget: $errorMessage")

            Text(text = errorMessage,
                style = TextStyle(
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                    textAlign = TextAlign.Center,
                    color = ColorProvider(Color.Black, Color.White)
                )
            )
        }
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun getErrorWidget(glance: GlanceRemoteViews, context: Context, errorCode: Int): RemoteViewsCompositionResult {
    return glance.compose(context, DpSize.Unspecified) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.Center) {
            val errorMessage = when (errorCode) {
                HeadwindDirectionDataType.ERROR_APP_NOT_SET_UP -> {
                    "Headwind app not set up"
                }
                HeadwindDirectionDataType.ERROR_NO_GPS -> {
                    "No GPS signal"
                }
                else -> {
                    "Weather data download failed"
                }
            }

            Log.d(KarooHeadwindExtension.TAG, "Error widget: $errorMessage")

            Text(text = errorMessage,
                style = TextStyle(
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                    textAlign = TextAlign.Center,
                    color = ColorProvider(Color.Black, Color.White)
                )
            )
        }
    }
}