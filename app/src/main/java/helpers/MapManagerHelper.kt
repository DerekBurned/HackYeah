package com.example.travelnow.helpers

import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import models.SafetyLevel
import models.SafetyReport

class MapManagerHelper(private val map: GoogleMap) {

    private val reportMarkers = mutableMapOf<String, Marker>()
    private val reportCircles = mutableMapOf<String, Circle>()
    private var currentMarker: Marker? = null
    private var focusedReportId: String? = null

    fun addReportToMap(report: SafetyReport) {
        val latLng = LatLng(report.latitude, report.longitude)
        val safetyLevel = report.getSafetyLevelEnum()
        val circleRadius = if (report.radiusMeters > 0) {
            report.radiusMeters.toDouble()
        } else {
            500.0
        }

        val circle = map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(circleRadius)
                .strokeWidth(3f)
                .strokeColor(safetyLevel.color)
                .fillColor(safetyLevel.colorWithAlpha)
                .clickable(true)
                .visible(focusedReportId == null || focusedReportId == report.id)
        )

        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(safetyLevel.displayName)
                .snippet(report.comment)
                .icon(BitmapDescriptorFactory.defaultMarker(getMarkerHue(safetyLevel)))
        )

        marker?.tag = report.id
        if (marker != null) {
            reportMarkers[report.id] = marker
            reportCircles[report.id] = circle
        }
    }

    fun updateReports(reports: List<SafetyReport>) {
        val newReportIds = reports.map { it.id }.toSet()
        val currentReportIds = reportMarkers.keys.toSet()

        // Remove old markers
        val toRemove = currentReportIds - newReportIds
        toRemove.forEach { reportId ->
            removeReport(reportId)
        }

        // Add new markers
        reports.forEach { report ->
            if (report.id !in currentReportIds) {
                addReportToMap(report)
            }
        }

        updateCircleVisibility()
        Log.d(TAG, "Updated ${reports.size} reports on map")
    }

    fun removeReport(reportId: String) {
        reportMarkers[reportId]?.remove()
        reportCircles[reportId]?.remove()
        reportMarkers.remove(reportId)
        reportCircles.remove(reportId)
    }

    fun clearAllReports() {
        reportMarkers.values.forEach { it.remove() }
        reportCircles.values.forEach { it.remove() }
        reportMarkers.clear()
        reportCircles.clear()
        focusedReportId = null
    }

    fun setFocusedReport(reportId: String?) {
        focusedReportId = if (focusedReportId == reportId) null else reportId
        updateCircleVisibility()

        reportId?.let {
            reportMarkers[it]?.let { marker ->
                animateToPosition(marker.position, 15f)
            }
        }
    }

    fun getFocusedReportId(): String? = focusedReportId

    fun getMarkerPosition(reportId: String): LatLng? {
        return reportMarkers[reportId]?.position
    }

    fun addTemporaryMarker(latLng: LatLng, title: String, snippet: String): Marker? {
        currentMarker?.remove()
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet)
                .draggable(true)
        )
        return currentMarker
    }

    fun removeTemporaryMarker() {
        currentMarker?.remove()
        currentMarker = null
    }

    fun animateToPosition(latLng: LatLng, zoom: Float) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    fun setMapType(type: Int) {
        map.mapType = type
    }

    fun zoomIn() {
        map.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        map.animateCamera(CameraUpdateFactory.zoomOut())
    }

    fun getCurrentCameraPosition(): LatLng {
        return map.cameraPosition.target
    }

    fun getCurrentZoom(): Float {
        return map.cameraPosition.zoom
    }

    private fun updateCircleVisibility() {
        reportCircles.forEach { (reportId, circle) ->
            circle.isVisible = focusedReportId == null || focusedReportId == reportId
        }
    }

    private fun getMarkerHue(safetyLevel: SafetyLevel): Float {
        return when (safetyLevel) {
            SafetyLevel.SAFE -> BitmapDescriptorFactory.HUE_GREEN
            SafetyLevel.BE_CAUTIOUS -> BitmapDescriptorFactory.HUE_YELLOW
            SafetyLevel.UNSAFE -> BitmapDescriptorFactory.HUE_ORANGE
            SafetyLevel.DANGEROUS -> BitmapDescriptorFactory.HUE_RED
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
    }

    companion object {
        private const val TAG = "MapManagerHelper"
    }
}