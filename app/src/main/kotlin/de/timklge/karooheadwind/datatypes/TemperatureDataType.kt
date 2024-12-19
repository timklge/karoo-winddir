package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class TemperatureDataType(context: Context) : BaseDataType(context, "temperature"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.temperature
    }
}