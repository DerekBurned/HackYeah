package ViewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import models.SafetyReport
import kotlinx.coroutines.launch
import repository.SafetyRepository

class SafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SafetyRepository(application)

    val reports: LiveData<List<SafetyReport>> = repository.getReportsFlow().asLiveData()

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _submitSuccess = MutableLiveData<Boolean>()
    val submitSuccess: LiveData<Boolean> = _submitSuccess

    private val _focusedReportId = MutableLiveData<String?>()
    val focusedReportId: LiveData<String?> = _focusedReportId

    private val _centerLocation = MutableLiveData<LatLng?>()
    val centerLocation: LiveData<LatLng?> = _centerLocation

    private val _mapType = MutableLiveData<Int>()
    val mapType: LiveData<Int> = _mapType

    private val _votedReports = MutableLiveData<Set<String>>()
    val votedReports: LiveData<Set<String>> = _votedReports

    private var lastLoadLocation: LatLng? = null
    private val loadDistanceThreshold = 5.0 // km
    private var lastLoadTime = 0L
    private val loadTimeThreshold = 30000L // 30 seconds

    init {
        syncUnsyncedReports()
        cleanOldReports()
    }

    /**
     * Load nearby reports - checks distance/time threshold before fetching
     */
    fun loadNearbyReports(latitude: Double, longitude: Double, radiusKm: Double = 100.0) {
        val currentLocation = LatLng(latitude, longitude)
        val currentTime = System.currentTimeMillis()

        var shouldRefresh = false

        lastLoadLocation?.let { lastLoc ->
            val distance = calculateDistance(
                lastLoc.latitude, lastLoc.longitude,
                latitude, longitude
            )
            if (distance > loadDistanceThreshold) {
                shouldRefresh = true
                Log.d(TAG, "Position changed by ${String.format("%.2f", distance)} km, refreshing")
            }
        } ?: run {
            shouldRefresh = true
            Log.d(TAG, "First load, fetching reports")
        }

        if (currentTime - lastLoadTime > loadTimeThreshold) {
            shouldRefresh = true
            Log.d(TAG, "Time threshold exceeded, refreshing")
        }

        if (shouldRefresh) {
            viewModelScope.launch {
                _loading.value = true
                _error.value = null

                Log.d(TAG, "Loading nearby reports for lat=$latitude, lon=$longitude, radius=$radiusKm")

                repository.getNearbyReports(latitude, longitude, radiusKm, forceRefresh = true)
                    .onSuccess { newReports ->
                        Log.d(TAG, "Successfully loaded ${newReports.size} reports from Firebase")
                        lastLoadLocation = currentLocation
                        lastLoadTime = currentTime

                        loadUserVotes(newReports.map { it.id })

                        _loading.value = false
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to load reports from Firebase: ${exception.message}")
                        Log.d(TAG, "Using cached data")
                        _error.value = "Using cached data"
                        _loading.value = false
                    }
            }
        } else {
            Log.d(TAG, "Using cached reports, position change too small")
        }
    }

    /**
     * Force refresh - always fetches from Firebase
     */
    fun forceRefresh(latitude: Double, longitude: Double, radiusKm: Double = 100.0) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            Log.d(TAG, "Force refreshing reports")

            repository.getNearbyReports(latitude, longitude, radiusKm, forceRefresh = true)
                .onSuccess { newReports ->
                    Log.d(TAG, "Successfully force loaded ${newReports.size} reports")
                    lastLoadLocation = LatLng(latitude, longitude)
                    lastLoadTime = System.currentTimeMillis()

                    loadUserVotes(newReports.map { it.id })
                    _loading.value = false
                }
                .onFailure { exception ->
                    Log.e(TAG, "Force refresh failed: ${exception.message}")
                    _error.value = exception.message
                    _loading.value = false
                }
        }
    }

    /**
     * Reset load thresholds - forces next loadNearbyReports to refresh
     */
    fun resetLoadThresholds() {
        lastLoadLocation = null
        lastLoadTime = 0L
        Log.d(TAG, "Load thresholds reset - next load will force refresh")
    }

    /**
     * Clear all cached reports and force refresh from Firebase
     */
    fun clearCacheAndRefresh(latitude: Double, longitude: Double, radiusKm: Double = 100.0) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                Log.d(TAG, "Clearing local cache...")
                repository.clearAllReports()
                Log.d(TAG, "Forcing refresh from Firebase...")
                repository.getNearbyReports(latitude, longitude, radiusKm, forceRefresh = true)
                    .onSuccess { newReports ->
                        Log.d(TAG, "Successfully refreshed ${newReports.size} reports")
                        lastLoadLocation = LatLng(latitude, longitude)
                        lastLoadTime = System.currentTimeMillis()

                        loadUserVotes(newReports.map { it.id })
                        _loading.value = false
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to refresh: ${exception.message}")
                        _error.value = exception.message
                        _loading.value = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache: ${e.message}")
                _error.value = e.message
                _loading.value = false
            }
        }
    }

    private fun loadUserVotes(reportIds: List<String>) {
        viewModelScope.launch {
            repository.getUserVotes(reportIds)
                .onSuccess { voted ->
                    _votedReports.value = voted
                    Log.d(TAG, "Loaded ${voted.size} user votes")
                }
        }
    }

    fun submitReport(
        latitude: Double,
        longitude: Double,
        areaName: String,
        safetyLevel: String,
        comment: String,
        radiusMeters: Int = 500
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            Log.d(TAG, "Submitting report: level=$safetyLevel, area=$areaName")

            repository.submitReport(latitude, longitude, areaName, safetyLevel, comment, radiusMeters)
                .onSuccess {
                    Log.d(TAG, "Report submitted successfully")
                    _submitSuccess.value = true
                    _loading.value = false
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to submit report: ${exception.message}")
                    _error.value = exception.message
                    _submitSuccess.value = false
                    _loading.value = false
                }
        }
    }

    fun voteOnReport(reportId: String, isUpvote: Boolean) {
        viewModelScope.launch {
            repository.voteOnReport(reportId, isUpvote)
                .onSuccess {
                    val currentVoted = _votedReports.value?.toMutableSet() ?: mutableSetOf()
                    currentVoted.add(reportId)
                    _votedReports.value = currentVoted

                    Log.d(TAG, "Vote successful for report $reportId")
                }
                .onFailure { exception ->
                    _error.value = exception.message
                    Log.e(TAG, "Vote failed: ${exception.message}")
                }
        }
    }

    fun hasUserVoted(reportId: String): Boolean {
        return _votedReports.value?.contains(reportId) ?: false
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            repository.deleteReport(reportId)
                .onSuccess {
                    Log.d(TAG, "Report deleted successfully")
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }
        }
    }

    fun setFocusedReport(reportId: String?) {
        _focusedReportId.value = reportId
    }

    fun setCenterLocation(latLng: LatLng?) {
        _centerLocation.value = latLng
    }

    fun setMapType(type: Int) {
        _mapType.value = type
    }

    fun clearError() {
        _error.value = null
    }

    fun resetSubmitSuccess() {
        _submitSuccess.value = false
    }

    /**
     * Sync any unsynced reports with Firebase
     */
    fun syncUnsyncedReports() {
        viewModelScope.launch {
            try {
                repository.syncUnsyncedReports()
                    .onSuccess { count ->
                        if (count > 0) {
                            Log.d(TAG, "Synced $count reports with Firebase")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync unsynced reports: ${e.message}")
            }
        }
    }

    /**
     * Clean old cached reports
     */
    private fun cleanOldReports() {
        viewModelScope.launch {
            try {
                repository.cleanOldReports()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean old reports: ${e.message}")
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    companion object {
        private const val TAG = "SafetyViewModel"
    }
}