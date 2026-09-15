'use client';

import React, { createContext, useContext, useState, useMemo, useCallback } from 'react';

export interface GeoPatient {
  hospitalId: string;
  name: string;
  diagnosis: string;
  age: number;
  gender: string;
  address: string;
  zone: string;
  coordinates: [number, number]; // [longitude, latitude]
  cprStatus: 'dnacpr' | 'full_cpr' | 'limited';
  ceilingTier: 1 | 2 | 3;
  syringeDriverActive: boolean;
  syringeDriverDetails?: string;
  assignedAshaWorker: {
    name: string;
    phone: string;
    subCenter: string;
  };
  primarySurrogate: {
    name: string;
    relation: string;
    phone: string;
  };
  dispatchStatus: 'idle' | 'en_route' | 'on_scene' | 'completed';
  activeAmbulanceEtaMinutes?: number;
}

export interface FacilityLocation {
  id: string;
  name: string;
  type: 'hospital' | 'hospice' | 'subcenter';
  coordinates: [number, number];
  address: string;
  availableBeds: number;
  has24hPalliative: boolean;
}

export const INITIAL_FACILITIES: FacilityLocation[] = [
  {
    id: 'kidwai_center',
    name: 'Kidwai Memorial Institute of Oncology',
    type: 'hospital',
    coordinates: [77.5925, 12.9372], // Bangalore central south
    address: 'Dr M H Marigowda Road, Bangalore 560029',
    availableBeds: 18,
    has24hPalliative: true,
  },
  {
    id: 'karunashraya_hospice',
    name: 'Bangalore Hospice Trust (Karunashraya)',
    type: 'hospice',
    coordinates: [77.7126, 12.9591], // Bangalore east / Marathahalli
    address: 'Varthur Main Road, Kundalahalli Gate, Bangalore 560037',
    availableBeds: 8,
    has24hPalliative: true,
  },
  {
    id: 'jayanagar_subcenter',
    name: 'Jayanagar Community Palliative Outreach Post',
    type: 'subcenter',
    coordinates: [77.5833, 12.9299],
    address: '4th Block Jayanagar, Bangalore',
    availableBeds: 0,
    has24hPalliative: true,
  },
];

export const INITIAL_GEO_PATIENTS: GeoPatient[] = [
  {
    hospitalId: 'CANCER-20418',
    name: 'Sunita Sharma',
    diagnosis: 'Metastatic Adenocarcinoma of Lung (EGFR+)',
    age: 61,
    gender: 'Female',
    address: 'Flat 402, Shanthi Nivas, 5th Block, Jayanagar, Bangalore 560041',
    zone: 'South Zone · Jayanagar',
    coordinates: [77.5855, 12.926],
    cprStatus: 'dnacpr',
    ceilingTier: 1, // Comfort care home
    syringeDriverActive: true,
    syringeDriverDetails: 'Morphine 30mg + Midazolam 10mg + Glycopyrronium 0.6mg / 24h SC',
    assignedAshaWorker: {
      name: 'Lakshmi Devi (ASHA Lead)',
      phone: '+91 98451 22340',
      subCenter: 'Jayanagar Urban PHC',
    },
    primarySurrogate: {
      name: 'Rohan Sharma',
      relation: 'Son',
      phone: '+91 98450 11234',
    },
    dispatchStatus: 'idle',
  },
  {
    hospitalId: 'CANCER-20452',
    name: 'Sana Iyer',
    diagnosis: 'Stage III Cervical Carcinoma (Post-chemoradiation)',
    age: 54,
    gender: 'Female',
    address: 'No 18, 12th Main Road, Indiranagar, Bangalore 560038',
    zone: 'East Zone · Indiranagar',
    coordinates: [77.6412, 12.9784],
    cprStatus: 'limited',
    ceilingTier: 2, // Ward ceiling / non-invasive
    syringeDriverActive: false,
    assignedAshaWorker: {
      name: 'Mary Kom (ANM)',
      phone: '+91 94480 33419',
      subCenter: 'Ulsoor Community Health Center',
    },
    primarySurrogate: {
      name: 'Ramesh Iyer',
      relation: 'Husband',
      phone: '+91 98452 44321',
    },
    dispatchStatus: 'idle',
  },
  {
    hospitalId: 'CANCER-20489',
    name: 'Vikram Mehta',
    diagnosis: 'Stage IV Pancreatic Adenocarcinoma (Peritoneal spread)',
    age: 68,
    gender: 'Male',
    address: 'Villa 14, Prestige Palms, Whitefield Main Rd, Bangalore 560066',
    zone: 'East Zone · Whitefield',
    coordinates: [77.7499, 12.9698],
    cprStatus: 'dnacpr',
    ceilingTier: 1, // Palliative home ceiling
    syringeDriverActive: true,
    syringeDriverDetails: 'Oxycodone 40mg + Levomepromazine 12.5mg / 24h SC',
    assignedAshaWorker: {
      name: 'Pushpa Gowda (Palliative Nurse)',
      phone: '+91 98860 88231',
      subCenter: 'Whitefield PHC',
    },
    primarySurrogate: {
      name: 'Ananya Mehta',
      relation: 'Daughter',
      phone: '+91 98801 77654',
    },
    dispatchStatus: 'idle',
  },
  {
    hospitalId: 'CANCER-20471',
    name: 'Meera Raghavan',
    diagnosis: 'Advanced Epithelial Ovarian Carcinoma with Ascites',
    age: 58,
    gender: 'Female',
    address: 'B-201, Green Glen Layout, Bellandur, Bangalore 560103',
    zone: 'South-East Zone · Bellandur',
    coordinates: [77.6775, 12.9261],
    cprStatus: 'full_cpr',
    ceilingTier: 3, // Full resuscitation for reversible acute cause
    syringeDriverActive: false,
    assignedAshaWorker: {
      name: 'Kavitha Reddy (ASHA)',
      phone: '+91 97411 99012',
      subCenter: 'Bellandur Sub-Health Center',
    },
    primarySurrogate: {
      name: 'Kavya Raghavan',
      relation: 'Daughter',
      phone: '+91 99000 88123',
    },
    dispatchStatus: 'idle',
  },
];

export type ModalType =
  | null
  | 'tep'
  | 'syringe_driver'
  | 'fhir'
  | 'qr_scanner'
  | 'map_dispatch'
  | 'esas'
  | 'deprescribing'
  | 'prognosis'
  | 'emergency_triage'
  | 'caregiver_burden'
  | 'sedation_crisis'
  | 'trajectory_timeline'
  | 'anticipatory_box'
  | 'malignant_wound'
  | 'bone_sins'
  | 'cachexia_ascites'
  | 'neuropathic_blocks'
  | 'breathlessness_crisis'
  | 'delirium_engine'
  | 'pediatric_palliative'
  | 'bowel_obstruction'
  | 'renal_hepatic_palliative'
  | 'palliative_radiotherapy'
  | 'spinal_cord_compression'
  | 'malignant_hypercalcemia'
  | 'svco_decompression';

export interface ContinuityContextType {
  currentRole: string;
  switchRole: (role: string) => void;
  selectedPatientId: string;
  selectPatient: (hospitalId: string) => void;
  selectedGeoPatient: GeoPatient;
  geoPatients: GeoPatient[];
  facilities: FacilityLocation[];
  activeModal: ModalType;
  openModal: (modal: ModalType) => void;
  closeModal: () => void;
  activeDispatchToast: string | null;
  dispatchAshaVisit: (patientId: string) => void;
  dispatchEmergencyAmbulance: (patientId: string) => void;
  cancelDispatch: (patientId: string) => void;
}

const ContinuityContext = createContext<ContinuityContextType | undefined>(undefined);

export function ContinuityProvider({ children }: { children: React.ReactNode }) {
  const [currentRole, setCurrentRole] = useState('Dr Sujay');
  const [selectedPatientId, setSelectedPatientId] = useState('CANCER-20418');
  const [geoPatients, setGeoPatients] = useState<GeoPatient[]>(INITIAL_GEO_PATIENTS);
  const [activeModal, setActiveModal] = useState<ModalType>(null);
  const [activeDispatchToast, setActiveDispatchToast] = useState<string | null>(null);

  const switchRole = useCallback((role: string) => {
    setCurrentRole(role);
  }, []);

  const selectPatient = useCallback((hospitalId: string) => {
    setSelectedPatientId(hospitalId);
  }, []);

  const openModal = useCallback((modal: ModalType) => {
    setActiveModal(modal);
  }, []);

  const closeModal = useCallback(() => {
    setActiveModal(null);
  }, []);

  const showDispatchToast = useCallback((msg: string) => {
    setActiveDispatchToast(msg);
    setTimeout(() => setActiveDispatchToast(null), 4000);
  }, []);

  const dispatchAshaVisit = useCallback(
    (patientId: string) => {
      setGeoPatients((prev) =>
        prev.map((p) => {
          if (p.hospitalId === patientId) {
            return {
              ...p,
              dispatchStatus: 'en_route',
              activeAmbulanceEtaMinutes: 18,
            };
          }
          return p;
        })
      );
      const pat = geoPatients.find((p) => p.hospitalId === patientId);
      showDispatchToast(
        `🚑 Community Palliative Outreach Dispatched: ${pat?.assignedAshaWorker.name} en route to ${pat?.name} (ETA 18 mins)`
      );
    },
    [geoPatients, showDispatchToast]
  );

  const dispatchEmergencyAmbulance = useCallback(
    (patientId: string) => {
      setGeoPatients((prev) =>
        prev.map((p) => {
          if (p.hospitalId === patientId) {
            return {
              ...p,
              dispatchStatus: 'en_route',
              activeAmbulanceEtaMinutes: 11,
            };
          }
          return p;
        })
      );
      const pat = geoPatients.find((p) => p.hospitalId === patientId);
      showDispatchToast(
        `🚨 108 Emergency Ambulance Dispatched to ${pat?.name} (${pat?.zone}) · Triage: ${
          pat?.cprStatus === 'dnacpr' ? 'DNACPR Active (Comfort Directives Transmitted)' : 'Standard Acute Resuscitation'
        } (ETA 11 mins)`
      );
    },
    [geoPatients, showDispatchToast]
  );

  const cancelDispatch = useCallback(
    (patientId: string) => {
      setGeoPatients((prev) =>
        prev.map((p) => (p.hospitalId === patientId ? { ...p, dispatchStatus: 'idle', activeAmbulanceEtaMinutes: undefined } : p))
      );
      showDispatchToast(`Transit dispatch resolved for patient ${patientId}.`);
    },
    [showDispatchToast]
  );

  const selectedGeoPatient = useMemo(() => {
    return geoPatients.find((p) => p.hospitalId === selectedPatientId) || geoPatients[0];
  }, [geoPatients, selectedPatientId]);

  const value = useMemo(
    () => ({
      currentRole,
      switchRole,
      selectedPatientId,
      selectPatient,
      selectedGeoPatient,
      geoPatients,
      facilities: INITIAL_FACILITIES,
      activeModal,
      openModal,
      closeModal,
      activeDispatchToast,
      dispatchAshaVisit,
      dispatchEmergencyAmbulance,
      cancelDispatch,
    }),
    [
      currentRole,
      switchRole,
      selectedPatientId,
      selectPatient,
      selectedGeoPatient,
      geoPatients,
      activeModal,
      openModal,
      closeModal,
      activeDispatchToast,
      dispatchAshaVisit,
      dispatchEmergencyAmbulance,
      cancelDispatch,
    ]
  );

  return (
    <ContinuityContext.Provider value={value}>
      {children}
      {activeDispatchToast && (
        <div className="dispatch-toast-banner" role="alert">
          <span>{activeDispatchToast}</span>
        </div>
      )}
    </ContinuityContext.Provider>
  );
}

export function useContinuityContext(): ContinuityContextType {
  const ctx = useContext(ContinuityContext);
  if (!ctx) {
    throw new Error('useContinuityContext must be used within a ContinuityProvider');
  }
  return ctx;
}
