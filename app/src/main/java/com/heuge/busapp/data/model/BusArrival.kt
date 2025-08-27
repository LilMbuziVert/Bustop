package com.heuge.busapp.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.*
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
            //Parse as UTC then convert to Sydney time
            val utcTime = if (realTimeTime.contains("+") || realTimeTime.contains("Z")) {
                Instant.parse(realTimeTime)
            } else {
                Instant.parse("${realTimeTime}Z")
            }

            val sydneyTime = utcTime.atZone(ZoneId.of("Australia/Sydney"))
            val now = ZonedDateTime.now(ZoneId.of("Australia/Sydney"))

            val minutesUntil = Duration.between(now, sydneyTime).toMinutes()

            when{
                minutesUntil <=0 -> "Now"
                minutesUntil == 1L -> "1 min"
                minutesUntil < 60 -> "$minutesUntil mins"
                else -> sydneyTime.format(DateTimeFormatter.ofPattern("HH:min"))
            }


        } catch (_: Exception) {
            realTimeTime.substringBefore("+").substringAfter("T").substring(0, 5)

        }
    }

}

@Serializable
data class BusStop(
    val id: String,
    val name: String? = null,
    val lastUsed: Long = System.currentTimeMillis()
)

@Serializable
data class StopInfoResponse(
    @SerialName("locations") val locations: List<StopLocation>? = null
)

@Serializable
data class StopLocation(
    @SerialName("name")val name: String? = null,
    @SerialName("disassembledName") val disassembledName: String? = null,
    @SerialName("desc") val desc: String? = null,
    @SerialName("id") val id: String? = null
)

data class BusStopGroup(
    val stops: List<BusStop>
) {
    val hasSecondStop: Boolean get() = stops.size >= 2
    val hasThirdStop: Boolean get() = stops.size >= 3
}
