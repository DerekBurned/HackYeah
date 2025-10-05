package com.example.travelnow

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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.travelnow.databinding.ActivityMapsBinding
import com.example.travelnow.helpers.*
import com.example.travelnow.models.SortOptions
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

    // Helpers
    private lateinit var locationHelper: LocationManagerHelper
    private lateinit var placesHelper: PlacesSearchHelper
    private lateinit var dialogManager: DialogManager
    private var mapHelper: MapManagerHelper? = null

    private val viewModel by lazy {
        (application as MyApplication).safetyViewModel
    }

    // State
    private var currentSortOption = SortOptions.DANGER_LEVEL_DESC
    private var centerLocation: LatLng? = null
    private var pendingReports: List<SafetyReport>? = null

    // Permission launcher
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
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            handleReportsUpdate(reports)
        }

        viewModel.loading.observe(this) { isLoading ->
            showLoading(isLoading)
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
            centerLocation = location
            location?.let { mapHelper?.animateToPosition(it, 14f) }
        }

        viewModel.mapType.observe(this) { type ->
            type?.let { mapHelper?.setMapType(it) }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapHelper = MapManagerHelper(googleMap)

        configureMap(googleMap)
        setupMapListeners(googleMap)
        checkLocationPermission()
        applyPendingData()
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

    private fun setupMapListeners(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            handleMapLongClick(latLng)
        }

        map.setOnMapClickListener { latLng ->
            handleMapClick(latLng)
        }

        map.setOnMarkerClickListener { marker ->
            handleMarkerClick(marker.tag as? String)
        }

        map.setOnCameraIdleListener {
            handleCameraIdle()
        }
    }

    private fun applyPendingData() {
        pendingReports?.let { reports ->
            Log.d(TAG, "Applying ${reports.size} pending reports")
            mapHelper?.updateReports(reports)
            pendingReports = null
        }

        viewModel.mapType.value?.let { type ->
            mapHelper?.setMapType(type)
        }

        viewModel.centerLocation.value?.let { location ->
            mapHelper?.animateToPosition(location, 14f)
        }
    }

    // Search Functionality
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
                binding.autoCompleteSearch.text.clear()
            },
            onFailure = {
                showToast("Could not find location")
            }
        )
    }

    private fun searchLocation(query: String) {
        locationHelper.getLatLngFromLocationName(
            query,
            onSuccess = { latLng, address ->
                updateLocationAndLoadReports(latLng, address)
            },
            onFailure = {
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

    // Location Functionality

    private fun checkLocationPermission() {
        if (locationHelper.hasLocationPermission()) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocation() {
        if (!locationHelper.hasLocationPermission()) return

        showLoading(true)
        locationHelper.getCurrentLocation(
            onSuccess = { location ->
                showLoading(false)
                val latLng = LatLng(location.latitude, location.longitude)
                centerLocation = latLng
                mapHelper?.animateToPosition(latLng, 14f)
                viewModel.setCenterLocation(latLng)
                viewModel.loadNearbyReports(location.latitude, location.longitude, 100.0)
                showToast("Location found!")
            },
            onFailure = { exception ->
                showLoading(false)
                showToast("Failed to get location: ${exception.message}")
            }
        )
    }

    // Map Event Handlers
    private fun handleMapLongClick(latLng: LatLng) {
        showLoading(true)
        locationHelper.getAddressFromLocation(latLng) { areaName ->
            showLoading(false)
            showAddReportDialog(latLng, areaName)
        }
    }

    private fun handleMapClick(latLng: LatLng) {
        mapHelper?.addTemporaryMarker(latLng, "Selected Location", "Long press to report safety status")
        mapHelper?.animateToPosition(latLng, 15f)

        locationHelper.getAddressFromLocation(latLng) { address ->
            showToast(address)
        }
    }

    private fun handleMarkerClick(reportId: String?): Boolean {
        return if (reportId != null) {
            showViewReportDialog(reportId)
            true
        } else {
            false
        }
    }

    private fun handleCameraIdle() {
        val center = mapHelper?.getCurrentCameraPosition() ?: return
        val zoom = mapHelper?.getCurrentZoom() ?: return

        if (zoom >= 10f) {
            viewModel.loadNearbyReports(center.latitude, center.longitude, 100.0)
        }
    }

    // Reports Management
    private fun handleReportsUpdate(reports: List<SafetyReport>) {
        if (mapHelper != null) {
            mapHelper?.updateReports(reports)
            pendingReports = null
        } else {
            pendingReports = reports
            Log.d(TAG, "Map not ready, storing ${reports.size} pending reports")
        }
    }

    private fun showAddReportDialog(latLng: LatLng, areaName: String) {
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

        dialogManager.showViewReportDialog(
            report = report,
            onUpvote = {
                viewModel.voteOnReport(reportId, true)
                showToast("Upvoted!")
            },
            onDownvote = {
                viewModel.voteOnReport(reportId, false)
                showToast("Downvoted!")
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

        viewModel.submitReport(
            latLng.latitude,
            latLng.longitude,
            areaName,
            level.name,
            comment,
            radiusMeters
        )
    }

    private fun showReportsBottomSheet() {
        val allReports = viewModel.reports.value ?: emptyList()
        val sortedReports = ReportSorter.sort(allReports, currentSortOption)

        dialogManager.showReportsBottomSheet(
            reports = sortedReports,
            currentSort = currentSortOption,
            onSort = { sortOption ->
                currentSortOption = sortOption
                val newSorted = ReportSorter.sort(allReports, sortOption)
                showReportsBottomSheet() // Refresh sheet with new sort
            },
            onUpvote = { report ->
                viewModel.voteOnReport(report.id, true)
                showToast("Upvoted ${report.areaName}")
            },
            onDownvote = { report ->
                viewModel.voteOnReport(report.id, false)
                showToast("Downvoted ${report.areaName}")
            },
            onItemClick = { report ->
                mapHelper?.setFocusedReport(report.id)
            }
        )
    }

    // UI Actions
    private fun showMapTypeDialog() {
        val currentType = viewModel.mapType.value ?: 0
        dialogManager.showMapTypeDialog(currentType) { type ->
            viewModel.setMapType(type)
            mapHelper?.setMapType(
                when (type) {
                    0 -> GoogleMap.MAP_TYPE_NORMAL
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_TERRAIN
                    3 -> GoogleMap.MAP_TYPE_HYBRID
                    else -> GoogleMap.MAP_TYPE_NORMAL
                }
            )
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

    // Utility Methods
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