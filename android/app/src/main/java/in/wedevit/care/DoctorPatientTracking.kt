package `in`.wedevit.care

import java.time.Instant

enum class SampleReportSource { PATIENT, FAMILY }
enum class SampleDoseOutcome { TAKEN, SKIPPED }

/** Reported entries, never evidence of ingestion or a prescription. */
data class SampleMedicineDose(
    val id:String, val patientId:String, val medicineLabel:String, val scheduledAt:Long,
    val reportedTakenAt:Long?=null, val loggedAt:Long?=null,
    val outcome:SampleDoseOutcome?=null, val source:SampleReportSource?=null,
) {
    fun status(asOf:Long):String = when {
        source == null -> if(scheduledAt > asOf) "Upcoming" else "Not logged"
        outcome == SampleDoseOutcome.SKIPPED -> "Skipped reported"
        outcome == SampleDoseOutcome.TAKEN && reportedTakenAt != null ->
            if(reportedTakenAt > scheduledAt) "Late reported" else "Taken reported"
        scheduledAt > asOf -> "Upcoming"
        else -> "Not logged"
    }
}

data class SamplePatientCheckIn(val id:String,val patientId:String,val at:Long,val text:String,val source:SampleReportSource)

object DoctorPatientTracking {
    val asOf:Long = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
    fun doses(patientId:String):List<SampleMedicineDose> {
        if(DoctorDemoData.patients.none { it.id == patientId }) return emptyList()
        val hour=3_600_000L
        return listOf(
            SampleMedicineDose("$patientId-dose-1",patientId,"Sample medicine A",asOf-28*hour,asOf-28*hour,asOf-26*hour,SampleDoseOutcome.TAKEN,SampleReportSource.PATIENT),
            SampleMedicineDose("$patientId-dose-2",patientId,"Sample medicine B",asOf-22*hour,asOf-21*hour,asOf-20*hour,SampleDoseOutcome.TAKEN,SampleReportSource.FAMILY),
            SampleMedicineDose("$patientId-dose-3",patientId,"Sample medicine A",asOf-4*hour,loggedAt=asOf-3*hour,outcome=SampleDoseOutcome.SKIPPED,source=SampleReportSource.PATIENT),
            SampleMedicineDose("$patientId-dose-4",patientId,"Sample medicine B",asOf-2*hour),
            SampleMedicineDose("$patientId-dose-5",patientId,"Sample medicine A",asOf+4*hour),
        )
    }
    fun checkIns(patientId:String):List<SamplePatientCheckIn> {
        val patient=DoctorDemoData.patients.firstOrNull {it.id==patientId} ?: return emptyList()
        return listOf(
            SamplePatientCheckIn("$patientId-check-1",patientId,asOf-2*86_400_000L,"Would like time to ask questions at the next visit.",SampleReportSource.PATIENT),
            SamplePatientCheckIn("$patientId-check-2",patientId,asOf-86_400_000L,"Family is arranging practical help for the next appointment.",SampleReportSource.FAMILY),
            SamplePatientCheckIn("$patientId-check-3",patientId,asOf,patient.summary,SampleReportSource.PATIENT),
        )
    }
}
