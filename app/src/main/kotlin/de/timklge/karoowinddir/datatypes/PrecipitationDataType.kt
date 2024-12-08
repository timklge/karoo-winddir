package de.timklge.karoowinddir.datatypes

import android.content.Context
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse

class PrecipitationDataType(context: Context) : BaseDataType(context, "precipitation"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.precipitation
    }
}