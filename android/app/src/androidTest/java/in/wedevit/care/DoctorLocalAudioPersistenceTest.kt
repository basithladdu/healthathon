package `in`.wedevit.care

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** In-memory Room only: no real profiles, microphone, files or network. */
class DoctorLocalAudioPersistenceTest {
    @Test fun failedSaveRollsBackAndRetryAttachesOnlyExactConversationWithoutChangingWords() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CareDatabase::class.java).build()
        try {
            val dao=db.care();val person="synthetic-audio-person"
            dao.addPerson(Person(person,"Synthetic audio fixture"))
            dao.saveDraft(Conversation(person,source="Original synthetic words",conversationId="conversation-a"))
            val consent=RecordingConsent("/private/synthetic.m4a",person,"Synthetic participant",10,0,2)
            val ref=DoctorLocalAudioRef("audio-ref",person,"conversation-a",consent.audioPath,"a".repeat(64),10,"",11)
            assertTrue(runCatching {db.withTransaction {
                persistDoctorLocalAudioMetadata(consent,ref,{true}) {dao.saveRecordingConsent(consent);db.conversations().addAudio(ref);dao.attachLocalRecording(person,"conversation-a",ref.localPath,10);error("synthetic disk failure")}
            }}.isFailure)
            assertEquals("",dao.draft(person)!!.audioPath)
            assertNull(db.conversations().audio(ref.id,person))
            db.withTransaction {persistDoctorLocalAudioMetadata(consent,ref,{true}) {dao.saveRecordingConsent(consent);db.conversations().addAudio(ref);dao.attachLocalRecording(person,"conversation-a",ref.localPath,10)}}
            assertEquals("Original synthetic words",dao.draft(person)!!.source)
            assertEquals(ref.localPath,dao.draft(person)!!.audioPath)
            assertEquals(0,dao.attachLocalRecording("another-person","conversation-a","/private/other.m4a",10))
            assertEquals(ref.localPath,dao.draft(person)!!.audioPath)
            dao.saveDraft(Conversation(person,source="New conversation words",conversationId="conversation-b"))
            assertEquals(0,dao.attachLocalRecording(person,"conversation-a",ref.localPath,10))
            assertEquals("",dao.draft(person)!!.audioPath)
            assertEquals("New conversation words",dao.draft(person)!!.source)
        } finally {db.close()}
    }
}
