export const CARE_ROUTES = {
  'daily-care': '/home',
  'clinical-documents': '/my-documents',
  'access-care': '/find-care',
  'my-space': '/my-space',
  community: '/support-groups',
  'document-search': '/find-in-documents',
  calendar: '/calendar',
  'family-tasks': '/family-tasks',
  'visit-questions': '/next-visit-questions',
  'support-resources': '/support-resources',
  comfort: '/comfort',
  'cancer-overview': '/my-cancer-care',
  'symptom-diary': '/how-i-feel',
  'care-story': '/care-story',
  'lab-history': '/test-results',
  'voice-journal': '/journal',
  'doctor-pack': '/care-summary',
  'home-help': '/help-at-home',
  'support-places': '/saved-places',
  'open-questions': '/questions',
  'copy-tracker': '/shared-copies',
  'cost-help': '/costs',
  reports: '/reports',
  'my-plan': '/care-note',
  'my-timeline': '/care-timeline',
  'prepare-conversation': '/prepare-for-a-visit',
  'care-near-me': '/find-support',
  'my-doctors': '/care-team',
  'emergency-card': '/emergency-card',
  'my-details': '/my-details',
  consent: '/consent',
  home: '/doctor',
  'doctor-review': '/doctor/review',
  'doctor-record': '/doctor/record',
  'doctor-history': '/doctor/history',
  'note-history': '/care-note/history',
  caregiver: '/workspace/family',
  guide: '/workspace/conversations',
  worklist: '/workspace/follow-ups',
  patients: '/workspace/patients',
  drafts: '/workspace/drafts',
  records: '/workspace/records',
  audit: '/workspace/history',
  patient: '/workspace/patient',
  outreach: '/workspace/outreach',
  draft: '/workspace/draft',
  verify: '/workspace/review',
  retrieve: '/workspace/retrieve',
  appointments: '/workspace/appointments',
} as const;

export type CareView = keyof typeof CARE_ROUTES;

export function careViewFromPath(path: string): CareView | undefined {
  const normalized = path.replace(/\/$/, '') || '/';
  if (normalized === '/next-doctor') return 'doctor-pack';
  if (normalized === '/costs' || normalized === '/family-tasks' || normalized === '/saved-places') return 'daily-care';
  if (normalized === '/questions') return 'visit-questions';
  return (Object.keys(CARE_ROUTES) as CareView[]).find((view) => CARE_ROUTES[view] === normalized);
}

export function canOpenCareView(view: CareView, role: 'family' | 'patient' | 'care-team', clinician: string) {
  if (role !== 'care-team') {
    if (view === 'caregiver') return role === 'family';
    if (view === 'my-details' || view === 'consent') return true;
    return !CARE_ROUTES[view].startsWith('/workspace/') && !CARE_ROUTES[view].startsWith('/doctor');
  }
  if (view === 'draft' || view === 'verify' || view === 'doctor-review' || view === 'doctor-record') return clinician.startsWith('Dr Sujay');
  if (view === 'outreach') return clinician.startsWith('Anitha Rao');
  return true;
}
