package `in`.wedevit.care

import android.content.Context

/** A family-chosen document type; the file name only suggests one. Nothing here reads or interprets results. */
object CancerDocumentRules {
    const val NOT_CANCER = "none"
    val types = listOf("PET-CT", "Biopsy", "Histopathology", "Staging", "Other")
    /** Shown through the screen's English/Hindi picker, `t(english, hindi)`. */
    fun label(type: String, t: (String, String) -> String): String = when (type) {
        "PET-CT" -> t("PET-CT scan", "पेट-सीटी स्कैन")
        "Biopsy" -> t("Biopsy", "बायोप्सी")
        "Histopathology" -> t("Histopathology", "हिस्टोपैथोलॉजी")
        "Staging" -> t("Staging report", "स्टेजिंग रिपोर्ट")
        else -> t("Cancer document", "कैंसर का कागज़")
    }
    /** Suggest a type from words in the file name only. */
    fun detect(name: String): String? {
        val text = " " + name.lowercase().replace(Regex("[^a-z0-9]+"), " ") + " "
        fun has(vararg words: String) = words.any { text.contains(" $it ") }
        return when {
            has("pet ct", "petct", "pet scan", "pet", "psma") -> "PET-CT"
            has("histopathology", "histopath", "hpe", "ihc", "immunohistochemistry") -> "Histopathology"
            has("biopsy", "fnac", "fnab", "core needle") -> "Biopsy"
            has("staging", "tnm") -> "Staging"
            else -> null
        }
    }
    /** The family's choice wins over the file-name suggestion; [NOT_CANCER] hides a wrong suggestion. */
    fun type(name: String, chosen: String?): String? = when (chosen) {
        NOT_CANCER -> null
        null, "" -> detect(name)
        else -> chosen.takeIf { it in types } ?: detect(name)
    }
}

/** Local to this phone, like the report files it labels. */
class CancerDocumentStore(context: Context, namespace: String) {
    private val prefs = context.applicationContext.getSharedPreferences("cancer-docs-${namespace.ifBlank { "guest" }}", Context.MODE_PRIVATE)
    fun all(): Map<String, String> = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    fun set(reportId: String, type: String) {
        require(type == CancerDocumentRules.NOT_CANCER || type in CancerDocumentRules.types) { "Choose a document type." }
        prefs.edit().putString(reportId, type).apply()
    }
    fun remove(reportId: String) { prefs.edit().remove(reportId).apply() }
}
