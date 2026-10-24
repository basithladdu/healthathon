package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareFeedbackRulesTest {
    private fun result(id: String, kind: CareFeedbackKind, text: String = id) = CareFeedback(id, kind, "Result", text)

    @Test fun failureInterruptsRoutineFeedbackWithoutDiscardingTheAcceptedSave() {
        val saved = result("saved", CareFeedbackKind.SAVED)
        val failed = result("failed", CareFeedbackKind.ERROR)
        val pending = listOf(saved, failed)
        assertEquals(failed, CareFeedbackRules.dialog(pending))
        assertNull(CareFeedbackRules.snackbar(pending))
        assertEquals(saved, CareFeedbackRules.snackbar(CareFeedbackRules.dismiss(pending, failed.id)))
    }

    @Test fun dismissingAnOlderResultCannotConsumeANewerFailure() {
        val first = result("first", CareFeedbackKind.ERROR)
        val next = result("next", CareFeedbackKind.ERROR)
        val remaining = CareFeedbackRules.dismiss(listOf(first, next), first.id)
        assertEquals(listOf(next), remaining)
        assertEquals(next, CareFeedbackRules.dialog(remaining))
    }

    @Test fun repeatedBackgroundFailureIsShownOnceButDifferentFailureIsRetained() {
        val failed = result("first", CareFeedbackKind.ERROR, "Draft not saved")
        val duplicate = failed.copy(id = "second")
        val different = result("third", CareFeedbackKind.ERROR, "Report not removed")
        assertEquals(listOf(failed), CareFeedbackRules.append(listOf(failed), duplicate))
        assertEquals(listOf(failed, different), CareFeedbackRules.append(listOf(failed), different))
    }

    @Test fun legacyWordingCannotTurnAnUntypedNoticeIntoAcceptedSaveFeedback() {
        val notice = result("legacy", CareFeedbackKind.NOTICE, "Saved")
        assertEquals(CareFeedbackKind.NOTICE, CareFeedbackRules.snackbar(listOf(notice))!!.kind)
        assertNull(CareFeedbackRules.dialog(listOf(notice)))
    }

    @Test fun distinctAcceptedOperationsWithTheSameMessageAreNotDropped() {
        val first = result("first", CareFeedbackKind.SAVED, "Symptom note saved")
        val next = first.copy(id = "next")
        assertEquals(listOf(first, next), CareFeedbackRules.append(listOf(first), next))
    }

    @Test fun onlyConsequentialCompletionUsesADialogWhileRoutineSaveUsesASnackbar() {
        val saved = result("saved", CareFeedbackKind.SAVED)
        val completed = result("removed", CareFeedbackKind.COMPLETED)
        assertNull(CareFeedbackRules.dialog(listOf(saved)))
        assertEquals(saved, CareFeedbackRules.snackbar(listOf(saved)))
        assertEquals(completed, CareFeedbackRules.dialog(listOf(completed)))
        assertNull(CareFeedbackRules.snackbar(listOf(completed)))
    }
}
