package `in`.wedevit.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Known synthetic-file fixture only. Never starts MediaRecorder or reads an existing recording. */
class DoctorAudioScopeTest {
    @Test fun savedSyntheticClipReloadsOnlyInItsAccountAndPatientScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString().replace('-', '_')
        val account = "qa_audio_$suffix"
        val patient = "qa_patient_$suffix"
        val directory = File(context.filesDir, "doctor-preview-audio/$account/$patient")
        assertTrue(directory.canonicalPath.startsWith(File(context.filesDir, "doctor-preview-audio").canonicalPath + File.separator))
        assertFalse("Test fixture must never reuse any existing recording directory", directory.exists())
        directory.mkdirs()
        val file = File(directory, "synthetic-clip.m4a")
        val metadata = File(directory, "synthetic-clip.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controllers = mutableListOf<DoctorAudioController>()
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-transcription-test.m4a").use { input -> file.outputStream().use { input.copyTo(it) } }
            metadata.writeText(JSONObject().put("id", "synthetic-clip").put("patientId", patient).put("participants", "Synthetic fictional voice").put("recordingConsentAt", 1).put("transcriptionConsentAt", 0).put("noticeVersion", 2).toString())
            val owner = DoctorAudioController(context, patient, account, scope, DoctorAudioStorage.LOCAL_PREVIEW).also { controllers += it }
            val clip = owner.state.value.clips.single()
            assertTrue(owner.owns(clip))
            assertFalse(owner.state.value.recording)
            assertEquals(0L, clip.consent.transcriptionConsentAt)
            assertTrue(owner.state.value.durationMs > 5000)
            owner.seekPlayback(clip, 5000)
            assertEquals(5000, owner.state.value.playbackPositionMs)
            assertNull("Seeking must not start playback", owner.state.value.playing)
            owner.seekPlayback(clip, Int.MAX_VALUE)
            assertEquals(owner.state.value.durationMs, owner.state.value.playbackPositionMs)
            owner.seekPlayback(clip, -1)
            assertEquals(0, owner.state.value.playbackPositionMs)
            val otherPatient = DoctorAudioController(context, "qa_other_patient_$suffix", account, scope, DoctorAudioStorage.LOCAL_PREVIEW).also { controllers += it }
            val otherAccount = DoctorAudioController(context, patient, "qa_other_account_$suffix", scope, DoctorAudioStorage.LOCAL_PREVIEW).also { controllers += it }
            val live = DoctorAudioController(context, patient, account, scope, DoctorAudioStorage.LIVE).also { controllers += it }
            assertTrue(otherPatient.state.value.clips.isEmpty())
            assertTrue(otherAccount.state.value.clips.isEmpty())
            assertFalse(otherPatient.owns(clip))
            assertFalse(otherAccount.owns(clip))
            assertTrue(live.state.value.clips.isEmpty())
            assertFalse(live.owns(clip))
            assertTrue(runCatching { otherPatient.seekPlayback(clip, 1000) }.isFailure)
            assertFalse(owner.owns(clip.copy(consent = clip.consent.copy(personId = "another-patient"))))
            assertTrue(runCatching { DoctorAudioController(context, "../invalid", account, scope) }.isFailure)
            assertTrue(runCatching { DoctorAudioController(context, patient, "../invalid", scope) }.isFailure)
            owner.close()
            val closedPosition = owner.state.value.playbackPositionMs
            owner.seekPlayback(clip, 2000)
            assertEquals(closedPosition, owner.state.value.playbackPositionMs)
            assertNull(owner.state.value.playing)
        } finally {
            controllers.forEach { it.close() }
            scope.cancel()
            // Only explicit files created above are removed; no directory traversal or recursive cleanup.
            metadata.delete(); file.delete(); directory.delete(); directory.parentFile?.delete()
        }
    }
}
