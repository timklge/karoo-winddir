package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class SurfacePressureDataType(context: Context) : BaseDataType(context, "surfacePressure"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.surfacePressure
    }
}