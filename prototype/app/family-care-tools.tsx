'use client';

import { FamilySymptomDiary } from './family-symptom-diary';
import type { SymptomEntry } from './family-symptom-state';
import { FamilyCareStory } from './family-care-story';
import type { CareStoryEntry } from './family-care-story-state';
import { FamilyHandoverPack } from './family-handover-pack';
import type { HandoverContact } from './family-handover-state';
import { FamilyHomeHelp } from './family-home-help';
import type { HomeHelpEntry } from './family-home-help-state';
import { FamilySupportPlaces } from './family-support-places';
import type { SupportPlace } from './family-support-state';
import { FamilyOpenQuestions } from './family-open-questions';
import type { OpenQuestion } from './family-open-question-state';
import { FamilyCopyTracker } from './family-copy-tracker';
import type { CareCopyEntry } from './family-copy-state';
import type { SummaryRelease } from './summary-state';
import { FamilyVoiceJournal } from './family-voice-journal';
import type { VoiceJournalEntry } from './family-voice-state';
import type { CareSaveStatus } from './care-local-store';
import { FamilyCostHelp } from './family-cost-help';
import type { FamilyCostState } from './family-cost-state';
import { FamilyCancerOverview } from './family-cancer-overview';
import type { CareReport } from './care-calendar-state';
import { FamilyLabHistory } from './family-lab-history';
import type { LabResultEntry } from './family-lab-state';
import { IconArrowRight } from './icons';
import { CareArt } from './care-art';
import { CareRouteLink } from './care-route-link';

export type FamilyTool = 'symptom-diary' | 'care-story' | 'doctor-pack' | 'home-help' | 'support-places' | 'open-questions' | 'copy-tracker' | 'voice-journal' | 'cancer-overview' | 'cost-help' | 'lab-history';
export function FamilyCareTools({ tool, patientId, patientName, author, today, hindi, symptoms, story, recordedStory, contacts, knownContacts, homeHelp, supportPlaces, questions, copies, releases, voiceJournal, onVoiceJournal, voiceSave, costs, onCosts, labResults, onLabResults, diagnosis, team, reports, onOpen, onCareNote, onReports, onSymptoms, onStory, onContacts, onHomeHelp, onSupportPlaces, onQuestions, onCopies, note, calendarText, reportNames }: {
  tool: FamilyTool; patientId: string; patientName: string; author: string; today: string; hindi: boolean;
  symptoms: SymptomEntry[]; story: CareStoryEntry[]; contacts: HandoverContact[];
  recordedStory?: readonly CareStoryEntry[];
  knownContacts?: readonly HandoverContact[];
  homeHelp: HomeHelpEntry[]; supportPlaces: SupportPlace[];
  onHomeHelp: (entries: HomeHelpEntry[]) => void; onSupportPlaces: (entries: SupportPlace[]) => void;
  questions: OpenQuestion[]; copies: CareCopyEntry[]; releases: readonly SummaryRelease[];
  voiceJournal: VoiceJournalEntry[]; onVoiceJournal: (entries: VoiceJournalEntry[]) => void; voiceSave: CareSaveStatus;
  costs: FamilyCostState; onCosts: (state: FamilyCostState) => void;
  labResults: LabResultEntry[]; onLabResults: (entries: LabResultEntry[]) => void;
  diagnosis: string; team: string; reports: CareReport[]; onOpen: (tool: FamilyTool) => void; onCareNote: () => void; onReports: () => void;
  onQuestions: (entries: OpenQuestion[]) => void; onCopies: (entries: CareCopyEntry[]) => void;
  onSymptoms: (entries: SymptomEntry[]) => void; onStory: (entries: CareStoryEntry[]) => void; onContacts: (entries: HandoverContact[]) => void;
  note: { version: number; physician: string; releasedAt: string; fields: Array<{ label: string; value: string }> } | null;
  calendarText: string; reportNames: string[];
}) {
  return <div className="family-care-tools">
    {tool === 'symptom-diary' && <FamilySymptomDiary key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={symptoms} onChange={onSymptoms} />}
    {tool === 'care-story' && <FamilyCareStory key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={story} recordedEntries={recordedStory} onChange={onStory} />}
    {tool === 'doctor-pack' && <FamilyHandoverPack key={patientId} patientId={patientId} patientName={patientName} author={author} hindi={hindi} note={note} calendarText={calendarText} reportNames={reportNames} contacts={contacts} knownContacts={knownContacts} onContactsChange={onContacts} />}
    {tool === 'home-help' && <FamilyHomeHelp key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={homeHelp} onChange={onHomeHelp} />}
    {tool === 'support-places' && <FamilySupportPlaces key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={supportPlaces} onChange={onSupportPlaces} />}
    {tool === 'open-questions' && <FamilyOpenQuestions key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={questions} onChange={onQuestions} />}
    {tool === 'copy-tracker' && <FamilyCopyTracker key={patientId} patientId={patientId} patientName={patientName} author={author} today={today} hindi={hindi} releases={releases} entries={copies} onChange={onCopies} />}
    {tool === 'voice-journal' && <FamilyVoiceJournal key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} entries={voiceJournal} onChange={onVoiceJournal} saveStatus={voiceSave} />}
    {tool === 'cost-help' && <FamilyCostHelp key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} state={costs} onChange={onCosts} />}
    {tool === 'lab-history' && <FamilyLabHistory key={patientId} patientId={patientId} author={author} today={today} hindi={hindi} reports={reports} entries={labResults} onChange={onLabResults} />}
    {tool === 'cancer-overview' && <FamilyCancerOverview key={patientId} patientId={patientId} patientName={patientName} diagnosis={diagnosis} team={team} hindi={hindi} symptoms={symptoms} story={story} reports={reports} onCareStory={() => onOpen('care-story')} onSymptoms={() => onOpen('symptom-diary')} onCareNote={onCareNote} onReports={onReports} />}
  </div>;
}

export function FamilyToolButtons({ hindi, onOpen }: { hindi: boolean; onOpen: (tool: FamilyTool) => void }) {
  const tools = [
    { key: 'cancer-overview', en: 'My cancer care', hi: 'मेरी कैंसर देखभाल' },
    { key: 'symptom-diary', en: 'How are you feeling?', hi: 'आज कैसा लग रहा है?' },
    { key: 'care-story', en: 'Your care story', hi: 'अब तक की देखभाल' },
    { key: 'lab-history', en: 'My test results', hi: 'मेरी जाँच के नतीजे' },
    { key: 'voice-journal', en: 'My journal', hi: 'मेरी डायरी' },
    { key: 'doctor-pack', en: 'Take to the next doctor', hi: 'अगले डॉक्टर के लिए' },
    { key: 'home-help', en: 'Help at home', hi: 'घर पर मदद' },
    { key: 'open-questions', en: 'Still to discuss', hi: 'अभी बात करना बाकी है' },
    { key: 'copy-tracker', en: 'Who has the latest copy?', hi: 'नई कॉपी किसके पास है?' },
    { key: 'cost-help', en: 'Help with costs', hi: 'खर्च में मदद' },
  ] as const;
  return <div className="family-tool-buttons">{tools.map(({ key, en, hi }) => <CareRouteLink key={key} view={key} data-tool={key} onOpen={() => onOpen(key)}>
    <span className="family-tool-icon" aria-hidden="true"><CareArt kind={key} /></span><span className="family-tool-label">{hindi ? hi : en}</span><span className="family-tool-arrow" aria-hidden="true"><IconArrowRight /></span>
  </CareRouteLink>)}</div>;
}
