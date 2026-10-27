package `in`.wedevit.care

/** A national number shown on Nearby. Tapping it opens the dialler with the number filled in; the app never places the call. */
data class EmergencyNumber(val number: String, val label: String, val labelHindi: String, val note: String = "", val noteHindi: String = "")

object NearbyHelp {
    /**
     * Sources (checked 27 Oct 2026):
     * - 112: Emergency Response Support System, Ministry of Home Affairs, single number for police, fire and ambulance:
     *   https://112.gov.in/about
     * - 108 and 102: Ministry of Health and Family Welfare, National Health Mission. 108 is the emergency ambulance for
     *   critical care, trauma and accidents; 102 is basic patient transport mainly for pregnant women and sick infants
     *   (JSSK). Both are run by each state, so cover differs by state:
     *   https://mohfw.gov.in/sites/default/files/02Chapter.pdf and Lok Sabha answer
     *   https://eparlib.nic.in/bitstream/123456789/956165/1/AU5611.pdf
     * - 14567: Elderline, the national helpline for senior citizens, Ministry of Social Justice and Empowerment,
     *   8 am to 8 pm every day: https://pib.gov.in/PressReleasePage.aspx?PRID=1758880
     * No palliative or cancer helpline is listed: no government source for a national one was found. The Saath Saath line
     * (1800-202-7777) is run by palliative care organisations, not the government, so it is left out.
     */
    val emergency = listOf(
        EmergencyNumber("112", "Emergency: police, fire, ambulance", "आपातकाल: पुलिस, आग, एम्बुलेंस"),
        EmergencyNumber("108", "Ambulance", "एम्बुलेंस", "Run by your state", "राज्य सरकार की सेवा"),
        EmergencyNumber("102", "Ambulance for pregnant women and babies", "गर्भवती महिलाओं और शिशुओं के लिए एम्बुलेंस", "Run by your state", "राज्य सरकार की सेवा"),
        EmergencyNumber("14567", "Elderline for older people", "बुज़ुर्गों के लिए एल्डरलाइन", "8 am – 8 pm", "सुबह 8 – रात 8 बजे"),
    )

    fun dialUri(number: String): String = "tel:" + number.replace(Regex("[^+0-9]"), "")

    /** Districts the directory lists for a state, as spelled in the source. Most states have none listed. */
    fun districts(centres: List<Centre>, state: String): List<String> =
        centres.filter { it.state == state }.map { it.district.trim() }.filter(String::isNotBlank).distinct().sorted()

    /**
     * Centres matching the filters. With a district chosen, a centre matches when the source lists that district, or when its
     * published address names it (many listings have no district field); listed-district matches come first.
     */
    fun visible(centres: List<Centre>, searchText: List<String>, search: String, state: String, district: String,
                service: String, morphine: Boolean): List<Centre> {
        val matches = centres.filterIndexed { index, c ->
            searchText[index].contains(search.trim(), true) &&
                (state == ALL_INDIA || c.state == state) && (service == ALL_SERVICES || c.services.contains(service, ignoreCase = true)) &&
                (!morphine || c.sourceListedMorphine == "available") &&
                (district.isBlank() || c.district.equals(district, true) || c.address.contains(district, true))
        }
        return if (district.isBlank()) matches else matches.sortedBy { !it.district.equals(district, true) }
    }

    const val ALL_INDIA = "All India"
    const val ALL_SERVICES = "All"
}
