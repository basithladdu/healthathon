/* B (s04-s05): Sahara blooms out of the night; the five of us as photo cards and flat characters;
   then a whip down to August, Dr Sujay at the table, and the wishes that never get written down.
   Parts: B_board.tsx (s04-s04b), B_table.tsx (s05), B_parts.tsx (shared drawing). */
import React from 'react';
import {AbsoluteFill, random} from 'remotion';
import {P, useGT, ramp, lerp, pop, easeOutCubic, easeInOutCubic} from '../kit';
import {Board, TB, boardCam} from './B_board';
import {TableRoom} from './B_table';

const T_WHIP = TB.back - 0.04;
const WHIP = 0.32;

/** the night we come from: a warm seed glows, then the logo blooms and cream floods out of it */
const Night: React.FC<{t: number}> = ({t}) => {
  const open = easeOutCubic(ramp(t, TB.sahara - 0.06, TB.sahara + 0.75));
  if (open >= 1) return null;
  const cam = boardCam(t);
  // the logo's screen position
  const cx = 960 + (960 - cam.x) * cam.z;
  const cy = 540 + (450 - cam.y) * cam.z;
  const R = open * 1500;
  const seedIn = pop(t, 10.53, 12, 0.5);
  const ant = ramp(t, TB.sahara - 0.22, TB.sahara - 0.04);
  const pulse = 1 + 0.12 * Math.sin(t * 9);
  const seedR = 16 * seedIn * pulse * lerp(1, 0.7, ant) * (1 - ramp(t, TB.sahara, TB.sahara + 0.1));
  return (
    <>
      <div style={{position: 'absolute', inset: 0, background: `radial-gradient(circle at ${cx}px ${cy}px, rgba(14,26,43,0) ${R}px, ${P.night0} ${R + 2}px)`}} />
      {/* ring of light at the edge of the bloom */}
      {open > 0 && <div style={{position: 'absolute', left: cx - R - 10, top: cy - R - 10, width: 2 * R + 20, height: 2 * R + 20, borderRadius: '50%', border: `${lerp(26, 4, open)}px solid rgba(242,179,94,${0.7 * (1 - open)})`, boxSizing: 'border-box'}} />}
      {/* stars */}
      {Array.from({length: 40}, (_, i) => {
        const x = random('bs' + i) * 1920;
        const y = random('bsy' + i) * 900;
        if (Math.hypot(x - cx, y - cy) < R) return null;
        const r = 1.5 + random('bsr' + i) * 2.5;
        const o = 0.3 + 0.5 * Math.abs(Math.sin(t * 1.6 + i));
        return <div key={i} style={{position: 'absolute', left: x - r, top: y - r - (t - 10.5) * 6, width: r * 2, height: r * 2, borderRadius: r, background: '#F7E6C4', opacity: o}} />;
      })}
      {seedR > 0.5 && (
        <>
          <div style={{position: 'absolute', left: cx - seedR * 7, top: cy - seedR * 7, width: seedR * 14, height: seedR * 14, borderRadius: '50%', background: 'radial-gradient(circle, rgba(242,179,94,0.55) 0%, rgba(242,179,94,0) 65%)'}} />
          <div style={{position: 'absolute', left: cx - seedR, top: cy - seedR, width: seedR * 2, height: seedR * 2, borderRadius: '50%', background: P.lampWarm}} />
        </>
      )}
    </>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  const w = easeInOutCubic(ramp(t, T_WHIP, T_WHIP + WHIP));
  const blur = Math.sin(w * Math.PI) * 16;
  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      {w < 1 && (
        <div style={{position: 'absolute', inset: 0, transform: `translateY(${-1080 * w}px)`, filter: blur > 0.5 ? `blur(${blur}px)` : undefined}}>
          <Board t={t} />
          <Night t={t} />
        </div>
      )}
      {t > T_WHIP && (
        <div style={{position: 'absolute', inset: 0, transform: `translateY(${1080 * (1 - w)}px)`, filter: blur > 0.5 ? `blur(${blur}px)` : undefined}}>
          <TableRoom t={t} />
        </div>
      )}
    </AbsoluteFill>
  );
};
