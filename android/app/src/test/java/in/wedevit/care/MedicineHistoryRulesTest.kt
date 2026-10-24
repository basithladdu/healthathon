package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class MedicineHistoryRulesTest {
    private val now=100_000L
    private fun event(outcome:String="taken",planned:Long?=null,taken:Long?=null)=MedicineIntakeEvent("event","medicine","Synthetic medicine",outcome,planned,taken,loggedAt=now)
    @Test fun statusesUseOnlyActualScheduledAndReportedTimes() {
        assertEquals("Taken reported",MedicineHistoryRules.status(event(),now))
        assertEquals("Taken reported",MedicineHistoryRules.status(event(planned=100,taken=100),now))
        assertEquals("Late reported",MedicineHistoryRules.status(event(planned=100,taken=101),now))
        assertEquals("Skipped reported",MedicineHistoryRules.status(event("skipped"),now))
        assertEquals("Upcoming",MedicineHistoryRules.status(event("planned",planned=now+1),now))
        assertEquals("Not logged",MedicineHistoryRules.status(event("planned",planned=now-1),now))
        assertEquals("Check-off corrected",MedicineHistoryRules.status(event("unmarked"),now))
    }
    @Test fun unknownTimesAreAllowedButNeverFabricated() {
        val report=event();MedicineHistoryRules.validate(report,now)
        assertNull(report.plannedAt);assertNull(report.reportedTakenAt)
        assertThrows(IllegalArgumentException::class.java){MedicineHistoryRules.validate(event("planned"),now)}
        assertThrows(IllegalArgumentException::class.java){MedicineHistoryRules.validate(event(taken=now+1),now)}
        assertThrows(IllegalArgumentException::class.java){MedicineHistoryRules.validate(event("skipped",taken=100),now)}
    }
    @Test fun linkedReportResolvesPlanRegardlessOfEntryOrderButNotAnotherDose() {
        val plan=event("planned",planned=100)
        val taken=event(planned=100,taken=101).copy(id="taken",loggedAt=1)
        assertEquals("Plan followed by a report",MedicineHistoryRules.status(plan,listOf(taken,plan),now))
        assertEquals("Plan followed by a report",MedicineHistoryRules.status(plan,listOf(plan,taken.copy(outcome="skipped",reportedTakenAt=null)),now))
        assertEquals("Not logged",MedicineHistoryRules.status(plan,listOf(taken.copy(plannedAt=200)),now))
        assertEquals("Not logged",MedicineHistoryRules.status(plan,listOf(taken.copy(medicineId="other")),now))
    }
    @Test fun pastCoursesDoNotDeleteOrMixTheOriginalMedicines() {
        val rows=listOf(CareItem("current","p","medicine","Current"),CareItem("past","p","medicine","Past"),CareItem("other","other","medicine","Other"))
        val snapshot=MedicineHistorySnapshot(listOf(MedicineCourse("past","Past","Old directions",true,100)))
        assertEquals(listOf("current"),MedicineHistoryRules.current(rows,"p",snapshot).map{it.id})
        assertEquals(3,rows.size);assertEquals("Old directions",snapshot.courses.single().directions)
        assertEquals(listOf("current","past"),MedicineHistoryRules.current(rows,"p",MedicineHistorySnapshot()).map{it.id})
    }
    @Test fun legacyCheckboxCanSupplyADateButNotTakenOrLoggedTimes() {
        val item=CareItem("m","p","medicine","Sample",doneOn="2026-10-23")
        assertEquals("2026-10-23",MedicineHistoryRules.legacyDate(item))
        assertNull(MedicineHistoryRules.legacyDate(item.copy(doneOn="unknown")))
        assertNull(MedicineHistoryRules.legacyDate(item.copy(doneOn="")))
    }
}
