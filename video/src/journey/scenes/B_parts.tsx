/* B: shared drawing parts for the origin scene (photo cards, desk, laptops, plants, motes, sparkles). */
import React from 'react';
import {Img, random, staticFile} from 'remotion';
import {P, FONT_HEAD, clamp, lerp, ramp, easeOutCubic} from '../kit';
import {TEAM, Who} from '../team';

/** per-photo crop so every face sits nicely inside the round card */
const CROP: Record<Who, {s: number; o: string}> = {
  basith: {s: 1.3, o: '50% 28%'},
  awaiz: {s: 2.5, o: '61% 16%'},
  sujay: {s: 1.25, o: '50% 40%'},
  sharada: {s: 1.25, o: '52% 30%'},
  shirin: {s: 1.3, o: '50% 40%'},
};
/** the colour each person wears as a flat character (used for the card backing disc) */
export const TONE: Record<Who, string> = {
  basith: P.marigold,
  awaiz: P.indigo,
  sujay: P.sage,
  sharada: '#8A9BB0',
  shirin: P.plum,
};
export const PALE: Record<Who, string> = {
  basith: '#F6E2BE',
  awaiz: '#DCE2F1',
  sujay: P.sageLight,
  sharada: '#E1E7EE',
  shirin: '#EEDFEC',
};

/** round photo card with a flat colour disc behind it. x,y = centre. */
export const PhotoCard: React.FC<{who: Who; x: number; y: number; d?: number; s?: number; rot?: number; gray?: number; ring?: string; opacity?: number}> = ({
  who,
  x,
  y,
  d = 240,
  s = 1,
  rot = 0,
  gray = 0,
  ring = P.white,
  opacity = 1,
}) => {
  if (s <= 0.001) return null;
  const c = CROP[who];
  const b = Math.max(5, d * 0.045);
  return (
    <div style={{position: 'absolute', left: x, top: y, width: 0, height: 0, transform: `scale(${s}) rotate(${rot}deg)`, opacity}}>
      <div style={{position: 'absolute', left: -d / 2 + d * 0.05, top: -d / 2 + d * 0.06, width: d, height: d, borderRadius: '50%', background: TONE[who], filter: `saturate(${1 - gray * 0.7})`}} />
      <div
        style={{
          position: 'absolute',
          left: -d / 2,
          top: -d / 2,
          width: d,
          height: d,
          borderRadius: '50%',
          overflow: 'hidden',
          border: `${b}px solid ${ring}`,
          boxSizing: 'border-box',
          background: '#DDD',
        }}
      >
        <Img src={staticFile(TEAM[who].photo)} style={{width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${c.s})`, transformOrigin: c.o, filter: `grayscale(${gray}) brightness(${1 + gray * 0.06})`}} />
      </div>
    </div>
  );
};

/** short dashes that burst outward around a point (focus accents) */
export const Sparkle: React.FC<{t: number; t0: number; x: number; y: number; r: number; color?: string; n?: number; rot?: number}> = ({t, t0, x, y, r, color = P.marigold, n = 8, rot = 0}) => {
  const dt = t - t0;
  if (dt < 0 || dt > 0.6) return null;
  const u = easeOutCubic(dt / 0.45);
  const o = 1 - ramp(dt, 0.3, 0.6);
  return (
    <svg style={{position: 'absolute', left: x, top: y, overflow: 'visible'}} width={1} height={1}>
      {Array.from({length: n}, (_, i) => {
        const a = ((i / n) * 360 + rot + 12) * (Math.PI / 180);
        const r0 = r + u * 26;
        const r1 = r0 + 14 + 22 * (1 - u);
        return <line key={i} x1={Math.cos(a) * r0} y1={Math.sin(a) * r0} x2={Math.cos(a) * r1} y2={Math.sin(a) * r1} stroke={i % 2 ? color : P.rose} strokeWidth={7} strokeLinecap="round" opacity={o} />;
      })}
    </svg>
  );
};

/** dust motes drifting through warm light */
export const Motes: React.FC<{t: number; x0: number; w: number; y0?: number; h?: number; n?: number; seed?: string; color?: string}> = ({t, x0, w, y0 = 0, h = 900, n = 24, seed = 'm', color = 'rgba(255,236,196,0.9)'}) => (
  <>
    {Array.from({length: n}, (_, i) => {
      const bx = x0 + random(seed + 'x' + i) * w;
      const by = y0 + random(seed + 'y' + i) * h;
      const x = bx + Math.sin(t * 0.45 + i) * 36 + t * 5;
      const y = by + Math.cos(t * 0.37 + i * 2) * 26 - ((t * 12 + i * 37) % 90);
      const r = 2.5 + random(seed + 'r' + i) * 3.5;
      return <div key={i} style={{position: 'absolute', left: x, top: y, width: r * 2, height: r * 2, borderRadius: r, background: color, opacity: 0.55 + 0.45 * Math.sin(t * 1.7 + i)}} />;
    })}
  </>
);

/** the back of an open laptop lid, seen from across the table. x = centre, base = table top */
export const LaptopBack: React.FC<{x: number; base: number; w?: number; glow?: number; sticker?: string; s?: number}> = ({x, base, w = 170, glow = 0, sticker = P.rose, s = 1}) => {
  if (s <= 0.001) return null;
  const h = w * 0.6;
  const g = clamp(glow, 0, 1.6);
  return (
    <div style={{position: 'absolute', left: x, top: base, width: 0, height: 0, transform: `scale(${s})`, transformOrigin: '0 0'}}>
      {g > 0 && <div style={{position: 'absolute', left: -w, top: -h * 2.6, width: w * 2, height: w * 2, borderRadius: '50%', background: `radial-gradient(circle at 50% 60%, rgba(255,226,165,${0.45 * g}) 0%, rgba(255,226,165,0) 62%)`}} />}
      <div style={{position: 'absolute', left: -w / 2 - 10, top: -6, width: w + 20, height: 9, borderRadius: 5, background: '#2E3646'}} />
      <div style={{position: 'absolute', left: -w / 2, top: -h - 4, width: w, height: h, borderRadius: '12px 12px 3px 3px', background: '#3E4A5E', boxShadow: g > 0 ? `0 -5px 0 rgba(255,214,140,${0.9 * clamp(g)})` : undefined}}>
        <div style={{position: 'absolute', right: 0, top: 0, width: w * 0.3, height: h, borderRadius: '0 12px 3px 0', background: 'rgba(0,0,0,0.12)'}} />
        <div style={{position: 'absolute', left: w / 2 - 12, top: h / 2 - 12, width: 24, height: 24, borderRadius: 12, background: g > 0 ? `rgba(255,230,176,${0.4 + 0.6 * clamp(g)})` : '#8C96A8'}} />
        <div style={{position: 'absolute', left: 14, top: 12, width: 22, height: 14, borderRadius: 4, background: sticker, transform: 'rotate(-8deg)'}} />
        <div style={{position: 'absolute', right: 18, bottom: 14, width: 16, height: 16, borderRadius: 8, background: P.mint}} />
      </div>
    </div>
  );
};

/** a long wooden desk; top edge at y=top */
export const Desk: React.FC<{x0: number; x1: number; top: number; floor: number}> = ({x0, x1, top, floor}) => (
  <>
    <div style={{position: 'absolute', left: x0 + 20, top: floor - 10, width: x1 - x0 - 40, height: 22, borderRadius: '50%', background: 'rgba(120,80,50,0.16)'}} />
    <div style={{position: 'absolute', left: x0, top, width: x1 - x0, height: 24, borderRadius: 8, background: '#C98F55'}} />
    <div style={{position: 'absolute', left: x0 + 20, top: top + 22, width: x1 - x0 - 40, height: 84, background: '#B37C46'}} />
    <div style={{position: 'absolute', left: x0 + 20, top: top + 90, width: x1 - x0 - 40, height: 16, background: '#9C6A3B'}} />
    {[x0 + 34, x1 - 66].map((x) => (
      <div key={x} style={{position: 'absolute', left: x, top: top + 104, width: 32, height: Math.max(0, floor - top - 104), background: '#9C6A3B'}} />
    ))}
  </>
);

/** potted plant (flat), base centre at x,y */
export const Plant: React.FC<{x: number; y: number; s?: number; t: number; seed?: number; pot?: string}> = ({x, y, s = 1, t, seed = 0, pot = P.coral}) => (
  <svg style={{position: 'absolute', left: x - 120 * s, top: y - 330 * s, overflow: 'visible'}} width={240 * s} height={330 * s} viewBox="0 0 240 330">
    <g transform={`rotate(${Math.sin(t * 0.9 + seed) * 2.4} 120 250)`}>
      <ellipse cx={70} cy={140} rx={34} ry={100} transform="rotate(-32 70 140)" fill={P.sageDark} />
      <ellipse cx={172} cy={136} rx={34} ry={100} transform="rotate(30 172 136)" fill="#24493D" />
      <ellipse cx={120} cy={104} rx={32} ry={112} fill={P.band} />
      <ellipse cx={92} cy={176} rx={26} ry={70} transform="rotate(-60 92 176)" fill="#4C8A74" />
    </g>
    <path d="M62 236 L178 236 L164 330 L76 330 Z" fill={pot} />
    <path d="M128 236 L178 236 L164 330 L128 330 Z" fill="rgba(0,0,0,0.12)" />
    <rect x={54} y={226} width={132} height={22} rx={8} fill={pot} />
  </svg>
);

/** big foreground leaves (for a depth > 1 layer) */
export const FgLeaves: React.FC<{x: number; y: number; t: number; flip?: boolean; s?: number}> = ({x, y, t, flip = false, s = 1}) => (
  <svg style={{position: 'absolute', left: x - 260 * s, top: y - 420 * s, overflow: 'visible', transform: flip ? 'scaleX(-1)' : undefined}} width={520 * s} height={520 * s} viewBox="0 0 520 520">
    <g transform={`rotate(${Math.sin(t * 0.8 + (flip ? 1 : 0)) * 2.5} 260 520)`}>
      <ellipse cx={170} cy={300} rx={64} ry={200} transform="rotate(-38 170 300)" fill="#2C5A4A" />
      <ellipse cx={330} cy={290} rx={60} ry={190} transform="rotate(30 330 290)" fill="#24493D" />
      <ellipse cx={250} cy={250} rx={58} ry={220} fill="#335F50" />
    </g>
  </svg>
);

/** handwritten-looking squiggle line as an SVG path (deterministic) */
export const scribble = (x0: number, y0: number, len: number, k: number) => {
  const pts: string[] = [];
  const N = 44;
  for (let s = 0; s <= N; s++) {
    const u = s / N;
    const x = x0 + u * len + Math.sin(u * 40 + k) * 3;
    const y = y0 - Math.abs(Math.sin(u * 19 + k * 1.7)) * 12 + Math.sin(u * 6 + k) * 2;
    pts.push(`${s === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`);
  }
  return pts.join(' ');
};

/** a centred text block wrapper for Rise */
export const Center: React.FC<{x: number; y: number; w?: number; children: React.ReactNode}> = ({x, y, w = 700, children}) => (
  <div style={{position: 'absolute', left: x - w / 2, top: y, width: w}}>{children}</div>
);

/** hanging sign that swings in from above */
export const HangingSign: React.FC<{x: number; y: number; t: number; t0: number; text: string; bg?: string; w?: number}> = ({x, y, t, t0, text, bg = P.plum, w = 360}) => {
  if (t < t0) return null;
  const dt = t - t0;
  const drop = lerp(-520, 0, Math.min(1.08, 1 - Math.exp(-dt * 7) * Math.cos(dt * 14)));
  const sw = Math.exp(-dt * 2.4) * Math.sin(dt * 7) * 9 + Math.sin(t * 1.3) * 1.2;
  return (
    <div style={{position: 'absolute', left: x, top: y + drop, width: 0, height: 0, transform: `rotate(${sw}deg)`, transformOrigin: '0 -600px'}}>
      {[-w / 2 + 40, w / 2 - 44].map((sx) => (
        <div key={sx} style={{position: 'absolute', left: sx, top: -600, width: 4, height: 600, background: '#B9A68C'}} />
      ))}
      <div
        style={{
          position: 'absolute',
          left: -w / 2,
          top: -8,
          width: w,
          height: 112,
          borderRadius: 22,
          background: bg,
          boxShadow: 'inset 0 -10px 0 rgba(0,0,0,0.16)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontFamily: FONT_HEAD,
          fontWeight: 600,
          fontSize: 60,
          color: P.cream,
          letterSpacing: 1,
        }}
      >
        {text}
      </div>
    </div>
  );
};
