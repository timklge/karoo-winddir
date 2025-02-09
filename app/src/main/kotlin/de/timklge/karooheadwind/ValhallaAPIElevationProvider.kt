package de.timklge.karooheadwind

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.mapbox.geojson.LineString
import kotlinx.coroutines.flow.single

@Serializable
data class HeightResponse(
    @SerialName("encoded_polyline") val encodedPolyline: String,
    @SerialName("range_height") val rangeHeight: List<List<Double>>,
    val warnings: List<String>? = emptyList(),
)

@Serializable
data class HeightRequest(
    val range: Boolean,
    @SerialName("shape_format") val shapeFormat: String,
    @SerialName("encoded_polyline") val encodedPolyline: String,
    @SerialName("resample_distance") val resampleDistance: Double? = null,
    @SerialName("height_precision") val heightPrecision: Int = 0,
)


class ValhallaAPIElevationProvider(
    private val karooSystemService: KarooSystemService,
) {
    suspend fun requestValhallaElevations(polyline: LineString, interval: Float = 100.0f): SampledElevationData {
        return callbackFlow {
            val url = "https://valhalla1.openstreetmap.de/height"
            val request = HeightRequest(range = true, shapeFormat = "polyline5", encodedPolyline = polyline.toPolyline(5), heightPrecision = 1, resampleDistance = interval.toDouble())
            val requestBody = Json.encodeToString(HeightRequest.serializer(), request).encodeToByteArray()

            Log.d(KarooHeadwindExtension.TAG, "Http request to ${url}...")

            val listenerId = karooSystemService.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "POST",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG),
                    body = requestBody,
                ),
            ) { event: OnHttpResponse ->
                if (event.state is HttpResponseState.Complete){
                    val completeEvent = (event.state as HttpResponseState.Complete)

                    Log.d(KarooHeadwindExtension.TAG, "Http response event; size ${completeEvent.body?.size}")

                    try {
                        val responseBody = completeEvent.body?.decodeToString() ?: error("Failed to read response")

                        val response = try {
                            Json.decodeFromString(HeightResponse.serializer(), responseBody)
                        } catch (e: Exception) {
                            Log.e(KarooHeadwindExtension.TAG, "Failed to parse response: ${completeEvent.body}", e)
                            throw e
                        }

                        Log.d(KarooHeadwindExtension.TAG, "Parsed elevation data response with ${response.rangeHeight.size} points")

                        val resultElevations = FloatArray(response.rangeHeight.size) { index -> response.rangeHeight[index][1].toFloat() }
                        val result = SampledElevationData(interval, resultElevations)

                        trySendBlocking(result)
                    } catch(e: Throwable){
                        Log.e(KarooHeadwindExtension.TAG, "Failed to process response", e)
                    }

                    close()
                }
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }.single()
    }
}