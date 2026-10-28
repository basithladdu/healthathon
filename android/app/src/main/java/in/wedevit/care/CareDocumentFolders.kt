package `in`.wedevit.care

import android.content.Context

/** Where a document is filed, and whether the person chose it ([chosen]) or the phone sorted it. */
data class DocumentFolder(val folder: String, val chosen: Boolean)

/**
 * Sorts a document into a folder from words already on this phone: its name and the text read on the phone
 * (bundled ML Kit text recognition). Nothing is uploaded to sort. Keyword rules only; the person can move any document.
 */
object DocumentFolderRules {
    const val BILLS = "bills"
    const val LAB = "lab"
    const val PRESCRIPTIONS = "prescriptions"
    const val DISCHARGE = "discharge"
    const val OTHER = "other"
    /** In the order the Docs tab shows them. */
    val folders = listOf(BILLS, LAB, PRESCRIPTIONS, DISCHARGE, OTHER)
    /** Tie-break order: a discharge summary lists medicines and tests, and a pharmacy bill lists medicines. */
    private val priority = listOf(DISCHARGE, BILLS, LAB, PRESCRIPTIONS)
    /** The lowest score that files a document anywhere but Other. */
    const val THRESHOLD = 3

    /** Shown through the screen's English/Hindi picker, `t(english, hindi)`. */
    fun label(folder: String, t: (String, String) -> String): String = when (folder) {
        BILLS -> t("Bills", "बिल")
        LAB -> t("Lab reports", "लैब रिपोर्ट")
        PRESCRIPTIONS -> t("Prescriptions", "पर्चे")
        DISCHARGE -> t("Discharge summaries", "डिस्चार्ज सारांश")
        else -> t("Other", "अन्य")
    }

    private val cues: Map<String, List<Pair<String, Int>>> = mapOf(
        DISCHARGE to listOf("discharge summary" to 6, "discharge" to 2, "date of discharge" to 4, "date of admission" to 3, "admitted on" to 3, "doa" to 1, "dod" to 1,
            "course in hospital" to 4, "hospital course" to 4, "condition at discharge" to 4, "advice on discharge" to 4, "discharge advice" to 4, "final diagnosis" to 2,
            "डिस्चार्ज" to 3, "भर्ती" to 2, "छुट्टी" to 1),
        BILLS to listOf("tax invoice" to 5, "invoice" to 3, "bill no" to 4, "bill" to 2, "receipt" to 3, "receipt no" to 2, "cash memo" to 4, "gstin" to 4, "gst" to 2,
            "net payable" to 4, "amount" to 1, "total" to 1, "paid" to 1, "payment" to 2, "rs" to 1, "₹" to 2, "balance" to 1,
            "बिल" to 3, "रसीद" to 3, "भुगतान" to 2, "राशि" to 1),
        LAB to listOf("lab report" to 4, "laboratory" to 3, "pathology" to 3, "reference range" to 4, "biological reference" to 4, "reference interval" to 4, "normal range" to 3,
            "test name" to 3, "investigation" to 2, "result" to 1, "units" to 1, "specimen" to 3, "sample collected" to 3, "sample" to 1, "lab" to 1,
            "haemoglobin" to 2, "hemoglobin" to 2, "cbc" to 2, "complete blood count" to 3, "platelet" to 2, "platelets" to 2, "creatinine" to 2, "bilirubin" to 2,
            "mg dl" to 2, "g dl" to 2, "जांच" to 2, "हीमोग्लोबिन" to 2),
        PRESCRIPTIONS to listOf("prescription" to 4, "rx" to 3, "℞" to 3, "tab" to 1, "tablet" to 1, "cap" to 1, "capsule" to 1, "syp" to 2, "syrup" to 1, "inj" to 1,
            "once daily" to 2, "twice daily" to 2, "od" to 1, "bd" to 2, "tds" to 2, "hs" to 1, "sos" to 1, "before food" to 2, "after food" to 2, "after meals" to 2,
            "पर्चा" to 4, "दवा" to 2, "खाने से पहले" to 2, "खाने के बाद" to 2),
    ).mapValues { (_, list) -> list.map { (phrase, weight) -> spaced(phrase) to weight } }

    /** Lower case, the same Hindi spelling rules as document search, and every run of other characters as one space. */
    private fun spaced(text: String) = " " + DocumentSearchWords.normalize(text).replace(Regex("[^\\p{L}\\p{M}\\p{N}₹℞]+"), " ").replace("₹", " ₹ ").replace("℞", " ℞ ").replace(Regex(" +"), " ").trim() + " "

    /** Each cue counts once per text; words in the file name count twice, because the person or their hospital named it. */
    fun scores(name: String, text: String): Map<String, Int> {
        val inName = spaced(name); val inText = spaced(text)
        return cues.mapValues { (_, list) -> list.sumOf { (phrase, weight) -> (if (inName.contains(phrase)) 2 * weight else 0) + (if (inText.contains(phrase)) weight else 0) } }
    }

    fun sort(name: String, text: String): String {
        val scores = scores(name, text)
        val best = priority.maxByOrNull { scores[it] ?: 0 } ?: return OTHER
        return if ((scores[best] ?: 0) >= THRESHOLD) best else OTHER
    }

    fun counts(filed: Collection<String>): Map<String, Int> = folders.associateWith { folder -> filed.count { it == folder } }

    fun encode(value: DocumentFolder): String = (if (value.chosen) "chosen:" else "sorted:") + value.folder
    fun decode(value: String?): DocumentFolder? {
        val parts = value?.split(':', limit = 2)?.takeIf { it.size == 2 && it[1] in folders } ?: return null
        return when (parts[0]) { "chosen" -> DocumentFolder(parts[1], true); "sorted" -> DocumentFolder(parts[1], false); else -> null }
    }
}

/** Local to this phone, like the report files it labels. The person's choice is never replaced by a later sort. */
class DocumentFolderStore(context: Context, namespace: String) {
    private val prefs = context.applicationContext.getSharedPreferences("document-folders-${namespace.ifBlank { "guest" }}", Context.MODE_PRIVATE)
    fun all(): Map<String, DocumentFolder> = prefs.all.mapNotNull { (key, value) -> DocumentFolderRules.decode(value as? String)?.let { key to it } }.toMap()
    fun sorted(reportId: String, folder: String) {
        if (DocumentFolderRules.decode(prefs.getString(reportId, null))?.chosen == true) return
        prefs.edit().putString(reportId, DocumentFolderRules.encode(DocumentFolder(folder, false))).apply()
    }
    fun choose(reportId: String, folder: String) {
        require(folder in DocumentFolderRules.folders) { "Choose a folder." }
        prefs.edit().putString(reportId, DocumentFolderRules.encode(DocumentFolder(folder, true))).apply()
    }
    fun remove(reportId: String) { prefs.edit().remove(reportId).apply() }
}
