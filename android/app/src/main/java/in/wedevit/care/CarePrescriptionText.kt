package `in`.wedevit.care

/** Copies only visible prescription lines, filling only name, strength and form. Packaging is never used to invent a medicine or dose. */
fun copiedPrescriptionMedicines(pages: List<DocumentPageText>): List<OrganizedMedicine> {
    val seen = mutableSetOf<String>()
    return pages.flatMap { page -> page.originalText.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.length !in 5..500 || line.contains(Regex("\\b(patient|diagnosis|hospital|address|registration|invoice|total)\\b", RegexOption.IGNORE_CASE))) return@mapNotNull null
        if (!line.contains(Regex("\\b(tab(?:let)?|cap(?:sule)?|syrup|drops?|ointment|cream|injection)\\b|\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|ml|units)\\b", RegexOption.IGNORE_CASE))) return@mapNotNull null
        if (!seen.add("${page.page}:$line")) return@mapNotNull null
        // Decision 8: a photo fills only the name, strength and form. Frequency, amount, directions and course are typed by hand.
        MedicinePhotoFields.limit(OrganizedMedicine(MedicinePhotoFields.nameIn(line), MedicinePhotoFields.strengthIn(line), MedicinePhotoFields.formIn(line), "", "", emptyList(), MedicineMealTiming.NOT_STATED,
            "", null, null, -1, listOf(page.page - 1), null,
            listOf("Copied text may contain reading errors. Check the name and strength against this prescription page, then type how much, how often and the directions by hand."), MedicineMatchQuality.PRESCRIPTION_ONLY, sourceQuote = raw))
    }.toList() }.take(50)
}
