package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class WindSpeedDataType(context: Context) : BaseDataType(context, "windSpeed"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.windSpeed
    }
}