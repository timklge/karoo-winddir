package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class RelativeHumidityDataType(context: Context) : BaseDataType(context, "relativeHumidity"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.relativeHumidity.toDouble()
    }
}