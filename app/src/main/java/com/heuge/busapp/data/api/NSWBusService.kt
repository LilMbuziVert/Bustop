package com.heuge.busapp.data.api

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.heuge.busapp.R
import com.heuge.busapp.data.model.ApiResponse
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.data.model.BusStop
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
        callback: (String?, String?) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        val url = "https://api.transport.nsw.gov.au/v1/tp/stop_finder?outputFormat=rapidJSON&type_sf=stop&name_sf=$stopId&coordOutputFormat=EPSG%3A4326"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "apikey $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        try {
                            val stopInfo = json.decodeFromString<StopInfoResponse>(responseBody)

                            // We look at the first location found
                            val location = stopInfo.locations?.firstOrNull()

                            val stopName = stopInfo.locations?.firstOrNull()?.let { location ->
                                location.name ?: location.disassembledName ?: location.desc
                            }

                            // Extract the signId (id) from the location or its nested assignedStops
                            // Usually, for a stop_finder on a specific ID, the top level ID is the G-number
                            val rawSignId = location?.id ?: location?.assignedStops?.firstOrNull()?.id
                            val signId = rawSignId?.replace("G", "") // Clean the "G" prefix

                            callback(stopName, signId)
                        } catch (e: Exception) {
                            callback(null, null)
                        }
                    } else {
                        callback(null, null)
                    }
                } catch (e: Exception) {
                    callback(null, null)
                }
            }
        })
    }

    fun getNearbyStops(
        lat: Double,
        lon: Double,
        callback: (List<BusStop>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        // 1. Limit decimals to 6 (standard for GPS) to prevent API rejection
        val formattedLat = "%.6f".format(lat)
        val formattedLon = "%.6f".format(lon)

        // 2. Updated URL with proximity and type flags
        val url = "https://api.transport.nsw.gov.au/v1/tp/stop_finder?" +
                "outputFormat=rapidJSON" +
                "&type_sf=coord" + // Changed from stop to coord
                "&name_sf=$formattedLon:$formattedLat:EPSG%3A4326" +
                "&coordOutputFormat=EPSG%3A4326" +
                "&anyType_sf=stop" + // We want stops near these coords
                "&limit=10"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "apikey $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorCallback("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        println("API DEBUG: $responseBody")
                        try {
                            val stopInfo = json.decodeFromString<StopInfoResponse>(responseBody)
                            val stops = stopInfo.locations
                                ?.firstOrNull() // Get the primary location object
                                ?.assignedStops // Access its list of nearby stops
                                ?.mapNotNull { assignedStop ->
                                    // The actual stop ID is inside the 'properties' object
                                    val internalId = assignedStop.properties?.stopId ?: return@mapNotNull null
                                    val globalId = assignedStop.id?.replace("G", "") ?: "" // "G2287141" -> "2287141"
                                    val name = assignedStop.name ?: assignedStop.disassembledName ?: "Stop $internalId"
                                    BusStop(
                                        id = internalId,
                                        signId = globalId,
                                        name = name
                                    )
                                }?.take(6) ?: emptyList() // Take the first 6 stops
                            callback(stops)
                        } catch (e: Exception) {
                            errorCallback("Parsing error: ${e.message}")
                        }
                    } else {
                        errorCallback("API Error: ${response.code}")
                    }
                } catch (e: Exception) {
                    errorCallback("Error: ${e.message}")
                }
            }
        })
    }
}