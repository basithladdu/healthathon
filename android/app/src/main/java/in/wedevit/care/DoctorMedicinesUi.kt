@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun medicineTime(at: Long?) = at?.let { DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: "Not recorded"

/** Existing medicines and scanner remain the source of truth; history never invents dose entries. */
@Composable fun DoctorMedicines(vm: CareModel, personId: String, onAddManual: () -> Unit, onScanPrescription: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val account=vm.accountNamespace
    val sessionId=vm.cloud?.session?.collectAsStateWithLifecycle()?.value?.userId
    val entries by vm.allItems.collectAsStateWithLifecycle();val selected by vm.selected.collectAsStateWithLifecycle();val editable by vm.canEdit.collectAsStateWithLifecycle()
    val repo=remember(vm.storageNamespace) { MedicineHistoryRepo(context,vm.cloud,vm.storageNamespace) }
    val prescriptionRepo=remember(vm.storageNamespace){DoctorPrescriptionRepo(context,vm.storageNamespace,vm.cloud)}
    var prescriptions by remember(account,personId){mutableStateOf<DoctorPrescriptionSnapshot?>(null)}
    var prescriptionError by remember(account,personId){mutableStateOf<String?>(null)}
    var prescriptionEditor by remember(account,personId){mutableStateOf<Boolean?>(null)}
    var prescriptionRequestId by remember(account,personId){mutableStateOf("")}
    var prescriptionGroupId by remember(account,personId){mutableStateOf("")}
    var pastGroupId by remember(account,personId){mutableStateOf<String?>(null)}
    var scannerOpen by remember(account,personId){mutableStateOf(false)}
    var prescriptionDraftRows by remember(account,personId){mutableStateOf<List<PrescriptionMedicineInput>?>(null)}
    var prescriptionDraftTitle by remember(account,personId){mutableStateOf<String?>(null)}
    var prescriptionDraftDate by remember(account,personId){mutableStateOf<String?>(null)}
    var prescriptionBase by remember(account,personId){mutableStateOf<DoctorPrescriptionSnapshot?>(null)}
    var prescriptionLegacyRows by remember(account,personId){mutableStateOf(emptyList<CareItem>())}
    var snapshot by remember(account,personId) { mutableStateOf(MedicineHistorySnapshot()) };var loaded by remember(account,personId) { mutableStateOf(false) }
    var busy by remember(account,personId) { mutableStateOf(false) };var error by remember(account,personId) { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(account,personId) { mutableStateOf("Current") };var add by rememberSaveable(account,personId) { mutableStateOf(false) }
    var edit by remember(account,personId) { mutableStateOf<CareItem?>(null) };var report by remember(account,personId) { mutableStateOf<CareItem?>(null) }
    var retire by remember(account,personId) { mutableStateOf<CareItem?>(null) };var reason by rememberSaveable(account,personId) { mutableStateOf("") }
    var retireRevision by remember(account,personId) { mutableStateOf(0L) }
    var retireTargetPast by remember(account,personId) { mutableStateOf(false) }
    fun same(write:Boolean=false)=selected==personId && vm.selected.value==personId && vm.accountNamespace==account &&
        vm.people.value.any { it.id==personId } && (vm.cloud==null || vm.cloud.session.value?.userId==account) && (!write || vm.canEdit.value)
    fun run(action:suspend()->Unit) {if(!busy && same()) {busy=true;error=null;scope.launch {try {action()}catch(e:Exception){if(e is CancellationException)throw e;if(vm.cloud!=null){snapshot=MedicineHistorySnapshot();loaded=false;report=null;retire=null};if(same())error=e.message ?: "Could not load medicine history. No success has been assumed."}finally{busy=false}}}}
    fun refresh() {if(vm.cloud!=null){snapshot=MedicineHistorySnapshot();loaded=false;prescriptions=null;report=null;retire=null};run {val result=repo.load(personId);val groups=runCatching{prescriptionRepo.load(personId)};if(same()){snapshot=result;loaded=true;prescriptions=groups.getOrNull();prescriptionError=groups.exceptionOrNull()?.message;vm.refreshMedicineHistory()}}}
    fun openPrescription(replace:Boolean){if(same(true)&&loaded&&prescriptions!=null&&!busy){prescriptionBase=prescriptions;prescriptionLegacyRows=entries.filter {it.personId==personId&&it.kind=="medicine"&&it.id !in snapshot.pastIds&&it.id !in prescriptions!!.excludedMedicineIds};prescriptionRequestId=UUID.randomUUID().toString();prescriptionGroupId=if(replace)UUID.randomUUID().toString()else prescriptions?.current?.id?:UUID.randomUUID().toString();prescriptionDraftRows=null;prescriptionDraftTitle=null;prescriptionDraftDate=null;prescriptionEditor=replace}}
    LaunchedEffect(account,personId,sessionId) {if(vm.cloud!=null&&sessionId!=account){snapshot=MedicineHistorySnapshot();loaded=false;add=false;edit=null;report=null;retire=null}else refresh()}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle,account,personId) {val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    val changedCallback by rememberUpdatedState(onNavigationBlockedChange)
    SideEffect {changedCallback(busy||add||edit!=null||report!=null||retire!=null||prescriptionEditor!=null||scannerOpen)}
    DisposableEffect(account,personId) {onDispose {changedCallback(false)}}
    LaunchedEffect(selected,editable,sessionId) {if(!same(true)){add=false;edit=null;report=null;retire=null;prescriptionEditor=null;pastGroupId=null;scannerOpen=false;prescriptionDraftRows=null}}
    BackHandler(busy||add||edit!=null||report!=null||retire!=null||prescriptionEditor!=null||pastGroupId!=null) {if(!busy){add=false;edit=null;report=null;retire=null;prescriptionEditor=null;pastGroupId=null}}
    if(!same()) {Text("This person's medicine record is unavailable. Return to Patients.",Modifier.padding(20.dp));return}
    if(scannerOpen) {
        if(vm.demoMode) {Text("Scanning is unavailable in this care mode.");TextButton(onClick={scannerOpen=false}){Text("Back to reviewed list")}}
        else MedicineScanner(vm,onClose={scannerOpen=false},onAccount={},onReviewedMedicines={drafts ->
            if(same(true)) {prescriptionDraftRows=(prescriptionDraftRows.orEmpty().filter {it.name.isNotBlank()}+drafts.map {PrescriptionMedicineInput(it.id,it.medicine.medicineName.trim(),medicineCareDetail(it.medicine))}).distinctBy {it.id};scannerOpen=false}
        })
        return
    }
    val medicines=entries.filter {it.personId==personId&&it.kind=="medicine"}
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-medicines"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) {Heading("Prescription",28);TextButton(onClick={openPrescription(true)},enabled=editable&&loaded&&prescriptions!=null&&!busy,modifier=Modifier.heightIn(min=48.dp).testTag("doctor-add-prescription")){Icon(Icons.Default.AddCircle,null,tint=DoctorAccent);Spacer(Modifier.width(6.dp));Text("Add prescription",color=DoctorAccent)}};Text("Current list, past prescriptions and intake reports",fontSize=13.sp,color=DoctorCardSub)}

        if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        error?.let {message->item {SurfaceCard(Peach){Text(message);Text("Your existing medicines and original history have not been cleared.");TextButton(onClick=::refresh,enabled=!busy){Text("Retry")}}}}
        item {MedicineHistoryTabs(tab,busy){tab=it}}
        if(tab!="Intake history") item {Text(if(tab=="Current")"Showing the active prescription and the medicines being taken now." else "Showing superseded prescriptions: when each was prescribed, when it ended and the medicines it held.",fontSize=13.sp,color=DoctorCardSub)}
        prescriptionError?.let {message->item {Text(message,fontSize=13.sp,color=DoctorCardSub)}}
        if(prescriptions==null&&loaded) item {TextButton(onClick={if(same(true)&&!busy)add=true},enabled=editable&&!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Existing manual entry / scanner")}}
        if(tab=="Current"&&loaded) item {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.Center){Column(Modifier.widthIn(max=260.dp)){Text("CURRENT PRESCRIPTION",fontSize=12.sp,color=DoctorCardFaint,fontWeight=FontWeight.Medium);Text(prescriptions?.current?.title?:"Prescription group/date not recorded",fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk)};TextButton(onClick={openPrescription(false)},enabled=editable&&prescriptions!=null&&!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Edit list",color=DoctorAccent)}}
            prescriptions?.current?.let {Text(if(it.prescribedOn.isBlank())"Start date not recorded"else "Started ${it.prescribedOn}",fontSize=13.sp,color=DoctorCardSub)}
        }
        if(!loaded) item {Text("Medicine history has not loaded. Course changes and intake reports stay unavailable until it does.",fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk);TextButton(onClick=::refresh,enabled=!busy){Text("Load history")}}
        if(tab=="Intake history") {
            item {Heading("Intake history",22)}
            if(loaded&&snapshot.events.isEmpty())item {SurfaceCard(DoctorCardReport){Text("No detailed intake reports yet",fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk);Text("Older daily checkboxes do not tell us the taken time, reporter or when they were logged.",fontSize=13.sp,color=DoctorCardSub)}}
            snapshot.events.groupBy {medicineDay(it.plannedAt?:it.reportedTakenAt?:it.loggedAt)}.forEach {(day,dayEvents)->
                item(key="day-$day") {Text(day,fontSize=13.sp,fontWeight=FontWeight.Medium,color=DoctorCardSub,modifier=Modifier.padding(top=4.dp))}
                items(dayEvents,key={it.id}) {event->MedicineReportCard(event,snapshot.events,System.currentTimeMillis())}
            }
        } else {
            val excluded=prescriptions?.excludedMedicineIds.orEmpty()
            val groupedIds=prescriptions?.medicines?.map {it.medicineId}?.toSet().orEmpty()
            val shown=if(!loaded) emptyList() else if(tab=="Current") medicines.filter {it.id !in snapshot.pastIds&&it.id !in excluded} else medicines.filter {it.id in snapshot.pastIds&&it.id !in groupedIds}
            if(tab=="Past") {
                val removed=prescriptions?.medicines?.filter {it.prescriptionId==prescriptions?.state?.currentId&&it.removedAt>0}.orEmpty()
                if(removed.isNotEmpty())item {Text("Removed from the current list",fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk)}
                items(removed,key={"removed-${it.prescriptionId}-${it.medicineId}"}) {row->MedicineSurface(false) {Text(row.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(row.instructions,fontSize=15.sp,color=DoctorCardSub);Text("Removed ${medicineTime(row.removedAt)}",fontSize=12.sp,color=DoctorCardFaint);TextButton(onClick={tab="Intake history"},modifier=Modifier.heightIn(min=48.dp)){Text("View intake history",color=DoctorAccent)}}}
                items(prescriptions?.groups?.filter {it.id!=prescriptions?.state?.currentId}.orEmpty(),key={"rx-${it.id}"}) {group ->
                    val count=prescriptions!!.members(group.id).size

                    Card(onClick={pastGroupId=group.id},colors=CardDefaults.cardColors(containerColor=DoctorCardReport),border=BorderStroke(1.dp,DoctorHairline),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().heightIn(min=100.dp)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Text(group.title,fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));MedicineChip("$count medicines",DoctorStatusRest,DoctorStatusRestBg)};Text("${if(group.prescribedOn.isBlank())"Start date not recorded" else group.prescribedOn}  to  ${if(group.endedAt>0)medicineDay(group.endedAt) else "end date not recorded"}",fontSize=15.sp,color=DoctorCardInk);Text(if(group.endedAt>0)"Ended: replaced by a newer prescription" else "Ended: reason not recorded",fontSize=13.sp,color=DoctorCardSub);Text("View saved medicine list",fontSize=13.sp,fontWeight=FontWeight.Medium,color=DoctorAccent)}}
                }
            }
            if(loaded&&shown.isEmpty()&&(tab=="Current"||prescriptions?.groups?.none {it.id!=prescriptions?.state?.currentId}!=false)&&!(tab=="Past"&&prescriptions?.medicines?.any {it.prescriptionId==prescriptions?.state?.currentId&&it.removedAt>0}==true))item {SurfaceCard(DoctorCardReport){Text(if(tab=="Current")"No current medicines recorded" else "No past medicines recorded",fontSize=20.sp,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(if(tab=="Current")"Add a prescription or enter medicines manually. Nothing is inferred from a report image." else "Moving a medicine here preserves its directions and intake reports.",fontSize=13.sp,color=DoctorCardSub)}}
            items(shown,key={it.id}) {medicine->MedicineSurface(tab=="Current") {
                val lines=medicine.detail.lines().filter {it.isNotBlank()}
                Text(medicine.title,fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth(),fontFamily=SaharaTitleFont,color=DoctorCardInk)
                Text(lines.firstOrNull()?:"Directions not recorded",fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
                if(lines.size>1)Text(lines.drop(1).joinToString("\n"),fontSize=13.sp,color=DoctorCardSub)
                if(medicine.id !in groupedIds) Text("Prescription group/date not recorded",fontSize=12.sp,color=DoctorCardFaint)
                val course=snapshot.courses.firstOrNull {it.medicineId==medicine.id}
                if(course?.past==true){Text("Moved to past ${medicineTime(course.changedAt)}",fontSize=13.sp,fontWeight=FontWeight.Medium,color=DoctorAccent);if(course.reason.isNotBlank())Text(course.reason,fontSize=13.sp,color=DoctorCardSub)}
                val last=snapshot.events.firstOrNull {it.medicineId==medicine.id&&it.outcome !in setOf("course_past","course_current")}
                if(last!=null)MedicineStatusChip(MedicineHistoryRules.status(last,snapshot.events,System.currentTimeMillis()),last.outcome)
                else MedicineHistoryRules.legacyDate(medicine)?.let {date->MedicineStatusChip("Taken reported","taken");Text("Check-off on $date; exact time and reporter not recorded.",fontSize=12.sp,color=DoctorCardFaint)}
                if(last==null&&MedicineHistoryRules.legacyDate(medicine)==null) MedicineStatusChip("Report not recorded","planned")
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={if(prescriptions!=null)openPrescription(false)else edit=medicine},enabled=editable&&!busy,modifier=Modifier.heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=DoctorAccent)){Text("Edit list")}
                    TextButton(onClick={if(same(true)&&loaded)report=medicine},enabled=editable&&loaded&&!busy,modifier=Modifier.heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=DoctorAccent)){Text("Record report")}
                    TextButton(onClick={tab="Intake history"},modifier=Modifier.heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=DoctorAccent)){Text("Intake history")}
                }
                if(medicine.id !in groupedIds) TextButton(onClick={if(same(true)&&loaded&&!busy){retireTargetPast=tab!="Past";retireRevision=course?.revision ?: 0;retire=medicine;reason=""}},enabled=editable&&loaded&&!busy,modifier=Modifier.heightIn(min=48.dp)){Text(if(tab=="Past")"Return to current medicines" else "Move to past medicines",color=DoctorAccent)}
            }}
            if(tab=="Past") {
                val missing=snapshot.courses.filter {it.past&&medicines.none {item->item.id==it.medicineId}}
                items(missing,key={"archived-${it.medicineId}"}) {course->MedicineSurface(false){Text(course.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(course.directions,fontSize=15.sp,color=DoctorCardSub);Text("Moved to past ${medicineTime(course.changedAt)}",fontSize=12.sp,color=DoctorCardFaint);Text("Earlier medicine entry is no longer available. Its course snapshot and reports are retained.",fontSize=12.sp,color=DoctorCardFaint)}}
            }
        }
        item {Text("${if(vm.cloud==null)"Saved only on this device." else "Shared medicine history, access controlled by the patient."} Reports are not proof a medicine was swallowed. Follow the prescriber's instructions.",fontSize=12.sp,lineHeight=16.sp,color=DoctorCardFaint)}
    }
    prescriptionEditor?.let {replacing ->
        val before=prescriptionBase
        if(before!=null&&loaded) {
            val currentRows=prescriptionLegacyRows
            DoctorPrescriptionEditor(prescriptionDraftRows ?: if(replacing)emptyList()else currentRows.map {PrescriptionMedicineInput(it.id,it.title,it.detail)},replacing,currentRows.isNotEmpty(),prescriptionDraftTitle ?: if(replacing)"Prescription"else before.current?.title?:"Prescription",prescriptionDraftDate ?: if(replacing)""else before.current?.prescribedOn.orEmpty(),busy,
                onDismiss={prescriptionEditor=null},onScan={title,date,rows->prescriptionDraftTitle=title;prescriptionDraftDate=date;prescriptionDraftRows=rows;scannerOpen=true},
                onSave={title,date,rows,confirmed ->if(same(true)&&loaded)run {
                    val request=SaveDoctorPrescription(prescriptionRequestId,personId,prescriptionGroupId,before.state.revision,before.current?.revision?:0,replacing,confirmed,title,date,rows,currentRows.map {it.id},DoctorPrescriptionRules.legacyFingerprint(currentRows))
                    val result=prescriptionRepo.save(request){same(true)}
                    if(same()){prescriptions=result;prescriptionEditor=null;tab="Current";vm.refreshMedicineHistory();if(vm.cloud!=null)vm.refresh()}
                }},saveError=error)
        }
    }
    pastGroupId?.let {id ->
        val group=prescriptions?.groups?.firstOrNull {it.id==id}
        if(group!=null) AlertDialog(onDismissRequest={pastGroupId=null},title={Text(group.title)},text={Column(Modifier.fillMaxWidth().heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(if(group.prescribedOn.isBlank())"Prescription date not recorded"else "Prescribed ${group.prescribedOn}")
            Text("Saved past prescription · names and instructions below retain the reviewed last-active list at replacement. Earlier removed medicines and intake reports remain in history.")
            prescriptions!!.medicines.filter {it.prescriptionId==id}.sortedWith(compareBy<DoctorPrescriptionMedicine>{it.removedAt>0}.thenBy {it.position}).forEach {row ->SurfaceCard(DoctorCardReport) {Text(row.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(row.instructions.ifBlank {"Instructions not recorded"},fontSize=15.sp,color=DoctorCardSub);if(row.removedAt>0)Text("Removed earlier ${medicineTime(row.removedAt)}",fontSize=12.sp,color=DoctorCardFaint);TextButton(onClick={pastGroupId=null;tab="Intake history"},modifier=Modifier.heightIn(min=48.dp)){Text("View intake history")}}}
        }},confirmButton={TextButton(onClick={pastGroupId=null}){Text("Close")}})
    }
    if(add) ModalBottomSheet(onDismissRequest={if(!busy)add=false},containerColor=Cream) {Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Heading("Add a prescription",25);Text("Add reviewed medicines to the current list. Existing medicines are not stopped or replaced automatically.")
        Button(onClick={if(same(true)&&!busy){add=false;onAddManual()}},modifier=Modifier.fillMaxWidth()){Text("Enter medicine manually")}
        OutlinedButton(onClick={if(same(true)&&!busy){add=false;onScanPrescription()}},modifier=Modifier.fillMaxWidth()){Text("Scan prescription and review")}
        Text("If this replaces an earlier course, move only the relevant older medicines to Past after confirming the change. Their reports remain available.")
    }}
    edit?.let {item->AddCareItem("medicine",vm,onDismiss={edit=null},initial=item)}
    report?.let {item->MedicineReportDialog(item,busy||!loaded,snapshot.events.filter {it.medicineId==item.id&&it.outcome=="planned"},onClose={report=null},onSave={event->if(loaded&&same(true))run {val result=repo.append(personId,event);if(same()){snapshot=result;report=null}}})}
    retire?.let {item->val past=retireTargetPast;AlertDialog(onDismissRequest={if(!busy)retire=null},title={Text(if(past)"Move to past medicines?" else "Return to current medicines?")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("${item.title}: only make this change when the prescription or care plan calls for it. This does not delete directions or intake history.");OutlinedTextField(reason,{reason=it.take(1000)},label={Text("Reason (optional)")},enabled=!busy&&loaded)}},confirmButton={TextButton(onClick={if(loaded&&same(true))run {val result=repo.setPast(personId,item,past,reason,retireRevision);if(same()){snapshot=result;retire=null;vm.refreshMedicineHistory()}}},enabled=!busy&&editable&&loaded){Text("Confirm change")}},dismissButton={TextButton(onClick={retire=null},enabled=!busy){Text("Keep unchanged")}})}
}

@Composable internal fun MedicineReportCard(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long) {
    SurfaceCard(DoctorCardReport) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Text(event.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));MedicineStatusChip(MedicineHistoryRules.status(event,all,now),event.outcome)}
        Spacer(Modifier.height(8.dp))
        Text("Planned: ${medicineTime(event.plannedAt)}",fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
        Text("Reported taken: ${if(event.outcome=="taken") event.reportedTakenAt?.let(::medicineTime) ?: event.reportedDate.takeIf {it.isNotBlank()}?.let {"$it · exact time not recorded"} ?: "Exact time not recorded" else "Not recorded"}",fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
        Text("Logged: ${medicineTime(event.loggedAt)}",fontSize=13.sp,color=DoctorCardSub)
        Text("Reporter: ${event.reporter.ifBlank {"Not recorded"}}",fontSize=13.sp,color=DoctorCardSub)
        if(event.note.isNotBlank())Text(event.note,fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
    }
}

private fun medicineDay(at: Long) = DateTimeFormatter.ofPattern("EEE, d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable private fun MedicineSurface(current: Boolean, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = if(current) DoctorCardMedicine else DoctorCardReport, border = if(current) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }

@Composable private fun MedicineChip(label: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) { Surface(color = bg, shape = RoundedCornerShape(50)) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg) } }

@Composable private fun MedicineStatusChip(label: String, outcome: String) { when { label.startsWith("Late") || outcome == "planned" && !label.startsWith("Plan followed") -> MedicineChip(label, DoctorStatusWarn, DoctorStatusWarnBg); outcome == "skipped" || outcome == "unmarked" || outcome.startsWith("course") -> MedicineChip(label, DoctorStatusRest, DoctorStatusRestBg); else -> MedicineChip(label, DoctorStatusGood, DoctorStatusGoodBg) } }

private fun chooseMedicineTime(context:Context,current:Long?,onPicked:(Long)->Unit) {
    val initial=Instant.ofEpochMilli(current?:System.currentTimeMillis()).atZone(ZoneId.systemDefault())
    DatePickerDialog(context,{_,year,month,day->TimePickerDialog(context,{_,hour,minute->onPicked(java.time.LocalDate.of(year,month+1,day).atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())},initial.hour,initial.minute,false).show()},initial.year,initial.monthValue-1,initial.dayOfMonth).show()
}

@Composable private fun MedicineReportDialog(item:CareItem,busy:Boolean,plans:List<MedicineIntakeEvent>,onClose:()->Unit,onSave:(MedicineIntakeEvent)->Unit) {
    val context=LocalContext.current;var outcome by rememberSaveable(item.id){mutableStateOf("taken")};var planned by rememberSaveable(item.id){mutableStateOf<Long?>(null)};var taken by rememberSaveable(item.id){mutableStateOf<Long?>(null)}
    var reporter by rememberSaveable(item.id){mutableStateOf("Not recorded")};var note by rememberSaveable(item.id){mutableStateOf("")};val id=rememberSaveable(item.id){UUID.randomUUID().toString()};var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Record medicine report")},text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(item.title,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont);Text("Record what was reported. Leave a time blank if it is unknown. New reports do not erase earlier reports.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("taken" to "Taken","skipped" to "Skipped","planned" to "Planned").forEach{(value,label)->FilterChip(outcome==value,onClick={outcome=value;if(value!="taken")taken=null},enabled=!busy,label={Text(label)})}}
        plans.distinctBy {it.plannedAt}.take(6).forEach {plan->FilterChip(selected=planned==plan.plannedAt,onClick={planned=plan.plannedAt},enabled=!busy,label={Text("For planned dose: ${medicineTime(plan.plannedAt)}")})}
        OutlinedButton(onClick={chooseMedicineTime(context,planned){planned=it}},enabled=!busy){Text("Planned: ${medicineTime(planned)}")}
        if(planned!=null)TextButton(onClick={planned=null},enabled=!busy){Text("Clear planned time")}
        if(outcome=="taken"){OutlinedButton(onClick={chooseMedicineTime(context,taken){taken=it}},enabled=!busy){Text("Reported taken: ${medicineTime(taken)}")};if(taken!=null)TextButton(onClick={taken=null},enabled=!busy){Text("Clear taken time")}}
        Text("Who supplied this report?");listOf("Patient report","Family report","Doctor report","Not recorded").forEach{value->FilterChip(reporter==value,onClick={reporter=value},enabled=!busy,label={Text(value)})}
        OutlinedTextField(note,{note=it.take(2000)},label={Text("Details / correction (optional)")},enabled=!busy)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={val event=MedicineIntakeEvent(id,item.id,item.title,outcome,planned,taken,loggedAt=System.currentTimeMillis(),reporter=reporter,note=note);runCatching{MedicineHistoryRules.validate(event,System.currentTimeMillis())}.onSuccess{onSave(event)}.onFailure{error=it.message?:("Check the report fields.")}},enabled=!busy){Text(if(busy)"Saving…" else "Save report")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}})
}

@Composable internal fun MedicineHistoryTabs(tab:String,busy:Boolean,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Current" to "Active prescription","Past" to "Replaced prescriptions").forEach {(value,hint)->
            val on=tab==value
            Surface(onClick={if(!busy)onSelect(value)},enabled=!busy,shape=RoundedCornerShape(20.dp),color=if(on)DoctorAccent else DoctorCardReport,border=if(on)null else BorderStroke(1.dp,DoctorHairline),modifier=Modifier.weight(1f).heightIn(min=56.dp).testTag("medicine-tab-$value")) {
                Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.Center) {Text(value,fontSize=16.sp,fontWeight=FontWeight.Medium,color=if(on)DoctorCardReport else DoctorCardInk);Text(hint,fontSize=12.sp,color=if(on)DoctorCardReport else DoctorCardSub)}
            }
        }
    }
}
