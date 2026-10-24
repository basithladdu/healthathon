package `in`.wedevit.care

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class DoctorCareDemoFixture(
    val person: Person, val draft: Conversation, val versions: List<CareVersion>,
    val items: List<CareItem>, val intake: List<MedicineIntakeEvent>, val contacts: List<CareContact>,
    val reports: List<CareReport>, val pastMedicineId: String,
)

/** Self-contained practice records, never an existing profile, account or clinical prescription. */
internal object DoctorCareDemoData {
    const val VERSION = 1
    val PERSON_ID = id("person")
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L
    private fun id(key: String) = UUID.nameUUIDFromBytes("sahara-doctor-care-demo-v$VERSION/$key".toByteArray()).toString()

    fun fixture(now: Long, zone: ZoneId = ZoneId.systemDefault()): DoctorCareDemoFixture {
        val person = Person(PERSON_ID, "Meena Krishnan", now - 10 * DAY)
        val source = "Goals-of-care discussion at home with Meena, her daughter Anita and neighbour Lakshmi present. Meena lives with advanced ovarian cancer and spoke about abdominal pain, tiredness and what matters most to her. Meena: I would like time to ask questions and plan around family time. Anita: We can help with transport. Dr Anita Sen: We can review the questions together. No treatment decision is recorded."
        val draft = Conversation(person.id, source = "$source\nUnfinished draft: ask who should attend the next conversation.",
            priorities = "Family time and a chance to ask questions.", participants = "Meena, Anita (daughter), Lakshmi (neighbour), Dr Anita Sen",
            topics = "Preparing questions, abdominal pain and tiredness, and practical help with transport.", questions = "Who should attend the next review?", nextSteps = "Bring the written question list.", updated = now - HOUR)
        val first = NoteRules.publish(person, draft.copy(source = source, questions = "What would Meena like to ask?", nextSteps = "Prepare a question list."), "Dr Anita Sen", true, 1).copy(id = id("note-1"), published = now - 4 * DAY)
        val second = NoteRules.publish(person, draft.copy(source = source, questions = "Who should attend, and what practical help is useful?", nextSteps = "Review the questions and arrange transport support."), "Dr Vikram Rao", true, 2).copy(id = id("note-2"), published = now - DAY)
        val medicineText = listOf("Morphine oral solution 5 mg" to "Oral, every 4 hours as needed. For breakthrough abdominal pain. Review if more than 3 doses in 24 hours.", "Ondansetron 4 mg" to "Oral, twice daily. For nausea. Take 30 minutes before meals.", "Dexamethasone 4 mg" to "Oral, each morning. Short course to ease nausea and appetite loss, now finished. Taken with food.")
        val medicines = (1..3).map { index -> CareItem(id("medicine-$index"), person.id, "medicine", medicineText[index-1].first, medicineText[index-1].second, doneOn=if(index==1)Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString() else "", created = now - (8 - index) * DAY) }
        val checkins = (6 downTo 0).map { days -> CareItem(id("checkin-$days"), person.id, "checkin",
            listOf("A good day", "An okay day", "A hard day", "An okay day", "A good day", "A hard day", "An okay day")[6-days],
            if(days==0) "Would like help preparing questions today. Anita is staying for the afternoon." else listOf("Pain settled after the morning dose and she rested well.", "Ate about half of each meal. Nausea eased after breakfast.", "Tired but calm. Sat on the veranda with Lakshmi.", "Pain woke her once in the night. Needed help getting up.", "Good appetite at lunch and a short walk in the garden.", "Slept poorly and felt nauseous in the morning.")[days-1], created = now - days * DAY - HOUR) }
        fun visit(key:String,doctor:String,at:Long,status:String,conditionRef:String="",discussionRef:String="",note:String,type:String="Home visit"):CareItem {
            val row=DoctorVisitRules.item(id(key),person.id,doctor,at,type,DoctorVisitDetails.encode(conditionRef,discussionRef,"$note This is a visit record kept in this app, not a booking with a clinic or service."))
            return row.copy(created=at-HOUR,detail=CareEntryCodec.encode(CareEntryCodec.decode(row.detail)!!.copy(status=status,completedAt=if(status=="completed")at+30*60_000 else 0,completedBy=if(status=="completed")doctor else "")))
        }
        val visits=listOf(
            visit("visit-next","Dr Vikram Rao",now+DAY,"open",type="Family meeting",note="Planned review of questions and practical support."),
            visit("visit-completed","Dr Anita Sen",now-4*DAY,"completed",id("checkin-4"),first.id,note="Home visit to review pain and nausea and record the first goals-of-care discussion; it links that day's check-in and the saved note."),
            visit("visit-completed-b","Dr Vikram Rao",now-DAY,"completed",id("checkin-1"),second.id,type="Pain review",note="Reviewed breakthrough dose use and the family's questions; it links that day's check-in and the updated note."),
            visit("visit-unconfirmed","Dr Vikram Rao",now-2*DAY,"open",type="Telephone follow-up",note="Planned call about nausea; completion and discussion are not confirmed."),
            visit("visit-cancelled","Dr Anita Sen",now-DAY,"cancelled",note="Cancelled because of travel difficulty; no discussion is recorded."),
        )
        val reports = listOf(
            CareReport(id("report-letter"), person.id, "Care discussion summary.png", "image/png", "demo-discussion-v1.png", 0, now - DAY),
            CareReport(id("report-lab"), person.id, "Blood test results.png", "image/png", "demo-support-v1.png", 0, now - 2 * DAY),
        )
        val lab = CareItem(id("lab"), person.id, "lab", "Blood test results", CareEntryCodec.encode(CareEntry(type="lab", category="Other", detail="Values and printed reference ranges as shown on the report. No interpretation is given here.", value="Hb 10.1 g/dL (printed 12.0-15.5); albumin 32 g/L (printed 35-50)", reportId=reports[1].id, reportName=reports[1].name)), created=now-2*DAY)
        fun event(key: String, med: Int, outcome: String, planned: Long? = null, taken: Long? = null, date: String = "", logged: Long) =
            MedicineIntakeEvent(id(key), medicines[med].id, medicines[med].title, outcome, planned, taken, date, logged, "Reported by family", "Report of what was said; not proof the dose was swallowed.", "manual")
        val intake = listOf(
            event("taken",0,"taken",now-4*HOUR,now-4*HOUR,logged=now-4*HOUR+600_000),
            event("late",1,"taken",now-2*HOUR,now-90*60_000,logged=now-80*60_000),
            event("skipped",0,"skipped",now-HOUR,logged=now-50*60_000),
            event("not-logged",1,"planned",now-30*60_000,logged=now-3*HOUR),
            event("upcoming",0,"planned",now+3*HOUR,logged=now-3*HOUR),
            event("date-only",2,"taken",date=Instant.ofEpochMilli(now-DAY).atZone(zone).toLocalDate().toString(),logged=now-DAY+HOUR).copy(source="daily-checkoff",reporter="Daily check-off by family"),
        )
        val contacts = listOf(
            CareContact(id("family"),person.id,"Anita Krishnan","Daughter","family"),
            CareContact(id("caregiver"),person.id,"Lakshmi Devi","Neighbour, helps with shopping and transport","caregiver"),
            CareContact(id("clinician"),person.id,"Dr Anita Sen","Palliative care physician","clinician"),
        )
        return DoctorCareDemoFixture(person,draft,listOf(first,second),medicines+checkins+visits+lab,intake,contacts,reports,medicines[2].id)
    }

    suspend fun seed(vm: CareModel, context: Context) {
        require(vm.demoMode && vm.cloud == null && vm.storageNamespace.isNotBlank()) { "Use the isolated doctor demo model." }
        vm.seedDoctorDemo { dao ->
            if (dao.people().first().any { it.id == PERSON_ID }) return@seedDoctorDemo
            val preferences=context.getSharedPreferences("doctor-demo-seed-${vm.storageNamespace}",Context.MODE_PRIVATE)
            val anchor=preferences.getLong("anchor-v$VERSION",0).takeIf {it>0} ?: System.currentTimeMillis().also {check(preferences.edit().putLong("anchor-v$VERSION",it).commit())}
            val data = fixture(anchor)
            val reports = withContext(Dispatchers.IO) { data.reports.mapIndexed { index, report ->
                val file = vm.reportFiles.file(report)
                check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(255,248,238))
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(56,44,38);textSize=34f }
                    val lines = if(index==0) listOf("HOME VISIT — DISCUSSION SUMMARY", "Meena Krishnan", "Family time and questions were discussed.", "Practical transport help was considered.", "No treatment decision is recorded.") else listOf("LABORATORY REPORT — BLOOD TESTS", "Meena Krishnan", "Haemoglobin 10.1 g/dL (printed 12.0-15.5)", "Albumin 32 g/L (printed 35-50)", "Values and printed ranges only; no interpretation.")
                    lines.forEachIndexed { line,text -> canvas.drawText(text,48f,90f+line*96f,paint) }
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                    report.copy(bytes=file.length())
                } finally { bitmap.recycle() }
            } }
            val medicine = MedicineHistoryRepo(context,null,vm.storageNamespace)
            data.intake.forEach { medicine.append(data.person.id,it) }
            medicine.setPast(data.person.id,data.items.first {it.id==data.pastMedicineId},true,"Earlier course finished and kept for reference.",0)
            val contacts = LocalCareContacts(context,vm.storageNamespace)
            data.contacts.filter { contact -> contacts.list(data.person.id).none {it.id==contact.id} }.forEach(contacts::save)
            dao.addPerson(data.person);dao.saveDraft(data.draft);data.versions.forEach {dao.insertVersion(it)}
            data.items.forEach {dao.saveItem(it)};reports.forEach {dao.addReport(it)}
        }
        // Additive fixture upgrades have deterministic IDs and never reset practice edits.
        vm.seedDoctorDemo { dao ->
            val db=CareDatabase.get(context,vm.storageNamespace);val rx=db.prescriptions()
            val person=dao.people().first().first {it.id==PERSON_ID}
            if(rx.state(PERSON_ID)==null) {
                val zone=ZoneId.systemDefault()
                fun at(date:String,hour:Int)=java.time.LocalDate.parse(date).atTime(hour,0).atZone(zone).toInstant().toEpochMilli()
                val currentId=id("rx-current-v1");val octoberId=id("rx-october-v1");val septemberId=id("rx-september-v1")
                val extra=CareItem(id("medicine-extra-past"),PERSON_ID,"medicine","Metoclopramide 10 mg","Oral, three times daily before meals. Earlier anti-sickness medicine, stopped after review.",created=at("2026-09-23",10))
                if(dao.item(extra.id)==null)dao.saveItem(extra)
                val groups=listOf(
                    DoctorPrescription(currentId,PERSON_ID,"Current prescription","2026-10-23",at("2026-10-23",12)),
                    DoctorPrescription(octoberId,PERSON_ID,"Earlier October prescription","2026-10-23",at("2026-10-23",10),endedAt=at("2026-10-23",12),endReason="Replaced after review"),
                    DoctorPrescription(septemberId,PERSON_ID,"September prescription","2026-09-23",at("2026-09-23",10),endedAt=at("2026-10-23",10),endReason="Replaced by the October prescription"),
                )
                groups.forEach {rx.putGroup(it)}
                val current=listOfNotNull(dao.item(id("medicine-1")),dao.item(id("medicine-2")))
                val past=listOfNotNull(dao.item(id("medicine-3")))
                rx.putMedicines((current.mapIndexed {i,item->DoctorPrescriptionMedicine(currentId,item.id,PERSON_ID,item.title,item.detail,i,groups[0].createdAt)}+
                    past.mapIndexed {i,item->DoctorPrescriptionMedicine(octoberId,item.id,PERSON_ID,item.title,item.detail,i,groups[1].createdAt)}+
                    DoctorPrescriptionMedicine(septemberId,extra.id,PERSON_ID,extra.title,extra.detail,0,groups[2].createdAt)))
                rx.putState(DoctorPrescriptionState(PERSON_ID,currentId,1))
            }
            val contextId=id("note-context-v2")
            if(dao.versions(PERSON_ID).first().none {it.id==contextId}) {
                val anchor=context.getSharedPreferences("doctor-demo-seed-${vm.storageNamespace}",Context.MODE_PRIVATE).getLong("anchor-v$VERSION",System.currentTimeMillis())
                val sample=fixture(anchor).draft
                val json=org.json.JSONObject(DoctorConversationContext.initial(sample));val goals=json.getJSONArray("goals")
                val quote="I would like time to ask questions and plan around family time."
                val start=sample.source.substringBefore(quote).toByteArray(Charsets.UTF_8).size
                val statuses=listOf("discussed","needs_review","deferred","undecided","not_recorded")
                val answers=listOf(quote,"Question wording needs review by the clinician.","Attendance will be discussed later.","No decision is recorded about practical arrangements.","")
                repeat(5){i->goals.getJSONObject(i).put("status",statuses[i]).put("origin",if(i==0)"source"else "manual").put("answer",answers[i]).put("evidence",org.json.JSONArray())}
                goals.getJSONObject(0).put("evidence",org.json.JSONArray().put(org.json.JSONObject().put("start",start).put("end",start+quote.toByteArray(Charsets.UTF_8).size).put("quote",quote)))
                json.put("englishDraft",org.json.JSONObject().put("text","Draft summary: prepare questions and discuss practical support. Not a treatment decision.").put("sourceHash",json.getString("sourceHash")).put("origin","manual").put("reviewed",false))
                val contextual=sample.copy(conversationId=id("context-conversation-v2"),contextJson=json.toString())
                val version=NoteRules.publish(person,contextual,"Dr Anita Sen",true,(dao.latest(PERSON_ID)?.version?:0)+1).copy(id=contextId,published=anchor-2*HOUR)
                dao.insertVersion(version.copy(contentHash=DoctorConversationContext.framedHash(version)))
            }
        }
        vm.select(PERSON_ID)
        vm.refreshMedicineHistory()
    }
}

/** A disposable isolated model; leaving it never switches the real selected care profile. */
@Composable internal fun DoctorCareDemoHost(onExit: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit, onHeaderBackChange: ((() -> Unit)?) -> Unit, initialPersonId:String = DoctorCareDemoData.PERSON_ID, initialSection:String = "Hub") {
    val context=LocalContext.current.applicationContext
    val vm=remember {CareModel.createDoctorDemo(context as Application)}
    val scope=rememberCoroutineScope()
    var ready by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var closing by remember {mutableStateOf(false)};var closeFailed by remember {mutableStateOf(false)}
    var childBlocked by remember {mutableStateOf(false)}
    var attempt by remember {mutableStateOf(0)}
    // Only sections EmbeddedPatientCare renders may be opened directly; anything else lands on Hub.
    var section by rememberSaveable(initialPersonId,initialSection) {mutableStateOf(if(initialSection in setOf("Summary","Conversations","Medicines","Progress","Visits","Reports")) initialSection else "Hub")}
    val people by vm.people.collectAsState();val selected by vm.selected.collectAsState();val draft by vm.draft.collectAsState()
    val person=people.firstOrNull {it.id==initialPersonId}
    fun leave() {if(!closing&&!childBlocked){closing=true;onNavigationBlockedChange(true);scope.launch {val job=vm.closeDoctorDemo();job.join();if(job.isCancelled){closing=false;closeFailed=true;onNavigationBlockedChange(false)}else onExit()}}}
    val back:()->Unit={if(!closing&&!childBlocked&&(ready||error!=null||closeFailed)){if(section=="Hub")leave() else section="Hub"}}
    DisposableEffect(vm) {onDispose {vm.closeDoctorDemo();onNavigationBlockedChange(false);onHeaderBackChange(null)}}
    SideEffect {if(closing || (!ready&&error==null))onNavigationBlockedChange(true)}
    LaunchedEffect(vm,attempt,initialPersonId) {try {if(DoctorTestProfiles.contains(initialPersonId)) DoctorTestProfiles.seed(vm,context) else DoctorCareDemoData.seed(vm,context);vm.select(initialPersonId);vm.refreshCareContacts();vm.refreshMedicineHistory();ready=true;onNavigationBlockedChange(false)}catch(e:Exception){if(e is CancellationException)throw e;android.util.Log.e("SaharaSeed","walkthrough load failed",e);error="The walkthrough could not load. Your own profiles were not changed.";onNavigationBlockedChange(false)}}
    DisposableEffect(section) {onHeaderBackChange(back);onDispose{onHeaderBackChange(null)}}
    BackHandler(enabled=!childBlocked&&!closing&&(ready||error!=null||closeFailed)) {back()}
    if(closing) Column(Modifier.padding(20.dp)){LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(DoctorTestProfiles.contains(initialPersonId)) "Saving this discussion…" else "Saving your demo practice draft…")}
    else if(closeFailed) Column(Modifier.padding(20.dp)){Text("Practice draft was not saved. Leaving may lose its latest demo edits; your own patient records are unchanged.");Button(onClick=::leave){Text("Retry saving practice draft")};TextButton(onClick=onExit){Text("Leave demo without saving")}}
    else if(error!=null) Column(Modifier.padding(20.dp)) {Text(error!!);Button(onClick={error=null;attempt++}){Text("Retry walkthrough setup")};TextButton(onClick=::leave){Text("Back to patients")}}
    else if(ready && person==null && DoctorTestProfiles.contains(initialPersonId)) Column(Modifier.padding(20.dp)) {Text("This test record was removed. It will not be recreated automatically.");TextButton(onClick=::leave){Text("Back to patients")}}
    else if(!ready || person==null || selected!=person.id || draft.personId!=person.id) Column(Modifier.padding(20.dp)) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(DoctorTestProfiles.contains(initialPersonId)) "Opening care records…" else "Opening care records…")}
    else if(section=="Hub") Column(Modifier.fillMaxSize().testTag("doctor-care-demo-hub")) {
        if(!DoctorTestProfiles.contains(initialPersonId)) Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)){Text("Practice record · not a live patient",color=Rust);Text("These records are separate from your own patients and are not synced.")}
        Box(Modifier.weight(1f)){DoctorSharedPatientHub(person,{section=it},deviceLocal=true)}
    } else EmbeddedPatientCare(vm,person.id,section,{section=it},{vm.message.value="Account and sharing are unavailable in this offline demo."},::leave,
        onNavigationBlockedChange={childBlocked=it;onNavigationBlockedChange(it||closing)},allowDeviceLocal=true,onHeaderBackChange={nested->onHeaderBackChange(nested ?: back)})
}
