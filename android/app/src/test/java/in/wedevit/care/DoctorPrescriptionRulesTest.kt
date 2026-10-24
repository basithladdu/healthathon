package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DoctorPrescriptionRulesTest {
    private fun request()=SaveDoctorPrescription(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),0,0,true,true,"Fictional prescription","",listOf(PrescriptionMedicineInput(UUID.randomUUID().toString(),"Demo medicine","No drug or dose recommendation")))
    @Test fun unknownPrescriptionDateIsAllowedWithoutInventingOne(){val value=request();DoctorPrescriptionRules.validate(value);assertEquals("",value.prescribedOn)}
    @Test fun replacementRequiresExplicitConfirmation(){assertThrows(IllegalArgumentException::class.java){DoctorPrescriptionRules.validate(request().copy(confirmedReplacement=false))}}
    @Test fun duplicateMedicineIdsAndInvalidDatesAreRejected(){val value=request();assertThrows(IllegalArgumentException::class.java){DoctorPrescriptionRules.validate(value.copy(medicines=value.medicines+value.medicines))};assertThrows(IllegalArgumentException::class.java){DoctorPrescriptionRules.validate(value.copy(prescribedOn="2026-02-31"))}}
    @Test fun removedMembershipExcludedButPastSnapshotRetained(){val group="current";val removed=DoctorPrescriptionMedicine(group,"removed","p","Original name","Original directions",0,1,2);val old=DoctorPrescriptionMedicine("past","old","p","Past name","Past directions",0,1);val active=DoctorPrescriptionMedicine(group,"active","p","Current name","Current directions",1,1);val state=DoctorPrescriptionSnapshot(DoctorPrescriptionState("p",group,2),emptyList(),listOf(removed,old,active));assertEquals(setOf("removed","old"),state.excludedMedicineIds);assertEquals(listOf(old),state.members("past"));assertEquals(listOf(active),state.members(group))}
}
