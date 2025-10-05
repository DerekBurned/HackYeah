package com.example.travelnow.helpers

import com.example.travelnow.models.SortOptions
import models.SafetyReport

object ReportSorter {

    fun sort(reports: List<SafetyReport>, sortOption: SortOptions): List<SafetyReport> {
        return when (sortOption) {
            SortOptions.DANGER_LEVEL_DESC -> sortByDangerDesc(reports)
            SortOptions.DANGER_LEVEL_ASC -> sortByDangerAsc(reports)
            SortOptions.TIME_NEWEST -> sortByTimeNewest(reports)
            SortOptions.TIME_OLDEST -> sortByTimeOldest(reports)
            SortOptions.VOTES_HIGH -> sortByVotesHigh(reports)
            SortOptions.VOTES_LOW -> sortByVotesLow(reports)
        }
    }

    private fun sortByDangerDesc(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedWith(
            compareByDescending<SafetyReport> { it.getSafetyLevelEnum().ordinal }
                .thenByDescending { it.timestamp }
        )
    }

    private fun sortByDangerAsc(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedWith(
            compareBy<SafetyReport> { it.getSafetyLevelEnum().ordinal }
                .thenByDescending { it.timestamp }
        )
    }

    private fun sortByTimeNewest(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedByDescending { it.timestamp }
    }

    private fun sortByTimeOldest(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedBy { it.timestamp }
    }

    private fun sortByVotesHigh(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedByDescending { it.upvotes - it.downvotes }
    }

    private fun sortByVotesLow(reports: List<SafetyReport>): List<SafetyReport> {
        return reports.sortedBy { it.upvotes - it.downvotes }
    }
}