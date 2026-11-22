/* Shared workspace for scenes C and D (own copy; does not depend on B files).
   One continuous room, one camera, one tower of tool cards. Everything is a pure function of GLOBAL film seconds. */
import React from 'react';
import {AbsoluteFill, random} from 'remotion';
import {P, FONT_HEAD, World, Cam, CamKey, camAt, Person, Expr, clamp, lerp, ramp, mv, spring, easeOutCubic, easeInOutCubic, at} from '../kit';
import {CAST} from '../cast';

/* ---------- layout (world units, depth 1) ---------- */
export const FLOOR = 905;
export const TABLE_TOP = 760;
export const TABLE_L = 250;
export const TABLE_R = 830;
export const ENG_X = [420, 660];
export const ENG_Y = 795;
export const STU_X = 40;
export const TOWER_X = 1150;
export const CARD_W = 540;
export const CARD_H = 72;
export const WALL_D = 0.92;
export const PHONE = {x: 1800, y: 568, h: 600};

/* ---------- word times ---------- */
export const W = {
  so: at('s06', 'So'),
  engineers: at('s06', 'engineers'),
  do: at('s06', 'do'),
  we: at('s06', 'We'),
  everything: at('s06', 'everything'),
  syringe: at('s07', 'Syringe'),
  spinal: at('s07', 'Spinal'),
  sedation: at('s07', 'Sedation'),
  twenty: at('s07', 'Twenty'),
  tools: at('s07', 'tools'),
  two: at('s07', 'two'),
  days: at('s07', 'days'),
  then: at('s08', 'Then'),
  shirin: at('s08', 'Shirin'),
  medical: at('s08', 'medical'),
  asked: at('s08', 'asked'),
  question: at('s08', 'question'),
  why: at('s09', 'Why'),
  family: at('s09', 'family'),
  install: at('s09', 'install'),
  this: at('s09', 'this'),
  she: at('s10', 'She'),
  right: at('s10', 'right'),
  wed: at('s10', "We'd"),
  us: at('s10', 'us'),
  not: at('s10', 'not'),
  them: at('s10', 'them'),
  so11: at('s11', 'So'),
  changed: at('s11', 'changed'),
  one: at('s11', 'one'),
  switch: at('s11', 'switch'),
  time: at('s11', 'time'),
  doctor: at('s11a', 'Doctor'),
  became: at('s11a', 'became'),
  app: at('s11a', 'app'),
  patients: at('s11a', 'patients'),
  families: at('s11a', 'families'),
  when: at('s11b', 'When'),
  shortlisted: at('s11b', 'shortlisted'),
  basith: at('s11b', 'Basith'),
  awaiz: at('s11b', 'Awaiz'),
  rebuilt: at('s11b', 'rebuilt'),
  real: at('s11b', 'real'),
  android: at('s11b', 'Android'),
  and11c: at('s11c', 'And'),
  doctors: at('s11c', 'doctors'),
  pulling: at('s11c', 'pulling'),
  back: at('s11c', 'back'),
  oneThing: at('s11c', 'one'),
  thing: at('s11c', 'thing'),
  goals: at('s11c', 'goals'),
  care: at('s11c', 'care'),
  talk: at('s11c', 'talk'),
};

/* ---------- camera: one key list for C and D ---------- */
export const KEYS: CamKey[] = [
  {t: 28.42, x: 560, y: 560, z: 1.9},
  {t: 28.9, x: 545, y: 572, z: 1.5},
  {t: 29.6, x: 540, y: 578, z: 1.55},
  {t: 30.5, x: 545, y: 582, z: 1.6},
  {t: 30.95, x: 548, y: 586, z: 1.68},
  {t: 31.25, x: 560, y: 586, z: 1.64},
  {t: 31.75, x: 860, y: 610, z: 1.12},
  {t: 32.9, x: 875, y: 595, z: 1.14},
  {t: 33.3, x: 890, y: 570, z: 1.1},
  {t: 34.6, x: 900, y: 545, z: 1.08},
  {t: 35.0, x: 930, y: 505, z: 1.04},
  {t: 35.45, x: 950, y: 460, z: 1.0},
  {t: 36.55, x: 1330, y: 140, z: 0.61},
  {t: 37.2, x: 1340, y: 130, z: 0.62},
  {t: 37.6, x: 1350, y: 135, z: 0.635},
  {t: 38.5, x: 1355, y: 140, z: 0.65},
  {t: 39.0, x: 400, y: 540, z: 1.0},
  {t: 40.5, x: 410, y: 535, z: 1.03},
  {t: 40.95, x: 420, y: 520, z: 1.05},
  {t: 41.75, x: 430, y: 505, z: 1.04},
  {t: 42.25, x: 520, y: 330, z: 0.78},
  {t: 43.65, x: 540, y: 340, z: 0.8},
  {t: 44.15, x: 1180, y: 380, z: 0.66},
  {t: 44.85, x: 1190, y: 390, z: 0.67},
  {t: 45.3, x: 1060, y: 410, z: 0.73},
  {t: 47.1, x: 1075, y: 410, z: 0.76},
  {t: 47.95, x: 1800, y: 520, z: 1.0},
  {t: 52.5, x: 1800, y: 518, z: 1.02},
  {t: 57.0, x: 1800, y: 522, z: 1.03},
  {t: 59.6, x: 1800, y: 525, z: 1.05},
  {t: 61.1, x: 1800, y: 525, z: 1.06},
];
export const roomCam = (t: number): Cam => {
  const c = camAt(KEYS, t, 0);
  c.x += Math.sin(t * 0.31) * 6;
  c.y += Math.cos(t * 0.27) * 4;
  c.z *= 1 + Math.sin(t * 0.37) * 0.01;
  return c;
};
/** screen-space projection of a depth-1 world point */
export const toScreen = (cam: Cam, x: number, y: number) => ({x: 960 + (x - cam.x) * cam.z, y: 540 + (y - cam.y) * cam.z});

/* ---------- tower: 20 real tool cards ---------- */
export const LABELS = [
  'Doctor screen 1',
  'Doctor screen 2',
  'Doctor screen 3',
  'Doctor screen 4',
  'Doctor screen 5',
  'Doctor screen 6',
  'Doctor screen 7',
  'Doctor screen 8',
  'Doctor screen 9',
  'Doctor screen 10',
  'Doctor screen 11',
  'Doctor screen 12',
  'Doctor screen 13',
  'Doctor screen 14',
  'Doctor screen 15',
  'Doctor screen 16',
  'Doctor screen 17',
  'Doctor screen 18',
  'Doctor screen 19',
  'Doctor screen 20',
];
const COLS = [P.indigo, P.rose, P.plum, P.coral, P.marigold, P.sky, P.sageDark, P.rose, P.sage, P.indigo, P.plum, P.marigold, P.coral, P.sky, P.sageDark, P.indigo, P.rose, P.plum, P.coral, P.marigold];
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
export const cardColor = (i: number) => COLS[i];
const lightCard = (c: string) => c === P.marigold || c === P.sky;

const FAST0 = 35.62;
export const ARRIVE: number[] = [W.syringe + 0.14, W.spinal + 0.14, W.sedation + 0.14, ...Array.from({length: 17}, (_, k) => FAST0 + (k * (W.twenty - FAST0)) / 16)];
export const FLIGHT = (i: number) => (i < 3 ? 0.46 : 0.3);
export const LAUNCH = ARRIVE.map((a, i) => a - FLIGHT(i));
export const cardCount = (t: number) => ARRIVE.filter((a) => t >= a).length;

const rnd = (s: string) => random('croom-' + s);
const restX = (i: number) => TOWER_X + (rnd('cx' + i) - 0.5) * 34;
const restY = (i: number) => FLOOR - CARD_H / 2 - i * CARD_H;
const restR = (i: number) => (rnd('cr' + i) - 0.5) * 4;

export const T_CREAK = W.install - 0.1;
export const T_FALL = W.right;
const sway = (t0: number) => {
  const t = Math.min(t0, T_FALL);
  const n = cardCount(t);
  let a = 18 * Math.pow(n / 20, 1.5) * Math.sin(t * 2.3);
  for (let j = 0; j < 20; j++) {
    const d = t - ARRIVE[j];
    if (d > 0 && d < 1.2) a += 9 * (j / 20) * Math.exp(-d * 4) * Math.sin(d * 13);
  }
  const c = ramp(t, T_CREAK, T_CREAK + 0.9);
  a += c * (20 * Math.sin(t * 6.2) + 7 * Math.sin(t * 23));
  // anticipation: lean back to the left before it goes
  a -= 40 * mv(t, T_FALL - 0.4, T_FALL);
  return a;
};
type Pose = {x: number; y: number; r: number; sx: number; sy: number; vis: boolean; o?: number};
const standing = (i: number, t: number) => {
  const f = (i + 0.5) / 20;
  const a = sway(t);
  let x = restX(i) + a * f * f;
  let y = restY(i);
  let r = restR(i) + ((a * 2 * f) / 1440) * 57.3;
  if (t > T_CREAK && t < T_FALL + 0.2 && i > 3 && i < 17) {
    const k = ramp(t, T_CREAK + 0.3, T_FALL - 0.2);
    x += (random('jx' + i + Math.floor(t * 30)) - 0.5) * 6 * k;
    r += (random('jr' + i + Math.floor(t * 30)) - 0.5) * 1.8 * k;
  }
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
const tFor = (vy: number, dy: number) => (-vy + Math.sqrt(vy * vy + 2 * G * Math.max(0, dy))) / G;
const fallData = (): Fall[] => {
  if (FALL) return FALL;
  const out: Fall[] = [];
  for (let i = 0; i < 20; i++) {
    const td = detachT(i);
    const a = standing(i, td);
    const b = standing(i, td - 0.02);
    let vx = (a.x - b.x) / 0.02 + (rnd('ix' + i) - 0.3) * 800;
    const vy = (a.y - b.y) / 0.02 - rnd('iy' + i) * 520;
    const spin = (rnd('sp' + i) - 0.4) * 380;
    const yf = FLOOR - CARD_H / 2;
    const T = tFor(vy, yf - a.y);
    let xl = a.x + vx * T;
    const c = clamp(xl, 1330, 2420);
    if (c !== xl) {
      xl = c;
      vx = (xl - a.x) / T;
    }
    out.push({td, xd: a.x, yd: a.y, rd: a.r, vx, vy, spin, T, tl: td + T, xl, yl: yf, rl: a.r + spin * T, rf: 0, vyl: vy + G * T});
  }
  const order = out.map((_, i) => i).sort((p, q) => out[p].tl - out[q].tl);
  const landed: number[] = [];
  for (const i of order) {
    const o = out[i];
    const k = landed.filter((j) => Math.abs(out[j].xl - o.xl) < 280).length;
    const yl = FLOOR - CARD_H / 2 - Math.min(k, 3) * 18;
    if (yl !== o.yl) {
      const T = tFor(o.vy, yl - o.yd);
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
    o.rf = clamp(o.rl * 0.25, -16, 16) + (rnd('rf' + i) - 0.5) * 12;
  });
  FALL = out;
  return out;
};
export const landTimes = () => fallData().map((f) => f.tl);

/* gather: the heap re-forms into the phone (s11a) */
export const T_GATHER = W.became + 0.06;
const gatherOrder = (() => {
  const xs = Array.from({length: 20}, (_, i) => i);
  return xs;
})();
export const gatherT = (i: number) => T_GATHER + gatherOrder.indexOf(i) * 0.015;
export const GATHER_DUR = 0.4;
export const T_PHONE_FULL = T_GATHER + 19 * 0.015 + GATHER_DUR;

export const cardPose = (i: number, t: number): Pose => {
  const L = LAUNCH[i];
  const A = ARRIVE[i];
  if (t < L) return {x: 0, y: 0, r: 0, sx: 0, sy: 0, vis: false};
  if (t < A) {
    const u = (t - L) / (A - L);
    const k = i % 2;
    const x0 = ENG_X[k];
    const y0 = 700;
    const x1 = restX(i);
    const y1 = restY(i);
    const ux = easeOutCubic(u);
    const arc = i < 3 ? 320 : 240;
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
  const x = F.xl + F.vx * 0.07 * (1 - Math.exp(-d * 5));
  const y = F.yl - by + sq * 20;
  const r = F.rl + (F.rf - F.rl) * spring(t, F.tl, 15, 0.45);
  const g0 = gatherT(i);
  if (t > g0) {
    // anticipation hop, then fly into the phone screen
    const u = ramp(t, g0, g0 + GATHER_DUR);
    if (u >= 1) return {x: 0, y: 0, r: 0, sx: 0, sy: 0, vis: false};
    const p = easeInOutCubic(u);
    const s = lerp(1, 0.16, p);
    return {x: lerp(x, PHONE.x, p), y: lerp(y, PHONE.y + 40, p) - 260 * Math.sin(Math.PI * u), r: lerp(r, 0, p) + Math.sin(u * Math.PI) * 30, sx: s, sy: s, vis: true};
  }
  // little shiver before they go
  const pre = ramp(t, g0 - 0.5, g0);
  return {x: x + Math.sin(t * 40 + i) * 3 * pre, y: y - 6 * pre, r, sx: 1 + sq, sy: 1 - sq, vis: true};
};

export const Glyph: React.FC<{i: number; c: string; s?: number}> = ({i, c, s = 46}) => {
  const k = i % 5;
  return (
    <svg width={s} height={s} viewBox="-20 -20 40 40" style={{flex: 'none'}}>
      <circle r={19} fill={P.white} />
      {k === 0 && <path d="M-3 -10 H3 V-3 H10 V3 H3 V10 H-3 V3 H-10 V-3 H-3 Z" fill={c} />}
      {k === 1 && <path d="M0 -11 C6 -3 9 1 9 5 A9 9 0 0 1 -9 5 C-9 1 -6 -3 0 -11 Z" fill={c} />}
      {k === 2 && <path d="M-11 2 L-5 2 L-2 -7 L2 9 L5 2 L11 2" stroke={c} strokeWidth={3.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />}
      {k === 3 && <path d="M0 9 C-14 0 -9 -11 0 -5 C9 -11 14 0 0 9 Z" fill={c} />}
      {k === 4 && <circle r={7} fill={c} />}
    </svg>
  );
};

export const ToolCard: React.FC<{i: number; x: number; y: number; r: number; sx: number; sy: number; glow?: number; label?: string; color?: string}> = ({i, x, y, r, sx, sy, glow = 0, label, color}) => {
  const c = color ?? cardColor(i);
  const ink = lightCard(c) ? P.ink : P.white;
  const lab = label ?? LABELS[i];
  const fs = lab.length > 22 ? 30 : lab.length > 17 ? 33 : 36;
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
      <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: fs, color: ink, whiteSpace: 'nowrap', paddingTop: 4}}>{lab}</div>
    </div>
  );
};

/* ---------- engineer poses (sitting at the table) ---------- */
type EP = {expr: Expr; look: number; armL: number; armR: number; elbowL: number; elbowR: number; lean: number; headTilt: number; talk: boolean; squashY: number};
export const engPose = (k: number, t0: number): EP => {
  const t = t0 - k * 0.06;
  const p: EP = {expr: 'happy', look: k === 0 ? 1 : -1, armL: 6, armR: 6, elbowL: 0, elbowR: 0, lean: 0, headTilt: k === 0 ? 4 : -4, talk: false, squashY: 1};
  if (t < 38.4) {
    // grin at each other, then anticipation and the knuckle stretch on "do"
    const ant = mv(t, W.do - 0.42, W.do - 0.04);
    const go = spring(t, W.do, 13, 0.4);
    const off = mv(t, W.we - 0.15, W.we + 0.1);
    const lift = go * (1 - off);
    p.lean = -5 * ant * (1 - go);
    p.squashY = 1 - 0.04 * ant * (1 - go);
    p.armL = lerp(6 - 4 * ant, 150, lift);
    p.armR = lerp(6 - 4 * ant, 150, lift);
    p.elbowL = -28 * lift;
    p.elbowR = -28 * lift;
    if (t > W.do - 0.4) p.headTilt = 0;
    if (t > W.we) {
      const typ = Math.sin(t * 22 + k * 2) * 3;
      p.armL = 8 + typ;
      p.armR = 8 - typ;
      p.look = t > W.everything + 0.5 ? 1 : 0;
      p.expr = t > W.everything && t < W.everything + 0.45 ? 'surprised' : 'happy';
      p.lean = 2 * Math.sin(t * 3 + k);
    }
    // cheer on "Twenty"
    if (t > W.twenty - 0.1) {
      const c = spring(t, W.twenty - 0.1, 12, 0.4) * (1 - mv(t, W.days + 0.2, W.days + 0.5));
      p.armR = lerp(p.armR, 165, c);
      p.elbowR = -12 * c;
      if (k === 1) {
        p.armL = lerp(p.armL, 160, c);
        p.elbowL = -12 * c;
      }
      p.look = 1;
    }
    return p;
  }
  // scene D: look to Shirin, the question lands, the tower goes
  p.look = -1;
  p.expr = 'neutral';
  p.headTilt = 0;
  if (t > W.why) p.expr = 'think';
  if (t > W.install) {
    p.look = 1;
    p.expr = 'worried';
    p.headTilt = -5;
  }
  if (t > T_FALL) {
    const w = spring(t, T_FALL, 12, 0.4);
    p.expr = 'surprised';
    p.lean = -9 * w * (1 - mv(t, T_FALL + 0.7, T_FALL + 1.0));
    p.armL = lerp(6, 40, w);
    p.armR = lerp(6, 40, w);
    p.elbowL = 110 * w;
    p.elbowR = 110 * w;
  }
  if (t > W.wed - 0.1) {
    const d = mv(t, W.wed - 0.1, W.wed + 0.25);
    p.expr = 'sad';
    p.look = k === 0 ? 0.6 : -0.2;
    p.headTilt = 7 * d;
    p.armL = lerp(40, 6, d);
    p.armR = lerp(40, 6, d);
    p.elbowL = lerp(110, 0, d);
    p.elbowR = lerp(110, 0, d);
  }
  if (t > W.not) {
    p.expr = 'think';
    p.look = 0;
    p.headTilt = -4;
  }
  if (t > W.them + 0.1) {
    p.expr = 'happy';
    p.headTilt = 4 * (k ? -1 : 1);
  }
  return p;
};

/** laptop screen light */
export const screenGlow = (t: number) => {
  let g = 0.25 + 0.75 * mv(t, W.we - 0.1, W.we + 0.2);
  g *= 1 + 0.8 * Math.exp(-Math.max(0, t - W.everything) * 3.5) * (t > W.everything ? 1 : 0);
  g *= 1 - 0.55 * mv(t, T_FALL, T_FALL + 0.4);
  g *= 1 + 0.55 * mv(t, W.them, W.them + 0.4);
  return g * (0.9 + 0.1 * Math.sin(t * 7));
};

/* ---------- room drawing ---------- */
const Wall: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -2000, top: -2200, width: 6400, height: 3700, background: P.creamWarm}} />
    <div style={{position: 'absolute', left: -2000, top: 640, width: 6400, height: 900, background: P.sand}} />
    <div style={{position: 'absolute', left: -2000, top: 632, width: 6400, height: 14, background: '#E4D2B7'}} />
    {/* painted arches */}
    <div style={{position: 'absolute', left: 150, top: -40, width: 780, height: 690, borderRadius: '390px 390px 0 0', background: P.sageLight}} />
    <div style={{position: 'absolute', left: 1420, top: 60, width: 760, height: 590, borderRadius: '380px 380px 0 0', background: '#F6E4C6'}} />
    <div style={{position: 'absolute', left: 2300, top: 120, width: 560, height: 530, borderRadius: '280px 280px 0 0', background: P.roseLight}} />
    {/* banner */}
    <div style={{position: 'absolute', left: -1500, top: -640, width: 700, height: 110, borderRadius: 18, background: P.plum, boxShadow: 'inset 0 -10px 0 #6F456A', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 58, color: P.cream}}>
      Health-a-thon 2026
    </div>
    {[-1420, -880].map((x) => (
      <div key={x} style={{position: 'absolute', left: x, top: -1100, width: 6, height: 462, background: '#B9A68C'}} />
    ))}
    {/* bunting */}
    <svg style={{position: 'absolute', left: 1500, top: -760, overflow: 'visible'}} width={1500} height={200}>
      <path d="M0 0 Q750 160 1500 0" stroke="#B9A68C" strokeWidth={5} fill="none" />
      {Array.from({length: 13}, (_, i) => {
        const u = (i + 0.5) / 13;
        const x = u * 1500;
        const y = 4 * 80 * u * (1 - u);
        const sw = Math.sin(t * 2 + i) * 4;
        const c = [P.marigold, P.rose, P.sage, P.plum][i % 4];
        return <path key={i} d={`M${x - 34} ${y} L${x + 34} ${y} L${x + sw} ${y + 70} Z`} fill={c} />;
      })}
    </svg>
    {/* window on the far left */}
    <div style={{position: 'absolute', left: -900, top: -120, width: 560, height: 640, borderRadius: 26, background: P.white, padding: 18, boxSizing: 'border-box'}}>
      <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: 12, overflow: 'hidden', background: 'linear-gradient(180deg,#BFDDEE 0%,#E3EEF0 60%,#F6E9D4 100%)'}}>
        <svg width={524} height={604} style={{position: 'absolute', left: 0, top: 0}}>
          {[0, 1, 2].map((k) => {
            const x = ((80 + k * 210 + t * (8 + k * 3)) % 700) - 120;
            return (
              <g key={k} transform={`translate(${x} ${90 + k * 70}) scale(${0.7 + k * 0.2})`} fill="#FFFFFF">
                <ellipse cx={0} cy={0} rx={60} ry={24} />
                <ellipse cx={-30} cy={-10} rx={34} ry={26} />
                <ellipse cx={22} cy={-18} rx={36} ry={30} />
              </g>
            );
          })}
          <rect x={30} y={400} width={110} height={220} fill="#E6D3BC" />
          <rect x={160} y={350} width={90} height={270} fill="#DCC3AA" />
          <g transform={`rotate(${Math.sin(t * 1.1) * 1.5} 380 620)`}>
            <rect x={368} y={470} width={24} height={150} fill="#7A5A44" />
            <circle cx={380} cy={430} r={90} fill={P.sage} />
            <circle cx={325} cy={475} r={58} fill={P.sageDark} />
          </g>
        </svg>
        <div style={{position: 'absolute', left: 252, top: 0, width: 18, height: '100%', background: P.white}} />
        <div style={{position: 'absolute', left: 0, top: 290, width: '100%', height: 18, background: P.white}} />
      </div>
    </div>
    {/* pinboard with sticky notes */}
    <div style={{position: 'absolute', left: 310, top: 160, width: 460, height: 290, borderRadius: 16, background: '#B98F5E', padding: 14, boxSizing: 'border-box'}}>
      <div style={{width: '100%', height: '100%', borderRadius: 8, background: '#D9B98C', position: 'relative'}}>
        {[
          [24, 26, P.marigold, -6],
          [36, 150, P.roseLight, 5],
          [330, 20, P.mint, 4],
          [318, 140, '#F3D9A0', -5],
          [176, 70, P.white, 2],
        ].map(([x, y, c, r], i) => (
          <div key={i} style={{position: 'absolute', left: x as number, top: y as number, width: 76, height: 76, background: c as string, transform: `rotate(${(r as number) + Math.sin(t * 1.3 + i) * 0.8}deg)`, boxShadow: 'inset 0 -6px 0 rgba(0,0,0,0.06)'}}>
            <div style={{position: 'absolute', left: 13, top: 26, width: 48, height: 6, borderRadius: 3, background: 'rgba(56,46,41,0.25)'}} />
            <div style={{position: 'absolute', left: 13, top: 41, width: 34, height: 6, borderRadius: 3, background: 'rgba(56,46,41,0.25)'}} />
            <div style={{position: 'absolute', left: 31, top: -6, width: 14, height: 14, borderRadius: 7, background: [P.rose, P.indigo, P.coral, P.sage, P.plum][i]}} />
          </div>
        ))}
      </div>
    </div>
    {/* shelf on the right with plant and books */}
    <div style={{position: 'absolute', left: 2560, top: 420, width: 420, height: 22, borderRadius: 6, background: '#C99A62'}} />
    {[
      [2590, 60, 150, P.indigo],
      [2655, 46, 124, P.rose],
      [2705, 56, 160, P.marigold],
    ].map(([x, w, h, c], i) => (
      <div key={i} style={{position: 'absolute', left: x as number, top: 420 - (h as number), width: w as number, height: h as number, borderRadius: 6, background: c as string}} />
    ))}
    <div style={{position: 'absolute', left: 2830, top: 350, width: 90, height: 70, borderRadius: '8px 8px 22px 22px', background: P.coral}} />
    <svg style={{position: 'absolute', left: 2800, top: 230, overflow: 'visible'}} width={150} height={130}>
      <g transform={`rotate(${Math.sin(t * 1.3) * 3} 75 125)`}>
        <ellipse cx={45} cy={70} rx={22} ry={48} transform="rotate(-30 45 70)" fill={P.sage} />
        <ellipse cx={105} cy={70} rx={22} ry={48} transform="rotate(30 105 70)" fill={P.sageDark} />
        <ellipse cx={75} cy={50} rx={20} ry={55} fill="#4C8A74" />
      </g>
    </svg>
  </>
);

const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -2200, top: FLOOR - 4, width: 6800, height: 1600, background: '#E2CDB0'}} />
    {[960, 1040, 1160, 1330].map((y, i) => (
      <div key={y} style={{position: 'absolute', left: -2200, top: y, width: 6800, height: 3 + i, background: 'rgba(160,120,80,0.16)'}} />
    ))}
    {Array.from({length: 22}, (_, i) => (
      <div key={i} style={{position: 'absolute', left: -1600 + i * 300 + (i % 2) * 140, top: 960, width: 3, height: 80, background: 'rgba(160,120,80,0.14)'}} />
    ))}
    <div style={{position: 'absolute', left: -2200, top: FLOOR - 14, width: 6800, height: 16, background: '#D8BF9C'}} />
    <div style={{position: 'absolute', left: TABLE_L - 40, top: 896, width: TABLE_R - TABLE_L + 80, height: 26, borderRadius: '50%', background: 'rgba(120,80,50,0.16)'}} />
  </>
);

const Lamp: React.FC<{x: number; t: number; seed: number}> = ({x, t, seed}) => {
  const sw = Math.sin(t * 1.2 + seed) * 1.6;
  return (
    <div style={{position: 'absolute', left: x, top: -2000, width: 0, height: 0, transform: `rotate(${sw}deg)`, transformOrigin: '0 0'}}>
      <div style={{position: 'absolute', left: -3, top: 0, width: 6, height: 2040, background: '#6B5A4E'}} />
      <svg style={{position: 'absolute', left: -380, top: 2040, overflow: 'visible'}} width={760} height={900}>
        <defs>
          <linearGradient id={'ccone' + seed} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FFE4A8" stopOpacity={0.5} />
            <stop offset="1" stopColor="#FFE4A8" stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d="M330 60 L430 60 L640 700 L120 700 Z" fill={`url(#ccone${seed})`} />
        <path d="M330 0 L430 0 L480 64 L280 64 Z" fill={P.marigold} />
        <path d="M400 0 L430 0 L480 64 L430 64 Z" fill={P.marigoldDeep} />
        <ellipse cx={380} cy={66} rx={34} ry={12} fill="#FFF3D6" />
      </svg>
    </div>
  );
};

const Motes: React.FC<{t: number}> = ({t}) => (
  <>
    {Array.from({length: 30}, (_, i) => {
      const bx = -500 + random('cmx' + i) * 3300;
      const by = -400 + random('cmy' + i) * 1200;
      const x = bx + Math.sin(t * 0.4 + i) * 40 + t * 6;
      const y = by + Math.cos(t * 0.33 + i * 2) * 30 - ((t * 10 + i * 30) % 120);
      const r = 2 + random('cmr' + i) * 3;
      return <div key={i} style={{position: 'absolute', left: x, top: y, width: r * 2, height: r * 2, borderRadius: r, background: 'rgba(255,240,205,0.85)'}} />;
    })}
  </>
);

const lerpColor = (a: string, b: string, u: number) => {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return `rgb(${pa.map((v, i) => Math.round(lerp(v, pb[i], clamp(u)))).join(',')})`;
};
export {lerpColor};

const Laptop: React.FC<{x: number; glow: number; k: number}> = ({x, glow, k}) => (
  <>
    <div style={{position: 'absolute', left: x - 82, top: TABLE_TOP - 6, width: 164, height: 8, borderRadius: 4, background: '#2E3646'}} />
    <div style={{position: 'absolute', left: x - 72, top: TABLE_TOP - 78, width: 144, height: 74, borderRadius: '10px 10px 3px 3px', background: '#3E4A5E', boxShadow: `0 -5px 0 rgba(255,214,140,${Math.min(1, 0.9 * glow)})`}}>
      <div style={{position: 'absolute', left: 61, top: 26, width: 22, height: 22, borderRadius: 11, background: lerpColor('#8C96A8', '#FFE6B0', glow)}} />
      <div style={{position: 'absolute', left: 12, top: 10, width: 18, height: 12, borderRadius: 3, background: [P.rose, P.mint][k], transform: 'rotate(-8deg)'}} />
      <div style={{position: 'absolute', left: 108, top: 46, width: 16, height: 16, borderRadius: 8, background: [P.marigold, P.sky][k]}} />
    </div>
  </>
);

const Table: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: TABLE_L, top: TABLE_TOP, width: TABLE_R - TABLE_L, height: 24, borderRadius: 8, background: '#C98F55'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 22, width: TABLE_R - TABLE_L - 40, height: 98, background: '#B37C46'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 100, width: TABLE_R - TABLE_L - 40, height: 20, background: '#9C6A3B'}} />
    {[TABLE_L + 30, TABLE_R - 62].map((x) => (
      <div key={x} style={{position: 'absolute', left: x, top: TABLE_TOP + 110, width: 32, height: FLOOR - TABLE_TOP - 108, background: '#9C6A3B'}} />
    ))}
    {/* mug with steam */}
    <div style={{position: 'absolute', left: 520, top: TABLE_TOP - 46, width: 40, height: 46, borderRadius: '4px 4px 12px 12px', background: P.rose}} />
    <div style={{position: 'absolute', left: 550, top: TABLE_TOP - 38, width: 22, height: 22, borderRadius: 11, border: `6px solid ${P.rose}`, boxSizing: 'border-box'}} />
    <svg style={{position: 'absolute', left: 510, top: TABLE_TOP - 130, overflow: 'visible'}} width={60} height={80}>
      {[0, 1].map((k) => {
        const ph = (t * 0.7 + k * 0.5) % 1;
        return <path key={k} d={`M${18 + k * 18} 80 q 10 -20 0 -40 q -10 -20 0 -40`} stroke="rgba(255,255,255,0.8)" strokeWidth={5} fill="none" strokeLinecap="round" opacity={Math.sin(ph * Math.PI)} transform={`translate(0 ${-ph * 20})`} />;
      })}
    </svg>
    {/* sticky-note stack and a pen */}
    <div style={{position: 'absolute', left: 290, top: TABLE_TOP - 18, width: 56, height: 18, borderRadius: 3, background: P.marigold, transform: 'rotate(-4deg)'}} />
    <div style={{position: 'absolute', left: 740, top: TABLE_TOP - 9, width: 70, height: 9, borderRadius: 5, background: P.indigo, transform: 'rotate(6deg)'}} />
  </>
);

const Foreground: React.FC<{t: number}> = ({t}) => (
  <svg style={{position: 'absolute', left: -760, top: 800, overflow: 'visible'}} width={400} height={400}>
    <g transform={`rotate(${Math.sin(t * 0.9) * 2} 200 400)`}>
      <ellipse cx={120} cy={170} rx={50} ry={140} transform="rotate(-35 120 170)" fill="#2C5A4A" />
      <ellipse cx={280} cy={160} rx={50} ry={140} transform="rotate(35 280 160)" fill="#24493D" />
      <ellipse cx={200} cy={120} rx={46} ry={150} fill="#335F50" />
    </g>
    <path d="M110 300 L290 300 L270 420 L130 420 Z" fill={P.coral} />
  </svg>
);

/* ---------- the whole workspace ---------- */
export const Studio: React.FC<{
  t: number;
  wall?: React.ReactNode;
  back?: React.ReactNode; // depth 1, behind the table (e.g. a board on the wall)
  mid?: React.ReactNode; // after the table, before the tower
  front?: React.ReactNode; // after the tower
  screen?: React.ReactNode;
  shake?: [number, number];
  people?: React.ReactNode;
}> = ({t, wall, back, mid, front, screen, shake = [0, 0], people}) => {
  const cam = roomCam(t);
  const glow = screenGlow(t);
  const order = Array.from({length: 20}, (_, i) => i);
  if (t > T_FALL) {
    const F = fallData();
    order.sort((a, b) => F[a].tl - F[b].tl);
  }
  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      <div style={{position: 'absolute', inset: 0, transform: `translate(${shake[0]}px,${shake[1]}px)`}}>
        <World cam={cam} depth={WALL_D}>
          <Wall t={t} />
          {wall}
        </World>
        <World cam={cam} depth={1}>
          <Floor />
          {back}
          <Lamp x={330} t={t} seed={1} />
          <Lamp x={760} t={t} seed={2} />
          <Motes t={t} />
          {ENG_X.map((x, k) => (
            <div key={'ch' + k} style={{position: 'absolute', left: x - 92, top: 585, width: 184, height: 200, borderRadius: 30, background: '#5A4F6E'}} />
          ))}
          {ENG_X.map((x, k) => {
            const e = engPose(k, t);
            const who = [CAST.engA, CAST.engB][k];
            return <Person key={'e' + k} {...who} t={t} x={x} y={ENG_Y} sitting {...e} />;
          })}
          {ENG_X.map((x, k) => (
            <div key={'gl' + k} style={{position: 'absolute', left: x - 130, top: 470, width: 260, height: 260, borderRadius: '50%', background: `radial-gradient(circle at 50% 70%, rgba(255,232,180,${Math.min(0.75, 0.45 * glow)}) 0%, rgba(255,232,180,0) 65%)`}} />
          ))}
          {ENG_X.map((x, k) => (
            <Laptop key={'l' + k} x={x} glow={glow} k={k} />
          ))}
          <Table t={t} />
          {people}
          {mid}
          {order.map((i) => {
            const c = cardPose(i, t);
            if (!c.vis) return null;
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
