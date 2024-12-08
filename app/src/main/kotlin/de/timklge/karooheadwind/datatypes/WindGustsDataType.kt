package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse

class WindGustsDataType(context: Context) : BaseDataType(context, "windGusts"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.windGusts
    }
}