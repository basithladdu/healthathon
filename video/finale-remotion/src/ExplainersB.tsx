import React from 'react';
import {staticFile} from 'remotion';
import {C, FONT_HEAD} from './theme';
import {Card, Chip, Check, Ripple, FingerDot, rise, inOutE, outE, prog, EW, EH} from './ui';
import {lerp} from './anim';
import type {EP} from './ExplainersA';

const IW = 716;
const IH = 346;

/** 7. QR scan beam and a tap counter. */
export const Ex7: React.FC<EP> = ({f}) => {
  const N = 21;
  const cells: boolean[] = [];
  let s = 7;
  for (let i = 0; i < N * N; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    cells.push((s >> 8) % 100 < 48);
  }
  const finder = (r: number, c: number) => (r < 7 && c < 7) || (r < 7 && c >= N - 7) || (r >= N - 7 && c < 7);
  const fin = (r: number, c: number) => {
    const rr = r >= N - 7 ? r - (N - 7) : r;
    const cc = c >= N - 7 ? c - (N - 7) : c;
    const m = Math.max(Math.abs(rr - 3), Math.abs(cc - 3));
    return m !== 2;
  };
  const cs = 11;
  const sweep = prog(f, 26, 100, inOutE);
  const beamY = (0.5 - 0.5 * Math.cos(sweep * Math.PI * 2)) * (N * cs);
  const done = prog(f, 100, 118, outE);
  const doc = prog(f, 116, 146, outE);
  const tap = prog(f, 168, 176, outE);
  return (
    <Card>
      <div style={{position: 'absolute', left: 6, top: 6, width: N * cs + 28, height: N * cs + 28, ...rise(f, 0)}}>
        <div style={{position: 'absolute', left: 14, top: 14, width: N * cs, height: N * cs, background: '#fff'}}>
          {cells.map((on, i) => {
            const r = Math.floor(i / N);
            const c = i % N;
            const isF = finder(r, c);
            const fill = isF ? fin(r, c) : on;
            return fill ? <div key={i} style={{position: 'absolute', left: c * cs, top: r * cs, width: cs, height: cs, background: C.ink}} /> : null;
          })}
          {sweep > 0 && sweep < 1 && (
            <div style={{position: 'absolute', left: -6, right: -6, top: beamY - 3, height: 6, borderRadius: 3, background: C.primary, boxShadow: '0 0 24px 8px rgba(59,117,98,0.45)'}} />
          )}
        </div>
        {[[0, 0], [1, 0], [0, 1], [1, 1]].map(([a, b], i) => (
          <div key={i} style={{position: 'absolute', [a ? 'right' : 'left']: 0, [b ? 'bottom' : 'top']: 0, width: 34, height: 34, borderColor: done > 0.5 ? C.success : C.primary, borderStyle: 'solid', borderWidth: 0, [a ? 'borderRightWidth' : 'borderLeftWidth']: 5, [b ? 'borderBottomWidth' : 'borderTopWidth']: 5, borderRadius: 6}} />
        ))}
        <div style={{position: 'absolute', right: -14, bottom: -14, width: 56, height: 56, borderRadius: 28, background: C.success, display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: done, transform: `scale(${lerp(0.6, 1, done)})`}}>
          <Check size={36} t={done} />
        </div>
      </div>
      <svg style={{position: 'absolute', left: 280, top: 110, width: 80, height: 40, opacity: doc}} viewBox="0 0 80 40">
        <path d="M6 20 H66 M52 8 L68 20 L52 32" fill="none" stroke={C.edge} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <div style={{position: 'absolute', left: 376, top: 14, width: 340, height: 218, borderRadius: 24, background: '#fff', border: `3px solid ${C.line}`, boxShadow: '0 14px 30px rgba(56,46,41,0.10)', padding: 26, opacity: doc, transform: `translateX(${(1 - doc) * 40}px)`}}>
        <div style={{fontSize: 32, fontWeight: 600, color: C.ink, fontFamily: FONT_HEAD}}>Care document</div>
        {[250, 280, 200, 260].map((w, i) => (
          <div key={i} style={{width: w, height: 12, borderRadius: 6, background: i === 0 ? C.primaryWash : C.line, marginTop: 22}} />
        ))}
        <div style={{position: 'absolute', right: 22, bottom: 20}}>
          <Chip bg={C.successWash} fg="#2F6140">Latest</Chip>
        </div>
      </div>
      <div style={{position: 'absolute', left: 376, top: 280, display: 'flex', alignItems: 'center', gap: 18, ...rise(f, 150)}}>
        <div style={{width: 60, height: 60, position: 'relative'}}>
          <FingerDot x={30} y={30} press={tap} />
          <Ripple f={f} start={170} x={30} y={30} />
        </div>
        <div style={{fontFamily: FONT_HEAD, fontSize: 56, lineHeight: 1, color: C.primaryPressed}}>{f >= 172 ? 1 : 0}</div>
        <div style={{fontSize: 34, color: C.inkSub}}>tap</div>
      </div>
    </Card>
  );
};

/** 8. Thermometer filling and body-map pulse. */
export const Ex8: React.FC<EP> = ({f}) => {
  const fill = prog(f, 22, 120, inOutE) * 0.7;
  const val = Math.round(fill * 10);
  const col = fill < 0.35 ? C.primary : fill < 0.55 ? '#8FA05A' : C.marigold;
  const tubeTop = 14;
  const tubeH = 230;
  const pulse = (f - 70) / 36;
  return (
    <Card>
      <div style={{position: 'absolute', left: 36, top: tubeTop, width: 64, height: tubeH + 20, borderRadius: 32, background: C.well, border: `3px solid ${C.line}`, ...rise(f, 0)}} />
      <div style={{position: 'absolute', left: 48, top: tubeTop + (1 - fill) * (tubeH + 20) + 8 * 0, width: 40, height: fill * (tubeH + 20) + 20, borderRadius: 20, background: col}} />
      <div style={{position: 'absolute', left: 14, top: tubeH - 8, width: 108, height: 108, borderRadius: 54, background: col, border: `3px solid ${C.surface}`}} />
      {[0, 5, 10].map((n) => (
        <div key={n} style={{position: 'absolute', left: 124, top: tubeTop + 250 - (n / 10) * 236 - 20, fontSize: 28, color: C.inkFaint, fontWeight: 600, ...rise(f, 6)}}>{n}</div>
      ))}
      <div style={{position: 'absolute', left: 176, top: 70, width: 150}}>
        <div style={{fontFamily: FONT_HEAD, fontSize: 120, lineHeight: 1, color: col}}>{val}</div>
        <div style={{fontSize: 32, color: C.inkSub, marginTop: 8}}>Distress</div>
      </div>
      {/* body map */}
      <svg style={{position: 'absolute', left: 430, top: -4, width: 286, height: 350, ...rise(f, 20)}} viewBox="0 0 286 350">
        <circle cx="143" cy="46" r="34" fill={C.well} stroke={C.edge} strokeWidth="3" />
        <path d="M143 84 C 96 84, 78 100, 74 150 L 66 232 C 66 244, 84 244, 86 232 L 100 160 L 104 330 C 104 344, 134 344, 136 330 L 143 240 L 150 330 C 152 344, 182 344, 182 330 L 186 160 L 200 232 C 202 244, 220 244, 220 232 L 212 150 C 208 100, 190 84, 143 84 Z" fill={C.well} stroke={C.edge} strokeWidth="3" strokeLinejoin="round" />
        {pulse > 0 &&
          [0, 0.33, 0.66].map((o, i) => {
            const t = (((pulse + o) % 1) + 1) % 1;
            return <circle key={i} cx="143" cy="158" r={14 + t * 50} fill="none" stroke={C.rose} strokeWidth="4" opacity={(1 - t) * 0.8} />;
          })}
        <circle cx="143" cy="158" r={14 + (pulse > 0 ? Math.sin(pulse * 6) * 1.5 : 0)} fill={C.rose} opacity={prog(f, 66, 80, outE)} />
      </svg>
      <div style={{position: 'absolute', left: 372, top: 128, fontSize: 32, fontWeight: 600, color: C.rose, ...rise(f, 34)}}>Pain</div>
    </Card>
  );
};

/** 10. Tablet strip to dose ticks. */
export const Ex10: React.FC<EP> = ({f}) => {
  const scan = prog(f, 20, 66, inOutE);
  const rows = ['Morning', 'Afternoon', 'Night'];
  return (
    <Card>
      <div style={{position: 'absolute', left: 4, top: 6, width: 240, height: 320, borderRadius: 26, background: 'linear-gradient(160deg,#EDEFF0,#C9CFD2)', border: `3px solid ${C.edge}`, ...rise(f, 0)}}>
        {Array.from({length: 8}).map((_, i) => (
          <div key={i} style={{position: 'absolute', left: 32 + (i % 2) * 102, top: 24 + Math.floor(i / 2) * 72, width: 74, height: 56, borderRadius: 28, background: '#FFFDF9', border: `3px solid ${C.edge}`}} />
        ))}
      </div>
      {[[0, 0], [1, 0], [0, 1], [1, 1]].map(([a, b], i) => (
        <div key={i} style={{position: 'absolute', [a ? 'left' : 'left']: a ? 214 : -8, top: b ? 306 : -14, width: 40, height: 40, borderColor: C.primary, borderStyle: 'solid', borderWidth: 0, [a ? 'borderRightWidth' : 'borderLeftWidth']: 5, [b ? 'borderBottomWidth' : 'borderTopWidth']: 5, borderRadius: 6, opacity: prog(f, 10, 24, outE) * (1 - prog(f, 90, 100, outE) * 0.3)}} />
      ))}
      {scan > 0 && scan < 1 && <div style={{position: 'absolute', left: 4, top: 6 + scan * 300, width: 240, height: 5, borderRadius: 3, background: C.primary, boxShadow: '0 0 20px 6px rgba(59,117,98,0.4)'}} />}
      <svg style={{position: 'absolute', left: 250, top: 100, width: 100, height: 60, opacity: prog(f, 70, 90, outE)}} viewBox="0 0 100 60">
        <path d="M6 30 H80 M64 14 L82 30 L64 46" fill="none" stroke={C.edge} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {rows.map((r, i) => {
        const d = 84 + i * 30;
        const t = prog(f, d, d + 16, outE);
        const tick = prog(f, d + 40 + 6 * i, d + 56 + 6 * i, outE);
        return (
          <div key={r} style={{position: 'absolute', left: 360, top: 14 + i * 108, width: 356, height: 88, borderRadius: 24, background: C.surface, border: `3px solid ${tick > 0.5 ? C.primary : C.line}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', opacity: t, transform: `translateX(${(1 - t) * 30}px)`}}>
            <div style={{fontSize: 34, fontWeight: 600}}>{r}</div>
            <div style={{width: 52, height: 52, borderRadius: 26, background: tick > 0.5 ? C.primary : C.surface, border: `4px solid ${tick > 0.5 ? C.primary : C.line}`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
              <Check size={34} t={tick} />
            </div>
          </div>
        );
      })}
    </Card>
  );
};

const RANGES: {name: string; pts: number[]}[] = [
  {name: '7 days', pts: [0.55, 0.62, 0.5, 0.7, 0.66, 0.78, 0.74]},
  {name: '30 days', pts: [0.3, 0.38, 0.34, 0.46, 0.42, 0.55, 0.5, 0.62, 0.58, 0.7, 0.66, 0.78, 0.72, 0.8]},
  {name: 'All', pts: [0.15, 0.2, 0.18, 0.26, 0.3, 0.28, 0.4, 0.46, 0.44, 0.56, 0.6, 0.66, 0.7, 0.78, 0.74, 0.84]},
];
/** 9. Symptom trend line drawing with range switches. */
export const Ex9: React.FC<EP> = ({f}) => {
  const per = 62;
  const start = 14;
  const idx = Math.min(2, Math.max(0, Math.floor((f - start) / per)));
  const lf = f - start - idx * per;
  const cw = IW;
  const ch = 220;
  const pts = RANGES[idx].pts;
  const path = pts.map((v, i) => `${i === 0 ? 'M' : 'L'}${(i / (pts.length - 1)) * (cw - 20) + 10},${ch - v * (ch - 30) - 10}`).join(' ');
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 0, display: 'flex', gap: 12, ...rise(f, 0)}}>
        {RANGES.map((r, i) => (
          <Chip key={r.name} bg={i === idx ? C.primary : C.well} fg={i === idx ? '#fff' : C.inkSub}>{r.name}</Chip>
        ))}
      </div>
      <div style={{position: 'absolute', right: 0, top: 4, fontSize: 32, fontWeight: 600, color: C.rose, ...rise(f, 10)}}>Pain</div>
      <svg style={{position: 'absolute', left: 0, top: 70, width: cw, height: ch}} viewBox={`0 0 ${cw} ${ch}`}>
        {[0, 1, 2].map((i) => (
          <line key={i} x1="0" x2={cw} y1={20 + i * 90} y2={20 + i * 90} stroke={C.line} strokeWidth="2" />
        ))}
        <path d={path} fill="none" stroke={C.rose} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - prog(lf, 4, 46, inOutE)} />
        <circle cx={cw - 10} cy={ch - pts[pts.length - 1] * (ch - 30) - 10} r="10" fill={C.rose} opacity={prog(lf, 40, 50, outE)} />
      </svg>
      <div style={{position: 'absolute', left: 0, bottom: 0, fontSize: 28, color: C.inkFaint, ...rise(f, 14)}}>First entry</div>
      <div style={{position: 'absolute', right: 0, bottom: 0, fontSize: 28, color: C.inkFaint, ...rise(f, 14)}}>Today</div>
    </Card>
  );
};

/** 13. Visits timeline. */
export const Ex13: React.FC<EP> = ({f}) => {
  const visits = [
    {d: '14 Oct', who: 'Dr. Mehta', sp: 'Palliative care'},
    {d: '2 Oct', who: 'Dr. Iyer', sp: 'Oncology'},
    {d: '18 Sep', who: 'Dr. Khan', sp: 'General medicine'},
  ];
  const tapT = 150;
  const open = prog(f, tapT + 8, tapT + 30, outE);
  return (
    <Card>
      <div style={{position: 'absolute', left: 148, top: 24, width: 4, height: 280 * prog(f, 6, 70, inOutE), background: C.line, borderRadius: 2}} />
      {visits.map((v, i) => {
        const d = 12 + i * 22;
        const t = prog(f, d, d + 20, outE);
        const hi = i === 0 && f > tapT;
        return (
          <div key={i} style={{position: 'absolute', left: 0, top: i * 112, width: IW, height: 100, opacity: t, transform: `translateY(${(1 - t) * 20}px)`}}>
            <div style={{position: 'absolute', left: 0, top: 14, fontSize: 32, fontWeight: 600, color: C.inkSub, width: 120}}>{v.d}</div>
            <div style={{position: 'absolute', left: 138, top: 20, width: 28, height: 28, borderRadius: 14, background: hi ? C.primary : C.surface, border: `5px solid ${C.primary}`}} />
            <div style={{position: 'absolute', left: 196, top: 0, width: 520, height: 96, borderRadius: 22, background: hi ? C.primaryWash : C.surface, border: `3px solid ${hi ? C.primary : C.line}`, padding: '8px 22px', lineHeight: 1.2}}>
              <div style={{fontSize: 32, fontWeight: 600}}>{v.who}</div>
              <div style={{fontSize: 28, color: C.inkSub}}>{v.sp}</div>
              {i === 0 && (
                <div style={{position: 'absolute', right: 16, top: 24, opacity: open, transform: `translateX(${(1 - open) * 20}px)`}}>
                  <Chip bg={C.primary} fg="#fff">Prescription</Chip>
                </div>
              )}
            </div>
          </div>
        );
      })}
      <FingerDot x={640} y={46 + (1 - prog(f, tapT - 30, tapT, outE)) * 80} press={prog(f, tapT, tapT + 6, outE) * (1 - prog(f, tapT + 8, tapT + 14, outE))} />
      <Ripple f={f} start={tapT + 4} x={640} y={46} />
    </Card>
  );
};

/** 11. Sort a document, then a cited answer. */
export const Ex11: React.FC<EP> = ({f}) => {
  const s1 = 1 - prog(f, 100, 118, inOutE);
  const s2 = prog(f, 106, 126, outE);
  const travel = prog(f, 30, 76, inOutE);
  const hl = prog(f, 190, 220, inOutE);
  return (
    <Card>
      <div style={{position: 'absolute', inset: 0, opacity: s1}}>
        <div style={{position: 'absolute', left: 30 + travel * 250, top: 40 + travel * 60 + (1 - prog(f, 0, 22, outE)) * -40, width: 200, height: 250, borderRadius: 16, background: '#fff', border: `3px solid ${C.line}`, boxShadow: '0 14px 30px rgba(56,46,41,0.12)', padding: 22, opacity: prog(f, 0, 14, outE), transform: `rotate(${(1 - travel) * -4}deg)`}}>
          <div style={{width: 110, height: 16, borderRadius: 8, background: C.primary}} />
          {[150, 156, 120, 150, 90].map((w, i) => (
            <div key={i} style={{width: w, height: 11, borderRadius: 6, background: C.line, marginTop: 20}} />
          ))}
        </div>
        {['Bills', 'Lab reports'].map((b, i) => {
          const target = i === 1;
          const on = target && travel > 0.9;
          return (
            <div key={b} style={{position: 'absolute', left: 430, top: 20 + i * 150, width: 286, height: 126, borderRadius: 24, background: on ? C.primaryWash : C.surface, border: `3px ${on ? 'solid' : 'dashed'} ${on ? C.primary : C.edge}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 36, fontWeight: 600, color: on ? C.primaryPressed : C.inkSub, ...rise(f, 6 + i * 6)}}>
              {b}
            </div>
          );
        })}
      </div>
      <div style={{position: 'absolute', inset: 0, opacity: s2}}>
        <div style={{position: 'absolute', right: 0, top: 0, padding: '14px 24px', borderRadius: 24, background: C.primary, color: '#fff', fontSize: 30, ...rise(f, 112)}}>Which medicines are listed?</div>
        <div style={{position: 'absolute', left: 0, top: 84, fontSize: 32, fontWeight: 600, color: C.inkSub, ...rise(f, 136)}}>Matching lines</div>
        <div style={{position: 'absolute', left: 0, top: 140, width: IW, height: 190, borderRadius: 20, background: '#fff', border: `3px solid ${C.line}`, padding: '14px 24px', ...rise(f, 150)}}>
          <div style={{fontSize: 28, color: C.inkFaint}}>Prescription summary</div>
          <div style={{position: 'relative', marginTop: 10, height: 48, fontSize: 29, display: 'flex', alignItems: 'center'}}>
            <div style={{position: 'absolute', left: -8, top: 0, height: 48, width: `${hl * 100 * 0.93}%`, background: 'rgba(230,193,104,0.55)', borderRadius: 10}} />
            <span style={{position: 'relative'}}>Medicines as listed for the prescription</span>
          </div>
          <div style={{width: 360, height: 12, borderRadius: 6, background: C.line, marginTop: 20}} />
        </div>
      </div>
    </Card>
  );
};

/** 12. Map pins with one route. */
export const Ex12: React.FC<EP> = ({f}) => {
  const pins = [
    {x: 520, y: 175, c: C.primary},
    {x: 300, y: 230, c: C.marigold},
    {x: 650, y: 235, c: C.marigold},
    {x: 190, y: 160, c: C.primary},
  ];
  const route = prog(f, 112, 172, inOutE);
  return (
    <Card style={{background: '#EEF1E6'}}>
      <svg style={{position: 'absolute', left: -32, top: -32, width: EW, height: EH}} viewBox={`0 0 ${EW} ${EH}`}>
        <path d="M-10 270 C 160 230, 300 330, 480 250 S 700 160, 800 200 L 800 250 C 700 210, 560 300, 480 300 S 160 280, -10 320 Z" fill="#CFE3E6" />
        <ellipse cx="180" cy="120" rx="120" ry="64" fill="#DCE8CB" />
        {[[0, 190, 800, 150], [260, 0, 330, 410], [60, 0, 120, 410], [0, 90, 800, 60]].map(([a, b, c, d], i) => (
          <line key={i} x1={a} y1={b} x2={c} y2={d} stroke="#fff" strokeWidth={i < 2 ? 16 : 10} strokeLinecap="round" />
        ))}
      </svg>
      <div style={{position: 'absolute', left: 0, top: -6, display: 'flex', gap: 10, ...rise(f, 0)}}>
        <Chip bg="rgba(255,253,249,0.94)" fg={C.primaryPressed}>Palliative centre</Chip>
        <Chip bg="rgba(255,253,249,0.94)" fg="#7A4A10">Morphine-listed</Chip>
      </div>
      <svg style={{position: 'absolute', left: 0, top: 0, width: IW, height: IH, overflow: 'visible'}} viewBox={`0 0 ${IW} ${IH}`}>
        <path d="M110 300 C 200 290, 280 270, 350 240 S 480 205, 520 178" fill="none" stroke={C.info} strokeWidth="8" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - route} />
        <circle cx="110" cy="300" r="14" fill={C.info} stroke="#fff" strokeWidth="5" />
        {pins.map((p, i) => {
          const t = prog(f, 24 + i * 14, 24 + i * 14 + 18, outE);
          const chosen = i === 0 && route > 0.95;
          return (
            <g key={i} transform={`translate(${p.x},${p.y - (1 - t) * 40}) scale(${chosen ? 1.25 : 1})`} opacity={t}>
              <path d="M0 0 C -22 -26, -26 -40, -26 -50 A 26 26 0 1 1 26 -50 C 26 -40, 22 -26, 0 0 Z" fill={p.c} stroke="#fff" strokeWidth="4" />
              <circle cx="0" cy="-50" r="9" fill="#fff" />
            </g>
          );
        })}
      </svg>
      <div style={{position: 'absolute', right: 0, bottom: 0, ...rise(f, 168)}}>
        <Chip bg={C.info} fg="#fff" style={{height: 56, fontSize: 30, padding: '0 24px'}}>Directions in Google Maps</Chip>
      </div>
    </Card>
  );
};

/** 14. Caring takes a toll: the four ZBI-4 questions, a burden meter, then the support options (app wording). */
const ZQ = [
  'Stressed, between caring for Rahul and meeting your other responsibilities?',
  'Strained when you are around Rahul?',
  'That your own health has suffered because of caring for Rahul?',
  "That you don't have enough time for yourself because of caring for Rahul?",
];
const ZA = ['Never', 'Rarely', 'Sometimes', 'Quite often', 'Nearly always'];
export const Ex14: React.FC<EP> = ({f}) => {
  const per = 38;
  const start = 8;
  const q = Math.min(3, Math.max(0, Math.floor((f - start) / per)));
  const lf = f - start - q * per;
  const answered = Math.max(0, Math.min(4, Math.floor((f - start - 22) / per) + 1));
  const quiz = 1 - prog(f, start + 4 * per - 6, start + 4 * per + 8, inOutE);
  const res = prog(f, start + 4 * per + 6, start + 4 * per + 26, outE);
  const fill = (answered * 3) / 16;
  const opts = ['Tele-MANAS 14416', 'Counsellor or psychiatrist nearby', 'Respite care', 'Caregiver support groups'];
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 0, width: IW, display: 'flex', alignItems: 'center', gap: 16}}>
        <div style={{flex: 1, height: 16, borderRadius: 8, background: C.line, overflow: 'hidden'}}>
          <div style={{width: `${fill * 100}%`, height: '100%', background: fill > 0.5 ? C.marigold : C.primary, borderRadius: 8}} />
        </div>
        <div style={{fontSize: 28, fontWeight: 600, color: C.inkSub, width: 92, textAlign: 'right'}}>{answered * 3}/16</div>
      </div>
      <div style={{position: 'absolute', left: 0, top: 44, width: IW, opacity: quiz}}>
        {f >= start && (
          <div key={q} style={{opacity: prog(lf, 0, 12, outE), transform: `translateY(${(1 - prog(lf, 0, 12, outE)) * 14}px)`}}>
            <div style={{fontSize: 28, lineHeight: 1.2, color: C.inkFaint, fontWeight: 600}}>In the past month, how often have you felt this way?</div>
            <div style={{fontSize: 31, lineHeight: 1.22, marginTop: 6, minHeight: 80}}>{ZQ[q]}</div>
            <div style={{display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 10}}>
              {ZA.map((a, i) => {
                const chosen = i === 3 && lf > 22;
                return (
                  <div key={a} style={{height: 46, padding: '4px 18px 0', borderRadius: 23, fontSize: 28, fontWeight: 600, lineHeight: '42px', background: chosen ? C.primary : C.surface, color: chosen ? '#fff' : C.ink, border: `2px solid ${C.primary}`}}>{a}</div>
                );
              })}
            </div>
          </div>
        )}
      </div>
      {res > 0 && (
        <div style={{position: 'absolute', left: 0, top: 44, width: IW, opacity: res}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 14, background: '#FDE0E0', border: `2px solid ${C.error}`, borderRadius: 18, padding: '4px 22px 0', height: 50}}>
            <div style={{fontFamily: FONT_HEAD, fontSize: 32, color: '#7A1F1F'}}>You’re carrying a lot</div>
          </div>
          {opts.map((o, i) => {
            const t = prog(f, start + 4 * per + 30 + i * 16, start + 4 * per + 48 + i * 16, outE);
            return (
              <div key={o} style={{marginTop: 6, height: 44, borderRadius: 14, background: C.surface, border: `2px solid ${C.line}`, padding: '5px 20px 0', fontSize: 28, fontWeight: 600, opacity: t, transform: `translateX(${(1 - t) * 24}px)`, whiteSpace: 'nowrap'}}>{o}</div>
            );
          })}
        </div>
      )}
      <div style={{position: 'absolute', left: 0, bottom: 0, fontSize: 28, color: C.inkFaint, opacity: res}}>A screening, not a diagnosis.</div>
    </Card>
  );
};

/** 15. Group posts and replies. */
export const Ex15: React.FC<EP> = ({f}) => {
  const post = (y: number, name: string, text: string, d: number, col: string, indent = 0) => {
    const t = prog(f, d, d + 20, outE);
    return (
      <div style={{position: 'absolute', left: indent, top: y, width: IW - indent, height: 88, borderRadius: 24, background: indent ? C.primaryWash : C.surface, border: `3px solid ${C.line}`, display: 'flex', alignItems: 'center', gap: 18, padding: '0 22px', opacity: t, transform: `translateY(${(1 - t) * 24}px)`}}>
        <div style={{width: 52, height: 52, borderRadius: 26, background: col, flex: '0 0 auto'}} />
        <div style={{minWidth: 0}}>
          <div style={{fontSize: 28, fontWeight: 600, color: C.inkSub}}>{name}</div>
          <div style={{fontSize: 30, whiteSpace: 'nowrap'}}>{text}</div>
        </div>
      </div>
    );
  };
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 0, ...rise(f, 0)}}>
        <Chip>Caring for a parent</Chip>
      </div>
      {post(56, 'Member', 'How do you manage the evenings?', 20, C.rose)}
      {post(150, 'Member', 'A quiet room and a warm drink helped.', 70, C.indigo, 56)}
      {post(244, 'Member', 'Thank you. We will try that.', 120, C.plum)}
    </Card>
  );
};
