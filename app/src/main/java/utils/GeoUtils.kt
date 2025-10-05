package utils

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


    /**
     * Calculates the great-circle distance between two points on Earth using the Haversine formula
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val from = LatLng(lat1, lon1)
        val to = LatLng(lat2, lon2)
        return SphericalUtil.computeDistanceBetween(from, to) / 1000.0
    }


    fun encode(latitude: Double, longitude: Double, precision: Int = 7): String {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        val geohash = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lonMin + lonMax) / 2
                if (longitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    lonMin = mid
                } else {
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEven = !isEven

            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }

        return geohash.toString()
    }


    fun getGeohashBounds(latitude: Double, longitude: Double, radiusKm: Double): Pair<String, String> {
        // Approximate: use smaller precision for larger radius
        val precision = when {
            radiusKm > 100 -> 4
            radiusKm > 20 -> 5
            radiusKm > 5 -> 6
            else -> 7
        }

        val centerHash = encode(latitude, longitude, precision)
        val lower = centerHash.substring(0, precision)

        val lastCharIndex = BASE32.indexOf(lower.last())
        val upper = if (lastCharIndex < BASE32.length - 1) {
            lower.substring(0, precision - 1) + BASE32[lastCharIndex + 1]
        } else {
            lower.substring(0, precision - 1) + "z"
        }

        return Pair(lower, upper)
    }
}

