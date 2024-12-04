package de.timklge.karoowinddir.datatypes

import android.content.Context
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse

class WindSpeedDataType(context: Context) : WeatherDataType(context, "windSpeed"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.windSpeed
    }
}