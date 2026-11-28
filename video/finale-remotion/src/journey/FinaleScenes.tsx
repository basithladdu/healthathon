/* Grand Finale scenes spliced into the Journey film. Drawn scenes in the film's own flat style: text rises straight onto
   the scene, no text boxes. Each scene gets local seconds t (0 = first frame of the insert). */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {Blob, CamKey, FONT_BODY, FONT_HEAD, P, Person, Rise, World, camAt, clamp, easeInOutCubic, easeOutCubic, easeOutQuint, kick, lerp, pop, ramp, spring} from './kit';
import {CAST} from './cast';
import {Face, PlayBadge, Who} from './team';
import {IndiaMap, makePins} from './scenes/X_map';

export type SP = {t: number};

/* ---------- shared ---------- */
const Bg: React.FC<{t: number; a?: string; b?: string; dark?: boolean; children: React.ReactNode; zoom?: boolean}> = ({t, a, b, dark, children, zoom = true}) => {
  const c1 = a ?? (dark ? P.night1 : P.cream);
  const c2 = b ?? (dark ? P.night0 : P.creamWarm);
  const z = zoom ? 1.03 - 0.03 * easeOutCubic(ramp(t, 0, 0.6)) + 0.003 * t : 1;
  return (
    <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${c1}, ${c2})`}}>
      <Blob x={260 + Math.sin(t * 0.5) * 50} y={180} r={340} color={dark ? 'rgba(95,227,192,0.10)' : 'rgba(159,209,184,0.40)'} blur={90} />
      <Blob x={1660 + Math.cos(t * 0.4) * 50} y={880} r={380} color={dark ? 'rgba(242,179,94,0.10)' : 'rgba(224,160,64,0.22)'} blur={100} />
      <Blob x={1500} y={120 + Math.sin(t * 0.6) * 30} r={220} color={dark ? 'rgba(127,179,217,0.08)' : 'rgba(196,106,132,0.13)'} blur={90} />
      <div style={{position: 'absolute', inset: 0, transform: `scale(${z})`}}>{children}</div>
    </div>
  );
};
const At: React.FC<{x: number; y: number; w: number; align?: 'left' | 'center' | 'right'; children: React.ReactNode; o?: number}> = ({x, y, w, align = 'left', children, o = 1}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, top: y, width: w, opacity: o}}>{children}</div>
);
const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'; stagger?: number; font?: string}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={p.stagger ?? 0.045} font={p.font} />
);
const PopAt: React.FC<{t: number; t0: number; x: number; y: number; children: React.ReactNode; f?: number; d?: number}> = ({t, t0, x, y, children, f = 13, d = 0.5}) => {
  const s = pop(t, t0, f, d);
  if (s <= 0.001) return null;
  return <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s})`}}>{children}</div>;
};
/* stroke icons in a 64 box */
export const Glyph: React.FC<{k: string; s?: number; c?: string; w?: number}> = ({k, s = 64, c = P.white, w = 4.5}) => {
  const st = {stroke: c, strokeWidth: w, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const};
  const body: Record<string, React.ReactNode> = {
    heart: <path d="M32 54 C8 38 8 14 22 14 C28 14 32 20 32 20 C32 20 36 14 42 14 C56 14 56 38 32 54 Z" fill={c} />,
    people: (<><circle cx={22} cy={22} r={8} {...st} /><circle cx={44} cy={24} r={7} {...st} /><path d="M6 52 A16 16 0 0 1 38 52 M34 50 A13 13 0 0 1 59 52" {...st} /></>),
    pen: <path d="M12 52 L16 38 L42 12 L52 22 L26 48 Z M36 18 L46 28" {...st} />,
    qr: (<><rect x={8} y={8} width={18} height={18} rx={3} {...st} /><rect x={38} y={8} width={18} height={18} rx={3} {...st} /><rect x={8} y={38} width={18} height={18} rx={3} {...st} /><path d="M38 38 H46 V46 M56 38 V56 H46 M38 50 V56" {...st} /></>),
    doc: (<><path d="M16 8 H40 L50 18 V56 H16 Z" {...st} /><path d="M24 28 H42 M24 38 H42 M24 48 H36" {...st} /></>),
    check: <path d="M14 33 L27 46 L50 20" {...st} strokeWidth={w * 1.5} />,
    clock: (<><circle cx={32} cy={32} r={24} {...st} /><path d="M32 18 V32 L42 38" {...st} /></>),
    hospital: (<><path d="M10 56 V20 L32 8 L54 20 V56 Z" {...st} /><path d="M32 24 V40 M24 32 H40" {...st} /></>),
    code: <path d="M24 18 L8 32 L24 46 M40 18 L56 32 L40 46 M36 12 L28 52" {...st} />,
    phone: (<><rect x={18} y={6} width={28} height={52} rx={6} {...st} /><path d="M28 50 H36" {...st} /></>),
    wrench: <path d="M40 10 A12 12 0 0 0 30 28 L10 48 A4 4 0 0 0 16 54 L36 34 A12 12 0 0 0 54 24 L46 30 L38 26 L34 18 Z" {...st} />,
    pill: (<><rect x={8} y={22} width={48} height={20} rx={10} {...st} transform="rotate(-35 32 32)" /><path d="M24 20 L40 44" {...st} /></>),
    lock: (<><rect x={12} y={28} width={40} height={28} rx={6} {...st} /><path d="M20 28 V20 A12 12 0 0 1 44 20 V28" {...st} /></>),
    shield: (<><path d="M32 6 L54 14 V30 C54 44 44 53 32 58 C20 53 10 44 10 30 V14 Z" {...st} /><path d="M22 32 L29 39 L43 24" {...st} /></>),
    spark: <path d="M32 6 L37 26 L58 32 L37 38 L32 58 L27 38 L6 32 L27 26 Z" fill={c} />,
    plug: (<><path d="M24 8 V20 M40 8 V20 M16 20 H48 V30 A16 16 0 0 1 16 30 Z M32 46 V58" {...st} /></>),
    puzzle: <path d="M10 22 H24 A6 6 0 1 1 36 22 H50 V36 A6 6 0 1 0 50 48 V56 H10 Z" fill={c} />,
    offline: (<><path d="M18 46 H46 A11 11 0 0 0 47 24 A15 15 0 0 0 18 28 A9 9 0 0 0 18 46 Z" {...st} /><path d="M10 10 L54 54" {...st} /></>),
  };
  return (
    <svg width={s} height={s} viewBox="0 0 64 64" style={{display: 'block', overflow: 'visible', flexShrink: 0}}>
      {body[k]}
    </svg>
  );
};
const Dot: React.FC<{k: string; d: number; bg: string; c?: string}> = ({k, d, bg, c}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)'}}>
    <Glyph k={k} s={d * 0.52} c={c} />
  </div>
);
const Floor: React.FC<{y: number; color?: string}> = ({y, color = P.sand}) => (
  <div style={{position: 'absolute', left: -100, right: -100, top: y, height: 1080 - y + 100, background: color, borderTopLeftRadius: '50% 40px', borderTopRightRadius: '50% 40px'}} />
);
const Burst: React.FC<{t: number; t0: number; x: number; y: number; color: string; r?: number}> = ({t, t0, x, y, color, r = 260}) => {
  const p = ramp(t, t0, t0 + 0.55);
  if (p <= 0 || p >= 1) return null;
  return (
    <svg style={{position: 'absolute', left: x - r * 1.6, top: y - r * 1.6, overflow: 'visible'}} width={r * 3.2} height={r * 3.2}>
      {Array.from({length: 14}).map((_, i) => {
        const a = (i / 14) * Math.PI * 2;
        const r0 = r * (0.6 + 0.7 * easeOutCubic(p));
        const r1 = r0 + 60 * (1 - p);
        const c = r * 1.6;
        return <line key={i} x1={c + Math.cos(a) * r0} y1={c + Math.sin(a) * r0} x2={c + Math.cos(a) * r1} y2={c + Math.sin(a) * r1} stroke={color} strokeWidth={10 * (1 - p)} strokeLinecap="round" />;
      })}
    </svg>
  );
};

/* ---------- 1. the evidence: one big number at a time ---------- */
export const EV_BEATS = [0.25, 2.35, 4.45, 6.55, 8.65];
const EV: {pre?: string; n: number; fmt: (n: number) => string; label: string; bg: [string, string]; c: string; src?: string; down?: boolean}[] = [
  {pre: 'ONLY', n: 37, fmt: (n) => (n < 30 ? `${n}%` : `30–${n}%`), label: 'get timely palliative-care referrals', bg: ['#16273D', '#0E1A2B'], c: P.lampWarm},
  {n: 41, fmt: (n) => `${n}%`, label: 'have end-of-life care documented', bg: ['#3B2A45', '#22172B'], c: '#F0A8BF'},
  {pre: 'BUT', n: 88, fmt: (n) => `${n}%`, label: 'want to choose their own end-of-life care', bg: ['#1F4A3C', '#123226'], c: P.monitor},
  {n: 63, fmt: (n) => `${n}%`, label: 'of end-stage patients: wishes not found within 15 minutes in the ER', bg: ['#4A2A22', '#2A1712'], c: P.coral, src: 'Russell 2021'},
  {n: 4, fmt: (n) => `<${n}%`, label: 'of those who need palliative care get it', bg: ['#16273D', '#0B1422'], c: P.marigold, down: true},
];
export const Evidence2: React.FC<SP> = ({t}) => {
  let i = 0;
  for (let k = 0; k < EV.length; k++) if (t >= EV_BEATS[k] - 0.3) i = k;
  const e = EV[i];
  const b = EV_BEATS[i];
  const prev = i > 0 ? EV[i - 1] : null;
  const wipe = easeInOutCubic(ramp(t, b - 0.3, b + 0.05));
  const cnt = easeOutCubic(ramp(t, b, b + 0.6));
  const n = e.down ? Math.round(lerp(40, e.n, cnt)) : Math.round(e.n * cnt);
  const sc = pop(t, b - 0.05, 11, 0.42);
  const [kx, ky] = kick(t, b, 14);
  const bar = easeOutCubic(ramp(t, b + 0.1, b + 0.9)) * (e.down ? 4 : e.n);
  const last = i === EV.length - 1;
  return (
    <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: prev ? `linear-gradient(160deg, ${prev.bg[0]}, ${prev.bg[1]})` : `linear-gradient(160deg, ${e.bg[0]}, ${e.bg[1]})`}}>
      <div style={{position: 'absolute', inset: 0, background: `linear-gradient(160deg, ${e.bg[0]}, ${e.bg[1]})`, clipPath: `circle(${wipe * 1300}px at 960px 470px)`}} />
      <Blob x={960} y={470} r={420} color={e.c + '22'} blur={120} />
      <div style={{position: 'absolute', inset: 0, transform: `translate(${kx}px,${ky}px) scale(${1 + 0.02 * (t - b)})`}}>
        <Burst t={t} t0={b} x={960} y={440} color={e.c} r={300} />
        {e.pre && (
          <At x={960} y={130} w={1600} align="center">
            <R t={t} t0={b - 0.2} text={e.pre} size={84} color={P.white} weight={800} align="center" />
          </At>
        )}
        <div style={{position: 'absolute', left: 0, right: 0, top: e.pre ? 215 : 170, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 330, lineHeight: 1, color: e.c, letterSpacing: -6, transform: `scale(${sc})`, textShadow: `0 0 80px ${e.c}55`, whiteSpace: 'nowrap'}}>
          {e.fmt(n)}
        </div>
        {/* 100 thin ticks: the share lit up */}
        <div style={{position: 'absolute', left: 360, top: 600, width: 1200, height: 46, display: 'flex', gap: 4}}>
          {Array.from({length: 100}).map((_, k) => (
            <div key={k} style={{flex: 1, borderRadius: 4, background: k < bar ? e.c : 'rgba(255,255,255,0.14)', transform: `scaleY(${k < bar ? 1 : 0.6})`}} />
          ))}
        </div>
        <At x={960} y={690} w={1500} align="center">
          <R t={t} t0={b + 0.25} text={e.label} size={e.label.length > 50 ? 56 : 64} color={P.white} weight={700} align="center" stagger={0.035} />
        </At>
        {e.src && <div style={{position: 'absolute', right: 120, top: 60, fontFamily: FONT_BODY, fontSize: 28, color: 'rgba(255,255,255,0.6)', opacity: ramp(t, b + 0.4, b + 0.8)}}>Source: {e.src}</div>}
        {last && <div style={{position: 'absolute', left: 0, right: 0, top: 860, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 34, color: 'rgba(255,255,255,0.7)', opacity: ramp(t, b + 0.9, b + 1.3)}}>of the 7–10 million Indians who need it</div>}
      </div>
    </div>
  );
};

/* ---------- 2. who it is for: the four people ---------- */
export const WF_T = [0.4, 1.3, 2.2, 3.1];
export const WhoFor: React.FC<SP> = ({t}) => {
  const ppl: {cast: any; x: number; title: string; c: string; k: string; lines: string[]; expr: any; hold?: React.ReactNode; flip?: boolean}[] = [
    {cast: CAST.patient, x: 290, title: 'Patient', c: P.rose, k: 'heart', lines: ['Symptoms, pain and distress', 'Multilingual', 'Simple Mode'], expr: 'happy'},
    {cast: CAST.daughter, x: 735, title: 'Caregiver', c: P.marigoldDeep, k: 'people', lines: ['Everyone stays in the loop', 'Reminders', 'Their own wellbeing check'], expr: 'happy'},
    {cast: CAST.teamDoctor, x: 1180, title: 'Their doctor', c: P.sageDark, k: 'pen', lines: ['Leads the goals-of-care talk', 'Sees trends', 'Signs'], expr: 'talk'},
    {
      cast: CAST.erDoctor, x: 1625, title: 'Emergency doctor', c: P.indigo, k: 'qr', lines: ['Scans the QR', 'Signed wishes in seconds', 'No login'], expr: 'happy', flip: true,
      hold: (<g><rect x={-18} y={-58} width={36} height={66} rx={7} fill="#1B1A1F" /><rect x={-14} y={-54} width={28} height={56} rx={4} fill="#1E5E52" /></g>),
    },
  ];
  return (
    <Bg t={t}>
      <Floor y={600} />
      <At x={960} y={50} w={1700} align="center"><R t={t} t0={0.05} text="Who Sahara is for" size={80} color={P.sageDark} weight={800} align="center" /></At>
      {ppl.map((p, i) => {
        const t0 = WF_T[i];
        const a = pop(t, t0, 11, 0.5);
        const sq = 1 + (t > t0 ? Math.exp(-(t - t0) * 6) * Math.sin((t - t0) * 20) * 0.08 : 0);
        return (
          <React.Fragment key={i}>
            <Blob x={p.x} y={470} r={170 * a} color={p.c + '33'} blur={50} />
            {a > 0.01 && (
              <div style={{position: 'absolute', left: 0, top: (1 - Math.min(1, a)) * 120, opacity: clamp(a * 3)}}>
                <Person {...p.cast} t={t} x={p.x} y={630} s={0.78} squashY={sq} expr={p.expr} talk={p.expr === 'talk' && t > t0 + 0.4 && t < t0 + 2} flip={p.flip} armR={p.hold ? 40 : 4} elbowR={p.hold ? -70 : 0} armL={4} hold={p.hold} />
              </div>
            )}
            <PopAt t={t} t0={t0 + 0.25} x={p.x + 110} y={275 + Math.sin(t * 1.4 + i) * 6}><Dot k={p.k} d={72} bg={p.c} /></PopAt>
            <At x={p.x} y={145} w={420} align="center"><R t={t} t0={t0 + 0.1} text={p.title} size={50} color={p.c} weight={800} align="center" /></At>
            {p.lines.map((l, j) => (
              <At key={j} x={p.x} y={665 + j * 66} w={440} align="center"><R t={t} t0={t0 + 0.35 + j * 0.18} text={l} size={30} color={P.ink} weight={600} align="center" /></At>
            ))}
          </React.Fragment>
        );
      })}
      <At x={960} y={890} w={1800} align="center"><R t={t} t0={WF_T[3] + 1.2} text="Patient, caregiver and doctor logins · website first, then the Android app" size={40} color={P.sageDark} weight={700} align="center" stagger={0.03} /></At>
    </Bg>
  );
};

/* ---------- 3. safety: the document builds itself safely ---------- */
export const SF_T = [0.9, 2.5, 4.1, 9, 9];
export const Safety: React.FC<SP> = ({t}) => {
  const lines = [0.92, 0.7, 0.85, 0.6, 0.8, 0.5];
  const doc = pop(t, 0.1, 11, 0.55);
  const sig1 = ramp(t, SF_T[0], SF_T[0] + 0.6);
  const sig2 = ramp(t, SF_T[0] + 0.5, SF_T[0] + 1.1);
  const thread = ramp(t, SF_T[2], SF_T[2] + 0.8);

  const items: [string, string][] = [
    ['People stay in control', P.rose],
    ['Complies with the DPDP Act', P.plum],
    ['Legal advice shaped the wording and format', P.indigo],
  ];
  return (
    <Bg t={t}>
      {/* speech bubbles the lines link back to */}
      {[0, 1, 2].map((k) => (
        <PopAt key={k} t={t} t0={SF_T[2] - 0.2 + k * 0.12} x={110} y={330 + k * 150}>
          <div style={{width: 120, height: 80, borderRadius: 30, background: [P.sageLight, P.roseLight, '#E3E8F5'][k], position: 'relative'}}>
            <div style={{position: 'absolute', left: 22, top: 26, width: 70, height: 8, borderRadius: 4, background: 'rgba(56,46,41,0.25)'}} />
            <div style={{position: 'absolute', left: 22, top: 44, width: 48, height: 8, borderRadius: 4, background: 'rgba(56,46,41,0.25)'}} />
          </div>
        </PopAt>
      ))}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        {[0, 1, 2].map((k) => (
          <path key={k} d={`M170 ${330 + k * 150} C 260 ${330 + k * 150}, 260 ${300 + k * 90}, 330 ${300 + k * 90}`} stroke={P.sage} strokeWidth={5} strokeDasharray="2 12" strokeLinecap="round" fill="none" pathLength={1} opacity={thread} />
        ))}
      </svg>
      {/* the document */}
      <div style={{position: 'absolute', left: 330, top: 150, width: 560, height: 740, transform: `rotate(-3deg) scale(${doc})`, transformOrigin: '50% 60%', borderRadius: 30, background: P.white, boxShadow: '0 40px 80px rgba(30,40,50,0.18)', padding: '50px 54px', boxSizing: 'border-box'}}>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 40, color: P.sageDark}}>Goals of care</div>
        <div style={{height: 6, width: 140, background: P.sage, borderRadius: 3, margin: '14px 0 26px'}} />
        {lines.map((w, k) => (
          <div key={k} style={{height: 16, borderRadius: 8, marginBottom: 28, background: k === 1 && t > SF_T[2] ? P.mint : P.well, width: `${w * 100 * easeOutCubic(ramp(t, 0.3 + k * 0.1, 0.8 + k * 0.1))}%`}} />
        ))}
        {/* signatures */}
        <svg width={460} height={150} style={{marginTop: 4}}>
          <path d="M10 70 C40 20 60 100 90 50 S140 30 160 70 S200 40 210 60" stroke={P.ink} strokeWidth={5} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - sig1} />
          <line x1={0} y1={90} x2={210} y2={90} stroke={P.well} strokeWidth={4} />
          <path d="M250 70 C270 30 300 90 320 50 S370 40 390 70 S420 50 440 60" stroke={P.rose} strokeWidth={5} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - sig2} />
          <line x1={240} y1={90} x2={450} y2={90} stroke={P.well} strokeWidth={4} />
          <text x={0} y={128} fontFamily={FONT_BODY} fontSize={24} fill={P.inkSub}>Doctor</text>
          <text x={240} y={128} fontFamily={FONT_BODY} fontSize={24} fill={P.inkSub}>Family</text>
        </svg>
      </div>
      {/* stamps and badges on the document */}
      <PopAt t={t} t0={SF_T[2]} x={790} y={210}><Dot k="doc" d={110} bg={P.indigo} /></PopAt>
      <PopAt t={t} t0={SF_T[0] + 0.9} x={830} y={820}><Dot k="check" d={110} bg={P.indigo} /></PopAt>
      <PopAt t={t} t0={SF_T[1]} x={330} y={830}><Dot k="shield" d={110} bg={P.plum} /></PopAt>
      <At x={1010} y={110} w={860}><R t={t} t0={0.15} text="Safe by design" size={96} color={P.sageDark} weight={800} /></At>
      {items.map(([txt, c], k) => (
        <React.Fragment key={k}>
          <PopAt t={t} t0={SF_T[k]} x={1032} y={360 + k * 160}><div style={{width: 26, height: 26, borderRadius: '50%', background: c}} /></PopAt>
          <At x={1070} y={330 + k * 160} w={820}><R t={t} t0={SF_T[k] + 0.05} text={txt} size={52} color={P.ink} weight={700} /></At>
        </React.Fragment>
      ))}
    </Bg>
  );
};

/* ---------- 4. clinically validated tools: paper form -> Sahara screen, one after another ---------- */
export const TL_T = [0.4, 4.9, 9.4];
const PhoneStill: React.FC<{still: string; h?: number}> = ({still, h = 640}) => (
  <div style={{width: h * 0.47, height: h, borderRadius: h * 0.07, background: '#1B1A1F', padding: h * 0.018, boxSizing: 'border-box', boxShadow: '0 40px 80px rgba(10,20,30,0.30)'}}>
    <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream}}>
      <Img src={staticFile(`stills/${still}`)} style={{width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top'}} />
    </div>
  </div>
);
const Paper: React.FC<{children: React.ReactNode; title: string; w?: number}> = ({children, title, w = 440}) => (
  <div style={{width: w, height: 560, borderRadius: 18, background: P.white, boxShadow: '0 30px 60px rgba(30,40,50,0.16)', padding: '36px 36px', boxSizing: 'border-box', transform: 'rotate(-3deg)'}}>
    <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.inkSub, marginBottom: 22}}>{title}</div>
    {children}
  </div>
);
const Scale: React.FC<{n: number; v: number; on: boolean; c: string; d?: number}> = ({n, v, on, c, d = 26}) => (
  <div style={{display: 'flex', gap: 5}}>
    {Array.from({length: n}).map((_, k) => <div key={k} style={{width: d, height: d, borderRadius: 6, fontFamily: FONT_BODY, fontSize: d * 0.5, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', color: on && k === v ? P.white : '#B3A89F', border: `3px solid ${on && k === v ? c : '#D8CFC6'}`, background: on && k === v ? c : 'transparent', boxSizing: 'border-box'}}>{k}</div>)}
  </div>
);
export const Tools: React.FC<SP> = ({t}) => {
  const tools: {name: string; sub: string; by: string; still: string; c: string}[] = [
    {name: 'ESAS-r', sub: 'symptoms, 0–10', by: 'Patient', still: 'f08-symptoms.png', c: P.sage},
    {name: 'NCCN Distress Thermometer', sub: '0–10', by: 'Patient', still: 'f14-patient-today.png', c: P.marigoldDeep},
    {name: 'Zarit Caregiver Burden', sub: '4-item screen (ZBI-4)', by: 'Caregiver', still: 'f14-carer-burden-ui.png', c: P.rose},
  ];
  let i = 0;
  for (let k = 0; k < 3; k++) if (t >= TL_T[k] - 0.2) i = k;
  const b = TL_T[i];
  const tl = tools[i];
  const inP = easeOutCubic(ramp(t, b - 0.2, b + 0.35));
  const outP = i < 2 ? easeInOutCubic(ramp(t, TL_T[i + 1] - 0.45, TL_T[i + 1] - 0.15)) : 0;
  const dx = (1 - inP) * 700 - outP * 1300;
  const morph = easeInOutCubic(ramp(t, b + 1.6, b + 2.3));
  const fill = t > b + 0.5;
  let form: React.ReactNode;
  if (i === 0) {
    form = (
      <Paper title="ESAS-r" w={600}>
        <div style={{display: 'flex', justifyContent: 'space-between', margin: '-8px 0 8px 170px', fontFamily: FONT_BODY, fontSize: 15, color: P.inkSub}}><span>0 = none</span><span>10 = worst possible</span></div>
        {['Pain', 'Tiredness', 'Drowsiness', 'Nausea', 'Lack of appetite', 'Shortness of breath', 'Depression', 'Anxiety', 'Wellbeing'].map((nm, r) => (
          <div key={r} style={{display: 'flex', alignItems: 'center', gap: 8, marginBottom: 9}}>
            <div style={{width: 162, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 19, color: P.ink, whiteSpace: 'nowrap'}}>{nm}</div>
            <Scale n={11} v={[3, 6, 2, 5, 1, 4, 2, 7, 4][r]} on={fill && t > b + 0.5 + r * 0.12} c={tl.c} d={30} />
          </div>
        ))}
      </Paper>
    );
  } else if (i === 1) {
    const lvl = 6 * easeOutCubic(ramp(t, b + 0.4, b + 1.2));
    form = (
      <Paper title="Distress Thermometer">
        <svg width={360} height={420} style={{overflow: 'visible'}}>
          <rect x={150} y={0} width={60} height={330} rx={30} fill={P.well} />
          <circle cx={180} cy={360} r={52} fill={P.well} />
          <rect x={162} y={12 + 310 * (1 - lvl / 10)} width={36} height={320 - 310 * (1 - lvl / 10)} rx={18} fill={P.marigold} />
          <circle cx={180} cy={360} r={40} fill={P.marigold} />
          {Array.from({length: 11}).map((_, k) => (
            <g key={k}>
              <rect x={222} y={12 + 310 * (1 - k / 10)} width={k % 5 ? 16 : 28} height={4} rx={2} fill={P.inkSub} />
              <text x={262} y={24 + 310 * (1 - k / 10)} fontFamily={FONT_HEAD} fontWeight={700} fontSize={24} fill={P.inkSub}>{k}</text>
            </g>
          ))}
        </svg>
      </Paper>
    );
  } else {
    form = (
      <Paper title="ZBI-4">
        <div style={{display: 'flex', justifyContent: 'space-between', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 18, color: P.inkSub, margin: '0 0 12px 90px'}}><span>Never</span><span>Nearly always</span></div>
        {[2, 3, 1, 2].map((v, r) => (
          <div key={r} style={{display: 'flex', alignItems: 'center', gap: 14, marginBottom: 30}}>
            <div style={{width: 76, height: 14, borderRadius: 7, background: P.well}} />
            {[0, 1, 2, 3, 4].map((k) => {
              const on = fill && k === v && t > b + 0.5 + r * 0.12;
              return <div key={k} style={{width: 44, height: 44, borderRadius: '50%', border: `5px solid ${on ? tl.c : '#D8CFC6'}`, background: on ? tl.c : 'transparent', boxSizing: 'border-box'}} />;
            })}
          </div>
        ))}
        <div style={{display: 'flex', gap: 34, margin: '0 0 0 104px', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 22, color: P.inkSub}}>{[0, 1, 2, 3, 4].map((k) => <span key={k}>{k}</span>)}</div>
      </Paper>
    );
  }
  return (
    <Bg t={t}>
      <At x={960} y={36} w={1700} align="center"><R t={t} t0={0.05} text="Clinically validated tools, made patient-friendly" size={52} color={P.sageDark} weight={800} align="center" /></At>
      {[0, 1, 2].map((k) => <div key={k} style={{position: 'absolute', left: 900 + k * 40, top: 118, width: 24, height: 10, borderRadius: 5, background: k === i ? tools[k].c : P.sand}} />)}
      <div style={{position: 'absolute', inset: 0, transform: `translateX(${dx + 130}px)`}}>
        <At x={960} y={150} w={1700} align="center"><div style={{textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 74, color: tl.c, whiteSpace: 'nowrap', marginLeft: -130}}>{tl.name} <span style={{fontSize: 44, color: P.inkSub}}>{tl.sub}</span></div></At>
        <div style={{position: 'absolute', left: 340 - (i === 0 ? 150 : 0) + morph * 40, top: 290, transform: `scale(${1 - morph * 0.08})`, opacity: 1 - morph * 0.25}}>{form}</div>
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
          <path d="M860 560 L1040 560 M1010 530 L1045 560 L1010 590" stroke={tl.c} strokeWidth={12} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - morph} />
        </svg>
        <div style={{position: 'absolute', left: 1120, top: 250 + (1 - morph) * 120, opacity: clamp(morph * 2), transform: `rotate(${2 + Math.sin(t) * 0.6}deg)`}}><PhoneStill still={tl.still} h={620} /></div>
        <At x={830} y={890} w={1400} align="center">
          <div style={{textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 40, color: P.ink}}>Paper form <span style={{color: tl.c}}>→</span> Sahara screen</div>
          <div style={{textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 34, color: P.inkSub, marginTop: 8}}>Answered by: <span style={{color: tl.c, fontWeight: 700}}>{tl.by}</span></div>
        </At>
      </div>
      <div style={{position: 'absolute', right: 60, bottom: 30, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.5)'}}>Practice data. All names are fictional.</div>
    </Bg>
  );
};

/* ---------- 5. oral morphine on the map ---------- */
const PINS = makePins(170, -9, -8);
const MPIN = (() => {
  let best = PINS[0];
  for (const p of PINS) if (p.y > 0.84 && p.y < 0.9 && Math.abs(p.x - 0.3) < Math.abs(best.x - 0.3) + (best.y > 0.84 && best.y < 0.9 ? 0 : 9)) best = p;
  return best;
})();
export const MO_T = [1.4, 3.3];
export const Morphine: React.FC<SP> = ({t}) => {
  const H = 940;
  const mx = 560 + MPIN.x * H;
  const my = 70 + MPIN.y * H;
  const keys: CamKey[] = [
    {t: 0, x: 960, y: 540, z: 1.0},
    {t: 1.2, x: mx + 95, y: my - 30, z: 2.6},
    {t: 6, x: mx + 95, y: my - 30, z: 2.75},
  ];
  const cam = camAt(keys, t, 0);
  const pin = pop(t, MO_T[0], 12, 0.45);
  const ring = (t - MO_T[0]) % 1.2;
  const rep = pop(t, MO_T[1], 12, 0.5);
  return (
    <Bg t={t} zoom={false}>
      <World cam={cam}>
        <IndiaMap t={t} x={560} y={70} H={H} pins={PINS} morphine={[]} rippleAt={-5} />
        {t > MO_T[0] && ring > 0 && <div style={{position: 'absolute', left: mx - 10 - ring * 60, top: my - 30 - ring * 60, width: 20 + ring * 120, height: 20 + ring * 120, borderRadius: '50%', border: `4px solid ${P.marigold}`, opacity: 1 - ring / 1.2, boxSizing: 'border-box'}} />}
        {pin > 0.01 && (
          <svg style={{position: 'absolute', left: mx - 40, top: my - 92, overflow: 'visible'}} width={80} height={92}>
            <g transform={`translate(40 92) scale(${pin * 1.5})`}>
              <path d="M0 0 C-14 -18 -22 -30 -22 -42 A22 22 0 1 1 22 -42 C22 -30 14 -18 0 0 Z" fill={P.marigold} />
              <circle cx={0} cy={-42} r={13} fill={P.white} />
              <rect x={-9} y={-46} width={18} height={8} rx={4} fill={P.marigoldDeep} transform="rotate(-35 0 -42)" />
            </g>
          </svg>
        )}
        {rep > 0.01 && (
          <svg style={{position: 'absolute', left: mx + 30, top: my - 160, overflow: 'visible'}} width={60} height={60}>
            <g transform={`translate(30 60) scale(${rep})`}>
              <path d="M0 0 C-10 -12 -15 -20 -15 -28 A15 15 0 1 1 15 -28 C15 -20 10 -12 0 0 Z" fill={P.plum} />
              <circle cx={0} cy={-28} r={7} fill={P.white} />
            </g>
          </svg>
        )}
      </World>
      <div style={{position: 'absolute', left: 0, right: 0, top: 0, height: 250, background: 'linear-gradient(180deg, rgba(255,248,238,0.95), rgba(255,248,238,0))'}} />
      <At x={960} y={50} w={1700} align="center"><R t={t} t0={0.1} text="In India, medical morphine is hard to find" size={58} color={P.ink} weight={800} align="center" /></At>
      <At x={960} y={128} w={1700} align="center"><R t={t} t0={0.45} text="most families don't know which centre stocks it" size={40} color={P.inkSub} weight={600} align="center" /></At>
      {/* screen-space labels by the pin (pin lands at about 1110, 600 on screen) */}
      <PopAt t={t} t0={MO_T[0] + 0.1} x={1250} y={430}><Dot k="pill" d={120} bg={P.marigold} /></PopAt>
      <At x={1340} y={390} w={560}><R t={t} t0={MO_T[0] + 0.2} text="Oral morphine available here" size={56} color={P.marigoldDeep} weight={800} /></At>
      <PopAt t={t} t0={MO_T[1]} x={1250} y={690}><Dot k="people" d={120} bg={P.plum} /></PopAt>
      <At x={1340} y={640} w={560}><R t={t} t0={MO_T[1] + 0.1} text="Families can also report where they found it" size={48} color={P.plum} weight={800} /></At>
      <At x={1340} y={770} w={560}><R t={t} t0={MO_T[1] + 0.5} text="anonymously" size={36} color={P.inkSub} weight={600} /></At>
    </Bg>
  );
};

/* ---------- 6. Sarvam AI: 23 languages ---------- */
const LANGS: [string, string][] = [
  ['অসমীয়া', 'Assamese'], ['বাংলা', 'Bengali'], ['बड़ो', 'Bodo'], ['डोगरी', 'Dogri'], ['ગુજરાતી', 'Gujarati'], ['हिन्दी', 'Hindi'],
  ['ಕನ್ನಡ', 'Kannada'], ['کٲشُر', 'Kashmiri'], ['कोंकणी', 'Konkani'], ['मैथिली', 'Maithili'], ['മലയാളം', 'Malayalam'], ['মৈতৈলোন্', 'Manipuri'],
  ['मराठी', 'Marathi'], ['नेपाली', 'Nepali'], ['ଓଡ଼ିଆ', 'Odia'], ['ਪੰਜਾਬੀ', 'Punjabi'], ['संस्कृतम्', 'Sanskrit'], ['ᱥᱟᱱᱛᱟᱲᱤ', 'Santali'],
  ['سنڌي', 'Sindhi'], ['தமிழ்', 'Tamil'], ['తెలుగు', 'Telugu'], ['اردو', 'Urdu'], ['English', 'English'],
];
export const SV_T0 = 1.2;
export const SV_STEP = 0.07;
export const Sarvam23: React.FC<SP> = ({t}) => {
  const cols = [P.sageDark, P.marigoldDeep, P.rose, P.indigo, P.plum, P.sage];
  return (
    <Bg t={t}>
      <PopAt t={t} t0={0.1} x={330} y={250} f={10}>
        <Img src={staticFile('brand/sarvam.jpeg')} style={{width: 260, height: 260, borderRadius: 64, objectFit: 'cover', boxShadow: '0 30px 70px rgba(30,40,50,0.2)'}} />
      </PopAt>
      <At x={520} y={110} w={1300}><R t={t} t0={0.25} text="Sarvam AI" size={130} color={P.ink} weight={800} /></At>
      <At x={520} y={265} w={1300}><R t={t} t0={0.6} text="Speech to text in 23 languages" size={64} color={P.sageDark} weight={800} /></At>
      <At x={520} y={350} w={1300}><R t={t} t0={0.95} text="all 22 scheduled Indian languages + English" size={42} color={P.inkSub} weight={600} /></At>
      {LANGS.map(([n, e], k) => {
        const c = k % 6;
        const r = Math.floor(k / 6);
        const t0 = SV_T0 + k * SV_STEP;
        const a = pop(t, t0, 14, 0.45);
        if (a <= 0.01) return null;
        const urdu = /[؀-ۿ]/.test(n);
        return (
          <div key={k} style={{position: 'absolute', left: 170 + c * 290 - 140, top: 490 + r * 115 + (1 - Math.min(1, a)) * 40, width: 280, textAlign: 'center', opacity: clamp(a * 2), transform: `scale(${0.7 + 0.3 * a})`}}>
            <div style={{fontFamily: urdu ? "'NotoUrdu'" : FONT_BODY, fontWeight: 600, fontSize: urdu ? 40 : 48, lineHeight: urdu ? 1.3 : 1.15, color: cols[(k * 5) % 6], whiteSpace: 'nowrap'}}>{n}</div>
            <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 22, color: P.inkSub}}>{e}</div>
          </div>
        );
      })}
      <PopAt t={t} t0={SV_T0 + 23 * SV_STEP + 0.6} x={1640} y={870}>
        <div style={{display: 'flex', alignItems: 'center', gap: 14, transform: 'rotate(-4deg)'}}>
          <Glyph k="phone" s={60} c={P.sage} />
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 40, color: P.sage, whiteSpace: 'nowrap'}}>App in 11 languages</div>
        </div>
      </PopAt>
    </Bg>
  );
};

/* ---------- 7. Whisper on the phone, Sarvam fills the document ---------- */
export const WH_T = [0.3, 2.3];
export const Whisper: React.FC<SP> = ({t}) => {
  const ph = pop(t, WH_T[0], 11, 0.55);
  const typed = ramp(t, WH_T[0] + 1.0, WH_T[0] + 2.2);
  const doc = pop(t, WH_T[1], 11, 0.55);
  const fill = ramp(t, WH_T[1] + 0.5, WH_T[1] + 1.8);
  return (
    <Bg t={t}>
      {/* phone */}
      <div style={{position: 'absolute', left: 170, top: 190, width: 330, height: 660, borderRadius: 50, background: '#1B1A1F', padding: 14, boxSizing: 'border-box', transform: `translateY(${(1 - ph) * 500}px) rotate(${-3 + Math.sin(t) * 0.6}deg)`, boxShadow: '0 40px 80px rgba(10,20,30,0.3)'}}>
        <div style={{width: '100%', height: '100%', borderRadius: 38, background: P.cream, position: 'relative', overflow: 'hidden'}}>
          <div style={{position: 'absolute', left: 30, right: 30, top: 70, height: 140, display: 'flex', alignItems: 'center', gap: 6}}>
            {Array.from({length: 22}).map((_, k) => (
              <div key={k} style={{flex: 1, borderRadius: 4, background: P.sage, height: `${20 + 70 * Math.abs(Math.sin(t * 7 + k * 0.9)) * (t < WH_T[0] + 2.4 ? 1 : 0.15)}%`}} />
            ))}
          </div>
          {[0.9, 0.75, 0.85, 0.6, 0.8].map((w, k) => (
            <div key={k} style={{position: 'absolute', left: 30, top: 260 + k * 52, height: 18, borderRadius: 9, background: P.well, width: `${w * 240 * clamp(typed * 5 - k)}px`}} />
          ))}
        </div>
      </div>
      <PopAt t={t} t0={WH_T[0] + 0.8} x={470} y={215}><Dot k="offline" d={100} bg={P.indigo} /></PopAt>
      <At x={570} y={300} w={560}><R t={t} t0={WH_T[0] + 0.2} text="OpenAI Whisper" size={74} color={P.ink} weight={800} /></At>
      <At x={570} y={400} w={500}><R t={t} t0={WH_T[0] + 0.5} text="speech to text on the phone, works offline" size={42} color={P.sageDark} weight={700} /></At>
      {/* Sarvam fills the document */}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <path d="M1240 330 C 1300 260, 1400 260, 1450 330" stroke={P.marigold} strokeWidth={8} fill="none" strokeLinecap="round" strokeDasharray="4 18" opacity={ramp(t, WH_T[1] + 0.2, WH_T[1] + 0.6)} />
      </svg>
      <PopAt t={t} t0={WH_T[1] - 0.1} x={1200} y={400}><Img src={staticFile('brand/sarvam.jpeg')} style={{width: 150, height: 150, borderRadius: 40, objectFit: 'cover', boxShadow: '0 20px 40px rgba(30,40,50,0.18)'}} /></PopAt>
      <div style={{position: 'absolute', left: 1430, top: 250, width: 380, height: 480, borderRadius: 26, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.16)', transform: `rotate(3deg) scale(${doc})`, padding: '34px 36px', boxSizing: 'border-box'}}>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: P.sageDark}}>Goals of care</div>
        {[0.9, 0.7, 0.85, 0.6, 0.8, 0.7].map((w, k) => (
          <div key={k} style={{height: 14, borderRadius: 7, marginTop: 30, background: k % 2 ? P.mint : P.well, width: `${w * 100 * clamp(fill * 6 - k)}%`}} />
        ))}
      </div>
      <At x={1250} y={790} w={1000} align="center"><R t={t} t0={WH_T[1] + 0.3} text="Sarvam AI transcribes Indian languages" size={46} color={P.ink} weight={800} align="center" /></At>
      <At x={1250} y={860} w={1000} align="center"><R t={t} t0={WH_T[1] + 0.6} text="and fills the goals-of-care document" size={40} color={P.sageDark} weight={700} align="center" /></At>
    </Bg>
  );
};

/* ---------- 8. process: doctors and engineers, round and round ---------- */
export const PR_STEPS = [0.3, 1.1, 1.9, 2.7];
const MILES: [string, string][] = [
  ['1–3 Aug', 'Doctors register'],
  ['15 Aug', 'Tech teams register'],
  ['20 Aug', "Dr Sujay's goals-of-care idea"],
  ['22 Aug', 'Matchmaking'],
  ['24 Aug', 'First team meeting'],
  ['Sep', "Dr Sharada's clinical reference, Shirin's review"],
  ['20 Oct', 'Mentor review'],
  ['29 Oct', "Dr Sharada's clinical and tech specs"],
  ['3 Nov', 'Team meeting, changes built next day'],
  ['21–22 Nov', "Dr Sujay's device test and video reviews"],
  ['24 Nov', 'Screen-by-screen review with the doctors'],
  ['28 Nov', 'Grand Finale'],
];
export const PR_M0 = 3.8;
export const PR_MSTEP = 0.28;
export const PR_END = PR_M0 + 12 * PR_MSTEP + 0.3;
export const Process: React.FC<SP> = ({t}) => {
  const C = {x: 960, y: 330};
  const r = 150;
  const steps: {txt: string; c: string; k: string; ang: number; lx: number; ly: number; align: 'left' | 'center' | 'right'; w: number}[] = [
    {txt: 'Doctors write the clinical rules', c: P.sage, k: 'doc', ang: -90, lx: 960, ly: 80, align: 'center', w: 900},
    {txt: 'Engineers build it', c: P.marigoldDeep, k: 'code', ang: 0, lx: 1180, ly: 300, align: 'left', w: 380},
    {txt: 'Doctors test it on their phones and review', c: P.sage, k: 'phone', ang: 90, lx: 960, ly: 510, align: 'center', w: 1000},
    {txt: 'Engineers fix it', c: P.marigoldDeep, k: 'wrench', ang: 180, lx: 740, ly: 300, align: 'right', w: 360},
  ];
  const loopOut = 1 - ramp(t, PR_END - 0.4, PR_END);
  const lineP = easeInOutCubic(ramp(t, PR_M0 - 0.6, PR_M0 + 12 * PR_MSTEP));
  const dotA = ((t * 75 - 90) * Math.PI) / 180;
  const docs: Who[] = ['sujay', 'sharada', 'shirin'];
  const engs: Who[] = ['basith', 'awaiz'];
  const nm: Record<Who, string> = {sujay: 'Dr Sujay', sharada: 'Dr Sharada', shirin: 'Shirin', basith: 'Basith', awaiz: 'Awaiz'};
  const LY = 790;
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', inset: 0, opacity: loopOut}}>
        {/* the loop */}
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
          <circle cx={C.x} cy={C.y} r={r} fill="none" stroke={P.sand} strokeWidth={22} />
          <circle cx={C.x} cy={C.y} r={r} fill="none" stroke={P.sage} strokeWidth={22} strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - easeOutCubic(ramp(t, 0.1, PR_STEPS[3] + 0.8))} transform={`rotate(-90 ${C.x} ${C.y})`} opacity={0.5} />
          {[45, 135, 225, 315].map((a) => {
            const rad = (a * Math.PI) / 180;
            const x = C.x + Math.cos(rad) * r;
            const y = C.y + Math.sin(rad) * r;
            return <path key={a} d="M-14 -16 L12 0 L-14 16" transform={`translate(${x} ${y}) rotate(${a + 90})`} stroke={P.sageDark} strokeWidth={8} fill="none" strokeLinecap="round" strokeLinejoin="round" opacity={ramp(t, 4, 4.5)} />;
          })}
          {t > 4.2 && <circle cx={C.x + Math.cos(dotA) * r} cy={C.y + Math.sin(dotA) * r} r={20} fill={P.marigold} />}
        </svg>
        <PopAt t={t} t0={0.2} x={C.x} y={C.y}>
          <Img src={staticFile('brand/sahara-logo.png')} style={{width: 150, height: 150, objectFit: 'contain'}} />
        </PopAt>
        {steps.map((s, k) => {
          const rad = (s.ang * Math.PI) / 180;
          return (
            <React.Fragment key={k}>
              <PopAt t={t} t0={PR_STEPS[k]} x={C.x + Math.cos(rad) * r} y={C.y + Math.sin(rad) * r}><Dot k={s.k} d={96} bg={s.c} /></PopAt>
              <At x={s.lx} y={s.ly + (k === 0 ? -40 : k === 2 ? 30 : 0)} w={s.w} align={s.align}><R t={t} t0={PR_STEPS[k] + 0.1} text={s.txt} size={44} color={s.c} weight={800} align={s.align} /></At>
            </React.Fragment>
          );
        })}
        {/* the people */}
        <At x={250} y={100} w={300} align="center"><R t={t} t0={0.2} text="Clinicians" size={40} color={P.sage} weight={800} align="center" /></At>
        {docs.map((w, k) => (
          <React.Fragment key={w}>
            <Face who={w} x={170} y={210 + k * 140} d={116} s={pop(t, 0.3 + k * 0.15)} />
            <div style={{position: 'absolute', left: 245, top: 192 + k * 140, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 32, color: P.ink, opacity: ramp(t, 0.5 + k * 0.15, 0.8 + k * 0.15)}}>{nm[w]}</div>
          </React.Fragment>
        ))}
        <At x={1670} y={100} w={300} align="center"><R t={t} t0={0.6} text="Engineers" size={40} color={P.marigoldDeep} weight={800} align="center" /></At>
        {engs.map((w, k) => (
          <React.Fragment key={w}>
            <Face who={w} x={1750} y={230 + k * 160} d={130} s={pop(t, 0.7 + k * 0.15)} />
            <div style={{position: 'absolute', right: 1920 - 1670, top: 212 + k * 160, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 32, color: P.ink, textAlign: 'right', opacity: ramp(t, 0.9 + k * 0.15, 1.2 + k * 0.15)}}>{nm[w]}</div>
          </React.Fragment>
        ))}
      </div>
      {/* the timeline */}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <line x1={110} y1={LY} x2={110 + 1700 * lineP} y2={LY} stroke={P.sageDark} strokeWidth={8} strokeLinecap="round" />
      </svg>
      {MILES.map(([d, l], k) => {
        const x = 150 + k * (1620 / 11);
        const t0 = PR_M0 + k * PR_MSTEP;
        const up = k % 2 === 0;
        const a = pop(t, t0, 14, 0.5);
        if (a <= 0.01) return null;
        const fin = k === MILES.length - 1;
        return (
          <React.Fragment key={k}>
            <div style={{position: 'absolute', left: x - 13, top: LY - 13, width: 26, height: 26, borderRadius: '50%', background: fin ? P.marigold : P.white, border: `6px solid ${fin ? P.marigoldDeep : P.sageDark}`, boxSizing: 'border-box', transform: `scale(${a})`}} />
            <div style={{position: 'absolute', left: x - 140, width: 280, textAlign: 'center', top: up ? undefined : LY + 26, bottom: up ? 1080 - LY + 26 : undefined, opacity: clamp(a * 2), transform: `translateY(${(1 - Math.min(1, a)) * (up ? 30 : -30)}px)`}}>
              {!up && <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: fin ? P.marigoldDeep : P.sageDark}}>{d}</div>}
              <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, lineHeight: 1.2, color: P.ink}}>{l}</div>
              {up && <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, color: fin ? P.marigoldDeep : P.sageDark}}>{d}</div>}
            </div>
          </React.Fragment>
        );
      })}
      {/* the summary */}
      {t > PR_END - 0.2 && (
        <>
          <At x={960} y={130} w={1700} align="center"><R t={t} t0={PR_END} text="25 meetings and reviews" size={84} color={P.sageDark} weight={800} align="center" /></At>
          <At x={960} y={250} w={1700} align="center"><R t={t} t0={PR_END + 0.35} text="650+ code commits" size={84} color={P.marigoldDeep} weight={800} align="center" /></At>
        </>
      )}
    </Bg>
  );
};

/* ---------- 9. others do one piece ---------- */
export const OT_T0 = 0.4;
export const OT_Q = [2.4, 3.5];
export const Others: React.FC<SP> = ({t}) => {
  const who: [string, string, string][] = [
    ['PalaSh', 'AIIMS Bathinda', P.sage],
    ['BandhuCare', '', P.marigoldDeep],
    ['Palli Sahayak', '', P.indigo],
    ['Universal Care Plan', 'London', P.rose],
    ['POLST', 'Oregon', P.plum],
    ['NHI', 'Taiwan', P.sageDark],
  ];
  return (
    <Bg t={t}>
      <At x={960} y={50} w={1700} align="center"><R t={t} t0={0.05} text="Others do one piece" size={84} color={P.sageDark} weight={800} align="center" /></At>
      {who.map(([n, s, c], k) => {
        const x = 330 + (k % 3) * 630;
        const y = 270 + Math.floor(k / 3) * 150;
        const t0 = OT_T0 + k * 0.2;
        const a = pop(t, t0, 12, 0.5);
        if (a <= 0.01) return null;
        return (
          <div key={k} style={{position: 'absolute', left: x - 60, top: y - 50 + Math.sin(t * 1.3 + k) * 5, display: 'flex', alignItems: 'center', gap: 22, opacity: clamp(a * 2), transform: `scale(${a})`, transformOrigin: 'left center'}}>
            <div style={{transform: `rotate(${Math.sin(t * 1.1 + k) * 8}deg)`}}><Glyph k="puzzle" s={96} c={c} /></div>
            <div>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 44, color: P.ink, whiteSpace: 'nowrap'}}>{n}</div>
              {s && <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 28, color: P.inkSub}}>{s}</div>}
            </div>
          </div>
        );
      })}
      <div style={{position: 'absolute', left: 180, right: 180, top: 560, height: 4, borderRadius: 2, background: P.sand, transform: `scaleX(${easeOutQuint(ramp(t, OT_Q[0] - 0.4, OT_Q[0]))})`}} />
      <At x={960} y={610} w={1700} align="center"><R t={t} t0={OT_Q[0]} text="Palli Sahayak answers “What do I do now?”" size={54} color={P.inkSub} weight={700} align="center" stagger={0.04} /></At>
      <PopAt t={t} t0={OT_Q[1] - 0.15} x={960} y={760}><Img src={staticFile('brand/sahara-logo.png')} style={{width: 90, height: 90, objectFit: 'contain'}} /></PopAt>
      <At x={960} y={820} w={1760} align="center"><R t={t} t0={OT_Q[1]} text="Sahara answers “What did this patient decide, and who signed it?”" size={60} color={P.sageDark} weight={800} align="center" stagger={0.045} /></At>
    </Bg>
  );
};

/* ---------- 10. the 90-day pilot ---------- */
export const PI_T = [1.0, 1.9, 2.8, 3.7];
export const Pilot: React.FC<SP> = ({t}) => {
  const items: [string, string, string][] = [
    ['doc', 'Signed plans', P.sage],
    ['clock', 'Time from ER arrival to the plan', P.indigo],
    ['check', 'Care matched the plan', P.marigoldDeep],
    ['heart', 'Caregiver distress', P.rose],
  ];
  const line = easeInOutCubic(ramp(t, 0.3, 3.6));
  const day = Math.max(1, Math.round(90 * line));
  return (
    <Bg t={t}>
      <At x={960} y={50} w={1800} align="center"><R t={t} t0={0.05} text="Our key performance indicators (KPIs)" size={80} color={P.sageDark} weight={800} align="center" /></At>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <line x1={160} y1={300} x2={160 + 1600 * line} y2={300} stroke={P.sageDark} strokeWidth={10} strokeLinecap="round" />
      </svg>
      <div style={{position: 'absolute', left: 120, top: 230, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 36, color: P.inkSub}}>Day 1</div>
      <div style={{position: 'absolute', left: 140 + 1600 * line, top: 230, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 36, color: P.marigoldDeep, whiteSpace: 'nowrap'}}>Day {day}</div>
      {items.map(([k, txt, c], i) => {
        const x = 330 + i * 420;
        const t0 = PI_T[i];
        return (
          <React.Fragment key={i}>
            <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
              <line x1={x} y1={300} x2={x} y2={300 + 110 * easeOutCubic(ramp(t, t0 - 0.2, t0))} stroke={c} strokeWidth={6} strokeDasharray="2 12" strokeLinecap="round" />
            </svg>
            <PopAt t={t} t0={t0} x={x} y={480 + Math.sin(t * 1.0 + i) * 4}><Dot k={k} d={130} bg={c} /></PopAt>
            <At x={x} y={580} w={380} align="center"><R t={t} t0={t0 + 0.15} text={txt} size={42} color={P.ink} weight={700} align="center" /></At>
          </React.Fragment>
        );
      })}
    </Bg>
  );
};

/* ---------- 11. who pays ---------- */
export const PY_T = [0.4, 1.3, 2.2];
export const Pays: React.FC<SP> = ({t}) => {
  const cable = ramp(t, PY_T[1] + 0.3, PY_T[1] + 1.0);
  const nDots = Math.round(370 * easeOutCubic(ramp(t, PY_T[2] + 0.1, PY_T[2] + 1.5)));
  return (
    <Bg t={t}>
      <Floor y={560} />
      <At x={960} y={50} w={1600} align="center"><R t={t} t0={0.05} text="Who pays" size={96} color={P.sageDark} weight={800} align="center" /></At>
      {/* families: free */}
      {pop(t, PY_T[0]) > 0.01 && (
        <div style={{position: 'absolute', inset: 0, opacity: clamp(pop(t, PY_T[0]) * 2), transform: `translateY(${(1 - Math.min(1, pop(t, PY_T[0]))) * 80}px)`}}>
          <Person {...CAST.patient} t={t} x={260} y={590} s={0.62} expr="happy" />
          <Person {...CAST.daughter} t={t} x={420} y={590} s={0.64} expr="happy" flip armL={20} />
        </div>
      )}
      <PopAt t={t} t0={PY_T[0] + 0.3} x={340} y={240}><div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 110, color: P.sage, transform: 'rotate(-5deg)'}}>Free</div></PopAt>
      <At x={340} y={640} w={500} align="center"><R t={t} t0={PY_T[0] + 0.4} text="for patients and families" size={42} color={P.ink} weight={700} align="center" /></At>
      {/* hospitals plug in */}
      <PopAt t={t} t0={PY_T[1]} x={860} y={440}>
        <svg width={220} height={240} style={{overflow: 'visible'}}>
          <rect x={20} y={40} width={180} height={200} rx={10} fill={P.indigo} />
          <rect x={20} y={40} width={90} height={200} rx={10} fill="rgba(255,255,255,0.08)" />
          {[0, 1, 2].map((r) => [0, 1, 2].map((c) => <rect key={`${r}${c}`} x={45 + c * 52} y={110 + r * 42} width={30} height={26} rx={4} fill="rgba(255,255,255,0.55)" />))}
          <rect x={80} y={0} width={60} height={60} rx={10} fill={P.white} />
          <path d="M110 12 V48 M92 30 H128" stroke={P.rose} strokeWidth={10} strokeLinecap="round" />
        </svg>
      </PopAt>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        <path d="M970 500 C 1020 560, 1060 420, 1120 470" stroke={P.ink} strokeWidth={8} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - cable} />
      </svg>
      <PopAt t={t} t0={PY_T[1] + 0.9} x={1160} y={470}><Dot k="plug" d={90} bg={P.marigold} /></PopAt>
      <At x={980} y={600} w={560} align="center"><R t={t} t0={PY_T[1] + 0.3} text="Hospitals and palliative clinics" size={42} color={P.indigo} weight={800} align="center" /></At>
      <At x={980} y={710} w={520} align="center"><R t={t} t0={PY_T[1] + 0.6} text="pay to plug Sahara into their EHR" size={36} color={P.ink} weight={600} align="center" /></At>
      {/* scale: National Cancer Grid */}
      <div style={{position: 'absolute', left: 1400, top: 260, width: 420, height: 280}}>
        {Array.from({length: 370}).map((_, k) => {
          if (k >= nDots) return null;
          const c = k % 25;
          const r = Math.floor(k / 25);
          return <div key={k} style={{position: 'absolute', left: c * 16.5, top: r * 18, width: 12, height: 12, borderRadius: '50%', background: k % 7 === 0 ? P.marigold : P.sage}} />;
        })}
      </div>
      <PopAt t={t} t0={PY_T[2] + 0.2} x={1610} y={210}><div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 76, color: P.marigoldDeep, whiteSpace: 'nowrap'}}>{nDots}{nDots >= 370 ? '+' : ''} centres</div></PopAt>
      <At x={1610} y={600} w={500} align="center"><R t={t} t0={PY_T[2] + 0.4} text="scale through the National Cancer Grid" size={40} color={P.ink} weight={700} align="center" /></At>
    </Bg>
  );
};

/* ---------- 12. tagline ---------- */
export const Tagline: React.FC<SP> = ({t}) => {
  return (
    <Bg t={t}>
      <PopAt t={t} t0={0.05} x={960} y={150} f={9}><Img src={staticFile('brand/sahara-logo.png')} style={{width: 170, height: 170, objectFit: 'contain'}} /></PopAt>
      <At x={960} y={260} w={1700} align="center"><R t={t} t0={0.3} text="India's first goals-of-care plan" size={86} color={P.sageDark} weight={800} align="center" /></At>
      <At x={960} y={385} w={1500} align="center"><R t={t} t0={0.75} text="that the patient, family and doctor build together, sign, update, and carry to the emergency room" size={54} color={P.ink} weight={700} align="center" stagger={0.045} /></At>
      <At x={960} y={620} w={1700} align="center"><R t={t} t0={4.2} text="This is what we built" size={60} color={P.sageDark} weight={800} align="center" /></At>
      {(['sujay', 'sharada', 'shirin', 'basith', 'awaiz'] as const).map((w, i) => (
        <Face key={w} who={w} x={560 + i * 200} y={820} d={160} s={pop(t, 4.6 + i * 0.15, 9, 0.6)} ring={i < 3 ? P.sage : P.marigoldDeep} />
      ))}
      <div style={{position: 'absolute', left: 0, right: 0, top: 940, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 52, color: P.sageDark, opacity: ramp(t, 5.8, 6.3)}}>sahara.wedevit.in</div>
    </Bg>
  );
};

/* ---------- overlays on the original film ---------- */
/** clinician credit, top left: photo, name, credit line */
export const Credit: React.FC<{who: Who; name: string; line: string; t: number; t0: number; t1: number}> = ({who, name, line, t, t0, t1}) => {
  const a = pop(t, t0, 12, 0.55) * (1 - ramp(t, t1 - 0.3, t1));
  if (a <= 0.01) return null;
  return (
    <div style={{position: 'absolute', left: 48, top: 40, display: 'flex', alignItems: 'center', gap: 20, padding: '12px 34px 12px 12px', borderRadius: 80, background: 'rgba(255,255,255,0.94)', boxShadow: '0 12px 30px rgba(20,25,40,0.22)', transform: `scale(${a})`, transformOrigin: 'left center', opacity: clamp(a * 1.5), maxWidth: 900}}>
      <div style={{position: 'relative', width: 104, height: 104, flexShrink: 0}}><Face who={who} x={52} y={52} d={104} /></div>
      <div>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 34, color: P.ink, lineHeight: 1.1}}>{name}</div>
        <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 25, color: P.sageDark, lineHeight: 1.25, marginTop: 4}}>{line}</div>
      </div>
    </div>
  );
};
export const CommitNote: React.FC<{t: number; t0: number; t1: number}> = ({t, t0, t1}) => {
  const a = pop(t, t0, 12, 0.55) * (1 - ramp(t, t1 - 0.25, t1));
  if (a <= 0.01) return null;
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: 70, display: 'flex', justifyContent: 'center'}}>
      <div style={{padding: '18px 46px', borderRadius: 80, background: P.ink, color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 60, transform: `scale(${a})`, whiteSpace: 'nowrap', boxShadow: '0 14px 34px rgba(20,25,40,0.3)'}}>600+ code commits and changes</div>
    </div>
  );
};
export {spring};
