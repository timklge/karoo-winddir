package de.timklge.karoowinddir.datatypes

import android.content.Context
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse

class WindGustsDataType(context: Context) : WeatherDataType(context, "windGusts"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.windGusts
    }
}