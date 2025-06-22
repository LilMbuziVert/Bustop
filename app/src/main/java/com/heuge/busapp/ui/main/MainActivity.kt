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
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.ui.adapter.BusArrivalAdapter
import kotlinx.coroutines.launch

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

        busService = NSWBusService(this)
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
