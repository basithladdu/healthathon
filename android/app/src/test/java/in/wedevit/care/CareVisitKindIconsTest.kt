package `in`.wedevit.care

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import org.junit.Assert.*
import org.junit.Test

class CareVisitKindIconsTest {
    @Test fun visitTypeIsReadFromTheWordsTyped() {
        assertEquals(CareVisitKind.FAMILY_MEETING, CareVisitKindRules.kind("Family meeting"))
        assertEquals(CareVisitKind.HOME, CareVisitKindRules.kind("Home visit"))
        assertEquals(CareVisitKind.CLINIC, CareVisitKindRules.kind("Oncology review"))
        assertEquals(CareVisitKind.VIDEO, CareVisitKindRules.kind("Video call"))
        assertEquals(CareVisitKind.PHONE, CareVisitKindRules.kind("Phone call"))
        assertEquals(CareVisitKind.OTHER, CareVisitKindRules.kind("Dr Vikram Rao"))
    }

    @Test fun aPinOnlyForARealAddress() {
        assertTrue(CareVisitKindRules.isTypeOnly("Family meeting"))
        assertTrue(CareVisitKindRules.isTypeOnly("Home visit"))
        assertFalse(CareVisitKindRules.isTypeOnly("12 MG Road, Bengaluru"))
        assertEquals(Icons.Default.Groups, careVisitPlaceIcon("Family meeting"))
        assertEquals(Icons.Default.Place, careVisitPlaceIcon("Tata Memorial Hospital, Parel"))
    }
}
