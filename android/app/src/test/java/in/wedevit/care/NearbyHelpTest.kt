package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class NearbyHelpTest {
    private fun centre(id: String, state: String, district: String = "", address: String = "", services: String = "Clinic visits", morphine: String = "") =
        Centre(name = "Centre $id", city = district, district = district, address = address, phone = "", services = services, morphine = "unknown",
            url = "https://example.org/$id", id = id, state = state, sourceListedMorphine = morphine)
    private val centres = listOf(
        centre("a", "Kerala", "Kollam", "Main road, Kollam"),
        centre("b", "Kerala", "", "Near bus stand, Kollam 691001"),
        centre("c", "Kerala", "Ernakulam", "MG road, Kochi", services = "Home care", morphine = "available"),
        centre("d", "Delhi", "", "Saket, New Delhi"),
    )
    private val text = centres.map { "${it.name} ${it.city} ${it.district} ${it.address} ${it.state}" }
    private fun ids(search: String = "", state: String = NearbyHelp.ALL_INDIA, district: String = "", service: String = NearbyHelp.ALL_SERVICES, morphine: Boolean = false) =
        NearbyHelp.visible(centres, text, search, state, district, service, morphine).map { it.id }

    @Test fun emergencyNumbersAreTheCitedNationalOnesAndOnlyOpenTheDialler() {
        assertEquals(listOf("112", "108", "102", "14567"), NearbyHelp.emergency.map { it.number })
        assertTrue(NearbyHelp.emergency.all { it.label.isNotBlank() && it.labelHindi.isNotBlank() && (it.note.isBlank() == it.noteHindi.isBlank()) })
        assertEquals("tel:14567", NearbyHelp.dialUri("14 567"))
    }
    @Test fun districtsComeOnlyFromTheChosenStatesListings() {
        assertEquals(listOf("Ernakulam", "Kollam"), NearbyHelp.districts(centres, "Kerala"))
        assertEquals(emptyList<String>(), NearbyHelp.districts(centres, "Delhi"))
    }
    @Test fun stateFilterKeepsSourceOrder() {
        assertEquals(listOf("a", "b", "c", "d"), ids())
        assertEquals(listOf("a", "b", "c"), ids(state = "Kerala"))
        assertEquals(listOf("d"), ids(state = "Delhi"))
    }
    @Test fun districtMatchesListedDistrictFirstThenAddressesNamingIt() {
        assertEquals(listOf("a", "b"), ids(state = "Kerala", district = "Kollam"))
        val reversed = centres.reversed()
        assertEquals(listOf("a", "b"), NearbyHelp.visible(reversed, text.reversed(), "", "Kerala", "Kollam", NearbyHelp.ALL_SERVICES, false).map { it.id })
    }
    @Test fun searchServiceAndMorphineCombineWithLocation() {
        assertEquals(listOf("c"), ids(state = "Kerala", service = "Home care"))
        assertEquals(listOf("c"), ids(morphine = true))
        assertEquals(listOf("d"), ids(search = "  saket "))
        assertEquals(emptyList<String>(), ids(state = "Delhi", district = "Kollam"))
    }
}
