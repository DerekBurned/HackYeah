
package com.example.travelnow.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale


class LocationManagerHelper(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private var cancellationToken: CancellationTokenSource? = null

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getCurrentLocation(
        onSuccess: (Location) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!hasLocationPermission()) {
            onFailure(SecurityException("Location permission not granted"))
            return
        }

        cancellationToken?.cancel()
        cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken!!.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                onSuccess(location)
            } else {
                onFailure(Exception("Location is null"))
            }
        }.addOnFailureListener { exception ->
            onFailure(exception)
        }
    }

    fun getAddressFromLocation(
        latLng: LatLng,
        callback: (String) -> Unit
    ) {
        Log.d(TAG, "getAddressFromLocation called for: $latLng")
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Using new Geocoder API (Android 13+)")
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    Log.d(TAG, "Geocoder callback received, addresses count: ${addresses.size}")
                    val address = if (addresses.isNotEmpty()) {
                        addresses.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
                    } else {
                        "Unknown Location"
                    }
                    Log.d(TAG, "Address found: $address")

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(address)
                    }
                }
            } else {
                Log.d(TAG, "Using old Geocoder API (Android 12 and below)")
                Thread {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                        val address = if (addresses != null && addresses.isNotEmpty()) {
                            addresses.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
                        } else {
                            "Unknown Location"
                        }
                        Log.d(TAG, "Address found: $address")

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(address)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Geocoder error in thread: ${e.message}", e)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback("Unknown Location")
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder error: ${e.message}", e)
            callback("Unknown Location")
        }
    }

    fun getLatLngFromLocationName(
        locationName: String,
        onSuccess: (LatLng, String) -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d(TAG, "getLatLngFromLocationName called for: $locationName")
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(locationName, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        val addressText = address.getAddressLine(0) ?: locationName
                        Log.d(TAG, "Location found: $latLng")

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onSuccess(latLng, addressText)
                        }
                    } else {
                        Log.d(TAG, "No location found for: $locationName")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onFailure()
                        }
                    }
                }
            } else {
                Thread {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocationName(locationName, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            val addressText = address.getAddressLine(0) ?: locationName
                            Log.d(TAG, "Location found: $latLng")

                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onSuccess(latLng, addressText)
                            }
                        } else {
                            Log.d(TAG, "No location found for: $locationName")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onFailure()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Location search error in thread: ${e.message}", e)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onFailure()
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location search error: ${e.message}", e)
            onFailure()
        }
    }

    fun cancelLocationRequest() {
        cancellationToken?.cancel()
    }

    companion object {
        private const val TAG = "LocationManagerHelper"
    }
}