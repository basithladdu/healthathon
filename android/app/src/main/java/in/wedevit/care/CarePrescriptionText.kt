package `in`.wedevit.care

/** Copies only visible prescription lines. Packaging is never used to invent a medicine or dose. */
fun copiedPrescriptionMedicines(pages: List<DocumentPageText>): List<OrganizedMedicine> {
    val seen = mutableSetOf<String>()
    return pages.flatMap { page -> page.originalText.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.length !in 5..500 || line.contains(Regex("\\b(patient|diagnosis|hospital|address|registration|invoice|total)\\b", RegexOption.IGNORE_CASE))) return@mapNotNull null
        if (!line.contains(Regex("\\b(tab(?:let)?|cap(?:sule)?|syrup|drops?|ointment|cream|injection)\\b|\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|ml|units)\\b", RegexOption.IGNORE_CASE))) return@mapNotNull null
        if (!seen.add("${page.page}:$line")) return@mapNotNull null
        val strength = Regex("\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|ml|units)\\b", RegexOption.IGNORE_CASE).find(line)?.value.orEmpty()
        val name = line.replace(Regex("^\\s*\\d+[.)-]\\s*"), "").take(120)
        OrganizedMedicine(name, strength, "", "", "", emptyList(), MedicineMealTiming.NOT_STATED,
            line, null, null, -1, listOf(page.page - 1), null,
            listOf("Copied text may contain reading errors. Check the name, exact dose and directions against this prescription page; packaging was not automatically matched."), MedicineMatchQuality.PRESCRIPTION_ONLY, sourceQuote = raw)
    }.toList() }.take(50)
}
