package com.gios.brightrecorderlite.tape

import java.util.Locale
import kotlin.math.abs

/**
 * The name a clip is filed under when nobody typed one.
 *
 * The full BrightRecorder turned a position into "Trastevere, Rome" with Android's Geocoder or
 * OpenStreetMap's Nominatim. Neither is available to a Light SDK tool: Geocoder needs an Android
 * Context, and Nominatim needs the INTERNET permission, which this tool does not ask for. There is
 * no offline gazetteer either. So the label is the position itself, rounded to two decimal places
 * (about a kilometer), which is honest about how well the phone knows where it is and still lets
 * two clips from the same neighborhood read the same.
 */
object Place {

    fun label(latitude: Double?, longitude: Double?): String {
        if (latitude == null || longitude == null) return Naming.NOWHERE
        if (latitude.isNaN() || longitude.isNaN()) return Naming.NOWHERE
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return Naming.NOWHERE
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f %s, %.2f %s", abs(latitude), ns, abs(longitude), ew)
    }
}
