package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineOrganizerTest {
    @Test fun sanitizesStructuredResponseAndComputesUnusedPhotos() {
        val result = sanitizeMedicineOrganization(
            """
            {
              "medicines": [
                {
                  "medicineName": " Amoxicillin ", "strength": " 500 mg ", "form": " capsule ",
                  "quantity": " 15 capsules ", "frequency": " twice daily ",
                  "suggestedTimes": ["8:00 AM", "08:00 pm", "25:00 PM", "8:00 AM"],
                  "mealTiming": "afterFood", "instructions": " Finish the prescribed course. ",
                  "confidence": 1.4, "uncertainties": ["Quantity is faint", "Quantity is faint"],
                  "medicineImageIndex": 1, "prescriptionPageIndexes": [1, 0, 99, 1]
                },
                {
                  "medicineName": "amoxicillin", "strength": "500 MG", "form": "", "quantity": "",
                  "frequency": "", "suggestedTimes": [], "mealTiming": "anytime", "instructions": "",
                  "confidence": 0.2, "uncertainties": [], "medicineImageIndex": 0,
                  "prescriptionPageIndexes": []
                },
                {
                  "medicineName": "Omeprazole", "strength": "20 mg", "form": "tablet", "quantity": "",
                  "frequency": "once daily", "suggestedTimes": ["7:30 AM"], "mealTiming": "beforeFood",
                  "instructions": "", "confidence": 0.62, "uncertainties": ["Strip photo is missing"],
                  "medicineImageIndex": 20, "prescriptionPageIndexes": [0]
                }
              ],
              "unmatchedMedicineImageIndexes": [2, 200],
              "warnings": ["Check the faint quantity", "Check the faint quantity"]
            }
            """.trimIndent(),
            prescriptionCount = 2,
            medicineImageCount = 4,
        )

        assertEquals(2, result.medicines.size)
        assertEquals(listOf("08:00", "20:00"), result.medicines[0].suggestedTimes)
        assertEquals(MedicineMealTiming.AFTER_FOOD, result.medicines[0].mealTiming)
        assertEquals(1.0, result.medicines[0].confidence!!, 0.0)
        assertEquals(listOf(1, 0), result.medicines[0].prescriptionPageIndexes)
        assertEquals(listOf("Quantity is faint"), result.medicines[0].uncertainties)
        assertEquals(-1, result.medicines[1].medicineImageIndex)
        assertEquals(MedicineMatchQuality.PRESCRIPTION_ONLY, result.medicines[1].matchQuality)
        assertEquals(listOf(0, 2, 3), result.unmatchedMedicineImageIndexes)
        assertEquals(listOf("Check the faint quantity"), result.warnings)
    }

    @Test fun neverTreatsAStripPhotoAsPrescriptionEvidence() {
        val result = sanitizeMedicineOrganization(
            """
            {
              "medicines": [{
                "medicineName": "Pulmoclear", "strength": "", "form": "tablet", "quantity": "",
                "frequency": "", "suggestedTimes": [], "mealTiming": "anytime", "instructions": "",
                "confidence": 0.3, "uncertainties": ["Prescription wording is unclear"],
                "medicineImageIndex": 0, "prescriptionPageIndexes": []
              }],
              "unmatchedMedicineImageIndexes": [], "warnings": []
            }
            """.trimIndent(),
            prescriptionCount = 1,
            medicineImageCount = 1,
        )

        val medicine = result.medicines.single()
        assertEquals(-1, medicine.medicineImageIndex)
        assertEquals(0.3, medicine.confidence!!, 0.0)
        assertEquals(listOf(0), result.unmatchedMedicineImageIndexes)
        assertTrue(medicine.uncertainties.any { it.contains("prescription page", ignoreCase = true) })
    }

    @Test fun keepsSafeTimesExplicitDurationAndPrintedOrder() {
        val result = sanitizeMedicineOrganization(
            """
            {
              "medicines": [
                {
                  "medicineName": "Pulmoclear", "strength": "", "form": "tablet", "quantity": "1 tablet",
                  "frequency": "Every 12 hours", "suggestedTimes": ["8AM", "20:00", "8", "24:00"],
                  "mealTiming": "anytime", "instructions": "For 5 days", "durationDays": 5,
                  "prescriptionItemNumber": 12, "confidence": 0.95, "uncertainties": [],
                  "medicineImageIndex": 1, "prescriptionPageIndexes": [0]
                },
                {
                  "medicineName": "Holirose", "strength": "40 mg", "form": "tablet", "quantity": "1 tablet",
                  "frequency": "Every 24 hours", "suggestedTimes": ["9 PM"], "mealTiming": "anytime",
                  "instructions": "", "durationDays": 0, "prescriptionItemNumber": 2,
                  "confidence": 0.98, "uncertainties": [], "medicineImageIndex": 0,
                  "prescriptionPageIndexes": [1]
                }
              ],
              "unmatchedMedicineImageIndexes": [], "warnings": []
            }
            """.trimIndent(),
            prescriptionCount = 2,
            medicineImageCount = 2,
        )

        assertEquals(listOf("Holirose", "Pulmoclear"), result.medicines.map { it.medicineName })
        assertEquals(listOf("21:00"), result.medicines[0].suggestedTimes)
        assertEquals(null, result.medicines[0].durationDays)
        assertEquals(listOf("08:00", "20:00"), result.medicines[1].suggestedTimes)
        assertEquals(5, result.medicines[1].durationDays)
        assertEquals(12, result.medicines[1].prescriptionItemNumber)
    }

    @Test fun permitsOnlyOneMedicinePerStripAndCapsUnsafeMatches() {
        val result = sanitizeMedicineOrganization(
            """
            {
              "medicines": [
                {
                  "medicineName": "Medicine A", "strength": "5 mg", "form": "tablet", "quantity": "",
                  "frequency": "", "suggestedTimes": [], "mealTiming": "anytime", "instructions": "",
                  "durationDays": 0, "prescriptionItemNumber": 1, "confidence": 0.9,
                  "uncertainties": [], "medicineImageIndex": 0, "prescriptionPageIndexes": [0]
                },
                {
                  "medicineName": "Medicine B", "strength": "10 mg", "form": "tablet", "quantity": "",
                  "frequency": "", "suggestedTimes": [], "mealTiming": "anytime", "instructions": "",
                  "durationDays": 0, "prescriptionItemNumber": 2, "confidence": 0.9,
                  "uncertainties": [], "medicineImageIndex": 0, "prescriptionPageIndexes": [0]
                },
                {
                  "medicineName": "Medicine C", "strength": "", "form": "", "quantity": "",
                  "frequency": "", "suggestedTimes": [], "mealTiming": "anytime", "instructions": "",
                  "durationDays": 0, "prescriptionItemNumber": 3, "confidence": 0.8,
                  "uncertainties": [], "medicineImageIndex": 9, "prescriptionPageIndexes": [0]
                },
                {
                  "medicineName": "IVABRAD", "strength": "5 mg", "form": "tablet", "quantity": "1 tablet",
                  "frequency": "Every 12 hours", "suggestedTimes": ["8AM", "8PM"],
                  "mealTiming": "anytime", "instructions": "", "durationDays": 0,
                  "prescriptionItemNumber": 4, "identityMatch": "genericEquivalent", "confidence": 0.94,
                  "uncertainties": [], "medicineImageIndex": -1, "prescriptionPageIndexes": [0]
                }
              ],
              "unmatchedMedicineImageIndexes": [], "warnings": []
            }
            """.trimIndent(),
            prescriptionCount = 1,
            medicineImageCount = 1,
        )

        assertEquals(0, result.medicines[0].medicineImageIndex)
        assertEquals(-1, result.medicines[1].medicineImageIndex)
        assertEquals(0.49, result.medicines[1].confidence!!, 0.0)
        assertTrue(result.medicines[1].uncertainties.any { it.contains("also matched", ignoreCase = true) })
        assertEquals(-1, result.medicines[2].medicineImageIndex)
        assertTrue(result.medicines[2].uncertainties.any { it.contains("invalid strip-photo", ignoreCase = true) })
        assertEquals(0.74, result.medicines[3].confidence!!, 0.0)
        assertEquals(MedicineMatchQuality.PRESCRIPTION_ONLY, result.medicines[3].matchQuality)
        assertTrue(result.medicines[3].uncertainties.any { it.contains("different brand", ignoreCase = true) })
    }

    @Test fun malformedOrMissingModelOutputFailsClosed() {
        for (value in listOf("", "{broken", "{}", "{\"medicines\":\"not a list\"}")) {
            val error = assertThrows(MedicineOrganizerException::class.java) {
                sanitizeMedicineOrganization(value, 1, 1)
            }
            assertEquals(MedicineOrganizerError.INVALID_RESPONSE, error.code)
        }
    }

    @Test fun draftIdsAreDeterministicAndDetailIsBoundedAndReadable() {
        val medicine = OrganizedMedicine(
            medicineName = "Amoxicillin",
            strength = "500 mg",
            form = "capsule",
            quantity = "1 capsule",
            frequency = "Twice daily",
            suggestedTimes = listOf("08:00", "20:00"),
            mealTiming = MedicineMealTiming.AFTER_FOOD,
            instructions = "Finish the prescribed course.",
            durationDays = 5,
            prescriptionItemNumber = 2,
            medicineImageIndex = 0,
            prescriptionPageIndexes = listOf(0),
            confidence = 0.95,
            uncertainties = listOf("Confirm the faint quantity."),
            matchQuality = MedicineMatchQuality.MATCHED,
        )
        val first = stableMedicineDraftId("scan-123", medicine, 0)
        val second = stableMedicineDraftId("scan-123", medicine, 0)
        assertEquals(first, second)
        assertNotEquals(first, stableMedicineDraftId("scan-123", medicine, 1))
        assertEquals(true, MedicineDraft(first, medicine).copy(confirmed = true).confirmed)
        val manual = medicine.copy(confidence = null, matchQuality = MedicineMatchQuality.MANUAL)
        assertEquals(null, manual.confidence)
        assertEquals(MedicineMatchQuality.MANUAL, manual.matchQuality)

        val detail = medicineCareDetail(medicine)
        assertTrue(detail.contains("Dose: 500 mg · 1 capsule · capsule"))
        assertTrue(detail.contains("Times: 08:00, 20:00"))
        assertTrue(detail.contains("Food: after food"))
        assertTrue(detail.contains("Course: 5 days"))
        assertTrue(detail.length <= 4000)
    }
}
