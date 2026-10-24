package `in`.wedevit.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** In-memory Room only. Never opens care.db, an account database, or the persistent demo. */
class DoctorPrescriptionTransactionTest {
    @Test fun omittedOrChangedLegacyMedicinesCannotBeSilentlyReplaced()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,CareDatabase::class.java).build()
        try {
            val person=UUID.randomUUID().toString();db.care().addPerson(Person(person,"Fictional legacy fixture"))
            val old=CareItem(UUID.randomUUID().toString(),person,"medicine","Demo legacy","Original fictional text",revision=2);db.care().saveItem(old)
            val repo=DoctorPrescriptionRepo(context,UUID.randomUUID().toString(),null,db)
            val fresh=PrescriptionMedicineInput(UUID.randomUUID().toString(),"Demo new","No clinical dose")
            val request=SaveDoctorPrescription(UUID.randomUUID().toString(),person,UUID.randomUUID().toString(),0,0,true,true,"Demo replacement","",listOf(fresh))
            try{repo.save(request){true};fail("Omitted legacy list must reject")}catch(_:IllegalArgumentException){}
            assertTrue(repo.load(person).groups.isEmpty());assertNull(db.care().item(fresh.id))
            val reviewed=request.copy(legacyCurrentIds=listOf(old.id),legacyFingerprint=DoctorPrescriptionRules.legacyFingerprint(listOf(old)))
            db.care().saveItem(old.copy(detail="Changed concurrently",revision=3))
            try{repo.save(reviewed){true};fail("Stale legacy fingerprint must reject")}catch(_:IllegalArgumentException){}
            assertTrue(repo.load(person).groups.isEmpty());assertEquals("Changed concurrently",db.care().item(old.id)!!.detail)
        }finally{db.close()}
    }
    @Test fun replacementRetryEditAndRollbackPreserveOriginalMedicineRows()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,CareDatabase::class.java).build()
        try {
            val person=UUID.randomUUID().toString();db.care().addPerson(Person(person,"Fictional transaction fixture"))
            val repo=DoctorPrescriptionRepo(context,UUID.randomUUID().toString(),null,db)
            suspend fun capture(value:SaveDoctorPrescription):SaveDoctorPrescription {
                val excluded=repo.load(person).excludedMedicineIds
                val current=db.care().items(person).first().filter {it.kind=="medicine"&&it.id !in excluded}
                return value.copy(legacyCurrentIds=current.map {it.id},legacyFingerprint=DoctorPrescriptionRules.legacyFingerprint(current))
            }
            fun row(name:String)=PrescriptionMedicineInput(UUID.randomUUID().toString(),name,"Fictional instructions; no drug or dose")
            val a=row("Demo A");val b=row("Demo B");val group=UUID.randomUUID().toString()
            val first=SaveDoctorPrescription(UUID.randomUUID().toString(),person,group,0,0,true,true,"Demo current","",listOf(a,b))
            val saved=repo.save(first){true};assertEquals(1L,saved.state.revision)
            assertEquals(saved,repo.save(first){true})
            val edit=SaveDoctorPrescription(UUID.randomUUID().toString(),person,group,1,1,false,true,"Demo current","",listOf(a))
            val captured=capture(edit);val original=db.care().item(a.id)!!
            db.care().saveItem(original.copy(detail="Changed outside the group",revision=original.revision+1))
            try{repo.save(captured){true};fail("A medicine changed without group revision must reject")}catch(_:IllegalArgumentException){}
            val edited=repo.save(capture(edit)){true};assertTrue(b.id in edited.excludedMedicineIds);assertNotNull(db.care().item(b.id))
            val c=row("Demo C");val replacement=SaveDoctorPrescription(UUID.randomUUID().toString(),person,UUID.randomUUID().toString(),2,2,true,true,"Demo replacement","2026-10-23",listOf(c))
            val activeBeforeReplacement=db.care().item(a.id)!!
            db.care().saveItem(activeBeforeReplacement.copy(title="Latest reviewed name",detail="Latest reviewed instructions",revision=activeBeforeReplacement.revision+1))
            val reviewedReplacement=capture(replacement)
            val replaced=repo.save(reviewedReplacement){true};assertEquals(listOf("Latest reviewed name"),replaced.members(group).map {it.name});assertEquals("Latest reviewed instructions",replaced.members(group).single().instructions);assertTrue(replaced.groups.first {it.id==group}.endedAt>0)
            assertEquals(b.instructions,replaced.medicines.first {it.prescriptionId==group&&it.medicineId==b.id}.instructions)
            assertNotNull(db.care().item(a.id));assertNotNull(db.care().item(b.id))
            assertEquals(replaced,repo.save(reviewedReplacement){true})
            // An old exact retry never reapplies it; latest state is returned for safe UI refresh.
            assertEquals(replaced,repo.save(first){true})
            try {repo.save(edit.copy(requestId=UUID.randomUUID().toString())){true};fail("Stale revision must reject")} catch(_:IllegalArgumentException){}
            var checks=0
            val rollback=replacement.copy(requestId=UUID.randomUUID().toString(),groupId=UUID.randomUUID().toString(),expectedStateRevision=3,expectedGroupRevision=1,medicines=listOf(row("Demo rollback")))
            try {repo.save(capture(rollback)){++checks==1};fail("Lost context must roll back")}catch(_:IllegalArgumentException){}
            assertEquals(replaced,repo.load(person));assertNull(db.care().item(rollback.medicines.single().id))
        } finally {db.close()}
    }
}
