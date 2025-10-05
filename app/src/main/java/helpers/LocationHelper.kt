
package com.example.travelnow.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import utils.GeoUtils
import java.util.Locale


// UPDATE LocationManagerHelper.kt - Add GPS checking
@SuppressLint("MissingPermission")
class LocationManagerHelper(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private var locationCancellationToken: CancellationTokenSource? = null

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return GeoUtils.calculateDistance(lat1, lon1, lat2, lon2)
    }

    fun getCurrentLocation(
        onSuccess: (Location) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!isGpsEnabled()) {
            onFailure(Exception("GPS is disabled"))
            return
        }

        if (!hasLocationPermission()) {
            onFailure(Exception("Location permission not granted"))
            return
        }

        locationCancellationToken?.cancel()
        locationCancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            locationCancellationToken!!.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                onSuccess(location)
            } else {
                onFailure(Exception("Cannot get location. Please ensure GPS is enabled."))
            }
        }.addOnFailureListener { exception ->
            onFailure(exception)
        }
    }

    fun getLatLngFromLocationName(
        locationName: String,
        onSuccess: (LatLng, String) -> Unit,
        onFailure: () -> Unit
    ) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(locationName, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        val addressText = address.getAddressLine(0) ?: locationName
                        onSuccess(latLng, addressText)
                    } else {
                        onFailure()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(locationName, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    val addressText = address.getAddressLine(0) ?: locationName
                    onSuccess(latLng, addressText)
                } else {
                    onFailure()
                }
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Error: ${e.message}")
            onFailure()
        }
    }

    fun getAddressFromLocation(latLng: LatLng, callback: (String) -> Unit) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        callback(addresses[0].getAddressLine(0) ?: "Unknown Location")
                    } else {
                        callback("Unknown Location")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    callback(addresses[0].getAddressLine(0) ?: "Unknown Location")
                } else {
                    callback("Unknown Location")
                }
            }
        } catch (e: Exception) {
            callback("Unknown Location")
        }
    }

    fun cancelLocationRequest() {
        locationCancellationToken?.cancel()
    }
}