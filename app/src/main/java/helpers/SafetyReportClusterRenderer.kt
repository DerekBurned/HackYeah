package helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.travelnow.models.SafetyReportClusterItem
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.android.gms.maps.model.Marker
import models.SafetyLevel

class SafetyReportClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<SafetyReportClusterItem>
) : DefaultClusterRenderer<SafetyReportClusterItem>(context, map, clusterManager) {

    override fun onBeforeClusterItemRendered(
        item: SafetyReportClusterItem,
        markerOptions: MarkerOptions
    ) {
        val safetyLevel = item.report.getSafetyLevelEnum()
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(getMarkerHue(safetyLevel)))
            .title(item.title)
            .snippet(item.snippet)
    }

    override fun onClusterItemUpdated(item: SafetyReportClusterItem, marker: Marker) {
        val safetyLevel = item.report.getSafetyLevelEnum()
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(getMarkerHue(safetyLevel)))
        marker.title = item.title
        marker.snippet = item.snippet
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<SafetyReportClusterItem>,
        markerOptions: MarkerOptions
    ) {
        val dominantLevel = getDominantSafetyLevel(cluster)
        val clusterSize = cluster.size

        markerOptions.icon(getClusterIcon(clusterSize, dominantLevel))
            .title("${clusterSize} safety reports")
            .snippet("Tap to zoom in")
    }

    override fun shouldRenderAsCluster(cluster: Cluster<SafetyReportClusterItem>): Boolean {
        return cluster.size > 1
    }

    private fun getDominantSafetyLevel(cluster: Cluster<SafetyReportClusterItem>): SafetyLevel {
        val levelCounts = mutableMapOf<SafetyLevel, Int>()

        cluster.items.forEach { item ->
            val level = item.report.getSafetyLevelEnum()
            levelCounts[level] = (levelCounts[level] ?: 0) + 1
        }

        return levelCounts.maxByOrNull { (level, count) ->
            count * 100 + level.ordinal
        }?.key ?: SafetyLevel.UNKNOWN
    }

    private fun getClusterIcon(clusterSize: Int, safetyLevel: SafetyLevel): BitmapDescriptor {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            color = safetyLevel.color
            style = Paint.Style.FILL
        }

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius * 0.9f, paint)

        paint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(radius, radius, radius * 0.9f, paint)

        paint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = if (clusterSize < 100) 32f else 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val text = if (clusterSize < 1000) clusterSize.toString() else "999+"
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        canvas.drawText(
            text,
            radius,
            radius + (textBounds.height() / 2f),
            paint
        )

        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
}