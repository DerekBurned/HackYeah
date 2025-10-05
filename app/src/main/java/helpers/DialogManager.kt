package com.example.travelnow.helpers

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelnow.SafetyReportAdapter
import com.example.travelnow.databinding.BottomSheetReportsBinding
import com.example.travelnow.databinding.DialogSafetyReportBinding
import com.example.travelnow.databinding.DialogViewReportBinding
import com.example.travelnow.models.SortOptions
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialog
import models.SafetyLevel
import models.SafetyReport

class DialogManager(private val context: Context) {

    private var currentAdapter: SafetyReportAdapter? = null

    fun showSafetyReportDialog(
        latLng: LatLng,
        areaName: String,
        onSubmit: (SafetyLevel, String, Int) -> Unit
    ) {
        Log.d(TAG, "Creating safety report dialog")
        val dialogBinding = DialogSafetyReportBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle("Report Safety Status")
            .setCancelable(true)
            .create()

        with(dialogBinding) {
            tvLocationName.text = areaName

            seekBarRadius.max = 1000
            seekBarRadius.progress = 500
            tvRadiusValue.text = "500m"

            seekBarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val radius = if (progress < 50) 50 else progress
                    tvRadiusValue.text = "${radius}m"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            val submitHandler: (SafetyLevel) -> Unit = { level ->
                val radius = if (seekBarRadius.progress < 50) 50 else seekBarRadius.progress
                val comment = etComment.text.toString()
                if (comment.isNotBlank()) {
                    Log.d(TAG, "Submitting report with level: $level")
                    onSubmit(level, comment, radius)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please add a comment", Toast.LENGTH_SHORT).show()
                }
            }

            btnSafe.setOnClickListener { submitHandler(SafetyLevel.SAFE) }
            btnCautious.setOnClickListener { submitHandler(SafetyLevel.BE_CAUTIOUS) }
            btnUnsafe.setOnClickListener { submitHandler(SafetyLevel.UNSAFE) }
            btnDangerous.setOnClickListener { submitHandler(SafetyLevel.DANGEROUS) }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
        }

        Log.d(TAG, "Showing safety report dialog")
        dialog.show()
    }

    fun showViewReportDialog(
        report: SafetyReport,
        hasVoted: Boolean,
        onUpvote: () -> Unit,
        onDownvote: () -> Unit
    ) {
        val dialogBinding = DialogViewReportBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle("Safety Report")
            .create()

        val safetyLevel = report.getSafetyLevelEnum()

        with(dialogBinding) {
            safetyIndicator.setBackgroundColor(safetyLevel.color)
            tvSafetyLevel.text = safetyLevel.displayName
            tvSafetyLevel.setTextColor(safetyLevel.color)
            tvAreaName.text = report.areaName
            tvComment.text = report.comment
            tvUserName.text = "Reported by: ${report.userName}"
            tvTimestamp.text = report.getFormattedDate()
            tvRadiusInfo.text = "Coverage: ${report.radiusMeters}m radius"

            val voteCount = report.upvotes - report.downvotes
            tvVoteCount.text = voteCount.toString()

            btnUpvote.isEnabled = !hasVoted
            btnDownvote.isEnabled = !hasVoted

            if (hasVoted) {
                btnUpvote.alpha = 0.5f
                btnDownvote.alpha = 0.5f
            }

            btnUpvote.setOnClickListener {
                if (!hasVoted) {
                    onUpvote()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "You have already voted on this report", Toast.LENGTH_SHORT).show()
                }
            }

            btnDownvote.setOnClickListener {
                if (!hasVoted) {
                    onDownvote()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "You have already voted on this report", Toast.LENGTH_SHORT).show()
                }
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    fun showReportsBottomSheet(
        reports: List<SafetyReport>,
        votedReportIds: Set<String>,
        currentSort: SortOptions,
        onSort: (SortOptions) -> Unit,
        onUpvote: (SafetyReport) -> Unit,
        onDownvote: (SafetyReport) -> Unit,
        onItemClick: (SafetyReport) -> Unit
    ): BottomSheetDialog {
        Log.d(TAG, "Creating bottom sheet with ${reports.size} reports")
        val bottomSheetDialog = BottomSheetDialog(context)
        val sheetBinding = BottomSheetReportsBinding.inflate(LayoutInflater.from(context))

        if (currentAdapter == null) {
            currentAdapter = SafetyReportAdapter(
                onUpvoteClick = onUpvote,
                onDownvoteClick = onDownvote,
                onItemClick = { report ->
                    onItemClick(report)
                    bottomSheetDialog.dismiss()
                }
            )
        }

        currentAdapter?.let { adapter ->
            adapter.setVotedReports(votedReportIds)

            with(sheetBinding) {
                recyclerViewReports.layoutManager = LinearLayoutManager(context)
                recyclerViewReports.adapter = adapter

                tvSortInfo.text = "Sorted by: ${getSortName(currentSort)}"

                if (reports.isEmpty()) {
                    Log.d(TAG, "No reports to display")
                    recyclerViewReports.visibility = android.view.View.GONE
                    tvNoReports.visibility = android.view.View.VISIBLE
                } else {
                    Log.d(TAG, "Displaying ${reports.size} reports")
                    recyclerViewReports.visibility = android.view.View.VISIBLE
                    tvNoReports.visibility = android.view.View.GONE
                    adapter.updateReports(reports)
                }

                btnSort.setOnClickListener {
                    showSortDialog(currentSort) { sortOption ->
                        onSort(sortOption)
                        bottomSheetDialog.dismiss()
                    }
                }

                btnClose.setOnClickListener {
                    bottomSheetDialog.dismiss()
                }
            }
        }

        bottomSheetDialog.setContentView(sheetBinding.root)
        return bottomSheetDialog
    }

    fun showSortDialog(currentSort: SortOptions, onSelected: (SortOptions) -> Unit) {
        val options = arrayOf(
            "Most Dangerous First",
            "Safest First",
            "Newest First",
            "Oldest First",
            "Highest Votes",
            "Lowest Votes"
        )

        AlertDialog.Builder(context)
            .setTitle("Sort Reports By")
            .setItems(options) { _, which ->
                val sortOption = when (which) {
                    0 -> SortOptions.DANGER_LEVEL_DESC
                    1 -> SortOptions.DANGER_LEVEL_ASC
                    2 -> SortOptions.TIME_NEWEST
                    3 -> SortOptions.TIME_OLDEST
                    4 -> SortOptions.VOTES_HIGH
                    5 -> SortOptions.VOTES_LOW
                    else -> SortOptions.DANGER_LEVEL_DESC
                }
                onSelected(sortOption)
            }
            .show()
    }

    fun showMapTypeDialog(currentType: Int, onSelected: (Int) -> Unit) {
        val options = arrayOf("Normal", "Satellite", "Terrain", "Hybrid")
        AlertDialog.Builder(context)
            .setTitle("Select Map Type")
            .setSingleChoiceItems(options, currentType) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun getSortName(sortOption: SortOptions): String {
        return when (sortOption) {
            SortOptions.DANGER_LEVEL_DESC -> "Most Dangerous First"
            SortOptions.DANGER_LEVEL_ASC -> "Safest First"
            SortOptions.TIME_NEWEST -> "Newest First"
            SortOptions.TIME_OLDEST -> "Oldest First"
            SortOptions.VOTES_HIGH -> "Highest Votes"
            SortOptions.VOTES_LOW -> "Lowest Votes"
        }
    }

    companion object {
        private const val TAG = "DialogManager"
    }
}