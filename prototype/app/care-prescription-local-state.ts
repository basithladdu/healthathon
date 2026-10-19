import { organizeMedicineText, type MedicineOrganization } from './care-medicine-import-state.ts';

/** Local copied candidates keep their page; packing text never becomes a prescription. */
export function copiedPrescriptionPages(pages: readonly string[], packagingCount: number): MedicineOrganization {
  if (pages.length < 1 || pages.length > 5 || !Number.isInteger(packagingCount) || packagingCount < 0 || packagingCount > 10 || pages.some((page) => typeof page !== 'string' || page.length > 40_000)) throw new Error('Choose up to five readable prescription pages and ten optional packaging photos.');
  const medicines = pages.flatMap((text, page) => organizeMedicineText([text], []).medicines.map((candidate) => ({
    ...candidate, prescriptionPageIndexes: [page], medicineImageIndex: -1,
    matchQuality: 'prescription-only' as const, confidence: null,
    uncertainties: ['Check this copied reading against the original page. Packaging has not been automatically matched.'],
  })));
  return { medicines, unmatchedMedicineImageIndexes: Array.from({ length: packagingCount }, (_, index) => index),
    warnings: ['Reading errors are possible. Nothing is prescribed or changed. Compare every name, dose and direction with the original.', ...(!medicines.length ? ['No medicine lines could be read safely. Enter them manually from the original.'] : [])] };
}
