package com.heuge.busapp.data.local

import com.heuge.busapp.data.model.BusStop
import android.location.Location

private const val CACHE_EXPIRATION_MS = 2 * 60 * 1000 // 2 minutes

class NearestStopsManager {

    private var cachedNearbyStops: List<BusStop> = emptyList()
    private var lastNearbyFetchTime: Long = 0


    /**
     * Returns the cached stops if they are still fresh, otherwise an empty list.
     */
    fun getCachedStops(): List<BusStop> {
        return if (isCacheValid()) cachedNearbyStops else emptyList()
    }

    /**
     * Checks if the cache is still valid based on the expiration time.
     */
    fun isCacheValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        return cachedNearbyStops.isNotEmpty() && (currentTime - lastNearbyFetchTime) < CACHE_EXPIRATION_MS
    }

    /**
     * Updates the cache with new stops.
     *
     */
    fun updateStops(stops: List<BusStop>) {
        // sort bus stops before caching them
        cachedNearbyStops = stops.sortedBy{ it.distance ?: Int.MAX_VALUE }
        lastNearbyFetchTime = System.currentTimeMillis()
    }

    fun clearCache() {
        cachedNearbyStops = emptyList()
        lastNearbyFetchTime = 0
    }
}