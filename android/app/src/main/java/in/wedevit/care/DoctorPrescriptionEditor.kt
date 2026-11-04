package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            OutlinedTextField(title,{title=it.take(200)},label={Text(t("Prescription label", "पर्चे का नाम"))},enabled=!busy)
            OutlinedTextField(date,{date=it.take(10)},label={Text(t("Prescription date (YYYY-MM-DD, optional)", "पर्चे की तारीख़ (YYYY-MM-DD, चाहें तो)"))},enabled=!busy)
            rows.forEachIndexed { index,row ->
                SurfaceCard {
                    Text(t("Medicine ${index+1}", "दवा ${index+1}"),fontSize=18.sp)
                    OutlinedTextField(row.name,{value->rows=rows.map {if(it.id==row.id)it.copy(name=value.take(200))else it}},label={Text(t("Medicine name", "दवा का नाम"))},enabled=!busy)
                    MedicineNameSuggestions(row.name,enabled=!busy){value->rows=rows.map {if(it.id==row.id)it.copy(name=value)else it}}
                    OutlinedTextField(row.instructions,{value->rows=rows.map {if(it.id==row.id)it.copy(instructions=value.take(4000))else it}},label={Text(t("Instructions as prescribed", "लिखे गए निर्देश"))},enabled=!busy)
                    TextButton(onClick={rows=rows.filterNot {it.id==row.id}},enabled=!busy&&rows.size>1,modifier=Modifier.heightIn(min=48.dp)){Text(t("Remove from current list", "अभी की सूची से हटाएँ"))}
                }
            }
            OutlinedButton(onClick={rows=rows+PrescriptionMedicineInput(UUID.randomUUID().toString(),"","")},enabled=!busy&&rows.size<50,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(t("Add another medicine", "एक और दवा जोड़ें"))}
            if(replacing) TextButton(onClick={onScan(title,date,rows)},enabled=!busy){Text(t("Scan prescription and review", "पर्चा स्कैन करके जाँचें"))}
            if(replacing&&currentExists) Row { Checkbox(confirmed,{confirmed=it},enabled=!busy);Text(t("Replace the entire current prescription after saving this reviewed list. Keep the previous prescription and its history in Past.", "यह जाँची सूची सहेजने के बाद पूरा मौजूदा पर्चा बदलें। पिछला पर्चा और उसका इतिहास पुराने में रखें।")) }
            Row {Checkbox(reviewed,{reviewed=it},enabled=!busy);Text(t("I reviewed every medicine name and instruction against the prescription.", "मैंने हर दवा का नाम और निर्देश पर्चे से मिला लिया है।"))}
            error?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
            saveError?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
        }
    },confirmButton={TextButton(enabled=!busy&&reviewed&&(!replacing||!currentExists||confirmed),onClick={
        if(title.isBlank()||rows.any {it.name.isBlank()})error=t("Add a prescription label and each medicine name.", "पर्चे का नाम और हर दवा का नाम लिखें।")
        else onSave(title,date,rows,!replacing||!currentExists||confirmed)
    }){Text(if(busy)"Saving…" else if(replacing)t("Save reviewed prescription", "जाँचा पर्चा सहेजें") else t("Save list", "सूची सहेजें"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(t("Cancel", "रद्द करें"))}})
}
