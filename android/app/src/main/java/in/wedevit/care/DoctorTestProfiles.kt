package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Additive, isolated test records. IDs never overlap earlier practice or real people. */
internal object DoctorTestProfiles {
    data class Profile(val key: String, val name: String, val family: String)
    val profiles = listOf(Profile("rahul", "Rahul", "Meera · sister / Kavya · partner / Arjun · brother"), Profile("arjun", "Arjun", "Nisha · mother / Dev · brother"))
    private const val DAY=86_400_000L
    fun id(key:String)=UUID.nameUUIDFromBytes("sahara-test-workspace-v2/$key".toByteArray(Charsets.UTF_8)).toString()
    fun personId(profile:Profile)=id("${profile.key}/person")
    fun contains(person:String)=profiles.any {personId(it)==person}
    fun needsSeed(profile:Profile,existing:Set<String>,completed:Boolean=false)=!completed && personId(profile) !in existing
    suspend fun insertRecords(dao:CareDao,data:DoctorCareDemoFixture,reports:List<CareReport> = data.reports) {
        dao.addPerson(data.person);dao.saveDraft(data.draft);data.versions.forEach {dao.insertVersion(it)};data.items.forEach {dao.saveItem(it)};reports.forEach {dao.addReport(it)}
    }

    /** [walkthrough] adds only the extra Rahul records used by the patient and family walkthrough; the default fixture is unchanged. */
    fun fixture(profile:Profile, now:Long, zone:ZoneId=ZoneId.systemDefault(), walkthrough:Boolean=false):DoctorCareDemoFixture {
        fun key(value:String)=id("${profile.key}/$value")
        val person=Person(personId(profile),profile.name,now-50*DAY)
        val rahul=profile.key=="rahul"
        val quotes=if(rahul) listOf("I want to keep evenings free for time with my family.","I would like my questions written down before the review.","Meera and Kavya can join if I ask them.","I have not decided who will help with travel.","We will bring our questions to the next visit.") else listOf("I want to keep a familiar daily routine at home.","I want more time to understand what is being discussed.","Nisha and Dev can help me prepare questions.","We are still unsure which visit time works for everyone.","We will check the visit time together before confirming.")
        val source=(if(rahul)"Goals-of-care discussion with Rahul at home, with Meera and Kavya present.\nHe lives with advanced bowel cancer and spoke about pain, nausea and what matters most to him." else "Goals-of-care discussion with Arjun at home, with Nisha and Dev present.\nHe lives with advanced heart and lung disease and spoke about breathlessness, rest and his daily routine.")+"\n"+quotes.joinToString("\n")
        val goalQuotes=mapOf("patient_priorities" to quotes[0],"understanding_questions" to quotes[1],"participants" to quotes[2],"concerns_uncertainty" to quotes[3],"next_steps" to quotes[4])
        fun contextual(d:Conversation):Conversation {
            val json=JSONObject(DoctorConversationContext.initial(d));val goals=json.getJSONArray("goals")
            repeat(goals.length()) {i-> val goal=goals.getJSONObject(i);val goalKey=goal.getString("key");val quote=goalQuotes.getValue(goalKey);val start=source.substringBefore(quote).toByteArray(Charsets.UTF_8).size
                goal.put("status",if(goalKey=="concerns_uncertainty")"undecided" else "discussed").put("origin","source").put("answer",quote)
                    .put("evidence",JSONArray().put(JSONObject().put("start",start).put("end",start+quote.toByteArray(Charsets.UTF_8).size).put("quote",quote))) }
            return d.copy(contextJson=json.toString())
        }
        val draft=contextual(Conversation(person.id,source=source,priorities=quotes[0],questions=quotes[1],participants=quotes[2],topics=if(rahul)"Family time, preparing questions, pain and nausea routine, and practical travel support." else "Home routine, understanding discussions, breathlessness at night and agreeing a visit time.",nextSteps=quotes[4],updated=now-3_600_000,conversationId=key("draft")))
        val sen="Dr Anita Sen";val rao="Dr Vikram Rao";val meera=PatientWalkthrough.DOCTOR
        val extra=walkthrough&&rahul
        val versions=(1..(if(extra)3 else 2)).map { n->
            val d=contextual(draft.copy(conversationId=key("discussion-$n")))
            val v=NoteRules.publish(person,d,when(n){1->sen;2->rao;else->meera},true,n).copy(id=key("note-$n"),published=now-(when(n){1->14L;2->3L;else->1L})*DAY)
            v.copy(contentHash=DoctorConversationContext.framedHash(v))
        }
        val rx=if(rahul) listOf(Triple("Morphine sulfate 10 mg","Oral, every 4 hours as needed. For breakthrough pain. Review if more than 3 doses in 24 hours.",2),Triple("Ondansetron 4 mg","Oral, twice daily. For nausea. Take 30 minutes before meals if nausea is worse then.\nTimes: 08:00, 20:00",2),Triple("Dexamethasone 4 mg","Oral, each morning. To ease nausea and appetite loss. Take with food.\nTimes: 08:00",3),Triple("Lactulose 15 ml","Oral, at night. To keep bowels regular while taking morphine.\nTimes: 21:00",20),Triple("Paracetamol 1 g","Oral, four times daily. For background pain. Do not take extra paracetamol from other sources.\nTimes: 08:00, 13:00, 18:00, 21:00",40))
            else listOf(Triple("Furosemide 40 mg","Oral, each morning. For fluid build-up and breathlessness. Weigh each morning and tell the team of a gain of 2 kg in 3 days.\nTimes: 08:00",2),Triple("Morphine oral solution 2.5 mg","Oral, every 4 hours as needed. For breathlessness at rest. Review if more than 4 doses in 24 hours.",2),Triple("Lorazepam 0.5 mg","Sublingual, at night if needed. For night-time anxiety with breathlessness. Review if used on more than 4 nights a week.",3),Triple("Senna 15 mg","Oral, at night. To prevent constipation while taking morphine.\nTimes: 21:00",20),Triple("Prednisolone 20 mg","Oral, each morning. Short course for wheeze, finished after review. Take with food.",40))
        val medicines=rx.mapIndexed {i,(title,detail,age)->CareItem(key("medicine-${i+1}"),person.id,"medicine",title,detail,created=now-age*DAY)}
        val narratives=if(rahul) listOf("Slept well and enjoyed a quiet family evening.","Pain settled after the morning dose. Would like questions written down.","Nausea in the morning made travel arrangements feel difficult.","Meera helped organise the discussion notes. Ate about half of each meal.","Comfortable day. Spent time with family and rested.","Pain woke him twice in the night. Needed help getting up.","Would like a shorter discussion with breaks in between.","Good appetite at lunch. Short walk in the garden with Kavya.","Tired but calm. Asked for help planning the next visit.","Nausea returned after breakfast and settled by the afternoon.","Quiet day. Asked that questions be ready for the next review.") else listOf("Kept a familiar morning routine despite some breathlessness on the stairs.","Breathless walking to the bathroom. Dev helped prepare the question list.","Found it difficult to arrange a suitable visit time. Ankles more swollen.","Would like the discussion explained slowly. Slept propped on pillows.","Enjoyed a calm afternoon at home and sat on the veranda for a while.","Good day. Swelling a little less and appetite better.","Anxious at night. Breathing felt tight until Nisha sat with him.","Asked family to check the appointment details.","Comfortable morning. Walked to the kitchen without stopping.","Tired after visitors but breathing easy at rest.","Ready to bring remaining questions to review.")
        val moods=if(rahul) listOf(0,1,2,1,0,2,1,0,1,2,1) else listOf(1,2,2,1,1,0,2,1,0,1,0)
        val checks=narratives.mapIndexed {i,text->CareItem(key("check-$i"),person.id,"checkin",listOf("A good day","An okay day","A hard day")[moods[i]],text,created=now-(narratives.size-1-i)*DAY-3_600_000)}
        // Rahul's recent distress answers and dated symptom notes, so the score history, per-part pain trend and the doctor's symptom summary have data.
        fun distress(n:Int,ageHours:Long,check:DistressCheck)=CareItem(key("distress-$n"),person.id,"checkin",DistressRules.title(check),DistressCodec.encode(check),created=now-ageHours*3_600_000)
        fun symptom(n:Int,ageHours:Long,note:SymptomNote):CareItem {
            val at=Instant.ofEpochMilli(now-ageHours*3_600_000).atZone(zone).toLocalDateTime().withSecond(0).withNano(0)
            val value=note.copy(observedDate=at.toLocalDate().toString(),observedTime=at.toLocalTime().toString(),respondent="patient",author="Rahul")
            return CareItem(key("symptom-$n"),person.id,"checkin",careInEnglish { "${value.symptom} · ${if(value.symptom==SymptomDiaryRules.MOOD)SymptomDiaryRules.moodSummary(value.moodRatings,false) else SymptomDiaryRules.ratingLabel(value.rating)}" },SymptomNoteCodec.encode(value),created=now-ageHours*3_600_000)
        }
        val diary=if(rahul) listOf(
            distress(1,5*24+4,DistressCheck(true,6,listOf("Pain","Worry or anxiety"),"Worried about the drive to the oncology review.")),
            distress(2,3*24+3,DistressCheck(true,4,listOf("Pain","Sleep"))),
            distress(3,26,DistressCheck(false)),
            symptom(1,5*24+5,SymptomNote("Pain",7,recall="worst-24h",painBodySites=listOf("abdomen","head"),painBodyRatings=mapOf("head" to 7,"abdomen" to 6),note="Headache through the afternoon; the abdominal ache is worse after meals.")),
            symptom(2,2*24+2,SymptomNote("Pain",5,recall="worst-24h",painBodySites=listOf("abdomen","head"),painBodyRatings=mapOf("head" to 5,"abdomen" to 4),note="Better after the breakthrough dose; slept through until 5 am.")),
            symptom(3,27,SymptomNote(SymptomDiaryRules.MOOD,moodRatings=mapOf("low-mood" to 4,"anxiety" to 5),note="Low after a long day; anxious about the review results.")),
        ) else emptyList()
        fun visit(n:Int,offset:Int,status:String,doctor:String,type:String,note:String):CareItem {
            val at=now+offset*DAY
            val linked=status=="completed"
            val condition=if(linked)checks.filter {it.created<=at}.maxByOrNull {it.created}?.id.orEmpty() else ""
            val discussion=if(linked)versions.filter {it.published<=at}.maxByOrNull {it.published}?.id.orEmpty() else ""
            val row=DoctorVisitRules.item(key("visit-$n"),person.id,doctor,at,type,DoctorVisitDetails.encode(condition,discussion,note))
            val entry=CareEntryCodec.decode(row.detail)!!
            return row.copy(created=at-DAY,detail=CareEntryCodec.encode(entry.copy(status=status,completedAt=if(status=="completed")at+1_800_000 else 0,completedBy=if(status=="completed")doctor else "")))
        }
        val visits=if(rahul) listOf(visit(1,-14,"completed",sen,"Home visit","Reviewed pain, nausea and bowel routine at home."),visit(2,-3,"completed",rao,"Pain review","Reviewed how breakthrough doses are being used and the questions written by the family."),visit(3,2,"open",rao,"Family meeting","Review questions and practical family support."),visit(4,6,"open",sen,"Telephone follow-up","Check nausea, appetite and travel plans by phone."),visit(5,-1,"cancelled",rao,"Home visit","Cancelled because of travel difficulty. To be rebooked with the family."),visit(6,-28,"completed",sen,"Home visit","First home visit to hear priorities and agree who joins discussions."))
            else listOf(visit(1,-14,"completed",sen,"Home visit","Reviewed breathlessness, ankle swelling and the daily routine at home."),visit(2,-3,"completed",rao,"Breathlessness review","Reviewed how breathless nights are managed and the questions from the family."),visit(3,2,"open",rao,"Family meeting","Review understanding, home routine and visit arrangements."),visit(4,6,"open",sen,"Telephone follow-up","Check breathing, sleep and fluid weights by phone."),visit(5,-1,"cancelled",rao,"Home visit","Cancelled. The family asked for an afternoon time instead."),visit(6,-28,"completed",sen,"Home visit","First home visit to hear priorities and agree a familiar daily routine."))
        val walkthroughVisits=if(extra) listOf(visit(7,4,"open",meera,"Oncology review","Arjun is attending with Rahul and will drive. Bring the reports folder and the written questions.")) else emptyList()
        fun task(n:Int,title:String,detail:String,status:String,dueDays:Int)=CareItem(key("task-$n"),person.id,"task",title,CareEntryCodec.encode(CareEntry("task",detail,"Arjun",meera,status,acceptedBy=if(status=="open")"" else "Arjun",acceptedAt=if(status=="open")0 else now-DAY)),now+dueDays*DAY,created=now-2*DAY)
        val walkthroughTasks=if(extra) listOf(task(1,"Collect the morphine refill from the pharmacy","Check the dose on the label matches the current prescription summary.","accepted",1),task(2,"Drive Rahul to the oncology review","Leave by 9 am so there is time to sit before the visit.","open",4),
            CareItem(key("task-3"),person.id,"task","Bring the reports folder to the review","Blood test results, the prescription summary and the written questions.",now+4*DAY,created=now-DAY)) else emptyList()
        fun journal(n:Int,title:String,detail:String,ageHours:Long)=CareItem(key("journal-$n"),person.id,"journal",title,detail,created=now-ageHours*3_600_000)
        val walkthroughJournal=if(extra) listOf(journal(1,"Rahul slept better after the evening dose","Rahul slept better after the evening dose. Arjun (brother): he settled by 10 pm and slept until morning.",30),journal(2,"Ate half of lunch and walked to the veranda","Ate half of lunch and walked to the veranda with help. Arjun (brother): he asked for the questions to be read out before the review.",8)) else emptyList()
        val reports=listOf(CareReport(key("document-1"),person.id,"${profile.name} care planning notes.png","image/png","${profile.key}-planning-v2.png",0,now-3*DAY),CareReport(key("document-2"),person.id,"${profile.name} family visit checklist.png","image/png","${profile.key}-family-v2.png",0,now-DAY),CareReport(key("document-3"),person.id,"${profile.name} blood test results.png","image/png","${profile.key}-bloods-v2.png",0,now-3*DAY),CareReport(key("document-4"),person.id,"${profile.name} prescription summary.png","image/png","${profile.key}-prescription-v2.png",0,now-2*DAY))
        fun lab(n:Int,title:String,value:String,unit:String,low:String,high:String,ageDays:Int,withReport:Boolean)=CareItem(key(if(n==1)"lab" else "lab-$n"),person.id,"lab",title,CareEntryCodec.encode(CareEntry(type="lab",category="Blood",detail="Value and printed reference range copied from the report. No interpretation is recorded.",value=value,unit=unit,rangeLow=low,rangeHigh=high,reportId=if(withReport)reports[2].id else "",reportName=if(withReport)reports[2].name else "")),created=now-ageDays*DAY)
        val labs=if(rahul) listOf(lab(1,"Haemoglobin","9.8","g/dL","13.0","17.0",3,true),lab(2,"Haemoglobin","10.2","g/dL","13.0","17.0",16,false),lab(3,"Haemoglobin","10.6","g/dL","13.0","17.0",30,false),lab(4,"Corrected calcium","10.9","mg/dL","8.5","10.5",3,true))
            else listOf(lab(1,"Serum creatinine","1.6","mg/dL","0.7","1.3",3,true),lab(2,"Serum creatinine","1.4","mg/dL","0.7","1.3",16,false),lab(3,"Serum creatinine","1.2","mg/dL","0.7","1.3",30,false),lab(4,"Serum sodium","133","mmol/L","135","145",3,true))
        val reporters=if(rahul) listOf("Reported by Kavya (partner)","Reported by Meera (sister)") else listOf("Reported by Nisha (mother)","Reported by Dev (brother)")
        fun event(n:Int,med:Int,outcome:String,planned:Long?,taken:Long?,logged:Long)=MedicineIntakeEvent(key("event-$n"),medicines[med].id,medicines[med].title,outcome,planned,taken,"",logged,reporters[n%2],if(outcome=="planned")"Planned dose. Nothing has been reported yet." else "Family report only. Not confirmed as taken or checked by anyone else.","manual")
        val intake=listOf(event(1,0,"taken",now-14_400_000,now-14_400_000,now-13_800_000),event(2,1,"taken",now-7_200_000,now-5_400_000,now-4_800_000),event(3,0,"skipped",now-3_600_000,null,now-3_000_000),event(4,1,"planned",now-1_800_000,null,now-10_800_000),event(5,0,"planned",now+10_800_000,null,now-10_800_000),event(6,2,"taken",null,null,now-DAY).copy(reportedDate=Instant.ofEpochMilli(now-DAY).atZone(zone).toLocalDate().toString(),source="daily-checkoff",reporter=reporters[0]+" · date only"),event(7,4,"taken",now-DAY-3_600_000,now-DAY-3_000_000,now-DAY-2_400_000),event(8,3,"skipped",now-2*DAY,null,now-2*DAY+600_000),event(9,2,"taken",now-2*DAY-7_200_000,now-2*DAY-5_400_000,now-2*DAY-4_800_000),event(10,0,"taken",now-2*DAY-14_400_000,now-2*DAY-13_800_000,now-2*DAY-13_200_000))
        val contacts=if(rahul) listOf(CareContact(key("family-1"),person.id,"Meera","Sister","family"),CareContact(key("family-2"),person.id,"Kavya","Partner","family"),CareContact(key("support"),person.id,"Priya Nair","Palliative care nurse coordinator","caregiver"))+(if(extra) listOf(CareContact(key("family-3"),person.id,"Arjun","Brother","family"),CareContact(key("doctor"),person.id,meera,"Palliative care doctor","caregiver")) else emptyList()) else listOf(CareContact(key("family-1"),person.id,"Nisha","Mother","family"),CareContact(key("family-2"),person.id,"Dev","Brother","family"),CareContact(key("support"),person.id,"Farah Khan","Community nurse coordinator","caregiver"))
        return DoctorCareDemoFixture(person,draft,versions,medicines+checks+diary+visits+walkthroughVisits+walkthroughTasks+walkthroughJournal+labs,intake,contacts,reports,medicines[2].id)
    }

    suspend fun seed(vm:CareModel,context:Context) {
        require(vm.demoMode&&vm.cloud==null&&vm.accountNamespace.isBlank()&&vm.storageNamespace==CareModel.DOCTOR_DEMO_STORAGE)
        profiles.forEach { profile->
          val prefs=context.getSharedPreferences("doctor-test-profiles-${vm.storageNamespace}",Context.MODE_PRIVATE)
          vm.seedDoctorDemo {dao->
            if(!needsSeed(profile,dao.people().first().map {it.id}.toSet(),prefs.getBoolean("complete-${profile.key}",false)))return@seedDoctorDemo
            val anchor=prefs.getLong(profile.key,0).takeIf {it>0}?:System.currentTimeMillis().also {check(prefs.edit().putLong(profile.key,it).commit())}
            val data=fixture(profile,anchor)
            val reports=data.reports.mapIndexed {index,report->
                val file=vm.reportFiles.file(report);check(file.parentFile!!.isDirectory||file.parentFile!!.mkdirs())
                if(file.isFile && file.length()>0) return@mapIndexed report.copy(bytes=file.length())
                val bitmap=Bitmap.createBitmap(1000,700,Bitmap.Config.ARGB_8888)
                try {val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(255,248,238));val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(56,44,38);textSize=32f}
                    val lines=listOf(profile.name,listOf("Care planning discussion","Family visit preparation","Blood test results","Prescription summary")[index],listOf(data.draft.priorities,"Bring questions; confirm who should attend.","Values and printed reference ranges as shown on the report.","Medicines as listed for the current prescription.")[index],"No diagnosis or treatment instruction is recorded.")
                    var baseline=70f
                    lines.forEach {line->
                        var remaining=line
                        while(remaining.isNotEmpty()) {
                            val fit=paint.breakText(remaining,true,920f,null).coerceAtLeast(1)
                            val wordBreak=if(fit<remaining.length)remaining.lastIndexOf(' ',fit).takeIf {it>0}?:fit else fit
                            check(baseline<=660f){"The test document text does not fit."}
                            canvas.drawText(remaining.take(wordBreak),40f,baseline,paint)
                            baseline+=44f;remaining=remaining.drop(wordBreak).trimStart()
                        }
                        baseline+=24f
                    }
                    file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};report.copy(bytes=file.length())
                }finally{bitmap.recycle()}}
            val history=MedicineHistoryRepo(context,null,vm.storageNamespace)
            history.resetSeededPerson(data.person.id)
            data.intake.forEach {history.append(data.person.id,it)}
            val localContacts=LocalCareContacts(context,vm.storageNamespace)
            data.contacts.filter {c->localContacts.list(data.person.id).none {it.id==c.id}}.forEach(localContacts::save)
            insertRecords(dao,data,reports)
            val rx=CareDatabase.get(context,vm.storageNamespace).prescriptions()
            val meds=data.items.filter {it.kind=="medicine"}
            val groups=(0..2).map {i->DoctorPrescription(id("${profile.key}/rx-$i"),data.person.id,if(i==0)"Current prescription" else "Earlier prescription ${3-i}",Instant.ofEpochMilli(anchor-i*20*DAY).atZone(ZoneId.systemDefault()).toLocalDate().toString(),anchor-i*20*DAY,endedAt=if(i==0)0 else anchor-(i-1)*20*DAY,endReason=if(i==0)""else "Replaced after review")}
            groups.forEach {rx.putGroup(it)};rx.putMedicines(meds.mapIndexed {i,m->val g=groups[if(i<3)0 else i-2];DoctorPrescriptionMedicine(g.id,m.id,data.person.id,m.title,m.detail,i,g.createdAt)})
            rx.putState(DoctorPrescriptionState(data.person.id,groups[0].id,1))
          }
          // Written only after Room commit; a failed marker is repaired by person presence.
          check(prefs.edit().putBoolean("complete-${profile.key}",true).commit()) {"The test workspace completion marker was not saved. Reopen to retry safely."}
        }
    }
}
