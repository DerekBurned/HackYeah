package local

import androidx.room.Entity
import androidx.room.PrimaryKey
import models.SafetyReport
import java.util.*

@Entity(tableName = "safety_reports")
data class SafetyReportEntity(
    @PrimaryKey
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val areaName: String,
    val safetyLevel: String,
    val comment: String,
    val userId: String,
    val userName: String,
    val upvotes: Int,
    val downvotes: Int,
    val radiusMeters: Int,
    val geohash: String,
    val timestamp: Long, // Store as Long (milliseconds)
    val syncedWithFirebase: Boolean = true,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun toSafetyReport(): SafetyReport {
        return SafetyReport(
            id = id,
            latitude = latitude,
            longitude = longitude,
            areaName = areaName,
            safetyLevel = safetyLevel,
            comment = comment,
            userId = userId,
            userName = userName,
            upvotes = upvotes,
            downvotes = downvotes,
            radiusMeters = radiusMeters,
            geohash = geohash,
            timestamp = if (timestamp > 0) Date(timestamp) else null
        )
    }

    companion object {
        fun fromSafetyReport(report: SafetyReport, syncedWithFirebase: Boolean = true): SafetyReportEntity {
            return SafetyReportEntity(
                id = report.id,
                latitude = report.latitude,
                longitude = report.longitude,
                areaName = report.areaName,
                safetyLevel = report.safetyLevel,
                comment = report.comment,
                userId = report.userId,
                userName = report.userName,
                upvotes = report.upvotes,
                downvotes = report.downvotes,
                radiusMeters = report.radiusMeters,
                geohash = report.geohash,
                timestamp = report.timestamp?.time ?: System.currentTimeMillis(),
                syncedWithFirebase = syncedWithFirebase
            )
        }
    }
}