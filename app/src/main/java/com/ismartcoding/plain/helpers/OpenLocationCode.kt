package com.ismartcoding.plain.helpers

import kotlin.math.floor

/**
 * Minimal Google Plus Codes (Open Location Code) encoder.
 * Reference: https://github.com/google/open-location-code
 */
object OpenLocationCode {
    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POS = 8
    private const val ENCODING_BASE = 20
    private const val LATITUDE_MAX = 90
    private const val LONGITUDE_MAX = 180
    private const val PAIR_CODE_LEN = 10
    private const val GRID_COLS = 4
    private const val GRID_ROWS = 5

    fun encode(latitude: Double, longitude: Double, codeLength: Int = 10): String {
        val length = codeLength.coerceIn(2, 15)
        val lat = clipLatitude(latitude) + LATITUDE_MAX
        val lng = normalizeLongitude(longitude) + LONGITUDE_MAX

        val sb = StringBuilder()
        var latRem = lat
        var lngRem = lng
        var resolution = ENCODING_BASE.toDouble()
        for (i in 0 until 5) {
            val latDigit = floor(latRem / resolution).toInt().coerceIn(0, ENCODING_BASE - 1)
            sb.append(CODE_ALPHABET[latDigit])
            latRem -= latDigit * resolution
            val lngDigit = floor(lngRem / resolution).toInt().coerceIn(0, ENCODING_BASE - 1)
            sb.append(CODE_ALPHABET[lngDigit])
            lngRem -= lngDigit * resolution
            resolution /= ENCODING_BASE
        }
        sb.insert(SEPARATOR_POS, SEPARATOR)

        if (length > PAIR_CODE_LEN) {
            var gLatRem = latRem
            var gLngRem = lngRem
            var latRes = 1.0 / (ENCODING_BASE * ENCODING_BASE * ENCODING_BASE * ENCODING_BASE * ENCODING_BASE) * GRID_ROWS
            var lngRes = 1.0 / (ENCODING_BASE * ENCODING_BASE * ENCODING_BASE * ENCODING_BASE * ENCODING_BASE) * GRID_COLS
            for (i in 0 until (length - PAIR_CODE_LEN).coerceAtMost(5)) {
                val row = floor(gLatRem / latRes).toInt().coerceIn(0, GRID_ROWS - 1)
                val col = floor(gLngRem / lngRes).toInt().coerceIn(0, GRID_COLS - 1)
                sb.append(CODE_ALPHABET[row * GRID_COLS + col])
                gLatRem -= row * latRes
                gLngRem -= col * lngRes
                latRes /= GRID_ROWS
                lngRes /= GRID_COLS
            }
        }

        // Truncate to length + separator (separator at index 8)
        val out = sb.toString()
        return if (out.length > length + 1) out.substring(0, length + 1) else out
    }

    private fun clipLatitude(latitude: Double): Double = latitude.coerceIn(-90.0, 90.0)

    private fun normalizeLongitude(longitude: Double): Double {
        var lng = longitude
        while (lng < -180) lng += 360
        while (lng >= 180) lng -= 360
        return lng
    }
}
