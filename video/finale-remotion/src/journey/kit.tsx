/* Shared kit for the Sahara journey film. Every visual is a pure function of the frame. */
import React from 'react';
import {Freeze, OffthreadVideo, random, Sequence, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import words from './words.json';
import baseWords from './base_words.json';
import edl from './edl.json';

export const FPS = 30;
export const TOTAL_SEC = (words as any).total as number;
export const TOTAL_FRAMES = Math.ceil(TOTAL_SEC * FPS);
/** Scenes were animated against base_words.json (the "base" timeline). The narration now follows words.json.
 *  edl.json is an edit list over film time: each segment either maps film time linearly onto a base-timeline span
 *  (cuts, skips and speed changes allowed) or shows an inserted shot by name. */
export const BASE_TOTAL = (baseWords as any).total as number;
type Seg = {n0: number; n1: number; b0?: number; b1?: number; insert?: string};
const SEGS = edl as Seg[];
export const segAt = (t: number): Seg => SEGS.find((s) => t >= s.n0 && t < s.n1) ?? SEGS[SEGS.length - 1];
/** film (narration) seconds -> scene timeline seconds (inside an insert: the base time it holds on) */
export const toBase = (t: number) => {
  const s = segAt(t);
  if (s.insert || s.b0 === undefined || s.b1 === undefined) return s.b0 ?? 0;
  return s.b0 + ((t - s.n0) / (s.n1 - s.n0)) * (s.b1 - s.b0);
};
/** first film time that shows base time b (approximate inverse, used only for rough lookups) */
export const toNew = (b: number) => {
  for (const s of SEGS) if (!s.insert && s.b0 !== undefined && s.b1 !== undefined && b >= s.b0 && b < s.b1) return s.n0 + ((b - s.b0) / (s.b1 - s.b0)) * (s.n1 - s.n0);
  return b;
};
export const FONT_HEAD = "'SaharaHead','SaharaSans',sans-serif";
export const FONT_BODY = "'SaharaSans',sans-serif";

/* ---------- palette (Sahara brand, extended for illustration) ---------- */
export const P = {
  // night / emergency room
  night0: '#0E1A2B',
  night1: '#16273D',
  night2: '#203852',
  nightWall: '#1B3048',
  nightFloor: '#122236',
  lampWarm: '#F2B35E',
  monitor: '#5FE3C0',
  // day / Sahara
  cream: '#FFF8EE',
  creamWarm: '#FBF1E3',
  sand: '#EFE2CF',
  well: '#EFE7DF',
  sage: '#3B7562',
  sageDark: '#2C5A4A',
  band: '#335F50',
  sageLight: '#DDEBE3',
  mint: '#9FD1B8',
  marigold: '#E0A040',
  marigoldDeep: '#B07A2E',
  rose: '#C46A84',
  roseLight: '#F5E3E8',
  plum: '#8C5A86',
  sky: '#7FB3D9',
  indigo: '#5A6FA8',
  coral: '#E8826A',
  ink: '#382E29',
  inkSub: '#5E5249',
  white: '#FFFFFF',
  // skin / hair
  skin1: '#C68A5E',
  skin2: '#A86F48',
  skin3: '#E0AC82',
  skin4: '#8E5B3A',
  hair: '#2A1E1A',
  hairGrey: '#B9B2AC',
};

/* ---------- time helpers ---------- */
type Word = {id: string; w: string; s: number; e: number};
const W: Word[] = (baseWords as any).words; // scene cues live on the base timeline
const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9']/g, '');
/** seconds when word `w` (nth occurrence, default first) of sentence `id` starts. at('s07','Twenty') */
export const at = (id: string, w?: string, nth = 0): number => {
  const ws = W.filter((x) => x.id === id);
  if (!ws.length) throw new Error('no sentence ' + id);
  if (!w) return ws[0].s;
  const hits = ws.filter((x) => norm(x.w) === norm(w));
  if (!hits.length) throw new Error(`no word ${w} in ${id}`);
  return hits[Math.min(nth, hits.length - 1)].s;
};
/** seconds when sentence `id` ends (last word end). */
export const endOf = (id: string) => {
  const ws = W.filter((x) => x.id === id);
  return ws[ws.length - 1].e;
};
/** current time in seconds (scene-global: use inside the film, not inside a Sequence with an offset unless you pass offset). */
export const useT = (offsetSec = 0) => useCurrentFrame() / FPS + offsetSec;

/* ---------- easing ---------- */
export const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const ramp = (t: number, a: number, b: number) => clamp((t - a) / (b - a));
export const easeInOutCubic = (x: number) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
export const easeOutQuint = (x: number) => 1 - Math.pow(1 - clamp(x), 5);
export const easeOutCubic = (x: number) => 1 - Math.pow(1 - clamp(x), 3);
export const easeInCubic = (x: number) => Math.pow(clamp(x), 3);
export const back = (x: number, s = 1.7) => {
  const t = clamp(x) - 1;
  return t * t * ((s + 1) * t + s) + 1;
};
/** progress 0..1 from time a to b with easeInOutCubic */
export const mv = (t: number, a: number, b: number, e: (x: number) => number = easeInOutCubic) => e(ramp(t, a, b));
/** damped spring 0 -> 1 starting at time t0 (seconds). damping .42-.5, freq 12-16 rad/s style. overshoots then settles. */
export const spring = (t: number, t0: number, freq = 14, damp = 0.45) => {
  const x = t - t0;
  if (x <= 0) return 0;
  const w = freq;
  const z = damp;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * x) * (Math.cos(wd * x) + ((z * w) / wd) * Math.sin(wd * x));
};
/** pop scale: 0 before t0, springs to 1 with overshoot. */
export const pop = (t: number, t0: number, freq = 14, damp = 0.45) => Math.max(0, spring(t, t0, freq, damp));
/** squash & stretch helper for a pop: returns [sx, sy]. */
export const squash = (t: number, t0: number) => {
  const x = t - t0;
  if (x <= 0) return [0, 0];
  const s = spring(t, t0);
  const k = Math.exp(-6 * x) * Math.sin(x * 18) * 0.18;
  return [s * (1 + k), s * (1 - k)];
};
/** log-space zoom interpolation */
export const zoomLerp = (z0: number, z1: number, p: number) => Math.exp(lerp(Math.log(z0), Math.log(z1), p));

/* ---------- camera ---------- */
export type Cam = {x: number; y: number; z: number; r?: number};
export type CamKey = {t: number; x: number; y: number; z: number; r?: number};
/** camera from keys (times in seconds): easeInOutCubic between keys, log zoom, plus a slow creep so nothing holds still. */
export const camAt = (keys: CamKey[], t: number, creep = 0.006): Cam => {
  let c: Cam;
  if (t <= keys[0].t) c = {...keys[0]};
  else if (t >= keys[keys.length - 1].t) c = {...keys[keys.length - 1]};
  else {
    const i = keys.findIndex((k, j) => t >= k.t && t <= keys[j + 1].t);
    const a = keys[i];
    const b = keys[i + 1];
    const p = easeInOutCubic((t - a.t) / (b.t - a.t));
    c = {x: lerp(a.x, b.x, p), y: lerp(a.y, b.y, p), z: zoomLerp(a.z, b.z, p), r: lerp(a.r ?? 0, b.r ?? 0, p)};
  }
  c.z *= 1 + creep * Math.max(0, t - keys[0].t);
  return c;
};
/** A world of 1920x1080 units. Camera (x,y) is the world point shown at screen centre; z is zoom. depth<1 = parallax background. */
export const World: React.FC<{cam: Cam; depth?: number; children: React.ReactNode; style?: React.CSSProperties}> = ({cam, depth = 1, children, style}) => {
  const z = Math.pow(cam.z, depth);
  const x = 960 + (cam.x - 960) * depth;
  const y = 540 + (cam.y - 540) * depth;
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: 1920,
        height: 1080,
        transformOrigin: '0 0',
        transform: `translate(960px,540px) rotate(${cam.r ?? 0}deg) scale(${z}) translate(${-x}px,${-y}px)`,
        ...style,
      }}
    >
      {children}
    </div>
  );
};
/** impact shake: decaying offset in px after t0 */
export const kick = (t: number, t0: number, amp = 10) => {
  const x = t - t0;
  if (x < 0 || x > 0.4) return [0, 0];
  const d = 1 - x / 0.4;
  return [Math.sin(x * 90) * amp * d, Math.cos(x * 70) * amp * 0.6 * d];
};

/* ---------- text: lines rise 1.3x their height through a clip on easeOutQuint ---------- */
export const Rise: React.FC<{
  t: number; // current seconds
  t0: number; // when it starts rising
  text: string;
  size?: number;
  color?: string;
  weight?: number;
  font?: string;
  stagger?: number; // seconds between words (0 = whole line rises together)
  dur?: number;
  out?: number; // seconds when it drops away (optional)
  style?: React.CSSProperties;
  align?: 'left' | 'center' | 'right';
  spacing?: number;
}> = ({t, t0, text, size = 64, color = P.ink, weight = 600, font = FONT_HEAD, stagger = 0.06, dur = 0.55, out, style, align = 'left', spacing = 0}) => {
  const ws = text.split(' ');
  const outP = out !== undefined ? easeInCubic(ramp(t, out, out + 0.35)) : 0;
  return (
    <div style={{fontFamily: font, fontSize: size, fontWeight: weight, color, lineHeight: 1.18, textAlign: align, letterSpacing: spacing, ...style}}>
      {ws.map((w, i) => {
        const p = easeOutQuint(ramp(t, t0 + i * stagger, t0 + i * stagger + dur));
        return (
          <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.08em', marginRight: i < ws.length - 1 ? '0.26em' : 0}}>
            <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130 + outP * -130}%)`}}>{w}</span>
          </span>
        );
      })}
    </div>
  );
};
/** rounded pill label */
export const Pill: React.FC<{children: React.ReactNode; bg?: string; color?: string; size?: number; style?: React.CSSProperties}> = ({children, bg = 'rgba(14,26,43,0.62)', color = P.white, size = 34, style}) => (
  <div style={{display: 'inline-block', padding: `${size * 0.32}px ${size * 0.7}px`, borderRadius: 999, background: bg, color, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: size, whiteSpace: 'nowrap', ...style}}>{children}</div>
);

/* ---------- finish: vignette + film grain (re-randomised 24x/s) ---------- */
export const Finish: React.FC<{vignette?: number}> = ({vignette = 0.38}) => {
  const f = useCurrentFrame();
  const seed = Math.floor((f / FPS) * 24);
  const ox = Math.floor(random('gx' + seed) * 200);
  const oy = Math.floor(random('gy' + seed) * 200);
  return (
    <>
      <div style={{position: 'absolute', inset: 0, pointerEvents: 'none', background: `radial-gradient(ellipse at center, rgba(0,0,0,0) 55%, rgba(8,8,20,${vignette}) 100%)`}} />
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: 0.06, mixBlendMode: 'overlay', pointerEvents: 'none'}}>
        <filter id="grain">
          <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed={seed} stitchTiles="stitch" />
          <feColorMatrix type="saturate" values="0" />
        </filter>
        <rect x={-ox} y={-oy} width={2200} height={1300} filter="url(#grain)" />
      </svg>
    </>
  );
};

/* ---------- phone with a real app recording ---------- */
/** Phone showing public/cut/fNN.mp4 (720x1600 real screen recordings). `startAt` = film second when the clip starts playing from clipFrom. */
export const PhoneClip: React.FC<{clip: number; x: number; y: number; h?: number; rot?: number; s?: number; startAt: number; clipFrom?: number; rate?: number; shadow?: boolean}> = ({
  clip,
  x,
  y,
  h = 860,
  rot = 0,
  s = 1,
  startAt,
  clipFrom = 0,
  rate = 1,
  shadow = true,
}) => {
  const f = useCurrentFrame();
  const t = f / FPS;
  const w = h * 0.47;
  const bez = h * 0.018;
  // the clip follows the scene's own (base) clock, so cuts and speed changes in the edit list carry through
  const vt = Math.max(0, (toBase(t) - startAt) * rate) + clipFrom;
  const src = staticFile(`cut/f${String(clip).padStart(2, '0')}.mp4`);
  return (
    <div
      style={{
        position: 'absolute',
        left: x - w / 2,
        top: y - h / 2,
        width: w,
        height: h,
        transform: `rotate(${rot}deg) scale(${s})`,
        borderRadius: h * 0.07,
        background: '#1B1A1F',
        padding: bez,
        boxShadow: shadow ? '0 40px 80px rgba(10,20,30,0.35), 0 8px 20px rgba(10,20,30,0.25)' : undefined,
      }}
    >
      <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream, position: 'relative'}}>
        <Freeze frame={Math.round(vt * FPS)}>
          <OffthreadVideo src={src} muted style={{width: '100%', height: '100%', objectFit: 'cover'}} />
        </Freeze>
      </div>
    </div>
  );
};

/* ---------- characters ---------- */
export type Expr = 'neutral' | 'happy' | 'worried' | 'surprised' | 'tired' | 'sad' | 'talk' | 'think';
export type PersonProps = {
  t: number; // seconds (ambient)
  x: number; // feet position in world units
  y: number;
  s?: number; // scale, 1 = ~360px tall
  skin?: string;
  hair?: string;
  hairStyle?: 'short' | 'long' | 'bun' | 'grey' | 'curly' | 'bald' | 'braid';
  top?: string; // shirt colour
  coat?: boolean; // white doctor's coat
  stetho?: boolean;
  bottom?: string;
  dupatta?: string; // scarf colour
  glasses?: boolean;
  expr?: Expr;
  look?: number; // -1 left .. 1 right (eyes)
  armL?: number; // degrees from hanging (positive = raise outward)
  armR?: number;
  elbowL?: number;
  elbowR?: number;
  lean?: number; // degrees
  headTilt?: number;
  flip?: boolean;
  seed?: number;
  sitting?: boolean;
  talk?: boolean; // mouth flaps
  hold?: React.ReactNode; // drawn at right hand, in local coords (hand at 0,0)
  bob?: number; // multiplier of idle bob
  squashY?: number; // 1 = none
  opacity?: number;
};
const blinkAmt = (t: number, seed: number) => {
  const per = 2.6 + (seed % 5) * 0.3;
  const ph = (t + seed * 0.71) % per;
  return ph < 0.06 ? ph / 0.06 : ph < 0.13 ? 1 - (ph - 0.06) / 0.07 : 0;
};
/** Original flat person: big rounded head, bean body, simple limbs. No outlines. Origin = between the feet. */
export const Person: React.FC<PersonProps> = (p) => {
  const {
    t,
    x,
    y,
    s = 1,
    skin = P.skin1,
    hair = P.hair,
    hairStyle = 'short',
    top = P.sage,
    coat = false,
    stetho = false,
    bottom = '#3A3F55',
    dupatta,
    glasses = false,
    expr = 'neutral',
    look = 0,
    armL = 0,
    armR = 0,
    elbowL = 0,
    elbowR = 0,
    lean = 0,
    headTilt = 0,
    flip = false,
    seed = 1,
    sitting = false,
    talk = false,
    hold,
    bob = 1,
    squashY = 1,
    opacity = 1,
  } = p;
  const idleBob = Math.sin(t * 2.1 + seed) * 3 * bob;
  const breathe = 1 + Math.sin(t * 2.1 + seed) * 0.012 * bob;
  const hT = headTilt + Math.sin(t * 0.9 + seed * 2) * 2 * bob;
  const bl = blinkAmt(t, seed);
  const mouthOpen = talk ? 0.35 + 0.65 * Math.abs(Math.sin(t * 13 + seed)) * (0.5 + 0.5 * Math.sin(t * 3.3 + seed)) : 0;
  const shade = 'rgba(30,20,40,0.16)';
  const legH = sitting ? 0 : 110;
  const bodyTop = -legH - 150;
  const coatC = '#F4F6F8';
  const torsoC = coat ? coatC : top;
  // arm drawing: shoulder at (sx, sy); angle a (deg) from hanging down, positive = outward
  const arm = (side: -1 | 1, a: number, el: number, colr: string) => {
    const sx = side * 52;
    const sy = bodyTop + 30;
    const L1 = 62;
    const L2 = 58;
    const a1 = ((side * a) * Math.PI) / 180;
    const ex = sx + Math.sin(a1) * L1;
    const ey = sy + Math.cos(a1) * L1;
    const a2 = a1 + ((side * el) * Math.PI) / 180;
    const hx = ex + Math.sin(a2) * L2;
    const hy = ey + Math.cos(a2) * L2;
    return {
      el: (
        <g key={side}>
          <path d={`M${sx} ${sy} L${ex} ${ey} L${hx} ${hy}`} stroke={colr} strokeWidth={26} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          <circle cx={hx} cy={hy} r={14} fill={skin} />
        </g>
      ),
      hand: [hx, hy] as [number, number],
    };
  };
  const sleeve = coat ? coatC : top;
  const L = arm(-1, armL, elbowL, sleeve);
  const R = arm(1, armR, elbowR, sleeve);
  const eyeY = bodyTop - 92;
  const eyeDX = 22;
  const lx = look * 6;
  const eye = (cx: number) => {
    if (expr === 'happy') return <path key={cx} d={`M${cx - 9} ${eyeY + 2} Q${cx} ${eyeY - 9} ${cx + 9} ${eyeY + 2}`} stroke="#2A1E1A" strokeWidth={5} fill="none" strokeLinecap="round" />;
    const open = expr === 'tired' ? 0.45 : expr === 'surprised' ? 1.25 : 1;
    const h = 11 * open * (1 - bl);
    return <ellipse key={cx} cx={cx + lx} cy={eyeY} rx={expr === 'surprised' ? 8 : 7} ry={Math.max(1.2, h)} fill="#2A1E1A" />;
  };
  const brow = (cx: number, side: number) => {
    const tilt = expr === 'worried' || expr === 'sad' || expr === 'tired' ? -side * 8 : expr === 'surprised' ? 0 : expr === 'think' ? side * 6 : 0;
    const by = eyeY - (expr === 'surprised' ? 26 : 19);
    return <path key={'b' + cx} d={`M${cx - 11} ${by + (tilt * side) / 3} L${cx + 11} ${by - (tilt * side) / 3}`} stroke={hairStyle === 'grey' ? '#8D857F' : '#2A1E1A'} strokeWidth={5} strokeLinecap="round" />;
  };
  const my = eyeY + 34;
  let mouth: React.ReactNode;
  if (talk && mouthOpen > 0.05) mouth = <ellipse cx={0} cy={my} rx={10} ry={3 + 9 * mouthOpen} fill="#6B2E2E" />;
  else if (expr === 'happy') mouth = <path d={`M-14 ${my - 4} Q0 ${my + 12} 14 ${my - 4}`} stroke="#6B2E2E" strokeWidth={5} fill="none" strokeLinecap="round" />;
  else if (expr === 'worried' || expr === 'sad') mouth = <path d={`M-11 ${my + 4} Q0 ${my - 5} 11 ${my + 4}`} stroke="#6B2E2E" strokeWidth={5} fill="none" strokeLinecap="round" />;
  else if (expr === 'surprised') mouth = <ellipse cx={0} cy={my + 2} rx={8} ry={10} fill="#6B2E2E" />;
  else if (expr === 'think') mouth = <path d={`M-8 ${my} L10 ${my - 3}`} stroke="#6B2E2E" strokeWidth={5} strokeLinecap="round" />;
  else mouth = <path d={`M-10 ${my} Q0 ${my + 5} 10 ${my}`} stroke="#6B2E2E" strokeWidth={5} fill="none" strokeLinecap="round" />;
  const headCY = bodyTop - 90;
  const hairEl = () => {
    switch (hairStyle) {
      case 'long':
        return (
          <>
            <path d={`M-72 ${headCY} Q-80 ${headCY + 120} -50 ${headCY + 150} L50 ${headCY + 150} Q80 ${headCY + 120} 72 ${headCY} Z`} fill={hair} />
          </>
        );
      case 'braid':
        return <path d={`M50 ${headCY + 10} Q86 ${headCY + 90} 62 ${headCY + 170}`} stroke={hair} strokeWidth={22} strokeLinecap="round" fill="none" />;
      case 'bun':
        return <circle cx={0} cy={headCY - 80} r={30} fill={hair} />;
      default:
        return null;
    }
  };
  const hairTop = () => {
    if (hairStyle === 'bald') return <path d={`M-58 ${headCY - 30} Q0 ${headCY - 92} 58 ${headCY - 30}`} fill="none" />;
    const c = hairStyle === 'grey' ? P.hairGrey : hair;
    if (hairStyle === 'curly')
      return (
        <g fill={c}>
          {[-48, -24, 0, 24, 48].map((dx, i) => (
            <circle key={i} cx={dx} cy={headCY - 62 + Math.abs(dx) * 0.35} r={26} />
          ))}
        </g>
      );
    return <path d={`M-70 ${headCY + 4} Q-74 ${headCY - 78} 0 ${headCY - 82} Q74 ${headCY - 78} 70 ${headCY + 4} Q46 ${headCY - 34} 6 ${headCY - 40} Q-40 ${headCY - 40} -70 ${headCY + 4} Z`} fill={c} />;
  };
  return (
    <svg
      style={{position: 'absolute', left: x - 200 * s, top: y - 560 * s, width: 400 * s, height: 600 * s, overflow: 'visible', opacity}}
      viewBox="-200 -560 400 600"
    >
      <g transform={`${flip ? 'scale(-1,1)' : ''} translate(0 ${idleBob}) scale(1 ${squashY}) rotate(${lean} 0 0)`}>
        {/* ground shadow */}
        <ellipse cx={0} cy={4 - idleBob} rx={70} ry={12} fill="rgba(20,20,40,0.18)" />
        {/* legs */}
        {!sitting && (
          <g>
            <rect x={-38} y={-legH - 6} width={30} height={legH + 2} rx={14} fill={bottom} />
            <rect x={8} y={-legH - 6} width={30} height={legH + 2} rx={14} fill={bottom} />
            <ellipse cx={-26} cy={-4} rx={26} ry={12} fill="#3A2B26" />
            <ellipse cx={26} cy={-4} rx={26} ry={12} fill="#3A2B26" />
          </g>
        )}
        {hairEl()}
        {/* back arm */}
        {L.el}
        {/* body */}
        <g transform={`translate(0 ${bodyTop + 75}) scale(${breathe}) translate(0 ${-(bodyTop + 75)})`}>
          <path d={`M-58 ${bodyTop + 24} Q-62 ${bodyTop - 4} -30 ${bodyTop - 8} L30 ${bodyTop - 8} Q62 ${bodyTop - 4} 58 ${bodyTop + 24} L66 ${bodyTop + 150} Q0 ${bodyTop + 164} -66 ${bodyTop + 150} Z`} fill={torsoC} />
          <path d={`M14 ${bodyTop - 8} L30 ${bodyTop - 8} Q62 ${bodyTop - 4} 58 ${bodyTop + 24} L66 ${bodyTop + 150} Q40 ${bodyTop + 158} 20 ${bodyTop + 160} Z`} fill={shade} />
          {coat && (
            <>
              <path d={`M-14 ${bodyTop - 6} L0 ${bodyTop + 40} L14 ${bodyTop - 6} Z`} fill={top} />
              <rect x={-2} y={bodyTop + 40} width={4} height={110} fill="#DCE1E6" />
              <rect x={22} y={bodyTop + 60} width={26} height={20} rx={4} fill="#DCE1E6" />
            </>
          )}
          {stetho && <path d={`M-26 ${bodyTop - 4} Q-34 ${bodyTop + 70} 0 ${bodyTop + 80} Q30 ${bodyTop + 70} 24 ${bodyTop - 4}`} stroke="#3C4A5C" strokeWidth={6} fill="none" />}
          {stetho && <circle cx={0} cy={bodyTop + 84} r={9} fill="#9AA6B2" />}
          {dupatta && <path d={`M-56 ${bodyTop + 4} Q0 ${bodyTop + 70} 56 ${bodyTop + 4} L60 ${bodyTop + 30} Q0 ${bodyTop + 96} -60 ${bodyTop + 30} Z`} fill={dupatta} />}
          {/* neck */}
          <rect x={-14} y={bodyTop - 24} width={28} height={22} rx={8} fill={skin} />
        </g>
        {/* head */}
        <g transform={`rotate(${hT} 0 ${bodyTop - 10})`}>
          <ellipse cx={0} cy={headCY} rx={70} ry={74} fill={skin} />
          <ellipse cx={-70} cy={headCY + 6} rx={11} ry={16} fill={skin} />
          <ellipse cx={70} cy={headCY + 6} rx={11} ry={16} fill={skin} />
          <path d={`M30 ${headCY - 60} Q78 ${headCY} 40 ${headCY + 62} Q70 ${headCY + 10} 30 ${headCY - 60} Z`} fill="rgba(60,30,20,0.10)" />
          {hairTop()}
          {eye(-eyeDX)}
          {eye(eyeDX)}
          {brow(-eyeDX, -1)}
          {brow(eyeDX, 1)}
          {glasses && (
            <g stroke="#2A1E1A" strokeWidth={4} fill="rgba(255,255,255,0.15)">
              <circle cx={-eyeDX} cy={eyeY} r={16} />
              <circle cx={eyeDX} cy={eyeY} r={16} />
              <path d={`M${-eyeDX + 16} ${eyeY} L${eyeDX - 16} ${eyeY}`} />
            </g>
          )}
          <ellipse cx={-40} cy={eyeY + 22} rx={11} ry={6} fill="rgba(230,110,110,0.25)" />
          <ellipse cx={40} cy={eyeY + 22} rx={11} ry={6} fill="rgba(230,110,110,0.25)" />
          {mouth}
        </g>
        {/* front arm */}
        {R.el}
        {hold && <g transform={`translate(${R.hand[0]} ${R.hand[1]})`}>{hold}</g>}
      </g>
    </svg>
  );
};

/* ---------- small reusable props ---------- */
export const SpeechBubble: React.FC<{x: number; y: number; w: number; h: number; s?: number; bg?: string; tail?: 'left' | 'right' | 'none'; children?: React.ReactNode; style?: React.CSSProperties}> = ({x, y, w, h, s = 1, bg = P.white, tail = 'left', children, style}) => (
  <div style={{position: 'absolute', left: x, top: y, width: w, height: h, transform: `scale(${s})`, transformOrigin: tail === 'right' ? '90% 110%' : '10% 110%', ...style}}>
    <div style={{position: 'absolute', inset: 0, borderRadius: 28, background: bg, boxShadow: '0 10px 24px rgba(10,20,30,0.18)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, color: P.ink, fontSize: 40, padding: '0 28px', textAlign: 'center'}}>
      {children}
    </div>
    {tail !== 'none' && (
      <svg width={40} height={30} style={{position: 'absolute', top: h - 2, left: tail === 'left' ? 40 : w - 80}}>
        <path d={tail === 'left' ? 'M0 0 L40 0 L6 28 Z' : 'M0 0 L40 0 L34 28 Z'} fill={bg} />
      </svg>
    )}
  </div>
);

/** soft blob for backgrounds */
export const Blob: React.FC<{x: number; y: number; r: number; color: string; blur?: number}> = ({x, y, r, color, blur = 60}) => (
  <div style={{position: 'absolute', left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: '50%', background: color, filter: `blur(${blur}px)`}} />
);

/** hook re-exported for convenience */
export const useFrameT = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  return f / fps;
};

/* ---------- scene windows: scenes read GLOBAL film seconds via useGT() ---------- */
const OffsetCtx = React.createContext(0);
export const SceneWindow: React.FC<{from: number; to: number; children: React.ReactNode}> = ({from, to, children}) => {
  const t = useCurrentFrame() / FPS;
  const s = segAt(t);
  if (s.insert) return null;
  const b = toBase(t);
  if (b < from || b >= to) return null;
  return <OffsetCtx.Provider value={0}>{children}</OffsetCtx.Provider>;
};
/** global film time in seconds (works inside SceneWindow). */
export const useGT = () => {
  const off = React.useContext(OffsetCtx);
  return toBase((useCurrentFrame() + off) / FPS);
};

/* ---------- film-time cues (the narration as recorded): for inserts and overlays ---------- */
const NW: Word[] = (words as any).words;
/** film seconds when word `w` of narration sentence `id` starts */
export const nat = (id: string, w?: string, nth = 0): number => {
  const ws = NW.filter((x) => x.id === id);
  if (!ws.length) throw new Error('no narration sentence ' + id);
  if (!w) return ws[0].s;
  const hits = ws.filter((x) => norm(x.w) === norm(w));
  if (!hits.length) throw new Error(`no narration word ${w} in ${id}`);
  return hits[Math.min(nth, hits.length - 1)].s;
};
export const nend = (id: string) => {
  const ws = NW.filter((x) => x.id === id);
  return ws[ws.length - 1].e;
};
/** film seconds */
export const useFilmT = () => useCurrentFrame() / FPS;
