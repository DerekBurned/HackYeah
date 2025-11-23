package repository

import android.content.Context
import android.util.Log
import com.example.travelnow.database.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import models.SafetyReport
import utils.GeoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import local.SafetyReportEntity

class SafetyRepository(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val reportsCollection = db.collection("safety_reports")
    private val votesCollection = db.collection("votes")

    private val database = AppDatabase.getDatabase(context)
    private val reportDao = database.safetyReportDao()

    private suspend fun ensureAuthenticated(): Boolean {
        return try {
            if (auth.currentUser == null) {
                Log.d(TAG, "No user found, signing in anonymously...")
                auth.signInAnonymously().await()
                Log.d(TAG, "Signed in anonymously with UID: ${auth.currentUser?.uid}")
            } else {
                Log.d(TAG, "Already authenticated with UID: ${auth.currentUser?.uid}")
            }
            auth.currentUser != null
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            false
        }
    }

    fun getReportsFlow(): Flow<List<SafetyReport>> {
        return reportDao.getAllReportsFlow().map { entities ->
            entities.map { it.toSafetyReport() }
        }
    }

    suspend fun submitReport(
        latitude: Double,
        longitude: Double,
        areaName: String,
        safetyLevel: String,
        comment: String,
        radiusMeters: Int = 500
    ): Result<String> {
        return try {
            Log.d(TAG, "Attempting to submit report...")

            if (!ensureAuthenticated()) {
                Log.e(TAG, "Authentication failed")
                return Result.failure(Exception("Authentication failed. Please try again."))
            }

            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "User ID is null after authentication")
                return Result.failure(Exception("Not authenticated"))
            }

            // Use precision 7 for storage
            val geohash = GeoUtils.encode(latitude, longitude, 7)
            val tempId = "temp_${System.currentTimeMillis()}"

            val entity = SafetyReportEntity(
                id = tempId,
                latitude = latitude,
                longitude = longitude,
                areaName = areaName,
                safetyLevel = safetyLevel,
                comment = comment,
                userId = userId,
                userName = "Anonymous User",
                upvotes = 0,
                downvotes = 0,
                radiusMeters = radiusMeters,
                geohash = geohash,
                timestamp = System.currentTimeMillis(),
                syncedWithFirebase = false
            )

            reportDao.insertReport(entity)
            Log.d(TAG, "Report saved to local database")

            try {
                val report = hashMapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "areaName" to areaName,
                    "safetyLevel" to safetyLevel,
                    "comment" to comment,
                    "userId" to userId,
                    "userName" to "Anonymous User",
                    "upvotes" to 0,
                    "downvotes" to 0,
                    "radiusMeters" to radiusMeters,
                    "geohash" to geohash,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                val documentRef = reportsCollection.add(report).await()
                Log.d(TAG, "Report submitted to Firebase with ID: ${documentRef.id}")

                reportDao.deleteReportById(tempId)
                val syncedEntity = entity.copy(
                    id = documentRef.id,
                    syncedWithFirebase = true
                )
                reportDao.insertReport(syncedEntity)

                Result.success(documentRef.id)
            } catch (e: Exception) {
                Log.e(TAG, "Firebase sync failed, but report saved locally: ${e.message}")
                Result.success(tempId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting report: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * FIXED: Get nearby reports with better geohash handling
     */
    suspend fun getNearbyReports(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 100.0,
        forceRefresh: Boolean = false
    ): Result<List<SafetyReport>> {
        return try {
            Log.d(TAG, "========== FETCHING NEARBY REPORTS ==========")
            Log.d(TAG, "Location: lat=$latitude, lon=$longitude")
            Log.d(TAG, "Radius: $radiusKm km")
            Log.d(TAG, "Force refresh: $forceRefresh")

            if (forceRefresh) {
                try {
                    val reports = fetchFromFirebaseWithBetterGeohash(latitude, longitude, radiusKm)

                    Log.d(TAG, "✓ Found ${reports.size} reports from Firebase")

                    // Save to Room database
                    if (reports.isNotEmpty()) {
                        val entities = reports.map {
                            SafetyReportEntity.fromSafetyReport(it, syncedWithFirebase = true)
                        }
                        reportDao.insertReports(entities)
                        Log.d(TAG, "✓ Cached ${entities.size} reports to local database")
                    }

                    return Result.success(reports)
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Firebase fetch failed: ${e.message}", e)
                    Log.d(TAG, "Falling back to local cache...")
                }
            }

            // Fallback to local cache
            val cachedReports = fetchFromLocalCache(latitude, longitude, radiusKm)
            Log.d(TAG, "Found ${cachedReports.size} reports from local cache")
            Result.success(cachedReports)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reports: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * IMPROVED: Fetch from Firebase with better geohash handling
     */
    private suspend fun fetchFromFirebaseWithBetterGeohash(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<SafetyReport> {
        Log.d(TAG, "--- Fetching from Firebase ---")

        // Get geohash bounds
        val bounds = GeoUtils.getGeohashBounds(latitude, longitude, radiusKm)
        Log.d(TAG, "Geohash bounds: ${bounds.first} to ${bounds.second}")

        // OPTION 1: Query with geohash bounds (may miss some reports)
        val snapshot = reportsCollection
            .whereGreaterThanOrEqualTo("geohash", bounds.first)
            .whereLessThanOrEqualTo("geohash", bounds.second)
            .orderBy("geohash")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        Log.d(TAG, "Firebase returned ${snapshot.documents.size} documents")

        val reports = mutableListOf<SafetyReport>()
        for (document in snapshot.documents) {
            try {
                val report = document.toObject(SafetyReport::class.java)
                if (report != null) {
                    // Calculate actual distance
                    val distance = GeoUtils.calculateDistance(
                        latitude, longitude,
                        report.latitude, report.longitude
                    )

                    Log.d(TAG, "Report ${report.id}: distance = ${String.format("%.2f", distance)} km")

                    if (distance <= radiusKm) {
                        reports.add(report)
                        Log.d(TAG, "  ✓ Added report ${report.id}")
                    } else {
                        Log.d(TAG, "  ✗ Skipped (too far)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing report: ${e.message}")
            }
        }

        Log.d(TAG, "Final count after distance filter: ${reports.size} reports")
        return reports
    }

    /**
     * OPTION 2: If geohash queries aren't working, fetch ALL and filter by distance
     * Use this as a fallback or for debugging
     */
    private suspend fun fetchAllFromFirebaseAndFilter(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<SafetyReport> {
        Log.d(TAG, "--- FALLBACK: Fetching ALL reports from Firebase ---")

        val snapshot = reportsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(500) // Limit to prevent excessive data
            .get()
            .await()

        Log.d(TAG, "Firebase returned ${snapshot.documents.size} total documents")

        val reports = mutableListOf<SafetyReport>()
        for (document in snapshot.documents) {
            try {
                val report = document.toObject(SafetyReport::class.java)
                if (report != null) {
                    val distance = GeoUtils.calculateDistance(
                        latitude, longitude,
                        report.latitude, report.longitude
                    )

                    if (distance <= radiusKm) {
                        reports.add(report)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing report: ${e.message}")
            }
        }

        Log.d(TAG, "Filtered to ${reports.size} reports within ${radiusKm}km")
        return reports
    }

    private suspend fun fetchFromLocalCache(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<SafetyReport> {
        // Get all cached reports
        val allCached = reportDao.getAllReports()

        // Filter by distance
        return allCached
            .map { it.toSafetyReport() }
            .filter { report ->
                val distance = GeoUtils.calculateDistance(
                    latitude, longitude,
                    report.latitude, report.longitude
                )
                distance <= radiusKm
            }
    }

    suspend fun syncUnsyncedReports(): Result<Int> {
        return try {
            val unsyncedReports = reportDao.getUnsyncedReports()
            Log.d(TAG, "Found ${unsyncedReports.size} unsynced reports")

            var syncedCount = 0
            for (entity in unsyncedReports) {
                try {
                    val report = hashMapOf(
                        "latitude" to entity.latitude,
                        "longitude" to entity.longitude,
                        "areaName" to entity.areaName,
                        "safetyLevel" to entity.safetyLevel,
                        "comment" to entity.comment,
                        "userId" to entity.userId,
                        "userName" to entity.userName,
                        "upvotes" to entity.upvotes,
                        "downvotes" to entity.downvotes,
                        "radiusMeters" to entity.radiusMeters,
                        "geohash" to entity.geohash,
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    val documentRef = reportsCollection.add(report).await()

                    reportDao.deleteReportById(entity.id)
                    val syncedEntity = entity.copy(
                        id = documentRef.id,
                        syncedWithFirebase = true
                    )
                    reportDao.insertReport(syncedEntity)
                    syncedCount++

                    Log.d(TAG, "Synced report ${entity.id} -> ${documentRef.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync report ${entity.id}: ${e.message}")
                }
            }

            Log.d(TAG, "Synced $syncedCount out of ${unsyncedReports.size} reports")
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing reports: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun voteOnReport(reportId: String, isUpvote: Boolean): Result<Unit> {
        return try {
            Log.d(TAG, "Voting on report $reportId (upvote: $isUpvote)")

            if (!ensureAuthenticated()) {
                return Result.failure(Exception("Not authenticated"))
            }

            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))

            val voteId = "${userId}_${reportId}"
            val voteRef = votesCollection.document(voteId)

            val existingVote = voteRef.get().await()
            if (existingVote.exists()) {
                Log.d(TAG, "User has already voted on this report")
                return Result.failure(Exception("You have already voted on this report"))
            }

            val reportRef = reportsCollection.document(reportId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(reportRef)
                if (!snapshot.exists()) {
                    throw Exception("Report not found")
                }

                val field = if (isUpvote) "upvotes" else "downvotes"
                val currentValue = snapshot.getLong(field) ?: 0
                transaction.update(reportRef, field, currentValue + 1)

                transaction.set(voteRef, hashMapOf(
                    "userId" to userId,
                    "reportId" to reportId,
                    "isUpvote" to isUpvote,
                    "timestamp" to FieldValue.serverTimestamp()
                ))
            }.await()

            val localReport = reportDao.getReportById(reportId)
            if (localReport != null) {
                val newUpvotes = if (isUpvote) localReport.upvotes + 1 else localReport.upvotes
                val newDownvotes = if (!isUpvote) localReport.downvotes + 1 else localReport.downvotes
                reportDao.updateVotes(reportId, newUpvotes, newDownvotes)
            }

            Log.d(TAG, "Vote recorded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error voting on report: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun hasUserVoted(reportId: String): Result<Boolean> {
        return try {
            if (!ensureAuthenticated()) {
                return Result.success(false)
            }

            val userId = auth.currentUser?.uid ?: return Result.success(false)
            val voteId = "${userId}_${reportId}"
            val voteRef = votesCollection.document(voteId)

            val vote = voteRef.get().await()
            Result.success(vote.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking vote: ${e.message}", e)
            Result.success(false)
        }
    }

    suspend fun getUserVotes(reportIds: List<String>): Result<Set<String>> {
        return try {
            if (!ensureAuthenticated()) {
                return Result.success(emptySet())
            }

            val userId = auth.currentUser?.uid ?: return Result.success(emptySet())
            val votedReports = mutableSetOf<String>()

            for (reportId in reportIds) {
                val voteId = "${userId}_${reportId}"
                val voteRef = votesCollection.document(voteId)
                val vote = voteRef.get().await()
                if (vote.exists()) {
                    votedReports.add(reportId)
                }
            }

            Log.d(TAG, "Found ${votedReports.size} voted reports")
            Result.success(votedReports)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user votes: ${e.message}", e)
            Result.success(emptySet())
        }
    }

    suspend fun deleteReport(reportId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to delete report $reportId")

            if (!ensureAuthenticated()) {
                return Result.failure(Exception("Not authenticated"))
            }

            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Not authenticated"))

            val localReport = reportDao.getReportById(reportId)
            if (localReport != null && localReport.userId == userId) {
                reportDao.deleteReportById(reportId)

                if (localReport.syncedWithFirebase) {
                    try {
                        val report = reportsCollection.document(reportId).get().await()
                        if (report.exists()) {
                            reportsCollection.document(reportId).delete().await()
                            votesCollection
                                .whereEqualTo("reportId", reportId)
                                .get()
                                .await()
                                .documents
                                .forEach { it.reference.delete() }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete from Firebase, but removed locally: ${e.message}")
                    }
                }

                Log.d(TAG, "Report deleted successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Unauthorized delete attempt or report not found")
                Result.failure(Exception("You can only delete your own reports"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting report: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun cleanOldReports() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            reportDao.deleteOldReports(thirtyDaysAgo)
            Log.d(TAG, "Cleaned old reports")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old reports: ${e.message}")
        }
    }

    /**
     * Clear all reports from local cache
     */
    suspend fun clearAllReports() {
        try {
            reportDao.deleteAllReports()
            Log.d(TAG, "Cleared all reports from local database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing reports: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SafetyRepository"
    }
}