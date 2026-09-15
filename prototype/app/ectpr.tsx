import { useEffect, useState, useMemo } from 'react';
import QRCode from 'qrcode';
import { AudioTtsPlayer } from './tts-speech';
import { OfflineQrScannerModal } from './offline-qr-scanner-modal';
import {
  IconHeartPulse,
  IconLungs,
  IconHospital,
  IconShieldAlert,
  IconShieldCheck,
  IconUsers,
  IconStethoscope,
} from './icons';

export type BreathingCeiling =
  | 'comfort-only'
  | 'mask'
  | 'non-rebreather'
  | 'hfnc'
  | 'niv'
  | 'intubation-trial'
  | 'intubation-no-limit';

export type EctprQuickView = {
  hospitalId: string;
  patientName: string;
  diagnosis: string;
  goal: 'prioritise-life' | 'balanced' | 'prioritise-comfort' | null;
  hospitalTransfer: 'any-deterioration' | 'listed-reasons-only' | 'no' | null;
  cpr: 'attempt' | 'dnacpr' | null;
  breathingCeiling: BreathingCeiling | null;
  icu: 'yes' | 'time-limited-trial' | 'no' | null;
  icuTrialDays: number | null;
  decisionMaker: { name: string; relationship: string } | null;
  treatingConsultant: string | null;
  palliativeContact: string | null;
  signedBy: string | null;
  signedOn: string | null;
  reviewDueBy: string | null;
  formVersion: string;
  recordVersion: string;
};

export const ectprQuickViews: Record<string, EctprQuickView> = {
  'CANCER-20418': {
    hospitalId: 'CANCER-20418',
    patientName: 'Meera Raghavan',
    diagnosis: 'Stage IV NSCLC',
    goal: null,
    hospitalTransfer: null,
    cpr: null,
    breathingCeiling: null,
    icu: null,
    icuTrialDays: null,
    decisionMaker: { name: 'Kavya Raghavan', relationship: 'Daughter (primary proxy)' },
    treatingConsultant: 'Dr Sujay',
    palliativeContact: 'Palliative care team',
    signedBy: 'Dr Sujay',
    signedOn: '21 Aug 2026',
    reviewDueBy: '28 Aug 2026',
    formVersion: 'ECTPR Beta v0.1',
    recordVersion: 'Version 1',
  },
  'CANCER-20377': {
    hospitalId: 'CANCER-20377',
    patientName: 'Leela Thomas',
    diagnosis: 'Stage IV Ovarian Carcinoma',
    goal: 'balanced',
    hospitalTransfer: 'listed-reasons-only',
    cpr: 'dnacpr',
    breathingCeiling: 'niv',
    icu: 'no',
    icuTrialDays: null,
    decisionMaker: { name: 'Joel Thomas', relationship: 'Son' },
    treatingConsultant: 'Dr Sujay',
    palliativeContact: 'Palliative care team',
    signedBy: 'Dr Sujay',
    signedOn: '21 Aug 2026',
    reviewDueBy: '04 Sep 2026',
    formVersion: 'ECTPR Beta v0.1',
    recordVersion: 'Version 1',
  },
};

const GOAL_LABELS: Record<string, string> = {
  'prioritise-life': 'A · Prioritise life',
  balanced: 'B · Balanced / ceiling of care',
  'prioritise-comfort': 'C · Prioritise comfort',
};

const TRANSFER_LABELS: Record<string, string> = {
  'any-deterioration': 'Yes, for any deterioration',
  'listed-reasons-only': 'Only for the specific reasons listed in the full record',
  no: 'No — manage at home or hospice; call the team first',
};

const TRANSFER_SHORT_LABELS: Record<string, string> = {
  'any-deterioration': 'Yes',
  'listed-reasons-only': 'Only for listed reasons',
  no: 'No — call the team first',
};

const CPR_LABELS: Record<string, string> = {
  attempt: 'Attempt CPR',
  dnacpr: 'Do not attempt CPR (DNACPR)',
};

const BREATHING_LABELS: Record<BreathingCeiling, string> = {
  'comfort-only': 'Comfort measures / oxygen for breathlessness only',
  mask: 'Nasal prongs or face mask',
  'non-rebreather': 'Non-rebreather mask',
  hfnc: 'High-flow nasal cannula (HFNC)',
  niv: 'Non-invasive ventilation (CPAP / BiPAP)',
  'intubation-trial': 'Intubation and invasive ventilation, as a time-limited trial',
  'intubation-no-limit': 'Intubation and invasive ventilation, without a pre-set limit',
};

const BREATHING_PLAIN_LANGUAGE: Record<BreathingCeiling, string> = {
  'comfort-only': 'Medicine and comfort measures for breathlessness, without chasing an oxygen number.',
  mask: 'A light tube or mask giving low-flow oxygen. Comfortable, does not stop talking or eating.',
  'non-rebreather': 'A tight-fitting mask giving strong oxygen. Tolerable, but can feel confining and makes talking harder.',
  hfnc: 'Warmed, humidified oxygen through a nasal tube, given faster and stronger than usual. Usually needs a higher-level bed.',
  niv: 'A tight mask that pushes air in. It can help, but it is often uncomfortable and makes talking and eating hard.',
  'intubation-trial': 'A breathing tube and machine in intensive care, tried for a set period to see if it helps.',
  'intubation-no-limit': 'A breathing tube and machine in intensive care, continued for as long as it is needed.',
};

const ICU_TRIAL_DAYS_FALLBACK = 0;

function icuLabel(record: EctprQuickView): string {
  if (record.icu === 'yes') return 'Yes';
  if (record.icu === 'time-limited-trial') {
    return `Yes, as a time-limited trial of ${record.icuTrialDays ?? ICU_TRIAL_DAYS_FALLBACK} days`;
  }
  if (record.icu === 'no') return 'No — ward-level or comfort care only';
  return '';
}

function unrecordedLabel(audience: 'clinician' | 'patient' | 'family'): string {
  return audience === 'clinician' ? 'No preference recorded — provide standard care' : 'Not decided yet';
}

function isBelowIntubation(ceiling: BreathingCeiling | null): boolean {
  return (
    ceiling === 'comfort-only' ||
    ceiling === 'mask' ||
    ceiling === 'non-rebreather' ||
    ceiling === 'hfnc' ||
    ceiling === 'niv'
  );
}

function AnswerCell(props: {
  overline: string;
  recorded: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={`ectpr-answer ${props.recorded ? 'ectpr-answer--recorded' : 'ectpr-answer--unrecorded'}`}>
      <div className="ectpr-answer-overline">{props.overline}</div>
      <div className="ectpr-answer-value">{props.children}</div>
    </div>
  );
}

export function EdQuickView(props: {
  record: EctprQuickView | null;
  audience: 'clinician' | 'patient' | 'family';
}): React.JSX.Element {
  const { record, audience } = props;

  if (!record) {
    return (
      <div className="ectpr-container">
        <div className="ectpr-card ectpr-quickview">
          <div className="ectpr-eyebrow">Emergency care &amp; treatment preferences</div>
          <h2 className="ectpr-heading">ED Quick View</h2>
          {audience === 'clinician' ? (
            <p className="ectpr-empty">
              No emergency care preference record has been signed for this patient. Provide full standard emergency
              care and contact the treating team.
            </p>
          ) : (
            <p className="ectpr-empty">
              No emergency care plan has been signed yet.{' '}
              {audience === 'family'
                ? 'Your care team will talk this through with the patient and family.'
                : 'Your care team will talk this through with you.'}
            </p>
          )}
        </div>
      </div>
    );
  }

  const cprLabel = record.cpr ? CPR_LABELS[record.cpr] : unrecordedLabel(audience);
  const breathingLabel = record.breathingCeiling
    ? BREATHING_LABELS[record.breathingCeiling]
    : unrecordedLabel(audience);
  const transferLabel = record.hospitalTransfer ? TRANSFER_LABELS[record.hospitalTransfer] : unrecordedLabel(audience);
  const icuText = record.icu ? icuLabel(record) : unrecordedLabel(audience);
  const goalLabel = record.goal ? GOAL_LABELS[record.goal] : unrecordedLabel(audience);

  const showEscalationNote =
    audience === 'clinician' && record.cpr === 'attempt' && isBelowIntubation(record.breathingCeiling);

  const plainLanguageLine =
    audience !== 'clinician' && record.breathingCeiling ? BREATHING_PLAIN_LANGUAGE[record.breathingCeiling] : null;

  const emergencySpokenText = useMemo(() => {
    return `Emergency clinical directives for ${record.patientName}, hospital ID ${record.hospitalId}. Primary diagnosis: ${record.diagnosis}. Overall care goal: ${goalLabel}. Cardiopulmonary resuscitation: ${cprLabel}. Highest breathing support ceiling: ${breathingLabel}. Hospital transfer: ${transferLabel}. Intensive care unit admission: ${icuText}. Named proxy decision maker: ${record.decisionMaker ? `${record.decisionMaker.name}, ${record.decisionMaker.relationship}` : 'Not recorded'}. Crucial directive: If in doubt, resuscitate. Signed by ${record.signedBy || 'clinical consultant'} on ${record.signedOn || 'recent record'}.`;
  }, [record, goalLabel, cprLabel, breathingLabel, transferLabel, icuText]);

  return (
    <div className="ectpr-container">
      <div className="ectpr-card ectpr-quickview">
        <div className="ectpr-eyebrow">
          <IconShieldAlert className="w-3.5 h-3.5 text-emerald-700" />
          <span>Emergency care &amp; treatment preferences</span>
        </div>
        <h2 className="ectpr-heading">ED Quick View</h2>
        <div className="ectpr-subline">
          {record.recordVersion} · signed by {record.signedBy ?? 'Not recorded'} on {record.signedOn ?? 'Not recorded'}{' '}
          · {record.formVersion}
        </div>

        <AudioTtsPlayer
          title="Emergency Voice Broadcast (Hands-Free Readout)"
          subtitle="Open-source voice readout of resuscitation and ceiling directives for emergency trauma teams"
          text={emergencySpokenText}
          variant="emergency"
          className="mb-3"
        />

        <div className="ectpr-rule-banner">
          {audience === 'clinician' ? (
            <p>
              <strong>If in doubt, resuscitate.</strong> Give full standard emergency care if this record is
              unsigned, out of date or ambiguous; if the problem is new, reversible and unrelated to the illness (for
              example an injury, choking, a drug reaction or low blood sugar); or if the patient has capacity and now
              says something different, which always overrides this record. An unrecorded answer means no preference
              has been recorded — it does not mean refusal.
            </p>
          ) : (
            <p>
              In an emergency, doctors give full standard care if this plan is unclear or out of date, or if the
              problem is new and could be reversed. What {audience === 'patient' ? 'you say' : 'the patient says'} at
              the time always comes first.
            </p>
          )}
        </div>

        <div className="ectpr-goal-row">
          <div className="ectpr-goal-label">
            <IconStethoscope className="w-4 h-4 text-emerald-700 inline mr-1" />
            <span>Overall goal</span>
          </div>
          <div className="ectpr-goal-value">{goalLabel}</div>
        </div>

        <div className="ectpr-answers-grid">
          <AnswerCell overline="Bring to hospital?" recorded={!!record.hospitalTransfer}>
            <span className="flex items-center gap-1.5">
              <IconHospital className="w-4 h-4 text-amber-600 inline" />
              <span>{transferLabel}</span>
            </span>
          </AnswerCell>
          <AnswerCell overline="CPR if the heart stops?" recorded={!!record.cpr}>
            <span className="flex items-center gap-1.5">
              <IconHeartPulse className="w-4 h-4 text-rose-600 inline" />
              <span>{cprLabel}</span>
            </span>
          </AnswerCell>
          <AnswerCell overline="Highest breathing support" recorded={!!record.breathingCeiling}>
            <span className="flex items-center gap-1.5">
              <IconLungs className="w-4 h-4 text-sky-600 inline" />
              <span>{breathingLabel}</span>
            </span>
            {plainLanguageLine && <div className="ectpr-plain-language">{plainLanguageLine}</div>}
          </AnswerCell>
          <AnswerCell overline="ICU admission?" recorded={!!record.icu}>
            <span className="flex items-center gap-1.5">
              <IconShieldCheck className="w-4 h-4 text-teal-600 inline" />
              <span>{icuText}</span>
            </span>
          </AnswerCell>
        </div>

        <div className="ectpr-contacts">
          <div className="ectpr-contacts-title">People to call:</div>
          <div>
            Named decision-maker:{' '}
            {record.decisionMaker
              ? `${record.decisionMaker.name} · ${record.decisionMaker.relationship}`
              : 'Not recorded'}
          </div>
          <div>Treating consultant: {record.treatingConsultant ?? 'Not recorded'}</div>
          <div>Palliative care: {record.palliativeContact ?? 'Not recorded'}</div>
          <div className="ectpr-muted">Ask your care team for their phone number.</div>
        </div>

        <div className="ectpr-review-due">Review due by {record.reviewDueBy ?? 'Not recorded'}</div>

        {showEscalationNote && (
          <div className="ectpr-note">
            The full record asks for a written reason when CPR is chosen with a breathing-support ceiling below
            intubation (Section 8).
          </div>
        )}

        <div className="ectpr-footer">
          A recorded preference from a clinician-signed ECTPR. Not a treatment order or a legal directive. Sample patient record.
        </div>
      </div>
    </div>
  );
}

async function sha256HexUpper(text: string): Promise<string> {
  const data = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest('SHA-256', data);
  const bytes = Array.from(new Uint8Array(digest));
  return bytes.map((b) => b.toString(16).padStart(2, '0').toUpperCase()).join('');
}

function buildQrPayload(record: EctprQuickView): string {
  const lines = [
    'EMERGENCY CARE PLAN (SAMPLE RECORD)',
    `Patient: ${record.patientName}`,
    `UHID: ${record.hospitalId}`,
    `Goal: ${record.goal ? GOAL_LABELS[record.goal] : 'Not recorded'}`,
    `CPR: ${record.cpr ? CPR_LABELS[record.cpr] : 'Not recorded'}`,
    `Breathing ceiling: ${record.breathingCeiling ? BREATHING_LABELS[record.breathingCeiling] : 'Not recorded'}`,
    `Hospital transfer: ${record.hospitalTransfer ? TRANSFER_LABELS[record.hospitalTransfer] : 'Not recorded'}`,
    `ICU: ${record.icu ? icuLabel(record) : 'Not recorded'}`,
    `Decision-maker: ${
      record.decisionMaker ? `${record.decisionMaker.name} (${record.decisionMaker.relationship})` : 'Not recorded'
    }`,
    `Signed: ${record.signedBy ?? 'Not recorded'}, ${record.signedOn ?? 'Not recorded'}`,
    `Record: ${record.formVersion} · ${record.recordVersion}`,
    'If unclear, or the problem is new and reversible: treat fully.',
  ];
  return lines.join('\n');
}

function CardFace(props: { record: EctprQuickView; watermark?: boolean; qrDataUrl?: string | null; issuedOn?: string | null }) {
  const { record, watermark, qrDataUrl, issuedOn } = props;
  return (
    <div className="ectpr-card ectpr-emergency-card">
      {watermark && <div className="ectpr-watermark">PREVIEW — NOT ISSUED</div>}
      <div className="ectpr-card-band">EMERGENCY CARE PLAN IN PLACE</div>
      <div className="ectpr-card-body">
        <div>Patient: {record.patientName}</div>
        <div>UHID: {record.hospitalId}</div>
        <div>Diagnosis: {record.diagnosis}</div>
        <div>CPR: {record.cpr ? CPR_LABELS[record.cpr] : 'Not recorded'}</div>
        <div>
          Breathing support ceiling: {record.breathingCeiling ? BREATHING_LABELS[record.breathingCeiling] : 'Not recorded'}
        </div>
        <div>
          Hospital transfer:{' '}
          {record.hospitalTransfer ? TRANSFER_SHORT_LABELS[record.hospitalTransfer] : 'Not recorded'}
        </div>
        <div>ICU: {record.icu ? icuLabel(record) : 'Not recorded'}</div>
        <div>Call first: {record.palliativeContact ?? 'Not recorded'}</div>
        <div>
          Decision-maker:{' '}
          {record.decisionMaker ? `${record.decisionMaker.name} (${record.decisionMaker.relationship})` : 'Not recorded'}
        </div>
        <div>Full record: ask the care team</div>
        <div>
          Signed by {record.signedBy ?? 'Not recorded'} · {record.signedOn ?? 'Not recorded'}
        </div>
      </div>
      {qrDataUrl && (
        <div className="ectpr-card-qr">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={qrDataUrl} alt="QR code containing this emergency card as text" width={120} height={120} />
        </div>
      )}
      {issuedOn && <div className="ectpr-card-issued">Issued {issuedOn}</div>}
      <div className="ectpr-card-footer">
        <div>If this card is unclear, or the problem is new and reversible — treat fully and call the number above.</div>
        <div className="ectpr-muted">Sample patient card</div>
      </div>
    </div>
  );
}

export function EmergencyCard(props: {
  record: EctprQuickView | null;
  audience: 'patient' | 'family';
  issuedOn: string | null;
  onIssue?: () => void;
}): React.JSX.Element {
  const { record, audience, issuedOn, onIssue } = props;
  const [qrResult, setQrResult] = useState<{
    payload: string;
    dataUrl: string | null;
    checksum: string | null;
  } | null>(null);
  const [scannerOpen, setScannerOpen] = useState(false);
  const payload = record && issuedOn ? buildQrPayload(record) : null;
  const currentQr = qrResult?.payload === payload ? qrResult : null;
  const qrDataUrl = currentQr?.dataUrl ?? null;
  const checksum = currentQr?.checksum ?? null;

  useEffect(() => {
    let cancelled = false;
    if (!payload) return;
    (async () => {
      try {
        const hash = await sha256HexUpper(payload);
        const checksumShort = hash.slice(0, 16);
        const fullPayload = `${payload}\nChecksum: ${checksumShort}`;
        const dataUrl = await QRCode.toDataURL(fullPayload, { errorCorrectionLevel: 'M', margin: 1, width: 240 });
        if (!cancelled) {
          setQrResult({ payload, dataUrl, checksum: checksumShort });
        }
      } catch {
        if (!cancelled) {
          setQrResult({ payload, dataUrl: null, checksum: null });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [payload]);

  if (!record) {
    return (
      <div className="ectpr-container">
        <div className="ectpr-card ectpr-empty-card">
          No emergency care plan has been signed yet, so there is no card to issue.
        </div>
      </div>
    );
  }

  if (!issuedOn) {
    if (audience === 'family') {
      const firstName = record.patientName.split(' ')[0];
      return (
        <div className="ectpr-container">
          <div className="ectpr-card ectpr-empty-card">{firstName} has not issued an emergency card yet.</div>
        </div>
      );
    }
    return (
      <div className="ectpr-container">
        <CardFace record={record} watermark />
        <button type="button" className="ectpr-button ectpr-button--primary" onClick={() => onIssue?.()}>
          Issue my emergency card
        </button>
        <p className="ectpr-issue-help">
          Issuing records that you have a card. Keep one copy at home, one with the family member who travels with
          you, and one inside the front door for the ambulance crew.
        </p>
      </div>
    );
  }

  function handlePrint() {
    document.body.classList.add('ectpr-printing');
    const cleanup = () => document.body.classList.remove('ectpr-printing');
    window.addEventListener('afterprint', cleanup, { once: true });
    setTimeout(cleanup, 3000);
    window.print();
  }

  return (
    <div className="ectpr-container">
      <CardFace record={record} qrDataUrl={qrDataUrl} issuedOn={issuedOn} />
      {checksum && (
        <div className="ectpr-checksum-block">
          <div className="ectpr-checksum">
            Checksum {checksum.slice(0, 4)}…{checksum.slice(-4)}
          </div>
          <p className="ectpr-muted">
            The checksum identifies this exact version of the card. It is not a digital signature and does not prove
            who made it. Anyone who scans the code can read it — keep the card with the patient.
          </p>
        </div>
      )}
      {!qrDataUrl && <p role="status">{currentQr ? 'The QR code could not be created. Reopen the card to try again.' : 'Preparing the QR code…'}</p>}
      <div className="ectpr-actions-row" style={{ display: 'flex', gap: '10px', marginTop: '16px', flexWrap: 'wrap' }}>
        <button type="button" className="ectpr-button ectpr-button--primary" onClick={handlePrint} disabled={!qrDataUrl}>
          🖨️ Print card
        </button>
        <button
          type="button"
          className="ectpr-button secondary-button"
          style={{
            background: '#e6f5ec',
            borderColor: '#b4e0c6',
            color: '#146b3e',
            fontWeight: 700,
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
          }}
          onClick={() => setScannerOpen(true)}
          disabled={!qrDataUrl}
        >
          <span>🚑 Paramedic Offline QR Scanner</span>
        </button>
      </div>

      {scannerOpen && (
        <OfflineQrScannerModal
          record={record}
          checksum={checksum}
          onClose={() => setScannerOpen(false)}
        />
      )}
    </div>
  );
}
