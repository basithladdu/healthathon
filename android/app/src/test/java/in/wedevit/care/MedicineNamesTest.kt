package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MedicineNamesTest {
    private val folder = File("src/main/assets/medicine-names")
    private val names by lazy { File(folder, "cdci-names.tsv").useLines { MedicineNames.parse(it) } }

    @Test fun bundledListIsSmallAttributedAndCoversPalliativeEssentials() {
        val asset = File(folder, "cdci-names.tsv")
        assertTrue(asset.length() < 500_000)
        assertTrue(names.size > 3000)
        listOf("NOTICE.txt", "DATA_LICENSE.md", "CDCI-License.txt").forEach { assertTrue(it, File(folder, it).isFile) }
        assertTrue(File(folder, "NOTICE.txt").readText().contains("CC BY 4.0"))
        assertTrue(asset.readLines().first().contains("CC BY 4.0"))
        val lower = names.map { it.name.lowercase() }.toSet()
        listOf("morphine", "fentanyl", "tramadol", "paracetamol", "ondansetron", "haloperidol", "metoclopramide", "lactulose", "dexamethasone", "midazolam", "gabapentin")
            .forEach { assertTrue(it, it in lower) }
        assertEquals("373529000", names.first { it.name == "Morphine" }.snomed)
    }

    @Test fun listHoldsNamesOnlyNeverStrengthsOrDoses() {
        val strength = Regex("\\d+(\\.\\d+)?\\s*(mg|mcg|ml|g\\b|microgram|unit|%)", RegexOption.IGNORE_CASE)
        assertTrue(names.none { strength.containsMatchIn(it.name) })
        assertTrue(names.none { it.name.contains(" tablet", true) || it.name.contains(" injection", true) })
    }

    @Test fun searchOffersWordStartsFirstAndStopsOnExactName() {
        val list = MedicineNames.parse(sequenceOf("# header", "Morphine\t373529000", "Morphine sulfate\t60886004", "Apomorphine\t", "Diamorphine hydrochloride\t", "Paracetamol\t", "x\t"))
        assertEquals(5, list.size)
        assertEquals(emptyList<MedicineName>(), MedicineNames.search(list, "m"))
        val morph = MedicineNames.search(list, "morph").map { it.name }
        assertEquals(listOf("Morphine", "Morphine sulfate", "Apomorphine", "Diamorphine hydrochloride"), morph)
        assertEquals(emptyList<MedicineName>(), MedicineNames.search(list, " morphine "))
        assertEquals(listOf("Paracetamol"), MedicineNames.search(list, "PARA").map { it.name })
        assertEquals(listOf("Morphine sulfate"), MedicineNames.search(list, "sulf").map { it.name })
        assertTrue(MedicineNames.search(names, "ondan").any { it.name == "Ondansetron" })
    }
}
