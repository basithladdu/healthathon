package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CarePatientSummaryTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val zone = ZoneId.of("UTC")
    private fun note(day: LocalDate, symptom: String = "Pain", rating: Int? = null, ratings: Map<String, Int> = emptyMap(), id: String = "$symptom-$day-${rating}-${ratings}") =
        CareItem(id = id, personId = "p", kind = "checkin", title = symptom, detail = SymptomNoteCodec.encode(SymptomNote(symptom = symptom, rating = rating, observedDate = day.toString(), observedTime = "09:00", respondent = "patient", author = "Rahul",
            painBodySites = ratings.keys.toList(), painBodyRatings = ratings)))
    private fun med(done: Boolean, id: String) = CareItem(id = id, personId = "p", kind = "medicine", title = id, doneOn = if (done) today.toString() else "")
    private fun visit(id: String, due: Long) = CareItem(id = id, personId = "p", kind = "visit", title = id, due = due)
    private fun version(n: Int, at: Long) = CareVersion(personId = "p", version = n, personName = "Rahul", source = "", priorities = "", participants = "", topics = "", questions = "", nextSteps = "", doctor = "Dr Rao", published = at, contentHash = "h$n")

    @Test fun trendDirection() {
        assertEquals(SummaryTrend.UP, CarePatientSummaryRules.trend(8, 6)); assertEquals(SummaryTrend.DOWN, CarePatientSummaryRules.trend(6, 8))
        assertEquals(SummaryTrend.SAME, CarePatientSummaryRules.trend(5, 5)); assertEquals(SummaryTrend.FIRST, CarePatientSummaryRules.trend(5, null))
    }

    @Test fun plainSymptomFallsFromEightToSix() {
        val items = listOf(note(today.minusDays(2), rating = 8), note(today, rating = 6))
        val line = CarePatientSummaryRules.symptoms(items, "p", today).single()
        assertEquals(6, line.value); assertEquals(8, line.previous); assertEquals(SummaryTrend.DOWN, line.trend)
        assertEquals("Pain 6/10, down from 8", CarePatientSummaryRules.symptomLine(line, false))
    }

    @Test fun painIsSplitBySpotAndHighestComesFirst() {
        val items = listOf(note(today.minusDays(1), ratings = mapOf("head" to 4, "chest" to 7)), note(today, ratings = mapOf("head" to 6, "chest" to 7)))
        val lines = CarePatientSummaryRules.symptoms(items, "p", today)
        assertEquals(2, lines.size); assertEquals(7, lines[0].value); assertEquals(SummaryTrend.SAME, lines[0].trend)
        assertEquals(SummaryTrend.UP, lines.first { it.site == "head" }.trend)
        assertTrue(CarePatientSummaryRules.symptomLine(lines.first { it.site == "head" }, false).startsWith("Pain (Head) 6/10, up from 4"))
    }

    @Test fun oldEntriesAndOtherPeopleAreIgnored() {
        assertTrue(CarePatientSummaryRules.symptoms(listOf(note(today.minusDays(9), rating = 5)), "p", today).isEmpty())
        assertTrue(CarePatientSummaryRules.symptoms(listOf(note(today, rating = 5).copy(personId = "other")), "p", today).isEmpty())
        assertEquals("No symptoms logged this week", CarePatientSummaryRules.noSymptoms(false))
    }

    @Test fun firstEntryHasNoComparison() {
        val line = CarePatientSummaryRules.symptoms(listOf(note(today, "Nausea", rating = 3)), "p", today).single()
        assertEquals(SummaryTrend.FIRST, line.trend); assertNull(line.previous)
    }

    @Test fun medicineCountsAndWording() {
        val s = CarePatientSummaryRules.build(listOf(med(true, "a"), med(false, "b"), med(false, "c")), emptyList(), "p", emptyList(), today, zone)
        assertEquals(3, s.medicineTotal); assertEquals(1, s.medicineTicked)
        assertEquals("1 of 3 today", CarePatientSummaryRules.medicineStatus(s.medicineTotal, s.medicineTicked, false))
        assertEquals("No medicines yet", CarePatientSummaryRules.medicineCount(0, false)); assertEquals("1 medicine", CarePatientSummaryRules.medicineCount(1, false)); assertEquals("3 medicines", CarePatientSummaryRules.medicineCount(3, false))
    }

    @Test fun nextVisitIsTheSoonestUpcoming() {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli(); val day = 86_400_000L
        val s = CarePatientSummaryRules.build(listOf(visit("later", dayStart + 5 * day), visit("soon", dayStart + 2 * day), visit("past", dayStart - day)), emptyList(), "p", emptyList(), today, zone)
        assertEquals("soon", s.nextVisit?.id)
        assertNull(CarePatientSummaryRules.build(emptyList(), emptyList(), "p", emptyList(), today, zone).nextVisit)
        assertEquals("No visit planned", CarePatientSummaryRules.nextVisit(null, false, zone))
    }

    @Test fun latestNoteAndEmptyStates() {
        val s = CarePatientSummaryRules.build(emptyList(), listOf(version(1, 1000), version(2, 5000)), "p", emptyList(), today, zone)
        assertEquals(2, s.latestNote?.version); assertEquals("Version 2, 5 Nov 2026, Dr Rao", CarePatientSummaryRules.noteLine(s.latestNote, "5 Nov 2026", false))
        assertEquals("No care note yet", CarePatientSummaryRules.noteLine(null, "", false))
    }

    @Test fun planStatusFromRecords() {
        assertEquals(EdKind.NONE, CarePatientSummaryRules.build(emptyList(), emptyList(), "p", emptyList(), today, zone).plan.kind)
        val draft = EctprRecord(id = "e1", patientId = "p", createdAt = "2026-10-01")
        val s = CarePatientSummaryRules.build(emptyList(), emptyList(), "p", listOf(draft), today, zone)
        assertEquals(EdKind.UNSIGNED, s.plan.kind); assertEquals("Draft, not signed yet", CarePatientSummaryRules.planLine(s.plan, false))
        assertEquals("Out of date, review was due 2026-09-01", CarePatientSummaryRules.planLine(SummaryPlan(EdKind.OUT_OF_DATE, "2026-09-01"), false))
        assertEquals("Active, review due 2027-01-01", CarePatientSummaryRules.planLine(SummaryPlan(EdKind.ACTIVE, "2027-01-01"), false))
    }

    @Test fun collapsedLineJoinsTopSymptomMedicinesAndVisit() {
        val s = CarePatientSummaryRules.build(listOf(note(today.minusDays(1), rating = 8), note(today, rating = 6), med(false, "a")), emptyList(), "p", emptyList(), today, zone)
        assertEquals("Pain 6/10, down from 8 · 1 medicine · No visit planned", CarePatientSummaryRules.collapsed(s, false, zone))
        assertNotNull(s)
    }
}
