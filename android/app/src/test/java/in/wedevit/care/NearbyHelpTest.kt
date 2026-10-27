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
    @Test fun centrePhonesWrittenWith91WithoutPlusDialAsIndianNumbers() {
        assertEquals("tel:+918985855123", NearbyHelp.dialUri("91 8985855123"))
        assertEquals("tel:+919160767111", NearbyHelp.dialUri("+91 9160767111"))
        assertEquals("tel:9866072574", NearbyHelp.dialUri("98660 72574"))
        assertEquals("tel:04772287497", NearbyHelp.dialUri("0477 2287497"))
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

    private val towns = listOf(
        Centre(name = "Karunya Hospice", city = "Bangalore", district = "Bengaluru Urban", address = "4th Cross, Bangalore 560 034", phone = "91 9876543210",
            services = "Home care · Inpatient care", morphine = "unknown", url = "https://example.org/k", id = "k", state = "Karnataka"),
        Centre(name = "Shanti Avedna", city = "Mumbai", district = "", address = "Mahalaxmi, Mumbai 400026", phone = "", services = "Inpatient care",
            morphine = "unknown", url = "https://example.org/s", id = "s", state = "Maharashtra", latitude = 18.98, longitude = 72.82),
        Centre(name = "Pain and Palliative Clinic", city = "Ernakulum", district = "Ernakulam", address = "Kakkanad", phone = "", services = "Day care · Physiotherapy",
            morphine = "unknown", url = "https://example.org/e", id = "e", state = "Kerala"),
    )
    private val keys = towns.map(NearbyHelp::searchKey)
    private fun found(search: String, service: String = NearbyHelp.ALL_SERVICES) =
        NearbyHelp.visible(towns, keys, search, NearbyHelp.ALL_INDIA, "", service, false).map { it.id }

    @Test fun searchIgnoresCaseMatchesPartWordsAndNeedsEveryWord() {
        assertEquals(listOf("k"), found("KARUN"))
        assertEquals(listOf("k"), found("hosp karnat"))
        assertEquals(listOf("s"), found("avedna, maharashtra"))
        assertEquals(emptyList<String>(), found("hospice kerala"))
        assertEquals(listOf("k", "s", "e"), found("   "))
    }
    @Test fun searchFindsPinWrittenWithOrWithoutSpace() {
        assertEquals(listOf("k"), found("560034"))
        assertEquals(listOf("k"), found("560 034"))
        assertEquals(listOf("s"), found("400026"))
    }
    @Test fun searchCoversCityDistrictStateAndOtherSpellings() {
        assertEquals(listOf("k"), found("bengaluru"))
        assertEquals(listOf("e"), found("ernakulam"))
        assertEquals(listOf("e"), found("kerala"))
        assertEquals(listOf("s"), found("bombay"))
    }
    @Test fun hindiPlaceAndServiceWordsSearchTheEnglishDirectory() {
        assertEquals(listOf("e"), found("केरल"))
        assertEquals(listOf("s"), found("मुंबई"))
        assertEquals(listOf("k"), found("बेंगलुरु हॉस्पिस"))
        assertEquals(listOf("k"), found("घर पर देखभाल"))
    }
    @Test fun serviceChipsComeFromTheDataAndHospiceMeansTheListingSaysSo() {
        assertEquals(listOf("All", "Home care", "Clinic visits", "Inpatient care", "Day care", "Physiotherapy", "Hospice"), NearbyHelp.serviceOptions)
        assertEquals(listOf("k"), found("", NearbyHelp.HOSPICE))
        assertEquals(listOf("e"), found("", "Physiotherapy"))
        assertEquals(listOf("k", "s"), found("", "Inpatient care"))
    }
    @Test fun shareTextHasTheListingDirectionsAndCallToConfirm() {
        val text = NearbyHelp.shareText(towns[0])
        assertTrue(text.startsWith("Karunya Hospice\n4th Cross, Bangalore 560 034"))
        assertTrue(text.contains("Phone / फ़ोन: 91 9876543210"))
        assertTrue(text.contains("https://www.google.com/maps/search/?api=1&query=Karunya+Hospice+4th+Cross%2C+Bangalore+560+034"))
        assertTrue(text.contains("call to confirm"))
        assertFalse(NearbyHelp.shareText(towns[1]).contains("Phone"))
    }
}
