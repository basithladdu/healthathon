/* v3 (owner notes): the comparison table and a large, real QR code. */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Rise, clamp, easeOutCubic, pop, ramp} from './kit';
import {SP} from './FinaleScenes';

const Bg: React.FC<{t: number; children: React.ReactNode}> = ({t, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.5) * 50} y={180} r={340} color="rgba(159,209,184,0.40)" blur={90} />
    <Blob x={1660 + Math.cos(t * 0.4) * 50} y={880} r={380} color="rgba(224,160,64,0.22)" blur={100} />
    <div style={{position: 'absolute', inset: 0, transform: `scale(${1.02 - 0.02 * easeOutCubic(ramp(t, 0, 0.6))})`}}>{children}</div>
  </div>
);

/* ---------- Others do one piece: the comparison table, row by row ---------- */
const COLS = ['Panha', 'Kaiku / Noona', 'Abridge', 'Vynca', 'ReSPECT / UK care plan', 'Palli Sahayak', 'Pallium India directory', 'Sahara'];
const ROWS: [string, number[]][] = [
  ['Symptom and distress logging by the family', [0, 2, 0, 0, 0, 0, 0]],
  ['Works in Indian languages', [0, 0, 0, 0, 0, 2, 0]],
  ['Medicines with dose reminders', [1, 0, 0, 0, 0, 0, 0]],
  ['Reports, with questions answered', [1, 1, 0, 0, 0, 0, 0]],
  ['Family members share the care', [0, 0, 0, 0, 0, 0, 0]],
  ['Recorded conversation, AI draft, doctor signs', [0, 0, 1, 1, 0, 0, 0]],
  ['Wishes visible in an emergency', [0, 0, 0, 1, 2, 0, 0]],
  ['Palliative centres and oral morphine near you', [0, 0, 0, 0, 0, 0, 1]],
  ['Built for and available in India', [2, 0, 0, 0, 0, 2, 2]],
];
export const CP_R0 = 1.0;
export const CP_STEP = 0.55;
const Mark: React.FC<{v: number; s: number; on?: boolean}> = ({v, s, on}) => {
  const d = 26;
  if (on) return <div style={{width: d, height: d, borderRadius: '50%', background: P.white, transform: `scale(${s})`}} />;
  if (v === 2) return <div style={{width: d, height: d, borderRadius: '50%', background: '#147A6C', transform: `scale(${s})`}} />;
  if (v === 1) return <div style={{width: d, height: d, borderRadius: '50%', border: `4px solid ${P.marigold}`, background: `linear-gradient(90deg, ${P.marigold} 50%, transparent 50%)`, boxSizing: 'border-box', transform: `scale(${s})`}} />;
  return <div style={{width: 18, height: 18, borderRadius: '50%', border: '3px solid #B8B0A8', boxSizing: 'border-box', transform: `scale(${s})`}} />;
};
export const Compare: React.FC<SP> = ({t}) => {
  const x0 = 90;
  const lw = 520;
  const cw = 150;
  const top = 250;
  const hh = 92;
  const rh = 66;
  const sah = x0 + lw + 7 * cw;
  const tableIn = easeOutCubic(ramp(t, 0.3, 0.9));
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: x0, top: 50, width: 1750}}>
        <Rise t={t} t0={0.05} text="Others do one piece, Sahara joins the whole journey" size={66} color="#0F4F4A" weight={800} />
      </div>
      <div style={{position: 'absolute', left: x0, top: 150, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 30, color: P.inkSub, opacity: ramp(t, 0.4, 0.8)}}>From the goals-of-care talk, to every day at home, to the emergency room</div>
      <div style={{position: 'absolute', left: x0, top, width: lw + 8 * cw, height: hh + ROWS.length * rh, background: P.white, borderRadius: 16, boxShadow: '0 24px 60px rgba(30,40,50,0.12)', opacity: tableIn, transform: `translateY(${(1 - tableIn) * 30}px)`}} />
      <div style={{position: 'absolute', left: sah, top, width: cw, height: hh + ROWS.length * rh, background: '#147A6C', borderRadius: 16, opacity: tableIn}} />
      {COLS.map((c, j) => (
        <div key={c} style={{position: 'absolute', left: x0 + lw + j * cw, top, width: cw, height: hh, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: j === 7 ? 30 : 23, lineHeight: 1.1, color: j === 7 ? P.white : P.ink, padding: '0 8px', boxSizing: 'border-box', opacity: ramp(t, 0.5 + j * 0.05, 0.8 + j * 0.05)}}>{c}</div>
      ))}
      {ROWS.map(([label, vals], r) => {
        const t0 = CP_R0 + r * CP_STEP;
        const y = top + hh + r * rh;
        const a = ramp(t, t0, t0 + 0.3);
        return (
          <React.Fragment key={r}>
            <div style={{position: 'absolute', left: x0, top: y, width: lw + 7 * cw, height: 2, background: P.well, opacity: tableIn}} />
            <div style={{position: 'absolute', left: x0 + 20, top: y, height: rh, display: 'flex', alignItems: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 25, color: P.ink, opacity: a, transform: `translateX(${(1 - a) * 30}px)`}}>{label}</div>
            {[...vals, 2].map((v, j) => (
              <div key={j} style={{position: 'absolute', left: x0 + lw + j * cw, top: y, width: cw, height: rh, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                <Mark v={v} s={pop(t, t0 + 0.05 + j * 0.03, 16, 0.5)} on={j === 7} />
              </div>
            ))}
          </React.Fragment>
        );
      })}
      <div style={{position: 'absolute', left: x0, top: top + hh + ROWS.length * rh + 26, display: 'flex', gap: 40, alignItems: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, color: P.inkSub, opacity: ramp(t, CP_R0 + 9 * CP_STEP, CP_R0 + 9 * CP_STEP + 0.4)}}>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={2} s={0.8} /> Does it</span>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={1} s={0.8} /> Partly</span>
        <span style={{display: 'flex', alignItems: 'center', gap: 10}}><Mark v={0} s={1} /> Not described</span>
        <span style={{marginLeft: 40, fontSize: 20}}>Sources: App Store and product pages, team comparison sheet (checked 20 Nov 2026)</span>
      </div>
    </Bg>
  );
};

/* ---------- the QR card, large and held ---------- */
export const Qr: React.FC<SP> = ({t}) => {
  const a = pop(t, 0.15, 10, 0.55);
  const scan = clamp((t - 0.9) / 1.2);
  return (
    <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${P.night1}, ${P.night0})`}}>
      <Blob x={960} y={520} r={460} color="rgba(95,227,192,0.14)" blur={120} />
      <div style={{position: 'absolute', left: 960 - 300, top: 150, width: 600, height: 760, borderRadius: 40, background: P.white, boxShadow: '0 40px 90px rgba(0,0,0,0.45)', transform: `scale(${a}) rotate(${-2 + Math.sin(t) * 0.5}deg)`, display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: 34, boxSizing: 'border-box'}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 14}}>
          <Img src={staticFile('brand/sahara-logo.png')} style={{width: 64, height: 64, objectFit: 'contain'}} />
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 44, color: P.sageDark}}>Emergency card</div>
        </div>
        <div style={{position: 'relative', marginTop: 26}}>
          <Img src={staticFile('brand/qr-sahara.png')} style={{width: 500, height: 500, display: 'block', imageRendering: 'pixelated'}} />
          {t > 0.9 && t < 2.3 && <div style={{position: 'absolute', left: -10, right: -10, top: scan * 490, height: 8, borderRadius: 4, background: P.monitor, boxShadow: `0 0 24px ${P.monitor}`, opacity: 0.85}} />}
        </div>
        <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 30, color: P.inkSub, marginTop: 18}}>Wishes · contacts · medicines</div>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 950, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 44, color: P.white, opacity: ramp(t, 0.6, 1.0)}}>One scan, no login</div>
    </div>
  );
};
