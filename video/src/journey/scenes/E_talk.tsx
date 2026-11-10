/* Scene E 41.0-58.8: the goals-of-care talk. Consent, recording, who said what, 13 topics,
   the app wrote the note, the doctors said no, the doctor leads, reads / edits / signs. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {at, camAt, clamp, easeInCubic, easeOutCubic, easeOutQuint, FONT_BODY, FONT_HEAD, kick, lerp, mv, P, Person, pop, ramp, Rise, squash, useGT, World} from '../kit';
import {CAST} from '../cast';
import {FgLeaves, IconCheck, Motes, Pen, PhoneSeq, Plant, toScreen} from './E_parts';

const SECOND_DOC = {skin: P.skin2, hair: P.hair, hairStyle: 'braid' as const, coat: true, top: P.coral, stetho: true, bottom: '#34405A', seed: 29};

const T = {
  everyone: at('s12', "everyone's"),
  consent: at('s12', 'consent'),
  doctor: at('s12', 'doctor'),
  records: at('s12', 'records'),
  sahara: at('s12', 'Sahara'),
  who: at('s12', 'who'),
  what: at('s12', 'what'),
  ticks: at('s12', 'ticks'),
  thirteen: at('s12', 'thirteen'),
  atFirst: at('s13', 'At'),
  write: at('s13', 'write'),
  note: at('s13', 'note'),
  doctors: at('s13', 'doctors'),
  said: at('s13', 'said'),
  no: at('s13', 'no'),
  leadsThe: at('s13', 'The', 1),
  leads: at('s13', 'leads'),
  so: at('s14', 'So'),
  nothing: at('s14', 'nothing'),
  final: at('s14', 'final'),
  reads: at('s14', 'reads'),
  edits: at('s14', 'edits'),
  signs: at('s14', 'signs'),
};
const END = 58.8;

/* ---------- room pieces ---------- */
const Window: React.FC<{x: number; y: number; w: number; h: number; t: number}> = ({x, y, w, h, t}) => {
  const sway = Math.sin(t * 0.8 + x) * 4;
  return (
    <svg style={{position: 'absolute', left: x, top: y, overflow: 'visible'}} width={w} height={h}>
      <defs>
        <linearGradient id={'sky' + x} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#F9E6C6" />
          <stop offset="1" stopColor="#DCEBDF" />
        </linearGradient>
      </defs>
      <rect x={-16} y={-16} width={w + 32} height={h + 32} rx={10} fill={P.cream} />
      <rect x={0} y={0} width={w} height={h} fill={`url(#sky${x})`} />
      <circle cx={w * 0.7} cy={h * 0.3} r={w * 0.11} fill="#FFF4DD" />
      <ellipse cx={w * 0.25} cy={h * 0.86} rx={w * 0.4} ry={h * 0.2} fill="#A9D2BC" />
      <ellipse cx={w * 0.85} cy={h * 0.9} rx={w * 0.35} ry={h * 0.18} fill="#8CC2A7" />
      <rect x={w / 2 - 6} y={0} width={12} height={h} fill={P.cream} />
      <rect x={0} y={h * 0.48} width={w} height={12} fill={P.cream} />
      <path d={`M-40 -30 L${w * 0.22} -30 Q${w * 0.18 + sway} ${h * 0.5} ${w * 0.24} ${h + 30} L-40 ${h + 30} Z`} fill={P.mint} />
      <path d={`M${w + 40} -30 L${w * 0.78} -30 Q${w * 0.82 - sway} ${h * 0.5} ${w * 0.76} ${h + 30} L${w + 40} ${h + 30} Z`} fill={P.mint} />
      <path d={`M-40 -30 L-10 -30 L-10 ${h + 30} L-40 ${h + 30} Z`} fill="rgba(30,60,50,0.15)" />
      <rect x={-60} y={-44} width={w + 120} height={14} rx={7} fill="#244C3F" />
    </svg>
  );
};

const Wall: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -1400, top: -800, width: 6800, height: 1760, background: P.sage}} />
    <div style={{position: 'absolute', left: -1400, top: 650, width: 6800, height: 300, background: P.band}} />
    <div style={{position: 'absolute', left: -1400, top: 640, width: 6800, height: 16, background: '#4B8672'}} />
    <Window x={70} y={130} w={330} h={430} t={t} />
    <Window x={3330} y={110} w={420} h={480} t={t} />
    <svg style={{position: 'absolute', left: 2380, top: 140, overflow: 'visible'}} width={10} height={10}>
      <rect x={-6} y={0} width={12} height={60} fill="#244C3F" />
      <path d="M-50 120 L-26 56 L26 56 L50 120 Z" fill={P.marigold} />
      <ellipse cx={0} cy={124} rx={150} ry={40} fill="rgba(242,179,94,0.12)" />
    </svg>
  </>
);

const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -1400, top: 880, width: 6800, height: 700, background: '#E8D6BD'}} />
    <div style={{position: 'absolute', left: -1400, top: 880, width: 6800, height: 14, background: '#D7C2A4'}} />
    {Array.from({length: 40}).map((_, i) => (
      <div key={i} style={{position: 'absolute', left: -1400 + i * 180, top: 900, width: 4, height: 400, background: 'rgba(120,90,60,0.08)', transform: 'skewX(-30deg)'}} />
    ))}
    <div style={{position: 'absolute', left: 260, top: 900, width: 1040, height: 90, borderRadius: '50%', background: '#D88A6E'}} />
    <div style={{position: 'absolute', left: 310, top: 912, width: 940, height: 66, borderRadius: '50%', background: '#E49D80'}} />
  </>
);

const ChairBack: React.FC = () => (
  <svg style={{position: 'absolute', left: 600, top: 500, overflow: 'visible'}} width={320} height={400}>
    <rect x={10} y={20} width={300} height={330} rx={70} fill={P.plum} />
    <rect x={190} y={20} width={120} height={330} rx={60} fill="rgba(30,20,40,0.14)" />
  </svg>
);
const ChairFront: React.FC = () => (
  <svg style={{position: 'absolute', left: 600, top: 500, overflow: 'visible'}} width={320} height={400}>
    <path d="M60 268 Q160 238 262 268 L270 340 L52 340 Z" fill={P.marigold} />
    <path d="M60 300 L262 300" stroke="rgba(255,255,255,0.35)" strokeWidth={6} />
    <rect x={20} y={318} width={280} height={62} rx={22} fill="#7A4C75" />
    <rect x={-14} y={200} width={74} height={180} rx={34} fill="#9E6A98" />
    <rect x={260} y={200} width={74} height={180} rx={34} fill="#9E6A98" />
    <rect x={30} y={374} width={18} height={40} rx={6} fill="#5B3956" />
    <rect x={272} y={374} width={18} height={40} rx={6} fill="#5B3956" />
  </svg>
);

const StepIcon: React.FC<{k: string}> = ({k}) => (
  <svg width={34} height={34} viewBox="-17 -17 34 34">
    {k === 'eye' && (
      <>
        <path d="M-15 0 Q0 -14 15 0 Q0 14 -15 0 Z" fill={P.cream} />
        <circle cx={0} cy={0} r={5} fill={P.sageDark} />
      </>
    )}
    {k === 'pen' && (
      <g transform="rotate(-45)">
        <rect x={-4} y={-14} width={8} height={22} rx={2} fill={P.cream} />
        <path d="M-4 8 L0 15 L4 8 Z" fill={P.cream} />
      </g>
    )}
    {k === 'sign' && <path d="M-14 6 C-8 -12 -2 -12 -4 4 C-6 16 4 -4 8 -4 C12 -4 8 8 15 4" stroke={P.cream} strokeWidth={3.5} fill="none" strokeLinecap="round" />}
  </svg>
);

const Clipboard: React.FC<{t: number}> = ({t}) => {
  const L = 2645;
  const TOP = 262;
  const W = 470;
  const H = 600;
  const appIn = pop(t, T.atFirst + 0.1, 11, 0.5);
  const appLines = [380, 360, 400, 300, 390, 370, 340, 260];
  const wStart = T.write - 0.35;
  const lineDur = (T.doctors + 0.2 - wStart) / appLines.length;
  const erase = mv(t, T.leads - 0.2, T.leads + 0.35);
  const docLines = [350, 380, 300, 390, 330, 280];
  const dStart = T.so + 0.05;
  const dDur = (T.reads - 0.15 - dStart) / docLines.length;
  const lineY = (i: number) => TOP + 150 + i * 50;
  let penX = -999;
  let penY = 0;
  for (let i = 0; i < appLines.length; i++) {
    const a = wStart + i * lineDur;
    if (t >= a && t < a + lineDur) {
      penX = L + 50 + appLines[i] * ramp(t, a, a + lineDur);
      penY = lineY(i);
    }
  }
  let dpX = -999;
  let dpY = 0;
  for (let i = 0; i < docLines.length; i++) {
    const a = dStart + i * dDur;
    if (t >= a && t < a + dDur) {
      dpX = L + 50 + docLines[i] * ramp(t, a, a + dDur);
      dpY = lineY(i);
    }
  }
  const readY = TOP + 140 + 50 * 5 * ramp(t, T.reads, T.edits - 0.1);
  const readOp = t > T.reads && t < T.edits + 0.1 ? 1 - ramp(t, T.edits - 0.15, T.edits + 0.1) : 0;
  const strike = mv(t, T.edits, T.edits + 0.25);
  const ins = mv(t, T.edits + 0.2, T.edits + 0.6);
  const sig = mv(t, T.signs, T.signs + 0.45, easeOutCubic);
  const draftOp = ramp(t, T.nothing - 0.1, T.nothing + 0.2) * (1 - ramp(t, T.signs + 0.2, T.signs + 0.45));
  const isApp = t < T.leads + 0.1;
  const hdrP = isApp ? 1 : easeOutQuint(ramp(t, T.leads + 0.1, T.leads + 0.5));
  return (
    <div style={{position: 'absolute', left: 0, top: 0, width: 1, height: 1, transformOrigin: `2880px 900px`, transform: `scale(${appIn})`}}>
      <div style={{position: 'absolute', left: L - 30, top: TOP - 40, width: W + 60, height: H + 70, borderRadius: 26, background: '#B9834A', boxShadow: '0 30px 60px rgba(10,30,20,0.3)'}} />
      <div style={{position: 'absolute', left: L + 30 + W * 0.6, top: TOP - 40, width: W * 0.4, height: H + 70, borderRadius: '0 26px 26px 0', background: 'rgba(30,20,10,0.12)'}} />
      <div style={{position: 'absolute', left: L, top: TOP, width: W, height: H, borderRadius: 10, background: P.cream}} />
      <div style={{position: 'absolute', left: 2880 - 80, top: TOP - 62, width: 160, height: 56, borderRadius: 16, background: '#8E9AA6'}} />
      <div style={{position: 'absolute', left: 2880 - 40, top: TOP - 78, width: 80, height: 30, borderRadius: 14, background: '#6F7B87'}} />
      <div style={{position: 'absolute', left: L + 40, top: TOP + 40, fontFamily: FONT_HEAD, fontSize: 40, color: P.ink, fontWeight: 600, whiteSpace: 'nowrap'}}>Care note</div>
      <div style={{position: 'absolute', left: L + W - 230, top: TOP + 44, width: 200, height: 46, overflow: 'hidden', textAlign: 'right'}}>
        <div style={{transform: `translateY(${(1 - hdrP) * 110}%)`, display: 'inline-block', padding: '5px 16px', borderRadius: 999, background: isApp ? P.indigo : P.sage, color: P.white, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, whiteSpace: 'nowrap'}}>
          {isApp ? 'by the app' : 'by the doctor'}
        </div>
      </div>
      <div style={{position: 'absolute', left: L + 40, top: TOP + 108, width: W - 80, height: 4, borderRadius: 2, background: P.sageLight}} />
      {appLines.map((w, i) => {
        const a = wStart + i * lineDur;
        const p = ramp(t, a, a + lineDur);
        const e = clamp(erase * 1.6 - i * 0.08);
        return <div key={'a' + i} style={{position: 'absolute', left: L + 50, top: lineY(i) - 7, width: w * p * (1 - e), height: 14, borderRadius: 7, background: '#8C94B8', opacity: 1 - e}} />;
      })}
      <svg style={{position: 'absolute', left: L + 50, top: 0, overflow: 'visible'}} width={10} height={10}>
        {docLines.map((w, i) => {
          const a = dStart + i * dDur;
          const p = ramp(t, a, a + dDur);
          const y = lineY(i);
          let d = `M0 ${y}`;
          for (let k = 1; k <= 12; k++) d += ` Q${(w / 12) * (k - 0.5)} ${y + (k % 2 ? -12 : 10)} ${(w / 12) * k} ${y + (k % 3) - 1}`;
          const struck = i === 2;
          let q = `M0 ${y + 26}`;
          for (let k = 1; k <= 10; k++) q += ` Q${30 * (k - 0.5)} ${y + 26 + (k % 2 ? -10 : 8)} ${30 * k} ${y + 26}`;
          return (
            <g key={'d' + i}>
              <path d={d} stroke={P.sageDark} strokeWidth={6} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p} opacity={p > 0 ? (struck ? 1 - 0.55 * strike : 1) : 0} />
              {struck && strike > 0 && <path d={`M-6 ${y} L${(w + 12) * strike} ${y}`} stroke={P.rose} strokeWidth={7} strokeLinecap="round" />}
              {struck && ins > 0 && <path d={q} stroke={P.marigoldDeep} strokeWidth={6} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - ins} />}
            </g>
          );
        })}
      </svg>
      <div style={{position: 'absolute', left: L + 34, top: readY - 6, width: W - 68, height: 40, borderRadius: 10, background: 'rgba(224,160,64,0.3)', opacity: readOp}} />
      <div style={{position: 'absolute', left: L + 70, top: TOP + 300, fontFamily: FONT_HEAD, fontSize: 120, color: 'rgba(94,82,73,0.13)', transform: 'rotate(-18deg)', opacity: draftOp, letterSpacing: 8}}>DRAFT</div>
      <div style={{position: 'absolute', left: L + 50, top: TOP + H - 70, width: 260, height: 3, background: P.inkSub, opacity: 0.4}} />
      <svg style={{position: 'absolute', left: L + 56, top: TOP + H - 150, overflow: 'visible'}} width={260} height={90}>
        <path d="M4 66 C20 10 40 10 36 52 C32 86 60 30 76 28 C92 26 78 70 98 64 C118 58 122 22 140 30 C160 40 140 70 166 60 C188 52 200 36 236 44" stroke={P.ink} strokeWidth={6} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - sig} opacity={sig > 0 ? 1 : 0} />
      </svg>
      {penX > 0 && (
        <div style={{position: 'absolute', left: penX, top: penY, transform: `rotate(-60deg)`, transformOrigin: '0 0'}}>
          <Pen s={0.9} body={P.indigo} tip="#C9CFE6" />
        </div>
      )}
      {dpX > 0 && (
        <div style={{position: 'absolute', left: dpX, top: dpY, transform: `rotate(-60deg)`, transformOrigin: '0 0'}}>
          <Pen s={0.9} body={P.sageDark} tip={P.marigold} />
        </div>
      )}
    </div>
  );
};

/* ---------- the scene ---------- */
export const Scene: React.FC = () => {
  const t = useGT();
  const cam0 = camAt(
    [
      {t: 41.0, x: 800, y: 470, z: 2.0},
      {t: 42.05, x: 950, y: 530, z: 1.0},
      {t: 43.4, x: 1000, y: 530, z: 1.04},
      {t: 46.3, x: 990, y: 530, z: 1.05},
      {t: 46.9, x: 960, y: 520, z: 1.02},
      {t: 49.3, x: 960, y: 520, z: 1.03},
      {t: 50.4, x: 2880, y: 540, z: 1.0},
      {t: 52.5, x: 2880, y: 540, z: 1.01},
      {t: 53.1, x: 2900, y: 535, z: 1.05},
      {t: 54.2, x: 2905, y: 530, z: 1.07},
      {t: 55.1, x: 2880, y: 470, z: 1.0},
      {t: 57.7, x: 2880, y: 475, z: 1.02},
      {t: 58.4, x: 2870, y: 560, z: 1.1},
    ],
    t,
    0.003,
  );
  const [kx, ky] = kick(t, T.no, 12);
  const [kx2, ky2] = kick(t, T.signs + 0.42, 5);
  const cam = {...cam0, x: cam0.x + kx + kx2, y: cam0.y + ky + ky2};

  const pm = mv(t, 49.3, 50.4);
  const phX = lerp(1580, 2300, pm) + (1 - easeOutQuint(ramp(t, T.doctor - 0.1, T.doctor + 0.6))) * 900;
  const phY = 545;
  const phRot = lerp(4, -3, pm);
  const phoneSegs = [
    {clip: 1, at: T.doctor, clipFrom: 0.4},
    {clip: 2, at: T.sahara + 0.1, clipFrom: 0.2, rate: 0.55},
    {clip: 3, at: T.ticks - 0.1, clipFrom: 0.6, rate: 0.85},
    {clip: 2, at: T.so, clipFrom: 4.6, rate: 1.1},
    {clip: 5, at: T.signs, clipFrom: 0.4},
  ];

  const heads = [
    {x: 470, y: 372, label: 'Daughter', c: P.rose},
    {x: 760, y: 392, label: 'Patient', c: P.indigo},
    {x: 1130, y: 372, label: 'Doctor', c: P.sageDark},
  ];
  const nod = (i: number) => {
    const a = T.consent + i * 0.12;
    const x = t - a;
    return x > 0 && x < 1.1 ? Math.sin(x * 11) * 5 * (1 - x / 1.1) : 0;
  };
  const famOut = mv(t, 49.3, 50.4);

  const recIn = pop(t, T.records - 0.05, 12, 0.5);
  const toDots = mv(t, T.ticks - 0.1, T.ticks + 0.25);
  const rowOut = mv(t, 49.2, 49.7);

  const shake = (seed: number) => {
    const a = T.doctors + 0.25 + seed * 0.08;
    const x = t - a;
    return x > 0 && x < 1.0 ? Math.sin(x * 20) * 11 * (1 - x) : 0;
  };
  const step = mv(t, T.leadsThe - 0.05, T.leads + 0.3, easeOutCubic);
  const antic = t > T.leadsThe - 0.25 && t < T.leadsThe + 0.05 ? -4 * Math.sin(ramp(t, T.leadsThe - 0.25, T.leadsThe + 0.05) * Math.PI) : 0;
  const docX = 3365 - 85 * step;
  const leadArm = mv(t, T.leadsThe, T.leads + 0.2);

  const [ssx, ssy] = squash(t, T.no - 0.05);
  const stampDrop = 1 + 1.4 * (1 - easeOutQuint(ramp(t, T.no - 0.05, T.no + 0.12)));
  const stampOut = mv(t, T.leads - 0.1, T.leads + 0.3);

  const steps = [
    {k: 'Reads', t: T.reads, ic: 'eye'},
    {k: 'Edits', t: T.edits, ic: 'pen'},
    {k: 'Signs', t: T.signs, ic: 'sign'},
  ];

  const lockIn = pop(t, T.signs + 0.25, 13, 0.45);
  const shackle = 1 - easeOutQuint(ramp(t, T.signs + 0.38, T.signs + 0.48));
  const lockW = [3160, 790] as const;
  const [lsx, lsy] = toScreen(cam, lockW[0], lockW[1]);
  const wipe = easeInCubic(ramp(t, END - 0.4, END - 0.02));

  const dataFlow = clamp(ramp(t, T.write - 0.5, T.write - 0.2)) * (1 - ramp(t, T.doctors + 0.1, T.doctors + 0.4));

  return (
    <AbsoluteFill style={{background: P.sage, overflow: 'hidden'}}>
      <World cam={cam} depth={0.85}>
        <Wall t={t} />
      </World>
      <World cam={cam} depth={0.92}>
        <Motes t={t} n={34} x0={-200} w={4400} h={900} color="rgba(255,248,238,0.35)" seed={3} />
      </World>
      <World cam={cam}>
        <Floor />
        <Plant x={130} y={920} s={1.1} t={t} />
        <Plant x={3960} y={920} s={1.0} t={t} pot={P.marigold} />

        {/* section 1: the family and the doctor */}
        <div style={{position: 'absolute', left: 0, top: 0, width: 1, height: 1, transform: `translateX(${-famOut * 260}px)`}}>
          <ChairBack />
          <Person {...CAST.patient} t={t} x={760} y={820} s={1.15} sitting expr={t > T.consent ? 'happy' : 'neutral'} look={0.5} headTilt={nod(1) + 3} talk={t > T.who && t < T.what + 0.4} />
          <ChairFront />
          <Person
            {...CAST.daughter}
            t={t}
            x={470}
            y={935}
            s={1.2}
            look={0.6}
            expr={t > T.consent ? 'happy' : 'neutral'}
            headTilt={nod(0) + 4}
            armR={28}
            elbowR={-30}
            talk={t > T.what + 0.1 && t < T.ticks + 0.6}
          />
          <Person
            {...CAST.teamDoctor}
            t={t}
            x={1130}
            y={935}
            s={1.2}
            flip
            look={0.6}
            expr={t > T.consent + 0.2 ? 'happy' : 'talk'}
            headTilt={nod(2) - 2}
            talk={(t > 41.1 && t < T.consent) || (t > T.sahara && t < T.who + 0.1)}
            armR={lerp(10, 48, mv(t, T.doctor, T.records))}
            elbowR={lerp(10, 70, mv(t, T.doctor, T.records))}
            hold={
              <g transform="rotate(-10)">
                <rect x={-18} y={-58} width={36} height={70} rx={7} fill="#1B1A1F" />
                <rect x={-14} y={-54} width={28} height={62} rx={5} fill={P.cream} />
                <circle cx={0} cy={-14} r={7} fill={P.rose} opacity={t > T.records ? 0.6 + 0.4 * Math.sin(t * 8) : 0} />
              </g>
            }
          />
          {heads.map((h, i) => {
            const pIn = pop(t, T.everyone + i * 0.13, 13, 0.45);
            const out = ramp(t, T.sahara - 0.1, T.sahara + 0.15);
            const chipIn = pop(t, T.who + i * 0.16, 13, 0.45);
            const fly = mv(t, T.what + 0.25 + i * 0.1, T.what + 0.75 + i * 0.1, easeInCubic);
            const tx = 1580 - h.x;
            const ty = 330 + i * 70 - h.y;
            const arc = Math.sin(fly * Math.PI) * -120;
            return (
              <React.Fragment key={i}>
                {pIn > 0 && out < 1 && (
                  <div style={{position: 'absolute', left: h.x - 34, top: h.y - 34, width: 68, height: 68, borderRadius: '50%', background: P.cream, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${pIn * (1 - out)})`, boxShadow: '0 8px 18px rgba(10,30,20,0.25)'}}>
                    <IconCheck s={44} color={P.sage} p={ramp(t, T.everyone + i * 0.13 + 0.1, T.everyone + i * 0.13 + 0.4)} />
                  </div>
                )}
                {chipIn > 0 && fly < 1 && (
                  <div
                    style={{
                      position: 'absolute',
                      left: h.x,
                      top: h.y,
                      transform: `translate(-50%,-50%) translate(${tx * fly}px, ${ty * fly + arc}px) scale(${chipIn * (1 - 0.55 * fly)})`,
                      padding: '10px 26px',
                      borderRadius: 999,
                      background: h.c,
                      color: P.white,
                      fontFamily: FONT_HEAD,
                      fontWeight: 600,
                      fontSize: 34,
                      whiteSpace: 'nowrap',
                      boxShadow: '0 8px 18px rgba(10,30,20,0.25)',
                    }}
                  >
                    {h.label}
                  </div>
                )}
              </React.Fragment>
            );
          })}
          {/* record row: waveform, then 13 topic dots */}
          <div style={{position: 'absolute', left: 470, top: 150, width: 860, height: 96, transformOrigin: '0 50%', transform: `scale(${recIn})`, opacity: 1 - rowOut}}>
            <div style={{position: 'absolute', left: 0, top: 0, width: 860, height: 96, borderRadius: 48, background: 'rgba(28,62,50,0.55)'}} />
            <div style={{position: 'absolute', left: 24, top: 24, width: 48, height: 48, borderRadius: '50%', background: P.rose, transform: `scale(${1 + 0.12 * Math.sin(t * 7)})`, opacity: 1 - toDots}} />
            <div style={{position: 'absolute', left: 24, top: 24, width: 48, height: 48, borderRadius: '50%', border: `6px solid ${P.rose}`, boxSizing: 'border-box', transform: `scale(${1 + ((t * 1.4) % 1) * 0.9})`, opacity: (1 - ((t * 1.4) % 1)) * (1 - toDots)}} />
            {Array.from({length: 26}).map((_, i) => {
              const hgt = (18 + 46 * Math.abs(Math.sin(t * 6.2 + i * 0.9) * Math.sin(t * 2.3 + i * 0.37))) * (1 - toDots);
              const grow = clamp((t - T.records - i * 0.02) / 0.25);
              return <div key={i} style={{position: 'absolute', left: 104 + i * 28, top: 48 - (hgt * grow) / 2, width: 12, height: hgt * grow, borderRadius: 6, background: P.cream, opacity: 1 - toDots}} />;
            })}
            {Array.from({length: 13}).map((_, i) => {
              const dIn = pop(t, T.ticks + i * 0.025, 14, 0.5);
              const tickT = T.ticks + 0.2 + i * 0.165;
              const on = pop(t, tickT, 16, 0.5);
              return (
                <div key={'d' + i} style={{position: 'absolute', left: 32 + i * 61, top: 18, width: 60, height: 60, transform: `scale(${dIn})`}}>
                  <div style={{position: 'absolute', inset: 4, borderRadius: '50%', background: 'rgba(255,248,238,0.25)'}} />
                  <div style={{position: 'absolute', inset: 4, borderRadius: '50%', background: P.marigold, transform: `scale(${on})`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                    <IconCheck s={34} color={P.white} p={ramp(t, tickT + 0.05, tickT + 0.2)} />
                  </div>
                </div>
              );
            })}
          </div>
          <div style={{position: 'absolute', left: 482, top: 262, width: 960, opacity: 1 - rowOut}}>
            <Rise t={t} t0={T.thirteen} text="13 topics, ticked as they come up" size={44} color={P.cream} align="left" stagger={0.09} />
          </div>
          <div style={{position: 'absolute', left: 482, top: 262, width: 960, opacity: 1 - ramp(t, T.ticks - 0.3, T.ticks - 0.1)}}>
            <Rise t={t} t0={T.records + 0.2} text="Recording, with consent" size={44} color={P.cream} align="left" stagger={0.09} />
          </div>
        </div>

        {/* section 2-3: the note and the doctors */}
        <Clipboard t={t} />
        {dataFlow > 0 && (
          <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: dataFlow}} width={10} height={10}>
            <path d="M2480 380 Q2560 290 2640 360" stroke={P.cream} strokeWidth={7} strokeDasharray="2 18" strokeLinecap="round" fill="none" strokeDashoffset={-t * 60} />
          </svg>
        )}
        {t >= T.no - 0.05 && stampOut < 1 && (
          <div style={{position: 'absolute', left: 2880, top: 560, transform: `translate(-50%,-50%) translateY(${-stampOut * 80}px) rotate(-12deg) scale(${ssx * stampDrop}, ${ssy * stampDrop})`, opacity: 1 - stampOut}}>
            <div style={{padding: '14px 70px 22px', borderRadius: 30, background: P.rose, color: P.cream, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 150, lineHeight: 1, boxShadow: '0 16px 30px rgba(80,20,40,0.3)'}}>No</div>
          </div>
        )}
        <Person
          {...SECOND_DOC}
          t={t}
          x={3560}
          y={935}
          s={1.15}
          flip
          look={0.8}
          expr={t < T.doctors ? 'think' : t < T.leads ? 'neutral' : 'happy'}
          headTilt={shake(1)}
          armR={t > T.leads + 0.3 ? 20 : 8}
        />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={docX}
          y={935}
          s={1.2}
          flip
          look={0.8}
          lean={antic + step * 3}
          expr={t < T.doctors ? 'think' : t < T.leadsThe ? 'neutral' : 'happy'}
          headTilt={shake(0)}
          armR={lerp(12, 82, leadArm)}
          elbowR={lerp(0, 40, leadArm)}
          hold={
            leadArm > 0.3 ? (
              <g transform="rotate(-70) translate(-20 -10)">
                <rect x={0} y={-8} width={90} height={16} rx={8} fill={P.sageDark} />
                <path d="M0 -8 L-16 0 L0 8 Z" fill={P.marigold} />
              </g>
            ) : undefined
          }
        />
        {steps.map((s, i) => {
          const sIn = pop(t, T.so + i * 0.1, 12, 0.5);
          const on = pop(t, s.t, 15, 0.45);
          const x = 2560 + i * 320;
          return (
            <div key={s.k} style={{position: 'absolute', left: x - 130, top: 100, width: 260, height: 74, transform: `scale(${sIn})`}}>
              {i > 0 && <div style={{position: 'absolute', left: -60, top: 33, width: 60, height: 8, borderRadius: 4, background: on > 0.5 ? P.marigold : 'rgba(255,248,238,0.35)'}} />}
              <div style={{position: 'absolute', inset: 0, borderRadius: 999, background: 'rgba(28,62,50,0.6)'}} />
              <div style={{position: 'absolute', inset: 0, borderRadius: 999, background: P.cream, opacity: clamp(on * 1.6), transform: `scale(${0.85 + 0.15 * on})`}} />
              <div style={{position: 'absolute', left: 10, top: 10, width: 54, height: 54, borderRadius: '50%', background: on > 0.4 ? P.sage : 'rgba(255,248,238,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                <StepIcon k={s.ic} />
              </div>
              <div style={{position: 'absolute', left: 82, top: 12, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 40, color: on > 0.4 ? P.sageDark : P.cream}}>{s.k}</div>
            </div>
          );
        })}
        {lockIn > 0 && (
          <div style={{position: 'absolute', left: lockW[0] - 50, top: lockW[1] - 60, width: 100, height: 110, transform: `scale(${lockIn})`}}>
            <svg width={100} height={110} viewBox="0 0 100 110" style={{overflow: 'visible'}}>
              <circle cx={50} cy={62} r={50} fill={P.sage} />
              <path d={`M34 ${56 - shackle * 14} L34 ${38 - shackle * 14} Q34 ${22 - shackle * 14} 50 ${22 - shackle * 14} Q66 ${22 - shackle * 14} 66 ${38 - shackle * 14} L66 ${48 - shackle * 14}`} stroke={P.cream} strokeWidth={8} fill="none" strokeLinecap="round" />
              <rect x={26} y={52} width={48} height={36} rx={8} fill={P.cream} />
              <circle cx={50} cy={68} r={5} fill={P.sage} />
            </svg>
          </div>
        )}

        {/* the phone */}
        <PhoneSeq t={t} segs={phoneSegs} x={phX} y={phY} h={800} rot={phRot} noteColor="rgba(255,248,238,0.6)" />
        <div style={{position: 'absolute', left: phX - 95, top: 74, transform: `rotate(${phRot}deg) scale(${pop(t, T.records, 13, 0.45) * (1 - ramp(t, T.ticks - 0.2, T.ticks))})`}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '8px 22px', borderRadius: 999, background: P.cream, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 30, color: P.ink, whiteSpace: 'nowrap'}}>
            <div style={{width: 18, height: 18, borderRadius: '50%', background: P.rose, opacity: 0.55 + 0.45 * Math.sin(t * 8)}} />
            Recording
          </div>
        </div>
      </World>
      <World cam={cam} depth={1.25}>
        <FgLeaves x={-120} y={1180} s={1.1} t={t} />
        <FgLeaves x={4320} y={1200} s={1.2} t={t} flip />
      </World>
      {wipe > 0 && <div style={{position: 'absolute', left: lsx - 2400 * wipe, top: lsy - 2400 * wipe, width: 4800 * wipe, height: 4800 * wipe, borderRadius: '50%', background: P.cream}} />}
    </AbsoluteFill>
  );
};
