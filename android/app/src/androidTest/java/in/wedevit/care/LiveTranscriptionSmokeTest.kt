package `in`.wedevit.care

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Explicit, opted-in network smoke test. Uses only a known fictional synthetic recording. */
@RunWith(AndroidJUnit4::class)
class LiveTranscriptionSmokeTest {
    @Test
    fun signedInUserCanTranscribeSyntheticDemoAudio() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("saharaLiveTranscription") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val cloud = CareCloud(context)
        check(cloud.session.value != null) { "Sign in to the development app before running the live smoke test." }
        val token = cloud.transcriptionToken(forceRefresh = true)
        val auth = URL("https://kjnhtakqzboayqorrlue.supabase.co/auth/v1/user").openConnection() as HttpURLConnection
        try {
            auth.setRequestProperty("Authorization", "Bearer $token")
            auth.setRequestProperty("apikey", CareCloud.PUBLIC_API_KEY)
            auth.connectTimeout = 10000; auth.readTimeout = 10000
            val status = auth.responseCode
            val errorCode = if (status != 200) runCatching {
                org.json.JSONObject(auth.errorStream.bufferedReader().use { it.readText() }).let { payload ->
                    payload.optString("error_code").ifBlank { payload.optString("code") }
                }
                    .takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) }.orEmpty()
            }.getOrDefault("") else ""
            assertEquals("Supabase Auth must accept the app's session (error code: $errorCode)", 200, status)
        } finally { auth.disconnect() }
        val audio = File(context.cacheDir, "synthetic-transcription-test.m4a")
        instrumentation.context.assets.open("synthetic-transcription-test.m4a").use { input ->
            audio.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val now = System.currentTimeMillis()
            val consent = RecordingConsent(audio.absolutePath, "synthetic-test", "Synthetic voice", now, now, noticeVersion = 2)
            val result = ConversationTranscriber().transcribe(audio, consent, token)
            assertEquals("google-gemini", result.provider)
            assertEquals("gemini-3.5-transcribe", result.model)
            assertTrue(result.text.contains("synthetic", ignoreCase = true))
            assertTrue(result.text.contains("family", ignoreCase = true))
            assertTrue(result.text.contains("appointment", ignoreCase = true))
        } finally {
            audio.delete()
        }
    }
}
