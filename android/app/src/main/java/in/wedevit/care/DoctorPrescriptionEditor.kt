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
) {
    var rows by remember { mutableStateOf(initial.ifEmpty { listOf(PrescriptionMedicineInput(UUID.randomUUID().toString(),"","")) }) }
    var title by remember { mutableStateOf(initialTitle) };var date by remember { mutableStateOf(initialDate) }
    var confirmed by remember { mutableStateOf(false) };var reviewed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(replacing)"New prescription" else "Edit current list")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Copy the prescriber's instructions exactly. This form does not recommend drugs or doses.")
            OutlinedTextField(title,{title=it.take(200)},label={Text("Prescription label")},enabled=!busy)
            OutlinedTextField(date,{date=it.take(10)},label={Text("Prescription date (YYYY-MM-DD, optional)")},enabled=!busy)
            rows.forEachIndexed { index,row ->
                SurfaceCard {
                    Text("Medicine ${index+1}",fontSize=18.sp)
                    OutlinedTextField(row.name,{value->rows=rows.map {if(it.id==row.id)it.copy(name=value.take(200))else it}},label={Text("Medicine name")},enabled=!busy)
                    OutlinedTextField(row.instructions,{value->rows=rows.map {if(it.id==row.id)it.copy(instructions=value.take(4000))else it}},label={Text("Instructions as prescribed")},enabled=!busy)
                    TextButton(onClick={rows=rows.filterNot {it.id==row.id}},enabled=!busy&&rows.size>1,modifier=Modifier.heightIn(min=48.dp)){Text("Remove from current list")}
                }
            }
            OutlinedButton(onClick={rows=rows+PrescriptionMedicineInput(UUID.randomUUID().toString(),"","")},enabled=!busy&&rows.size<50,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Add another medicine")}
            if(replacing) TextButton(onClick={onScan(title,date,rows)},enabled=!busy){Text("Scan prescription and review")}
            if(replacing&&currentExists) Row { Checkbox(confirmed,{confirmed=it},enabled=!busy);Text("Replace the entire current prescription after saving this reviewed list. Keep the previous prescription and its history in Past.") }
            if(!replacing) Text("Removing a medicine only ends its current-list membership. Earlier directions, prescriptions and intake reports are retained.")
            Row {Checkbox(reviewed,{reviewed=it},enabled=!busy);Text("I reviewed every medicine name and instruction against the prescription.")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            saveError?.let {Text(it,color=MaterialTheme.colorScheme.error);Text("Cancel and refresh the current list before reviewing again. After a connection error, check the saved state rather than assuming success or failure.")}
        }
    },confirmButton={TextButton(enabled=!busy&&reviewed&&(!replacing||!currentExists||confirmed),onClick={
        if(title.isBlank()||rows.any {it.name.isBlank()})error="Add a prescription label and each medicine name."
        else onSave(title,date,rows,!replacing||!currentExists||confirmed)
    }){Text(if(busy)"Saving…" else if(replacing)"Save reviewed prescription" else "Save list")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Cancel")}})
}
