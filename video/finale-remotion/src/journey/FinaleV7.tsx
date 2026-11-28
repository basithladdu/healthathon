/* v5 final: the goals-of-care title beat, the live transcript, the ER loop ending with the real page, the caregiver beat. */
import React from 'react';
import {AbsoluteFill, Freeze, Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Rise, clamp, easeOutCubic, pop, ramp} from './kit';
import {Glyph, SP} from './FinaleScenes';
import {Journey} from './Journey';

const At: React.FC<{x: number; y: number; w: number; align?: 'left' | 'center' | 'right'; children: React.ReactNode}> = ({x, y, w, align = 'left', children}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, top: y, width: w}}>{children}</div>
);
const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={0.05} dur={0.7} />
);
const Dot: React.FC<{k: string; d: number; bg: string}> = ({k, d, bg}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)', flexShrink: 0}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);
const Bg: React.FC<{t: number; children: React.ReactNode}> = ({t, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.4) * 40} y={180} r={340} color="rgba(159,209,184,0.40)" blur={90} />
    <Blob x={1660 + Math.cos(t * 0.3) * 40} y={880} r={380} color="rgba(224,160,64,0.22)" blur={100} />
    <div style={{position: 'absolute', inset: 0}}>{children}</div>
  </div>
);
const Phone: React.FC<{h: number; src: string; style?: React.CSSProperties}> = ({h, src, style}) => (
  <div style={{width: h * 0.47, height: h, borderRadius: h * 0.07, background: '#1B1A1F', padding: h * 0.018, boxSizing: 'border-box', boxShadow: '0 40px 80px rgba(10,20,30,0.30)', ...style}}>
    <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream}}>
      <Img src={staticFile(src)} style={{width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top'}} />
    </div>
  </div>
);

/* ---------- "The goals-of-care talk": a beat that holds ---------- */
export const GocTitle: React.FC<SP> = ({t}) => (
  <div style={{position: 'absolute', inset: 0, background: `linear-gradient(150deg, ${P.sage}, ${P.sageDark})`}}>
    <Blob x={1500} y={300} r={420} color="rgba(255,255,255,0.08)" blur={80} />
    <At x={960} y={380} w={1700} align="center"><R t={t} t0={0.2} text="The goals-of-care talk" size={120} color={P.white} weight={800} align="center" /></At>
    <At x={960} y={560} w={1500} align="center"><R t={t} t0={0.9} text="the doctor, the patient and the caregiver decide together" size={48} color="rgba(255,255,255,0.85)" weight={600} align="center" /></At>
  </div>
);

/* ---------- the recorded talk, written out live ---------- */
export const TR_LINES: [string, string, string, number][] = [
  ['Doctor', P.indigo, 'What matters most to you now?', 0.6],
  ['Patient', P.rose, 'I want to be at home, without pain.', 2.9],
  ['Caregiver', P.marigoldDeep, 'We will follow what she wants.', 5.2],
];
export const Transcript: React.FC<SP> = ({t}) => {
  let cur = 0;
  TR_LINES.forEach((l, i) => {
    if (t >= l[3]) cur = i;
  });
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 120, top: 60, display: 'flex', alignItems: 'center', gap: 16, padding: '12px 26px', borderRadius: 40, background: P.ink}}>
        <div style={{width: 22, height: 22, borderRadius: '50%', background: '#E5534B', opacity: 0.6 + 0.4 * Math.abs(Math.sin(t * 3))}} />
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.white, letterSpacing: 2}}>REC · with everyone's consent</div>
      </div>
      <At x={120} y={150} w={1600}><R t={t} t0={0.1} text="Sahara writes out who said what" size={64} color={P.sageDark} weight={800} /></At>
      {TR_LINES.map(([who, c, line, t0], i) => {
        const shown = Math.floor(line.length * clamp((t - t0) / 1.6));
        const on = i === cur;
        return (
          <div key={i} style={{position: 'absolute', left: 120, top: 300 + i * 200, width: 1680, opacity: t > t0 - 0.2 ? 1 : 0}}>
            <div style={{display: 'flex', alignItems: 'center', gap: 26}}>
              <div style={{width: 210, padding: '12px 0', textAlign: 'center', borderRadius: 40, background: c, color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 34}}>{who}</div>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 52, color: P.ink, whiteSpace: 'nowrap'}}>
                “{line.slice(0, shown)}{shown < line.length ? '' : '”'}
                {on && shown < line.length && <span style={{opacity: Math.sin(t * 12) > 0 ? 1 : 0}}>|</span>}
              </div>
            </div>
            {/* waveform under the current speaker */}
            <div style={{marginLeft: 236, marginTop: 14, height: 44, display: 'flex', alignItems: 'center', gap: 5, opacity: on ? 1 : 0.15}}>
              {Array.from({length: 48}).map((_, k) => (
                <div key={k} style={{width: 7, borderRadius: 4, background: c, height: on ? 8 + 34 * Math.abs(Math.sin(t * 6 + k * 0.7)) * Math.abs(Math.sin(k * 0.37 + t)) : 8}} />
              ))}
            </div>
          </div>
        );
      })}
      <div style={{position: 'absolute', right: 60, bottom: 30, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.5)'}}>Practice data. All names are fictional.</div>
    </Bg>
  );
};

/* ---------- the loop ending: the scan opens the real page ---------- */
export const ErInfo: React.FC<SP> = ({t}) => {
  const page = easeOutCubic(ramp(t, 0.3, 1.1));
  const rows: [string, string, string, string][] = [
    ['heart', P.rose, 'What she wanted', 'Comfort care at home'],
    ['people', P.indigo, 'Contacts', 'Priya (daughter) · Dr Anjali Sharma, palliative care'],
    ['pill', P.marigoldDeep, 'Medicines', 'Ondansetron 4 mg · Dexamethasone 4 mg'],
  ];
  return (
    <AbsoluteFill>
      <Freeze frame={6740}>
        <Journey audio={false} captions={false} />
      </Freeze>
      <div style={{position: 'absolute', inset: 0, background: `rgba(8,14,24,${0.5 * page})`}} />
      <div style={{position: 'absolute', left: 110, top: 60 - (1 - page) * 900, width: 1700, display: 'flex', gap: 36, alignItems: 'flex-start'}}>
        <div style={{width: 720, padding: 14, background: P.white, borderRadius: 18, boxShadow: '0 30px 70px rgba(0,0,0,0.45)'}}>
          <Img src={staticFile('real/plan_left.png')} style={{width: '100%', display: 'block'}} />
        </div>
        <div style={{flex: 1, borderRadius: 26, background: P.white, boxShadow: '0 30px 70px rgba(0,0,0,0.45)', overflow: 'hidden'}}>
          <div style={{background: '#B5442B', color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, padding: '18px 28px'}}>Opened by the scan · signed plan</div>
          {rows.map(([k, c, h, v], i) => (
            <div key={i} style={{display: 'flex', alignItems: 'center', gap: 22, padding: '22px 28px', borderBottom: i < 2 ? `2px solid ${P.well}` : undefined, opacity: ramp(t, 1.0 + i * 0.6, 1.5 + i * 0.6)}}>
              <Dot k={k} d={70} bg={c} />
              <div>
                <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, color: P.inkSub}}>{h}</div>
                <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: i === 0 ? 44 : 28, color: P.ink, lineHeight: 1.2}}>{v}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div style={{position: 'absolute', right: 60, bottom: 24, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,255,255,0.6)'}}>Practice data. All names are fictional.</div>
    </AbsoluteFill>
  );
};

/* ---------- the caregiver: her own login, her own wellbeing check, someone to call ---------- */
export const Carer: React.FC<SP> = ({t}) => {
  const items: [string, string, string, number][] = [
    ['stills/f14-family-today.png', 'Her own login', P.sage, 0.4],
    ['stills/f14-carer-burden-ui.png', 'Her own wellbeing check', P.rose, 2.2],
    ['stills/f14-carer-burden.png', 'Someone to call, and support', P.indigo, 4.0],
  ];
  return (
    <Bg t={t}>
      <At x={960} y={40} w={1800} align="center"><R t={t} t0={0.05} text="The caregiver is cared for too" size={78} color={P.sageDark} weight={800} align="center" /></At>
      {items.map(([src, label, c, t0], i) => {
        const a = pop(t, t0, 9, 0.62);
        const x = 330 + i * 630;
        return (
          <React.Fragment key={i}>
            <div style={{position: 'absolute', left: x - 140, top: 170, transform: `translateY(${(1 - Math.min(1, a)) * 500}px) rotate(${(i - 1) * 2}deg)`, opacity: clamp(a * 2)}}><Phone h={600} src={src} /></div>
            <At x={x} y={810} w={580} align="center"><R t={t} t0={t0 + 0.3} text={label} size={44} color={c} weight={800} align="center" /></At>
          </React.Fragment>
        );
      })}
      <div style={{position: 'absolute', right: 60, bottom: 30, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.5)'}}>Practice data. All names are fictional.</div>
    </Bg>
  );
};
