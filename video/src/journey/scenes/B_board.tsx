/* B part 1: Sahara blooms out of the night, the five of us pop in as photo cards, then each pair gets
   its own corner of a long studio wall: Basith and Awaiz at a desk, Dr Sujay and Dr Sharada at an X-ray
   lightbox, Shirin with her books. Each photo card sits above the flat character who stands for that person. */
import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';
import {P, FONT_HEAD, at, camAt, World, Person, Rise, CamKey, clamp, lerp, ramp, mv, pop, spring, back, easeOutCubic, easeInCubic} from '../kit';
import {CAST} from '../cast';
import {Who, ORDER} from '../team';
import {PhotoCard, Sparkle, Motes, LaptopBack, Desk, Plant, FgLeaves, Center, HangingSign, PALE, TONE} from './B_parts';

/* ---------- beats ---------- */
export const TB = {
  sahara: at('s04', 'Sahara'),
  started: at('s04', 'started'),
  with: at('s04', 'With'),
  five: at('s04', 'five'),
  us: at('s04', 'us'),
  basith: at('s04a', 'Basith'),
  awaiz: at('s04a', 'Awaiz'),
  run: at('s04a', 'run'),
  small: at('s04a', 'small'),
  software: at('s04a', 'software'),
  company: at('s04a', 'company'),
  dr: at('s04b', 'Dr'),
  sujay: at('s04b', 'Sujay'),
  sharada: at('s04b', 'Sharada'),
  lung: at('s04b', 'lung'),
  doctors: at('s04b', 'doctors'),
  shirin: at('s04b', 'Shirin'),
  medical: at('s04b', 'medical'),
  student: at('s04b', 'student'),
  back: at('s05', 'Back'),
};

/* ---------- layout (world units) ---------- */
const FLOOR = 900;
const CARD_Y = 268;
const CARD_D = 250;
const NAME_Y = 404;
const DOC_C = 2880;
const STU_C = 4800;
const SPOT: Record<Who, {x: number; y: number}> = {
  basith: {x: 560, y: CARD_Y},
  awaiz: {x: 1360, y: CARD_Y},
  sujay: {x: DOC_C - 400, y: CARD_Y},
  sharada: {x: DOC_C + 400, y: CARD_Y},
  shirin: {x: STU_C, y: CARD_Y},
};
const ROLE: Record<Who, string> = {
  basith: 'Technical lead · WeDevIT',
  awaiz: 'Android engineer · WeDevIT',
  sujay: 'IISc, Bengaluru',
  sharada: 'AIIMS Mangalagiri',
  shirin: 'Final-year medical student',
};
const NAME: Record<Who, string> = {basith: 'Basith', awaiz: 'Awaiz', sujay: 'Dr Sujay', sharada: 'Dr Sharada', shirin: 'Shirin'};
/** when each person is named (their card comes into focus) */
const FOCUS: Record<Who, number> = {basith: TB.basith, awaiz: TB.awaiz, sujay: TB.sujay, sharada: TB.sharada, shirin: TB.shirin};

const ROW_Y = 600;
const ROW_D = 230;
const rowX = (i: number) => 960 + (i - 2) * 300;
const popT = (i: number) => TB.five + 0.02 + i * 0.1;

/* ---------- camera ---------- */
const KEYS: CamKey[] = [
  {t: 10.51, x: 960, y: 520, z: 1.42},
  {t: TB.with - 0.3, x: 960, y: 515, z: 1.48},
  {t: TB.five + 0.25, x: 960, y: 545, z: 1.0},
  {t: TB.basith - 0.05, x: 960, y: 548, z: 1.02},
  {t: TB.basith + 0.6, x: 960, y: 548, z: 1.08},
  {t: TB.run, x: 960, y: 560, z: 1.08},
  {t: TB.company + 0.5, x: 960, y: 548, z: 1.14},
  {t: TB.dr - 0.08, x: 960, y: 548, z: 1.15},
  {t: TB.dr + 0.62, x: DOC_C, y: 548, z: 1.06},
  {t: TB.lung - 0.1, x: DOC_C, y: 552, z: 1.1},
  {t: TB.lung + 0.4, x: DOC_C, y: 560, z: 1.14},
  {t: TB.shirin - 0.15, x: DOC_C + 40, y: 560, z: 1.15},
  {t: TB.shirin + 0.5, x: STU_C, y: 548, z: 1.1},
  {t: 22.6, x: STU_C, y: 545, z: 1.16},
];
export const boardCam = (t: number) => {
  const c = camAt(KEYS, t, 0.004);
  c.x += Math.sin(t * 0.33) * 6;
  c.y += Math.cos(t * 0.29) * 4;
  return c;
};

/* ---------- card state over time ---------- */
const cardState = (who: Who, i: number, t: number) => {
  const p0 = popT(i);
  const appear = pop(t, p0, 15, 0.38);
  const drop = (1 - Math.min(1, spring(t, p0, 13, 0.5))) * -70;
  const rx = rowX(i);
  const sp = SPOT[who];
  const f = FOCUS[who];
  const isEng = who === 'basith' || who === 'awaiz';
  // engineers move to their spot on their own name; the others leave together on "Basith"
  const t0 = isEng ? TB.basith - 0.06 + i * 0.1 : TB.basith + 0.04 + (i - 2) * 0.07;
  let x = rx;
  let y = ROW_Y + drop;
  let d = ROW_D;
  let rot = (1 - Math.min(1, appear)) * (i % 2 ? 14 : -14) + Math.sin(t * 1.4 + i) * 1.4;
  if (isEng) {
    const m = spring(t, t0, 9, 0.55);
    x = lerp(rx, sp.x, m);
    y = lerp(y, sp.y, m);
    d = lerp(ROW_D, CARD_D, m);
  } else {
    // anticipation dip, then a quick arc to the right
    const ant = mv(t, t0 - 0.12, t0) * (1 - ramp(t, t0, t0 + 0.1));
    const m = easeInCubic(ramp(t, t0, t0 + 0.55));
    const land = spring(t, t0 + 0.55, 12, 0.5);
    x = lerp(rx, sp.x, t < t0 + 0.55 ? m : 1) - 18 * ant;
    y = lerp(y, sp.y, t < t0 + 0.55 ? m : 1) + 14 * ant - Math.sin(Math.min(1, m) * Math.PI) * 120;
    d = ROW_D * (t < t0 + 0.55 ? lerp(1, 1.0, m) : 1);
    rot += t > t0 && t < t0 + 0.7 ? 10 * Math.sin(m * Math.PI) * (1 - land) : 0;
  }
  // focus: a bump, colour comes in
  const fb = spring(t, f, 11, 0.38);
  const pre = who === 'basith' ? 1 : t < f ? lerp(1, 0.86, ramp(t, TB.basith, TB.basith + 0.4)) : lerp(0.86, 1, fb);
  const bump = t > f ? 1 + Math.exp(-(t - f) * 5) * Math.sin((t - f) * 16) * 0.08 : 1;
  const gray = who === 'basith' || t < TB.basith ? 0 : clamp(1 - ramp(t, f - 0.02, f + 0.25)) * clamp(ramp(t, TB.basith, TB.basith + 0.4)) * 0.85;
  return {x, y, d, s: Math.max(0, appear) * pre * bump, rot, gray};
};

/* ---------- background ---------- */
const Wall: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -2400, top: -1600, width: 9800, height: 3600, background: P.creamWarm}} />
    {/* soft wainscot */}
    <div style={{position: 'absolute', left: -2400, top: 700, width: 9800, height: 400, background: '#F4E6D2'}} />
    <div style={{position: 'absolute', left: -2400, top: 694, width: 9800, height: 10, background: '#EAD8BF'}} />
    {/* framed doodles and shelves between sections */}
    <Frame x={-160} y={250} w={170} h={210} c={P.sage} t={t} k={0} />
    <Frame x={2020} y={180} w={150} h={150} c={P.rose} t={t} k={1} />
    <Frame x={3740} y={250} w={170} h={210} c={P.marigold} t={t} k={2} />
    <Frame x={5660} y={200} w={160} h={190} c={P.indigo} t={t} k={3} />
    <Shelf x={1840} y={520} t={t} />
    <Shelf x={3700} y={560} t={t} />
  </>
);
/** painted arches grow behind each person on their name (depth 1, so they stay behind the cards) */
const Arches: React.FC<{t: number}> = ({t}) => (
  <>
    {ORDER.map((w) => {
      const g = spring(t, FOCUS[w] - 0.1, 10, 0.55);
      const sp = SPOT[w];
      const h = 800 * g;
      return <div key={w} style={{position: 'absolute', left: sp.x - 230, top: FLOOR - h, width: 460, height: h, borderRadius: '230px 230px 0 0', background: PALE[w]}} />;
    })}
  </>
);
const rampT = (t: number, t0: number) => Math.max(0, t - t0);
const Frame: React.FC<{x: number; y: number; w: number; h: number; c: string; t: number; k: number}> = ({x, y, w, h, c, t, k}) => (
  <div style={{position: 'absolute', left: x - w / 2, top: y - h / 2, width: w, height: h, borderRadius: 10, background: '#C99A62', padding: 10, boxSizing: 'border-box', transform: `rotate(${(k % 2 ? 2 : -2) + Math.sin(t * 0.7 + k) * 0.6}deg)`}}>
    <div style={{width: '100%', height: '100%', borderRadius: 4, background: P.cream, position: 'relative', overflow: 'hidden'}}>
      <div style={{position: 'absolute', left: '18%', top: '30%', width: '64%', height: '64%', borderRadius: '50% 50% 0 0', background: c}} />
      <div style={{position: 'absolute', left: '58%', top: '14%', width: '22%', height: '18%', borderRadius: '50%', background: P.marigold}} />
    </div>
  </div>
);
const Shelf: React.FC<{x: number; y: number; t: number}> = ({x, y, t}) => (
  <>
    <div style={{position: 'absolute', left: x - 150, top: y, width: 300, height: 18, borderRadius: 6, background: '#C99A62'}} />
    {[
      [-130, 40, 120, P.indigo],
      [-86, 34, 100, P.rose],
      [-48, 44, 132, P.marigold],
    ].map(([dx, w, h, c], i) => (
      <div key={i} style={{position: 'absolute', left: x + (dx as number), top: y - (h as number), width: w as number, height: h as number, borderRadius: 5, background: c as string}} />
    ))}
    <Plant x={x + 80} y={y + 4} s={0.42} t={t} seed={x} />
  </>
);
const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -2400, top: FLOOR - 4, width: 9800, height: 1200, background: '#E2CDB0'}} />
    <div style={{position: 'absolute', left: -2400, top: FLOOR - 14, width: 9800, height: 14, background: '#D8BF9C'}} />
    {[960, 1040].map((y, i) => (
      <div key={y} style={{position: 'absolute', left: -2400, top: y, width: 9800, height: 3 + i, background: 'rgba(160,120,80,0.16)'}} />
    ))}
  </>
);

/* ---------- props ---------- */
/** wall-mounted X-ray lightbox with flat lungs that breathe */
const Lightbox: React.FC<{x: number; y: number; t: number}> = ({x, y, t}) => {
  const inS = pop(t, TB.dr + 0.3, 12, 0.5);
  const on = mv(t, TB.lung - 0.04, TB.lung + 0.2);
  const flick = t > TB.lung && t < TB.lung + 0.25 ? (Math.sin((t - TB.lung) * 80) > 0 ? 1 : 0.55) : 1;
  const br = 1 + 0.05 * Math.sin((t - TB.lung) * 3.4) * on;
  const w = 330;
  const h = 260;
  if (inS <= 0.001) return null;
  return (
    <div style={{position: 'absolute', left: x - w / 2, top: y - h / 2, width: w, height: h, transform: `scale(${inS * 1.22})`, transformOrigin: '50% 100%'}}>
      {on > 0 && <div style={{position: 'absolute', left: -180, top: -170, width: w + 360, height: h + 340, borderRadius: '50%', background: `radial-gradient(ellipse at center, rgba(200,236,255,${0.55 * on * flick}) 0%, rgba(200,236,255,0) 60%)`}} />}
      <div style={{position: 'absolute', inset: 0, borderRadius: 18, background: '#5E6E80', boxShadow: 'inset 0 -10px 0 #4A586A'}} />
      <div style={{position: 'absolute', left: 16, top: 16, right: 16, bottom: 26, borderRadius: 8, background: on > 0 ? `rgb(${lerp(46, 214, on * flick)},${lerp(58, 236, on * flick)},${lerp(72, 246, on * flick)})` : '#2E3A48', overflow: 'hidden'}}>
        <svg width={w - 32} height={h - 42} viewBox="0 0 298 218" style={{position: 'absolute', left: 0, top: 0}}>
          <g transform={`translate(149 112) scale(${br}) translate(-149 -112)`} opacity={0.25 + 0.75 * on}>
            <path d="M138 26 L138 84 Q 128 98 112 104 M160 26 L160 84 Q 170 98 186 104" stroke={on > 0.5 ? '#5E7C95' : '#6A7A8E'} strokeWidth={10} fill="none" strokeLinecap="round" />
            <path d="M124 70 Q 72 64 60 128 Q 52 182 86 192 Q 126 196 132 150 Z" fill={on > 0.5 ? P.rose : '#7A8698'} />
            <path d="M174 70 Q 226 64 238 128 Q 246 182 212 192 Q 172 196 166 150 Z" fill={on > 0.5 ? P.rose : '#7A8698'} />
            <path d="M200 120 Q 218 150 206 176" stroke="rgba(255,255,255,0.45)" strokeWidth={6} fill="none" strokeLinecap="round" />
            <path d="M96 120 Q 82 150 92 176" stroke="rgba(255,255,255,0.45)" strokeWidth={6} fill="none" strokeLinecap="round" />
          </g>
        </svg>
      </div>
      <div style={{position: 'absolute', left: w / 2 - 14, bottom: 6, width: 28, height: 12, borderRadius: 6, background: on > 0.5 ? P.monitor : '#3A4656'}} />
    </div>
  );
};

/** a stool with a stack of textbooks */
const Books: React.FC<{x: number; t: number}> = ({x, t}) => {
  const books: [number, number, string, number][] = [
    [0, 200, P.indigo, -2],
    [1, 180, P.rose, 3],
    [2, 196, P.sage, -1],
    [3, 160, P.marigold, 4],
  ];
  return (
    <>
      <div style={{position: 'absolute', left: x - 110, top: 740, width: 220, height: 26, borderRadius: 10, background: '#B37C46'}} />
      {[x - 90, x + 62].map((lx) => (
        <div key={lx} style={{position: 'absolute', left: lx, top: 762, width: 28, height: FLOOR - 762, background: '#9C6A3B'}} />
      ))}
      {books.map(([k, w, c, r]) => {
        const s = pop(t, TB.medical + k * 0.08, 14, 0.42);
        if (s <= 0.001) return null;
        const yy = 740 - (k + 1) * 42;
        return (
          <div key={k} style={{position: 'absolute', left: x - w / 2, top: yy, width: w, height: 40, borderRadius: 6, background: c, transform: `rotate(${r}deg) scale(${s})`, transformOrigin: '50% 100%', boxShadow: 'inset 0 -8px 0 rgba(0,0,0,0.14)'}}>
            <div style={{position: 'absolute', left: 14, top: 8, width: w - 28, height: 18, borderRadius: 3, background: 'rgba(255,248,238,0.85)'}} />
          </div>
        );
      })}
    </>
  );
};

/** an open book held in the hand */
const OpenBook: React.FC = () => (
  <g transform="rotate(-12) translate(-10 -40)">
    <path d="M0 0 Q -40 -14 -78 -4 L -78 54 Q -40 44 0 58 Z" fill={P.cream} />
    <path d="M0 0 Q 40 -14 78 -4 L 78 54 Q 40 44 0 58 Z" fill="#F1E4D0" />
    <path d="M-66 10 L -14 16 M -66 24 L -14 30 M 14 16 L 66 10 M 14 30 L 66 24" stroke="#C9B9A4" strokeWidth={4} strokeLinecap="round" />
    <path d="M-80 54 Q -40 46 0 60 Q 40 46 80 54 L 80 62 Q 40 54 0 68 Q -40 54 -80 62 Z" fill={P.plum} />
  </g>
);

/** code glyph chips floating up from the laptops on "software" */
const CodeChips: React.FC<{t: number; x: number; k: number}> = ({t, x, k}) => {
  const chips = ['</>', '{ }', '01'];
  return (
    <>
      {chips.map((c, j) => {
        const t0 = TB.software + j * 0.12 + k * 0.06;
        const dt = t - t0;
        if (dt < 0 || dt > 3.4) return null;
        const side = j === 1 ? (k ? 1 : -1) : j === 0 ? -1 : 1;
        const s = pop(t, t0, 13, 0.42) * (1 - ramp(dt, 2.9, 3.4));
        const px = x + side * (150 + j * 18) + Math.sin(dt * 2 + j) * 8;
        const py = 700 - 70 * easeOutCubic(dt / 1.2) - dt * 10;
        const col = [P.marigold, P.mint, P.rose][j];
        return (
          <div key={j} style={{position: 'absolute', left: px - 50, top: py - 30, width: 100, height: 60, borderRadius: 16, background: P.white, boxShadow: `inset 0 -6px 0 ${col}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 30, color: P.ink, transform: `scale(${s}) rotate(${side * 6}deg)`}}>
            {c}
          </div>
        );
      })}
    </>
  );
};

/* ---------- people under the cards ---------- */
const People: React.FC<{t: number}> = ({t}) => {
  // engineers rise from behind the desk on "run"
  const eA = spring(t, TB.run - 0.04, 11, 0.5);
  const eB = spring(t, TB.run + 0.1, 11, 0.5);
  const typ = (k: number) => (t > TB.software ? Math.sin(t * 20 + k) * 4 : 0);
  const glow = mv(t, TB.software - 0.05, TB.software + 0.25) * (1 + 0.5 * Math.exp(-Math.max(0, t - TB.software) * 3)) * (0.9 + 0.1 * Math.sin(t * 7));
  // doctors pop up from the floor on their names
  const dS = pop(t, TB.sujay + 0.22, 13, 0.45);
  const dH = pop(t, TB.sharada + 0.22, 13, 0.45);
  const sq = (t0: number) => (t > t0 ? 1 - Math.exp(-(t - t0) * 7) * Math.sin((t - t0) * 20) * 0.08 : 1);
  const lungOn = t > TB.lung;
  const st = pop(t, TB.shirin + 0.25, 13, 0.45);
  return (
    <>
      {/* engineers' desk */}
      {[0, 1].map((k) => {
        const e = k ? eB : eA;
        const x = k ? SPOT.awaiz.x : SPOT.basith.x;
        if (e <= 0.001) return null;
        return (
          <div key={k} style={{position: 'absolute', left: x - 300, top: 0, width: 600, height: 896, overflow: 'hidden'}}>
           <div style={{position: 'absolute', left: 300 - x, top: 0}}>
            <div style={{position: 'absolute', left: x - 92, top: 636 + (1 - e) * 300, width: 184, height: 200, borderRadius: 30, background: '#5A4F6E'}} />
            <Person
              {...(k ? CAST.engB : CAST.engA)}
              t={t}
              x={x}
              y={796 + (1 - e) * 360}
              s={0.8}
              sitting
              expr={t > TB.software ? 'happy' : 'neutral'}
              look={k ? -0.5 : 0.5}
              armL={10 + typ(k)}
              armR={10 - typ(k)}
              elbowL={-30}
              elbowR={-30}
              headTilt={k ? -3 : 3}
            />
           </div>
          </div>
        );
      })}
      <Desk x0={260} x1={1660} top={790} floor={FLOOR} />
      <LaptopBack x={SPOT.basith.x} w={140} base={792} glow={glow} sticker={P.rose} s={spring(t, TB.run + 0.15, 13, 0.45)} />
      <LaptopBack x={SPOT.awaiz.x} w={140} base={792} glow={glow} sticker={P.mint} s={spring(t, TB.run + 0.28, 13, 0.45)} />
      {/* mug */}
      <div style={{position: 'absolute', left: 940, top: 746, width: 40, height: 46, borderRadius: '4px 4px 12px 12px', background: P.rose}} />
      <div style={{position: 'absolute', left: 970, top: 754, width: 22, height: 22, borderRadius: 11, border: `6px solid ${P.rose}`, boxSizing: 'border-box'}} />
      <CodeChips t={t} x={SPOT.basith.x} k={0} />
      <CodeChips t={t} x={SPOT.awaiz.x} k={1} />
      <HangingSign x={960} y={262} t={t} t0={TB.company - 0.18} text="WeDevIT" bg={P.indigo} w={330} />

      {/* doctors at the lightbox */}
      <Lightbox x={DOC_C} y={640} t={t} />
      {dS > 0.001 && (
        <Person {...CAST.teamDoctor} t={t} x={SPOT.sujay.x + 40} y={FLOOR} s={0.76 * dS} squashY={sq(TB.sujay + 0.22)} look={lungOn ? 1 : 0.3} expr={lungOn ? 'talk' : 'happy'} talk={t > TB.lung && t < TB.doctors + 0.5} armR={lungOn ? lerp(10, 112, spring(t, TB.lung, 11, 0.5)) : 8} elbowR={lungOn ? -20 : 0} armL={6} />
      )}
      {dH > 0.001 && (
        <Person {...CAST.sharada} t={t} x={SPOT.sharada.x - 40} y={FLOOR} s={0.76 * dH} squashY={sq(TB.sharada + 0.22)} look={lungOn ? -1 : -0.3} expr="happy" headTilt={lungOn ? -5 : 0} armL={6} armR={6} />
      )}
      <Plant x={DOC_C - 760} y={FLOOR} s={0.9} t={t} seed={2} pot={P.plum} />
      <Plant x={DOC_C + 760} y={FLOOR} s={0.8} t={t} seed={4} />

      {/* Shirin with her books */}
      <Books x={STU_C - 420} t={t} />
      {st > 0.001 && (
        <Person {...CAST.medStudent} t={t} x={STU_C + 10} y={FLOOR} s={0.76 * st} squashY={sq(TB.shirin + 0.25)} expr="happy" look={-0.4} armR={lerp(6, 48, spring(t, TB.medical, 11, 0.5))} elbowR={lerp(0, -84, spring(t, TB.medical, 11, 0.5))} armL={6} hold={t > TB.medical ? <OpenBook /> : undefined} />
      )}
      <Plant x={STU_C + 470} y={FLOOR} s={1.0} t={t} seed={7} pot={P.marigold} />
    </>
  );
};

/* ---------- logo bloom ---------- */
const LOGO = {x: 960, y: 450};
const Logo: React.FC<{t: number}> = ({t}) => {
  const bloom = pop(t, TB.sahara - 0.04, 11, 0.42);
  if (bloom <= 0.001) return null;
  // on "With" it lifts and shrinks to make room for the five of us; leaves on "Basith"
  const lift = spring(t, TB.with - 0.06, 10, 0.6);
  const leave = easeInCubic(ramp(t, TB.basith - 0.1, TB.basith + 0.3));
  const y = lerp(LOGO.y, 175, lift) - leave * 400;
  const s = bloom * lerp(1, 0.46, lift);
  const word = (i: number) => easeOutQuint2(ramp(t, TB.sahara + 0.16 + i * 0.045, TB.sahara + 0.7 + i * 0.045));
  return (
    <div style={{position: 'absolute', left: LOGO.x, top: y, width: 0, height: 0, transform: `scale(${s}) rotate(${(1 - Math.min(1, bloom)) * -50 + Math.sin(t * 1.2) * 1.5}deg)`}}>
      {/* soft halo */}
      <div style={{position: 'absolute', left: -260, top: -260, width: 520, height: 520, borderRadius: '50%', background: 'radial-gradient(circle, rgba(221,235,227,0.95) 0%, rgba(221,235,227,0) 68%)'}} />
      <Img src={staticFile('brand/sahara-logo.png')} style={{position: 'absolute', left: -190, top: -200, width: 380, height: 380}} />
      <div style={{position: 'absolute', left: -300, top: 170, width: 600, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 112, color: P.sageDark, lineHeight: 1.1}}>
        {'Sahara'.split('').map((ch, i) => (
          <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: 8}}>
            <span style={{display: 'inline-block', transform: `translateY(${(1 - word(i)) * 120}%)`}}>{ch}</span>
          </span>
        ))}
      </div>
    </div>
  );
};
const easeOutQuint2 = (x: number) => 1 - Math.pow(1 - clamp(x), 5);

/** petals that burst from the logo as it blooms */
const Petals: React.FC<{t: number}> = ({t}) => {
  const t0 = TB.sahara;
  const dt = t - t0;
  if (dt < 0 || dt > 1.2) return null;
  return (
    <>
      {Array.from({length: 14}, (_, i) => {
        const a = (i / 14) * Math.PI * 2 + 0.2;
        const u = easeOutCubic(dt / 0.9);
        const d = 120 + u * (260 + (i % 3) * 60);
        const s = (1 - ramp(dt, 0.55, 1.2)) * (i % 2 ? 16 : 11);
        const c = [P.marigold, P.mint, P.rose, P.sage][i % 4];
        return <div key={i} style={{position: 'absolute', left: LOGO.x + Math.cos(a) * d - s, top: LOGO.y + Math.sin(a) * d * 0.9 - s, width: s * 2, height: s * 2.6, borderRadius: '50%', background: c, transform: `rotate(${(a * 180) / Math.PI + 90}deg)`}} />;
      })}
    </>
  );
};

/* ---------- the board ---------- */
export const Board: React.FC<{t: number}> = ({t}) => {
  const cam = boardCam(t);
  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      <World cam={cam} depth={0.86}>
        <Wall t={t} />
      </World>
      <World cam={cam}>
        <Arches t={t} />
        <Floor />
        <People t={t} />
        <Motes t={t} x0={-200} w={6000} y0={0} h={900} n={60} seed="bm" />
        <Petals t={t} />
        <Logo t={t} />
        {/* the five of us: underline swoosh on "us" */}
        <Swoosh t={t} />
        {ORDER.map((w, i) => {
          const c = cardState(w, i, t);
          return <PhotoCard key={w} who={w} x={c.x} y={c.y} d={c.d} s={c.s} rot={c.rot} gray={c.gray} ring={P.white} />;
        })}
        {ORDER.map((w, i) => (i < 5 ? <Sparkle key={'p' + w} t={t} t0={popT(i) + 0.05} x={rowX(i)} y={ROW_Y} r={ROW_D * 0.56} n={6} rot={i * 20} color={TONE[w]} /> : null))}
        {ORDER.map((w) => (
          <Sparkle key={'f' + w} t={t} t0={FOCUS[w] + 0.08} x={SPOT[w].x} y={SPOT[w].y} r={CARD_D * 0.58} color={P.marigold} />
        ))}
        {/* names and roles rise under each card */}
        {ORDER.map((w) => (
          <React.Fragment key={'n' + w}>
            <Center x={SPOT[w].x} y={NAME_Y}>
              <Rise t={t} t0={FOCUS[w] + 0.12} text={NAME[w]} size={68} color={P.ink} align="center" stagger={0.07} />
            </Center>
            <Center x={SPOT[w].x} y={NAME_Y + 76}>
              <Rise t={t} t0={roleT(w)} text={ROLE[w]} size={36} weight={500} color={P.inkSub} align="center" stagger={0.05} />
            </Center>
          </React.Fragment>
        ))}
      </World>
      <World cam={cam} depth={1.3}>
        <FgLeaves x={-180} y={1180} t={t} s={1.1} />
        <FgLeaves x={DOC_C + 1050} y={1200} t={t} flip s={1.0} />
        <FgLeaves x={STU_C + 950} y={1180} t={t} flip s={1.1} />
      </World>
    </AbsoluteFill>
  );
};
const roleT = (w: Who) => (w === 'sujay' || w === 'sharada' ? FOCUS[w] + 0.4 : w === 'shirin' ? TB.medical : FOCUS[w] + 0.38);

const Swoosh: React.FC<{t: number}> = ({t}) => {
  const p = easeOutCubic(ramp(t, TB.us - 0.05, TB.us + 0.4));
  const o = 1 - ramp(t, TB.basith - 0.2, TB.basith + 0.1);
  if (p <= 0 || o <= 0) return null;
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={1} height={1}>
      <path d={`M ${rowX(0) - 120} ${ROW_Y + 160} Q 960 ${ROW_Y + 230} ${rowX(4) + 120} ${ROW_Y + 150}`} stroke={P.marigold} strokeWidth={12} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={`${p} 2`} opacity={o} />
    </svg>
  );
};
