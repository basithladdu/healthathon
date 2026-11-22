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
    private fun ids(search: String = "", state: String = NearbyHelp.ALL_INDIA, district: String = "", service: String = NearbyHelp.ALL_SERVICES, layers: Set<String> = NearbyHelp.layerOptions.toSet()) =
        NearbyHelp.visible(centres, text, search, state, district, service, layers).map { it.id }

    @Test fun emergencyNumbersAreTheCitedNationalOnesAndOnlyOpenTheDialler() {
        // 102 (pregnant women and babies) was removed at the 3 Nov review; 112 is the number on the Nearby call bar.
        assertEquals(listOf("112", "108", "14567"), NearbyHelp.emergency.map { it.number })
        assertEquals("112", NearbyHelp.EMERGENCY_CALL)
        assertEquals("tel:112", NearbyHelp.dialUri(NearbyHelp.EMERGENCY_CALL))
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
        assertEquals(listOf("a", "b"), NearbyHelp.visible(reversed, text.reversed(), "", "Kerala", "Kollam", NearbyHelp.ALL_SERVICES).map { it.id })
    }
    @Test fun searchServiceAndMorphineCombineWithLocation() {
        assertEquals(listOf("c"), ids(state = "Kerala", service = "Home care"))
        assertEquals(listOf("c"), ids(layers = setOf(NearbyHelp.LAYER_MORPHINE)))
        assertEquals(listOf("d"), ids(search = "  saket "))
        assertEquals(emptyList<String>(), ids(state = "Delhi", district = "Kollam"))
    }

    @Test fun eachCentreIsInExactlyOneLayerByItsSourceMorphineListing() {
        assertEquals(listOf("palliative", "palliative", "morphine", "palliative"), centres.map(NearbyHelp::layerOf))
        // Only the source's "available" counts; the free-text morphine field and other values never move a centre to the morphine layer.
        assertEquals(NearbyHelp.LAYER_PALLIATIVE, NearbyHelp.layerOf(centre("x", "Kerala", morphine = "unknown")))
        assertEquals(NearbyHelp.LAYER_PALLIATIVE, NearbyHelp.layerOf(centre("y", "Kerala", morphine = "")))
    }
    @Test fun layerFilterShowsOnlyTheChosenLayersAndCombinesWithOtherFilters() {
        assertEquals(listOf("a", "b", "c", "d"), ids())
        assertEquals(listOf("a", "b", "d"), ids(layers = setOf(NearbyHelp.LAYER_PALLIATIVE)))
        assertEquals(listOf("c"), ids(layers = setOf(NearbyHelp.LAYER_MORPHINE)))
        assertEquals(emptyList<String>(), ids(state = "Delhi", layers = setOf(NearbyHelp.LAYER_MORPHINE)))
        assertEquals(listOf("a", "b"), ids(state = "Kerala", district = "Kollam", layers = setOf(NearbyHelp.LAYER_PALLIATIVE)))
        assertEquals(emptyList<String>(), ids(layers = emptySet()))
    }
    @Test fun layerChipsToggleButTheLastLayerStaysOn() {
        val all = NearbyHelp.layerOptions.toSet()
        assertEquals(setOf(NearbyHelp.LAYER_MORPHINE), NearbyHelp.toggleLayer(all, NearbyHelp.LAYER_PALLIATIVE))
        assertEquals(setOf(NearbyHelp.LAYER_MORPHINE), NearbyHelp.toggleLayer(setOf(NearbyHelp.LAYER_MORPHINE), NearbyHelp.LAYER_MORPHINE))
        assertEquals(all, NearbyHelp.toggleLayer(setOf(NearbyHelp.LAYER_MORPHINE), NearbyHelp.LAYER_PALLIATIVE))
        assertEquals(all, NearbyHelp.toggleLayer(all, "votes"))
    }
    @Test fun layersHaveDistinctPinColours() {
        assertEquals("#B9472C", NearbyHelp.layerColorHex(NearbyHelp.LAYER_PALLIATIVE))
        assertEquals("#2B5C9E", NearbyHelp.layerColorHex(NearbyHelp.LAYER_MORPHINE))
        assertEquals(NearbyHelp.layerOptions.size, NearbyHelp.layerOptions.map(NearbyHelp::layerColor).distinct().size)
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
        NearbyHelp.visible(towns, keys, search, NearbyHelp.ALL_INDIA, "", service).map { it.id }

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

    @Test fun directionsGoToTheVerifiedSpotOnlyWhenTheDirectoryHasAnExactOne() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=18.980000,72.820000", NearbyHelp.directionsUrl(towns[1]))
        val area = towns[1].copy(locationPrecision = CentreLocation.PINCODE)
        assertTrue(NearbyHelp.directionsUrl(area).startsWith("https://www.google.com/maps/search/?api=1&query=Shanti+Avedna"))
        assertTrue(NearbyHelp.directionsUrl(towns[0]).startsWith("https://www.google.com/maps/search/?api=1&query=Karunya+Hospice"))
    }
    @Test fun cardTagsComeFromTheListingInChipOrderAndEachHasItsOwnColour() {
        assertEquals(listOf("Home care", "Inpatient care", "Hospice"), NearbyHelp.serviceTags(towns[0]))
        assertEquals(listOf("Day care", "Physiotherapy"), NearbyHelp.serviceTags(towns[2]))
        val tags = NearbyHelp.serviceOptions.drop(1) + NearbyHelp.layerOptions
        assertEquals(tags.size, tags.map { NearbyHelp.tagColors(it) }.distinct().size)
        assertEquals(tags.size, tags.map { NearbyHelp.tagColors(it).first }.distinct().size)
    }
    @Test fun centresGivingHomeClinicOrInpatientCareShowPalliativeCareEvenWithMorphineListed() {
        // AIIMS Mangalagiri (22 Nov review): OP / IP / Home care with morphine listed must say "Palliative care", not only "Morphine listed".
        val aiims = centre("m", "Andhra Pradesh", services = "Clinic visits · Inpatient care · Home care", morphine = "available").copy(name = "AIIMS, Mangalagiri")
        assertTrue(NearbyHelp.offersPalliativeCare(aiims))
        assertEquals(listOf(NearbyHelp.LAYER_PALLIATIVE, NearbyHelp.LAYER_MORPHINE, "Home care", "Clinic visits", "Inpatient care"), NearbyHelp.cardTags(aiims))
        // A hospice with only inpatient beds and morphine listed (Aashraya Hospice).
        val hospice = centre("h", "Andhra Pradesh", services = "Inpatient care", morphine = "available").copy(name = "Aashraya Hospice")
        assertEquals(listOf(NearbyHelp.LAYER_PALLIATIVE, NearbyHelp.LAYER_MORPHINE, "Inpatient care", "Hospice"), NearbyHelp.cardTags(hospice))
        // Home care alone, and the word palliative in the name, are enough.
        assertTrue(NearbyHelp.offersPalliativeCare(centre("hc", "Kerala", services = "Home care", morphine = "available")))
        assertTrue(NearbyHelp.offersPalliativeCare(centre("p", "Kerala", services = "", morphine = "available").copy(name = "Ashraya Pain & Palliative Care")))
        // Morphine listed with nothing else in the listing: only the morphine tag.
        val bare = centre("b", "Kerala", services = "", morphine = "available")
        assertFalse(NearbyHelp.offersPalliativeCare(bare))
        assertEquals(listOf(NearbyHelp.LAYER_MORPHINE), NearbyHelp.cardTags(bare))
        // A centre in the palliative layer always carries the palliative tag, once.
        assertEquals(listOf(NearbyHelp.LAYER_PALLIATIVE, "Home care"), NearbyHelp.cardTags(centre("k", "Kerala", services = "Home care")))
    }
    @Test fun distanceOrdersMappedCentresFirstAndKeepsTheRestInOrder() {
        val pune = 18.52 to 73.86
        val near = towns[1].copy(id = "near", latitude = 18.53, longitude = 73.85)
        val list = listOf(towns[0], towns[1], near, towns[2])
        assertEquals(listOf("near", "s", "k", "e"), NearbyHelp.byDistance(list, pune.first, pune.second).map { it.id })
        assertEquals(listOf("near"), NearbyHelp.nearest(list, pune.first, pune.second, 1).map { it.id })
        assertNull(NearbyHelp.distanceKm(towns[0], pune.first, pune.second))
        // Pune to Mumbai is about 120 km in a straight line.
        assertEquals(120.0, NearbyHelp.distanceKm(pune.first, pune.second, 18.98, 72.82), 10.0)
    }
    @Test fun pickerSearchMatchesValueOrShownLabelInAnyCase() {
        val states = listOf("Kerala" to "केरल", "Karnataka" to "कर्नाटक", "Tamil Nadu" to "तमिलनाडु")
        assertEquals(states, NearbyHelp.pick(states, "  "))
        assertEquals(listOf("Tamil Nadu"), NearbyHelp.pick(states, "tamil").map { it.first })
        assertEquals(listOf("Kerala"), NearbyHelp.pick(states, "केरल").map { it.first })
        assertEquals(listOf("Karnataka"), NearbyHelp.pick(states, "KA").map { it.first })
        assertEquals(emptyList<String>(), NearbyHelp.pick(states, "goa").map { it.first })
    }
}
