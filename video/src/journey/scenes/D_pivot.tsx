/* D: the medical student's question. The tower creaks, collapses, gets swept away; one card is kept,
   "Goals of care talk", which expands to sage and hands off to scene E. Room shared with B and C. */
import React from 'react';
import {P, FONT_HEAD, useGT, Person, SpeechBubble, ramp, mv, lerp, pop, spring, kick, back, easeOutCubic, easeOutQuint, easeInCubic, easeInOutCubic} from '../kit';
import {CAST} from '../cast';
import {Workspace, ToolCard, Glyph, cardPose, landTimes, roomCam, toScreen, broomX, FLOOR, STU_X, TOWER_X, GOALS, CARD_W, CARD_H, GOAL_HOVER, T_FALL, T_KEEP, T_SWEEP, LABELS} from './B_room';

const T_IN = 28.44; // "medical"
const T_HAND = 30.01; // "asked"
const T_Q = 30.53; // "one question"
const QW: [string, number][] = [
  ['Why', 31.34],
  ['would', 31.44],
  ['a', 31.51],
  ['family', 31.56],
  ['install', 31.95],
  ['this?', 32.36],
];
const T_SIGN = 34.42;
const SIGN_FRONT: [string, number][] = [
  ['for', 34.78],
  ['doctors', 34.84],
  ['like', 34.9],
  ['us', 34.95],
];
const SIGN_BACK: [string, number][] = [
  ['for', 35.87],
  ['families', 36.06],
];
const T_FLIP = 35.56; // "not"
const GOAL_WORDS = [39.57, 39.84, 39.91, 40.19];
const T_EXPAND = 40.19;

const Words: React.FC<{t: number; words: [string, number][]; size: number; color: string; accent?: string; accentIdx?: number}> = ({t, words, size, color, accent, accentIdx = -1}) => (
  <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: size, color, lineHeight: 1.12, textAlign: 'center'}}>
    {words.map(([w, t0], i) => {
      const p = easeOutQuint(ramp(t, t0 - 0.04, t0 + 0.45));
      return (
        <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.08em', marginRight: i < words.length - 1 ? '0.26em' : 0}}>
          <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`, color: i === accentIdx ? accent : color}}>{w}</span>
        </span>
      );
    })}
  </div>
);

const Student: React.FC<{t: number}> = ({t}) => {
  const u = ramp(t, T_IN, T_IN + 0.5);
  const x = lerp(-460, STU_X, back(u, 1.4));
  const settle = t > T_IN + 0.5 ? -7 * Math.exp(-(t - T_IN - 0.5) * 6) * Math.sin((t - T_IN - 0.5) * 14) : 0;
  const lean = 12 * Math.sin(Math.min(1, u) * Math.PI) * (u < 1 ? 1 : 0) + settle;
  const hop = -Math.abs(Math.sin(u * Math.PI * 3)) * 18 * (u < 1 ? 1 : 0);
  const hand = spring(t, T_HAND, 12, 0.72) * (1 - mv(t, 31.15, 31.45));
  let expr: 'happy' | 'think' | 'neutral' | 'surprised' | 'worried' = 'happy';
  if (t > T_Q) expr = 'think';
  if (t > 33.65) expr = 'surprised';
  if (t > 34.4) expr = 'neutral';
  if (t > 38.05) expr = 'happy';
  const talk = t > 31.3 && t < 32.8;
  const look = t > 33.3 ? 1 : 0.8;
  return <Person {...CAST.medStudent} t={t} x={x} y={FLOOR + hop} lean={lean} armR={lerp(6, 138, hand)} elbowR={12 * hand} armL={6} expr={talk ? 'neutral' : expr} talk={talk} look={look} headTilt={t > 34.45 && t < 35.2 ? Math.sin((t - 34.45) * 10) * 4 : 0} />;
};

/** "?" over the raised hand, then the big question bubble */
const Question: React.FC<{t: number}> = ({t}) => {
  const out: React.ReactNode[] = [];
  if (t > T_Q && t < 31.5) {
    const s = pop(t, T_Q, 14, 0.4) * (1 - easeInCubic(ramp(t, 31.2, 31.45)));
    out.push(
      <div key="q" style={{position: 'absolute', left: STU_X + 110, top: FLOOR - 560, width: 90, height: 90, borderRadius: 45, background: P.marigold, boxShadow: `inset 0 -8px 0 ${P.marigoldDeep}`, transform: `scale(${s}) rotate(${Math.sin(t * 8) * 8}deg)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: P.white}}>
        ?
      </div>,
    );
  }
  if (t > 31.25 && t < 33.8) {
    const s = pop(t, 31.25, 13, 0.45) * (1 - easeInCubic(ramp(t, 33.45, 33.75)));
    out.push(
      <SpeechBubble key="b" x={120} y={90} w={720} h={270} s={s} bg={P.white} tail="left">
        <div>
          <Words t={t} words={QW.slice(0, 4)} size={74} color={P.ink} accent={P.rose} accentIdx={3} />
          <Words t={t} words={QW.slice(4)} size={74} color={P.ink} />
        </div>
      </SpeechBubble>,
    );
  }
  return <>{out}</>;
};

/** the tower groans before it goes: vibration arcs either side */
const Creak: React.FC<{t: number}> = ({t}) => {
  if (t < 32.3 || t > T_FALL + 0.15) return null;
  const k = ramp(t, 32.3, 32.8) * (1 - ramp(t, T_FALL, T_FALL + 0.15));
  const flick = Math.floor(t * 12) % 2;
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {[-1, 1].map((side) =>
        [0, 1, 2].map((j) => {
          const cx = TOWER_X + side * (CARD_W / 2 + 40 + j * 26);
          const cy = 420 + (flick ? 10 : -10) + j * 4;
          const r = 40 + j * 22;
          return <path key={side + '' + j} d={`M${cx} ${cy - r} q ${side * 26} ${r} 0 ${r * 2}`} stroke={P.inkSub} strokeWidth={7} fill="none" strokeLinecap="round" opacity={k * (0.8 - j * 0.2)} />;
        }),
      )}
    </svg>
  );
};

/** dust puffs where cards land */
const Dust: React.FC<{t: number}> = ({t}) => {
  const L = landTimes();
  const out: React.ReactNode[] = [];
  L.forEach((tl, i) => {
    const d = t - tl;
    if (d < 0 || d > 0.9 || i === GOALS) return;
    const p = cardPose(i, tl + 0.001);
    const big = i > 12 ? 1.4 : 1;
    for (let j = 0; j < 6; j++) {
      const side = j % 2 ? 1 : -1;
      const u = easeOutCubic(d / 0.9);
      const x = p.x + side * (60 + j * 30) * u + side * 60;
      const y = FLOOR - 16 - (j % 3) * 18 * u - 20 * u;
      const r = (14 + j * 5) * (0.4 + u) * big;
      const o = 0.85 * (1 - ramp(d, 0.35, 0.9));
      out.push(<circle key={i * 10 + j} cx={x} cy={y} r={r} fill={j % 3 === 0 ? '#D9C6A8' : '#F1E6D4'} opacity={o} />);
    }
  });
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {out}
    </svg>
  );
};

/** hanging sign: "for doctors like us" flips to "for families" */
const Sign: React.FC<{t: number}> = ({t}) => {
  if (t < T_SIGN || t > 38.45) return null;
  const drop = spring(t, T_SIGN, 9, 0.35);
  const y = lerp(-900, 0, drop) - 1100 * easeInCubic(ramp(t, 37.85, 38.4));
  const swing = Math.sin((t - T_SIGN) * 5) * 4 * Math.exp(-(t - T_SIGN) * 1.5) + Math.sin(t * 1.4) * 0.8;
  const p = ramp(t, T_FLIP, T_FLIP + 0.32);
  const sx = Math.abs(Math.cos(p * Math.PI));
  const back = p >= 0.5;
  const cx = 800;
  const w = 600;
  const h = 124;
  const top = 128 + y;
  return (
    <div style={{position: 'absolute', left: cx, top: top - 600, width: 0, height: 0, transform: `rotate(${swing}deg)`, transformOrigin: '0 0'}}>
      {[-w / 2 + 50, w / 2 - 56].map((dx) => (
        <div key={dx} style={{position: 'absolute', left: dx, top: 0, width: 6, height: 600, background: '#8A7A6A'}} />
      ))}
      <div
        style={{
          position: 'absolute',
          left: -w / 2,
          top: 600,
          width: w,
          height: h,
          borderRadius: 22,
          background: back ? P.sage : P.indigo,
          boxShadow: `inset 0 -10px 0 ${back ? P.sageDark : '#46588C'}`,
          transform: `scaleX(${Math.max(0.02, sx)}) scale(${1 + (back ? Math.exp(-(t - T_FLIP - 0.16) * 6) * Math.sin((t - T_FLIP - 0.16) * 18) * 0.06 : 0)})`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          paddingBottom: 8,
          boxSizing: 'border-box',
        }}
      >
        {back ? <Words t={t} words={SIGN_BACK} size={60} color={P.white} /> : <Words t={t} words={SIGN_FRONT} size={56} color={P.cream} />}
      </div>
    </div>
  );
};

/** a gust (echo of the notes blowing away in B) sweeps the pile off frame */
const Gust: React.FC<{t: number}> = ({t}) => {
  if (t < T_SWEEP - 0.05 || t > 37.9) return null;
  const bx = broomX(t);
  const o = 1 - ramp(t, 37.45, 37.9);
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {[0, 1, 2, 3, 4, 5].map((j) => {
        const y = FLOOR - 22 - j * 24 - (j % 2) * 10;
        const head = bx + 40 - (j % 3) * 60;
        const len = Math.max(60, Math.min(380 + (j % 3) * 140, head - 1240));
        const wob = Math.sin(t * 9 + j) * 10;
        return (
          <path
            key={j}
            d={`M${head - len} ${y + wob} C ${head - len * 0.6} ${y - 18 + wob} ${head - len * 0.3} ${y + 18} ${head} ${y} q 40 -6 34 -40 q -8 -22 -30 -10`}
            stroke="#FFFFFF"
            strokeWidth={j % 2 ? 7 : 10}
            fill="none"
            strokeLinecap="round"
            opacity={0.9 * o}
          />
        );
      })}
    </svg>
  );
};

/** the kept card: glows, lifts, words hop, then grows into a sage frame */
const Goals: React.FC<{t: number}> = ({t}) => {
  const p = cardPose(GOALS, t);
  if (!p.vis) return null;
  const glow = ramp(t, T_KEEP, T_KEEP + 0.3) * (0.85 + 0.15 * Math.sin(t * 6));
  const hop = (k: number) => -38 * Math.sin(Math.PI * ramp(t, GOAL_WORDS[k], GOAL_WORDS[k] + 0.3));
  const sparkles: React.ReactNode[] = [];
  if (t > T_KEEP) {
    for (let j = 0; j < 9; j++) {
      const a = (j / 9) * Math.PI * 2 + t * 0.9;
      const R = 330 + Math.sin(t * 2 + j) * 30;
      const s = (6 + (j % 3) * 3) * ramp(t, T_KEEP + j * 0.04, T_KEEP + 0.3 + j * 0.04) * (1 - ramp(t, T_EXPAND - 0.2, T_EXPAND));
      sparkles.push(<div key={j} style={{position: 'absolute', left: p.x + Math.cos(a) * R - s, top: p.y + Math.sin(a) * R * 0.42 - s, width: s * 2, height: s * 2, borderRadius: s, background: j % 2 ? P.marigold : '#FFE6B0', transform: 'rotate(45deg)'}} />);
    }
  }
  return (
    <>
      {sparkles}
      <ToolCard i={GOALS} x={p.x} y={p.y} r={p.r} sx={p.sx} sy={p.sy} glow={glow} wordHop={hop} />
    </>
  );
};

/** screen-space sage expansion that hands off to scene E */
const Expand: React.FC<{t: number}> = ({t}) => {
  if (t < T_EXPAND) return null;
  const cam = roomCam(t);
  const p = cardPose(GOALS, t);
  const c = toScreen(cam, p.x, p.y);
  const w0 = CARD_W * p.sx * cam.z;
  const h0 = CARD_H * p.sy * cam.z;
  const e = easeInOutCubic(ramp(t, T_EXPAND + 0.05, 40.85));
  const L = lerp(c.x - w0 / 2, -20, e);
  const T = lerp(c.y - h0 / 2, -20, e);
  const R = lerp(c.x + w0 / 2, 1940, e);
  const B = lerp(c.y + h0 / 2, 1100, e);
  const lab = LABELS[GOALS].split(' ');
  const out = easeInCubic(ramp(t, 40.42, 40.8));
  const k = p.sx * cam.z * lerp(1, 1.15, e);
  const shift = 59 * k * e; // centre the (left-aligned) content as it grows
  const hop = -38 * Math.sin(Math.PI * ramp(t, GOAL_WORDS[3], GOAL_WORDS[3] + 0.3));
  return (
    <div style={{position: 'absolute', left: L, top: T, width: R - L, height: B - T, borderRadius: lerp(14 * p.sx * cam.z, 0, e), background: P.sage, overflow: 'hidden', boxShadow: `inset 0 ${-9 * p.sx * cam.z * (1 - e)}px 0 ${P.sageDark}`}}>
      <div
        style={{
          position: 'absolute',
          left: lerp(c.x, 960, e) - L - CARD_W / 2 + shift,
          top: lerp(c.y, 540, e) - T - CARD_H / 2,
          width: CARD_W,
          height: CARD_H,
          transform: `scale(${k}) rotate(${p.r * (1 - e)}deg)`,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          padding: '0 18px 6px 14px',
          boxSizing: 'border-box',
        }}
      >
        <div style={{transform: `scale(${1 - clampOut(out, 0)})`, display: 'flex'}}>
          <Glyph i={GOALS} c={P.sage} />
        </div>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 33, color: P.white, whiteSpace: 'nowrap', display: 'flex', gap: '0.26em', overflow: 'hidden', paddingTop: 4}}>
          {lab.map((w, n) => (
            <span key={n} style={{display: 'inline-block', transform: `translateY(${(n === 3 ? hop : 0) + clampOut(out, n + 1) * 140}%)`}}>
              {w}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
};
const clampOut = (o: number, k: number) => Math.max(0, Math.min(1, o * 1.4 - k * 0.12));

export const Scene: React.FC = () => {
  const t = useGT();
  let sx = 0;
  let sy = 0;
  const L = landTimes();
  const sorted = [...L].sort((a, b) => a - b);
  sorted.slice(0, 14).forEach((tl, j) => {
    const [kx, ky] = kick(t, tl, j < 4 ? 16 : 8);
    sx += kx;
    sy += ky;
  });
  return (
    <Workspace
      t={t}
      hideCard={GOALS}
      people={<Student t={t} />}
      mid={<Sign t={t} />}
      front={
        <>
          <Creak t={t} />
          <Dust t={t} />
          <Gust t={t} />
          <Goals t={t} />
          <Question t={t} />
        </>
      }
      screen={<Expand t={t} />}
      shake={[sx, sy]}
    />
  );
};
