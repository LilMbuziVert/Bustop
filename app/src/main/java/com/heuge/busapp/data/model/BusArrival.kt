package com.heuge.busapp.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class StopEvent(
    @SerialName("departureTimePlanned") val departureTimePlanned: String? = null,
    @SerialName("departureTimeEstimated") val departureTimeEstimated: String? = null,
    @SerialName("transportation") val transportation: Transportation? = null
)

@Serializable
data class Transportation(
    @SerialName("number") val number: String? = null,
    @SerialName("destination") val destination: Destination? = null
)

@Serializable
data class Destination(
    @SerialName("name") val name: String? = null
)

@Serializable
data class ApiResponse(
    @SerialName("stopEvents") val stopEvents: List<StopEvent>? = null
)

data class BusArrival(
    val routeName: String,
    val destination: String,
    val scheduledTime: String,
    val realTimeTime: String
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun getFormattedTime(): String {
        return try {
            val time = LocalDateTime.parse(realTimeTime.substringBefore("+"))
            val now = LocalDateTime.now()
            val minutesUntil = java.time.Duration.between(now, time).toMinutes()

            when {
                minutesUntil <= 0 -> "Now"
                minutesUntil == 1L -> "1 min"
                minutesUntil < 60 -> "$minutesUntil mins"
                else -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
        } catch (_: Exception) {
            realTimeTime.substringBefore("+").substringAfter("T").substring(0, 5)
        }
    }
}