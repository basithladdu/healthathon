package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

/** While no AI provider is chosen the screens say so in English and Hindi (network guard: CareRemoteAssistTest). */
class AssistHonestyTest {
    @Test fun offNoticesAreBilingualAndDoNotClaimAnything() {
        listOf(CareAiOffCopy.title, CareAiOffCopy.body, CareOfflineTranslationCopy.unavailable, CareOfflineTranslationCopy.downloadFailed, CareOfflineTranslationCopy.waitingForWifi).forEach {
            assertTrue(it, it.contains(" · ") && it.any { c -> c in 'ऀ'..'ॿ' })
        }
        assertTrue(CareAiOffCopy.body.contains("not sent anywhere"))
        assertTrue(CareOfflineTranslationCopy.unavailable.startsWith("Translation is unavailable"))
    }
}
