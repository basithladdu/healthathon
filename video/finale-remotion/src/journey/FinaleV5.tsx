/* v4 scenes: the evidence as four different pictures, the team coming together, the surprise question, the real forms,
   the visit calendar, the real QR and what it opens, RAG over documents, Google Play, and "We filled the gaps". */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Person, Rise, clamp, easeInOutCubic, easeOutCubic, lerp, pop, ramp} from './kit';
import {CAST} from './cast';
import {Glyph, SP} from './FinaleScenes';
import {Face, PlayBadge, Who} from './team';

const At: React.FC<{x: number; y: number; w: number; align?: 'left' | 'center' | 'right'; children: React.ReactNode}> = ({x, y, w, align = 'left', children}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, top: y, width: w}}>{children}</div>
);
const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'; stagger?: number}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={p.stagger ?? 0.05} dur={0.7} />
);
const PopAt: React.FC<{t: number; t0: number; x: number; y: number; children: React.ReactNode}> = ({t, t0, x, y, children}) => {
  const s = pop(t, t0, 9, 0.62);
  if (s <= 0.001) return null;
  return <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s})`}}>{children}</div>;
};
const Dot: React.FC<{k: string; d: number; bg: string}> = ({k, d, bg}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)', flexShrink: 0}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);
const Bg: React.FC<{t: number; dark?: boolean; children: React.ReactNode}> = ({t, dark, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: dark ? `linear-gradient(160deg, ${P.night1}, ${P.night0})` : `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.4) * 40} y={180} r={340} color={dark ? 'rgba(95,227,192,0.10)' : 'rgba(159,209,184,0.40)'} blur={90} />
    <Blob x={1660 + Math.cos(t * 0.3) * 40} y={880} r={380} color={dark ? 'rgba(242,179,94,0.10)' : 'rgba(224,160,64,0.22)'} blur={100} />
    <div style={{position: 'absolute', inset: 0, transform: `scale(${1.02 - 0.02 * easeOutCubic(ramp(t, 0, 0.8))})`}}>{children}</div>
  </div>
);
const Phone: React.FC<{h: number; children: React.ReactNode; style?: React.CSSProperties}> = ({h, children, style}) => (
  <div style={{width: h * 0.47, height: h, borderRadius: h * 0.07, background: '#1B1A1F', padding: h * 0.018, boxSizing: 'border-box', boxShadow: '0 40px 80px rgba(10,20,30,0.30)', ...style}}>
    <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream, position: 'relative'}}>{children}</div>
  </div>
);
const StillImg: React.FC<{src: string}> = ({src}) => <Img src={staticFile(src)} style={{width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top'}} />;
const Fictional: React.FC<{dark?: boolean}> = ({dark}) => (
  <div style={{position: 'absolute', right: 60, bottom: 30, fontFamily: FONT_BODY, fontSize: 20, color: dark ? 'rgba(255,255,255,0.5)' : 'rgba(56,46,41,0.5)'}}>Practice data. All names are fictional.</div>
);
/** a counter that eases up to n */
const count = (t: number, t0: number, n: number, d = 1.2) => Math.round(n * easeOutCubic(ramp(t, t0, t0 + d)));

/* ---------- 1. the evidence: four numbers, four pictures, one flowing sequence ---------- */
export const ST_B = [0.3, 4.6, 8.9, 99];
const Person2: React.FC<{c: string; s?: number; arm?: number}> = ({c, s = 1, arm = 0}) => (
  <svg width={40 * s} height={70 * s} viewBox="0 0 40 70" style={{display: 'block', overflow: 'visible'}}>
    <circle cx={20} cy={12} r={10} fill={c} />
    <path d="M6 66 V36 A14 14 0 0 1 34 36 V66 Z" fill={c} />
    {arm > 0 && <path d={`M30 36 L${30 + 8 * arm} ${36 - 30 * arm}`} stroke={c} strokeWidth={7} strokeLinecap="round" />}
  </svg>
);
export const Stats4: React.FC<SP> = ({t}) => {
  let i = 0;
  for (let k = 0; k < 3; k++) if (t >= ST_B[k] - 0.4) i = k;
  const b = ST_B[i];
  const fadeIn = easeInOutCubic(ramp(t, b - 0.4, b + 0.2));
  const fadeOut = i < 2 ? easeInOutCubic(ramp(t, ST_B[i + 1] - 0.6, ST_B[i + 1] - 0.2)) : 0;
  const vis = fadeIn * (1 - fadeOut);
  const shift = (1 - fadeIn) * 40 - fadeOut * 40;
  const NUM: React.CSSProperties = {fontFamily: FONT_HEAD, fontWeight: 800, lineHeight: 1, whiteSpace: 'nowrap'};
  let body: React.ReactNode = null;
  if (i === 0) {
    // 100 people, about a third light up
    const lit = count(t, b + 0.6, 37, 1.6);
    body = (
      <>
        <At x={960} y={70} w={1600} align="center"><R t={t} t0={b} text="ONLY" size={60} color={P.white} weight={800} align="center" /></At>
        <div style={{...NUM, position: 'absolute', left: 0, right: 0, top: 140, textAlign: 'center', fontSize: 190, color: P.lampWarm}}>{lit < 30 ? `${lit}%` : `30–${lit}%`}</div>
        <div style={{position: 'absolute', left: 360, top: 350, width: 1200, display: 'grid', gridTemplateColumns: 'repeat(20, 60px)', rowGap: 4}}>
          {Array.from({length: 100}).map((_, k) => (
            <div key={k} style={{opacity: 0.25 + 0.75 * clamp((t - b - 0.1 - k * 0.006) * 4)}}><Person2 c={k < lit ? P.lampWarm : 'rgba(255,255,255,0.22)'} s={0.75} /></div>
          ))}
        </div>
        <At x={960} y={820} w={1500} align="center"><R t={t} t0={b + 1.4} text="get timely palliative care" size={64} color={P.white} weight={700} align="center" /></At>
      </>
    );
  } else if (i === 1) {
    // a stack of patient files, 41% carry a written plan
    const n = count(t, b + 0.5, 41, 1.6);
    const files = 20;
    body = (
      <>
        <div style={{...NUM, position: 'absolute', left: 160, top: 200, fontSize: 230, color: '#F0A8BF'}}>{n}%</div>
        <At x={170} y={460} w={640}><R t={t} t0={b + 1.2} text="have their end-of-life wishes written down" size={56} color={P.white} weight={700} /></At>
        {Array.from({length: files}).map((_, k) => {
          const col = k % 5;
          const row = Math.floor(k / 5);
          const has = k < Math.round((n / 100) * files);
          const a = ramp(t, b + 0.1 + k * 0.04, b + 0.5 + k * 0.04);
          return (
            <div key={k} style={{position: 'absolute', left: 930 + col * 170, top: 180 + row * 190 + (1 - a) * 30, opacity: a, width: 130, height: 160}}>
              <div style={{position: 'absolute', left: 0, top: 14, width: 130, height: 146, borderRadius: 12, background: '#D9C7A8'}} />
              <div style={{position: 'absolute', left: 0, top: 0, width: 56, height: 24, borderRadius: '10px 10px 0 0', background: '#D9C7A8'}} />
              {has && <div style={{position: 'absolute', left: 18, top: 34, width: 94, height: 112, borderRadius: 6, background: P.white, transform: `translateY(${(1 - easeOutCubic(ramp(t, b + 0.6 + k * 0.07, b + 1.0 + k * 0.07))) * 30}px)`}}>
                {[0, 1, 2].map((l) => <div key={l} style={{margin: '16px 12px 0', height: 8, borderRadius: 4, background: l === 0 ? '#F0A8BF' : P.well}} />)}
              </div>}
            </div>
          );
        })}
      </>
    );
  } else if (i === 2) {
    // HOWEVER: a crowd, 88% raise a hand
    const n = count(t, b + 0.8, 88, 1.6);
    const people = 25;
    body = (
      <>
        <At x={960} y={60} w={1600} align="center"><R t={t} t0={b} text="HOWEVER" size={70} color={P.monitor} weight={800} align="center" /></At>
        <div style={{...NUM, position: 'absolute', left: 0, right: 0, top: 150, textAlign: 'center', fontSize: 210, color: P.monitor}}>{n}%</div>
        <At x={960} y={380} w={1600} align="center"><R t={t} t0={b + 1.3} text="want to choose their own end-of-life care" size={64} color={P.white} weight={700} align="center" /></At>
        <div style={{position: 'absolute', left: 230, top: 600, width: 1460, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end'}}>
          {Array.from({length: people}).map((_, k) => {
            const raise = k % 8 !== 3 ? easeOutCubic(ramp(t, b + 0.8 + ((k * 7) % people) * 0.05, b + 1.3 + ((k * 7) % people) * 0.05)) : 0;
            return <div key={k} style={{transform: `translateY(${Math.sin(t * 2 + k) * 3 - raise * 6}px)`}}><Person2 c={raise > 0.5 ? P.monitor : 'rgba(255,255,255,0.3)'} s={1.2 + (k % 3) * 0.12} arm={raise} /></div>;
          })}
        </div>
      </>
    );
  } else {
    // 63%: the 15 minutes run out while the plan is not found
    const n = count(t, b + 0.6, 63, 1.4);
    const sweep = easeInOutCubic(ramp(t, b + 0.2, b + 2.4));
    return (
      <Bg t={t} dark>
        <div style={{position: 'absolute', inset: 0, opacity: vis, transform: `translateY(${shift}px)`}}>
          <svg width={460} height={460} style={{position: 'absolute', left: 230, top: 200, overflow: 'visible'}}>
            <circle cx={230} cy={230} r={200} fill="rgba(255,255,255,0.06)" stroke="rgba(255,255,255,0.25)" strokeWidth={8} />
            <path d={`M230 230 L230 30 A200 200 0 ${sweep > 0.5 ? 1 : 0} 1 ${230 + 200 * Math.sin(sweep * 2 * Math.PI)} ${230 - 200 * Math.cos(sweep * 2 * Math.PI)} Z`} fill="rgba(232,130,106,0.45)" />
            {Array.from({length: 15}).map((_, k) => <rect key={k} x={226} y={36} width={8} height={k % 5 ? 16 : 30} rx={4} fill="rgba(255,255,255,0.5)" transform={`rotate(${k * 24} 230 230)`} />)}
            <rect x={225} y={60} width={10} height={170} rx={5} fill={P.white} transform={`rotate(${sweep * 360} 230 230)`} />
            <circle cx={230} cy={230} r={16} fill={P.white} />
            <text x={230} y={500} textAnchor="middle" fontFamily={FONT_HEAD} fontWeight={800} fontSize={44} fill="rgba(255,255,255,0.8)">15 minutes</text>
          </svg>
          <div style={{...NUM, position: 'absolute', left: 860, top: 210, fontSize: 230, color: P.coral}}>{n}%</div>
          <At x={870} y={470} w={880}><R t={t} t0={b + 1.2} text="of end-stage patients: wishes not found within 15 minutes of reaching the ER" size={52} color={P.white} weight={700} /></At>
          <div style={{position: 'absolute', left: 872, top: 720, fontFamily: FONT_BODY, fontSize: 28, color: 'rgba(255,255,255,0.6)', opacity: ramp(t, b + 2.0, b + 2.5)}}>Russell 2021</div>
        </div>
      </Bg>
    );
  }
  return (
    <Bg t={t} dark>
      <div style={{position: 'absolute', inset: 0, opacity: vis, transform: `translateY(${shift}px)`}}>{body}</div>
    </Bg>
  );
};

/* ---------- 2. the team: two groups, then together ---------- */
export const Team2: React.FC<SP> = ({t}) => {
  const docs: [Who, string, string][] = [['sujay', 'Dr Sujay', 'Pulmonologist'], ['sharada', 'Dr Sharada', 'Pulmonologist'], ['shirin', 'Shirin', 'Medical student']];
  const engs: [Who, string, string][] = [['basith', 'Basith', 'Engineer'], ['awaiz', 'Awaiz', 'Engineer']];
  const join = easeInOutCubic(ramp(t, 4.0, 5.6));
  const logo = pop(t, 5.4, 8, 0.6);
  const face = (w: Who, n: string, r: string, x: number, y: number, c: string, i: number) => (
    <React.Fragment key={w}>
      <Face who={w} x={x} y={y + Math.sin(t * 1.2 + i) * 4} d={190} s={pop(t, 0.5 + i * 0.25, 9, 0.6)} ring={c} />
      <div style={{position: 'absolute', left: x - 140, width: 280, top: y + 108, textAlign: 'center', opacity: clamp((t - 0.8 - i * 0.25) * 2)}}>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 40, color: P.ink}}>{n}</div>
        <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 26, color: c}}>{r}</div>
      </div>
    </React.Fragment>
  );
  const lx = lerp(0, 90, join);
  const rx = lerp(0, -90, join);
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 60 + lx, top: 120, width: 740, height: 560, borderRadius: 50, background: 'rgba(59,117,98,0.10)'}} />
      <div style={{position: 'absolute', left: 1120 + rx, top: 120, width: 640, height: 560, borderRadius: 50, background: 'rgba(224,160,64,0.14)'}} />
      <div style={{position: 'absolute', left: 60 + lx, top: 150, width: 740, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 50, color: P.sageDark, opacity: ramp(t, 0.2, 0.7)}}>Clinicians</div>
      <div style={{position: 'absolute', left: 1120 + rx, top: 150, width: 640, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 50, color: P.marigoldDeep, opacity: ramp(t, 1.2, 1.7)}}>Technologists · WeDevIT</div>
      {docs.map(([w, n, r], i) => face(w, n, r, 190 + i * 240 + lx, 360, P.sage, i))}
      {engs.map(([w, n, r], i) => face(w, n, r, 1290 + i * 300 + rx, 360, P.marigoldDeep, i + 3))}
      {logo > 0.01 && (
        <div style={{position: 'absolute', left: 960 - 90, top: 270, width: 180, height: 180, borderRadius: 48, background: P.white, boxShadow: '0 24px 60px rgba(30,40,50,0.18)', transform: `scale(${logo})`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
          <Img src={staticFile('brand/sahara-logo.png')} style={{width: 150, height: 150, objectFit: 'contain'}} />
        </div>
      )}
      <At x={960} y={760} w={1800} align="center"><R t={t} t0={5.8} text="Team Zeros and Ones came together to build Sahara" size={70} color={P.sageDark} weight={800} align="center" /></At>
      <div style={{position: 'absolute', left: 0, right: 0, top: 24, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 64, color: P.ink, opacity: ramp(t, 0.0, 0.5)}}>Zeros and Ones</div>
    </Bg>
  );
};

/* ---------- 3. the surprise question: a doctor-only prompt ---------- */
export const Surprise: React.FC<SP> = ({t}) => {
  const ph = pop(t, 0.3, 9, 0.62);
  const tapNo = 3.6;
  const pressed = t > tapNo && t < tapNo + 0.25;
  const next = easeOutCubic(ramp(t, tapNo + 0.4, tapNo + 0.9));
  const pulse = 1 + 0.04 * Math.sin((t - tapNo) * 5) * (t > tapNo + 1 ? 1 : 0);
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 0, top: 0, opacity: clamp(ph * 2)}}>
        <Person {...(CAST.teamDoctor as any)} t={t} x={330} y={960} s={1.15} expr="think" look={0.6} />
      </div>
      <div style={{position: 'absolute', left: 560, top: 110, transform: `translateY(${(1 - ph) * 500}px) rotate(-2deg)`}}>
        <Phone h={820}>
          <div style={{position: 'absolute', inset: 0, background: P.cream}}>
            <div style={{height: 70, background: P.sageDark, color: P.white, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 26, display: 'flex', alignItems: 'center', padding: '0 24px'}}>Doctor view</div>
            <div style={{position: 'absolute', left: 20, right: 20, top: 100, transform: `translateX(${-next * 420}px)`}}>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 32, lineHeight: 1.25, color: P.ink}}>Would you be surprised if this patient died in the next 12 months?</div>
              <div style={{display: 'flex', gap: 16, marginTop: 40}}>
                <div style={{flex: 1, height: 74, borderRadius: 18, border: `3px solid ${P.sage}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.sage}}>Yes</div>
                <div style={{flex: 1, height: 74, borderRadius: 18, background: pressed || t > tapNo ? P.sage : 'transparent', border: `3px solid ${P.sage}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: t > tapNo ? P.white : P.sage, transform: `scale(${pressed ? 0.94 : 1})`}}>No</div>
              </div>
            </div>
            <div style={{position: 'absolute', left: 20, right: 20, top: 100, transform: `translateX(${(1 - next) * 420}px)`}}>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, lineHeight: 1.25, color: P.ink}}>It may be time to talk about what matters to this patient</div>
              <div style={{marginTop: 40, height: 84, borderRadius: 20, background: P.marigold, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 28, color: P.white, transform: `scale(${pulse})`}}>Start the goals-of-care talk</div>
            </div>
          </div>
        </Phone>
      </div>
      {t > tapNo - 0.3 && t < tapNo + 0.4 && <div style={{position: 'absolute', left: 840, top: 395, width: 50, height: 50, borderRadius: '50%', background: 'rgba(56,46,41,0.25)', transform: `scale(${1 + (t - tapNo + 0.3) * 1.2})`}} />}
      {/* the if/else flow */}
      <At x={1060} y={70} w={820}><R t={t} t0={0.5} text="It starts with the surprise question" size={50} color={P.sageDark} weight={800} /></At>
      <PopAt t={t} t0={1.0} x={1460} y={260}>
        <div style={{width: 760, padding: '22px 30px', borderRadius: 22, background: P.white, border: `4px solid ${P.sageDark}`, boxSizing: 'border-box', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 34, lineHeight: 1.2, color: P.ink, textAlign: 'center'}}>“Would you be surprised if this patient died in the next 12 months?”</div>
      </PopAt>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <path d="M1300 345 L1240 470" stroke={P.marigoldDeep} strokeWidth={8} strokeLinecap="round" fill="none" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - easeOutCubic(ramp(t, tapNo + 0.2, tapNo + 0.6))} />
        <path d="M1620 345 L1690 470" stroke="#B3A89F" strokeWidth={8} strokeLinecap="round" fill="none" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - easeOutCubic(ramp(t, 2.0, 2.4))} />
      </svg>
      <PopAt t={t} t0={tapNo + 0.2} x={1220} y={420}><div style={{padding: '6px 22px', borderRadius: 30, background: P.marigoldDeep, color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30}}>No</div></PopAt>
      <PopAt t={t} t0={2.0} x={1720} y={420}><div style={{padding: '6px 22px', borderRadius: 30, background: '#B3A89F', color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30}}>Yes</div></PopAt>
      <PopAt t={t} t0={tapNo + 0.6} x={1250} y={610}>
        <div style={{width: 440, padding: '22px 26px', borderRadius: 22, background: P.marigold, boxSizing: 'border-box', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 32, lineHeight: 1.2, color: P.white, textAlign: 'center'}}>Sahara prompts the doctor to start the goals-of-care talk</div>
      </PopAt>
      <PopAt t={t} t0={2.4} x={1700} y={600}>
        <div style={{width: 340, padding: '20px 22px', borderRadius: 22, background: P.white, border: '3px solid #D8CFC6', boxSizing: 'border-box', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 28, lineHeight: 1.2, color: P.inkSub, textAlign: 'center'}}>Continue usual care, ask again later</div>
      </PopAt>
      <At x={1030} y={780} w={860}><R t={t} t0={tapNo + 1.4} text="No means the doctor believes the patient may be nearing the end of life. The talk is between the doctor, the patient and the caregiver." size={30} color={P.ink} weight={600} /></At>
      <PopAt t={t} t0={tapNo + 2.0} x={1460} y={985}>
        <div style={{display: 'flex', alignItems: 'center', gap: 14, padding: '10px 26px', borderRadius: 60, background: P.sageLight}}>
          <Glyph k="lock" s={34} c={P.sageDark} />
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 28, color: P.sageDark, whiteSpace: 'nowrap'}}>Doctor view only · never shown to patients</div>
        </div>
      </PopAt>
    </Bg>
  );
};

/* ---------- 4. the real forms turning into the real screens ---------- */
export const TR_T = [0.4, 5.6, 10.8];
export const ToolsReal: React.FC<SP> = ({t}) => {
  const tools: {name: string; sub: string; by: string; form: string; still: string; c: string; fw: number}[] = [
    {name: 'ESAS-r', sub: 'Edmonton Symptom Assessment System, revised', by: 'Patient', form: 'real/esas_r.png', still: 'real/symptom_screen.png', c: P.sage, fw: 640},
    {name: 'NCCN Distress Thermometer', sub: '0–10', by: 'Patient', form: 'real/nccn.png', still: 'real/distress_screen.png', c: P.marigoldDeep, fw: 640},
    {name: 'Zarit Caregiver Burden', sub: '4-item screen (ZBI-4)', by: 'Caregiver', form: 'real/zarit.png', still: 'stills/f14-carer-burden-ui.png', c: P.rose, fw: 680},
  ];
  let i = 0;
  for (let k = 0; k < 3; k++) if (t >= TR_T[k] - 0.3) i = k;
  const b = TR_T[i];
  const tl = tools[i];
  const inP = easeOutCubic(ramp(t, b - 0.3, b + 0.3));
  const outP = i < 2 ? easeInOutCubic(ramp(t, TR_T[i + 1] - 0.6, TR_T[i + 1] - 0.3)) : 0;
  const dx = (1 - inP) * 600 - outP * 1400;
  const morph = easeInOutCubic(ramp(t, b + 1.6, b + 2.6));
  return (
    <Bg t={t}>
      <At x={960} y={36} w={1700} align="center"><R t={t} t0={0.05} text="Clinically validated tools, made patient-friendly" size={52} color={P.sageDark} weight={800} align="center" /></At>
      {[0, 1, 2].map((k) => <div key={k} style={{position: 'absolute', left: 900 + k * 40, top: 118, width: 24, height: 10, borderRadius: 5, background: k === i ? tools[k].c : P.sand}} />)}
      <div style={{position: 'absolute', inset: 0, transform: `translateX(${dx}px)`}}>
        <div style={{position: 'absolute', left: 0, right: 0, top: 150, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 70, color: tl.c, whiteSpace: 'nowrap'}}>{tl.name} <span style={{fontSize: 42, color: P.inkSub}}>{tl.sub}</span></div>
        <div style={{position: 'absolute', left: 130, top: 300, width: tl.fw, padding: 16, background: P.white, borderRadius: 10, boxShadow: '0 30px 60px rgba(30,40,50,0.16)', transform: `rotate(-2deg) scale(${1 - morph * 0.06})`, opacity: 1 - morph * 0.2}}>
          <Img src={staticFile(tl.form)} style={{width: '100%', display: 'block'}} />
          <div style={{position: 'absolute', left: 16, top: -40, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.inkSub}}>Paper form</div>
        </div>
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
          <path d="M860 560 L1080 560 M1050 530 L1085 560 L1050 590" stroke={tl.c} strokeWidth={12} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - morph} />
        </svg>
        <div style={{position: 'absolute', left: 1180, top: 300 + (1 - morph) * 100, opacity: clamp(morph * 2), transform: 'rotate(2deg)'}}>
          <Phone h={640}><StillImg src={tl.still} /></Phone>
          <div style={{position: 'absolute', left: 0, right: 0, top: -44, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: tl.c}}>Sahara screen</div>
        </div>
        <div style={{position: 'absolute', left: 130, top: 940, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 36, color: P.inkSub, opacity: ramp(t, b + 2.8, b + 3.3)}}>Answered by: <span style={{color: tl.c, fontWeight: 700}}>{tl.by}</span></div>
      </div>
      <Fictional />
    </Bg>
  );
};

/* ---------- 5. the visit calendar ---------- */
export const VisitCal: React.FC<SP> = ({t}) => {
  const ph = pop(t, 0.2, 9, 0.62);
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 1080, top: 120, transform: `translateY(${(1 - ph) * 500}px) rotate(2deg)`}}><Phone h={840}><StillImg src="real/calendar.png" /></Phone></div>
      <PopAt t={t} t0={0.5} x={330} y={330}><Dot k="clock" d={150} bg={P.marigold} /></PopAt>
      <At x={200} y={440} w={800}><R t={t} t0={0.6} text="Next hospital visit" size={84} color={P.sageDark} weight={800} /></At>
      <At x={200} y={560} w={760}><R t={t} t0={1.2} text="The calendar shows the day of the visit and its entry, with a reminder" size={44} color={P.ink} weight={600} /></At>
      <Fictional />
    </Bg>
  );
};

/* ---------- 6. the real QR, the scan, and what opens ---------- */
export const QrReal: React.FC<SP> = ({t}) => {
  const card = pop(t, 0.15, 8, 0.62);
  const phoneIn = easeOutCubic(ramp(t, 3.6, 4.4));
  const scan = ramp(t, 4.4, 5.3);
  const page = easeOutCubic(ramp(t, 5.4, 6.2));
  const cardOut = easeInOutCubic(ramp(t, 3.6, 4.4));
  return (
    <Bg t={t} dark>
      <div style={{position: 'absolute', left: lerp(170, 40, cardOut), top: lerp(250, 300, cardOut), width: 1580, transform: `scale(${card * lerp(1, 0.62, cardOut)})`, transformOrigin: '0 0'}}>
        <div style={{padding: 14, background: P.white, borderRadius: 16, boxShadow: '0 40px 90px rgba(0,0,0,0.45)'}}><Img src={staticFile('real/wallet_card.png')} style={{width: '100%', display: 'block'}} /></div>
      </div>
      <At x={960} y={90} w={1700} align="center"><R t={t} t0={0.4} text="The emergency card: one real QR" size={64} color={P.white} weight={800} align="center" /></At>
      {scan > 0 && scan < 1 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
          <path d="M1290 520 L800 330 L800 560 Z" fill="rgba(95,227,192,0.25)" />
        </svg>
      )}
      <div style={{position: 'absolute', left: 1250, top: 150 + (1 - phoneIn) * 900}}>
        <Phone h={820}>
          <div style={{position: 'absolute', inset: 0, background: '#22303F'}}>
            {scan > 0 && scan < 1 && <div style={{position: 'absolute', left: 40, right: 40, top: 200 + scan * 300, height: 6, background: P.monitor, boxShadow: `0 0 20px ${P.monitor}`}} />}
            <div style={{position: 'absolute', left: 0, right: 0, top: `${(1 - page) * 100}%`, bottom: 0, background: P.white}}>
              <div style={{height: 60, background: '#B5442B', color: P.white, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 22, display: 'flex', alignItems: 'center', padding: '0 18px'}}>Emergency care plan · signed</div>
              <Img src={staticFile('real/plan_left.png')} style={{width: '100%', display: 'block'}} />
              <Img src={staticFile('real/goc-1.png')} style={{width: '100%', display: 'block'}} />
            </div>
          </div>
        </Phone>
      </div>
      <At x={170} y={830} w={1000}><R t={t} t0={6.0} text="A scan opens her signed wishes: CPR, breathing support, transfer, who to call" size={44} color={P.white} weight={700} /></At>
      <Fictional dark />
    </Bg>
  );
};

/* ---------- 7. RAG over the patient's own documents ---------- */
export const Rag: React.FC<SP> = ({t}) => {
  const ph = pop(t, 0.2, 9, 0.62);
  const docIn = (k: number) => easeOutCubic(ramp(t, 0.8 + k * 0.4, 1.4 + k * 0.4));
  const hl = ramp(t, 4.2, 4.8);
  return (
    <Bg t={t}>
      <At x={960} y={50} w={1800} align="center"><R t={t} t0={0.05} text="Ask your own documents" size={84} color={P.sageDark} weight={800} align="center" /></At>
      <At x={960} y={160} w={1800} align="center"><R t={t} t0={0.4} text="RAG over the patient's own prescriptions and reports" size={44} color={P.inkSub} weight={700} align="center" /></At>
      {[0, 1].map((k) => (
        <div key={k} style={{position: 'absolute', left: 170 + k * 60, top: 300 + k * 50, width: 380, height: 480, borderRadius: 14, background: P.white, boxShadow: '0 24px 50px rgba(30,40,50,0.16)', transform: `translateX(${(1 - docIn(k)) * -400}px) rotate(${-4 + k * 5}deg)`, opacity: docIn(k), padding: 30, boxSizing: 'border-box'}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.ink}}>{k ? 'Prescription' : 'Lab report'}</div>
          {[0, 1, 2, 3, 4, 5, 6].map((l) => (
            <div key={l} style={{marginTop: 22, height: 16, borderRadius: 8, width: `${[90, 70, 85, 60, 80, 65, 75][l]}%`, background: k === 1 && l === 3 ? `rgba(224,160,64,${0.25 + 0.75 * hl})` : P.well, boxShadow: k === 1 && l === 3 && hl > 0 ? `0 0 0 ${6 * hl}px rgba(224,160,64,0.3)` : undefined}} />
          ))}
        </div>
      ))}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <path d="M640 560 C 760 560, 800 500, 900 500" stroke={P.marigold} strokeWidth={6} strokeDasharray="4 14" fill="none" strokeLinecap="round" opacity={hl} />
      </svg>
      <div style={{position: 'absolute', left: 900, top: 230, transform: `translateY(${(1 - ph) * 600}px) rotate(1deg)`}}><Phone h={700}><StillImg src="stills/f11-ask-documents.png" /></Phone></div>
      <At x={1290} y={330} w={560}><R t={t} t0={2.4} text="Upload prescriptions and reports" size={42} color={P.ink} weight={700} /></At>
      <At x={1290} y={470} w={560}><R t={t} t0={3.3} text="Ask a question in plain words" size={42} color={P.ink} weight={700} /></At>
      <At x={1290} y={610} w={560}><R t={t} t0={4.3} text="The answer shows the exact line it came from" size={42} color={P.marigoldDeep} weight={800} /></At>
      <Fictional />
    </Bg>
  );
};

/* ---------- 8. live on Google Play ---------- */
export const PlayLive: React.FC<SP> = ({t}) => {
  const a = pop(t, 0.2, 8, 0.62);
  return (
    <Bg t={t}>
      <At x={960} y={120} w={1700} align="center"><R t={t} t0={0.1} text="Live on Google Play" size={96} color={P.sageDark} weight={800} align="center" /></At>
      <div style={{position: 'absolute', left: 960 - 760, top: 330, width: 1520, transform: `scale(${a})`, borderRadius: 24, overflow: 'hidden', boxShadow: '0 30px 70px rgba(30,40,50,0.18)'}}>
        <Img src={staticFile('brand/play-listing.png')} style={{width: '100%', display: 'block'}} />
      </div>
      {pop(t, 1.0) > 0.01 && <PlayBadge x={960} y={840} s={1.5 * pop(t, 1.0, 9, 0.6)} />}
    </Bg>
  );
};

/* ---------- 9. We filled the gaps ---------- */
const COLS = ['Panha', 'Kaiku / Noona', 'Abridge', 'Vynca', 'ReSPECT (UK)', 'Palli Sahayak', 'Pallium India directory', 'PalaSh (AIIMS Bathinda)', 'BandhuCare', 'Kerala care grid', 'London UCP · Oregon · Taiwan', 'Sahara'];
const ROWS: [string, number[]][] = [
  ['Symptom and distress logging by the family', [0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0]],
  ['Works in Indian languages', [0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0]],
  ['Medicines with dose reminders', [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]],
  ['Reports, with questions answered', [1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0]],
  ['Family members share the care', [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]],
  ['Recorded talk, AI draft, doctor signs', [0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0]],
  ['Wishes visible in an emergency', [0, 0, 0, 1, 2, 0, 0, 0, 0, 0, 2]],
  ['Palliative centres and oral morphine near you', [0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0]],
  ['Built for and available in India', [2, 0, 0, 0, 0, 2, 2, 2, 2, 2, 0]],
];
export const GAP_R0 = 0.8;
export const GAP_STEP = 0.32;
export const GAP_SAHARA = GAP_R0 + ROWS.length * GAP_STEP + 0.6;
const Mark: React.FC<{v: number; s: number}> = ({v, s}) => {
  if (v === 3) return <div style={{width: 26, height: 26, borderRadius: '50%', background: P.white, transform: `scale(${s})`}} />;
  if (v === 2) return <div style={{width: 24, height: 24, borderRadius: '50%', background: '#147A6C', transform: `scale(${s})`}} />;
  if (v === 1) return <div style={{width: 24, height: 24, borderRadius: '50%', border: `4px solid ${P.marigold}`, background: `linear-gradient(90deg, ${P.marigold} 50%, transparent 50%)`, boxSizing: 'border-box', transform: `scale(${s})`}} />;
  return <div style={{width: 16, height: 16, borderRadius: '50%', border: '3px solid #C4BCB4', boxSizing: 'border-box', transform: `scale(${s})`}} />;
};
export const Gaps: React.FC<SP> = ({t}) => {
  const x0 = 30;
  const lw = 480;
  const cw = 111;
  const top = 230;
  const hh = 110;
  const rh = 64;
  const H = hh + ROWS.length * rh;
  const sah = x0 + lw + 11 * cw;
  const fillCol = easeOutCubic(ramp(t, GAP_SAHARA, GAP_SAHARA + 0.8));
  return (
    <Bg t={t}>
      <At x={960} y={50} w={1800} align="center"><R t={t} t0={0.05} text="We filled the gaps" size={92} color={P.sageDark} weight={800} align="center" /></At>
      <div style={{position: 'absolute', left: 0, right: 0, top: 160, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 30, color: P.inkSub, opacity: ramp(t, 0.5, 1.0)}}>Others each do one piece · Sahara joins the whole journey</div>
      <div style={{position: 'absolute', left: x0, top, width: lw + 12 * cw, height: H, background: P.white, borderRadius: 16, boxShadow: '0 24px 60px rgba(30,40,50,0.12)', opacity: ramp(t, 0.3, 0.8)}} />
      <div style={{position: 'absolute', left: sah, top, width: cw, height: H * fillCol, background: '#147A6C', borderRadius: 16}} />
      {COLS.map((c, j) => (
        <div key={c} style={{position: 'absolute', left: x0 + lw + j * cw, top, width: cw, height: hh, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: j === 11 ? 28 : 18, lineHeight: 1.12, color: j === 11 ? (fillCol > 0.1 ? P.white : P.ink) : P.ink, padding: '0 6px', boxSizing: 'border-box', opacity: j === 11 ? ramp(t, GAP_SAHARA - 0.2, GAP_SAHARA + 0.2) : ramp(t, 0.6 + j * 0.05, 0.9 + j * 0.05)}}>{c}</div>
      ))}
      {ROWS.map(([label, vals], r) => {
        const t0 = GAP_R0 + r * GAP_STEP;
        const y = top + hh + r * rh;
        const a = ramp(t, t0, t0 + 0.4);
        return (
          <React.Fragment key={r}>
            <div style={{position: 'absolute', left: x0, top: y, width: lw + 11 * cw, height: 2, background: P.well, opacity: ramp(t, 0.3, 0.8)}} />
            <div style={{position: 'absolute', left: x0 + 18, top: y, height: rh, display: 'flex', alignItems: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 20, color: P.ink, opacity: a, transform: `translateX(${(1 - a) * 30}px)`}}>{label}</div>
            {vals.map((v, j) => (
              <div key={j} style={{position: 'absolute', left: x0 + lw + j * cw, top: y, width: cw, height: rh, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                <Mark v={v} s={pop(t, t0 + 0.1 + j * 0.03, 12, 0.6)} />
              </div>
            ))}
            <div style={{position: 'absolute', left: sah, top: y, width: cw, height: rh, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
              <Mark v={3} s={pop(t, GAP_SAHARA + 0.3 + r * 0.12, 12, 0.55)} />
            </div>
          </React.Fragment>
        );
      })}
      <div style={{position: 'absolute', left: x0, top: top + H + 22, display: 'flex', gap: 36, alignItems: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 22, color: P.inkSub, opacity: ramp(t, GAP_SAHARA + 1.6, GAP_SAHARA + 2.0)}}>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={2} s={0.8} /> Does it</span>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={1} s={0.8} /> Partly</span>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={0} s={1} /> Not described</span>
        <span style={{marginLeft: 30, fontSize: 19}}>Sources: app stores, product and programme pages; team comparison sheet (20 Nov 2026)</span>
      </div>
    </Bg>
  );
};
