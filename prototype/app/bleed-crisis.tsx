'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconClock,
  IconUsers,
  IconHeartPulse,
} from './icons';

export type BleedSite =
  | 'carotid'
  | 'haemoptysis'
  | 'upper_gi'
  | 'fungating'
  | 'haematuria'
  | 'gynae';

export type BleedRoute = 'iv' | 'im' | 'buccal' | 'sc';

interface BleedCrisisModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  weightKg?: number;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

const SITES: Record<
  BleedSite,
  {
    label: string;
    lead: string;
    /** Minutes from catastrophic bleed onset to loss of consciousness. */
    minutesToUnconscious: number;
    points: number;
    position: string;
    firstAid: string[];
  }
> = {
  carotid: {
    label: 'Carotid blowout (head & neck tumour)',
    lead: 'Arterial. The fastest on this list — plan in minutes, not hours.',
    minutesToUnconscious: 2,
    points: 8,
    position: 'Sit upright, bleeding side down, head turned away from the carer.',
    firstAid: [
      'Firm gloved pressure over the bleeding point with a dark towel — do not let go.',
      'A second person calls for help; the first person never leaves the bedside.',
      'Suction within reach: blood in the airway is what patients fear most.',
    ],
  },
  haemoptysis: {
    label: 'Massive haemoptysis (lung or airway tumour)',
    lead: 'Death is usually by asphyxiation rather than blood loss, so position is the whole game.',
    minutesToUnconscious: 4,
    points: 6,
    position: 'Lateral, bleeding lung DOWN, head slightly lower, to protect the good lung.',
    firstAid: [
      'Turn onto the bleeding side immediately — this keeps the unaffected lung dry.',
      'Suction the mouth only. Do not suction blindly down the airway.',
      'Oxygen for comfort; do not lie the patient flat on their back.',
    ],
  },
  upper_gi: {
    label: 'Upper GI bleed (varices, gastric tumour)',
    lead: 'Large-volume haematemesis. Frightening to watch and often recurrent.',
    minutesToUnconscious: 8,
    points: 5,
    position: 'Left lateral, recovery position, head up 20–30°.',
    firstAid: [
      'Left lateral position so vomited blood drains out, not in.',
      'Bowl and dark towels ready; keep the mouth clear.',
      'Nothing by mouth until reviewed.',
    ],
  },
  fungating: {
    label: 'Fungating or ulcerating tumour (breast, skin, node)',
    lead: 'Usually oozing or venous, with a real chance of controlling it at the bedside.',
    minutesToUnconscious: 20,
    points: 3,
    position: 'Whatever is comfortable; elevate the part if you can.',
    firstAid: [
      'Direct pressure for a full 10 minutes by the clock — most settle.',
      'Do not peel off a dressing that has clotted into the wound; pack on top.',
      'Alginate or adrenaline-soaked gauze under pressure.',
    ],
  },
  haematuria: {
    label: 'Heavy haematuria (bladder, prostate, renal)',
    lead: 'Clot retention is the emergency more often than blood loss.',
    minutesToUnconscious: 25,
    points: 2,
    position: 'Comfortable; catheter bag visible and unkinked.',
    firstAid: [
      'Three-way catheter and bladder irrigation if clot retention develops.',
      'Watch for a bladder that stops draining and a distended, painful lower abdomen.',
      'Avoid tranexamic acid here — clots that cannot be passed make things worse.',
    ],
  },
  gynae: {
    label: 'Vaginal bleeding (cervical, uterine tumour)',
    lead: 'Can be brisk, and is often controllable with packing.',
    minutesToUnconscious: 15,
    points: 4,
    position: 'Supine with knees up, or left lateral if faint.',
    firstAid: [
      'Vaginal packing under direct pressure; record how many packs went in.',
      'Tranexamic acid works well at this site.',
      'Ask about the last pack removal before adding more.',
    ],
  },
};

/** Midazolam onset by route, in minutes, at the crisis dose. */
const ROUTE_ONSET: Record<BleedRoute, { label: string; onset: number; note: string }> = {
  iv: { label: 'IV (line already in)', onset: 2, note: 'Only if a patent line is in situ. Do not site one during the bleed.' },
  buccal: { label: 'Buccal', onset: 7, note: 'Works with clenched teeth and no needle. Blood in the mouth reduces absorption.' },
  im: { label: 'IM (deltoid or thigh)', onset: 9, note: 'The most reliable pre-positioned route in a shut-down patient.' },
  sc: { label: 'Subcut', onset: 15, note: 'Too slow for an arterial bleed once perfusion has dropped.' },
};

function cockcroftGault(age: number, weightKg: number, creatinine: number, female: boolean): number {
  if (!creatinine) return 0;
  const raw = ((140 - age) * weightKg) / (72 * creatinine);
  return Math.round(female ? raw * 0.85 : raw);
}

export function BleedCrisisModal({
  patientId,
  patientName,
  primaryCancer,
  weightKg = 54,
  onClose,
  onDispatchPlan,
}: BleedCrisisModalProps) {
  const [tab, setTab] = useState<'risk' | 'crisis_box' | 'clock' | 'stop_bleed' | 'family'>('risk');

  const [site, setSite] = useState<BleedSite>('carotid');
  const [sentinelBleed, setSentinelBleed] = useState(true);
  const [priorRadiotherapy, setPriorRadiotherapy] = useState(true);
  const [visibleVessel, setVisibleVessel] = useState(false);
  const [platelets, setPlatelets] = useState(62);
  const [inr, setInr] = useState(1.4);
  const [anticoagulant, setAnticoagulant] = useState<'none' | 'lmwh' | 'doac' | 'warfarin'>('lmwh');
  const [antiplatelet, setAntiplatelet] = useState(true);

  const [weight, setWeight] = useState(weightKg);
  const [age, setAge] = useState(62);
  const [creatinine, setCreatinine] = useState(1.3);
  const [female, setFemale] = useState(true);
  const [benzoTolerant, setBenzoTolerant] = useState(false);
  const [route, setRoute] = useState<BleedRoute>('im');

  const [packChecks, setPackChecks] = useState<Record<string, boolean>>({
    dark_towels: true,
    midazolam_drawn: false,
    route_agreed: false,
    suction: false,
    named_carer: false,
    position_taught: false,
    ambulance_decision: false,
    thinners_reviewed: false,
  });

  const [lang, setLang] = useState<'en' | 'hi'>('en');
  const [playhead, setPlayhead] = useState<number | null>(null);
  const timer = useRef<number | null>(null);

  const siteInfo = SITES[site];
  const crcl = cockcroftGault(age, weight, creatinine, female);

  // Catastrophic bleed risk, 0–20. Site anatomy dominates; a herald (sentinel)
  // bleed is the strongest single warning sign, especially in carotid blowout.
  const risk = useMemo(() => {
    let score = siteInfo.points;
    if (sentinelBleed) score += 4;
    if (priorRadiotherapy) score += 2;
    if (visibleVessel) score += 3;
    if (platelets < 20) score += 3;
    else if (platelets < 50) score += 2;
    if (inr > 2) score += 2;
    else if (inr > 1.5) score += 1;
    if (anticoagulant === 'warfarin' || anticoagulant === 'doac') score += 2;
    else if (anticoagulant === 'lmwh') score += 1;
    if (antiplatelet) score += 1;
    return Math.min(20, score);
  }, [siteInfo, sentinelBleed, priorRadiotherapy, visibleVessel, platelets, inr, anticoagulant, antiplatelet]);

  const tier = useMemo(() => {
    if (risk >= 13) {
      return {
        name: 'Crisis box at the bedside today',
        colour: '#b91c1c',
        bg: '#fef2f2',
        line: 'A catastrophic bleed is a realistic event within days. Drugs, towels and a named person belong in the room, not in a plan.',
      };
    }
    if (risk >= 8) {
      return {
        name: 'Prepare within 48 hours',
        colour: '#c2410c',
        bg: '#fff7ed',
        line: 'Stock the box, agree the route, and brief the family before the weekend or any handover.',
      };
    }
    return {
      name: 'Watch and review',
      colour: '#0369a1',
      bg: '#eff6ff',
      line: 'Manage the bleeding you can see, review blood thinners, and reassess the moment a herald bleed appears.',
    };
  }, [risk]);

  // Crisis midazolam. The intent is comfort during a death that is usually
  // already underway, not rescue. Frailty and benzodiazepine exposure set it.
  const midazolam = useMemo(() => {
    const frail = weight < 45;
    let dose = frail ? 5 : 10;
    if (benzoTolerant) dose = frail ? 10 : 15;
    if (route === 'iv') dose = benzoTolerant ? 5 : 2.5;
    return {
      dose,
      repeat:
        route === 'iv'
          ? 'Repeat 2.5 mg every 5 minutes, titrated to settling.'
          : `Repeat the same dose once after ${ROUTE_ONSET[route].onset + 5} minutes if still distressed.`,
      onset: ROUTE_ONSET[route].onset,
    };
  }, [weight, benzoTolerant, route]);

  const beatsTheBleed = midazolam.onset <= siteInfo.minutesToUnconscious;

  const txa = useMemo(() => {
    if (site === 'haematuria') {
      return {
        verdict: 'avoid' as const,
        line: 'Avoid tranexamic acid in upper-tract haematuria — clot colic and clot retention.',
        dosing: [] as string[],
      };
    }
    const renal = crcl > 0 && crcl < 30;
    const dosing = [
      site === 'haemoptysis'
        ? 'Nebulised: 500 mg three times daily — good evidence in haemoptysis, without a systemic clotting load.'
        : 'Topical: 500 mg–1 g on gauze, held against the bleeding point for 10 minutes.',
      renal
        ? `Oral: 1 g once daily (creatinine clearance ${crcl} mL/min — reduced for renal clearance).`
        : 'Oral: 1 g three times daily while bleeding, stopping after 48 hours dry.',
      'IV: 1 g over 10 minutes if access is already in and the aim is to buy time for embolisation.',
    ];
    return {
      verdict: renal ? ('caution' as const) : ('use' as const),
      line: renal
        ? `Reduced dosing — creatinine clearance ${crcl} mL/min.`
        : 'Reasonable at this site alongside pressure and dressings.',
      dosing,
    };
  }, [site, crcl]);

  const thinnerAdvice = useMemo(() => {
    if (anticoagulant === 'none' && !antiplatelet) return 'Nothing to stop. Record that this was checked.';
    if (risk >= 13) {
      return anticoagulant === 'warfarin'
        ? 'Stop warfarin. Vitamin K 5 mg orally only if this is a bleed you intend to treat; otherwise it adds nothing to comfort.'
        : 'Stop the anticoagulant and the antiplatelet now. At this risk the clotting trade-off has flipped.';
    }
    if (risk >= 8) {
      return 'Stop the antiplatelet. Reduce or stop the anticoagulant unless it is treating a clot the patient can feel.';
    }
    return 'Continue for now, but write down the bleed risk and the date of the next review.';
  }, [anticoagulant, antiplatelet, risk]);

  const escalation = useMemo(() => {
    const options: { label: string; detail: string; fit: string }[] = [];
    if (site === 'carotid') {
      options.push({
        label: 'Interventional radiology — covered stent or embolisation',
        detail: 'The only intervention that reliably prevents a second carotid bleed after a herald bleed.',
        fit: 'Worth a same-day phone call if this patient would accept transfer and a procedure.',
      });
    }
    if (site === 'haemoptysis' || site === 'fungating' || site === 'gynae') {
      options.push({
        label: 'Palliative radiotherapy to stop the bleeding',
        detail: 'A single 8 Gy fraction, or 20 Gy in 5 where survival is longer. Bleeding settles over days, not minutes.',
        fit: 'Fits someone with weeks of life who can lie still for treatment.',
      });
    }
    if (site === 'upper_gi') {
      options.push({
        label: 'Endoscopy or band ligation',
        detail: 'Only where the patient would accept sedation and the bleed is variceal or a single treatable point.',
        fit: 'Ask whether a repeat bleed would change anything before booking it.',
      });
    }
    if (platelets < 50) {
      options.push({
        label: 'Platelet transfusion',
        detail: 'One adult dose, for bleeding you can see rather than for a number on a report.',
        fit: 'Reasonable for a bleed you are trying to stop; not as a standing top-up.',
      });
    }
    options.push({
      label: 'Stay, and keep them comfortable',
      detail: 'Pre-drawn midazolam, dark towels, a named person who does not leave, and a written decision about the ambulance.',
      fit: 'The right answer when transfer or a procedure would not serve what this patient wants.',
    });
    return options;
  }, [site, platelets]);

  const packItems: { id: string; label: string; detail: string }[] = [
    {
      id: 'dark_towels',
      label: 'Dark towels or green drapes in the room',
      detail: 'Blood on dark cloth reads as water. This is the single most effective thing for the people watching.',
    },
    {
      id: 'midazolam_drawn',
      label: `Midazolam ${midazolam.dose} mg drawn up and dated`,
      detail: 'Pre-drawn and in the room. Nobody can find a key and break an ampoule in ninety seconds.',
    },
    { id: 'route_agreed', label: `Route written down: ${ROUTE_ONSET[route].label}`, detail: ROUTE_ONSET[route].note },
    { id: 'suction', label: 'Suction, or a bowl and gauze, within arm’s reach', detail: 'For the mouth and airway only.' },
    {
      id: 'named_carer',
      label: 'One named person who will stay in the room',
      detail: 'Named out loud, in front of them, with a second person to make the call for help.',
    },
    { id: 'position_taught', label: 'Position practised with the family', detail: siteInfo.position },
    {
      id: 'ambulance_decision',
      label: 'Ambulance decision written down',
      detail: 'Recorded in advance, so nobody has to decide it in the moment.',
    },
    { id: 'thinners_reviewed', label: 'Blood thinners reviewed', detail: thinnerAdvice },
  ];

  const packDone = packItems.filter((item) => packChecks[item.id]).length;
  const packReady = packDone === packItems.length;

  useEffect(() => {
    if (playhead === null) return undefined;
    timer.current = window.setTimeout(() => {
      setPlayhead((value) => (value === null || value >= 20 ? null : value + 0.25));
    }, 45);
    return () => {
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [playhead]);

  const dispatch = () => {
    const summary = `${siteInfo.label} · bleed risk ${risk}/20 (${tier.name}). Midazolam ${midazolam.dose} mg ${route.toUpperCase()}, onset about ${midazolam.onset} min. ${siteInfo.position} Crisis box ${packDone}/${packItems.length}. ${thinnerAdvice}`;
    onDispatchPlan?.(summary);
    onClose();
  };

  const tabs: { id: typeof tab; label: string }[] = [
    { id: 'risk', label: 'Who is at risk' },
    { id: 'crisis_box', label: `Crisis box (${packDone}/${packItems.length})` },
    { id: 'clock', label: 'Bleed clock' },
    { id: 'stop_bleed', label: 'Stopping the bleed' },
    { id: 'family', label: 'Telling the family' },
  ];

  const card: React.CSSProperties = {
    background: '#f8fafc',
    border: '1px solid #e2e8f0',
    borderRadius: '10px',
    padding: '14px',
  };
  const label: React.CSSProperties = {
    display: 'block',
    fontSize: '11px',
    fontWeight: 800,
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    color: '#64748b',
    marginBottom: '5px',
  };
  const input: React.CSSProperties = {
    width: '100%',
    padding: '8px 10px',
    borderRadius: '8px',
    border: '1px solid #cbd5e1',
    fontSize: '13px',
    background: '#ffffff',
    color: '#0f172a',
  };

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bleed-crisis-title"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.75)',
        backdropFilter: 'blur(6px)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        overflowY: 'auto',
      }}
    >
      <div
        className="modal-card"
        style={{
          width: '100%',
          maxWidth: '1020px',
          maxHeight: '92vh',
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid #e2e8f0',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid #e2e8f0',
            background:
              risk >= 13
                ? 'linear-gradient(135deg, #7f1d1d 0%, #991b1b 100%)'
                : 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
            color: '#ffffff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '12px',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '4px 10px',
                  borderRadius: '9999px',
                  fontSize: '11px',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                  background: tier.bg,
                  color: tier.colour,
                }}
              >
                <IconShieldAlert className="w-3.5 h-3.5" />
                {tier.name}
              </span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>
                Patient: <strong style={{ color: '#ffffff' }}>{patientName}</strong> ({patientId})
              </span>
              <span style={{ fontSize: '12px', color: '#94a3b8' }}>·</span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>{primaryCancer}</span>
            </div>
            <h1
              id="bleed-crisis-title"
              style={{ margin: '8px 0 2px 0', fontSize: '22px', fontWeight: 800, letterSpacing: '-0.02em', color: '#ffffff' }}
            >
              Catastrophic Bleeding
            </h1>
            <p style={{ margin: 0, fontSize: '13px', color: '#e2e8f0', opacity: 0.9 }}>
              Who is going to bleed, what sits at the bedside before it happens, and what the person in the room does in the first two minutes.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            style={{
              background: 'rgba(255, 255, 255, 0.15)',
              border: 'none',
              borderRadius: '8px',
              color: '#ffffff',
              padding: '6px 12px',
              fontSize: '14px',
              cursor: 'pointer',
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            ✕ Close
          </button>
        </div>

        <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', background: '#f8fafc', padding: '0 16px', overflowX: 'auto' }}>
          {tabs.map((entry) => (
            <button
              key={entry.id}
              type="button"
              onClick={() => setTab(entry.id)}
              style={{
                padding: '14px 16px',
                fontSize: '13px',
                fontWeight: tab === entry.id ? 700 : 500,
                color: tab === entry.id ? '#0f172a' : '#64748b',
                background: 'transparent',
                borderTop: 'none',
                borderLeft: 'none',
                borderRight: 'none',
                borderBottom: tab === entry.id ? '3px solid #dc2626' : '3px solid transparent',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
            >
              {entry.label}
            </button>
          ))}
        </div>

        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          {tab === 'risk' && (
            <div style={{ display: 'grid', gap: '16px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '12px' }}>
                <div style={card}>
                  <span style={label}>Bleeding site</span>
                  <select style={input} value={site} onChange={(e) => setSite(e.target.value as BleedSite)}>
                    {(Object.keys(SITES) as BleedSite[]).map((key) => (
                      <option key={key} value={key}>
                        {SITES[key].label}
                      </option>
                    ))}
                  </select>
                  <p style={{ margin: '8px 0 0 0', fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>{siteInfo.lead}</p>
                </div>
                <div style={card}>
                  <span style={label}>Platelets (×10⁹/L)</span>
                  <input style={input} type="number" value={platelets} onChange={(e) => setPlatelets(Number(e.target.value))} />
                  <span style={{ ...label, marginTop: '10px' }}>INR</span>
                  <input style={input} type="number" step="0.1" value={inr} onChange={(e) => setInr(Number(e.target.value))} />
                </div>
                <div style={card}>
                  <span style={label}>Anticoagulant</span>
                  <select
                    style={input}
                    value={anticoagulant}
                    onChange={(e) => setAnticoagulant(e.target.value as typeof anticoagulant)}
                  >
                    <option value="none">None</option>
                    <option value="lmwh">Low molecular weight heparin</option>
                    <option value="doac">DOAC (apixaban, rivaroxaban)</option>
                    <option value="warfarin">Warfarin</option>
                  </select>
                  <label style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '10px', fontSize: '13px', color: '#0f172a' }}>
                    <input type="checkbox" checked={antiplatelet} onChange={(e) => setAntiplatelet(e.target.checked)} />
                    On aspirin or clopidogrel
                  </label>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '10px' }}>
                {[
                  {
                    state: sentinelBleed,
                    set: setSentinelBleed,
                    title: 'Herald bleed already happened',
                    detail: 'A small self-limiting bleed from the same place. The strongest single warning there is.',
                  },
                  {
                    state: priorRadiotherapy,
                    set: setPriorRadiotherapy,
                    title: 'Previous radiotherapy to this area',
                    detail: 'Irradiated tissue breaks down over a vessel rather than sealing around it.',
                  },
                  {
                    state: visibleVessel,
                    set: setVisibleVessel,
                    title: 'Vessel visible in the wound, or a fistula',
                    detail: 'An exposed artery in a fungating bed, or tumour eroding into gut or airway.',
                  },
                ].map((flag) => (
                  <button
                    key={flag.title}
                    type="button"
                    onClick={() => flag.set(!flag.state)}
                    style={{
                      textAlign: 'left',
                      padding: '12px',
                      borderRadius: '10px',
                      border: `1px solid ${flag.state ? '#fca5a5' : '#e2e8f0'}`,
                      background: flag.state ? '#fef2f2' : '#ffffff',
                      cursor: 'pointer',
                    }}
                  >
                    <strong style={{ display: 'block', fontSize: '13px', color: flag.state ? '#991b1b' : '#0f172a' }}>
                      {flag.state ? '● ' : '○ '}
                      {flag.title}
                    </strong>
                    <small style={{ display: 'block', marginTop: '4px', fontSize: '12px', color: '#64748b', lineHeight: 1.45 }}>
                      {flag.detail}
                    </small>
                  </button>
                ))}
              </div>

              <div
                style={{
                  background: tier.bg,
                  border: `1px solid ${tier.colour}33`,
                  borderRadius: '12px',
                  padding: '16px',
                  display: 'flex',
                  gap: '16px',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                }}
              >
                <div style={{ minWidth: '110px' }}>
                  <div style={{ fontSize: '34px', fontWeight: 800, color: tier.colour, lineHeight: 1 }}>
                    {risk}
                    <span style={{ fontSize: '16px', color: '#64748b' }}>/20</span>
                  </div>
                  <small style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: '#64748b' }}>
                    Bleed risk
                  </small>
                </div>
                <div style={{ flex: 1, minWidth: '240px' }}>
                  <strong style={{ display: 'block', fontSize: '15px', color: tier.colour }}>{tier.name}</strong>
                  <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#334155', lineHeight: 1.55 }}>{tier.line}</p>
                </div>
              </div>

              <div style={{ ...card, background: '#fffbeb', borderColor: '#fde68a' }}>
                <strong style={{ display: 'block', fontSize: '13px', color: '#92400e', marginBottom: '4px' }}>Blood thinners</strong>
                <p style={{ margin: 0, fontSize: '13px', color: '#78350f', lineHeight: 1.55 }}>{thinnerAdvice}</p>
              </div>
            </div>
          )}

          {tab === 'crisis_box' && (
            <div style={{ display: 'grid', gap: '16px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
                <div style={card}>
                  <span style={label}>Weight (kg)</span>
                  <input style={input} type="number" value={weight} onChange={(e) => setWeight(Number(e.target.value))} />
                </div>
                <div style={card}>
                  <span style={label}>Route</span>
                  <select style={input} value={route} onChange={(e) => setRoute(e.target.value as BleedRoute)}>
                    {(Object.keys(ROUTE_ONSET) as BleedRoute[]).map((key) => (
                      <option key={key} value={key}>
                        {ROUTE_ONSET[key].label}
                      </option>
                    ))}
                  </select>
                </div>
                <div style={card}>
                  <span style={label}>Benzodiazepine exposure</span>
                  <label style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '13px', color: '#0f172a' }}>
                    <input type="checkbox" checked={benzoTolerant} onChange={(e) => setBenzoTolerant(e.target.checked)} />
                    Already on regular midazolam or clonazepam
                  </label>
                </div>
              </div>

              <div style={{ background: '#0f172a', borderRadius: '12px', padding: '18px', color: '#ffffff' }}>
                <div style={{ display: 'flex', gap: '18px', flexWrap: 'wrap', alignItems: 'baseline' }}>
                  <div>
                    <div style={{ fontSize: '30px', fontWeight: 800, lineHeight: 1 }}>Midazolam {midazolam.dose} mg</div>
                    <small style={{ fontSize: '12px', color: '#cbd5e1' }}>
                      {ROUTE_ONSET[route].label} · onset about {midazolam.onset} minutes
                    </small>
                  </div>
                  <span
                    style={{
                      padding: '5px 11px',
                      borderRadius: '9999px',
                      fontSize: '11px',
                      fontWeight: 800,
                      textTransform: 'uppercase',
                      background: beatsTheBleed ? '#064e3b' : '#7f1d1d',
                      color: beatsTheBleed ? '#a7f3d0' : '#fecaca',
                    }}
                  >
                    {beatsTheBleed ? 'Onset within the bleed window' : 'Slower than the bleed'}
                  </span>
                </div>
                <p style={{ margin: '10px 0 0 0', fontSize: '13px', color: '#e2e8f0', lineHeight: 1.55 }}>{midazolam.repeat}</p>
                <p style={{ margin: '8px 0 0 0', fontSize: '12px', color: '#94a3b8', lineHeight: 1.55 }}>
                  {beatsTheBleed
                    ? 'This dose can settle distress before consciousness is lost. It still does not stop the bleed.'
                    : `A ${siteInfo.label.split(' (')[0].toLowerCase()} takes about ${siteInfo.minutesToUnconscious} minutes to unconsciousness and this route needs about ${midazolam.onset}. Give it anyway — it covers a slower bleed, and it gives the person in the room something to do — but the work that changes this death is the towel, the position and staying.`}
                </p>
              </div>

              <div style={{ display: 'grid', gap: '8px' }}>
                {packItems.map((item) => {
                  const done = Boolean(packChecks[item.id]);
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => setPackChecks((prev) => ({ ...prev, [item.id]: !prev[item.id] }))}
                      style={{
                        textAlign: 'left',
                        display: 'flex',
                        gap: '12px',
                        alignItems: 'flex-start',
                        padding: '12px 14px',
                        borderRadius: '10px',
                        border: `1px solid ${done ? '#86efac' : '#e2e8f0'}`,
                        background: done ? '#f0fdf4' : '#ffffff',
                        cursor: 'pointer',
                      }}
                    >
                      <span style={{ fontSize: '16px', lineHeight: 1.2, color: done ? '#16a34a' : '#cbd5e1' }}>{done ? '✓' : '○'}</span>
                      <span>
                        <strong style={{ display: 'block', fontSize: '13px', color: '#0f172a' }}>{item.label}</strong>
                        <small style={{ display: 'block', marginTop: '3px', fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>
                          {item.detail}
                        </small>
                      </span>
                    </button>
                  );
                })}
              </div>

              <div
                style={{
                  ...card,
                  background: packReady ? '#f0fdf4' : '#fff7ed',
                  borderColor: packReady ? '#86efac' : '#fed7aa',
                  display: 'flex',
                  gap: '10px',
                  alignItems: 'center',
                }}
              >
                {packReady ? <IconShieldCheck className="w-5 h-5" /> : <IconClock className="w-5 h-5" />}
                <p style={{ margin: 0, fontSize: '13px', color: packReady ? '#166534' : '#9a3412', fontWeight: 650 }}>
                  {packReady
                    ? 'Box complete. Anyone walking into this room tonight can manage a bleed without a phone call.'
                    : `${packItems.length - packDone} item${packItems.length - packDone === 1 ? '' : 's'} outstanding. Until these are done the plan only exists on a screen.`}
                </p>
              </div>
            </div>
          )}

          {tab === 'clock' && (
            <div style={{ display: 'grid', gap: '14px' }}>
              <p style={{ margin: 0, fontSize: '13px', color: '#475569', lineHeight: 1.6 }}>
                Two clocks run at once in a catastrophic bleed: how long the patient stays conscious, and how long the drug takes to work. Drawing them together stops teams planning a rescue that arrives after the death.
              </p>

              <div style={{ background: '#0b1220', borderRadius: '12px', padding: '16px' }}>
                <svg viewBox="0 0 640 260" style={{ width: '100%', height: 'auto' }} role="img" aria-label="Consciousness against drug onset over twenty minutes">
                  <defs>
                    <linearGradient id="bleedFade" x1="0" y1="0" x2="1" y2="0">
                      <stop offset="0%" stopColor="#f87171" stopOpacity="0.85" />
                      <stop offset="100%" stopColor="#7f1d1d" stopOpacity="0.15" />
                    </linearGradient>
                  </defs>

                  {[0, 5, 10, 15, 20].map((minute) => (
                    <g key={minute}>
                      <line x1={60 + minute * 27} y1={30} x2={60 + minute * 27} y2={200} stroke="#1e293b" strokeWidth="1" />
                      <text x={60 + minute * 27} y={220} fill="#64748b" fontSize="11" textAnchor="middle">
                        {minute} min
                      </text>
                    </g>
                  ))}

                  <text x={12} y={44} fill="#94a3b8" fontSize="10">
                    awake
                  </text>
                  <text x={12} y={196} fill="#94a3b8" fontSize="10">
                    gone
                  </text>

                  {/* Consciousness decay for the chosen site. */}
                  <path
                    d={`M60,40 ${Array.from({ length: 41 }, (_, step) => {
                      const minute = step * 0.5;
                      const k = 3 / siteInfo.minutesToUnconscious;
                      const level = Math.exp(-k * minute);
                      return `L${(60 + minute * 27).toFixed(1)},${(40 + (1 - level) * 160).toFixed(1)}`;
                    }).join(' ')} L${60 + 20 * 27},200 L60,200 Z`}
                    fill="url(#bleedFade)"
                    stroke="#fca5a5"
                    strokeWidth="2"
                  />

                  {/* Drug onset per route; the chosen one is highlighted. */}
                  {(Object.keys(ROUTE_ONSET) as BleedRoute[]).map((key, index) => {
                    const x = 60 + ROUTE_ONSET[key].onset * 27;
                    const active = key === route;
                    return (
                      <g key={key}>
                        <line x1={x} y1={30} x2={x} y2={200} stroke={active ? '#38bdf8' : '#334155'} strokeWidth={active ? 2.5 : 1.5} strokeDasharray="4 4" />
                        <circle cx={x} cy={30} r={active ? 5 : 3.5} fill={active ? '#38bdf8' : '#475569'} />
                        <text x={x + 6} y={52 + index * 16} fill={active ? '#38bdf8' : '#64748b'} fontSize="10" fontWeight={active ? 700 : 400}>
                          {key.toUpperCase()} {ROUTE_ONSET[key].onset}′
                        </text>
                      </g>
                    );
                  })}

                  {/* Loss of consciousness for this site. */}
                  <line
                    x1={60 + siteInfo.minutesToUnconscious * 27}
                    y1={24}
                    x2={60 + siteInfo.minutesToUnconscious * 27}
                    y2={200}
                    stroke="#ef4444"
                    strokeWidth="2.5"
                  />
                  <text
                    x={60 + siteInfo.minutesToUnconscious * 27 - 6}
                    y={20}
                    fill="#ef4444"
                    fontSize="11"
                    fontWeight="700"
                    textAnchor="end"
                  >
                    unconscious ~{siteInfo.minutesToUnconscious}′
                  </text>

                  {playhead !== null && (
                    <g>
                      <line x1={60 + playhead * 27} y1={24} x2={60 + playhead * 27} y2={206} stroke="#fde047" strokeWidth="2" />
                      <text x={60 + playhead * 27 + 5} y={238} fill="#fde047" fontSize="11" fontWeight="700">
                        {playhead.toFixed(1)} min
                      </text>
                    </g>
                  )}
                </svg>

                <div style={{ display: 'flex', gap: '10px', marginTop: '10px', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    onClick={() => setPlayhead(playhead === null ? 0 : null)}
                    style={{
                      padding: '8px 14px',
                      borderRadius: '8px',
                      border: 'none',
                      background: '#1d4ed8',
                      color: '#ffffff',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    {playhead === null ? '▶ Run the two minutes' : '■ Stop'}
                  </button>
                  <span style={{ fontSize: '12px', color: '#94a3b8', alignSelf: 'center' }}>
                    {playhead === null
                      ? 'Watch where the drug marker falls against the red line.'
                      : playhead < midazolam.onset
                        ? 'Drug not working yet — this is the stretch that pressure, position and presence have to cover.'
                        : 'Drug on board.'}
                  </span>
                </div>
              </div>

              <div style={{ ...card, background: '#eff6ff', borderColor: '#bfdbfe' }}>
                <strong style={{ display: 'block', fontSize: '13px', color: '#1e40af', marginBottom: '6px' }}>The first two minutes</strong>
                <ol style={{ margin: 0, paddingLeft: '18px', display: 'grid', gap: '6px' }}>
                  {siteInfo.firstAid.map((step) => (
                    <li key={step} style={{ fontSize: '13px', color: '#1e3a5f', lineHeight: 1.55 }}>
                      {step}
                    </li>
                  ))}
                  <li style={{ fontSize: '13px', color: '#1e3a5f', lineHeight: 1.55 }}>
                    Give the pre-drawn midazolam {midazolam.dose} mg {route.toUpperCase()}, then go straight back to pressure and position.
                  </li>
                  <li style={{ fontSize: '13px', color: '#1e3a5f', lineHeight: 1.55 }}>{siteInfo.position}</li>
                </ol>
              </div>
            </div>
          )}

          {tab === 'stop_bleed' && (
            <div style={{ display: 'grid', gap: '14px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px' }}>
                <div style={card}>
                  <span style={label}>Age</span>
                  <input style={input} type="number" value={age} onChange={(e) => setAge(Number(e.target.value))} />
                </div>
                <div style={card}>
                  <span style={label}>Creatinine (mg/dL)</span>
                  <input style={input} type="number" step="0.1" value={creatinine} onChange={(e) => setCreatinine(Number(e.target.value))} />
                </div>
                <div style={card}>
                  <span style={label}>Sex</span>
                  <select style={input} value={female ? 'f' : 'm'} onChange={(e) => setFemale(e.target.value === 'f')}>
                    <option value="f">Female</option>
                    <option value="m">Male</option>
                  </select>
                </div>
                <div style={{ ...card, background: '#ecfeff', borderColor: '#a5f3fc' }}>
                  <span style={label}>Creatinine clearance</span>
                  <strong style={{ fontSize: '22px', color: '#0e7490' }}>
                    {crcl || '—'}
                    <span style={{ fontSize: '12px', color: '#64748b' }}> mL/min</span>
                  </strong>
                  <small style={{ display: 'block', fontSize: '11px', color: '#64748b' }}>Cockcroft-Gault</small>
                </div>
              </div>

              <div
                style={{
                  ...card,
                  background: txa.verdict === 'avoid' ? '#fef2f2' : txa.verdict === 'caution' ? '#fff7ed' : '#f0fdf4',
                  borderColor: txa.verdict === 'avoid' ? '#fecaca' : txa.verdict === 'caution' ? '#fed7aa' : '#bbf7d0',
                }}
              >
                <strong
                  style={{
                    display: 'block',
                    fontSize: '14px',
                    color: txa.verdict === 'avoid' ? '#b91c1c' : txa.verdict === 'caution' ? '#c2410c' : '#166534',
                  }}
                >
                  Tranexamic acid — {txa.verdict === 'avoid' ? 'do not use at this site' : txa.verdict === 'caution' ? 'reduced dose' : 'use it'}
                </strong>
                <p style={{ margin: '6px 0 0 0', fontSize: '13px', color: '#334155', lineHeight: 1.55 }}>{txa.line}</p>
                {txa.dosing.length > 0 && (
                  <ul style={{ margin: '8px 0 0 0', paddingLeft: '18px', display: 'grid', gap: '5px' }}>
                    {txa.dosing.map((line) => (
                      <li key={line} style={{ fontSize: '13px', color: '#334155', lineHeight: 1.5 }}>
                        {line}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div style={{ display: 'grid', gap: '10px' }}>
                {escalation.map((option) => (
                  <div key={option.label} style={{ ...card, background: '#ffffff' }}>
                    <strong style={{ display: 'block', fontSize: '13px', color: '#0f172a' }}>{option.label}</strong>
                    <p style={{ margin: '5px 0 0 0', fontSize: '13px', color: '#475569', lineHeight: 1.55 }}>{option.detail}</p>
                    <small style={{ display: 'block', marginTop: '6px', fontSize: '12px', color: '#0e7490', fontWeight: 650 }}>{option.fit}</small>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'family' && (
            <div style={{ display: 'grid', gap: '14px' }}>
              <div style={{ display: 'flex', gap: '8px' }}>
                {(['en', 'hi'] as const).map((code) => (
                  <button
                    key={code}
                    type="button"
                    onClick={() => setLang(code)}
                    style={{
                      padding: '7px 14px',
                      borderRadius: '8px',
                      border: `1px solid ${lang === code ? '#0f172a' : '#cbd5e1'}`,
                      background: lang === code ? '#0f172a' : '#ffffff',
                      color: lang === code ? '#ffffff' : '#475569',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    {code === 'en' ? 'English' : 'हिन्दी'}
                  </button>
                ))}
              </div>

              <div style={{ ...card, background: '#ffffff' }}>
                <strong style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: '#0f172a', marginBottom: '8px' }}>
                  <IconUsers className="w-4 h-4" /> Say it before it happens, not during
                </strong>
                {(lang === 'en'
                  ? [
                      `Because of where ${patientName.split(' ')[0]}’s tumour sits, there is a chance of a sudden heavy bleed. I would rather tell you now than have you meet it without warning.`,
                      'If it happens it will look frightening and it will be quick. They are very unlikely to be aware of much of it.',
                      `Your job is three things: press hard on the place it is coming from with one of the dark towels, ${siteInfo.position.toLowerCase()} and stay with them. Someone else makes the call.`,
                      `There is midazolam ${midazolam.dose} mg drawn up in the box. Give it ${ROUTE_ONSET[route].label.toLowerCase()} as we practised, then go straight back to pressure.`,
                      'The dark towels are there so it looks like water rather than blood. It helps more than people expect.',
                      'You do not have to call an ambulance. If that is what we agreed, staying here is not a failure to act.',
                    ]
                  : [
                      `${patientName.split(' ')[0]} की गाँठ जहाँ है, उसकी वजह से अचानक तेज़ खून बहने की सम्भावना है। मैं यह आपको अभी बता देना चाहता/चाहती हूँ।`,
                      'अगर ऐसा हुआ तो देखने में डरावना होगा और जल्दी होगा। उन्हें इसका ज़्यादा पता नहीं चलेगा।',
                      'आपको तीन काम करने हैं: गहरे रंग के तौलिए से उस जगह पर ज़ोर से दबाना, उन्हें बताई गई स्थिति में रखना, और उनके पास रुकना। बुलाने का काम कोई दूसरा करेगा।',
                      `डिब्बे में मिडाज़ोलम ${midazolam.dose} mg पहले से भरा रखा है। जैसा अभ्यास किया है वैसे दीजिए, फिर वापस दबाव पर लौटिए।`,
                      'गहरे तौलिए इसलिए हैं कि खून पानी जैसा दिखे। इससे देखने वालों को बहुत फ़र्क़ पड़ता है।',
                      'एम्बुलेंस बुलाना ज़रूरी नहीं है। अगर हमने यही तय किया है, तो घर पर रुकना कोई चूक नहीं है।',
                    ]
                ).map((line, index) => (
                  <p
                    key={index}
                    style={{
                      margin: '0 0 10px 0',
                      fontSize: '13px',
                      color: '#334155',
                      lineHeight: 1.65,
                      paddingLeft: '12px',
                      borderLeft: '3px solid #e2e8f0',
                    }}
                  >
                    {line}
                  </p>
                ))}
              </div>

              <div style={{ ...card, background: '#f5f3ff', borderColor: '#ddd6fe' }}>
                <strong style={{ display: 'block', fontSize: '13px', color: '#5b21b6', marginBottom: '6px' }}>Afterwards</strong>
                <ul style={{ margin: 0, paddingLeft: '18px', display: 'grid', gap: '5px' }}>
                  <li style={{ fontSize: '13px', color: '#4c1d95', lineHeight: 1.55 }}>
                    Ring the family back the next day. A bleed death is a common trigger for a difficult bereavement.
                  </li>
                  <li style={{ fontSize: '13px', color: '#4c1d95', lineHeight: 1.55 }}>
                    Debrief whoever was in the room, including the nurse and the driver.
                  </li>
                  <li style={{ fontSize: '13px', color: '#4c1d95', lineHeight: 1.55 }}>
                    Tell them plainly that pressing and staying was the right thing, and that nothing they did caused it.
                  </li>
                </ul>
              </div>
            </div>
          )}
        </div>

        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '12px',
            flexWrap: 'wrap',
          }}
        >
          <small style={{ fontSize: '12px', color: '#64748b', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            {risk >= 13 ? <IconHeartPulse className="w-4 h-4" /> : <IconCheckCircle className="w-4 h-4" />}
            Risk {risk}/20 · midazolam {midazolam.dose} mg {route.toUpperCase()} · box {packDone}/{packItems.length}
          </small>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '9px 16px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                color: '#475569',
                fontSize: '13px',
                fontWeight: 650,
                cursor: 'pointer',
              }}
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={dispatch}
              style={{
                padding: '9px 18px',
                borderRadius: '8px',
                border: 'none',
                background: '#b91c1c',
                color: '#ffffff',
                fontSize: '13px',
                fontWeight: 750,
                cursor: 'pointer',
              }}
            >
              Put this plan in the record
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
