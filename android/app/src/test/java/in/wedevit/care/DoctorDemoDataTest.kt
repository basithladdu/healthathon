package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorDemoDataTest {
    private val now = 1_800_000_000_000L
    private val patient = DoctorDemoData.patients.first().id
    private fun appointment(id: String = "test", start: Long = now + 3_600_000, duration: Int = 30) =
        DoctorDemoAppointment(id, patient, start, duration, "In person")

    @Test fun fixturesAreFictionalCompleteAndAppointmentsReferencePatients() {
        assertEquals(6, DoctorDemoData.patients.size)
        assertEquals(6, DoctorDemoData.patients.map { it.id }.distinct().size)
        DoctorDemoData.patients.forEach {
            assertTrue(it.name.contains("Demo")); assertTrue(it.careNotes.isNotEmpty())
            assertTrue(it.medicines.isNotEmpty()); assertTrue(it.reportSummaries.isNotEmpty()); assertTrue(it.observations.isNotEmpty())
        }
        val values = DoctorDemoData.seedAppointments(now)
        assertEquals(values.size, values.map { it.id }.distinct().size)
        values.forEach { assertNull(DoctorDemoData.validateAppointment(it, values, now)) }
        assertTrue(values.any { it.status == "Completed" && it.startMillis < now })
        assertEquals(values, DoctorDemoData.seedAppointments(now))
    }

    @Test fun invalidPatientTimeDurationTypeAndStatusAreRejected() {
        val good = appointment()
        listOf(good.copy(id = ""), good.copy(patientId = "real-patient"), good.copy(startMillis = now),
            good.copy(startMillis = -1), good.copy(startMillis = Long.MAX_VALUE), good.copy(durationMinutes = 0),
            good.copy(durationMinutes = -30), good.copy(durationMinutes = 481), good.copy(type = "Automatic call"),
            good.copy(status = "Unknown"), good.copy(note = "x".repeat(1001))).forEach {
            assertNotNull(DoctorDemoData.validateAppointment(it, emptyList(), now))
        }
    }

    @Test fun overlapIsCheckedAcrossPatientsAndCancelledBookingsDoNotBlock() {
        val first = appointment("first")
        val otherPatient = DoctorDemoData.patients[1].id
        val clash = appointment("second", first.startMillis + 60_000).copy(patientId = otherPatient)
        assertNotNull(DoctorDemoData.validateAppointment(clash, listOf(first), now))
        assertNull(DoctorDemoData.validateAppointment(clash, listOf(first.copy(status = "Cancelled")), now))
        assertNull(DoctorDemoData.validateAppointment(clash.copy(status = "Cancelled"), listOf(first), now))
        assertNull(DoctorDemoData.validateAppointment(clash.copy(startMillis = first.endMillis), listOf(first), now))
    }

    @Test fun reschedulingIgnoresItselfButNotOtherBookings() {
        val first = appointment("first")
        val second = appointment("second", first.startMillis + 3_600_000)
        assertNull(DoctorDemoData.validateAppointment(first.copy(note = "Updated demo note"), listOf(first, second), now))
        assertNotNull(DoctorDemoData.validateAppointment(first.copy(startMillis = second.startMillis), listOf(first, second), now))
        assertNull(DoctorDemoData.validateAppointment(first.copy(startMillis = second.endMillis), listOf(first, second), now))
    }

    @Test fun completedHistoricalBookingIsValidButCannotBeRebookedInPast() {
        val historical = appointment().copy(startMillis = now - 3_600_000, status = "Completed")
        assertNull(DoctorDemoData.validateAppointment(historical, emptyList(), now))
        assertNotNull(DoctorDemoData.validateAppointment(historical.copy(status = "Scheduled"), emptyList(), now))
    }

    @Test fun cancelAndCompleteAreTerminalButIdempotent() {
        assertNull(DoctorDemoData.validateStatusChange("Scheduled", "Cancelled"))
        assertNull(DoctorDemoData.validateStatusChange("Scheduled", "Completed"))
        assertNull(DoctorDemoData.validateStatusChange("Cancelled", "Cancelled"))
        assertNull(DoctorDemoData.validateStatusChange("Completed", "Completed"))
        assertNotNull(DoctorDemoData.validateStatusChange("Cancelled", "Completed"))
        assertNotNull(DoctorDemoData.validateStatusChange("Completed", "Cancelled"))
        assertNotNull(DoctorDemoData.validateStatusChange("Cancelled", "Scheduled"))
        assertNotNull(DoctorDemoData.validateStatusChange("Completed", "Scheduled"))
    }
}
