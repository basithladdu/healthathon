package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

/** Reviewed rows only: no parsing, provider calls, or guessed medicine directions. */
@Composable internal fun DoctorPrescriptionEditor(
    initial: List<PrescriptionMedicineInput>, replacing: Boolean, currentExists: Boolean,
    initialTitle: String, initialDate: String, busy: Boolean,
    onDismiss: () -> Unit, onScan: (String, String, List<PrescriptionMedicineInput>) -> Unit,
    onSave: (String, String, List<PrescriptionMedicineInput>, Boolean) -> Unit,
    saveError: String? = null,
) { val t = careText();
    var rows by remember { mutableStateOf(initial.ifEmpty { listOf(PrescriptionMedicineInput(UUID.randomUUID().toString(),"","")) }) }
    var title by remember { mutableStateOf(initialTitle) };var date by remember { mutableStateOf(initialDate) }
    var confirmed by remember { mutableStateOf(false) };var reviewed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(replacing)t("New prescription", "नया पर्चा") else t("Edit current list", "अभी की सूची बदलें"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{title=it.take(200)},label={Text(t("Name", "नाम"))},enabled=!busy,singleLine=true,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(date,{date=it.take(10)},label={Text(t("Date (YYYY-MM-DD)", "तारीख़ (YYYY-MM-DD)"))},enabled=!busy,singleLine=true,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())
            rows.forEachIndexed { index,row ->
                HorizontalDivider(color=DoctorHairline)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(t("Medicine ${index+1}", "दवा ${index+1}"),fontSize=16.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk,modifier=Modifier.weight(1f).a11yHeading())
                    IconButton(onClick={rows=rows.filterNot {it.id==row.id}},enabled=!busy&&rows.size>1,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Delete,t("Remove", "हटाएँ"),tint=if(!busy&&rows.size>1) RustText else DoctorCardFaint)}
                }
                OutlinedTextField(row.name,{value->rows=rows.map {if(it.id==row.id)it.copy(name=value.take(200))else it}},label={Text(t("Medicine name", "दवा का नाम"))},enabled=!busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())
                MedicineNameSuggestions(row.name,enabled=!busy){value->rows=rows.map {if(it.id==row.id)it.copy(name=value)else it}}
                OutlinedTextField(row.instructions,{value->rows=rows.map {if(it.id==row.id)it.copy(instructions=value.take(4000))else it}},label={Text(t("Directions", "निर्देश"))},enabled=!busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick={rows=rows+PrescriptionMedicineInput(UUID.randomUUID().toString(),"","")},enabled=!busy&&rows.size<50,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Add medicine", "दवा जोड़ें"))}
            if(replacing) OutlinedButton(onClick={onScan(title,date,rows)},enabled=!busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.DocumentScanner,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Scan prescription", "पर्चा स्कैन करें"))}
            HorizontalDivider(color=DoctorHairline)
            if(replacing&&currentExists) PrescriptionCheck(confirmed,!busy,t("Replace current prescription", "अभी का पर्चा बदलें")){confirmed=it}
            PrescriptionCheck(reviewed,!busy,t("Checked against the prescription", "पर्चे से मिला लिया")){reviewed=it}
            error?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
            saveError?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
        }
    },confirmButton={Button(enabled=!busy&&reviewed&&(!replacing||!currentExists||confirmed),onClick={
        if(title.isBlank()||rows.any {it.name.isBlank()})error=t("Add a name for each medicine", "हर दवा का नाम लिखें")
        else onSave(title,date,rows,!replacing||!currentExists||confirmed)
    },shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)){Text(if(busy)t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text(t("Cancel", "रद्द करें"))}},containerColor=Cream,shape=RoundedCornerShape(20.dp))
}

@Composable private fun PrescriptionCheck(checked:Boolean,enabled:Boolean,text:String,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked,onCheckedChange=null,enabled=enabled,modifier=Modifier.minimumInteractiveComponentSize())
        Text(text,Modifier.weight(1f),fontSize=15.sp,lineHeight=21.sp,color=DoctorCardInk)
    }
}
