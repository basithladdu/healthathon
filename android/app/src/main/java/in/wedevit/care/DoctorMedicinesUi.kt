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
@Composable fun DoctorMedicines(vm: CareModel, personId: String, onAddManual: () -> Unit, onScanPrescription: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) { val t = careText();
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
    fun run(action:suspend()->Unit) {if(!busy && same()) {busy=true;error=null;scope.launch {try {action()}catch(e:Exception){if(e is CancellationException)throw e;if(vm.cloud!=null){snapshot=MedicineHistorySnapshot();loaded=false;report=null;retire=null};if(same())error=e.message ?: t("Could not load medicine history. No success has been assumed.", "दवाओं का इतिहास नहीं खुल सका। कुछ भी सफल नहीं माना गया।")}finally{busy=false}}}}
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
    if(!same()) {Text(t("This person's medicine record is unavailable. Return to Patients.", "इस व्यक्ति का दवा रिकॉर्ड उपलब्ध नहीं है। मरीज़ों पर लौटें।"),Modifier.padding(20.dp));return}
    if(scannerOpen) {
        MedicineScanner(vm,onClose={scannerOpen=false},onAccount={},onReviewedMedicines={drafts ->
            if(same(true)) {prescriptionDraftRows=(prescriptionDraftRows.orEmpty().filter {it.name.isNotBlank()}+drafts.map {PrescriptionMedicineInput(it.id,it.medicine.medicineName.trim(),medicineCareDetail(it.medicine))}).distinctBy {it.id};scannerOpen=false}
        })
        return
    }
    val medicines=entries.filter {it.personId==personId&&it.kind=="medicine"}
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-medicines"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) {Heading(t("Prescription", "पर्चा"),28);Button(onClick={openPrescription(true)},enabled=editable&&loaded&&prescriptions!=null&&!busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp).testTag("doctor-add-prescription")){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Add prescription", "पर्चा जोड़ें"))}}}

        if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        error?.let {message->item {SurfaceCard(Peach){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ErrorOutline,null,tint=RustText,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(message, Modifier.weight(1f).announce(),fontSize=15.sp,color=DoctorCardInk);TextButton(onClick=::refresh,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text(t("Retry", "फिर कोशिश करें"), color = RustText)}}}}}
        item {DoctorSideEffectsSection(vm,personId)}
        item {MedicineHistoryTabs(tab,busy){tab=it}}
        prescriptionError?.let {message->item {Text(message,fontSize=14.sp,color=DoctorCardSub,modifier=Modifier.announce())}}
        if(prescriptions==null&&loaded) item {TextButton(onClick={if(same(true)&&!busy)add=true},enabled=editable&&!busy,modifier=Modifier.heightIn(min=48.dp)){Text(t("Add medicine", "दवा जोड़ें"))}}
        if(tab=="Current"&&loaded) item {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.Center){Column(Modifier.widthIn(max=260.dp)){Text(t("CURRENT PRESCRIPTION", "अभी का पर्चा"),fontSize=14.sp,color=DoctorCardSub,fontWeight=FontWeight.Medium,letterSpacing=0.5.sp);Text(prescriptions?.current?.title?:t("Not recorded", "दर्ज नहीं"),fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk)};TextButton(onClick={openPrescription(false)},enabled=editable&&prescriptions!=null&&!busy,modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Default.Edit,null,Modifier.size(18.dp),tint=DoctorAccent);Spacer(Modifier.width(6.dp));Text(t("Edit list", "सूची बदलें"),color=DoctorAccent)}}
            prescriptions?.current?.let {if(it.prescribedOn.isNotBlank()) Text("Started ${it.prescribedOn}",fontSize=14.sp,color=DoctorCardSub)}
        }
        if(!loaded) item {OutlinedButton(onClick=::refresh,enabled=!busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Load history", "इतिहास खोलें"))}}
        if(tab=="Intake history") {
            item {Heading(t("Intake history", "दवा लेने का इतिहास"),22)}
            if(loaded&&snapshot.events.isEmpty())item {MedicineEmpty(t("No detailed intake reports yet", "अभी दवा लेने की कोई विस्तृत सूचना नहीं"))}
            snapshot.events.groupBy {medicineDay(it.plannedAt?:it.reportedTakenAt?:it.loggedAt)}.forEach {(day,dayEvents)->
                item(key="day-$day") {Text(day,fontSize=14.sp,fontWeight=FontWeight.Medium,color=DoctorCardSub,modifier=Modifier.padding(top=4.dp))}
                items(dayEvents,key={it.id}) {event->MedicineReportCard(event,snapshot.events,System.currentTimeMillis())}
            }
        } else {
            val excluded=prescriptions?.excludedMedicineIds.orEmpty()
            val groupedIds=prescriptions?.medicines?.map {it.medicineId}?.toSet().orEmpty()
            val shown=if(!loaded) emptyList() else if(tab=="Current") medicines.filter {it.id !in snapshot.pastIds&&it.id !in excluded} else medicines.filter {it.id in snapshot.pastIds&&it.id !in groupedIds}
            if(tab=="Past") {
                val removed=prescriptions?.medicines?.filter {it.prescriptionId==prescriptions?.state?.currentId&&it.removedAt>0}.orEmpty()
                if(removed.isNotEmpty())item {Text(t("Removed from the current list", "अभी की सूची से हटाई गई"),fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk)}
                items(removed,key={"removed-${it.prescriptionId}-${it.medicineId}"}) {row->MedicineSurface(false) {Text(row.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(row.instructions,fontSize=15.sp,color=DoctorCardSub);Text(t("Removed ${medicineTime(row.removedAt)}", "${medicineTime(row.removedAt)} को हटाई गई"),fontSize=14.sp,color=DoctorCardFaint);TextButton(onClick={tab="Intake history"},modifier=Modifier.heightIn(min=48.dp)){Text(t("View intake history", "दवा लेने का इतिहास देखें"),color=DoctorAccent)}}}
                items(prescriptions?.groups?.filter {it.id!=prescriptions?.state?.currentId}.orEmpty(),key={"rx-${it.id}"}) {group ->
                    val count=prescriptions!!.members(group.id).size

                    Card(onClick={pastGroupId=group.id},colors=CardDefaults.cardColors(containerColor=DoctorCardReport),border=BorderStroke(1.dp,DoctorHairline),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=100.dp)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Text(group.title,fontSize=20.sp,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont,color=DoctorCardInk,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));MedicineChip("$count medicines",DoctorStatusRest,DoctorStatusRestBg)};Text("${if(group.prescribedOn.isBlank())t("Start date not recorded", "शुरू की तारीख़ दर्ज नहीं") else group.prescribedOn}  to  ${if(group.endedAt>0)medicineDay(group.endedAt) else t("end date not recorded", "ख़त्म होने की तारीख़ दर्ज नहीं")}",fontSize=15.sp,color=DoctorCardInk)}}
                }
            }
            if(loaded&&shown.isEmpty()&&(tab=="Current"||prescriptions?.groups?.none {it.id!=prescriptions?.state?.currentId}!=false)&&!(tab=="Past"&&prescriptions?.medicines?.any {it.prescriptionId==prescriptions?.state?.currentId&&it.removedAt>0}==true))item {MedicineEmpty(if(tab=="Current")t("No current medicines recorded", "अभी की कोई दवा दर्ज नहीं") else t("No past medicines recorded", "कोई पुरानी दवा दर्ज नहीं"))}
            items(shown,key={it.id}) {medicine->MedicineSurface(tab=="Current") {
                var menu by remember(medicine.id){mutableStateOf(false)}
                val lines=medicine.detail.lines().filter {it.isNotBlank()}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(medicine.title,fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f), fontFamily = SaharaTitleFont,color=DoctorCardInk);Box {IconButton(onClick={menu=true},modifier=Modifier.size(48.dp)){Icon(Icons.Default.MoreVert,t("Medicine actions", "दवा के विकल्प"),tint=DoctorCardSub)};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text(t("Edit list", "सूची बदलें"))},onClick={menu=false;if(prescriptions!=null)openPrescription(false)else edit=medicine},enabled=editable&&!busy);DropdownMenuItem(text={Text(t("Record report", "सूचना दर्ज करें"))},onClick={menu=false;if(same(true)&&loaded)report=medicine},enabled=editable&&loaded&&!busy);DropdownMenuItem(text={Text(t("Intake history", "दवा लेने का इतिहास"))},onClick={menu=false;tab="Intake history"})}}}
                Text(lines.firstOrNull()?:t("Directions not recorded", "निर्देश दर्ज नहीं"),fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
                if(lines.size>1)Text(lines.drop(1).joinToString("\n"),fontSize=14.sp,color=DoctorCardSub)
                val course=snapshot.courses.firstOrNull {it.medicineId==medicine.id}
                if(course?.past==true){Text(t("Moved to past ${medicineTime(course.changedAt)}", "${medicineTime(course.changedAt)} को पुरानी में डाली गई"),fontSize=14.sp,fontWeight=FontWeight.Medium,color=DoctorAccent);if(course.reason.isNotBlank())Text(course.reason,fontSize=14.sp,color=DoctorCardSub)}
                val last=snapshot.events.firstOrNull {it.medicineId==medicine.id&&it.outcome !in setOf("course_past","course_current")}
                if(last!=null)MedicineStatusChip(MedicineHistoryRules.status(last,snapshot.events,System.currentTimeMillis()),last.outcome)
                else MedicineHistoryRules.legacyDate(medicine)?.let {date->MedicineStatusChip("Taken reported","taken");Text(date,fontSize=14.sp,color=DoctorCardFaint)}
                if(last==null&&MedicineHistoryRules.legacyDate(medicine)==null) MedicineStatusChip(t("Report not recorded", "सूचना दर्ज नहीं"),"planned")
                if(medicine.id !in groupedIds) TextButton(onClick={if(same(true)&&loaded&&!busy){retireTargetPast=tab!="Past";retireRevision=course?.revision ?: 0;retire=medicine;reason=""}},enabled=editable&&loaded&&!busy,modifier=Modifier.heightIn(min=48.dp)){Text(if(tab=="Past")t("Return to current medicines", "अभी की दवाओं में वापस डालें") else t("Move to past medicines", "पुरानी दवाओं में डालें"),color=DoctorAccent)}
            }}
            if(tab=="Past") {
                val missing=snapshot.courses.filter {it.past&&medicines.none {item->item.id==it.medicineId}}
                items(missing,key={"archived-${it.medicineId}"}) {course->MedicineSurface(false){Text(course.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(course.directions,fontSize=15.sp,color=DoctorCardSub);Text(t("Moved to past ${medicineTime(course.changedAt)}", "${medicineTime(course.changedAt)} को पुरानी में डाली गई"),fontSize=14.sp,color=DoctorCardFaint)}}
            }
        }
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
            Text(if(group.prescribedOn.isBlank())t("Prescription date not recorded", "पर्चे की तारीख़ दर्ज नहीं")else "Prescribed ${group.prescribedOn}")
            prescriptions!!.medicines.filter {it.prescriptionId==id}.sortedWith(compareBy<DoctorPrescriptionMedicine>{it.removedAt>0}.thenBy {it.position}).forEach {row ->HorizontalDivider(color=DoctorHairline);Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(row.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);Text(row.instructions.ifBlank {t("Instructions not recorded", "निर्देश दर्ज नहीं")},fontSize=15.sp,color=DoctorCardSub);if(row.removedAt>0)Text(t("Removed earlier ${medicineTime(row.removedAt)}", "पहले ${medicineTime(row.removedAt)} को हटाई गई"),fontSize=14.sp,color=DoctorCardFaint);TextButton(onClick={pastGroupId=null;tab="Intake history"},modifier=Modifier.heightIn(min=48.dp)){Text(t("View intake history", "दवा लेने का इतिहास देखें"))}}}
        }},containerColor=Cream,confirmButton={TextButton(onClick={pastGroupId=null},modifier=Modifier.heightIn(min=48.dp)){Text(t("Close", "बंद करें"))}})
    }
    if(add) ModalBottomSheet(onDismissRequest={if(!busy)add=false},containerColor=Cream) {Column(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Heading(t("Add a prescription", "पर्चा जोड़ें"),24)
        Button(onClick={if(same(true)&&!busy){add=false;onAddManual()}},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.Edit,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Enter medicine manually", "दवा हाथ से लिखें"))}
        OutlinedButton(onClick={if(same(true)&&!busy){add=false;onScanPrescription()}},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.DocumentScanner,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(t("Scan prescription", "पर्चा स्कैन करें"))}
    }}
    edit?.let {item->AddCareItem("medicine",vm,onDismiss={edit=null},initial=item)}
    report?.let {item->MedicineReportDialog(item,busy||!loaded,snapshot.events.filter {it.medicineId==item.id&&it.outcome=="planned"},onClose={report=null},onSave={event->if(loaded&&same(true))run {val result=repo.append(personId,event);if(same()){snapshot=result;report=null}}})}
    retire?.let {item->val past=retireTargetPast;AlertDialog(onDismissRequest={if(!busy)retire=null},title={Text(if(past)t("Move to past medicines?", "पुरानी दवाओं में डालें?") else t("Return to current medicines?", "अभी की दवाओं में वापस डालें?"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(item.title,fontSize=18.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk);OutlinedTextField(reason,{reason=it.take(1000)},label={Text(t("Reason (optional)", "कारण (चाहें तो)"))},enabled=!busy&&loaded,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())}},containerColor=Cream,confirmButton={Button(onClick={if(loaded&&same(true))run {val result=repo.setPast(personId,item,past,reason,retireRevision);if(same()){snapshot=result;retire=null;vm.refreshMedicineHistory()}}},enabled=!busy&&editable&&loaded,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)){Text(t("Confirm", "पक्का करें"))}},dismissButton={TextButton(onClick={retire=null},enabled=!busy){Text(t("Cancel", "रद्द करें"))}})}
}

@Composable internal fun MedicineReportCard(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long) { val t = careText();
    SurfaceCard(DoctorCardReport) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Text(event.name,fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=SaharaTitleFont,color=DoctorCardInk,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));MedicineStatusChip(MedicineHistoryRules.status(event,all,now),event.outcome)}
        Spacer(Modifier.height(8.dp))
        Text(t("Planned: ${medicineTime(event.plannedAt)}", "तय: ${medicineTime(event.plannedAt)}"),fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
        Text(t("Reported taken: ", "लेने का बताया समय: ") + "${if(event.outcome=="taken") event.reportedTakenAt?.let(::medicineTime) ?: event.reportedDate.takeIf {it.isNotBlank()}?.let {t("$it · exact time not recorded", "$it · सही समय दर्ज नहीं")} ?: t("Exact time not recorded", "सही समय दर्ज नहीं") else t("Not recorded", "दर्ज नहीं")}",fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
        Text(t("Logged: ${medicineTime(event.loggedAt)}", "दर्ज: ${medicineTime(event.loggedAt)}"),fontSize=14.sp,color=DoctorCardSub)
        Text(t("Reporter: ", "बताने वाले: ") + event.reporter.ifBlank {t("Not recorded", "दर्ज नहीं")},fontSize=14.sp,color=DoctorCardSub)
        if(event.note.isNotBlank())Text(event.note,fontSize=15.sp,lineHeight=22.sp,color=DoctorCardInk)
    }
}

private fun medicineDay(at: Long) = DateTimeFormatter.ofPattern("EEE, d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable private fun MedicineEmpty(text: String) { Surface(shape = RoundedCornerShape(16.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Medication, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)); Text(text, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f)) } } }

@Composable private fun MedicineSurface(current: Boolean, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(16.dp), color = if(current) DoctorCardMedicine else DoctorCardReport, border = if(current) null else BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }

/** Intake statuses stay English (the chip colour reads them); only the shown label is Hindi. */
internal val MedicineStatusHindi = mapOf("Late reported" to "देर से लेने की सूचना", "Taken reported" to "लेने की सूचना", "Skipped reported" to "छोड़ने की सूचना", "Check-off corrected" to "टिक ठीक किया गया",
    "Moved to past medicines" to "पुरानी दवाओं में डाली गई", "Returned to current medicines" to "अभी की दवाओं में वापस", "Upcoming" to "आने वाली", "Not logged" to "दर्ज नहीं", "Plan followed by a report" to "योजना के बाद सूचना मिली")

@Composable private fun MedicineChip(label: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) { val t = careText(); Surface(color = bg, shape = RoundedCornerShape(50)) { Text(t(label, MedicineStatusHindi[label] ?: label), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg) } }

@Composable private fun MedicineStatusChip(label: String, outcome: String) { when { label.startsWith("Late") || outcome == "planned" && !label.startsWith("Plan followed") -> MedicineChip(label, DoctorStatusWarn, DoctorStatusWarnBg); outcome == "skipped" || outcome == "unmarked" || outcome.startsWith("course") -> MedicineChip(label, DoctorStatusRest, DoctorStatusRestBg); else -> MedicineChip(label, DoctorStatusGood, DoctorStatusGoodBg) } }

private fun chooseMedicineTime(context:Context,current:Long?,onPicked:(Long)->Unit) {
    val initial=Instant.ofEpochMilli(current?:System.currentTimeMillis()).atZone(ZoneId.systemDefault())
    DatePickerDialog(context,{_,year,month,day->TimePickerDialog(context,{_,hour,minute->onPicked(java.time.LocalDate.of(year,month+1,day).atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())},initial.hour,initial.minute,false).show()},initial.year,initial.monthValue-1,initial.dayOfMonth).show()
}

@Composable private fun MedicineReportDialog(item:CareItem,busy:Boolean,plans:List<MedicineIntakeEvent>,onClose:()->Unit,onSave:(MedicineIntakeEvent)->Unit) { val t = careText();
    val context=LocalContext.current;var outcome by rememberSaveable(item.id){mutableStateOf("taken")};var planned by rememberSaveable(item.id){mutableStateOf<Long?>(null)};var taken by rememberSaveable(item.id){mutableStateOf<Long?>(null)}
    var reporter by rememberSaveable(item.id){mutableStateOf("Not recorded")};var note by rememberSaveable(item.id){mutableStateOf("")};val id=rememberSaveable(item.id){UUID.randomUUID().toString()};var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(t("Record medicine report", "दवा की सूचना दर्ज करें"))},text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(item.title,fontWeight=FontWeight.Medium, fontFamily = SaharaTitleFont)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("taken" to t("Taken", "ली"),"skipped" to t("Skipped", "छोड़ी"),"planned" to t("Planned", "तय")).forEach{(value,label)->FilterChip(outcome==value,onClick={outcome=value;if(value!="taken")taken=null},enabled=!busy,label={Text(label)})}}
        plans.distinctBy {it.plannedAt}.take(6).forEach {plan->FilterChip(selected=planned==plan.plannedAt,onClick={planned=plan.plannedAt},enabled=!busy,label={Text(t("For planned dose: ${medicineTime(plan.plannedAt)}", "तय ख़ुराक के लिए: ${medicineTime(plan.plannedAt)}"))})}
        OutlinedButton(onClick={chooseMedicineTime(context,planned){planned=it}},enabled=!busy){Text(t("Planned: ", "तय: ") + medicineTime(planned))}
        if(planned!=null)TextButton(onClick={planned=null},enabled=!busy){Text(t("Clear planned time", "तय समय हटाएँ"))}
        if(outcome=="taken"){OutlinedButton(onClick={chooseMedicineTime(context,taken){taken=it}},enabled=!busy){Text(t("Reported taken: ${medicineTime(taken)}", "लेने का बताया समय: ${medicineTime(taken)}"))};if(taken!=null)TextButton(onClick={taken=null},enabled=!busy){Text(t("Clear taken time", "लेने का समय हटाएँ"))}}
        Text(t("Reported by", "किसने बताया"));listOf("Patient report","Family report","Doctor report","Not recorded").forEach{value->FilterChip(reporter==value,onClick={reporter=value},enabled=!busy,label={Text(when(value){"Patient report"->t("Patient report", "मरीज़ ने बताया");"Family report"->t("Family report", "परिवार ने बताया");"Doctor report"->t("Doctor report", "डॉक्टर ने बताया");else->t("Not recorded", "दर्ज नहीं")})})}
        OutlinedTextField(note,{note=it.take(2000)},label={Text(t("Note (optional)", "नोट (चाहें तो)"))},enabled=!busy)
        error?.let {Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())}
    }},confirmButton={TextButton(onClick={val event=MedicineIntakeEvent(id,item.id,item.title,outcome,planned,taken,loggedAt=System.currentTimeMillis(),reporter=reporter,note=note);runCatching{MedicineHistoryRules.validate(event,System.currentTimeMillis())}.onSuccess{onSave(event)}.onFailure{error=it.message?:(t("Check the report fields.", "सूचना के खाने जाँचें।"))}},enabled=!busy){Text(if(busy)t("Saving…", "सहेज रहे हैं…") else t("Save report", "सूचना सहेजें"))}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text(t("Cancel", "रद्द करें"))}})
}

@Composable internal fun MedicineHistoryTabs(tab:String,busy:Boolean,onSelect:(String)->Unit) { val t = careText()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Current" to "Active prescription","Past" to "Replaced prescriptions","Intake history" to "Taken / missed").forEach {(value,_)->
            val on=tab==value
            Surface(onClick={if(!busy)onSelect(value)},enabled=!busy,shape=RoundedCornerShape(50),color=if(on)DoctorAccent else DoctorCardReport,border=if(on)null else BorderStroke(1.dp,DoctorHairline),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("medicine-tab-$value")) {
                Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {Text(when(value){"Current"->t("Current", "अभी");"Past"->t("Past", "पुराने");else->t("Intake history", "ली गई दवा")},fontSize=16.sp,fontWeight=FontWeight.Medium,color=if(on)DoctorCardReport else DoctorCardInk)}
            }
        }
    }
}
