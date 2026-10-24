package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*

class DoctorLocalAudioRulesTest {
    @Test fun onlyExplicitIsolatedTestProfilesCanRecordInDemo() {
        assertTrue(DoctorLocalAudioRules.allowed(true,false,"",CareModel.DOCTOR_DEMO_STORAGE,"rahul",true))
        assertFalse(DoctorLocalAudioRules.allowed(true,false,"",CareModel.DOCTOR_DEMO_STORAGE,"old-sample",false))
        assertFalse(DoctorLocalAudioRules.allowed(true,true,"account",CareModel.DOCTOR_DEMO_STORAGE,"rahul",true))
        assertFalse(DoctorLocalAudioRules.allowed(true,false,"","","rahul",true))
        assertFalse(DoctorLocalAudioRules.allowed(false,true,"","account","person",false))
    }
    @Test fun stoppedAudioNeverAttachesToAnotherPersonConversationOrAccount() {
        assertTrue(DoctorLocalAudioRules.sameDraft("a","a","p","p","c","c"))
        assertFalse(DoctorLocalAudioRules.sameDraft("a","b","p","p","c","c"))
        assertFalse(DoctorLocalAudioRules.sameDraft("a","a","p","q","c","c"))
        assertFalse(DoctorLocalAudioRules.sameDraft("a","a","p","p","c","new"))
    }
    @Test fun meterUsesOnlyMeasuredAmplitudeAndIsZeroForSilence() {
        assertEquals(0f,DoctorLocalAudioRules.level(0),0f)
        assertEquals(0f,DoctorLocalAudioRules.level(-1),0f)
        assertEquals(1f,DoctorLocalAudioRules.level(40000),0f)
        assertEquals(.5f,DoctorLocalAudioRules.level(16384),.001f)
    }
    @Test fun metadataRefusesWrongOwnershipAndMissingProfileBeforeAnyWrite() = runBlocking {
        val consent=RecordingConsent("/private/clip.m4a","p","Synthetic participant",10,0,2)
        val ref=DoctorLocalAudioRef("ref","p","conversation","/private/clip.m4a","a".repeat(64),10,"",11)
        var writes=0
        assertTrue(runCatching {persistDoctorLocalAudioMetadata(consent,ref.copy(personId="q"),{true}){writes++}}.isFailure)
        assertTrue(runCatching {persistDoctorLocalAudioMetadata(consent,ref,{false}){writes++}}.isFailure)
        assertEquals(0,writes)
        persistDoctorLocalAudioMetadata(consent,ref,{true}){writes++}
        assertEquals(1,writes)
    }
    @Test fun failedMetadataWriteCanRetryTheSameCapturedIdentityWithoutChangingIt() = runBlocking {
        val consent=RecordingConsent("/private/clip.m4a","p","Synthetic participant",10,0,2)
        val ref=DoctorLocalAudioRef("stable-ref","p","earlier-conversation","/private/clip.m4a","a".repeat(64),10,"",11)
        assertTrue(runCatching {persistDoctorLocalAudioMetadata(consent,ref,{true}){error("synthetic disk failure")}}.isFailure)
        var persisted:DoctorLocalAudioRef?=null
        persistDoctorLocalAudioMetadata(consent,ref,{true}){persisted=ref}
        assertEquals(ref,persisted)
    }
    @Test fun closeWaitsForIndependentSaveAndFailureRequiresSuccessfulRetry() = runBlocking {
        val release=CompletableDeferred<Unit>()
        var error:String?=null
        val save=CoroutineScope(SupervisorJob()+Dispatchers.Default).launch {release.await();error="synthetic save failure"}
        val close=async {runCatching {awaitDoctorLocalAudioSave(save){error}}}
        yield();assertFalse(close.isCompleted)
        release.complete(Unit)
        assertTrue(close.await().isFailure)
        val retry=CoroutineScope(SupervisorJob()+Dispatchers.Default).launch {error=null}
        awaitDoctorLocalAudioSave(retry){error}
        assertNull(error)
    }
}
