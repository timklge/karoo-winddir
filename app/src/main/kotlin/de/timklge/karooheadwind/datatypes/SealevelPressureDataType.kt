package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class SealevelPressureDataType(context: Context) : BaseDataType(context, "sealevelPressure"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.sealevelPressure
    }
}