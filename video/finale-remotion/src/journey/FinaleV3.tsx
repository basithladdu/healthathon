/* v3: team in one beat, the clinician's trend graph, deployment / cost / sustainability with real costs. */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Rise, clamp, easeInOutCubic, easeOutCubic, pop, ramp} from './kit';
import {Glyph, SP} from './FinaleScenes';
import {Face, Who} from './team';

const At: React.FC<{x: number; y: number; w: number; align?: 'left' | 'center' | 'right'; children: React.ReactNode}> = ({x, y, w, align = 'left', children}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, top: y, width: w}}>{children}</div>
);
const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'; stagger?: number}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={p.stagger ?? 0.045} />
);
const PopAt: React.FC<{t: number; t0: number; x: number; y: number; children: React.ReactNode}> = ({t, t0, x, y, children}) => {
  const s = pop(t, t0, 13, 0.5);
  if (s <= 0.001) return null;
  return <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s})`}}>{children}</div>;
};
const Dot: React.FC<{k: string; d: number; bg: string}> = ({k, d, bg}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)', flexShrink: 0}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);
const Bg: React.FC<{t: number; children: React.ReactNode}> = ({t, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.5) * 50} y={180} r={340} color="rgba(159,209,184,0.40)" blur={90} />
    <Blob x={1660 + Math.cos(t * 0.4) * 50} y={880} r={380} color="rgba(224,160,64,0.22)" blur={100} />
    <div style={{position: 'absolute', inset: 0, transform: `scale(${1.03 - 0.03 * easeOutCubic(ramp(t, 0, 0.6)) + 0.003 * t})`}}>{children}</div>
  </div>
);

/* ---------- deployment, cost and sustainability ---------- */
export const DP_T = [0.15, 6.4, 12.0];
const Step: React.FC<{t: number; t0: number; x: number; y: number; k: string; c: string; head: string; sub: string; w?: number}> = ({t, t0, x, y, k, c, head, sub, w = 480}) => (
  <>
    <PopAt t={t} t0={t0} x={x} y={y}><Dot k={k} d={170} bg={c} /></PopAt>
    <At x={x} y={y + 120} w={w} align="center"><R t={t} t0={t0 + 0.15} text={head} size={46} color={c} weight={800} align="center" /></At>
    <At x={x} y={y + 250} w={w} align="center"><R t={t} t0={t0 + 0.4} text={sub} size={36} color={P.ink} weight={600} align="center" /></At>
  </>
);
export const Deploy: React.FC<SP> = ({t}) => {
  let i = 0;
  for (let k = 0; k < 2; k++) if (t >= DP_T[k] - 0.2) i = k;
  const b = DP_T[i];
  const inP = easeOutCubic(ramp(t, b - 0.2, b + 0.35));
  const outP = i < 1 ? easeInOutCubic(ramp(t, DP_T[i + 1] - 0.45, DP_T[i + 1] - 0.15)) : 0;
  const dx = (1 - inP) * 700 - outP * 1400;
  const qs = ['Rollout plan', 'Cost to run Sahara', ''];
  let body: React.ReactNode = null;
  if (i === 0) {
    const ar = easeOutCubic(ramp(t, b + 1.0, b + 1.4));
    body = (
      <>
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
          <path d="M530 470 L760 470 M734 448 L760 470 L734 492 M1160 470 L1390 470 M1364 448 L1390 470 L1364 492" stroke="#C9B79C" strokeWidth={10} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - ar} />
        </svg>
        <Step t={t} t0={b + 0.4} x={330} y={470} k="heart" c={P.rose} head="Free for patients and caregivers" sub="always" />
        <Step t={t} t0={b + 1.6} x={960} y={470} k="plug" c={P.indigo} head="Hospitals and clinics pay" sub="per clinic, plugged into their EHR" />
        <Step t={t} t0={b + 2.8} x={1590} y={470} k="hospital" c={P.sage} head="National Cancer Grid centres" sub="370+ centres to scale to" />
      </>
    );
  } else if (i === 1) {
    const rows: [string, string, string, string][] = [
      ['people', 'One-time set-up: ₹25,000 per clinic', 'set-up and staff training', P.indigo],
      ['code', 'Recurring: ₹15,000 a month per clinic', 'servers, software, support', P.sage],
    ];
    body = (
      <>
        {rows.map(([k, a, v, c], r) => {
          const t0 = b + 0.4 + r * 0.8;
          const p = pop(t, t0, 12, 0.55);
          if (p <= 0.01) return null;
          return (
            <div key={r} style={{position: 'absolute', left: 330, top: 340 + r * 220, width: 1260, height: 160, display: 'flex', alignItems: 'center', gap: 40, opacity: clamp(p * 2), transform: `translateX(${(1 - Math.min(1, p)) * 60}px)`}}>
              <Dot k={k} d={130} bg={c} />
              <div>
                <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 60, color: P.ink}}>{a}</div>
                <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 40, color: c}}>{v}</div>
              </div>
            </div>
          );
        })}
      </>
    );
  } else {
    const coin = (k: number) => {
      if (t < b + 0.8 + k * 0.35) return null;
      const ph = ((t - b - 0.8 - k * 0.35) % 1.4) / 1.4;
      return <div key={k} style={{position: 'absolute', left: 900 + k * 60, top: 330 + ph * 200, width: 44, height: 44, borderRadius: '50%', background: P.marigold, border: `5px solid ${P.marigoldDeep}`, boxSizing: 'border-box', opacity: 1 - ph}} />;
    };
    body = (
      <>
        {[0, 1, 2].map(coin)}
        <PopAt t={t} t0={b + 0.4} x={560} y={470}><Dot k="hospital" d={170} bg={P.indigo} /></PopAt>
        <PopAt t={t} t0={b + 1.2} x={1360} y={470}><Dot k="code" d={170} bg={P.sage} /></PopAt>
        <At x={560} y={580} w={460} align="center"><R t={t} t0={b + 0.5} text="Hospital and clinic fees" size={46} color={P.indigo} weight={800} align="center" /></At>
        <At x={1360} y={580} w={460} align="center"><R t={t} t0={b + 1.3} text="Servers, AI and support" size={46} color={P.sageDark} weight={800} align="center" /></At>
        <At x={960} y={760} w={1500} align="center"><R t={t} t0={b + 2.0} text="Clinic fees cover the running costs" size={60} color={P.sageDark} weight={800} align="center" /></At>
      </>
    );
  }
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 0, right: 0, top: 40, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 32, color: P.inkSub, letterSpacing: 3}}>DEPLOYMENT · COST</div>
      {[0, 1].map((k) => <div key={k} style={{position: 'absolute', left: 920 + k * 40, top: 92, width: 24, height: 10, borderRadius: 5, background: k === i ? P.sageDark : P.sand}} />)}
      <div style={{position: 'absolute', inset: 0, transform: `translateX(${dx}px)`}}>
        <At x={960} y={130} w={1700} align="center"><R t={t} t0={b} text={qs[i]} size={96} color={P.sageDark} weight={800} align="center" /></At>
        {body}
      </div>
    </Bg>
  );
};

/* ---------- the team in one beat ---------- */
export const Team: React.FC<SP> = ({t}) => {
  const ppl: [Who, string, string, string][] = [
    ['sujay', 'Dr Sujay', 'Pulmonologist', P.sage],
    ['sharada', 'Dr Sharada', 'Pulmonologist', P.sage],
    ['shirin', 'Shirin', 'Medical student', P.sage],
    ['basith', 'Basith', 'Engineer, WeDevIT', P.marigoldDeep],
    ['awaiz', 'Awaiz', 'Engineer, WeDevIT', P.marigoldDeep],
  ];
  return (
    <Bg t={t}>
      <At x={960} y={110} w={1800} align="center"><R t={t} t0={0.1} text="Three clinicians and two engineers" size={86} color={P.sageDark} weight={800} align="center" /></At>
      <At x={960} y={225} w={1800} align="center"><R t={t} t0={0.45} text="met at Health-a-thon" size={56} color={P.inkSub} weight={700} align="center" /></At>
      {ppl.map(([w, n, r, c], i) => {
        const x = 320 + i * 310 + (i >= 3 ? 50 : 0);
        const a = pop(t, 0.7 + i * 0.18, 12, 0.5);
        return (
          <React.Fragment key={w}>
            <Face who={w} x={x} y={520 + Math.sin(t * 1.3 + i) * 5} d={230} s={a} ring={c} />
            <div style={{position: 'absolute', left: x - 160, width: 320, top: 660, textAlign: 'center', opacity: clamp((t - 0.9 - i * 0.18) * 3)}}>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 48, color: P.ink}}>{n}</div>
              <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 30, color: c}}>{r}</div>
            </div>
          </React.Fragment>
        );
      })}
    </Bg>
  );
};

/* ---------- what the doctor sees: the trend graph ---------- */
export const Trend: React.FC<SP> = ({t}) => {
  const W = 1240;
  const H = 480;
  const days = 14;
  const series: [string, number[], string][] = [
    ['Pain', [7, 7, 6, 7, 6, 5, 5, 6, 4, 4, 3, 4, 3, 3], P.rose],
    ['Distress', [8, 7, 7, 6, 6, 6, 5, 5, 5, 4, 4, 4, 3, 3], P.marigold],
    ['Breathlessness', [4, 4, 5, 4, 4, 3, 4, 3, 3, 3, 2, 3, 2, 2], P.indigo],
  ];
  const draw = easeInOutCubic(ramp(t, 0.6, 2.4));
  const px = (d: number) => (d / (days - 1)) * W;
  const py = (v: number) => H - (v / 10) * H;
  const last = Math.floor(draw * (days - 1) + 0.001);
  return (
    <Bg t={t}>
      <At x={960} y={50} w={1800} align="center"><R t={t} t0={0.05} text="What the doctor sees" size={84} color={P.sageDark} weight={800} align="center" /></At>
      <At x={960} y={160} w={1800} align="center"><R t={t} t0={0.35} text="how symptoms change over time" size={48} color={P.inkSub} weight={700} align="center" /></At>
      <div style={{position: 'absolute', left: 260, top: 300}}>
        <svg width={W + 40} height={H + 60} style={{overflow: 'visible'}}>
          {[0, 2, 4, 6, 8, 10].map((v) => (
            <g key={v}>
              <line x1={0} y1={py(v)} x2={W} y2={py(v)} stroke="rgba(56,46,41,0.10)" strokeWidth={2} />
              <text x={-20} y={py(v) + 8} textAnchor="end" fontFamily={FONT_BODY} fontSize={24} fill={P.inkSub}>{v}</text>
            </g>
          ))}
          <text x={0} y={H + 44} fontFamily={FONT_BODY} fontSize={24} fill={P.inkSub}>Day 1</text>
          <text x={W} y={H + 44} textAnchor="end" fontFamily={FONT_BODY} fontSize={24} fill={P.inkSub}>Day 14</text>
          {series.map(([n, v, c]) => (
            <g key={n}>
              <path d={v.map((y, i) => `${i ? 'L' : 'M'}${px(i)} ${py(y)}`).join(' ')} stroke={c} strokeWidth={9} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - draw} />
              {v.map((y, i) => (i <= last && draw > 0 ? <circle key={i} cx={px(i)} cy={py(y)} r={9} fill={P.white} stroke={c} strokeWidth={5} /> : null))}
            </g>
          ))}
        </svg>
      </div>
      {series.map(([n, , c], k) => (
        <div key={n} style={{position: 'absolute', left: 1560, top: 360 + k * 70, display: 'flex', alignItems: 'center', gap: 14, opacity: ramp(t, 0.8 + k * 0.2, 1.1 + k * 0.2)}}>
          <div style={{width: 34, height: 10, borderRadius: 5, background: c}} />
          <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 34, color: P.ink}}>{n}</div>
        </div>
      ))}
      <div style={{position: 'absolute', right: 60, bottom: 30, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.5)'}}>Practice data. All names are fictional.</div>
    </Bg>
  );
};
