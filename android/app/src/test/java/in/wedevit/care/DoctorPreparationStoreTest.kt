package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorPreparationStoreTest {
    @Test fun roundTripEditRemoveAndDiscussionIsolation() {
        val disk = mutableMapOf<String,String>()
        val store = DoctorPreparationStore({disk[it]}, {key,value -> disk[key]=value;true})
        val first = DoctorPreparationStore.scope("account", "person", "discussion")
        store.save(first,listOf(DoctorPreparationQuestion("id","What matters?")))
        assertEquals("What matters?",store.load(first).single().text)
        store.save(first,listOf(DoctorPreparationQuestion("id","Who should join?")))
        assertEquals("id",store.load(first).single().id)
        listOf(DoctorPreparationStore.scope("other","person","discussion"),DoctorPreparationStore.scope("account","other","discussion"),DoctorPreparationStore.scope("account","person","new")).forEach { assertTrue(store.load(it).isEmpty()) }
        assertEquals(DoctorPreparationStore.scope("account","person",""),DoctorPreparationStore.scope("account","person",""))
        store.save(first,emptyList());assertTrue(store.load(first).isEmpty())
    }
    @Test fun invalidQuestionsAndFailedSaveDoNotReplaceStoredRows() {
        val store = DoctorPreparationStore({null},{_,_->false})
        assertTrue(runCatching {store.save("scope",listOf(DoctorPreparationQuestion("id","valid")))}.isFailure)
        assertTrue(runCatching {DoctorPreparationStore.validate(listOf(DoctorPreparationQuestion("id"," ")))}.isFailure)
        assertTrue(runCatching {DoctorPreparationStore.validate(listOf(DoctorPreparationQuestion("id","a".repeat(1001))))}.isFailure)
        assertTrue(runCatching {DoctorPreparationStore.validate((0..20).map {DoctorPreparationQuestion("$it","Question")})}.isFailure)
    }
}
