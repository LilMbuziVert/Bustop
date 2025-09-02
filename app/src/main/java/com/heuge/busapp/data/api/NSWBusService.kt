package com.heuge.busapp.data.api

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.heuge.busapp.R
import com.heuge.busapp.data.model.ApiResponse
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.data.model.StopInfoResponse
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.IOException
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class NSWBusService (context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val apiKey: String = context.getString(R.string.nsw_transport_api_key).also { key ->
        if (key == "default_key") {
            throw IllegalStateException("API key not configured. Please add NSW_TRANSPORT_API_KEY to local.properties")
        }
    }

    fun getBusArrivals(
        stopId: String,
        callback: (List<BusArrival>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        val url = "https://api.transport.nsw.gov.au/v1/tp/departure_mon?outputFormat=rapidJSON&coordOutputFormat=EPSG%3A4326&mode=direct&type_dm=stop&name_dm=$stopId&departureMonitorMacro=true&TfNSWDM=true&version=10.2.1.42"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "apikey $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorCallback("Network error: ${e.message}")
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        println("wow")
                        println("swag:$responseBody") //debug

                        try {
                            val apiResponse = json.decodeFromString<ApiResponse>(responseBody)
                            val arrivals = apiResponse.stopEvents
                                ?.mapNotNull { event ->
                                    val routeName = event.transportation?.number ?: return@mapNotNull null
                                    val destination = event.transportation.destination?.name ?: "Unknown"
                                    val scheduledTime = event.departureTimePlanned ?: return@mapNotNull null
                                    val realTimeTime = event.departureTimeEstimated ?: scheduledTime

                                    val planned = OffsetDateTime.parse(scheduledTime,
                                        DateTimeFormatter.ISO_DATE_TIME)
                                    val estimated = OffsetDateTime.parse(realTimeTime,
                                        DateTimeFormatter.ISO_DATE_TIME)

                                    val delayMinutes = Duration.between(planned, estimated).toMinutes()

                                    BusArrival(
                                        routeName = routeName,
                                        destination = destination,
                                        scheduledTime = scheduledTime,
                                        realTimeTime = realTimeTime,
                                        delayMinutes = delayMinutes
                                    )
                                }
                                ?.take(5) ?: emptyList()
                            callback(arrivals)
                        } catch (e: Exception) {
                            println("Parsing error: ${e.message}")
                            println("Response body: $responseBody")
                            errorCallback("Parsing error: ${e.message}")
                        }
                    } else {
                        errorCallback("API Error: ${response.code}")
                    }
                } catch (e: Exception) {
                    errorCallback("Parsing error: ${e.message}")
                }
            }
        })
    }

    fun getStopInfo(
        stopId: String,
        callback: (String?) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        // Stop finder endpoint
        val url = "https://api.transport.nsw.gov.au/v1/tp/stop_finder?outputFormat=rapidJSON&type_sf=stop&name_sf=$stopId&coordOutputFormat=EPSG%3A4326"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "apikey $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Stop info API failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""

                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val stopInfo = json.decodeFromString<StopInfoResponse>(responseBody)

                            // Try different possible fields for the stop name
                            val stopName = stopInfo.locations?.firstOrNull()?.let { location ->
                                location.name ?: location.disassembledName ?: location.desc
                            }
                            callback(stopName)
                        } catch (e: Exception) {
                            println("Stop info parsing error: ${e.message}")
                            println("Response was: $responseBody")
                            callback(null)
                        }
                    } else {
                        println("Stop info API returned: ${response.code}")
                        callback(null)
                    }
                } catch (e: Exception) {
                    println("Stop info error: ${e.message}")
                    callback(null)
                }
            }
        })
    }
}