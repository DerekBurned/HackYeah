package com.example.travelnow.models

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import models.SafetyReport

class SafetyReportClusterItem(
    val report: SafetyReport
) : ClusterItem {

    private val position = LatLng(report.latitude, report.longitude)

    override fun getPosition(): LatLng = position

    override fun getTitle(): String = report.getSafetyLevelEnum().displayName

    override fun getSnippet(): String = report.comment

    override fun getZIndex(): Float = 1f
}