package com.heuge.busapp.ui.main

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
        } catch (e: Exception) {
            realTimeTime.substringBefore("+").substringAfter("T").substring(0, 5)
        }
    }
}

class NSWBusService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // Replace with your actual API key from https://opendata.transport.nsw.gov.au/
    private val apiKey = "poo"

    suspend fun getBusArrivals(stopId: String, callback: (List<BusArrival>) -> Unit, errorCallback: (String) -> Unit) {
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

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        println("API Response: $responseBody") // Debug logging

                        try {
                            val apiResponse = json.decodeFromString<ApiResponse>(responseBody)
                            val arrivals = apiResponse.stopEvents
                                ?.mapNotNull { event ->
                                    val routeName = event.transportation?.number ?: return@mapNotNull null
                                    val destination = event.transportation?.destination?.name ?: "Unknown"
                                    val scheduledTime = event.departureTimePlanned ?: return@mapNotNull null
                                    val realTimeTime = event.departureTimeEstimated ?: scheduledTime

                                    BusArrival(
                                        routeName = routeName,
                                        destination = destination,
                                        scheduledTime = scheduledTime,
                                        realTimeTime = realTimeTime
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
}

class BusArrivalAdapter(private var arrivals: List<BusArrival>) :
    RecyclerView.Adapter<BusArrivalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val routeNumber: TextView = view.findViewById(R.id.routeNumber)
        val destination: TextView = view.findViewById(R.id.destination)
        val arrivalTime: TextView = view.findViewById(R.id.arrivalTime)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_arrival, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val arrival = arrivals[position]
        holder.routeNumber.text = arrival.routeName
        holder.destination.text = "To ${arrival.destination}"
        holder.arrivalTime.text = arrival.getFormattedTime()
    }

    override fun getItemCount() = arrivals.size

    fun updateArrivals(newArrivals: List<BusArrival>) {
        arrivals = newArrivals
        notifyDataSetChanged()
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var stopIdEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var errorTextView: TextView
    private lateinit var noDataTextView: TextView

    private lateinit var busService: NSWBusService
    private lateinit var adapter: BusArrivalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()

        busService = NSWBusService()
    }

    private fun initializeViews() {
        stopIdEditText = findViewById(R.id.stopIdEditText)
        searchButton = findViewById(R.id.searchButton)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        errorTextView = findViewById(R.id.errorTextView)
        noDataTextView = findViewById(R.id.noDataTextView)
    }

    private fun setupRecyclerView() {
        adapter = BusArrivalAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val stopId = stopIdEditText.text.toString().trim()
            if (stopId.isNotEmpty()) {
                searchBusArrivals(stopId)
            } else {
                showError("Please enter a stop ID")
            }
        }
    }

    private fun searchBusArrivals(stopId: String) {
        showLoading()

        lifecycleScope.launch {
            busService.getBusArrivals(
                stopId = stopId,
                callback = { arrivals ->
                    runOnUiThread {
                        hideLoading()
                        if (arrivals.isNotEmpty()) {
                            showResults(arrivals)
                        } else {
                            showNoData()
                        }
                    }
                },
                errorCallback = { error ->
                    runOnUiThread {
                        hideLoading()
                        showError(error)
                    }
                }
            )
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        errorTextView.visibility = View.GONE
        noDataTextView.visibility = View.GONE
        searchButton.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        searchButton.isEnabled = true
    }

    private fun showResults(arrivals: List<BusArrival>) {
        adapter.updateArrivals(arrivals)
        recyclerView.visibility = View.VISIBLE
        errorTextView.visibility = View.GONE
        noDataTextView.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        noDataTextView.visibility = View.GONE
    }

    private fun showNoData() {
        noDataTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        errorTextView.visibility = View.GONE
    }
}
