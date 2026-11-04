package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CareDevicesTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun device(due: LocalDate? = null, id: String = "a", type: CareDeviceType = CareDeviceType.PORT) = CareDevice(id = id, type = type, inserted = LocalDate.of(2026, 10, 1), changeDue = due, centre = "Sample centre")

    @Test fun dueCountsWholeDaysFromToday() {
        assertEquals(CareDeviceDue(CareDeviceDueKind.LATER, 5), CareDeviceRules.due(device(today.plusDays(5)), today))
        assertEquals(CareDeviceDue(CareDeviceDueKind.LATER, 1), CareDeviceRules.due(device(today.plusDays(1)), today))
        assertEquals(CareDeviceDue(CareDeviceDueKind.TODAY, 0), CareDeviceRules.due(device(today), today))
        assertEquals(CareDeviceDue(CareDeviceDueKind.OVERDUE, 3), CareDeviceRules.due(device(today.minusDays(3)), today))
        assertNull(CareDeviceRules.due(device(null), today))
    }

    @Test fun datesMustMakeSense() {
        assertNull(CareDeviceRules.error(today, today.plusDays(30), today))
        assertNull(CareDeviceRules.error(null, null, today))
        assertNull(CareDeviceRules.error(null, today.minusDays(2), today))
        assertEquals(CareDeviceError.INSERTED_FUTURE, CareDeviceRules.error(today.plusDays(1), null, today))
        assertEquals(CareDeviceError.CHANGE_BEFORE_INSERTED, CareDeviceRules.error(today, today, today))
        assertEquals(CareDeviceError.CHANGE_BEFORE_INSERTED, CareDeviceRules.error(today, today.minusDays(1), today))
    }

    @Test fun anyYesMeansTellTheCareTeamToday() {
        val fine = CareDeviceRules.record(device(), today, emptySet())
        assertFalse(CareDeviceRules.tellCareTeam(fine, today))
        val red = CareDeviceRules.record(device(), today, setOf(CareDeviceSign.REDNESS))
        assertTrue(CareDeviceRules.tellCareTeam(red, today))
        assertTrue(CareDeviceRules.tellCareTeam(listOf(fine, red.copy(id = "b")), today))
        // Yesterday's yes does not show today.
        assertFalse(CareDeviceRules.tellCareTeam(red, today.plusDays(1)))
        assertFalse(CareDeviceRules.tellCareTeam(device(), today))
    }

    @Test fun aSecondCheckTheSameDayReplacesTheFirstAndOldChecksAreDropped() {
        val once = CareDeviceRules.record(device(), today, setOf(CareDeviceSign.PAIN, CareDeviceSign.FEVER))
        val again = CareDeviceRules.record(once, today, emptySet())
        assertEquals(1, again.checks.size)
        assertFalse(CareDeviceRules.tellCareTeam(again, today))
        var many = device()
        repeat(CareDeviceRules.MAX_CHECKS + 5) { many = CareDeviceRules.record(many, today.minusDays(it.toLong()), emptySet()) }
        assertEquals(CareDeviceRules.MAX_CHECKS, many.checks.size)
        assertEquals(today, many.checks.last().day)
        assertTrue(many.checks.zipWithNext().all { (a, b) -> a.day < b.day })
    }

    @Test fun soonestChangeFirstAndUndatedLast() {
        val list = listOf(device(null, "none"), device(today.plusDays(20), "later"), device(today.minusDays(1), "overdue"))
        assertEquals(listOf("overdue", "later", "none"), CareDeviceRules.sorted(list).map { it.id })
    }

    @Test fun upsertReplacesByIdOrAdds() {
        val list = listOf(device(id = "a"))
        assertEquals(1, CareDeviceRules.upsert(list, device(id = "a").copy(centre = "Other")).size)
        assertEquals("Other", CareDeviceRules.upsert(list, device(id = "a").copy(centre = "Other")).single().centre)
        assertEquals(2, CareDeviceRules.upsert(list, device(id = "b")).size)
    }

    @Test fun encodeAndDecodeKeepEverything() {
        val d = CareDeviceRules.record(device(today.plusDays(10)).copy(notes = "Sample note"), today, setOf(CareDeviceSign.DISCHARGE, CareDeviceSign.MOVED))
        val plain = CareDevice(id = "z", type = CareDeviceType.STOMA)
        assertEquals(listOf(d, plain), CareDeviceRules.decode(CareDeviceRules.encode(listOf(d, plain))))
        assertEquals(emptyList<CareDevice>(), CareDeviceRules.decode(null))
        assertEquals(emptyList<CareDevice>(), CareDeviceRules.decode("not json"))
        // An unknown type from a newer app reads as Other rather than losing the device.
        val unknown = """{"v":1,"devices":[{"id":"q","type":"FUTURE_THING","centre":"","notes":""}]}"""
        assertEquals(CareDeviceType.OTHER, CareDeviceRules.decode(unknown).single().type)
    }

    @Test fun palliativePhoneComesFromTheNewestPlanThatHasOne() {
        val old = EctprRecord(id = "1", patientId = "p", createdAt = "2026-10-01T00:00:00Z", careTeam = CareTeam(palliativePhone24h = "080 1111 1111"))
        val newer = EctprRecord(id = "2", patientId = "p", createdAt = "2026-10-20T00:00:00Z", careTeam = CareTeam(palliativePhone24h = " 080 2222 2222 "))
        val blank = EctprRecord(id = "3", patientId = "p", createdAt = "2026-11-01T00:00:00Z")
        assertEquals("080 2222 2222", CareDeviceRules.palliativePhone(listOf(old, newer, blank)))
        assertEquals("", CareDeviceRules.palliativePhone(listOf(blank)))
    }
}
