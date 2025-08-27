package com.heuge.busapp.ui.main

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.animation.LinearInterpolator
import android.animation.ObjectAnimator
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.PagerSnapHelper
import com.google.android.material.textfield.TextInputLayout
import com.heuge.busapp.R
import com.heuge.busapp.data.api.NSWBusService
import com.heuge.busapp.data.local.RecentStopsManager
import com.heuge.busapp.data.model.BusArrival
import com.heuge.busapp.ui.adapter.BusArrivalAdapter
import com.heuge.busapp.ui.adapter.RecentStopsAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var stopIdEditText: EditText
    private lateinit var stopIdTextInputLayout: TextInputLayout
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

    private lateinit var indicatorContainer: LinearLayout
    private val indicators = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

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
        loadRecentStops()
    }

    //fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()


    private fun setupTouchOutsideToClearFocus() {
        val rootLayout = findViewById<View>(R.id.root_layout)
        rootLayout.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
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

        // Set light status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    // Custom animation replacements for e-ink
    private fun eInkFadeIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(200) // Slower for e-ink
            .setInterpolator(LinearInterpolator()) // Linear for e-ink
            .start()
    }

    private fun eInkFadeOut(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .withEndAction { view.visibility = View.GONE }
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


    private fun animateProgressBar() {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
        animator.duration = 1000
        animator.interpolator = LinearInterpolator()
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.RESTART
        animator.start()
    }

    // Custom touch feedback for e-ink
    private fun setupEInkTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Instant visual feedback
                    v.alpha = 0.7f
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1.0f
                    v.performClick() // Notify that the view was clicked
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                }
            }
            true // Indicate we handled the touch
        }
    }


    private fun initializeViews() {
        stopIdEditText = findViewById(R.id.stopIdEditText)
        stopIdTextInputLayout = findViewById(R.id.stopIdTextInputLayout)
        progressBar = findViewById(R.id.progressBar)
        busArrivalRecyclerView = findViewById(R.id.recyclerView)
        recentStopsRecyclerView = findViewById(R.id.recentStopsRecyclerView)
        errorTextView = findViewById(R.id.errorTextView)
        noDataTextView = findViewById(R.id.noDataTextView)
        recentStopsSection = findViewById(R.id.recentStopsSection)
        indicatorContainer = findViewById(R.id.indicatorContainer)
    }

    private fun setupRecyclerView() {
        adapter = BusArrivalAdapter(emptyList())
        busArrivalRecyclerView.layoutManager = LinearLayoutManager(this)
        busArrivalRecyclerView.adapter = adapter
    }

    private fun setupRecentStopsRecyclerView() {
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recentStopsRecyclerView)

        recentStopsAdapter = RecentStopsAdapter { busStop ->
            // Handle stop selection
            loadBusArrivals(busStop.id)
        }

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

        // Set up carousel indicator with the actual count
        // Calculate groups for indicator
        val groupCount = (recentStops.size + 2) / 3  // Ceiling division
        setupCarouselIndicator(groupCount)

        // Animate visibility changes using e-ink fade functions
        if (recentStops.isEmpty()) {
            eInkFadeOut(recentStopsSection)
        } else {
            eInkFadeIn(recentStopsSection)
        }
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
        stopIdTextInputLayout.setEndIconOnClickListener {
            val stopId = stopIdEditText.text.toString().trim()
            if (stopId.isNotEmpty()) {
                searchBusArrivals(stopId)
            } else {
                showError("Please enter a stop ID")
            }
        }

        // Handle Enter key press
        stopIdEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val stopId = stopIdEditText.text.toString().trim()
                if (stopId.isNotEmpty()) {
                    searchBusArrivals(stopId)
                    // Hide keyboard after search
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(stopIdEditText.windowToken, 0)
                } else {
                    showError("Please enter a stop ID")
                }
                true // Consume the event
            } else {
                false // Don't consume the event
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
        eInkFadeIn(progressBar)
        progressBar.isIndeterminate = false
        animateProgressBar()

        eInkFadeOut(busArrivalRecyclerView)
        eInkFadeOut(errorTextView)
        eInkFadeOut(noDataTextView)
    }


    private fun hideLoading() {
        eInkFadeOut(progressBar)
    }

    private fun showResults(arrivals: List<BusArrival>) {
        adapter.updateArrivals(arrivals)
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
}
