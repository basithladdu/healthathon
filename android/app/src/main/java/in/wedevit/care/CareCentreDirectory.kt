package `in`.wedevit.care

import org.json.JSONArray

/** Public, dated directory listings. These are not staff approvals or medicine-stock receipts. */
object CareCentreDirectory {
    private val bundledLock = Any()
    @Volatile private var bundled: List<Centre>? = null
    /** The bundled India directory with approximate map positions (india-geo.json), parsed on first use and shared by every care model in the process. */
    fun bundled(context: android.content.Context): List<Centre> = bundled ?: synchronized(bundledLock) {
        bundled ?: CentreLocation.withOverlay(decode(context.assets.open("india.json").bufferedReader().use { it.readText() }),
            CentreLocation.overlay(context.assets.open("india-geo.json").bufferedReader().use { it.readText() })).also { bundled = it }
    }
    fun decode(json: String): List<Centre> = JSONArray(json).let { data ->
        (0 until data.length()).map { index -> data.getJSONObject(index).let { row ->
            val services = row.optJSONArray("services") ?: JSONArray()
            val phone = row.optJSONArray("phoneNumbers")?.optString(0).orEmpty().ifBlank { row.optString("phone") }
            val latitude = row.optDouble("latitude").takeIf { it.isFinite() && it in -90.0..90.0 }
            val longitude = row.optDouble("longitude").takeIf { it.isFinite() && it in -180.0..180.0 }
            Centre(name = row.getString("name"), city = row.optString("city"), district = row.optString("district"),
                address = row.optString("address"), phone = phone,
                services = (0 until services.length()).joinToString(" · ") { services.getString(it) },
                morphine = "unknown", url = row.getString("directoryUrl"), id = row.getString("id"),
                latitude = if (longitude != null) latitude else null, longitude = if (latitude != null) longitude else null,
                state = row.getString("state"), sourceName = row.optString("sourceName", "Published directory"),
                checkedOn = row.optString("checkedOn"), verification = "directory_listed", sourceListedMorphine = row.optString("sourceListedMorphine"))
        } }.also { centres ->
            require(centres.all { it.name.isNotBlank() && it.state.isNotBlank() && it.url.startsWith("https://") && it.id.isNotBlank() }) { "A centre listing is missing its public source or location." }
            require(centres.map { it.id }.distinct().size == centres.size) { "The centre directory has duplicate record IDs." }
        }
    }
}
