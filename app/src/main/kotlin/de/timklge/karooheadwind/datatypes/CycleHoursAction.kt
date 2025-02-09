package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.saveWidgetSettings
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamWidgetSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class CycleHoursAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooHeadwindExtension.TAG, "Cycling hours")

        val currentSettings = context.streamWidgetSettings().first()
        val data = context.streamCurrentWeatherData().firstOrNull()?.firstOrNull()

        var hourOffset = currentSettings.currentForecastHourOffset + 3
        if (data == null || hourOffset >= ((data.forecastData?.weatherCode?.size) ?: 0)) {
            hourOffset = 0
        }

        val newSettings = currentSettings.copy(currentForecastHourOffset = hourOffset)
        saveWidgetSettings(context, newSettings)
    }
}