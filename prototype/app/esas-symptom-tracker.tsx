'use client';

import { FamilySymptomDiary } from './family-symptom-diary';
import type { SymptomEntry } from './family-symptom-state';
import type { CareSaveStatus } from './care-local-store';

export interface EsasTrackerProps {
  patientId: string;
  patientName: string;
  author?: string;
  hindi?: boolean;
  entries?: SymptomEntry[];
  onEntriesChange?: (entries: SymptomEntry[]) => void;
  saveStatus?: CareSaveStatus;
  onClose?: () => void;
}

// Keep the existing route/import stable. The previous panel changed instrument
// wording and generated baseline/triage results, so it must not claim ESAS-r use.
// The formal questionnaire remains pending clinical selection; the personal diary
// uses the caller's existing record store and never calls the old total callback.
export function EsasSymptomTracker({
  patientId, patientName, author, hindi = false, entries, onEntriesChange, saveStatus, onClose,
}: EsasTrackerProps) {
  const today = new Date().toLocaleDateString('en-CA');
  const canRecord = Boolean(patientId.trim() && author?.trim() && entries && onEntriesChange);
  return <section className="esas-container">
    <header className="esas-header">
      <div className="esas-title-group">
        <h2 id="esas-title" className="esas-title">{hindi ? 'तबीयत के नोट' : 'Symptom notes'}</h2>
        <p>{patientName} · {patientId}</p>
      </div>
      {onClose && <button type="button" className="secondary-button" onClick={onClose} aria-label={hindi ? 'तबीयत के नोट बंद करें' : 'Close symptom notes'}>{hindi ? 'बंद करें' : 'Close'}</button>}
    </header>
    <div className="esas-body">
      <p>{hindi
        ? 'क्लिनिकल प्रश्नावली का चुनाव देखभाल टीम की समीक्षा के बाद होगा। यहाँ अपने अनुभव के नोट लिख सकते हैं।'
        : 'The care team is reviewing the clinical questionnaire. Personal symptom notes can be recorded here.'}</p>
      {canRecord && entries && onEntriesChange && author ? <FamilySymptomDiary
        patientId={patientId} author={author} today={today} hindi={hindi}
        entries={entries} onChange={onEntriesChange} saveStatus={saveStatus}
      /> : <p role="status">{hindi
        ? 'नोट लिखने के लिए मरीज़ की तबीयत की डायरी खोलें। इस स्क्रीन ने कोई आकलन या स्कोर सेव नहीं किया है।'
        : "Open the patient's feeling diary to record an entry. This screen has not saved an assessment or a score."}</p>}
    </div>
  </section>;
}
