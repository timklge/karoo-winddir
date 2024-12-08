package de.timklge.karoowinddir.datatypes

import android.content.Context
import de.timklge.karoowinddir.OpenMeteoCurrentWeatherResponse

class CloudCoverDataType(context: Context) : BaseDataType(context, "cloudCover"){
    override fun getValue(data: OpenMeteoCurrentWeatherResponse): Double {
        return data.current.cloudCover.toDouble()
    }
}