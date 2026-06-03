package com.heuge.busapp.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

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
    val realTimeTime: String,
    val delayMinutes: Long,
    var isPast: Boolean = false
) {
    val delayStatus: String
        get() = if (isPast) "" else when {
            delayMinutes > 0 -> "Late $delayMinutes min"
            delayMinutes < 0 -> "Early ${-delayMinutes} min"
            else -> "On time"
        }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getFormattedTime(): String {
        return try {
            //Parse as UTC then convert to Sydney time
            val utcTime = try {
                OffsetDateTime.parse(realTimeTime).toInstant()
            } catch (_: Exception) {
                if (realTimeTime.contains("Z")) {
                    Instant.parse(realTimeTime)
                } else {
                    Instant.parse("${realTimeTime}Z")
                }
            }

            val sydneyTime = utcTime.atZone(ZoneId.of("Australia/Sydney"))
            val now = ZonedDateTime.now(ZoneId.of("Australia/Sydney"))

            val minutesDifference = Duration.between(now, sydneyTime).toMinutes()

            if (isPast) {
                val absMinutes = abs(minutesDifference)
                if (absMinutes == 0L) "Now" else "$absMinutes mins ago"
            } else {
                when {
                    minutesDifference <= 0 -> "Now"
                    minutesDifference == 1L -> "1 min"
                    minutesDifference < 60 -> "$minutesDifference mins"
                    else -> sydneyTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                }
            }


        } catch (_: Exception) {
            realTimeTime.substringBefore("+").substringAfter("T").substring(0, 5)

        }
    }

}

@Serializable
data class BusStop(
    val id: String,             // Internal ID (10126815) - used for API calls
    val signId: String? = null, // Physical ID (2287141) - used for display
    val name: String? = null,
    val lastUsed: Long = System.currentTimeMillis(),
    val distance: Int? = null
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
    @SerialName("id") val id: String? = null,
    @SerialName("assignedStops") val assignedStops: List<AssignedStop>? = null,
)


@Serializable
data class AssignedStop(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("disassembledName") val disassembledName: String? = null,
    @SerialName("properties") val properties: StopProperties? = null,
    @SerialName("distance") val distance: Int? = null
)

@Serializable
data class StopProperties(
    @SerialName("stopId") val stopId: String? = null
)

@Serializable
data class AlertResponse(
    @SerialName("infos") val infos: AlertInfoContainer? = null
)

@Serializable
data class AlertInfoContainer(
    @SerialName("current") val current: List<AlertInfo>? = null
)

@Serializable
data class AlertInfo(
    @SerialName("priority") val priority: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("timestamps") val timestamps: AlertTimestamps? = null,
    @SerialName("affected") val affected: AffectedDetails? = null
)

@Serializable
data class AlertTimestamps(
    @SerialName("availability") val availability: Availability? = null
)
@Serializable
data class Availability(
    @SerialName("from") val from: String? = null, // ISO-8601 string
    @SerialName("to") val to: String? = null
)
@Serializable
data class AffectedDetails(
    @SerialName("stops") val stops: List<AffectedStop>? = null,
    @SerialName("lines") val lines: List<AffectedLine>? = null
)

@Serializable
data class AffectedStop(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null
)
@Serializable
data class AffectedLine(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null
)
data class TravelAlert(
    val title: String,
    val content: String,
    val priority: String? = null
)

data class BusStopGroup(
    val stops: List<BusStop>
) {
    val hasSecondStop: Boolean get() = stops.size >= 2
    val hasThirdStop: Boolean get() = stops.size >= 3
}
