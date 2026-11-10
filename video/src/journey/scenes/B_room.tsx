/* Shared team workspace for scenes B, C and D: one continuous room, one camera, one tower.
   Everything here is a pure function of GLOBAL film seconds, so the three scene windows join seamlessly. */
import React from 'react';
import {AbsoluteFill, Img, random, staticFile} from 'remotion';
import {
  P,
  FONT_HEAD,
  FONT_BODY,
  World,
  Cam,
  CamKey,
  camAt,
  Person,
  Expr,
  clamp,
  lerp,
  ramp,
  mv,
  pop,
  spring,
  kick,
  easeOutQuint,
  easeOutCubic,
  easeInCubic,
  easeInOutCubic,
  back,
} from '../kit';
import {CAST} from '../cast';

/* ---------- layout (world units, depth 1) ---------- */
export const FLOOR = 905;
export const TABLE_TOP = 760;
export const TABLE_L = 380;
export const TABLE_R = 1220;
export const ENG_X = [610, 830, 1050];
export const ENG_Y = 850;
export const DOC_X = 300;
export const STU_X = 70;
export const TOWER_X = 1540;
export const CARD_W = 500;
export const CARD_H = 72;
export const WALL_D = 0.92;
export const GOALS = 5;

/* ---------- camera: one key list for B, C, D ---------- */
/** camera position that puts a wall-layer point (depth WALL_D) at screen centre */
const wc = (x: number, y: number) => ({x: 960 + (x - 960) / WALL_D, y: 540 + (y - 540) / WALL_D});
const POSTER = {x: 250, y: 250};
const NOTE = {x: 800, y: 330};
const pc = wc(POSTER.x, POSTER.y - 6);
const nc = wc(NOTE.x, NOTE.y + 40);
export const KEYS: CamKey[] = [
  {t: 10.6, x: pc.x, y: pc.y + 18, z: 3.6},
  {t: 11.4, x: pc.x, y: pc.y + 22, z: 3.05},
  {t: 12.12, x: pc.x + 40, y: pc.y + 60, z: 2.6},
  {t: 12.42, x: 452, y: 706, z: 2.5},
  {t: 12.98, x: 455, y: 704, z: 2.62},
  {t: 13.8, x: 650, y: 605, z: 1.38},
  {t: 14.85, x: 670, y: 595, z: 1.4},
  {t: 15.4, x: nc.x - 20, y: nc.y + 60, z: 1.32},
  {t: 16.55, x: nc.x - 10, y: nc.y + 50, z: 1.36},
  {t: 17.1, x: nc.x, y: nc.y + 10, z: 1.62},
  {t: 17.85, x: nc.x, y: nc.y + 20, z: 1.64},
  {t: 18.5, x: 790, y: 610, z: 1.45},
  {t: 20.25, x: 795, y: 615, z: 1.52},
  {t: 20.95, x: 900, y: 570, z: 1.12},
  {t: 21.2, x: 930, y: 575, z: 1.1},
  {t: 21.75, x: 1400, y: 700, z: 1.45},
  {t: 22.55, x: 1410, y: 690, z: 1.47},
  {t: 23.0, x: 1420, y: 672, z: 1.5},
  {t: 24.35, x: 1425, y: 660, z: 1.52},
  {t: 24.85, x: 1400, y: 640, z: 1.38},
  {t: 26.3, x: 1360, y: -200, z: 0.92},
  {t: 27.55, x: 1345, y: -190, z: 0.94},
  {t: 28.45, x: 1000, y: 200, z: 0.64},
  {t: 29.3, x: 960, y: 290, z: 0.69},
  {t: 29.95, x: 560, y: 480, z: 1.0},
  {t: 31.15, x: 600, y: 460, z: 1.02},
  {t: 31.6, x: 780, y: 410, z: 0.92},
  {t: 33.35, x: 810, y: 400, z: 0.95},
  {t: 33.75, x: 1260, y: 290, z: 0.58},
  {t: 34.4, x: 1260, y: 300, z: 0.6},
  {t: 34.95, x: 880, y: 340, z: 0.86},
  {t: 36.3, x: 900, y: 350, z: 0.88},
  {t: 36.75, x: 1450, y: 620, z: 0.8},
  {t: 37.85, x: 1440, y: 600, z: 0.82},
  {t: 38.55, x: 1150, y: 500, z: 1.05},
  {t: 39.4, x: 1150, y: 440, z: 1.1},
  {t: 39.9, x: 1150, y: 282, z: 2.05},
  {t: 41.0, x: 1150, y: 282, z: 2.4},
];

/* ---------- tower: 20 tool cards ---------- */
export const LABELS = [
  'Syringe driver',
  'Spinal cord compression',
  'Sedation protocol',
  'Bleeding risk',
  'Bowel obstruction',
  'Goals of care talk',
  'Delirium',
  'Wound care',
  'Breathlessness',
  'Prognosis',
  'Radiotherapy',
  'Nutrition',
  'Bone metastases',
  'Nausea and vomiting',
  'Hypercalcaemia',
  'Opioid converter',
  'Mouth care',
  'Seizures',
  'Fluids at the end',
  'Anxiety',
];
const COLS = [P.indigo, P.rose, P.plum, P.coral, P.marigold, P.sage, P.sky, P.sageDark, P.rose, P.indigo, P.plum, P.marigold, P.coral, P.sky, P.sageDark, P.indigo, P.rose, P.plum, P.coral, P.marigold];
const SHADE: Record<string, string> = {
  [P.indigo]: '#46588C',
  [P.rose]: '#A5536C',
  [P.plum]: '#6F456A',
  [P.coral]: '#C9664F',
  [P.marigold]: P.marigoldDeep,
  [P.sage]: P.sageDark,
  [P.sky]: '#5E93BA',
  [P.sageDark]: '#1F453A',
};
export const cardColor = (i: number) => (i === GOALS ? P.sage : COLS[i]);
const lightCard = (c: string) => c === P.marigold || c === P.sky;

/** launch times (film seconds) and flight durations */
export const LAUNCH: number[] = [21.13, 22.5, 24.33, ...Array.from({length: 17}, (_, k) => 24.85 + k * 0.066)];
export const FLIGHT = (i: number) => (i < 3 ? 0.42 : 0.3);
export const ARRIVE = (i: number) => LAUNCH[i] + FLIGHT(i);
export const cardCount = (t: number) => LAUNCH.filter((_, i) => t >= ARRIVE(i)).length;

const rnd = (s: string) => random('room-' + s);
const restX = (i: number) => TOWER_X + (rnd('cx' + i) - 0.5) * 30;
const restY = (i: number) => FLOOR - CARD_H / 2 - i * CARD_H;
const restR = (i: number) => (rnd('cr' + i) - 0.5) * 4;

export const T_CREAK = 31.95;
export const T_FALL = 33.61; // "right"
/** tower sway amplitude (px at the top) */
const sway = (t0: number) => {
  const t = Math.min(t0, T_FALL);
  const n = cardCount(t);
  let a = 16 * Math.pow(n / 20, 1.5) * Math.sin(t * 2.3);
  for (let j = 0; j < 20; j++) {
    const d = t - ARRIVE(j);
    if (d > 0 && d < 1.2) a += 9 * (j / 20) * Math.exp(-d * 4) * Math.sin(d * 13);
  }
  // creak: the question makes it groan
  const c = ramp(t, T_CREAK, 33.25);
  a += c * (18 * Math.sin(t * 6.2) + 6 * Math.sin(t * 23));
  // anticipation: a lean back to the left before it goes
  a -= 34 * mv(t, 33.2, T_FALL);
  return a;
};
type Pose = {x: number; y: number; r: number; sx: number; sy: number; vis: boolean};
const standing = (i: number, t: number) => {
  const f = (i + 0.5) / 20;
  const a = sway(t);
  let x = restX(i) + a * f * f;
  let y = restY(i);
  let r = restR(i) + ((a * 2 * f) / 1280) * 57.3;
  // jitter on the creak
  if (t > T_CREAK && t < T_FALL + 0.2 && i > 3 && i < 16) {
    const k = ramp(t, T_CREAK + 0.4, 33.3);
    x += (random('jx' + i + Math.floor(t * 30)) - 0.5) * 5 * k;
    r += (random('jr' + i + Math.floor(t * 30)) - 0.5) * 1.6 * k;
  }
  // topple about the base pivot
  if (t > T_FALL) {
    const u = (t - T_FALL) / 0.45;
    const th = 0.7 * Math.pow(u, 2.4) * (0.5 + 0.5 * f);
    const dx = x - TOWER_X;
    const dy = y - FLOOR;
    x = TOWER_X + dx * Math.cos(th) - dy * Math.sin(th);
    y = FLOOR + dx * Math.sin(th) + dy * Math.cos(th);
    r += (th * 180) / Math.PI;
  }
  return {x, y, r};
};
const detachT = (i: number) => T_FALL + 0.17 + 0.25 * (1 - (i + 0.5) / 20);
const G = 4600;
type Fall = {td: number; xd: number; yd: number; rd: number; vx: number; vy: number; spin: number; T: number; tl: number; xl: number; yl: number; rl: number; rf: number; vyl: number};
let FALL: Fall[] | null = null;
const fallData = (): Fall[] => {
  if (FALL) return FALL;
  const out: Fall[] = [];
  for (let i = 0; i < 20; i++) {
    const td = detachT(i);
    const a = standing(i, td);
    const b = standing(i, td - 0.02);
    let vx = (a.x - b.x) / 0.02 + (rnd('ix' + i) - 0.25) * 900;
    let vy = (a.y - b.y) / 0.02 - rnd('iy' + i) * 520;
    let spin = (rnd('sp' + i) - 0.4) * 380;
    let yf = FLOOR - CARD_H / 2;
    if (i === GOALS) {
      vy = -420;
      spin = -60;
      yf = TABLE_TOP - CARD_H / 2 + 4;
    }
    const tFor = (yy: number) => (-vy + Math.sqrt(vy * vy + 2 * G * Math.max(0, yy - a.y))) / G;
    let T = tFor(yf);
    let xl = a.x + vx * T;
    if (i === GOALS) {
      xl = 1150;
      vx = (xl - a.x) / T;
    } else {
      const c = clamp(xl, 1360, 2330);
      if (c !== xl) {
        xl = c;
        vx = (xl - a.x) / T;
      }
    }
    out.push({td, xd: a.x, yd: a.y, rd: a.r, vx, vy, spin, T, tl: td + T, xl, yl: yf, rl: a.r + spin * T, rf: 0, vyl: vy + G * T});
  }
  // stack: later landers rest on earlier neighbours
  const order = out.map((_, i) => i).filter((i) => i !== GOALS).sort((p, q) => out[p].tl - out[q].tl);
  const landed: number[] = [];
  for (const i of order) {
    const o = out[i];
    const k = landed.filter((j) => Math.abs(out[j].xl - o.xl) < 260).length;
    const yl = FLOOR - CARD_H / 2 - Math.min(k, 3) * 17;
    if (yl !== o.yl) {
      const T = (-o.vy + Math.sqrt(o.vy * o.vy + 2 * G * Math.max(0, yl - o.yd))) / G;
      o.yl = yl;
      o.T = T;
      o.tl = o.td + T;
      o.xl = o.xd + o.vx * T;
      o.rl = o.rd + o.spin * T;
      o.vyl = o.vy + G * T;
    }
    landed.push(i);
  }
  out.forEach((o, i) => {
    o.rf = i === GOALS ? -5 : clamp(o.rl * 0.25, -18, 18) + (rnd('rf' + i) - 0.5) * 14;
  });
  FALL = out;
  return out;
};
export const landTimes = () => fallData().map((f) => f.tl);

/* broom sweep */
export const T_SWEEP = 36.66;
export const broomX = (t: number) => lerp(1080, 3400, Math.pow(ramp(t, T_SWEEP, 37.55), 1.4));
/** goals card: kept, lifted, centred */
export const T_KEEP = 38.02;
export const T_LIFT = 38.33;
export const GOAL_HOVER = {x: 1150, y: 282, s: 1.3};
export const goalsBob = (t: number) => Math.sin((t - T_LIFT) * 2.4) * 6 * ramp(t, 38.9, 39.3) * (1 - ramp(t, 39.6, 39.9));

export const cardPose = (i: number, t: number): Pose => {
  const L = LAUNCH[i];
  const A = ARRIVE(i);
  if (t < L) return {x: 0, y: 0, r: 0, sx: 0, sy: 0, vis: false};
  if (t < A) {
    const u = (t - L) / (A - L);
    const k = i % 3;
    const x0 = ENG_X[k];
    const y0 = 700;
    const x1 = restX(i);
    const y1 = restY(i);
    const ux = easeOutCubic(u);
    const arc = i < 3 ? 300 : 220;
    const s = lerp(0.35, 1, easeOutCubic(u));
    return {x: lerp(x0, x1, ux), y: lerp(y0, y1, u) - arc * 4 * u * (1 - u), r: (1 - u) * (i < 3 ? -40 : -360) + restR(i), sx: s, sy: s, vis: true};
  }
  const F = fallData()[i];
  if (t < F.td) {
    const p = standing(i, t);
    const d = t - A;
    const q = Math.exp(-d * 9) * Math.sin(d * 30) * 0.22;
    return {x: p.x, y: p.y + q * 12, r: p.r, sx: 1 + q, sy: 1 - q, vis: true};
  }
  if (t < F.tl) {
    const d = t - F.td;
    return {x: F.xd + F.vx * d, y: F.yd + F.vy * d + 0.5 * G * d * d, r: F.rd + F.spin * d, sx: 1, sy: 1, vis: true};
  }
  const d = t - F.tl;
  const hb = clamp(Math.abs(F.vyl) / 3200, 0.15, 1);
  let by = 0;
  if (d < 0.2) by = 46 * hb * Math.sin((Math.PI * d) / 0.2);
  else if (d < 0.33) by = 13 * hb * Math.sin((Math.PI * (d - 0.2)) / 0.13);
  const sq = d < 0.12 ? Math.sin((Math.PI * d) / 0.12) * 0.18 * hb : 0;
  let x = F.xl + F.vx * 0.07 * (1 - Math.exp(-d * 5));
  let y = F.yl - by + sq * 20;
  let r = F.rl + (F.rf - F.rl) * spring(t, F.tl, 15, 0.45);
  let sx = 1 + sq;
  let sy = 1 - sq;
  if (i === GOALS) {
    if (t > T_LIFT) {
      const u = back(ramp(t, T_LIFT, T_LIFT + 0.6), 1.3);
      const s = lerp(1, GOAL_HOVER.s, u);
      x = lerp(x, GOAL_HOVER.x, u);
      y = lerp(y, GOAL_HOVER.y, u) + goalsBob(t);
      r = lerp(r, 0, clamp(u)) + Math.sin((t - T_LIFT) * 1.7) * 2 * ramp(t, 38.9, 39.3) * (1 - ramp(t, 39.6, 39.9));
      sx = s;
      sy = s;
    }
    return {x, y, r, sx, sy, vis: true};
  }
  // swept away by the broom
  const bx = broomX(t);
  const left = x - CARD_W / 2 + 30;
  const push = Math.max(0, bx - left);
  if (push > 0) {
    x += push * 1.1;
    y -= Math.sin(Math.min(push, 900) / 900 * Math.PI) * (120 + rnd('gy' + i) * 160);
    r += push * (0.08 + rnd('gr' + i) * 0.1);
  }
  return {x, y, r, sx, sy, vis: x < 3600};
};

export const Glyph: React.FC<{i: number; c: string}> = ({i, c}) => {
  const k = i % 5;
  return (
    <svg width={46} height={46} viewBox="-20 -20 40 40" style={{flex: 'none'}}>
      <circle r={19} fill={P.white} />
      {k === 0 && <path d="M-3 -10 H3 V-3 H10 V3 H3 V10 H-3 V3 H-10 V-3 H-3 Z" fill={c} />}
      {k === 1 && <path d="M0 -11 C6 -3 9 1 9 5 A9 9 0 0 1 -9 5 C-9 1 -6 -3 0 -11 Z" fill={c} />}
      {k === 2 && <path d="M-11 2 L-5 2 L-2 -7 L2 9 L5 2 L11 2" stroke={c} strokeWidth={3.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />}
      {k === 3 && <path d="M0 9 C-14 0 -9 -11 0 -5 C9 -11 14 0 0 9 Z" fill={c} />}
      {k === 4 && <circle r={7} fill={c} />}
    </svg>
  );
};

export const ToolCard: React.FC<{i: number; x: number; y: number; r: number; sx: number; sy: number; glow?: number; wordHop?: (w: number) => number; labelOut?: number}> = ({i, x, y, r, sx, sy, glow = 0, wordHop, labelOut = 0}) => {
  const c = cardColor(i);
  const ink = lightCard(c) ? P.ink : P.white;
  const lab = LABELS[i];
  const fs = lab.length > 19 ? 31 : lab.length > 16 ? 33 : 35;
  return (
    <div
      style={{
        position: 'absolute',
        left: x - CARD_W / 2,
        top: y - CARD_H / 2,
        width: CARD_W,
        height: CARD_H,
        transform: `rotate(${r}deg) scale(${sx},${sy})`,
        borderRadius: 14,
        background: c,
        boxShadow: `inset 0 -9px 0 ${SHADE[c] ?? 'rgba(0,0,0,0.2)'}${glow > 0 ? `, 0 0 ${40 * glow}px ${16 * glow}px rgba(246,200,110,${0.75 * glow})` : ''}`,
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        padding: '0 18px 6px 14px',
        boxSizing: 'border-box',
      }}
    >
      <Glyph i={i} c={c} />
      <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: fs, color: ink, whiteSpace: 'nowrap', display: 'flex', gap: '0.26em', overflow: 'hidden', paddingTop: 4}}>
        {lab.split(' ').map((w, k) => (
          <span key={k} style={{display: 'inline-block', transform: `translateY(${(wordHop ? wordHop(k) : 0) + labelOut * 140}%)`}}>
            {w}
          </span>
        ))}
      </div>
    </div>
  );
};

/* ---------- team poses (global t) ---------- */
type EP = {expr: Expr; look: number; armL: number; armR: number; elbowL: number; elbowR: number; lean: number; headTilt: number; talk: boolean; squashY: number};
const engPose = (k: number, t0: number): EP => {
  const t = t0 - k * 0.07;
  const p: EP = {expr: 'neutral', look: -1, armL: 6, armR: 6, elbowL: 0, elbowR: 0, lean: 0, headTilt: 0, talk: false, squashY: 1};
  if (t < 18.1) {
    if (t > 15.0) {
      p.look = k === 0 ? 0.6 : k === 1 ? 0 : -0.6;
      p.headTilt = -4;
      p.expr = 'think';
    }
    if (t > 16.8) p.expr = 'worried';
    return p;
  }
  if (t < 28.25) {
    p.expr = 'happy';
    p.look = 0;
    // anticipation: tuck in
    const ant = mv(t, 18.72, 19.12);
    const go = spring(t, 19.16, 13, 0.4);
    const off = mv(t, 19.8, 20.1);
    const lift = go * (1 - off);
    p.lean = -4 * ant * (1 - go);
    p.squashY = 1 - 0.035 * ant * (1 - go);
    p.armL = lerp(6 - 4 * ant, 150, lift);
    p.armR = lerp(6 - 4 * ant, 150, lift);
    p.elbowL = -25 * lift;
    p.elbowR = -25 * lift;
    if (t > 20.04) {
      const typ = Math.sin(t * 22 + k) * 3;
      p.armL = 8 + typ;
      p.armR = 8 - typ;
      p.expr = t > 20.6 && t < 21.1 ? 'surprised' : 'happy';
      p.look = t > 21.2 ? 1 : 0;
      p.lean = 2 * Math.sin(t * 3 + k);
    }
    if (k === 0 && t > 26.5 && t < 27.6) {
      const c = spring(t, 26.5, 12, 0.4) * (1 - mv(t, 27.3, 27.6));
      p.armR = lerp(8, 168, c);
      p.elbowR = -10 * c;
    }
    if (t > 27.4) {
      p.lean = -3 * mv(t, 27.4, 27.8);
      p.look = 0.4;
    }
    return p;
  }
  // scene D
  p.look = -1;
  p.expr = 'neutral';
  if (t > 31.4) p.expr = 'think';
  if (t > 32.5) {
    p.look = 1;
    p.expr = 'worried';
    p.headTilt = -5;
  }
  if (t > 33.62) {
    const w = spring(t, 33.62, 12, 0.4);
    p.expr = 'surprised';
    p.lean = -9 * w * (1 - mv(t, 34.3, 34.7));
    p.armL = lerp(6, 40, w);
    p.armR = lerp(6, 40, w);
    p.elbowL = 110 * w;
    p.elbowR = 110 * w;
    p.squashY = 1 - 0.04 * w;
  }
  if (t > 34.4) {
    const d = mv(t, 34.4, 34.7);
    p.expr = 'sad';
    p.look = 0;
    p.headTilt = 7 * d;
    p.armL = lerp(40, 6, d);
    p.armR = lerp(40, 6, d);
    p.elbowL = lerp(110, 0, d);
    p.elbowR = lerp(110, 0, d);
    p.squashY = 1;
  }
  if (t > 36.6) {
    p.expr = 'worried';
    p.look = 1;
    p.headTilt = 0;
  }
  if (t > 38.05) {
    p.expr = 'happy';
    p.look = 1;
    p.headTilt = -4;
  }
  return p;
};
const docPose = (t: number): Partial<EP> => {
  const p: Partial<EP> = {expr: 'neutral', look: 1, armL: 4, armR: 6, elbowR: 0, headTilt: 0, talk: false};
  if (t < 13.3) p.expr = 'neutral';
  else if (t < 17.75) {
    p.talk = t > 13.62 && !(t > 14.4 && t < 14.9) && !(t > 16.0 && t < 16.55);
    p.expr = t > 16.6 ? 'sad' : 'talk';
    const g = Math.sin(t * 4.2) * 10;
    p.armR = 38 + g;
    p.elbowR = -60;
    if (t > 14.9 && t < 16.4) {
      const u = spring(t, 14.9, 12, 0.45) * (1 - mv(t, 16.1, 16.4));
      p.armR = lerp(38, 128, u);
      p.elbowR = lerp(-60, -14, u);
      p.look = 1;
      p.headTilt = -5 * u;
    }
  } else if (t < 28.25) {
    p.expr = t > 20 ? 'happy' : 'neutral';
    p.look = t > 21.2 ? 1 : 0.6;
    p.armL = 4;
    p.armR = 6;
    if (t > 25.6) p.expr = 'surprised';
    if (t > 26.7) {
      p.expr = 'think';
      p.headTilt = 6;
    }
  } else {
    p.look = -1;
    p.expr = 'neutral';
    if (t > 33.5) {
      p.expr = 'surprised';
      p.look = 1;
    }
    if (t > 34.4) {
      p.expr = 'neutral';
      p.look = 0;
      p.headTilt = Math.sin((t - 34.4) * 9) * 5 * (1 - ramp(t, 35.0, 35.4));
    }
    if (t > 38.05) {
      p.expr = 'happy';
      p.look = 1;
    }
  }
  return p;
};

/* ---------- room drawing ---------- */
const Wall: React.FC<{t: number}> = ({t}) => {
  const cloud = (cx: number, cy: number, s: number, sp: number, k: number) => {
    const x = ((cx + t * sp) % 900) - 120;
    return (
      <g key={k} transform={`translate(${x} ${cy}) scale(${s})`} fill="#FFFFFF">
        <ellipse cx={0} cy={0} rx={60} ry={24} />
        <ellipse cx={-30} cy={-10} rx={34} ry={26} />
        <ellipse cx={22} cy={-18} rx={36} ry={30} />
      </g>
    );
  };
  return (
    <>
      <div style={{position: 'absolute', left: -1800, top: -2000, width: 5600, height: 3500, background: P.creamWarm}} />
      {/* lower wall */}
      <div style={{position: 'absolute', left: -1800, top: 640, width: 5600, height: 900, background: P.sand}} />
      <div style={{position: 'absolute', left: -1800, top: 632, width: 5600, height: 14, background: '#E4D2B7'}} />
      {/* painted arch behind the table */}
      <div style={{position: 'absolute', left: 360, top: -60, width: 900, height: 712, borderRadius: '450px 450px 0 0', background: P.sageLight}} />
      <div style={{position: 'absolute', left: 1120, top: -60, width: 140, height: 712, borderRadius: '0 450px 0 0', background: 'rgba(59,117,98,0.07)'}} />
      {/* banner */}
      <div style={{position: 'absolute', left: 420, top: -560, width: 700, height: 110, borderRadius: 18, background: P.plum, boxShadow: 'inset 0 -10px 0 #6F456A', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 58, color: P.cream}}>
        Health-a-thon 2026
      </div>
      {[500, 1040].map((x) => (
        <div key={x} style={{position: 'absolute', left: x, top: -990, width: 6, height: 432, background: '#B9A68C'}} />
      ))}
      {/* bunting */}
      <svg style={{position: 'absolute', left: 1000, top: -700, overflow: 'visible'}} width={1400} height={200}>
        <path d="M0 0 Q700 150 1400 0" stroke="#B9A68C" strokeWidth={5} fill="none" />
        {Array.from({length: 12}, (_, i) => {
          const u = (i + 0.5) / 12;
          const x = u * 1400;
          const y = 4 * 75 * u * (1 - u);
          const sw = Math.sin(t * 2 + i) * 4;
          const c = [P.marigold, P.rose, P.sage, P.plum][i % 4];
          return <path key={i} d={`M${x - 34} ${y} L${x + 34} ${y} L${x + sw} ${y + 70} Z`} fill={c} />;
        })}
      </svg>
      {/* window */}
      <div style={{position: 'absolute', left: 1700, top: -160, width: 700, height: 720, borderRadius: 26, background: P.white, padding: 20, boxSizing: 'border-box'}}>
        <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: 12, overflow: 'hidden', background: 'linear-gradient(180deg,#BFDDEE 0%,#E3EEF0 60%,#F6E9D4 100%)'}}>
          <svg width={660} height={680} style={{position: 'absolute', left: 0, top: 0}}>
            {cloud(80, 120, 1.1, 9, 0)}
            {cloud(520, 220, 0.8, 6, 1)}
            {cloud(300, 70, 0.6, 12, 2)}
            <rect x={40} y={430} width={110} height={260} fill="#E6D3BC" />
            <rect x={170} y={380} width={90} height={310} fill="#DCC3AA" />
            <rect x={470} y={450} width={130} height={240} fill="#E6D3BC" />
            {[0, 1, 2, 3].map((r) => [0, 1].map((c) => <rect key={r + '' + c} x={190 + c * 40} y={410 + r * 56} width={22} height={30} fill="#F5E9DA" />))}
            <g transform={`rotate(${Math.sin(t * 1.1) * 1.5} 380 690)`}>
              <rect x={368} y={520} width={24} height={170} fill="#7A5A44" />
              <circle cx={380} cy={470} r={95} fill={P.sage} />
              <circle cx={320} cy={520} r={62} fill={P.sageDark} />
              <circle cx={445} cy={515} r={68} fill="#4C8A74" />
            </g>
          </svg>
          <div style={{position: 'absolute', left: 320, top: 0, width: 20, height: '100%', background: P.white}} />
          <div style={{position: 'absolute', left: 0, top: 330, width: '100%', height: 20, background: P.white}} />
        </div>
      </div>
      <div style={{position: 'absolute', left: 1680, top: 552, width: 740, height: 26, borderRadius: 8, background: '#EADBC5'}} />
      {/* shelf on the left */}
      <div style={{position: 'absolute', left: -620, top: 300, width: 520, height: 22, borderRadius: 6, background: '#C99A62'}} />
      {[
        [-590, 70, 150, P.indigo],
        [-515, 50, 128, P.rose],
        [-460, 60, 160, P.marigold],
        [-395, 44, 120, P.sage],
      ].map(([x, w, h, c], i) => (
        <div key={i} style={{position: 'absolute', left: x as number, top: 300 - (h as number), width: w as number, height: h as number, borderRadius: 6, background: c as string, transform: i === 3 ? 'rotate(12deg)' : undefined, transformOrigin: 'bottom left'}} />
      ))}
      <div style={{position: 'absolute', left: -250, top: 230, width: 90, height: 70, borderRadius: '8px 8px 22px 22px', background: P.coral}} />
      <svg style={{position: 'absolute', left: -280, top: 110, overflow: 'visible'}} width={150} height={130}>
        <g transform={`rotate(${Math.sin(t * 1.3) * 3} 75 125)`}>
          <ellipse cx={45} cy={70} rx={22} ry={48} transform="rotate(-30 45 70)" fill={P.sage} />
          <ellipse cx={105} cy={70} rx={22} ry={48} transform="rotate(30 105 70)" fill={P.sageDark} />
          <ellipse cx={75} cy={50} rx={20} ry={55} fill="#4C8A74" />
        </g>
      </svg>
      {/* poster: the Sahara logo grows here */}
      <div style={{position: 'absolute', left: POSTER.x - 130, top: POSTER.y - 165, width: 260, height: 330, borderRadius: 10, background: P.marigoldDeep, padding: 13, boxSizing: 'border-box'}}>
        <div style={{width: '100%', height: '100%', borderRadius: 4, background: P.cream}} />
      </div>
      {/* pinboard */}
      <div style={{position: 'absolute', left: 530, top: 140, width: 540, height: 330, borderRadius: 16, background: '#B98F5E', padding: 14, boxSizing: 'border-box'}}>
        <div style={{width: '100%', height: '100%', borderRadius: 8, background: '#D9B98C', position: 'relative'}}>
          {[
            [26, 30, P.marigold, -6],
            [40, 190, P.roseLight, 5],
            [410, 24, P.mint, 4],
            [404, 176, '#F3D9A0', -5],
          ].map(([x, y, c, r], i) => (
            <div key={i} style={{position: 'absolute', left: x as number, top: y as number, width: 80, height: 80, background: c as string, transform: `rotate(${r}deg)`, boxShadow: 'inset 0 -6px 0 rgba(0,0,0,0.06)'}}>
              <div style={{position: 'absolute', left: 14, top: 26, width: 50, height: 6, borderRadius: 3, background: 'rgba(56,46,41,0.25)'}} />
              <div style={{position: 'absolute', left: 14, top: 42, width: 36, height: 6, borderRadius: 3, background: 'rgba(56,46,41,0.25)'}} />
              <div style={{position: 'absolute', left: 34, top: -6, width: 14, height: 14, borderRadius: 7, background: [P.rose, P.indigo, P.coral, P.sage][i]}} />
            </div>
          ))}
        </div>
      </div>
    </>
  );
};

/** the Sahara logo in the poster: a seed drops, then the logo blooms (s04) */
export const T_SEED = 10.79;
export const T_BLOOM = 11.04;
const PosterArt: React.FC<{t: number}> = ({t}) => {
  const seedIn = spring(t, T_SEED - 0.12, 15, 0.45);
  const seedY = lerp(-120, 0, easeInCubic(ramp(t, T_SEED - 0.12, T_SEED + 0.06)));
  const sq = t > T_SEED + 0.06 && t < T_BLOOM ? Math.exp(-(t - T_SEED - 0.06) * 10) * Math.sin((t - T_SEED - 0.06) * 30) * 0.35 : 0;
  const bloom = pop(t, T_BLOOM, 11, 0.42);
  const seedGone = ramp(t, T_BLOOM, T_BLOOM + 0.12);
  const cx = POSTER.x;
  const cy = POSTER.y - 40;
  return (
    <>
      {t > T_SEED - 0.12 && seedGone < 1 && (
        <div style={{position: 'absolute', left: cx - 14, top: cy + 40 - 14 + seedY, width: 28, height: 28, borderRadius: 14, background: P.sage, transform: `scale(${(1 + sq) * Math.min(1, seedIn * 1.2) * (1 - seedGone)},${(1 - sq) * Math.min(1, seedIn * 1.2) * (1 - seedGone)})`}} />
      )}
      {t > T_BLOOM && (
        <>
          {/* burst ring */}
          {Array.from({length: 10}, (_, i) => {
            const a = (i / 10) * Math.PI * 2 + 0.3;
            const u = easeOutCubic(ramp(t, T_BLOOM, T_BLOOM + 0.7));
            const d = 30 + u * 110;
            const s = (1 - ramp(t, T_BLOOM + 0.35, T_BLOOM + 0.75)) * 9;
            return <div key={i} style={{position: 'absolute', left: cx + Math.cos(a) * d - s, top: cy + 10 + Math.sin(a) * d - s, width: s * 2, height: s * 2, borderRadius: s, background: i % 2 ? P.mint : P.marigold}} />;
          })}
          <Img src={staticFile('brand/sahara-logo.png')} style={{position: 'absolute', left: cx - 95, top: cy - 85, width: 190, height: 190, transform: `scale(${bloom}) rotate(${(1 - Math.min(1, bloom)) * -40}deg)`, transformOrigin: '50% 70%'}} />
          <div style={{position: 'absolute', left: cx - 120, top: cy + 108, width: 240, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 46, color: P.sageDark}}>
            {'Sahara'.split('').map((ch, i) => {
              const p = easeOutQuint(ramp(t, T_BLOOM + 0.18 + i * 0.04, T_BLOOM + 0.7 + i * 0.04));
              return (
                <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top'}}>
                  <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 120}%)`}}>{ch}</span>
                </span>
              );
            })}
          </div>
        </>
      )}
    </>
  );
};

/** blank note card on the pinboard ("what matters"), pops on s05 "patients" */
export const T_NOTE = 14.96;
export const NOTE_BOX = {x: NOTE.x - 160, y: NOTE.y - 118, w: 320, h: 236};
const NoteCard: React.FC<{t: number}> = ({t}) => {
  if (t < T_NOTE) return null;
  const s = pop(t, T_NOTE, 13, 0.42);
  return (
    <div style={{position: 'absolute', left: NOTE_BOX.x, top: NOTE_BOX.y, width: NOTE_BOX.w, height: NOTE_BOX.h, transform: `scale(${s}) rotate(${-2 + (1 - Math.min(1, s)) * 10}deg)`, transformOrigin: '50% 0%'}}>
      <div style={{position: 'absolute', inset: 0, background: P.white, borderRadius: 6, boxShadow: '0 8px 0 rgba(56,46,41,0.10)'}}>
        <div style={{position: 'absolute', left: 22, top: 18, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 30, color: P.rose}}>What matters</div>
        {[0, 1, 2, 3].map((k) => (
          <div key={k} style={{position: 'absolute', left: 22, right: 22, top: 92 + k * 36, height: 2, background: '#DCE6EE'}} />
        ))}
      </div>
      <div style={{position: 'absolute', left: NOTE_BOX.w / 2 - 13, top: -10, width: 26, height: 26, borderRadius: 13, background: P.marigold, boxShadow: 'inset 0 -5px 0 #B07A2E'}} />
    </div>
  );
};

const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -2000, top: FLOOR - 4, width: 6200, height: 1600, background: '#E2CDB0'}} />
    {[960, 1040, 1160, 1330].map((y, i) => (
      <div key={y} style={{position: 'absolute', left: -2000, top: y, width: 6200, height: 3 + i, background: 'rgba(160,120,80,0.16)'}} />
    ))}
    {Array.from({length: 18}, (_, i) => (
      <div key={i} style={{position: 'absolute', left: -1400 + i * 300 + (i % 2) * 140, top: 960, width: 3, height: 80, background: 'rgba(160,120,80,0.14)'}} />
    ))}
    <div style={{position: 'absolute', left: -2000, top: FLOOR - 14, width: 6200, height: 16, background: '#D8BF9C'}} />
    {/* table shadow */}
    <div style={{position: 'absolute', left: 340, top: 896, width: 920, height: 26, borderRadius: '50%', background: 'rgba(120,80,50,0.16)'}} />
  </>
);

const Lamp: React.FC<{x: number; t: number; seed: number; on: number}> = ({x, t, seed, on}) => {
  const sw = Math.sin(t * 1.2 + seed) * 1.6;
  return (
    <div style={{position: 'absolute', left: x, top: -1800, width: 0, height: 0, transform: `rotate(${sw}deg)`, transformOrigin: '0 0'}}>
      <div style={{position: 'absolute', left: -3, top: 0, width: 6, height: 1830, background: '#6B5A4E'}} />
      <svg style={{position: 'absolute', left: -380, top: 1830, overflow: 'visible'}} width={760} height={900}>
        <defs>
          <linearGradient id={'cone' + seed} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FFE4A8" stopOpacity={0.5 * on} />
            <stop offset="1" stopColor="#FFE4A8" stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d="M330 60 L430 60 L700 760 L60 760 Z" fill={`url(#cone${seed})`} />
        <path d="M330 0 L430 0 L480 64 L280 64 Z" fill={P.marigold} />
        <path d="M400 0 L430 0 L480 64 L430 64 Z" fill={P.marigoldDeep} />
        <ellipse cx={380} cy={66} rx={34} ry={12} fill="#FFF3D6" />
      </svg>
    </div>
  );
};

/** dust motes drifting in the lamp light */
const Motes: React.FC<{t: number}> = ({t}) => (
  <>
    {Array.from({length: 26}, (_, i) => {
      const bx = -200 + random('mx' + i) * 2600;
      const by = -300 + random('my' + i) * 1100;
      const x = bx + Math.sin(t * 0.4 + i) * 40 + t * 6;
      const y = by + Math.cos(t * 0.33 + i * 2) * 30 - ((t * 10 + i * 30) % 120);
      const r = 2 + random('mr' + i) * 3;
      return <div key={i} style={{position: 'absolute', left: x, top: y, width: r * 2, height: r * 2, borderRadius: r, background: 'rgba(255,240,205,0.85)'}} />;
    })}
  </>
);

const Laptop: React.FC<{x: number; glow: number; t: number; k: number}> = ({x, glow, t, k}) => (
  <>
    <div style={{position: 'absolute', left: x - 78, top: TABLE_TOP - 6, width: 156, height: 8, borderRadius: 4, background: '#2E3646'}} />
    <div style={{position: 'absolute', left: x - 68, top: TABLE_TOP - 74, width: 136, height: 70, borderRadius: '10px 10px 3px 3px', background: '#3E4A5E', boxShadow: glow > 0 ? `0 -4px 0 rgba(255,214,140,${0.9 * glow})` : undefined}}>
      <div style={{position: 'absolute', left: 58, top: 25, width: 20, height: 20, borderRadius: 10, background: glow > 0 ? lerpColor('#8C96A8', '#FFE6B0', glow) : '#8C96A8'}} />
      {/* stickers */}
      <div style={{position: 'absolute', left: 12, top: 10, width: 18, height: 12, borderRadius: 3, background: [P.rose, P.mint, P.marigold][k], transform: 'rotate(-8deg)'}} />
    </div>
  </>
);
const lerpColor = (a: string, b: string, u: number) => {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return `rgb(${pa.map((v, i) => Math.round(lerp(v, pb[i], clamp(u)))).join(',')})`;
};

/** laptop screen light (s06 "We built everything") */
export const screenGlow = (t: number) => {
  let g = mv(t, 20.04, 20.3) * (1 + 0.7 * Math.exp(-Math.max(0, t - 20.51) * 4) * (t > 20.51 ? 1 : 0));
  g *= 1 - 0.6 * mv(t, 33.7, 34.2);
  g *= 1 + 0.6 * mv(t, 37.9, 38.3);
  return g * (0.9 + 0.1 * Math.sin(t * 7));
};

const Table: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: TABLE_L, top: TABLE_TOP, width: TABLE_R - TABLE_L, height: 24, borderRadius: 8, background: '#C98F55'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 22, width: TABLE_R - TABLE_L - 40, height: 98, background: '#B37C46'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 100, width: TABLE_R - TABLE_L - 40, height: 20, background: '#9C6A3B'}} />
    {[TABLE_L + 30, TABLE_R - 62].map((x) => (
      <div key={x} style={{position: 'absolute', left: x, top: TABLE_TOP + 110, width: 32, height: FLOOR - TABLE_TOP - 108, background: '#9C6A3B'}} />
    ))}
    {/* mug with steam */}
    <div style={{position: 'absolute', left: 660, top: TABLE_TOP - 46, width: 40, height: 46, borderRadius: '4px 4px 12px 12px', background: P.rose}} />
    <div style={{position: 'absolute', left: 690, top: TABLE_TOP - 38, width: 22, height: 22, borderRadius: 11, border: `6px solid ${P.rose}`, boxSizing: 'border-box'}} />
    <svg style={{position: 'absolute', left: 650, top: TABLE_TOP - 130, overflow: 'visible'}} width={60} height={80}>
      {[0, 1].map((k) => {
        const ph = ((t * 0.7 + k * 0.5) % 1);
        return <path key={k} d={`M${18 + k * 18} 80 q 10 -20 0 -40 q -10 -20 0 -40`} stroke="rgba(255,255,255,0.8)" strokeWidth={5} fill="none" strokeLinecap="round" opacity={Math.sin(ph * Math.PI)} transform={`translate(0 ${-ph * 20})`} />;
      })}
    </svg>
  </>
);

/** desk calendar: flips JUL -> AUG on s05 "August" */
export const T_CAL = 12.36;
const DeskCal: React.FC<{t: number}> = ({t}) => {
  const x = 452;
  const w = 112;
  const h = 92;
  const top = TABLE_TOP - h - 4;
  const p = ramp(t, T_CAL, T_CAL + 0.28);
  const land = t > T_CAL + 0.28 ? Math.exp(-(t - T_CAL - 0.28) * 9) * Math.sin((t - T_CAL - 0.28) * 28) * 0.08 : 0;
  const page = (m: string, bg: string) => (
    <div style={{position: 'absolute', inset: 0, borderRadius: 8, background: P.white, overflow: 'hidden'}}>
      <div style={{height: 30, paddingTop: 6, boxSizing: 'border-box', background: bg, color: P.white, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 15, textAlign: 'center', lineHeight: '24px'}}>2026</div>
      <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 42, color: P.ink, textAlign: 'center', lineHeight: '58px'}}>{m}</div>
    </div>
  );
  return (
    <div style={{position: 'absolute', left: x - w / 2, top, width: w, height: h, transform: `scale(${1 + land},${1 - land})`, transformOrigin: '50% 100%'}}>
      <div style={{position: 'absolute', left: -8, top: 10, width: w + 16, height: h - 4, borderRadius: 10, background: '#8A6A50'}} />
      {page('AUG', P.rose)}
      {p < 0.5 && <div style={{position: 'absolute', inset: 0, transform: `scaleY(${Math.cos(p * Math.PI)})`, transformOrigin: '50% 0%'}}>{page('JUL', P.indigo)}</div>}
      {p >= 0.5 && p < 1 && <div style={{position: 'absolute', left: 0, top: -h, width: w, height: h, borderRadius: 8, background: '#E8E2DA', transform: `scaleY(${-Math.cos(p * Math.PI)})`, transformOrigin: '50% 100%'}} />}
      {[22, 52, 82].map((rx) => (
        <div key={rx} style={{position: 'absolute', left: rx, top: -8, width: 10, height: 18, borderRadius: 5, background: '#5E5249'}} />
      ))}
    </div>
  );
};

/** counter "20 tools" and the 7-day week (s07) */
export const STATS = {x: 1000, y: -330};
const Stats: React.FC<{t: number}> = ({t}) => {
  if (t < 24.9 || t > 28.75) return null;
  const n = cardCount(t);
  const inS = pop(t, 24.9, 12, 0.45);
  const outS = 1 - easeInCubic(ramp(t, 28.35, 28.7));
  const twenty = t > 26.22 ? 1 + Math.exp(-(t - 26.22) * 6) * Math.sin((t - 26.22) * 20) * 0.18 : 1;
  const last = LAUNCH.map((_, i) => ARRIVE(i)).filter((a) => a <= t).pop() ?? 0;
  const tick = t - last < 0.2 ? 1 + 0.14 * Math.sin(((t - last) / 0.2) * Math.PI) : 1;
  const done = t > 26.22;
  const wk = pop(t, 27.12, 13, 0.45);
  const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  return (
    <div style={{position: 'absolute', left: STATS.x - 170, top: STATS.y - 90, width: 340, transform: `scale(${inS * outS})`, transformOrigin: '50% 50%'}}>
      <div style={{width: 340, height: 180, borderRadius: 28, background: done ? P.marigold : P.white, boxShadow: `inset 0 -10px 0 ${done ? P.marigoldDeep : '#E9DCCB'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16, transform: `scale(${twenty})`}}>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 120, color: done ? P.white : P.ink, lineHeight: 1, transform: `scale(${tick})`, minWidth: 140, textAlign: 'right'}}>{Math.max(3, n)}</div>
        <div style={{overflow: 'hidden', paddingBottom: 6}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 52, color: done ? P.white : P.inkSub, transform: `translateY(${(1 - easeOutQuint(ramp(t, 26.5, 26.95))) * 130}%)`}}>tools</div>
        </div>
      </div>
      {t > 27.12 && (
        <div style={{marginTop: 26, width: 340, borderRadius: 22, background: P.white, padding: '16px 16px 20px', boxSizing: 'border-box', boxShadow: 'inset 0 -8px 0 #E9DCCB', transform: `scale(${wk})`, transformOrigin: '50% 0%'}}>
          <div style={{overflow: 'hidden', textAlign: 'center'}}>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 52, color: P.ink, transform: `translateY(${(1 - easeOutQuint(ramp(t, 27.27, 27.75))) * 130}%)`}}>one week</div>
          </div>
          <div style={{display: 'flex', gap: 6, marginTop: 8, justifyContent: 'center'}}>
            {days.map((d, i) => {
              const f = ramp(t, 27.3 + i * 0.05, 27.42 + i * 0.05);
              return (
                <div key={i} style={{width: 38, height: 46, borderRadius: 9, background: f > 0 ? lerpColor('#EFE7DF', P.marigold, f) : '#EFE7DF', color: f > 0.5 ? P.white : P.inkSub, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 21, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${1 + Math.sin(f * Math.PI) * 0.25})`}}>
                  {d}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

const Foreground: React.FC<{t: number}> = ({t}) => (
  <>
    <svg style={{position: 'absolute', left: -560, top: 820, overflow: 'visible'}} width={400} height={400}>
      <g transform={`rotate(${Math.sin(t * 0.9) * 2} 200 400)`}>
        <ellipse cx={120} cy={170} rx={50} ry={140} transform="rotate(-35 120 170)" fill="#2C5A4A" />
        <ellipse cx={280} cy={160} rx={50} ry={140} transform="rotate(35 280 160)" fill="#24493D" />
        <ellipse cx={200} cy={120} rx={46} ry={150} fill="#335F50" />
      </g>
      <path d="M110 300 L290 300 L270 420 L130 420 Z" fill={P.coral} />
    </svg>
  </>
);

/* ---------- the whole workspace ---------- */
export const roomCam = (t: number): Cam => {
  const c = camAt(KEYS, t, 0);
  c.x += Math.sin(t * 0.31) * 7;
  c.y += Math.cos(t * 0.27) * 5;
  c.z *= 1 + Math.sin(t * 0.37) * 0.012;
  return c;
};

export const Workspace: React.FC<{
  t: number;
  wall?: React.ReactNode; // in the wall layer (depth WALL_D)
  mid?: React.ReactNode; // after the table, before the tower
  front?: React.ReactNode; // after the tower
  screen?: React.ReactNode; // screen space, above all
  shake?: [number, number];
  people?: React.ReactNode; // standing people beside the doctor
  hideCard?: number; // a card the scene draws itself
}> = ({t, wall, mid, front, screen, shake = [0, 0], people, hideCard = -1}) => {
  const cam = roomCam(t);
  const glow = screenGlow(t);
  const order = Array.from({length: 20}, (_, i) => i);
  if (t > T_FALL) {
    const F = fallData();
    order.sort((a, b) => (a === GOALS ? 1 : b === GOALS ? -1 : F[a].tl - F[b].tl));
  }
  const d = docPose(t);
  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      <div style={{position: 'absolute', inset: 0, transform: `translate(${shake[0]}px,${shake[1]}px)`}}>
        <World cam={cam} depth={WALL_D}>
          <Wall t={t} />
          <PosterArt t={t} />
          <NoteCard t={t} />
          {wall}
        </World>
        <World cam={cam} depth={1}>
          <Floor />
          <Lamp x={470} t={t} seed={1} on={1} />
          <Lamp x={1110} t={t} seed={2} on={1} />
          <Motes t={t} />
          {/* chairs + engineers */}
          {ENG_X.map((x, k) => (
            <div key={'ch' + k} style={{position: 'absolute', left: x - 92, top: 640, width: 184, height: 200, borderRadius: 30, background: '#5A4F6E'}} />
          ))}
          {ENG_X.map((x, k) => {
            const e = engPose(k, t);
            const who = [CAST.engA, CAST.engB, CAST.engC][k];
            return <Person key={'e' + k} {...who} t={t} x={x} y={ENG_Y} sitting {...e} />;
          })}
          {ENG_X.map((x, k) => (
            <div key={'gl' + k} style={{position: 'absolute', left: x - 120, top: 520, width: 240, height: 240, borderRadius: '50%', background: `radial-gradient(circle at 50% 70%, rgba(255,232,180,${0.42 * glow}) 0%, rgba(255,232,180,0) 65%)`}} />
          ))}
          {ENG_X.map((x, k) => (
            <Laptop key={'l' + k} x={x} glow={glow} t={t} k={k} />
          ))}
          <Table t={t} />
          <DeskCal t={t} />
          <Person {...CAST.teamDoctor} t={t} x={DOC_X} y={FLOOR} {...d} />
          {people}
          {mid}
          <Stats t={t} />
          {order.map((i) => {
            const c = cardPose(i, t);
            if (!c.vis || i === hideCard) return null;
            return <ToolCard key={i} i={i} x={c.x} y={c.y} r={c.r} sx={c.sx} sy={c.sy} />;
          })}
          {front}
        </World>
        <World cam={cam} depth={1.25}>
          <Foreground t={t} />
        </World>
      </div>
      {screen}
    </AbsoluteFill>
  );
};
/** screen-space projection of a depth-1 world point */
export const toScreen = (cam: Cam, x: number, y: number) => ({x: 960 + (x - cam.x) * cam.z, y: 540 + (y - cam.y) * cam.z});
export {kick};
