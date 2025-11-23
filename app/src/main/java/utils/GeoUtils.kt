package utils

import kotlin.math.*

object GeoUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Encode latitude and longitude into a geohash
     * @param precision Number of characters (default 7 for ~153m accuracy)
     */
    fun encode(latitude: Double, lon: Double, precision: Int = 7): String {
        var longitude = lon
        var lat = latitude

        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        val geohash = StringBuilder()
        var bits = 0
        var bit = 0
        var ch = 0
        var even = true

        while (geohash.length < precision) {
            if (even) {
                // longitude
                val mid = (lonMin + lonMax) / 2
                if (longitude > mid) {
                    ch = ch or (1 shl (4 - bits))
                    lonMin = mid
                } else {
                    lonMax = mid
                }
            } else {
                // latitude
                val mid = (latMin + latMax) / 2
                if (lat > mid) {
                    ch = ch or (1 shl (4 - bits))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            even = !even

            if (bits < 4) {
                bits++
            } else {
                geohash.append(BASE32[ch])
                bits = 0
                ch = 0
            }
        }

        return geohash.toString()
    }

    /**
     * Get geohash query bounds for a radius search
     * CRITICAL: This returns bounds for Firestore queries
     *
     * For large radius searches (> 50km), use lower precision (4-5)
     * For medium searches (5-50km), use precision 5-6
     * For small searches (< 5km), use precision 7
     */
    fun getGeohashBounds(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): Pair<String, String> {
        // Adjust precision based on radius for better queries
        val precision = when {
            radiusKm > 100 -> 4  // ~20km per cell
            radiusKm > 50 -> 5   // ~5km per cell
            radiusKm > 10 -> 6   // ~1km per cell
            else -> 7            // ~153m per cell
        }

        // Get the center geohash
        val centerHash = encode(latitude, longitude, precision)

        // For Firestore range queries, we need to create bounds
        // that include all possible geohashes in the area

        // Lower bound: center hash with all zeros
        val lowerBound = centerHash.substring(0, precision - 1)

        // Upper bound: increment last character or add 'z' to ensure we catch everything
        val upperBound = incrementGeohash(centerHash) + "~"

        return Pair(lowerBound, upperBound)
    }

    /**
     * Increment a geohash string (for creating upper bounds)
     */
    private fun incrementGeohash(geohash: String): String {
        val chars = geohash.toCharArray()
        for (i in chars.indices.reversed()) {
            val index = BASE32.indexOf(chars[i])
            if (index < BASE32.length - 1) {
                chars[i] = BASE32[index + 1]
                return String(chars)
            } else {
                chars[i] = BASE32[0]
            }
        }
        return geohash + BASE32[0]
    }

    /**
     * BETTER APPROACH: Get multiple geohash prefixes that cover a circular area
     * This returns multiple geohash prefixes to query
     */
    fun getGeohashPrefixes(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<String> {
        val precision = when {
            radiusKm > 100 -> 3
            radiusKm > 50 -> 4
            radiusKm > 10 -> 5
            radiusKm > 5 -> 6
            else -> 7
        }

        val centerHash = encode(latitude, longitude, precision)

        // Return the center hash prefix and its neighbors
        val prefixes = mutableListOf(centerHash)

        // Add neighbor prefixes for better coverage
        // (This is simplified - in production you'd calculate all 8 neighbors)
        val neighbors = getNeighbors(centerHash)
        prefixes.addAll(neighbors)

        return prefixes
    }

    /**
     * Get neighboring geohashes (simplified version)
     */
    private fun getNeighbors(geohash: String): List<String> {
        // This is a simplified version
        // In production, implement proper neighbor calculation
        val neighbors = mutableListOf<String>()

        // For now, just return variations
        val lastChar = geohash.lastOrNull() ?: return emptyList()
        val prefix = if (geohash.length > 1) geohash.substring(0, geohash.length - 1) else ""

        val index = BASE32.indexOf(lastChar)

        // Add adjacent characters
        if (index > 0) {
            neighbors.add(prefix + BASE32[index - 1])
        }
        if (index < BASE32.length - 1) {
            neighbors.add(prefix + BASE32[index + 1])
        }

        return neighbors
    }

    /**
     * Decode geohash to latitude/longitude bounds
     */
    fun decode(geohash: String): Pair<DoubleArray, DoubleArray> {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        var even = true

        for (c in geohash) {
            val cd = BASE32.indexOf(c)
            for (j in 0 until 5) {
                val mask = 1 shl (4 - j)
                if (even) {
                    if (cd and mask != 0) {
                        lonMin = (lonMin + lonMax) / 2
                    } else {
                        lonMax = (lonMin + lonMax) / 2
                    }
                } else {
                    if (cd and mask != 0) {
                        latMin = (latMin + latMax) / 2
                    } else {
                        latMax = (latMin + latMax) / 2
                    }
                }
                even = !even
            }
        }

        return Pair(
            doubleArrayOf(latMin, latMax),
            doubleArrayOf(lonMin, lonMax)
        )
    }
}