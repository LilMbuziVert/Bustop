package com.heuge.busapp.ui.main

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.api.NSWBusService
import com.heuge.busapp.data.local.RecentStopsManager
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.ui.adapter.BusArrivalAdapter
import com.heuge.busapp.ui.adapter.RecentStopsAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var stopIdEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var busArrivalRecyclerView: RecyclerView
    private lateinit var recentStopsRecyclerView: RecyclerView
    private lateinit var errorTextView: TextView
    private lateinit var noDataTextView: TextView
    private lateinit var recentStopsSection: LinearLayout

    private lateinit var busService: NSWBusService
    private lateinit var adapter: BusArrivalAdapter

    private lateinit var recentStopsManager: RecentStopsManager
    private lateinit var recentStopsAdapter: RecentStopsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()

        busService = NSWBusService(this)

        recentStopsManager = RecentStopsManager(this)
        setupRecentStopsRecyclerView()
        loadRecentStops()
    }

    private fun initializeViews() {
        stopIdEditText = findViewById(R.id.stopIdEditText)
        searchButton = findViewById(R.id.searchButton)
        progressBar = findViewById(R.id.progressBar)
        busArrivalRecyclerView = findViewById(R.id.recyclerView)
        recentStopsRecyclerView = findViewById(R.id.recentStopsRecyclerView)
        errorTextView = findViewById(R.id.errorTextView)
        noDataTextView = findViewById(R.id.noDataTextView)
        recentStopsSection = findViewById(R.id.recentStopsSection)
    }

    private fun setupRecyclerView() {
        adapter = BusArrivalAdapter(emptyList())
        busArrivalRecyclerView.layoutManager = LinearLayoutManager(this)
        busArrivalRecyclerView.adapter = adapter
    }

    private fun setupRecentStopsRecyclerView() {
        recentStopsAdapter = RecentStopsAdapter { busStop ->
            // Handle stop selection
            loadBusArrivals(busStop.id)
        }

        recentStopsRecyclerView.adapter = recentStopsAdapter
        recentStopsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun loadRecentStops() {
        val recentStops = recentStopsManager.getRecentStops()
        recentStopsAdapter.updateStops(recentStops)

        // Show/hide recent stops section based on whether there are any
        recentStopsSection.visibility = if (recentStops.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadBusArrivals(stopId: String) {
        // Fill the search field when clicking a recent stop
        stopIdEditText.setText(stopId)

        // Add to recent stops when clicking a recent stop (moves it to top)
        recentStopsManager.addRecentStop(stopId)
        loadRecentStops() // Refresh the UI

        // Call the actual search method
        searchBusArrivals(stopId)
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val stopId = stopIdEditText.text.toString().trim()
            if (stopId.isNotEmpty()) {
                //recentStopsManager.addRecentStop(stopId)
                //loadRecentStops() // Refresh the UI
                searchBusArrivals(stopId)
            } else {
                showError("Please enter a stop ID")
            }
        }
    }

    private fun searchBusArrivals(stopId: String) {
        showLoading()

        // First, try to get the stop name
        busService.getStopInfo(
            stopId = stopId,
            callback = { stopName ->
                // Add to recent stops with the actual name
                runOnUiThread {
                    recentStopsManager.addRecentStop(stopId, stopName)
                    loadRecentStops()
                }

                // Then get the bus arrivals
                getBusArrivalsWithStopName(stopId, stopName)
            },
            errorCallback = { error ->
                // If stop info fails, still try to get arrivals but without name
                println("Stop info error: $error")
                getBusArrivalsWithStopName(stopId, null)
            }
        )
    }

    private fun getBusArrivalsWithStopName(stopId: String, stopName: String?) {
        lifecycleScope.launch {
            busService.getBusArrivals(
                stopId = stopId,
                callback = { arrivals ->
                    runOnUiThread {
                        hideLoading()
                        if (arrivals.isNotEmpty()) {
                            showResults(arrivals)
                            // Update recent stops with name if we got it
                            if (stopName != null) {
                                recentStopsManager.addRecentStop(stopId, stopName)
                                loadRecentStops()
                            }
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
        busArrivalRecyclerView.visibility = View.GONE
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
        busArrivalRecyclerView.visibility = View.VISIBLE
        errorTextView.visibility = View.GONE
        noDataTextView.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        busArrivalRecyclerView.visibility = View.GONE
        noDataTextView.visibility = View.GONE
    }

    private fun showNoData() {
        noDataTextView.visibility = View.VISIBLE
        busArrivalRecyclerView.visibility = View.GONE
        errorTextView.visibility = View.GONE
    }
}
