package Activities

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.travelnow.MyApplication
import androidx.appcompat.app.AppCompatActivity
import com.example.travelnow.databinding.ActivityMapsBinding
import com.example.travelnow.helpers.*
import com.example.travelnow.models.SortOptions
import com.example.travelnow.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import models.SafetyLevel
import models.SafetyReport

@SuppressLint("MissingPermission")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var auth: FirebaseAuth

    private lateinit var locationHelper: LocationManagerHelper
    private lateinit var placesHelper: PlacesSearchHelper
    private lateinit var dialogManager: DialogManager
    private var mapHelper: MapManagerHelper? = null

    private val viewModel by lazy {
        (application as MyApplication).safetyViewModel
    }

    private var currentSortOption = SortOptions.DANGER_LEVEL_DESC
    private var centerLocation: LatLng? = null
    private var pendingReports: List<SafetyReport>? = null
    private var isInitialLoad = true
    private val CITY_NAME_KEY: String = "city"

    // Store pending city search until map is ready
    private var pendingCitySearch: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            showToast("Location permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "========== MapsActivity onCreate START ==========")
        super.onCreate(savedInstanceState)
        Log.d(TAG, "After super.onCreate")

        try {
            binding = ActivityMapsBinding.inflate(layoutInflater)
            Log.d(TAG, "Binding inflated successfully")
            setContentView(binding.root)
            Log.d(TAG, "Content view set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            throw e
        }

        // Store the city name FIRST before anything else
        val cityName = intent.getStringExtra(CITY_NAME_KEY)
        Log.d(TAG, "=== INTENT EXTRAS DEBUG ===")
        Log.d(TAG, "City name from intent: '$cityName'")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        Log.d(TAG, "Has CITY_NAME_KEY: ${intent.hasExtra(CITY_NAME_KEY)}")

        if (!cityName.isNullOrEmpty()) {
            pendingCitySearch = cityName.trim()
            Log.d(TAG, "✓ Pending city search SET to: '$pendingCitySearch'")
        } else {
            pendingCitySearch = null
            Log.d(TAG, "✗ No city name in intent or empty")
        }

        // Initialize components
        initializeComponents()
        authenticateUser()
        setupMapFragment()
        setupUI()
        setupObservers()
    }

    private fun initializeComponents() {
        auth = FirebaseAuth.getInstance()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationHelper = LocationManagerHelper(this, fusedLocationClient)
        placesHelper = PlacesSearchHelper(this)
        dialogManager = DialogManager(this)
    }

    private fun authenticateUser() {
        showLoading(true)
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d(TAG, "Signed in anonymously")
                    showLoading(false)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Authentication failed", e)
                    showToast("Authentication failed: ${e.message}")
                    showLoading(false)
                }
        } else {
            showLoading(false)
        }
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun setupUI() {
        setupSearchBar()
        setupFABs()
    }

    private fun setupSearchBar() {
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line
        )

        with(binding.autoCompleteSearch) {

            setAdapter(adapter)
            threshold = 1

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    handleSearchTextChanged(s, adapter)
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            setOnItemClickListener { _, _, position, _ ->
                handlePlaceSelected(position)
                clearFocus()
            }

            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = v.text.toString()
                    if (query.isNotEmpty()) {
                        searchLocation(query)
                    }
                    clearFocus()
                    true
                } else false
            }
        }

        binding.clearSearch.setOnClickListener {
            binding.autoCompleteSearch.text.clear()
            mapHelper?.removeTemporaryMarker()
            mapHelper?.zoomOut()
            binding.clearSearch.visibility = View.GONE
        }
    }

    private fun setupFABs() {
        with(binding) {
            fabMyLocation.setOnClickListener { getCurrentLocation() }
            fabMapType.setOnClickListener { showMapTypeDialog() }
            fabClearMarkers.setOnClickListener { clearAllMarkers() }
            fabZoomIn.setOnClickListener { mapHelper?.zoomIn() }
            fabZoomOut.setOnClickListener { mapHelper?.zoomOut() }
            fabShowReports.setOnClickListener { showReportsBottomSheet() }
            fabRefresh.setOnClickListener { refreshReports() }
        }
    }

    private fun setupObservers() {
        viewModel.reports.observe(this) { reports ->
            Log.d(TAG, "Reports observer triggered with ${reports.size} reports")
            handleReportsUpdate(reports)
        }

        viewModel.loading.observe(this) { isLoading ->
            if (isInitialLoad) {
                showLoading(isLoading)
                if (!isLoading) {
                    isInitialLoad = false
                }
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                showToast("Error: $it")
                viewModel.clearError()
            }
        }

        viewModel.submitSuccess.observe(this) { success ->
            if (success) {
                showToast("Report submitted successfully!")
                viewModel.resetSubmitSuccess()
            }
        }

        viewModel.focusedReportId.observe(this) { reportId ->
            mapHelper?.setFocusedReport(reportId)
        }

        viewModel.centerLocation.observe(this) { location ->
            // Only update center location if there's no pending city search
            if (pendingCitySearch == null) {
                centerLocation = location
                location?.let { mapHelper?.animateToPosition(it, 14f) }
            }
        }

        viewModel.mapType.observe(this) { type ->
            type?.let {
                val mapType = when (type) {
                    0 -> GoogleMap.MAP_TYPE_NORMAL
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_TERRAIN
                    3 -> GoogleMap.MAP_TYPE_HYBRID
                    else -> GoogleMap.MAP_TYPE_NORMAL
                }
                mapHelper?.setMapType(mapType)
            }
        }

        viewModel.votedReports.observe(this) { votedIds ->
            Log.d(TAG, "Voted reports updated: ${votedIds.size} reports")
        }
    }

    private fun handleReportsUpdate(reports: List<SafetyReport>) {
        Log.d(TAG, "Handling ${reports.size} reports update")
        if (mapHelper != null) {
            Log.d(TAG, "MapHelper is ready, updating reports")
            mapHelper?.updateReports(reports)
            pendingReports = null
        } else {
            Log.d(TAG, "MapHelper not ready, storing pending reports")
            pendingReports = reports
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "========== onMapReady called ==========")
        Log.d(TAG, "Current pendingCitySearch value: '$pendingCitySearch'")

        mapHelper = MapManagerHelper(googleMap, this)
        configureMap(googleMap)
        setupMapListeners(googleMap)

        mapHelper?.onReportClickListener = { reportId ->
            showViewReportDialog(reportId)
        }

        applyPendingData()

        val cityToSearch = pendingCitySearch
        if (!cityToSearch.isNullOrEmpty()) {
            Log.d(TAG, "✓ Processing pending city search: '$cityToSearch'")
            pendingCitySearch = null // Clear BEFORE calling to prevent re-entry
            searchLocationAndAnimate(cityToSearch)
        } else {
            Log.d(TAG, "✗ No pending city search, checking for current location")
            checkLocationPermission()
        }
    }

    private fun searchLocationAndAnimate(cityName: String) {
        Log.d(TAG, "========== searchLocationAndAnimate START ==========")
        Log.d(TAG, "Searching for city: '$cityName'")
        showLoading(true)

        locationHelper.getLatLngFromLocationName(
            cityName,
            onSuccess = { latLng, address ->
                runOnUiThread {
                    Log.d(TAG, "✓✓✓ City FOUND: '$address' at $latLng")
                    Log.d(TAG, "Thread: ${Thread.currentThread().name}")
                    showLoading(false)

                    binding.autoCompleteSearch.setText(address)

                    centerLocation = latLng
                    viewModel.setCenterLocation(latLng)

                    mapHelper?.let { helper ->
                        Log.d(TAG, "MapHelper exists, animating camera to position: $latLng")

                        helper.addTemporaryMarker(latLng, address, "Long press to report safety")

                        helper.animateToPosition(latLng, 12f)

                        viewModel.loadNearbyReports(latLng.latitude, latLng.longitude, 100.0)

                        showToast("Viewing $address")
                        Log.d(TAG, "✓ Camera animation triggered successfully")
                    } ?: run {
                        Log.e(TAG, "✗✗✗ MapHelper is NULL!")
                        showToast("Map not ready")
                    }
                }
            },
            onFailure = { exception ->
                runOnUiThread {
                    showLoading(false)
                    Log.e(TAG, "✗✗✗ City NOT FOUND: '$cityName'")
                    Log.e(TAG, "Error: ${exception.message}")
                    showToast("Could not find city: $cityName. Using your location instead.")
                    checkLocationPermission()
                }
            }
        )
    }

    private fun applyPendingData() {
        pendingReports?.let { reports ->
            Log.d(TAG, "Applying ${reports.size} pending reports")
            mapHelper?.updateReports(reports)
            pendingReports = null
        }

        viewModel.mapType.value?.let { type ->
            val mapType = when (type) {
                0 -> GoogleMap.MAP_TYPE_NORMAL
                1 -> GoogleMap.MAP_TYPE_SATELLITE
                2 -> GoogleMap.MAP_TYPE_TERRAIN
                3 -> GoogleMap.MAP_TYPE_HYBRID
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
            mapHelper?.setMapType(mapType)
        }
    }

    private fun configureMap(map: GoogleMap) {
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isTiltGesturesEnabled = true
            isRotateGesturesEnabled = true
        }

        if (locationHelper.hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun setupMapListeners(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            Log.d(TAG, "Map long clicked at: $latLng")
            handleMapLongClick(latLng)
        }

        map.setOnMarkerClickListener { marker ->
            if (marker.zIndex == 10f) {
                false
            } else {

                mapHelper?.getClusterManager()?.onMarkerClick(marker) ?: false
            }
        }

        map.setOnCameraIdleListener {
            handleCameraIdle()
        }
    }

    private fun handleSearchTextChanged(text: CharSequence?, adapter: ArrayAdapter<String>) {
        if (!text.isNullOrEmpty()) {
            binding.clearSearch.visibility = View.VISIBLE
            searchPlaces(text.toString(), adapter)
        } else {
            binding.clearSearch.visibility = View.GONE
        }
    }

    private fun searchPlaces(query: String, adapter: ArrayAdapter<String>) {
        placesHelper.searchPlaces(
            query,
            onSuccess = { suggestions, _ ->
                adapter.clear()
                adapter.addAll(suggestions)
                adapter.notifyDataSetChanged()
            },
            onFailure = { exception ->
                Log.e(TAG, "Search failed: ${exception.message}")
            }
        )
    }

    private fun handlePlaceSelected(position: Int) {
        val prediction = placesHelper.getPredictionAt(position) ?: return
        val locationName = prediction.getFullText(null).toString()

        locationHelper.getLatLngFromLocationName(
            locationName,
            onSuccess = { latLng, address ->
                updateLocationAndLoadReports(latLng, address)
                placesHelper.resetSession()
            },
            onFailure = {
                showToast("Could not find location")
            }
        )
    }

    private fun searchLocation(query: String) {
        Log.d(TAG, "Searching for location: $query")
        locationHelper.getLatLngFromLocationName(
            query,
            onSuccess = { latLng, address ->
                Log.d(TAG, "Location found: $address at $latLng")
                updateLocationAndLoadReports(latLng, address)
            },
            onFailure = {
                Log.e(TAG, "Location not found for query: $query")
                showToast("Location not found")
            }
        )
    }

    private fun updateLocationAndLoadReports(latLng: LatLng, title: String) {
        mapHelper?.addTemporaryMarker(latLng, title, "Long press to report safety")
        mapHelper?.animateToPosition(latLng, 14f)
        centerLocation = latLng
        viewModel.setCenterLocation(latLng)
        viewModel.loadNearbyReports(latLng.latitude, latLng.longitude, 100.0)
    }

    private fun checkLocationPermission() {
        if (locationHelper.hasLocationPermission()) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocation() {
        if (!locationHelper.hasLocationPermission()) return

        if (!locationHelper.isGpsEnabled()) {
            dialogManager.showGpsSettingsDialog()
            return
        }

        showLoading(true)
        locationHelper.getCurrentLocation(
            onSuccess = { location ->
                showLoading(false)
                val latLng = LatLng(location.latitude, location.longitude)
                centerLocation = latLng
                viewModel.setCenterLocation(latLng)
                mapHelper?.animateToPosition(latLng, 14f)
                viewModel.loadNearbyReports(location.latitude, location.longitude, 100.0)
                showToast("Location found!")
            },
            onFailure = { exception ->
                showLoading(false)
                if (exception.message?.contains("GPS is disabled") == true) {
                    dialogManager.showGpsSettingsDialog()
                } else {
                    showToast("Failed to get location: ${exception.message}")
                }
            }
        )
    }

    private fun handleMapLongClick(latLng: LatLng) {
        Log.d(TAG, "Map long clicked at: $latLng")

        if (!locationHelper.isGpsEnabled()) {
            dialogManager.showGpsSettingsDialog()
            return
        }

        val currentLoc = centerLocation
        if (currentLoc == null) {
            showToast("Getting your location... Please wait and try again.")
            getCurrentLocation()
            return
        }

        val distance = locationHelper.calculateDistance(
            currentLoc.latitude,
            currentLoc.longitude,
            latLng.latitude,
            latLng.longitude
        )

        if (distance > 100.0) {
            dialogManager.showDistanceTooFarDialog(distance)
            return
        }

        Log.d(TAG, "Getting address for long click")

        var callbackExecuted = false

        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!callbackExecuted) {
                Log.w(TAG, "Geocoder timeout, using default location name")
                callbackExecuted = true
                showAddReportDialog(latLng, "Selected Location")
            }
        }

        timeoutHandler.postDelayed(timeoutRunnable, 3000)

        locationHelper.getAddressFromLocation(latLng) { areaName ->
            if (!callbackExecuted) {
                callbackExecuted = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                Log.d(TAG, "Address received: $areaName")
                showAddReportDialog(latLng, areaName)
            }
        }
    }

    private var lastReportLoadTime = 0L
    private val REPORT_LOAD_DELAY = 1000L // 1 second

    private fun handleCameraIdle() {
        // Always update clustering
        mapHelper?.onCameraIdle()

        val center = mapHelper?.getCurrentCameraPosition() ?: return
        val zoom = mapHelper?.getCurrentZoom() ?: return

        // Only reload reports if enough time has passed and zoom is appropriate
        if (zoom >= 10f) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReportLoadTime > REPORT_LOAD_DELAY) {
                lastReportLoadTime = currentTime
                viewModel.loadNearbyReports(center.latitude, center.longitude, 100.0)
            }
        }
    }

    private fun showAddReportDialog(latLng: LatLng, areaName: String) {
        Log.d(TAG, "Showing add report dialog for: $areaName")
        dialogManager.showSafetyReportDialog(
            latLng = latLng,
            areaName = areaName,
            onSubmit = { level, comment, radius ->
                submitReport(latLng, areaName, level, comment, radius)
            }
        )
    }

    private fun showViewReportDialog(reportId: String) {
        val report = viewModel.reports.value?.find { it.id == reportId } ?: return
        val hasVoted = viewModel.hasUserVoted(reportId)

        dialogManager.showViewReportDialog(
            report = report,
            hasVoted = hasVoted,
            onUpvote = {
                viewModel.voteOnReport(reportId, true)
            },
            onDownvote = {
                viewModel.voteOnReport(reportId, false)
            }
        )
    }

    private fun submitReport(
        latLng: LatLng,
        areaName: String,
        level: SafetyLevel,
        comment: String,
        radiusMeters: Int
    ) {
        if (comment.isBlank()) {
            showToast("Please add a comment")
            return
        }

        val currentLoc = centerLocation
        if (currentLoc != null) {
            val distance = locationHelper.calculateDistance(
                currentLoc.latitude,
                currentLoc.longitude,
                latLng.latitude,
                latLng.longitude
            )

            if (distance > 100.0) {
                showToast("Location is too far from your current position")
                return
            }
        }

        viewModel.submitReport(
            latLng.latitude,
            latLng.longitude,
            areaName,
            level.name,
            comment,
            radiusMeters
        )
    }

    override fun onResume() {
        super.onResume()

        // Only get current location if we don't have a center location AND no pending city search
        if (locationHelper.isGpsEnabled() && centerLocation == null && pendingCitySearch == null) {
            checkLocationPermission()
        }
    }

    private fun showReportsBottomSheet() {
        val allReports = viewModel.reports.value ?: emptyList()
        val votedReportIds = viewModel.votedReports.value ?: emptySet()
        Log.d(TAG, "Showing bottom sheet with ${allReports.size} reports, ${votedReportIds.size} voted")
        val sortedReports = ReportSorter.sort(allReports, currentSortOption)

        val bottomSheet = dialogManager.showReportsBottomSheet(
            reports = sortedReports,
            votedReportIds = votedReportIds,
            currentSort = currentSortOption,
            onSort = { sortOption ->
                currentSortOption = sortOption
                showReportsBottomSheet()
            },
            onUpvote = { report ->
                viewModel.voteOnReport(report.id, true)
            },
            onDownvote = { report ->
                viewModel.voteOnReport(report.id, false)
            },
            onItemClick = { report ->
                mapHelper?.setFocusedReport(report.id)
            }
        )
        bottomSheet.show()
    }

    private fun showMapTypeDialog() {
        val currentType = viewModel.mapType.value ?: 0
        dialogManager.showMapTypeDialog(currentType) { type ->
            viewModel.setMapType(type)
        }
    }

    private fun clearAllMarkers() {
        mapHelper?.clearAllReports()
        mapHelper?.removeTemporaryMarker()
        showToast("All safety zones cleared")
    }

    private fun refreshReports() {
        val center = centerLocation ?: mapHelper?.getCurrentCameraPosition()
        if (center == null) {
            showToast("Map not ready")
            return
        }

        viewModel.forceRefresh(center.latitude, center.longitude)
        showToast("Refreshing reports...")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.cancelLocationRequest()
        Log.d(TAG, "MapsActivity destroyed")
    }

    companion object {
        private const val TAG = "MapsActivity"
    }
}