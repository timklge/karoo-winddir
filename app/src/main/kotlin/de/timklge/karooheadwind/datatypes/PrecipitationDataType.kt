package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class PrecipitationDataType(context: Context) : BaseDataType(context, "precipitation"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.precipitation
    }
}