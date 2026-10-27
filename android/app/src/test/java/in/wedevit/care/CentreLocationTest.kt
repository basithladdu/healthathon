package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CentreLocationTest {
    private fun centre(id: String, lat: Double? = null, lon: Double? = null, precision: String = "") = Centre(name = "Centre $id", city = "", district = "",
        address = "Somewhere", phone = "", services = "", morphine = "unknown", url = "https://example.org", id = id, latitude = lat, longitude = lon, state = "Kerala",
        locationPrecision = precision)

    private val overlayJson = """{"centres":{"a":[13.05,77.59,"exact"],"b":[9.93,76.26,"pincode"],"c":[8.69,76.81,"city"],"d":[11.6,76.08,"district"],
        "e":[51.5,-0.12,"city"],"f":[10.0,76.0,"state"]}}"""

    @Test fun directoryCoordinatesStayExactAndAreNeverReplacedByTheOverlay() {
        val exact = centre("a", 13.0545589, 77.5916468)
        val result = CentreLocation.withOverlay(listOf(exact), mapOf("a" to Triple(1.0, 2.0, "pincode"))).single()
        assertEquals(13.0545589, result.latitude!!, 0.0); assertEquals(CentreLocation.EXACT, CentreLocation.precision(result))
        assertFalse(CentreLocation.approximate(result))
    }

    @Test fun overlayFillsOnlyKnownApproximateKindsInsideIndia() {
        val overlay = CentreLocation.overlay(overlayJson)
        assertEquals(setOf("b", "c", "d"), overlay.keys)   // "exact" comes from the directory, London and "state" are dropped
        val filled = CentreLocation.withOverlay(listOf(centre("b"), centre("c"), centre("d"), centre("z")), overlay)
        assertEquals(listOf("pincode", "city", "district", null), filled.map(CentreLocation::precision))
        assertTrue(filled.take(3).all(CentreLocation::approximate))
    }

    @Test fun coverageCountsEachPrecisionHonestly() {
        val list = listOf(centre("a", 1.0, 2.0), centre("b", 1.0, 2.0, "pincode"), centre("c", 1.0, 2.0, "district"), centre("z"))
        assertEquals(CentreLocation.Coverage(exact = 1, approximate = 2, listOnly = 1), CentreLocation.coverage(list))
    }

    @Test fun cardNoteNamesTheAreaInEnglishAndHindi() {
        assertEquals("Approximate location (PIN area) · call to confirm" to "अनुमानित जगह (पिन क्षेत्र) · फ़ोन करके पक्का करें",
            CentreLocation.note(centre("b", 1.0, 2.0, "pincode")))
        assertTrue(CentreLocation.note(centre("c", 1.0, 2.0, "city"))!!.first.contains("town area"))
        assertTrue(CentreLocation.note(centre("d", 1.0, 2.0, "district"))!!.first.contains("district area"))
        assertNull(CentreLocation.note(centre("a", 1.0, 2.0))); assertNull(CentreLocation.note(centre("z")))
    }

    @Test fun everyMappedCentreBecomesOnePinAndApproximateOnesAreMarked() {
        val pins = CentreLocation.pins(listOf(centre("a", 1.0, 2.0), centre("b", 3.0, 4.0, "city"), centre("z")))
        assertEquals(listOf(CentreLocation.Pin("a", 1.0, 2.0, false), CentreLocation.Pin("b", 3.0, 4.0, true)), pins)
    }

    @Test fun clusterTapZoomsInUntilItSplitsThenListsSharedAreaPins() {
        val apart = listOf(9.9 to 76.2, 10.0 to 76.3)
        assertEquals(8.0, CentreLocation.clusterTapZoom(5.0, 8, apart)!!, 0.0)
        assertEquals(6.0, CentreLocation.clusterTapZoom(5.0, 3, apart)!!, 0.0)          // always at least one level in
        assertNull(CentreLocation.clusterTapZoom(5.0, 8, listOf(9.9 to 76.2, 9.9 to 76.2)))   // one area centre: list them
        assertNull(CentreLocation.clusterTapZoom(15.0, CentreLocation.CLUSTER_MAX_ZOOM + 1, apart)) // splits only past street level
    }

    @Test fun bundledOverlayCoversTheDirectoryWithoutTouchingExactRows() {
        val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { File(it, "india.json").exists() }
        val directory = CareCentreDirectory.decode(File(assets, "india.json").readText())
        val located = CentreLocation.withOverlay(directory, CentreLocation.overlay(File(assets, "india-geo.json").readText()))
        val coverage = CentreLocation.coverage(located)
        assertEquals(directory.count { it.latitude != null }, coverage.exact)
        assertTrue("most centres should be on the map: $coverage", coverage.exact + coverage.approximate > directory.size * 0.95)
        directory.zip(located).filter { it.first.latitude != null }.forEach { (before, after) -> assertEquals(before, after) }
    }
}
