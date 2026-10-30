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

    /** Dialler link for a number. Many listings write Indian numbers as "91 98…" without "+"; those get "+" so the dialler reads a country code. */
    fun dialUri(number: String): String {
        val digits = number.replace(Regex("[^+0-9]"), "")
        return "tel:" + if (digits.length == 12 && digits.startsWith("91")) "+$digits" else digits
    }

    /** Districts the directory lists for a state, as spelled in the source. Most states have none listed. */
    fun districts(centres: List<Centre>, state: String): List<String> =
        centres.filter { it.state == state }.map { it.district.trim() }.filter(String::isNotBlank).distinct().sorted()

    /**
     * Centres matching the filters. With a district chosen, a centre matches when the source lists that district, or when its
     * published address names it (many listings have no district field); listed-district matches come first.
     */
    fun visible(centres: List<Centre>, searchText: List<String>, search: String, state: String, district: String,
                service: String, layers: Set<String> = layerOptions.toSet()): List<Centre> {
        val words = queryWords(search)
        val matches = centres.filterIndexed { index, c ->
            matches(searchText[index], words) &&
                (state == ALL_INDIA || c.state == state) && servesAs(c, service) &&
                layerOf(c) in layers &&
                (district.isBlank() || c.district.equals(district, true) || c.address.contains(district, true))
        }
        return if (district.isBlank()) matches else matches.sortedBy { !it.district.equals(district, true) }
    }

    /**
     * Map and list layers. Every centre in the directory is a palliative care centre; the ones whose source listing says
     * morphine is available form the second layer, in their own colour. "Listed" only: the app never says morphine is in stock.
     */
    fun layerOf(c: Centre): String = if (c.sourceListedMorphine == "available") LAYER_MORPHINE else LAYER_PALLIATIVE
    val layerOptions = listOf(LAYER_PALLIATIVE, LAYER_MORPHINE)
    /** Turns a layer chip on or off. The last layer that is on stays on, so the map is never empty because of the chips alone. */
    fun toggleLayer(layers: Set<String>, layer: String): Set<String> = when {
        layer !in layerOptions -> layers
        layer !in layers -> layers + layer
        layers.size > 1 -> layers - layer
        else -> layers
    }
    /** Pin, chip and badge colour per layer: Sahara terracotta and a blue that differ for red-green colour blindness too. */
    fun layerColor(layer: String): Long = if (layer == LAYER_MORPHINE) 0xFF2B5C9E else 0xFFB9472C
    fun layerColorHex(layer: String): String = "#%06X".format(layerColor(layer) and 0xFFFFFFL)

    /** Service chips, in the order shown. Each one is in the bundled directory; "Hospice" means the listing's name or address says hospice. */
    val serviceOptions = listOf(ALL_SERVICES, "Home care", "Clinic visits", "Inpatient care", "Day care", "Physiotherapy", HOSPICE)

    fun servesAs(c: Centre, service: String): Boolean = when (service) {
        ALL_SERVICES -> true
        HOSPICE -> c.name.contains("hospice", true) || c.address.contains("hospice", true) || c.services.contains("hospice", true)
        else -> c.services.contains(service, ignoreCase = true)
    }

    /** One lower-case string to search per centre: name, place, address, state and services, plus the address with PIN digits joined ("560 034" → "560034"). */
    fun searchKey(c: Centre): String = listOf(c.name, c.city, c.district, c.address, c.state, c.services, c.address.replace(Regex("(?<=\\d)\\s+(?=\\d)"), ""))
        .joinToString(" ").lowercase()

    /** Every query word must appear somewhere in the key (any case, part of a word is enough); a word with known other spellings matches any of them. */
    fun matches(key: String, words: List<String>): Boolean = words.all { word -> (aliases.firstOrNull { word in it } ?: listOf(word)).any { key.contains(it, ignoreCase = true) } }

    /** Query words, with Hindi place and service names turned into the English the directory uses. */
    fun queryWords(search: String): List<String> {
        var text = search.lowercase()
        hindi.entries.sortedByDescending { it.key.length }.forEach { (hi, en) -> text = text.replace(hi, " $en ") }
        return text.split(Regex("[\\s,]+")).filter(String::isNotBlank)
    }

    /** Spellings the directory mixes for the same place. */
    private val aliases = listOf(listOf("bengaluru", "bangalore"), listOf("mumbai", "bombay"), listOf("kochi", "cochin"), listOf("thiruvananthapuram", "trivandrum"),
        listOf("chennai", "madras"), listOf("gurugram", "gurgaon"), listOf("ernakulam", "ernakulum"), listOf("kolkata", "calcutta"))

    /** Hindi names a person may type for states, large cities and services. */
    private val hindi = mapOf("केरल" to "kerala", "कर्नाटक" to "karnataka", "तमिलनाडु" to "tamil nadu", "तमिल नाडु" to "tamil nadu", "आंध्र प्रदेश" to "andhra pradesh",
        "तेलंगाना" to "telangana", "महाराष्ट्र" to "maharashtra", "दिल्ली" to "delhi", "उत्तर प्रदेश" to "uttar pradesh", "बिहार" to "bihar", "पश्चिम बंगाल" to "west bengal",
        "गुजरात" to "gujarat", "राजस्थान" to "rajasthan", "मध्य प्रदेश" to "madhya pradesh", "पंजाब" to "punjab", "हरियाणा" to "haryana", "ओडिशा" to "odisha", "असम" to "assam",
        "झारखंड" to "jharkhand", "छत्तीसगढ़" to "chhattisgarh", "उत्तराखंड" to "uttarakhand", "हिमाचल प्रदेश" to "himachal pradesh", "गोवा" to "goa", "पुडुचेरी" to "puducherry",
        "चंडीगढ़" to "chandigarh", "मुंबई" to "mumbai", "बेंगलुरु" to "bengaluru", "बैंगलोर" to "bangalore", "चेन्नई" to "chennai", "कोलकाता" to "kolkata", "हैदराबाद" to "hyderabad",
        "पुणे" to "pune", "जयपुर" to "jaipur", "लखनऊ" to "lucknow", "अहमदाबाद" to "ahmedabad", "कोच्चि" to "kochi", "तिरुवनंतपुरम" to "thiruvananthapuram", "पटना" to "patna",
        "भोपाल" to "bhopal", "नागपुर" to "nagpur", "कोयंबटूर" to "coimbatore", "हॉस्पिस" to "hospice", "होस्पिस" to "hospice", "घर पर देखभाल" to "home care", "फ़िज़ियोथेरेपी" to "physiotherapy")

    /** Text to send with Share: the listing as published, a directions link, and the reminder to call first. */
    fun shareText(c: Centre): String = listOf(c.name, c.address, listOf(c.city, c.district, c.state).filter(String::isNotBlank).distinct().joinToString(", "),
        c.phone.takeIf { it.any(Char::isDigit) }?.let { "Phone / फ़ोन: $it" }, "Directions / रास्ता: " + directionsUrl(c),
        careBilingual("Directory listing, call to confirm services.", "सूची की जानकारी है, सेवाएँ फ़ोन करके पक्की करें।")).filterNotNull().filter(String::isNotBlank).joinToString("\n")

    fun directionsUrl(c: Centre): String = "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(c.name + " " + c.address, "UTF-8")

    const val ALL_INDIA = "All India"
    const val ALL_SERVICES = "All"
    const val HOSPICE = "Hospice"
    const val LAYER_PALLIATIVE = "palliative"
    const val LAYER_MORPHINE = "morphine"
}
