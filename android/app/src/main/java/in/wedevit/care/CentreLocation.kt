package `in`.wedevit.care

import org.json.JSONObject

/**
 * How sure a centre's map pin is. Exact pins come from the directory row; pincode, city and district pins come from
 * assets/india-geo.json (scripts/geocode_centres.py, GeoNames CC BY 4.0) and mark the centre of that area, not the building.
 */
object CentreLocation {
    const val EXACT = "exact"
    const val PINCODE = "pincode"
    const val CITY = "city"
    const val DISTRICT = "district"
    private val approximateKinds = setOf(PINCODE, CITY, DISTRICT)

    /** The pin's precision, or null when the centre has no map position. Coordinates without a stated precision are exact. */
    fun precision(c: Centre): String? = if (c.latitude == null || c.longitude == null) null else c.locationPrecision.ifBlank { EXACT }

    fun approximate(c: Centre): Boolean = precision(c) in approximateKinds

    data class Coverage(val exact: Int, val approximate: Int, val listOnly: Int)

    fun coverage(centres: List<Centre>): Coverage {
        val kinds = centres.map(::precision)
        return Coverage(kinds.count { it == EXACT }, kinds.count { it in approximateKinds }, kinds.count { it == null })
    }

    /** The overlay {"centres": {id: [lat, lon, precision]}}; rows with an unknown precision or a point outside India are dropped. */
    fun overlay(json: String): Map<String, Triple<Double, Double, String>> {
        val rows = JSONObject(json).getJSONObject("centres")
        return rows.keys().asSequence().mapNotNull { id ->
            val row = rows.getJSONArray(id)
            val lat = row.optDouble(0); val lon = row.optDouble(1); val kind = row.optString(2)
            if (kind in approximateKinds && lat in 6.0..37.5 && lon in 68.0..98.0) id to Triple(lat, lon, kind) else null
        }.toMap()
    }

    /** Fills in approximate positions only for centres that have none; a directory row's own coordinates always win. */
    fun withOverlay(centres: List<Centre>, overlay: Map<String, Triple<Double, Double, String>>): List<Centre> = centres.map { c ->
        val point = overlay[c.id]
        if (precision(c) != null || point == null) c else c.copy(latitude = point.first, longitude = point.second, locationPrecision = point.third)
    }

    /** Card line for an approximate pin, English and Hindi; null for exact or unmapped centres. */
    fun note(c: Centre): Pair<String, String>? = when (precision(c)) {
        PINCODE -> "Approximate location (PIN area) · call to confirm" to "अनुमानित जगह (पिन क्षेत्र) · फ़ोन करके पक्का करें"
        CITY -> "Approximate location (town area) · call to confirm" to "अनुमानित जगह (शहर या कस्बे का इलाक़ा) · फ़ोन करके पक्का करें"
        DISTRICT -> "Approximate location (district area) · call to confirm" to "अनुमानित जगह (ज़िले का इलाक़ा) · फ़ोन करके पक्का करें"
        else -> null
    }

    /** One map point per mapped centre. The map clusters these; approximate ones are drawn hollow. */
    data class Pin(val id: String, val latitude: Double, val longitude: Double, val approximate: Boolean)

    fun pins(centres: List<Centre>): List<Pin> = centres.mapNotNull { c ->
        if (precision(c) == null) null else Pin(c.id, c.latitude!!, c.longitude!!, approximate(c))
    }

    /**
     * What tapping a cluster does: the zoom to fly to, at least one level in, or null to list its centres instead. Many
     * approximate pins share one area centre, and zooming never splits those, so a cluster that is all one place, or that
     * only splits past street level, opens as a list.
     */
    fun clusterTapZoom(current: Double, expansion: Int, leaves: List<Pair<Double, Double>>): Double? =
        if (leaves.distinct().size <= 1 || expansion > CLUSTER_MAX_ZOOM) null else maxOf(current + 1.0, expansion.toDouble())

    /** Pins closer than this many screen pixels merge into a numbered cluster, up to street level. */
    const val CLUSTER_RADIUS = 44
    const val CLUSTER_MAX_ZOOM = 16
}
