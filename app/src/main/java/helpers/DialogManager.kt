// ========== DialogManager.kt ==========
package com.example.travelnow.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelnow.adapters.SafetyReportAdapter
import com.example.travelnow.databinding.BottomSheetReportsBinding
import com.example.travelnow.databinding.DialogSafetyReportBinding
import com.example.travelnow.databinding.DialogViewReportBinding
import com.example.travelnow.models.SortOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialog
import models.SafetyLevel
import models.SafetyReport

class DialogManager(private val context: Context) {

    companion object {
        private const val TAG = "DialogManager"
    }

    fun showGpsSettingsDialog() {
        AlertDialog.Builder(context)
            .setTitle("GPS Disabled")
            .setMessage("GPS is required to report safety status. Please enable location services to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }

    fun showDistanceTooFarDialog(distance: Double) {
        AlertDialog.Builder(context)
            .setTitle("Location Too Far")
            .setMessage("You can only report locations within 100km of your current position.\n\nSelected location is ${String.format("%.1f", distance)}km away.")
            .setPositiveButton("OK", null)
            .show()
    }

    fun showSafetyReportDialog(
        latLng: LatLng,
        areaName: String,
        onSubmit: (SafetyLevel, String, Int) -> Unit
    ) {
        val dialogBinding = DialogSafetyReportBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle("Report Safety Status")
            .create()

        with(dialogBinding) {
            tvLocationName.text = areaName

            val radiusMeters = seekBarRadius.progress
            tvRadiusValue.text = "$radiusMeters meters"

            seekBarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvRadiusValue.text = "$progress meters"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            btnSafe.setOnClickListener {
                onSubmit(SafetyLevel.SAFE, etComment.text.toString(), seekBarRadius.progress)
                dialog.dismiss()
            }

            btnCautious.setOnClickListener {
                onSubmit(SafetyLevel.BE_CAUTIOUS, etComment.text.toString(), seekBarRadius.progress)
                dialog.dismiss()
            }

            btnUnsafe.setOnClickListener {
                onSubmit(SafetyLevel.UNSAFE, etComment.text.toString(), seekBarRadius.progress)
                dialog.dismiss()
            }

            btnDangerous.setOnClickListener {
                onSubmit(SafetyLevel.DANGEROUS, etComment.text.toString(), seekBarRadius.progress)
                dialog.dismiss()
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
        }

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
            .setTitle("Safety Report Details")
            .create()

        val safetyLevel = report.getSafetyLevelEnum()

        with(dialogBinding) {
            safetyIndicator.setBackgroundColor(safetyLevel.color)
            tvSafetyLevel.text = safetyLevel.displayName
            tvSafetyLevel.setTextColor(safetyLevel.color)
            tvAreaName.text = report.areaName
            tvComment.text = report.comment
            tvUserName.text = report.userName
            tvTimestamp.text = report.getFormattedDate()

            tvRadiusInfo.text = "Coverage ${report.radiusMeters}m radius"

            val voteCount = report.upvotes - report.downvotes
            tvVoteCount.text = voteCount.toString()

            if (hasVoted) {
                btnUpvote.isEnabled = false
                btnDownvote.isEnabled = false
                btnUpvote.alpha = 0.5f
                btnDownvote.alpha = 0.5f
            } else {
                btnUpvote.setOnClickListener {
                    onUpvote()
                    dialog.dismiss()
                }
                btnDownvote.setOnClickListener {
                    onDownvote()
                    dialog.dismiss()
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
        val bottomSheetDialog = BottomSheetDialog(context)
        val sheetBinding = BottomSheetReportsBinding.inflate(LayoutInflater.from(context))

        val adapter = SafetyReportAdapter(
            votedReportIds = votedReportIds,
            onUpvoteClick = onUpvote,
            onDownvoteClick = onDownvote,
            onItemClick = onItemClick
        )

        with(sheetBinding) {
            recyclerViewReports.layoutManager = LinearLayoutManager(context)
            recyclerViewReports.adapter = adapter

            if (reports.isEmpty()) {
                recyclerViewReports.visibility = View.GONE
                tvNoReports.visibility = View.VISIBLE
            } else {
                recyclerViewReports.visibility = View.VISIBLE
                tvNoReports.visibility = View.GONE
                adapter.updateReports(reports)
            }

            btnSort.setOnClickListener {
                showSortDialog(currentSort, onSort)
            }

            btnClose.setOnClickListener {
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.setContentView(sheetBinding.root)
        return bottomSheetDialog
    }

    fun showMapTypeDialog(currentType: Int, onTypeSelected: (Int) -> Unit) {
        val options = arrayOf("Normal", "Satellite", "Terrain", "Hybrid")
        AlertDialog.Builder(context)
            .setTitle("Select Map Type")
            .setSingleChoiceItems(options, currentType) { dialog, which ->
                onTypeSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showSortDialog(currentSort: SortOptions, onSelected: (SortOptions) -> Unit) {
        val options = arrayOf(
            "Most Dangerous First",
            "Safest First",
            "Newest First",
            "Oldest First",
            "Highest Votes",
            "Lowest Votes"
        )

        val currentIndex = when (currentSort) {
            SortOptions.DANGER_LEVEL_DESC -> 0
            SortOptions.DANGER_LEVEL_ASC -> 1
            SortOptions.TIME_NEWEST -> 2
            SortOptions.TIME_OLDEST -> 3
            SortOptions.VOTES_HIGH -> 4
            SortOptions.VOTES_LOW -> 5
        }

        AlertDialog.Builder(context)
            .setTitle("Sort Reports By")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
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
                dialog.dismiss()
            }
            .show()
    }
}