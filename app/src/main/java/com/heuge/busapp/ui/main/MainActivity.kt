package com.heuge.busapp.ui.main

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.textfield.TextInputLayout
import com.heuge.busapp.R
import com.heuge.busapp.data.api.NSWBusService
import com.heuge.busapp.data.local.RecentStopsManager
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.ui.adapter.BusArrivalAdapter
import com.heuge.busapp.ui.adapter.BusNumberAdapter
import com.heuge.busapp.ui.adapter.RecentStopsAdapter
import kotlinx.coroutines.launch
import androidx.core.view.isGone
import com.heuge.busapp.data.model.BusStop

class MainActivity : AppCompatActivity() {
    private lateinit var stopIdEditText: EditText
    private lateinit var stopIdTextInputLayout: TextInputLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var busArrivalRecyclerView: RecyclerView
    private lateinit var recentStopsRecyclerView: RecyclerView
    private lateinit var errorTextView: TextView
    private lateinit var noDataTextView: TextView
    private lateinit var recentStopsSection: LinearLayout

    private lateinit var recentStopsButton: TextView
    private lateinit var nearestStopsButton: TextView

    private lateinit var busService: NSWBusService
    private lateinit var adapter: BusArrivalAdapter
    private lateinit var busNumberAdapter: BusNumberAdapter

    private lateinit var recentStopsManager: RecentStopsManager
    private lateinit var recentStopsAdapter: RecentStopsAdapter

    private lateinit var indicatorContainer: LinearLayout
    private val indicators = mutableListOf<View>()

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var currentStopId: String? = null
    // Stores all buses available at the current stop
    private var availableBusNumbers: List<String> = emptyList()

    // Stores all arrivals so we can filter without losing data
    private var allArrivals: List<BusArrival> = emptyList()

    private var isNearestStopsExpanded = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var cachedNearbyStops: List<BusStop> = emptyList()
    private var lastNearbyFetchTime: Long = 0
    private val CACHE_EXPIRATION_MS = 2 * 60 * 1000 // 2 minutes

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchNearbyStops()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            // Revert expansion if permission denied
            if (isNearestStopsExpanded) toggleNearestStops()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Apply e-ink optimizations
        applyEInkOptimizations()
        setupTouchOutsideToClearFocus()

        // Ensure there is enough spacing between title and status bar
        val rootLayout = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalPadding = dpToPx(16)
            view.updatePadding(
                left = horizontalPadding,
                top = systemBarsInsets.top + dpToPx(24), // extra top margin
                right = horizontalPadding
            )
            insets
        }

        initializeViews()
        setupRecyclerView()
        setupClickListeners()


        busService = NSWBusService(this)

        recentStopsManager = RecentStopsManager(this)
        setupRecentStopsRecyclerView()
        setupBusNumberRecyclerView()
        setupSwipeRefresh()
        loadRecentStops()
    }



    private fun setupTouchOutsideToClearFocus() {
        val rootLayout = findViewById<View>(R.id.root_layout)
        rootLayout.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Hide any visible delete buttons when clicking outside
                recentStopsAdapter.hideDeleteButtons()
                
                currentFocus?.let { view ->
                    view.clearFocus()
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
                // Call performClick for accessibility compliance
                v.performClick()
            }
            false
        }
    }

    private fun applyEInkOptimizations() {
        // Remove window animations for instant updates
        window.setWindowAnimations(0)

        // Disable hardware acceleration for better e-ink compatibility
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // Read android:windowLightStatusBar from the current theme
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowLightStatusBar))
        val lightStatusBar = typedArray.getBoolean(0, false)
        typedArray.recycle()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (lightStatusBar) {
                controller?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                controller?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightStatusBar) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                0
            }
        }
    }



    // Custom animation replacements for e-ink
    private fun eInkFadeIn(view: View) {
        if (view.isVisible && view.alpha == 1f) return
        
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
        }
        
        view.animate()
            .alpha(1f)
            .setDuration(200) // Slower for e-ink
            .setInterpolator(LinearInterpolator()) // Linear for e-ink
            .start()
    }

    private fun eInkFadeOut(view: View) {
        if (view.isGone) return
        
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .withEndAction { 
                view.visibility = View.GONE 
                view.alpha = 1f // Reset alpha for next time
            }
            .start()
    }

    // Replace smooth scrolling with instant updates
    private fun setupEInkRecyclerView() {
        recentStopsRecyclerView.itemAnimator = null // Remove animations
        recentStopsRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER


        // Clear any previous fling listener to avoid crash
        recentStopsRecyclerView.onFlingListener = null


        // Custom snap behavior for e-ink
        val snapHelper = object : PagerSnapHelper() {
            override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
                val view = super.findSnapView(layoutManager)
                // Instant snap without smooth scrolling
                return view
            }
        }
        snapHelper.attachToRecyclerView(recentStopsRecyclerView)
    }

    private fun setupBusNumberRecyclerView() {
        val recycler = findViewById<RecyclerView>(R.id.busNumberRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        busNumberAdapter = BusNumberAdapter(emptyList()) { selectedBus ->
            filterByBusNumber(selectedBus)
        }
        recycler.adapter = busNumberAdapter
    }

    // call this after arrivals are loaded
    private fun updateBusNumbers() {
        busNumberAdapter.updateData(availableBusNumbers)
    }


    private fun animateProgressBar() {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
        animator.duration = 1000
        animator.interpolator = LinearInterpolator()
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.RESTART
        animator.start()
    }


    private fun initializeViews() {
        stopIdEditText = findViewById(R.id.stopIdEditText)
        stopIdTextInputLayout = findViewById(R.id.stopIdTextInputLayout)
        progressBar = findViewById(R.id.progressBar)
        busArrivalRecyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)
        recentStopsRecyclerView = findViewById(R.id.recentStopsRecyclerView)
        errorTextView = findViewById(R.id.errorTextView)
        noDataTextView = findViewById(R.id.noDataTextView)
        recentStopsSection = findViewById(R.id.recentStopsSection)
        indicatorContainer = findViewById(R.id.indicatorContainer)
        recentStopsButton = findViewById(R.id.recentStopsButton)
        nearestStopsButton = findViewById(R.id.nearestStopsButton)
    }

    private fun setupRecyclerView() {
        adapter = BusArrivalAdapter(emptyList())
        busArrivalRecyclerView.layoutManager = LinearLayoutManager(this)
        busArrivalRecyclerView.adapter = adapter
    }

    private fun setupRecentStopsRecyclerView() {
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recentStopsRecyclerView)

        recentStopsAdapter = RecentStopsAdapter(
            onStopClick = { busStop ->
                loadBusArrivals(stopId = busStop.id, signId = busStop.signId)
            },
            onDeleteClick = { busStop ->
                recentStopsManager.removeStop(busStop.id)
                loadRecentStops()
            }
        )

        // Listen for scroll changes to update indicator
        recentStopsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val snapView = snapHelper.findSnapView(layoutManager)
                    val snapPosition = snapView?.let { layoutManager.getPosition(it) } ?: 0
                    if (snapPosition >= 0 && snapPosition < indicators.size) {
                        updateIndicator(snapPosition)
                    }
                }
            }
        })

        recentStopsRecyclerView.adapter = recentStopsAdapter
        recentStopsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        //e-ink stuff
        setupEInkRecyclerView()
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshCurrentStopData()
        }

        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.black,
            android.R.color.darker_gray
        )
    }

    private fun setupCarouselIndicator(itemCount: Int) {
        indicatorContainer.removeAllViews()
        indicators.clear()

        if (itemCount <= 1) {
            indicatorContainer.visibility = View.GONE
            return
        }

        indicatorContainer.visibility = View.VISIBLE

        for (i in 0 until itemCount) {
            val indicator = View(this)
            val params = LinearLayout.LayoutParams(
                dpToPx(8), dpToPx(8)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            indicator.layoutParams = params
            indicator.background = ContextCompat.getDrawable(
                this,
                if (i == 0) R.drawable.indicator_active else R.drawable.indicator_inactive
            )

            indicators.add(indicator)
            indicatorContainer.addView(indicator)
        }
    }

    private fun updateIndicator(position: Int) {
        indicators.forEachIndexed { index, indicator ->
            indicator.background = ContextCompat.getDrawable(
                this,
                if (index == position) R.drawable.indicator_active else R.drawable.indicator_inactive
            )
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }


    private fun loadRecentStops() {
        val recentStops = recentStopsManager.getRecentStops()
        recentStopsAdapter.updateStops(recentStops)

        val groupCount = (recentStops.size + 2) / 3
        setupCarouselIndicator(groupCount)

        if (recentStops.isEmpty()) {
            if (recentStopsSection.isVisible) eInkFadeOut(recentStopsSection)
        } else {
            if (recentStopsSection.visibility != View.VISIBLE) eInkFadeIn(recentStopsSection)
        }
    }

    private fun loadBusArrivals(stopId: String, signId: String? = null) {
        currentStopId = stopId
        stopIdEditText.setText(signId?: stopId)
        searchBusArrivals(stopId, signId)
    }

    private fun toggleNearestStops() {
        isNearestStopsExpanded = !isNearestStopsExpanded
        if (isNearestStopsExpanded) {
            // Expand Nearest, Shrink Recent
            nearestStopsButton.text = getString(R.string.nearest_stops)
            nearestStopsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.my_location_24px, 0, 0, 0)
            nearestStopsButton.compoundDrawablePadding = dpToPx(8)

            recentStopsButton.text = ""
            recentStopsButton.compoundDrawablePadding = 0

            val currentTime = System.currentTimeMillis()
            if(cachedNearbyStops.isEmpty() || (currentTime - lastNearbyFetchTime) > CACHE_EXPIRATION_MS) {
                // Old data / No data, get new data from GPS
                checkLocationPermissionAndFetch()
            }
            else{
                // Data is fresh -> Just update the UI from memory
                recentStopsAdapter.updateStops(cachedNearbyStops)
                val groupCount = (cachedNearbyStops.size + 2) / 3
                setupCarouselIndicator(groupCount)
            }

        } else {
            // Restore default
            nearestStopsButton.text = ""
            nearestStopsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.my_location_24px, 0, 0, 0)
            nearestStopsButton.compoundDrawablePadding = 0

            recentStopsButton.text = getString(R.string.recent_stops)
            recentStopsButton.compoundDrawablePadding = dpToPx(8)
            
            loadRecentStops()
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        fetchNearbyStops()
    }

    @SuppressLint("MissingPermission")
    private fun fetchNearbyStops() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    busService.getNearbyStops(location.latitude, location.longitude,
                        callback = { stops ->

                            //Save to cache
                            cachedNearbyStops = stops
                            lastNearbyFetchTime = System.currentTimeMillis()

                            runOnUiThread {
                                recentStopsAdapter.updateStops(stops)
                                val groupCount = (stops.size + 2) / 3
                                setupCarouselIndicator(groupCount)
                            }
                        },
                        errorCallback = { error ->
                            runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
                        }
                    )
                } else {
                    Toast.makeText(this, "Could not get location", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupClickListeners() {
        recentStopsButton.setOnClickListener {
            if (isNearestStopsExpanded) {
                toggleNearestStops()
            }
        }

        nearestStopsButton.setOnClickListener {
            toggleNearestStops()
        }

        stopIdTextInputLayout.setEndIconOnClickListener {
            val stopId = stopIdEditText.text.toString().trim()
            if (stopId.isNotEmpty()) {
                searchBusArrivals(stopId)
            } else {
                showError("Please enter a stop ID")
            }
        }

        stopIdEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val stopId = stopIdEditText.text.toString().trim()
                if (stopId.isNotEmpty()) {
                    searchBusArrivals(stopId)
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(stopIdEditText.windowToken, 0)
                } else {
                    showError("Please enter a stop ID")
                }
                true
            } else {
                false
            }
        }
    }

    private fun searchBusArrivals(stopId: String, signId: String? = null) {
        currentStopId = stopId
        showLoading()

        busService.getStopInfo(
            stopId = stopId,
            callback = { stopName, fetchedSignId->
                runOnUiThread {
                    val finalSignId = signId ?: fetchedSignId

                    recentStopsManager.addRecentStop(
                        stopId = stopId,
                        stopName = stopName,
                        signId = finalSignId
                    )
                    if (!isNearestStopsExpanded) {
                        loadRecentStops()
                    }
                }
                getBusArrivalsWithStopName(stopId, stopName)
            },
            errorCallback = { _ ->
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


    private fun refreshCurrentStopData() {
        currentStopId?.let { stopId ->
            refreshBusArrivals(stopId)
        } ?: run {
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun refreshBusArrivals(stopId: String) {

        lifecycleScope.launch {
            busService.getBusArrivals(
                stopId = stopId,
                callback = { arrivals ->
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        if (arrivals.isNotEmpty()) {
                            showResults(arrivals)
                        } else {
                            showNoData()
                        }
                    }
                },
                errorCallback = { error ->
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        showError(error)
                    }
                }
            )
        }
    }

    private fun showLoading() {
        eInkFadeIn(progressBar)
        progressBar.isIndeterminate = false
        animateProgressBar()

        if (busArrivalRecyclerView.isVisible) eInkFadeOut(busArrivalRecyclerView)
        if (errorTextView.isVisible) eInkFadeOut(errorTextView)
        if (noDataTextView.isVisible) eInkFadeOut(noDataTextView)
    }


    private fun hideLoading() {
        eInkFadeOut(progressBar)
    }

    private fun showResults(arrivals: List<BusArrival>) {
        allArrivals = arrivals
        adapter.updateArrivals(arrivals)

        availableBusNumbers = listOf("All") + arrivals.map { it.routeName }.distinct()
        updateBusNumbers()

        eInkFadeIn(busArrivalRecyclerView)
        eInkFadeOut(errorTextView)
        eInkFadeOut(noDataTextView)

    }

    private fun showError(message: String) {
        errorTextView.text = message
        eInkFadeIn(errorTextView)
        eInkFadeOut(busArrivalRecyclerView)
        eInkFadeOut(noDataTextView)
    }

    private fun showNoData() {
        eInkFadeIn(noDataTextView)
        eInkFadeOut(busArrivalRecyclerView)
        eInkFadeOut(errorTextView)
    }

    private fun filterByBusNumber(busNumber: String) {
        if (busNumber == "All") {
            adapter.updateArrivals(allArrivals)
        } else {
            val filteredArrivals = allArrivals.filter { it.routeName == busNumber }
            if (filteredArrivals.isNotEmpty()) {
                adapter.updateArrivals(filteredArrivals)
            } else {
                showNoData()
            }
        }
    }

}
