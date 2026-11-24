package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFlowTest {
    @Test fun opensOnTheIntroOnceThenOnTheRoleCardsAndResumesAfterSignIn() {
        assertEquals(OnboardingStep.Intro, OnboardingFlow.start(introSeen = false, signedIn = false, role = ""))
        assertEquals(OnboardingStep.Role, OnboardingFlow.start(introSeen = true, signedIn = false, role = SignupRole.FAMILY))
        assertEquals(OnboardingStep.Name, OnboardingFlow.start(introSeen = true, signedIn = true, role = SignupRole.PATIENT))
        assertEquals(OnboardingStep.Role, OnboardingFlow.start(introSeen = true, signedIn = true, role = SignupRole.DOCTOR))
    }

    @Test fun backWalksTheStepsAndLeavesTheAppFromTheFirstScreen() {
        assertNull(OnboardingFlow.back(OnboardingStep.Intro, signedIn = false, introShown = true))
        assertNull(OnboardingFlow.back(OnboardingStep.Role, signedIn = false, introShown = false))
        assertEquals(OnboardingStep.Intro, OnboardingFlow.back(OnboardingStep.Role, signedIn = false, introShown = true))
        assertEquals(OnboardingStep.Account, OnboardingFlow.back(OnboardingStep.Name, signedIn = false, introShown = false))
        assertEquals(OnboardingStep.Role, OnboardingFlow.back(OnboardingStep.Name, signedIn = true, introShown = false))
        assertEquals(OnboardingStep.Treatment, OnboardingFlow.back(OnboardingStep.Finish, signedIn = false, introShown = false))
        assertEquals(OnboardingStep.CareDoctor, OnboardingFlow.back(OnboardingStep.Treatment, signedIn = false, introShown = false))
        assertEquals(OnboardingStep.Illness, OnboardingFlow.back(OnboardingStep.CareDoctor, signedIn = false, introShown = false))
    }

    @Test fun careNowAnswersBecomeProfileFields() {
        val p = OnboardingFlow.profile("", "", illnessSince = " Mar 2025 ", doctorName = " Dr Shirin ", treatments = listOf("chemotherapy", "laser", "chemotherapy"), treatmentNote = " ")
        assertEquals(CarePatientProfile(illnessSince = "Mar 2025", doctorName = "Dr Shirin", treatments = listOf("chemotherapy")), p)
        // "none_now" (No treatment now) was taken off on 24 Nov, so a saved one is dropped like any unknown code.
        assertEquals(listOf("surgery", "not_sure"), OnboardingFlow.codes("surgery,,unknown,none_now,not_sure"))
        assertEquals(listOf("surgery"), OnboardingFlow.codes("surgery,,unknown,none_now"))
    }

    @Test fun durationAndLandlineBecomeProfileFields() {
        val p = OnboardingFlow.profile("", "", illnessSince = CareIllnessDuration(8, years = false).encode(), doctorPhone = CarePhone.combine("+91", "9876543210"),
            doctorLandline = CarePhone.combine("+91", "080 2345 6789"))
        assertEquals(CarePatientProfile(illnessSince = "8 months", doctorPhone = "+91 98765 43210", doctorLandline = "+91 08023456789"), p)
    }

    @Test fun progressGrowsAlongTheProfilePathAndIsHiddenOnTheIntroAndDoctorSignIn() {
        assertNull(OnboardingFlow.progress(OnboardingStep.Intro)); assertNull(OnboardingFlow.progress(OnboardingStep.Doctor))
        val path = listOf(OnboardingStep.Role, OnboardingStep.Account, OnboardingStep.Name, OnboardingStep.Age, OnboardingStep.Illness, OnboardingStep.Finish).map { OnboardingFlow.progress(it)!! }
        assertEquals(path.sorted(), path); assertEquals(1f, path.last())
        assertEquals(OnboardingFlow.progress(OnboardingStep.Account), OnboardingFlow.progress(OnboardingStep.SignUp))
    }

    @Test fun optionalDetailsBecomeAProfileOnlyWhenGiven() {
        assertNull(OnboardingFlow.profile("", "  "))
        assertNull(OnboardingFlow.profile("200", ""))
        assertEquals(CarePatientProfile(age = 62, illness = ""), OnboardingFlow.profile("62", ""))
        assertEquals(CarePatientProfile(illness = "Breast cancer"), OnboardingFlow.profile("", " Breast cancer "))
    }
}
