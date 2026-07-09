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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.heuge.busapp.R
import com.heuge.busapp.data.api.NSWBusService
import com.heuge.busapp.data.local.NearestStopsManager
import com.heuge.busapp.data.local.RecentStopsManager
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.data.model.BusStop
import com.heuge.busapp.data.model.TravelAlert
import com.heuge.busapp.ui.adapter.BusArrivalAdapter
import com.heuge.busapp.ui.adapter.BusNumberAdapter
import com.heuge.busapp.ui.adapter.RecentStopsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var stopIdEditText: EditText
    private lateinit var searchIcon: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var busArrivalRecyclerView: RecyclerView
    private lateinit var recentStopsRecyclerView: RecyclerView
    private lateinit var errorTextView: TextView
    private lateinit var noDataTextView: TextView
    private lateinit var recentStopsSection: LinearLayout

    private lateinit var recentStopsButton: TextView
    private lateinit var nearestStopsButton: TextView
    private lateinit var alertsButton: TextView
    private lateinit var closeAlertsButton: ImageView
    private lateinit var recentStopsShimmer: ShimmerFrameLayout

    private lateinit var alertsSection: LinearLayout
    private lateinit var alertsRecyclerView: RecyclerView
    private lateinit var noAlertsText: TextView
    private lateinit var alertsAdapter: com.heuge.busapp.ui.adapter.TravelAlertAdapter
    private lateinit var carouselContainer: LinearLayout
    private lateinit var appBarLayout: com.google.android.material.appbar.AppBarLayout

    private lateinit var busService: NSWBusService
    private lateinit var adapter: BusArrivalAdapter
    private lateinit var busNumberAdapter: BusNumberAdapter

    private lateinit var recentStopsManager: RecentStopsManager
    private lateinit var recentStopsAdapter: RecentStopsAdapter

    private lateinit var nearestStopsManager: NearestStopsManager

    private lateinit var indicatorContainer: LinearLayout
    private val indicators = mutableListOf<View>()

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var currentStopId: String? = null

    private var availableBusNumbers: List<String> = emptyList() // Stores all buses available at the current stop


    private var allArrivals: List<BusArrival> = emptyList() // Stores all arrivals so we can filter without losing data

    private var isNearestStopsExpanded = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

        nearestStopsManager = NearestStopsManager()
        recentStopsManager = RecentStopsManager(this)
        setupRecentStopsRecyclerView()
        setupBusNumberRecyclerView()
        setupSwipeRefresh()
        loadRecentStops()
        updateScrollFlags(false)
        
        // Initial button states
        recentStopsButton.isSelected = true
        nearestStopsButton.isSelected = false

        // Close alerts when scrolling down to content
        appBarLayout.addOnOffsetChangedListener(object : com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener {
            private var lastOffset = 0
            override fun onOffsetChanged(appBarLayout: com.google.android.material.appbar.AppBarLayout, verticalOffset: Int) {
                if (verticalOffset < lastOffset && alertsButton.isSelected) {
                    toggleAlertsSection()
                }
                lastOffset = verticalOffset
            }
        })
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
        searchIcon = findViewById(R.id.searchIcon)
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
        alertsButton = findViewById(R.id.alertsButton)
        recentStopsShimmer = findViewById(R.id.recentStopsShimmer)

        alertsSection = findViewById(R.id.alertsSection)
        alertsRecyclerView = findViewById(R.id.alertsRecyclerView)
        noAlertsText = findViewById(R.id.noAlertsText)
        closeAlertsButton = findViewById(R.id.closeAlertsButton)
        carouselContainer = findViewById(R.id.carouselContainer)
        appBarLayout = findViewById(R.id.appBarLayout)
    }

    private fun setupRecyclerView() {
        adapter = BusArrivalAdapter(emptyList()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loadEarlierArrivals()
            } else {
                Toast.makeText(this, "Earlier arrivals not supported on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        busArrivalRecyclerView.layoutManager = LinearLayoutManager(this)
        busArrivalRecyclerView.adapter = adapter

        alertsAdapter = com.heuge.busapp.ui.adapter.TravelAlertAdapter(emptyList())
        alertsRecyclerView.layoutManager = LinearLayoutManager(this)
        alertsRecyclerView.adapter = alertsAdapter
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
        // Ensure shimmer is hidden when loading recent stops (local)
        recentStopsShimmer.stopShimmer()
        recentStopsShimmer.visibility = View.GONE
        recentStopsRecyclerView.visibility = View.VISIBLE

        val recentStops = recentStopsManager.getRecentStops()
        recentStopsAdapter.updateStops(recentStops)

        val groupCount = (recentStops.size + 2) / 3
        setupCarouselIndicator(groupCount)

        // Always show the section (buttons) even if empty
        if (recentStopsSection.visibility != View.VISIBLE) {
            eInkFadeIn(recentStopsSection)
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
            nearestStopsButton.isSelected = true
            recentStopsButton.isSelected = false

            nearestStopsButton.text = getString(R.string.nearest_stops)
            nearestStopsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.my_location_24px, 0, 0, 0)
            nearestStopsButton.compoundDrawablePadding = dpToPx(8)

            recentStopsButton.text = ""
            recentStopsButton.compoundDrawablePadding = 0

            if (!nearestStopsManager.isCacheValid()) {
                // Old data / No data, get new data from GPS
                checkLocationPermissionAndFetch()
            } else {
                // Data is fresh -> Just update the UI from memory
                updateNearbyStopsUI(nearestStopsManager.getCachedStops())
            }

        } else {
            // Restore default
            recentStopsButton.isSelected = true
            nearestStopsButton.isSelected = false

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
        // Show Shimmer while fetching
        showShimmer()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    busService.getNearbyStops(location.latitude, location.longitude,
                        callback = { stops ->

                            nearestStopsManager.updateStops(stops)
                            runOnUiThread { updateNearbyStopsUI(stops) }
                        },
                        errorCallback = { error ->
                            runOnUiThread { 
                                hideShimmer()
                                Toast.makeText(this, error, Toast.LENGTH_SHORT).show() 
                            }
                        }
                    )
                } else {
                    hideShimmer()
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
            if (!isNearestStopsExpanded) {
                toggleNearestStops()
            }
        }

        alertsButton.setOnClickListener {
            toggleAlertsSection()
        }

        closeAlertsButton.setOnClickListener {
            toggleAlertsSection()
        }

        searchIcon.setOnClickListener {
            val stopId = stopIdEditText.text.toString().trim()
            if (stopId.isNotEmpty()) {
                searchBusArrivals(stopId)
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(stopIdEditText.windowToken, 0)
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

    private fun toggleAlertsSection() {
        if (alertsSection.isVisible) {
            eInkFadeOut(alertsSection)
            alertsButton.isSelected = false
        } else {
            eInkFadeIn(alertsSection)
            alertsButton.isSelected = true
            fetchTravelAlerts()
        }
    }

    private fun fetchTravelAlerts() {
        busService.getTravelAlerts(
            callback = { alerts ->
                runOnUiThread {
                    if (alerts.isEmpty()) {
                        alertsRecyclerView.visibility = View.GONE
                        noAlertsText.visibility = View.VISIBLE
                    } else {
                        noAlertsText.visibility = View.GONE
                        alertsRecyclerView.visibility = View.VISIBLE
                        alertsAdapter.updateAlerts(alerts)
                    }
                }
            },
            errorCallback = { _ ->
                runOnUiThread {
                    alertsRecyclerView.visibility = View.GONE
                    noAlertsText.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun searchBusArrivals(stopId: String, signId: String? = null) {
        currentStopId = stopId
        allArrivals = emptyList() // Clear previous results
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        } else {
            hideLoading()
            showError("Android 8.0 or higher is required for arrival data")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        } else {
            swipeRefreshLayout.isRefreshing = false
            showError("Android 8.0 or higher is required for arrival data")
        }
    }

    private fun updateScrollFlags(canScroll: Boolean) {
        val params = carouselContainer.layoutParams as com.google.android.material.appbar.AppBarLayout.LayoutParams
        if (canScroll) {
            params.scrollFlags = com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
        } else {
            params.scrollFlags = 0
            // Force it to expand and stay expanded instantly
            appBarLayout.setExpanded(true, false)
        }
        carouselContainer.layoutParams = params

        // Lock manual dragging of the AppBar via touch on the header itself
        val appBarParams = appBarLayout.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val behavior = appBarParams.behavior as? com.google.android.material.appbar.AppBarLayout.Behavior
        behavior?.setDragCallback(object : com.google.android.material.appbar.AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: com.google.android.material.appbar.AppBarLayout): Boolean = canScroll
        })

        // Prevent the content below from triggering AppBar scroll
        swipeRefreshLayout.isNestedScrollingEnabled = canScroll
        busArrivalRecyclerView.isNestedScrollingEnabled = canScroll
    }

    private fun showLoading() {
        updateScrollFlags(false)
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
        updateScrollFlags(true)

        lifecycleScope.launch(Dispatchers.Default) {
            // Mark past arrivals on background thread
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val now = ZonedDateTime.now(ZoneId.of("Australia/Sydney"))
                arrivals.forEach { arrival ->
                    try {
                        val utcTime = try {
                            OffsetDateTime.parse(arrival.realTimeTime).toInstant()
                        } catch (_: Exception) {
                            if (arrival.realTimeTime.contains("Z")) {
                                Instant.parse(arrival.realTimeTime)
                            } else {
                                Instant.parse("${arrival.realTimeTime}Z")
                            }
                        }
                        val sydneyTime = utcTime.atZone(ZoneId.of("Australia/Sydney"))
                        // Subtract a small buffer (e.g. 1 min) if you want "Now" arrivals to stay active slightly longer
                        arrival.isPast = sydneyTime.isBefore(now.minusSeconds(30))
                    } catch (_: Exception) {
                    }
                }
            }

            val distinctBusNumbers = arrivals.map { it.routeName }.distinct()

            withContext(Dispatchers.Main) {
                allArrivals = arrivals
                adapter.updateArrivals(arrivals)

                availableBusNumbers = listOf("All") + distinctBusNumbers
                updateBusNumbers()

                eInkFadeIn(busArrivalRecyclerView)
                eInkFadeOut(errorTextView)
                eInkFadeOut(noDataTextView)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadEarlierArrivals() {
        val stopId = currentStopId ?: return
        
        // Use the current earliest arrival as our reference point
        val firstArrival = allArrivals.minByOrNull { it.realTimeTime }
        val referenceTime = try {
            if (firstArrival != null) {
                val utcTime = try {
                    OffsetDateTime.parse(firstArrival.realTimeTime).toInstant()
                } catch (_: Exception) {
                    if (firstArrival.realTimeTime.contains("Z")) {
                        Instant.parse(firstArrival.realTimeTime)
                    } else {
                        Instant.parse("${firstArrival.realTimeTime}Z")
                    }
                }
                utcTime.atZone(ZoneId.of("Australia/Sydney")).toOffsetDateTime()
            } else {
                OffsetDateTime.now(ZoneId.of("Australia/Sydney"))
            }
        } catch (_: Exception) {
            OffsetDateTime.now(ZoneId.of("Australia/Sydney"))
        }

        // We want arrivals BEFORE our current earliest arrival.
        // We'll search starting from 2 hours prior to our earliest item.
        val searchStartTime = referenceTime.minusHours(2)
        val timeLabel = searchStartTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        Toast.makeText(this, "Searching from $timeLabel...", Toast.LENGTH_SHORT).show()

        busService.getBusArrivals(
            stopId = stopId,
            dateTime = searchStartTime,
            callback = { results ->
                lifecycleScope.launch(Dispatchers.Default) {
                    // 1. Filter results to only those that are BEFORE our reference time
                    // 2. Sort by time descending so we get the most recent ones first
                    // 3. Take the top 5 (which are the 5 closest to our current list)
                    val earlierArrivals = results
                        .filter { it.realTimeTime < (firstArrival?.realTimeTime ?: "") }
                        .sortedByDescending { it.realTimeTime }
                        .take(5)
                        .reversed() // Reverse back to chronological order for the UI

                    withContext(Dispatchers.Main) {
                        if (earlierArrivals.isNotEmpty()) {
                            val combined = (earlierArrivals + allArrivals)
                                .distinctBy { it.realTimeTime + it.routeName + it.destination }
                                .sortedBy { it.realTimeTime }
                            
                            showResults(combined)
                            Toast.makeText(this@MainActivity, "Loaded ${earlierArrivals.size} earlier arrivals", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "No earlier arrivals found in the last 2 hours", Toast.LENGTH_SHORT).show()
                            hideLoading()
                        }
                    }
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showError(message: String) {
        updateScrollFlags(false)
        errorTextView.text = message
        eInkFadeIn(errorTextView)
        eInkFadeOut(busArrivalRecyclerView)
        eInkFadeOut(noDataTextView)
    }

    private fun showNoData() {
        updateScrollFlags(false)
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

    private fun showShimmer() {
        recentStopsRecyclerView.visibility = View.GONE
        indicatorContainer.visibility = View.GONE
        recentStopsShimmer.visibility = View.VISIBLE
        recentStopsShimmer.startShimmer()
    }

    private fun hideShimmer() {
        recentStopsShimmer.stopShimmer()
        recentStopsShimmer.visibility = View.GONE
        recentStopsRecyclerView.visibility = View.VISIBLE
    }

    private fun updateNearbyStopsUI(stops: List<BusStop>) {
        hideShimmer()
        recentStopsAdapter.updateStops(stops)
        val groupCount = (stops.size + 2) / 3
        setupCarouselIndicator(groupCount)
    }

}
