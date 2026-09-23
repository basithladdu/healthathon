export type MedicineAccessStatus = 'to-call' | 'contacted' | 'call-again';

export type MedicineAccessSnapshot = {
  location: string;
  contactName: string;
  contactPhone: string;
  enquiry: string;
  status: MedicineAccessStatus;
  updatedAt: string;
};

export type MedicineAccessEntry = MedicineAccessSnapshot & {
  previous?: MedicineAccessSnapshot | null;
};

export const EMPTY_MEDICINE_ACCESS_ENTRY: MedicineAccessEntry = {
  location: '',
  contactName: '',
  contactPhone: '',
  enquiry: '',
  status: 'to-call',
  updatedAt: '',
  previous: null,
};
