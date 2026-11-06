package `in`.wedevit.care

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

@Entity(indices=[Index("personId")])
data class DoctorConversationArchive(@PrimaryKey val id:String,val personId:String,val conversationId:String,val draftJson:String,val archivedAt:Long)
@Entity(indices=[Index("personId"),Index("conversationId")])
data class DoctorLocalAudioRef(@PrimaryKey val id:String,val personId:String,val conversationId:String,val localPath:String,val sha256:String,val consentAt:Long,val recorderUid:String,val createdAt:Long)

@Dao interface DoctorConversationDao {
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun archive(value:DoctorConversationArchive)
    @Query("SELECT * FROM DoctorConversationArchive WHERE personId=:person ORDER BY archivedAt DESC") fun archives(person:String):kotlinx.coroutines.flow.Flow<List<DoctorConversationArchive>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun addAudio(value:DoctorLocalAudioRef)
    @Query("SELECT * FROM DoctorLocalAudioRef WHERE id=:id AND personId=:person") suspend fun audio(id:String,person:String):DoctorLocalAudioRef?
    @Query("SELECT * FROM DoctorLocalAudioRef WHERE localPath=:path AND personId=:person LIMIT 1") suspend fun audioForPath(path:String,person:String):DoctorLocalAudioRef?
    @Query("DELETE FROM DoctorLocalAudioRef WHERE personId=:person") suspend fun purgeAudio(person:String)
    @Query("DELETE FROM DoctorLocalAudioRef WHERE personId=:person AND localPath=:path") suspend fun removeAudioPath(person:String,path:String)
    @Query("DELETE FROM DoctorConversationArchive WHERE personId=:person") suspend fun purgeArchives(person:String)
    @Transaction suspend fun purgePerson(person:String) { purgeAudio(person);purgeArchives(person) }
}

object DoctorConversationSchema {
    fun create(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorConversationArchive (id TEXT NOT NULL,personId TEXT NOT NULL,conversationId TEXT NOT NULL,draftJson TEXT NOT NULL,archivedAt INTEGER NOT NULL,PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorConversationArchive_personId ON DoctorConversationArchive(personId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS DoctorLocalAudioRef (id TEXT NOT NULL,personId TEXT NOT NULL,conversationId TEXT NOT NULL,localPath TEXT NOT NULL,sha256 TEXT NOT NULL,consentAt INTEGER NOT NULL,recorderUid TEXT NOT NULL,createdAt INTEGER NOT NULL,PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorLocalAudioRef_personId ON DoctorLocalAudioRef(personId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoctorLocalAudioRef_conversationId ON DoctorLocalAudioRef(conversationId)")
    }
}

data class DoctorSourceEvidence(val start:Int,val end:Int,val quote:String)
data class DoctorConversationIdentity(val accountId:String,val personId:String,val conversationId:String,val sourceHash:String)
internal fun doctorConversationIdentityMatches(expected:DoctorConversationIdentity,current:DoctorConversationIdentity,permitted:Boolean)=permitted && expected.personId.isNotBlank() && expected==current
data class DoctorGoalContext(val key:String,val status:String="not_recorded",val origin:String="legacy",val answer:String="",val evidence:List<DoctorSourceEvidence> = emptyList())
object DoctorConversationContext {
    const val HASH_SCHEME="sha256-framed-v2"
    val keys=listOf("patient_priorities","understanding_questions","participants","concerns_uncertainty","next_steps")
    val statuses=setOf("not_recorded","needs_review","discussed","deferred","undecided")
    val origins=setOf("manual","source","assisted","legacy")
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun fileSha256(file:java.io.File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    fun initial(d:Conversation):String {
        val answers=listOf(d.priorities,d.questions,d.participants,"",d.nextSteps)
        return JSONObject().put("schemaVersion",1).put("sourceHash",sha256(d.source.toByteArray(Charsets.UTF_8)))
            .put("goals",JSONArray(keys.mapIndexed { i,key -> goalJson(DoctorGoalContext(key,status=if(answers[i].isBlank()) "not_recorded" else "needs_review",answer=answers[i])) }))
            .put("audioRef",JSONObject.NULL).put("audioHash",JSONObject.NULL).put("recorderUid",JSONObject.NULL).put("recorderLabel","").put("englishDraft",JSONObject.NULL)
            .put("transcription",if(d.transcribedAt>0 && d.transcriptModel.isNotBlank()) JSONObject().put("provider",d.transcriptProvider).put("model",d.transcriptModel).put("at",d.transcribedAt).put("sourceHash",JSONObject.NULL) else JSONObject.NULL).toString()
    }
    fun acceptedTranscript(d:Conversation):Conversation {
        val context=JSONObject(d.contextJson.ifBlank { initial(d) })
        context.put("sourceHash",sha256(d.source.toByteArray(Charsets.UTF_8)))
        context.put("transcription",JSONObject().put("provider",d.transcriptProvider).put("model",d.transcriptModel).put("at",d.transcribedAt).put("sourceHash",sha256(d.source.toByteArray(Charsets.UTF_8))))
        return d.copy(conversationId=d.conversationId.ifBlank { newId() },contextJson=context.toString())
    }
    fun sourceChanged(old:Conversation,newSource:String):Pair<String,Boolean> {
        val j=JSONObject(old.contextJson);val goals=j.getJSONArray("goals")
        var archive=!j.isNull("englishDraft") || (!j.isNull("transcription") && !j.getJSONObject("transcription").isNull("sourceHash"))
        repeat(goals.length()) { i -> val g=goals.getJSONObject(i)
            if(g.getJSONArray("evidence").length()>0 || g.getString("status") !in setOf("not_recorded","needs_review")) archive=true
            if(g.getString("status")!="not_recorded")g.put("status","needs_review")
            g.put("evidence",JSONArray())
        }
        j.put("sourceHash",sha256(newSource.toByteArray(Charsets.UTF_8))).put("englishDraft",JSONObject.NULL)
        return j.toString() to archive
    }
    fun goalJson(g:DoctorGoalContext)=JSONObject().put("key",g.key).put("status",g.status).put("origin",g.origin).put("answer",g.answer)
        .put("evidence",JSONArray(g.evidence.map { JSONObject().put("start",it.start).put("end",it.end).put("quote",it.quote) }))
    fun goals(d:Conversation):List<DoctorGoalContext> {
        val array=JSONObject(d.contextJson.ifBlank { initial(d) }).getJSONArray("goals")
        return keys.map { key -> val g=(0 until array.length()).map { array.getJSONObject(it) }.first { it.getString("key")==key }
            val evidence=g.getJSONArray("evidence")
            DoctorGoalContext(key,g.getString("status"),g.getString("origin"),g.getString("answer"),(0 until evidence.length()).map { i -> val e=evidence.getJSONObject(i);DoctorSourceEvidence(e.getInt("start"),e.getInt("end"),e.getString("quote")) })
        }
    }
    fun validate(source:String,text:String) {
        require(text.toByteArray(Charsets.UTF_8).size<=65_536) { "The review context is too large." }
        val j=JSONObject(text);val bytes=source.toByteArray(Charsets.UTF_8)
        fun shape(value:JSONObject,fields:Set<String>) { require(value.keys().asSequence().toSet()==fields) { "This review context is unsupported. Keep the original and review it before editing." } }
        fun strings(value:JSONObject,vararg fields:String) { fields.forEach { require(value.get(it) is String) } }
        shape(j,setOf("schemaVersion","sourceHash","goals","recorderUid","recorderLabel","audioRef","audioHash","englishDraft","transcription"))
        strings(j,"sourceHash","recorderLabel")
        require(j.get("schemaVersion") is Number && (j.get("schemaVersion") as Number).toDouble()==1.0 && j.getString("sourceHash")==sha256(bytes)) { "The original words changed. Review the goals again." }
        val goals=j.getJSONArray("goals");require(goals.length()==5)
        val found=mutableSetOf<String>()
        repeat(goals.length()) { i -> val g=goals.getJSONObject(i);shape(g,setOf("key","status","origin","answer","evidence"));strings(g,"key","status","origin","answer");require(g.getString("key") in keys && found.add(g.getString("key")))
            require(g.getString("status") in statuses && (g.getString("origin") in origins || CarriedForward.isOrigin(g.getString("origin"))) && g.getString("answer").length<=4000)
            val ev=g.getJSONArray("evidence");require(ev.length()<=50)
            require(g.getString("status")!="not_recorded" || (g.getString("answer").isBlank() && ev.length()==0))
            require(g.getString("status")!="discussed" || g.getString("answer").isNotBlank())
            repeat(ev.length()) { k -> val e=ev.getJSONObject(k);shape(e,setOf("start","end","quote"));strings(e,"quote")
                listOf("start","end").forEach { key -> require((e.get(key) is Int || e.get(key) is Long) && e.get(key).toString().matches(Regex("[0-9]{1,6}"))) }
                val start=e.getInt("start");val end=e.getInt("end");val quote=e.getString("quote").toByteArray(Charsets.UTF_8)
                require(start>=0 && end>start && end<=bytes.size && bytes.copyOfRange(start,end).contentEquals(quote)) { "A source quotation no longer matches the original words." }
            }
        }
        listOf("audioRef","recorderUid").forEach { if(!j.isNull(it)) { strings(j,it);require(j.getString(it).matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")));UUID.fromString(j.getString(it)) } }
        if(!j.isNull("audioHash"))strings(j,"audioHash")
        require(j.isNull("audioHash") || j.getString("audioHash").matches(Regex("[a-f0-9]{64}")))
        require(j.isNull("audioRef")==j.isNull("audioHash"))
        require(j.getString("recorderLabel").length<=120)
        if(!j.isNull("englishDraft")) { val e=j.getJSONObject("englishDraft");shape(e,setOf("text","sourceHash","origin","reviewed"));strings(e,"text","sourceHash","origin");require(e.getString("text").length<=NoteRules.MAX_SOURCE_CHARS && e.getString("sourceHash")==j.getString("sourceHash") && e.getString("origin") in origins && e.get("reviewed") is Boolean) }
        if(!j.isNull("transcription")) {
            val t=j.getJSONObject("transcription");shape(t,setOf("provider","model","at","sourceHash"))
            strings(t,"provider","model");if(!t.isNull("sourceHash"))strings(t,"sourceHash")
            require(t.getString("provider").length<=120 && t.getString("model").length<=200 && (t.get("at") is Int || t.get("at") is Long) && t.getLong("at") in 1..999_999_999_999_999L)
            require(t.isNull("sourceHash") || t.getString("sourceHash").matches(Regex("[a-f0-9]{64}")))
        }
    }
    fun framedHash(v:CareVersion):String {
        val values=listOf(v.conversationId,v.personId,v.personName,v.version.toString(),v.source,v.priorities,v.participants,v.topics,v.questions,v.nextSteps,v.doctor,v.published.toString(),v.draftOrigin,v.reviewStatus,v.reviewerVerification,v.transcriptionModel,v.transcribedAt.toString(),v.contextJson)
        val out=java.io.ByteArrayOutputStream();out.write("sahara-care-note-v2\n".toByteArray(Charsets.US_ASCII))
        values.forEach { val b=it.toByteArray(Charsets.UTF_8);out.write("${b.size}:".toByteArray(Charsets.US_ASCII));out.write(b) }
        return sha256(out.toByteArray())
    }
    fun validateVersion(v:CareVersion) {
        if(v.hashScheme!=HASH_SCHEME)return
        UUID.fromString(v.conversationId)
        validate(v.source,v.contextJson)
        require(v.contentHash==framedHash(v)) { "The saved conversation context did not match this exact version." }
    }
    fun sameContent(a:CareVersion,b:CareVersion):Boolean =
        if(a.hashScheme==HASH_SCHEME && b.hashScheme==HASH_SCHEME) framedHash(a.copy(version=0,published=0))==framedHash(b.copy(version=0,published=0)) else a.contentHash==b.contentHash
    fun readable(v:CareVersion):String {
        if(v.hashScheme!=HASH_SCHEME)return ""
        return runCatching {
            validateVersion(v)
            val j=JSONObject(v.contextJson)
            buildString {
                appendLine("Saved goals review")
                val labels=listOf("Patient priorities","Understanding and questions","Who should participate","Concerns and uncertainty","Next steps and follow-up")
                goals(Conversation(v.personId,source=v.source,contextJson=v.contextJson)).forEachIndexed { i,g ->
                    appendLine("${labels[i]} · ${g.status.replace('_',' ')} · ${g.origin}")
                    appendLine(g.answer.ifBlank { "No answer recorded" })
                    g.evidence.forEach { appendLine("Original words: “${it.quote}”") }
                }
                if(!j.isNull("englishDraft")) { val e=j.getJSONObject("englishDraft");appendLine("\nEnglish draft · ${if(e.getBoolean("reviewed")) "review recorded" else "not reviewed"} · ${e.getString("origin")}");appendLine(e.getString("text")) }
                if(!j.isNull("transcription")) { val t=j.getJSONObject("transcription");appendLine("\nTranscript made by: ${t.getString("provider")} / ${t.getString("model")} · ${java.time.Instant.ofEpochMilli(t.getLong("at"))}")
                    appendLine(if(t.isNull("sourceHash")) "Original transcript text binding not recorded" else if(t.getString("sourceHash")==j.getString("sourceHash")) "Source matches the captured transcript text; accuracy still requires review" else "The written source was edited after this transcript was captured") }
                if(j.getString("recorderLabel").isNotBlank())appendLine("Recorder label (self-entered): ${j.getString("recorderLabel")}")
                if(!j.isNull("audioRef"))appendLine("Audio reference retained on its original device only. Audio is not included in this written note.")
            }.trimEnd()
        }.getOrElse { "Additional saved review context could not be read. Its original metadata has been retained in the app." }
    }
    fun archiveJson(d:Conversation):String = JSONObject().put("personId",d.personId).put("source",d.source).put("priorities",d.priorities).put("participants",d.participants).put("topics",d.topics).put("questions",d.questions).put("nextSteps",d.nextSteps).put("audioPath",d.audioPath).put("consentAt",d.consentAt).put("updated",d.updated).put("transcriptProvider",d.transcriptProvider).put("transcriptModel",d.transcriptModel).put("transcribedAt",d.transcribedAt).put("transcriptAudioPath",d.transcriptAudioPath).put("conversationId",d.conversationId).put("contextJson",d.contextJson).toString()
    /** Read-only archive projection; never inserts a draft or creates a CareVersion. */
    fun decodeArchive(a:DoctorConversationArchive):Conversation? = runCatching {
        val j=JSONObject(a.draftJson)
        require(j.getString("personId")==a.personId)
        val conversation=j.optString("conversationId")
        require(conversation.isBlank() || conversation==a.conversationId)
        Conversation(a.personId,source=j.getString("source"),priorities=j.optString("priorities"),participants=j.optString("participants"),topics=j.optString("topics"),questions=j.optString("questions"),nextSteps=j.optString("nextSteps"),audioPath=j.optString("audioPath"),consentAt=j.optLong("consentAt"),updated=j.optLong("updated"),transcriptProvider=j.optString("transcriptProvider"),transcriptModel=j.optString("transcriptModel"),transcribedAt=j.optLong("transcribedAt"),transcriptAudioPath=j.optString("transcriptAudioPath"),conversationId=conversation,contextJson=j.optString("contextJson"))
    }.getOrNull()
}
