package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DoctorMedicinePresentation(val status:String,val planned:String,val taken:String,val logged:String,val reporter:String)

/** A report-time view: a later entry must not leak into an earlier sample snapshot. */
fun doctorMedicinePresentation(dose:SampleMedicineDose,asOf:Long,zone:ZoneId):DoctorMedicinePresentation {
    fun time(value:Long)=DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a",Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(value))
    val snapshot=DoctorProgressData.historicalDose(dose,asOf)
    val knownLog=snapshot.loggedAt
    val knownReport=knownLog!=null && snapshot.source!=null
    val taken=snapshot.reportedTakenAt
    val status=when {
        !knownReport -> if(dose.scheduledAt>asOf) "Upcoming" else "Not logged"
        dose.outcome==SampleDoseOutcome.SKIPPED -> "Skipped reported"
        taken!=null -> if(taken>dose.scheduledAt) "Late reported" else "Taken reported"
        dose.outcome==SampleDoseOutcome.TAKEN -> "Taken reported · time not recorded"
        dose.scheduledAt>asOf -> "Upcoming"
        else -> "Not logged"
    }
    return DoctorMedicinePresentation(status,time(dose.scheduledAt),taken?.let(::time) ?: if(knownReport && dose.outcome==SampleDoseOutcome.TAKEN) "Time not recorded" else "Not reported",knownLog?.let(::time) ?: "Not logged",
        if(knownReport) when(dose.source) {SampleReportSource.PATIENT->"Patient";SampleReportSource.FAMILY->"Family";else->"Not recorded"} else "Not recorded")
}

@Composable fun DoctorMedicineContent(doses:List<SampleMedicineDose>,asOf:Long,zone:ZoneId=ZoneId.systemDefault()) {
    Column(Modifier.fillMaxWidth().testTag("doctor-medicine-content"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Medicine reports",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Text("Fictional sample · as of ${DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a",Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(asOf))} · ${zone.id}",fontSize=14.sp,lineHeight=20.sp)
        Text("Reported activity is not proof of ingestion or prescribing advice.",fontSize=14.sp,lineHeight=20.sp,color=Ink.copy(alpha=.75f))
        Text("Planned = schedule · Reported taken = reported intake · Logged = entry time",fontSize=14.sp,lineHeight=20.sp,color=Ink.copy(alpha=.75f))
        if(doses.isEmpty()) Text("No medicine reports recorded.")
        doses.forEachIndexed {index,dose ->
            val view=doctorMedicinePresentation(dose,asOf,zone)
            val accent=when(index%3) {0->SaharaApricot;1->SaharaSoftBlue;else->SaharaLavender}
            Surface(color=Paper,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Ink.copy(alpha=.1f)),modifier=Modifier.fillMaxWidth().testTag("doctor-medicine-${dose.id}")) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(dose.medicineLabel,fontSize=18.sp,fontWeight=FontWeight.SemiBold,lineHeight=25.sp)
                    Surface(color=accent,shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(if(view.status=="Taken reported") Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,null,tint=Rust,modifier=Modifier.size(20.dp))
                            Text(view.status,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                    MedicineTime("Planned",view.planned)
                    MedicineTime("Reported taken",view.taken)
                    MedicineTime("Logged",view.logged)
                    HorizontalDivider(color=Ink.copy(alpha=.1f))
                    Text("Reporter: ${view.reporter}",fontSize=14.sp,color=Ink.copy(alpha=.75f))
                }
            }
        }
    }
}

@Composable private fun MedicineTime(label:String,value:String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=14.sp,color=Ink.copy(alpha=.7f))
        Text(value,fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium)
    }
}
