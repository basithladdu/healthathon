package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/** A report-time view: a later entry must not leak into an earlier snapshot. */
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

@Composable fun DoctorMedicineContent(doses:List<SampleMedicineDose>,asOf:Long,zone:ZoneId=ZoneId.systemDefault()) { val t = careText();
    Column(Modifier.fillMaxWidth().testTag("doctor-medicine-content"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(t("Medicine reports", "दवा की सूचनाएँ"),Modifier.a11yHeading(),fontSize=22.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk)
        Text("As of ${DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a",Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(asOf))} · ${zone.id}",fontSize=14.sp,lineHeight=20.sp,color=DoctorCardSub)
        if(doses.isEmpty()) Surface(color=Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DoctorHairline),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.Schedule,null,tint=Rust,modifier=Modifier.size(24.dp));Text(t("No medicine reports recorded.", "दवा की कोई सूचना दर्ज नहीं।"),fontSize=16.sp,color=DoctorCardInk)}
        }
        doses.forEach {dose ->
            val view=doctorMedicinePresentation(dose,asOf,zone)
            val good=view.status=="Taken reported"
            Surface(color=Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DoctorHairline),modifier=Modifier.fillMaxWidth().testTag("doctor-medicine-${dose.id}")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(dose.medicineLabel,fontSize=18.sp,fontWeight=FontWeight.SemiBold,lineHeight=25.sp,color=DoctorCardInk)
                    Surface(color=if(good) DoctorStatusGoodBg else DoctorStatusWarnBg,shape=RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(good) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,null,tint=if(good) DoctorStatusGood else DoctorStatusWarn,modifier=Modifier.size(18.dp))
                            Text(view.status,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(good) DoctorStatusGood else DoctorStatusWarn)
                        }
                    }
                    MedicineTime("Planned",view.planned)
                    MedicineTime(t("Reported taken", "लेने का बताया समय"),view.taken)
                    MedicineTime("Logged",view.logged)
                    HorizontalDivider(color=DoctorHairline)
                    Text(t("Reporter: ${view.reporter}", "बताने वाले: ${view.reporter}"),fontSize=14.sp,color=DoctorCardSub)
                }
            }
        }
    }
}

@Composable private fun MedicineTime(label:String,value:String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,fontSize=14.sp,color=DoctorCardSub)
        Text(value,fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium,color=DoctorCardInk)
    }
}
