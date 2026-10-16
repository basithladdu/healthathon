package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorPatientHubTest {
    @Test fun narrowAndLargeTextUseReadableSingleColumn() {
        assertTrue(doctorHubSingleColumn(339f, 1f))
        assertFalse(doctorHubSingleColumn(340f, 1f))
        assertTrue(doctorHubSingleColumn(400f, 1.3f))
        assertFalse(doctorHubSingleColumn(400f, 1.2f))
    }
}
