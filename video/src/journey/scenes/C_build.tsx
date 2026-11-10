/* C: "So we did what engineers do. We built everything." Basith and Awaiz grin, crack their knuckles,
   laptops glow; tool cards fly out on their words and stack into a wobbling tower; 20 tools in two days (15-16 Sep). */
import React from 'react';
import {P, FONT_HEAD, FONT_BODY, useGT, ramp, kick, pop, easeOutCubic, easeOutQuint, easeInCubic} from '../kit';
import {NameTag} from '../team';
import {Studio, W, ENG_X, TABLE_TOP, LAUNCH, ARRIVE, cardPose, cardColor, cardCount, lerpColor} from './C_room';

const Sparks: React.FC<{t: number}> = ({t}) => {
  const out: React.ReactNode[] = [];
  // knuckle-crack sparks above the raised hands
  const T0 = W.do + 0.05;
  if (t > T0 && t < T0 + 0.7) {
    const u = easeOutCubic(ramp(t, T0, T0 + 0.55));
    const o = 1 - ramp(t, T0 + 0.35, T0 + 0.7);
    ENG_X.forEach((x, k) =>
      [-1, 1].forEach((side) => {
        const hx = x + side * 132;
        const hy = 795 - 210;
        for (let j = 0; j < 4; j++) {
          const a = ((-90 + side * 30 + (j - 1.5) * 32) * Math.PI) / 180;
          const r0 = 24 + u * 26;
          const r1 = r0 + 18 + 10 * (1 - u);
          out.push(<line key={`s${k}${side}${j}`} x1={hx + Math.cos(a) * r0} y1={hy + Math.sin(a) * r0} x2={hx + Math.cos(a) * r1} y2={hy + Math.sin(a) * r1} stroke={P.marigold} strokeWidth={6} strokeLinecap="round" opacity={o} />);
        }
      }),
    );
  }
  // "everything": light bursts out of each laptop, sideways so faces stay clear
  const TE = W.everything;
  if (t > TE - 0.05 && t < TE + 0.9) {
    const u = easeOutCubic(ramp(t, TE - 0.05, TE + 0.6));
    const o = 1 - ramp(t, TE + 0.4, TE + 0.9);
    ENG_X.forEach((x, k) => {
      for (let j = 0; j < 6; j++) {
        const deg = j < 3 ? -175 + j * 22 : -49 + (j - 3) * 22;
        const a = (deg * Math.PI) / 180;
        const cy = TABLE_TOP - 40;
        const r0 = 86 + u * 20;
        const r1 = r0 + 18 + 30 * u;
        out.push(<line key={`r${k}${j}`} x1={x + Math.cos(a) * r0} y1={cy + Math.sin(a) * r0} x2={x + Math.cos(a) * r1} y2={cy + Math.sin(a) * r1} stroke="#FFD98A" strokeWidth={7} strokeLinecap="round" opacity={o} />);
      }
    });
  }
  // flight trails
  LAUNCH.forEach((L, i) => {
    const A = ARRIVE[i];
    if (t < L || t > A + 0.15) return;
    for (let j = 1; j <= 5; j++) {
      const tt = Math.min(t, A) - j * 0.035;
      if (tt < L) continue;
      const p = cardPose(i, tt);
      const o = (1 - j / 6) * (t > A ? 1 - ramp(t, A, A + 0.15) : 1);
      out.push(<circle key={`t${i}${j}`} cx={p.x} cy={p.y} r={16 - j * 2} fill={cardColor(i)} opacity={o * 0.55} />);
    }
  });
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {out}
    </svg>
  );
};

const Tags: React.FC<{t: number}> = ({t}) => {
  const out = 1 - easeInCubic(ramp(t, 35.1, 35.4));
  return (
    <>
      {(['basith', 'awaiz'] as const).map((who, k) => (
        <NameTag key={who} who={who} x={ENG_X[k] - 92} y={404 - k * 6} a={pop(t, W.engineers + k * 0.14, 13, 0.45) * out} />
      ))}
    </>
  );
};

/** counter: "20 tools", then two calendar pages, 15 and 16 Sep */
export const STATS = {x: 1780, y: -360};
export const Stats: React.FC<{t: number}> = ({t}) => {
  if (t < 35.2 || t > 39.2) return null;
  const n = cardCount(t);
  const inS = pop(t, 35.2, 12, 0.45);
  const outS = 1 - easeInCubic(ramp(t, 38.6, 38.95));
  const done = t > W.twenty;
  const twenty = done ? 1 + Math.exp(-(t - W.twenty) * 6) * Math.sin((t - W.twenty) * 20) * 0.16 : 1;
  const last = ARRIVE.filter((a) => a <= t).pop() ?? 0;
  const tick = t - last < 0.16 ? 1 + 0.16 * Math.sin(((t - last) / 0.16) * Math.PI) : 1;
  const bg = lerpColor('#FFFFFF', P.marigold, ramp(t, W.twenty, W.twenty + 0.15));
  const page = (k: number, t0: number, dow: string, d: string) => {
    const s = pop(t, t0, 13, 0.42);
    const hot = ramp(t, t0 + 0.12, t0 + 0.3);
    return (
      <div key={k} style={{width: 220, height: 250, transform: `scale(${s}) rotate(${(k ? 4 : -4) * Math.min(1, s)}deg)`, transformOrigin: '50% 100%', position: 'relative'}}>
        <div style={{position: 'absolute', inset: 0, borderRadius: 18, background: P.white, boxShadow: 'inset 0 -9px 0 #E9DCCB', overflow: 'hidden'}}>
          <div style={{height: 64, background: lerpColor('#8C96A8', P.rose, hot), color: P.white, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 30, textAlign: 'center', lineHeight: '70px', letterSpacing: 2}}>{dow}</div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 104, color: P.ink, textAlign: 'center', lineHeight: '118px'}}>{d}</div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 34, color: P.inkSub, textAlign: 'center', lineHeight: '20px', letterSpacing: 3}}>SEP</div>
        </div>
        {[50, 150].map((rx) => (
          <div key={rx} style={{position: 'absolute', left: rx, top: -12, width: 16, height: 30, borderRadius: 8, background: '#5E5249'}} />
        ))}
      </div>
    );
  };
  return (
    <div style={{position: 'absolute', left: STATS.x - 300, top: STATS.y - 130, width: 600, transform: `scale(${inS * outS})`, transformOrigin: '50% 50%'}}>
      <div style={{width: 600, height: 260, borderRadius: 36, background: bg, boxShadow: `inset 0 -12px 0 ${done ? P.marigoldDeep : '#E9DCCB'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 22, transform: `scale(${twenty})`}}>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 190, color: done ? P.white : P.ink, lineHeight: 1, transform: `scale(${tick})`, minWidth: 230, textAlign: 'right'}}>{Math.max(3, n)}</div>
        <div style={{overflow: 'hidden', paddingBottom: 8}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 86, color: done ? P.white : P.inkSub, transform: `translateY(${(1 - easeOutQuint(ramp(t, W.tools - 0.05, W.tools + 0.4))) * 130}%)`}}>tools</div>
        </div>
      </div>
      <div style={{display: 'flex', gap: 60, justifyContent: 'center', marginTop: 70}}>
        {t > W.two - 0.05 && page(0, W.two - 0.05, 'TUE', '15')}
        {t > W.days - 0.05 && page(1, W.days - 0.05, 'WED', '16')}
      </div>
    </div>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  let sx = 0;
  let sy = 0;
  for (const [t0, a] of [
    [W.do + 0.05, 6],
    [W.everything, 6],
    [ARRIVE[0], 7],
    [ARRIVE[1], 7],
    [ARRIVE[2], 7],
    [W.twenty, 6],
  ] as [number, number][]) {
    const [kx, ky] = kick(t, t0, a);
    sx += kx;
    sy += ky;
  }
  return (
    <Studio
      t={t}
      front={
        <>
          <Sparks t={t} />
          <Tags t={t} />
        </>
      }
      shake={[sx, sy]}
    />
  );
};
