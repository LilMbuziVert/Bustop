package com.heuge.busapp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.heuge.busapp.data.model.BusStop
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class RecentStopsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("recent_stops", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val maxRecentStops = 10

    fun addRecentStop(stopId: String, stopName: String? = null){
        val recentStops = getRecentStops().toMutableList()

        //Remove if it already exists
        recentStops.removeAll {it.id == stopId}

        //Add to beginning
        recentStops.add(0, BusStop(stopId, stopName, System.currentTimeMillis()))

        //take only specified max items
        val trimmedStops = recentStops.take(maxRecentStops)

        //Save to preferences
        val stopsJson = json.encodeToString(trimmedStops)
        prefs.edit { putString("stops", stopsJson) }
    }

    fun getRecentStops(): List<BusStop>{
        val stopsJson = prefs.getString("stops", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<BusStop>>(stopsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearRecentStops(){
        prefs.edit { remove("stops") }
    }

    fun removeStop(stopId: String) {
        val recentStops = getRecentStops().toMutableList()
        recentStops.removeAll { it.id == stopId }

        val stopsJson = json.encodeToString(recentStops)
        prefs.edit { putString("stops", stopsJson) }
    }
}