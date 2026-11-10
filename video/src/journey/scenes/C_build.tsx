/* C: "So we did what engineers do. We built everything." Knuckle-stretch, screens glow,
   twenty tool cards fly out of the laptops and stack into a wobbling tower. Room shared with B and D. */
import React from 'react';
import {P, useGT, ramp, kick, easeOutCubic} from '../kit';
import {Workspace, ENG_X, ENG_Y, TABLE_TOP, LAUNCH, ARRIVE, cardPose, cardColor} from './B_room';

const T_CRACK = 19.22; // just before "do"
const T_ALL = 20.51; // "everything"

const Sparks: React.FC<{t: number}> = ({t}) => {
  const out: React.ReactNode[] = [];
  // knuckle-stretch sparks around raised hands
  if (t > T_CRACK && t < T_CRACK + 0.6) {
    const u = easeOutCubic(ramp(t, T_CRACK, T_CRACK + 0.5));
    const o = 1 - ramp(t, T_CRACK + 0.3, T_CRACK + 0.6);
    ENG_X.forEach((x, k) => {
      [-1, 1].forEach((side) => {
        const hx = x + side * 128;
        const hy = ENG_Y - 214;
        for (let j = 0; j < 4; j++) {
          const a = ((-90 + side * 25 + (j - 1.5) * 34) * Math.PI) / 180;
          const r0 = 22 + u * 26;
          const r1 = r0 + 18 + 10 * (1 - u);
          out.push(<line key={`s${k}${side}${j}`} x1={hx + Math.cos(a) * r0} y1={hy + Math.sin(a) * r0} x2={hx + Math.cos(a) * r1} y2={hy + Math.sin(a) * r1} stroke={P.marigold} strokeWidth={6} strokeLinecap="round" opacity={o} />);
        }
      });
    });
  }
  // "everything": light bursts out of each laptop, sideways so faces stay clear
  if (t > T_ALL - 0.05 && t < T_ALL + 0.9) {
    const u = easeOutCubic(ramp(t, T_ALL - 0.05, T_ALL + 0.6));
    const o = 1 - ramp(t, T_ALL + 0.4, T_ALL + 0.9);
    ENG_X.forEach((x, k) => {
      for (let j = 0; j < 6; j++) {
        const deg = j < 3 ? -175 + j * 22 : -49 + (j - 3) * 22;
        const a = (deg * Math.PI) / 180;
        const cy = TABLE_TOP - 40;
        const r0 = 80 + u * 20;
        const r1 = r0 + 18 + 26 * u;
        out.push(<line key={`r${k}${j}`} x1={x + Math.cos(a) * r0} y1={cy + Math.sin(a) * r0} x2={x + Math.cos(a) * r1} y2={cy + Math.sin(a) * r1} stroke="#FFD98A" strokeWidth={7} strokeLinecap="round" opacity={o} />);
      }
    });
  }
  // flight trails behind flying cards
  LAUNCH.forEach((L, i) => {
    const A = ARRIVE(i);
    if (t < L || t > A + 0.15) return;
    for (let j = 1; j <= 5; j++) {
      const tt = Math.min(t, A) - j * 0.035;
      if (tt < L) continue;
      const p = cardPose(i, tt);
      const o = (1 - j / 6) * (t > A ? 1 - ramp(t, A, A + 0.15) : 1);
      out.push(<circle key={`t${i}${j}`} cx={p.x} cy={p.y} r={14 - j * 2} fill={cardColor(i)} opacity={o * 0.55} />);
    }
  });
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {out}
    </svg>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  // tiny camera bumps on the crack and on each labelled card landing
  let sx = 0;
  let sy = 0;
  for (const [t0, a] of [
    [T_CRACK, 6],
    [ARRIVE(0), 7],
    [ARRIVE(1), 7],
    [ARRIVE(2), 7],
    [26.22, 5],
  ] as [number, number][]) {
    const [kx, ky] = kick(t, t0, a);
    sx += kx;
    sy += ky;
  }
  return <Workspace t={t} front={<Sparks t={t} />} shake={[sx, sy]} />;
};
