package `in`.wedevit.care

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Medicines tab (25 Nov redesign): every shown word has Hindi, and the owner's "no explainer, no reporter line" rules hold. */
class MedicinesPageTest {
    private val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
    private fun source(name: String) = File(dir, name).readText()

    @Test fun medicinesPageHasHindi() {
        val page = source("MedicinesPage.kt"); val state = source("MedicineTodayState.kt")
        val missing = listOf("MedicinesPage", "DoseListRow", "TimeGroupHeader", "ListHeader", "AsNeededRow", "MedicinesHero", "MedicinesEmpty", "PastMedicinesPage", "SideEffectsToRaiseSheet")
            .flatMap { name -> HindiScan.missing(HindiScan.body(page, name), emptySet()).map { "$name: $it" } } +
            listOf("rememberTodayDoses", "WhichNightDialogHost", "DoseUndoSnackbarEffect", "RemoveMedicineDialog")
                .flatMap { name -> HindiScan.missing(HindiScan.body(state, name), emptySet()).map { "$name: $it" } }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun medicinesPageDropsTheRetiredPieces() {
        val page = source("MedicinesPage.kt")
        // Owner override: no tinted section surfaces, no pill Lottie or confetti, no tap hint, no inline Undo card.
        listOf("SaharaSection.Medicines.wash", "SaharaLottie", "Tap a dose when taken", "DoseUndoRow(", "MedicineDoseActions(", "LinearProgressIndicator").forEach {
            assertFalse("MedicinesPage.kt still uses $it", page.contains(it))
        }
        // The one-tap Undo lives in the app snackbar, once per page.
        assertTrue(page.contains("DoseUndoSnackbarEffect(person)"))
    }

    @Test fun reporterLinesAreForTheCareTeamOnly() {
        val history = source("MedicineHistoryUi.kt"); val sideEffects = source("SideEffectUi.kt")
        assertTrue(history.contains("if (clinician) Text(t(\"Reported by \""))
        assertTrue(sideEffects.contains("if (!showReporter)"))
    }
}
