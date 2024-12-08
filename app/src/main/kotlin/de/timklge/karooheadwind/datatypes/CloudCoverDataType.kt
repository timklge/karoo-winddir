package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class CloudCoverDataType(context: Context) : BaseDataType(context, "cloudCover"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.cloudCover.toDouble()
    }
}