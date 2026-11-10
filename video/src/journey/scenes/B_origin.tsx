/* B: where Sahara started. Logo blooms on the poster, calendar flips to AUG, the team doctor's insight,
   the handwritten wishes blow off the note like leaves. Room shared with C and D (B_room.tsx). */
import React from 'react';
import {P, useGT, ramp, easeOutCubic} from '../kit';
import {Workspace, NOTE_BOX} from './B_room';

const LINE_T = [15.38, 15.54, 15.7, 15.82]; // say what they want
const BLOW_T = 17.0; // gets written down
const INK = '#33405E';
const lines = [0, 1, 2, 3].map((k) => {
  const x0 = NOTE_BOX.x + 26;
  const len = [250, 220, 262, 170][k];
  const y0 = NOTE_BOX.y + 84 + k * 36;
  const pts: string[] = [];
  const N = 40;
  for (let s = 0; s <= N; s++) {
    const u = s / N;
    const x = x0 + u * len + Math.sin(u * 40 + k) * 3;
    const y = y0 - Math.abs(Math.sin(u * 19 + k * 1.7)) * 11 + Math.sin(u * 6 + k) * 2;
    pts.push(`${s === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`);
  }
  return {d: pts.join(' '), x0, len, y0};
});

const Writing: React.FC<{t: number}> = ({t}) => {
  if (t < LINE_T[0] || t > BLOW_T + 1.6) return null;
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {lines.map((L, k) => {
        const p = easeOutCubic(ramp(t, LINE_T[k], LINE_T[k] + 0.42));
        const b0 = BLOW_T + k * 0.09;
        const q = ramp(t, b0, b0 + 0.5);
        if (p <= q) return null;
        return <path key={k} d={L.d} pathLength={1} stroke={INK} strokeWidth={4.5} fill="none" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={`0 ${q} ${p - q} 3`} />;
      })}
      {lines.map((L, k) =>
        Array.from({length: 8}, (_, j) => {
          const u = (j + 0.5) / 8;
          const ts = BLOW_T + k * 0.09 + u * 0.5;
          const dt = t - ts;
          if (dt < 0 || dt > 1.3) return null;
          const x = L.x0 + u * L.len + 560 * dt + 50 * Math.sin(dt * 7 + j) * dt;
          const y = L.y0 - 6 - 210 * dt + 120 * dt * dt + 26 * Math.sin(dt * 9 + j + k);
          const r = dt * 420 * (j % 2 ? 1 : -1) + j * 30;
          const o = 1 - ramp(dt, 0.75, 1.3);
          const s = 1 + 0.5 * Math.sin(dt * 12 + j);
          return <ellipse key={k * 10 + j} cx={0} cy={0} rx={13} ry={5.5 * s} fill={j % 3 === 0 ? P.sage : INK} opacity={o} transform={`translate(${x} ${y}) rotate(${r})`} />;
        }),
      )}
      {/* wind gusts */}
      {[0, 1, 2].map((g) => {
        const u = ramp(t, BLOW_T - 0.1 + g * 0.12, BLOW_T + 0.7 + g * 0.12);
        if (u <= 0 || u >= 1) return null;
        const y = NOTE_BOX.y + 40 + g * 70;
        const x = NOTE_BOX.x - 260 + u * 900;
        return (
          <path
            key={'w' + g}
            d={`M${x} ${y} q 60 -30 120 0 t 120 0 q 30 -24 4 -40`}
            stroke="rgba(255,255,255,0.95)"
            strokeWidth={7}
            fill="none"
            strokeLinecap="round"
            opacity={Math.sin(u * Math.PI)}
            pathLength={1}
            strokeDasharray={`${0.5 + 0.5 * Math.sin(u * Math.PI)} 2`}
          />
        );
      })}
    </svg>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  return <Workspace t={t} wall={<Writing t={t} />} />;
};
