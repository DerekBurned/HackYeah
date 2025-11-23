package local


import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyReportDao {

    @Query("SELECT * FROM safety_reports ORDER BY timestamp DESC")
    fun getAllReportsFlow(): Flow<List<SafetyReportEntity>>

    @Query("SELECT * FROM safety_reports ORDER BY timestamp DESC")
    suspend fun getAllReports(): List<SafetyReportEntity>

    @Query("""
        SELECT * FROM safety_reports 
        WHERE geohash >= :lowerBound AND geohash <= :upperBound 
        ORDER BY timestamp DESC
    """)
    suspend fun getNearbyReports(lowerBound: String, upperBound: String): List<SafetyReportEntity>

    @Query("SELECT * FROM safety_reports WHERE id = :reportId")
    suspend fun getReportById(reportId: String): SafetyReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: SafetyReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<SafetyReportEntity>)

    @Update
    suspend fun updateReport(report: SafetyReportEntity)

    @Query("DELETE FROM safety_reports WHERE id = :reportId")
    suspend fun deleteReportById(reportId: String)

    @Query("DELETE FROM safety_reports")
    suspend fun deleteAllReports()

    @Query("SELECT * FROM safety_reports WHERE syncedWithFirebase = 0")
    suspend fun getUnsyncedReports(): List<SafetyReportEntity>

    @Query("UPDATE safety_reports SET syncedWithFirebase = 1 WHERE id = :reportId")
    suspend fun markAsSynced(reportId: String)

    @Query("""
        UPDATE safety_reports 
        SET upvotes = :upvotes, downvotes = :downvotes 
        WHERE id = :reportId
    """)
    suspend fun updateVotes(reportId: String, upvotes: Int, downvotes: Int)

    @Query("DELETE FROM safety_reports WHERE timestamp < :timestamp")
    suspend fun deleteOldReports(timestamp: Long)

    @Query("SELECT COUNT(*) FROM safety_reports")
    suspend fun getReportCount(): Int
}