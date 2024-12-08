package de.timklge.karoowinddir.datatypes

import android.content.Context
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse

class SurfacePressureDataType(context: Context) : BaseDataType(context, "surfacePressure"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.surfacePressure
    }
}