package ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import models.SafetyReport
import kotlinx.coroutines.launch
import repository.SafetyRepository


class SafetyViewModel : ViewModel() {
    private val repository = SafetyRepository()

    private val _reports = MutableLiveData<List<SafetyReport>>()
    val reports: LiveData<List<SafetyReport>> = _reports

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

    private var currentReportIds = emptySet<String>()
    private var isLoadingData = false
    private var lastLoadLocation: LatLng? = null
    private val loadDistanceThreshold = 5.0

    fun loadNearbyReports(latitude: Double, longitude: Double, radiusKm: Double = 50.0) {
        val currentLocation = LatLng(latitude, longitude)

        if (isLoadingData) {
            Log.d(TAG, "Already loading data, skipping")
            return
        }

        lastLoadLocation?.let { lastLoc ->
            val distance = calculateDistance(
                lastLoc.latitude, lastLoc.longitude,
                latitude, longitude
            )

        }

        viewModelScope.launch {
            isLoadingData = true
            _loading.value = true
            _error.value = null

            Log.d(TAG, "Loading nearby reports for lat=$latitude, lon=$longitude, radius=$radiusKm")

            repository.getNearbyReports(latitude, longitude, radiusKm)
                .onSuccess { newReports ->
                    Log.d(TAG, "Successfully loaded ${newReports.size} reports")
                    _reports.value = newReports
                    currentReportIds = newReports.map { it.id }.toSet()
                    lastLoadLocation = currentLocation

                    loadUserVotes(newReports.map { it.id })

                    _loading.value = false
                    isLoadingData = false
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to load reports: ${exception.message}")
                    _error.value = exception.message
                    _loading.value = false
                    isLoadingData = false
                }
        }
    }

    fun loadRecentReports() {
        if (isLoadingData) return

        viewModelScope.launch {
            isLoadingData = true
            _loading.value = true
            _error.value = null

            repository.getRecentReports()
                .onSuccess { reports ->
                    _reports.value = reports
                    currentReportIds = reports.map { it.id }.toSet()

                    loadUserVotes(reports.map { it.id })

                    _loading.value = false
                    isLoadingData = false
                }
                .onFailure { exception ->
                    _error.value = exception.message
                    _loading.value = false
                    isLoadingData = false
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
                    forceRefresh(latitude, longitude)
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
                    // Update local state
                    val currentReports = _reports.value ?: return@onSuccess
                    val updatedReports = currentReports.map { report ->
                        if (report.id == reportId) {
                            if (isUpvote) {
                                report.copy(upvotes = report.upvotes + 1)
                            } else {
                                report.copy(downvotes = report.downvotes + 1)
                            }
                        } else {
                            report
                        }
                    }
                    _reports.value = updatedReports

                    // Mark as voted
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
                    _reports.value = _reports.value?.filter { it.id != reportId }
                    currentReportIds = _reports.value?.map { it.id }?.toSet() ?: emptySet()
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

    fun forceRefresh(latitude: Double, longitude: Double, radiusKm: Double = 50.0) {
        currentReportIds = emptySet()
        isLoadingData = false
        lastLoadLocation = null
        loadNearbyReports(latitude, longitude, radiusKm)
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