package `in`.wedevit.care

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlinx.coroutines.Job

internal object DoctorLocalAudioRules {
    fun level(measured: Int): Float = measured.coerceIn(0,32767)/32767f
    fun allowed(demo: Boolean, cloud: Boolean, account: String, storage: String, person: String, testPerson: Boolean): Boolean =
        person.isNotBlank() && if (demo) !cloud && account.isBlank() && storage == CareModel.DOCTOR_DEMO_STORAGE && testPerson else !cloud || account.isNotBlank()

    fun sameDraft(account: String, liveAccount: String, person: String, livePerson: String, conversation: String, liveConversation: String): Boolean =
        account == liveAccount && person.isNotBlank() && person == livePerson && conversation == liveConversation
}

internal data class DoctorLocalAudioInfo(val bytes: Long, val durationMicros: Long)

internal suspend fun awaitDoctorLocalAudioSave(job: Job?, failure: () -> String?) {
    job?.join()
    check(job?.isCancelled!=true && failure()==null) { "The recording's consent could not be saved. Keep this screen open and retry saving." }
}

internal suspend fun persistDoctorLocalAudioMetadata(consent: RecordingConsent, ref: DoctorLocalAudioRef, personExists: suspend () -> Boolean, writeBoth: suspend () -> Unit) {
    require(consent.personId.isNotBlank() && consent.personId==ref.personId && consent.audioPath==ref.localPath && consent.recordingConsentAt>0 && consent.recordingConsentAt==ref.consentAt) { "The recording's ownership or consent changed." }
    require(ref.sha256.matches(Regex("[0-9a-f]{64}"))) { "The recording could not be verified." }
    check(personExists()) { "The care profile is no longer available." }
    writeBoth()
}

/** Validates a real finalized AAC container; silence is valid, but is never evidence of speech. */
internal fun inspectDoctorLocalAudio(file: File, root: File, person: String): DoctorLocalAudioInfo {
    require(person.isNotBlank() && file.isFile && file.extension == "m4a") { "The recording file is unavailable." }
    val folder=File(root,person).canonicalFile
    require(folder.parentFile==root.canonicalFile && file.canonicalFile.parentFile == folder) { "The recording belongs to another care profile." }
    require(file.length() in 1..100L*1024*1024) { "The recording is empty or too large." }
    val extractor=MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val tracks=(0 until extractor.trackCount).map(extractor::getTrackFormat)
        val audio=tracks.singleOrNull { it.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm" }
            ?: error("The recording does not contain a playable AAC audio track.")
        val duration=if(audio.containsKey(MediaFormat.KEY_DURATION))audio.getLong(MediaFormat.KEY_DURATION) else 0L
        require(duration>0) { "The recording was too short to save." }
        return DoctorLocalAudioInfo(file.length(),duration)
    } finally {extractor.release()}
}
