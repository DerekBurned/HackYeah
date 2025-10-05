package com.example.travelnow.helpers

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.clustering.ClusterManager
import com.example.travelnow.models.SafetyReportClusterItem
import helpers.SafetyReportClusterRenderer
import models.SafetyLevel
import models.SafetyReport

class MapManagerHelper(
    private val map: GoogleMap,
    private val context: Context
) {

    private val clusterManager: ClusterManager<SafetyReportClusterItem>
    private val reportCircles = mutableMapOf<String, Circle>()
    private val reportItems = mutableMapOf<String, SafetyReportClusterItem>()
    private var currentMarker: Marker? = null
    private var focusedReportId: String? = null

    var onReportClickListener: ((String) -> Unit)? = null

    init {
        clusterManager = ClusterManager(context, map)

        val renderer = SafetyReportClusterRenderer(context, map, clusterManager)
        clusterManager.renderer = renderer

        // IMPORTANT: Don't set these listeners here, they're set in MapsActivity
        // map.setOnCameraIdleListener(clusterManager)
        // map.setOnMarkerClickListener(clusterManager)

        clusterManager.setOnClusterItemClickListener { item ->
            onReportClickListener?.invoke(item.report.id)
            true
        }

        clusterManager.setOnClusterClickListener { cluster ->
            val builder = LatLngBounds.builder()
            cluster.items.forEach { item ->
                builder.include(item.position)
            }
            val bounds = builder.build()

            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {
                Log.e(TAG, "Error zooming to cluster: ${e.message}")
            }
            true
        }
    }

    fun addReportToMap(report: SafetyReport) {
        val latLng = LatLng(report.latitude, report.longitude)
        val safetyLevel = report.getSafetyLevelEnum()
        val circleRadius = if (report.radiusMeters > 0) {
            report.radiusMeters.toDouble()
        } else {
            500.0
        }

        Log.d(TAG, "Adding report to map: ${report.id} at $latLng with radius $circleRadius")

        val circle = map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(circleRadius)
                .strokeWidth(3f)
                .strokeColor(safetyLevel.color)
                .fillColor(safetyLevel.colorWithAlpha)
                .clickable(false)
                .visible(true)
                .zIndex(0.5f)
        )

        val clusterItem = SafetyReportClusterItem(report)
        clusterManager.addItem(clusterItem)

        reportCircles[report.id] = circle
        reportItems[report.id] = clusterItem

        Log.d(TAG, "Successfully added report ${report.id} to cluster manager")
    }

    fun updateReports(reports: List<SafetyReport>) {
        Log.d(TAG, "Updating reports on map. Received ${reports.size} reports")

        // Don't clear everything if reports are similar
        val newReportIds = reports.map { it.id }.toSet()
        val currentReportIds = reportItems.keys.toSet()

        val toRemove = currentReportIds - newReportIds
        val toAdd = reports.filter { it.id !in currentReportIds }

        // Only update if there are actual changes
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            Log.d(TAG, "No changes detected, skipping update")
            return
        }

        Log.d(TAG, "Removing ${toRemove.size} old reports, adding ${toAdd.size} new reports")

        toRemove.forEach { reportId ->
            removeReport(reportId)
        }

        toAdd.forEach { report ->
            addReportToMap(report)
        }

        // Only trigger cluster recalculation if something changed
        clusterManager.cluster()
    }

    fun removeReport(reportId: String) {
        reportItems[reportId]?.let { item ->
            clusterManager.removeItem(item)
        }
        reportCircles[reportId]?.remove()
        reportItems.remove(reportId)
        reportCircles.remove(reportId)
        Log.d(TAG, "Removed report: $reportId")
    }

    fun clearAllReports() {
        clusterManager.clearItems()
        reportCircles.values.forEach { it.remove() }
        reportItems.clear()
        reportCircles.clear()
        focusedReportId = null
        clusterManager.cluster()
        Log.d(TAG, "Cleared all reports from map")
    }

    fun setFocusedReport(reportId: String?) {
        focusedReportId = if (focusedReportId == reportId) null else reportId

        reportId?.let {
            reportItems[it]?.let { item ->
                animateToPosition(item.position, 15f)
            }
        }
    }



    fun addTemporaryMarker(latLng: LatLng, title: String, snippet: String): Marker? {
        currentMarker?.remove()
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet)
                .draggable(true)
                .zIndex(10f)
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
        Log.d(TAG, "Map type changed to: $type")
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

    fun onCameraIdle() {
        clusterManager.onCameraIdle()
    }

    fun getClusterManager(): ClusterManager<SafetyReportClusterItem> {
        return clusterManager
    }

    companion object {
        private const val TAG = "MapManagerHelper"
    }
}