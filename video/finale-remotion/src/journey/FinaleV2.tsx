/* v2 additions: section slides, the problem list, deployment / cost / sustainability. Same flat style as FinaleScenes. */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Person, Rise, clamp, easeInOutCubic, easeOutCubic, pop, ramp} from './kit';
import {CAST} from './cast';
import {Glyph, SP} from './FinaleScenes';

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
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)'}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);
const Bg: React.FC<{t: number; children: React.ReactNode}> = ({t, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.5) * 50} y={180} r={340} color="rgba(159,209,184,0.40)" blur={90} />
    <Blob x={1660 + Math.cos(t * 0.4) * 50} y={880} r={380} color="rgba(224,160,64,0.22)" blur={100} />
    <div style={{position: 'absolute', inset: 0, transform: `scale(${1.03 - 0.03 * easeOutCubic(ramp(t, 0, 0.6))})`}}>{children}</div>
  </div>
);

/* ---------- section slide: colour panel wipes in over the last frame, title, hold, wipes off to reveal the next ---------- */
const SEC_COL = [P.night1, P.plum, P.marigoldDeep, P.sageDark, P.rose, P.indigo, P.sage, P.marigoldDeep, P.sageDark];
export const Section: React.FC<{t: number; dur: number; n: number; title: string; first?: boolean}> = ({t, dur, n, title, first}) => {
  const c = SEC_COL[(n - 1) % SEC_COL.length];
  const inP = first ? 1 : easeInOutCubic(ramp(t, 0, 0.45));
  const outP = easeInOutCubic(ramp(t, dur - 0.45, dur));
  const sk = 260;
  // panel covers x from L to Rr (with a slanted edge)
  const L = -sk + outP * (1920 + sk * 2);
  const Rr = -sk + inP * (1920 + sk * 2);
  const clip = `polygon(${L}px 0, ${Rr + sk}px 0, ${Rr}px 1080px, ${L - sk}px 1080px)`;
  const zt = t - 0.35;
  return (
    <div style={{position: 'absolute', inset: 0, clipPath: clip}}>
      <div style={{position: 'absolute', inset: 0, background: `linear-gradient(150deg, ${c}, ${P.night0})`}} />
      {/* drifting rings */}
      {[0, 1, 2].map((k) => (
        <div key={k} style={{position: 'absolute', left: 1400 + k * 120 - 300 + Math.sin(t * 0.6 + k) * 30, top: 200 + k * 180 - 300, width: 600, height: 600, borderRadius: '50%', border: '3px solid rgba(255,255,255,0.10)', transform: `scale(${0.8 + 0.2 * easeOutCubic(ramp(t, 0.2 + k * 0.1, 1.2 + k * 0.1)) + 0.02 * t})`}} />
      ))}
      <div style={{position: 'absolute', left: 200, top: 330, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 150, color: 'rgba(255,255,255,0.22)', transform: `translateY(${(1 - easeOutCubic(ramp(zt, 0, 0.6))) * 60}px)`, opacity: ramp(zt, 0, 0.3)}}>{String(n).padStart(2, '0')}</div>
      <div style={{position: 'absolute', left: 200, top: 520, width: 1500}}>
        <Rise t={t} t0={0.4} text={title} size={130} color={P.white} weight={800} stagger={0.07} />
      </div>
      <div style={{position: 'absolute', left: 205, top: 700, height: 10, width: 260 * easeOutCubic(ramp(zt, 0.3, 0.9)), borderRadius: 5, background: P.lampWarm}} />
      <Img src={staticFile('brand/sahara-logo.png')} style={{position: 'absolute', right: 120, bottom: 110, width: 110, height: 110, objectFit: 'contain', opacity: 0.9 * ramp(zt, 0.4, 0.9), filter: 'brightness(0) invert(1)'}} />
    </div>
  );
};

/* ---------- what goes wrong today ---------- */
export const PB_T = [0.6, 1.6, 2.6, 3.6, 4.6];
export const Problems: React.FC<SP> = ({t}) => {
  const items: [string, string, string][] = [
    ['doc', 'Wishes get lost', P.indigo],
    ['heart', 'Symptoms go unseen', P.rose],
    ['pill', 'Pain relief is hard to find', P.marigoldDeep],
    ['people', 'Caregivers carry it alone', P.plum],
    ['code', "Care in a language they don't read", P.sageDark],
  ];
  return (
    <Bg t={t}>
      <At x={960} y={90} w={1700} align="center"><R t={t} t0={0.1} text="What goes wrong today" size={96} color={P.sageDark} weight={800} align="center" /></At>
      {items.map(([k, label, c], i) => {
        const x = 230 + i * 365;
        const t0 = PB_T[i];
        const a = pop(t, t0, 12, 0.5);
        const lt = t - t0;
        // each icon misbehaves a little: drifts off, fades, wobbles, slumps, scrambles
        let fx: React.CSSProperties = {};
        if (i === 0) fx = {transform: `translate(${Math.min(1.2, Math.max(0, lt - 0.8)) * 30}px, ${-Math.min(1.2, Math.max(0, lt - 0.8)) * 24}px) rotate(${Math.min(1.2, Math.max(0, lt - 0.8)) * 14}deg)`, opacity: 1 - 0.55 * clamp((lt - 0.8) / 2)};
        if (i === 1) fx = {opacity: 1 - 0.6 * clamp((lt - 0.8) / 1.2) + 0.15 * Math.sin(lt * 4) * clamp(lt - 0.8)};
        if (i === 2) fx = {transform: `rotate(${Math.sin(lt * 3) * 10}deg)`};
        if (i === 4) fx = {transform: `skewX(${Math.sin(lt * 5) * 8}deg)`};
        return (
          <React.Fragment key={i}>
            <div style={{position: 'absolute', left: x - 110, top: 300 + Math.sin(t * 1.3 + i) * 6, width: 220, height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${a})`}}>
              {i === 3 ? (
                <div style={{position: 'absolute', left: -x + 110, top: -300 + Math.sin(t * 1.3 + i) * -6}}>
                  <Person {...(CAST.daughter as any)} t={t} x={x} y={530} s={0.62} expr="tired" lean={6} headTilt={-8} />
                </div>
              ) : (
                <div style={fx}><Dot k={k} d={190} bg={c} /></div>
              )}
              {i === 2 && a > 0.5 && <div style={{position: 'absolute', right: 6, top: -4, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 90, color: P.marigoldDeep, transform: `scale(${pop(t, t0 + 0.5)})`}}>?</div>}
            </div>
            <At x={x} y={580} w={330} align="center"><R t={t} t0={t0 + 0.2} text={label} size={44} color={c} weight={800} align="center" /></At>
          </React.Fragment>
        );
      })}
    </Bg>
  );
};

/* ---------- deployment, cost and sustainability: three unhurried beats ---------- */
export const DP_T = [0.4, 5.3, 10.2];
const Step: React.FC<{t: number; t0: number; x: number; y: number; k: string; c: string; head: string; sub: string; w?: number}> = ({t, t0, x, y, k, c, head, sub, w = 460}) => (
  <>
    <PopAt t={t} t0={t0} x={x} y={y}><Dot k={k} d={170} bg={c} /></PopAt>
    <At x={x} y={y + 120} w={w} align="center"><R t={t} t0={t0 + 0.15} text={head} size={46} color={c} weight={800} align="center" /></At>
    <At x={x} y={y + 240} w={w} align="center"><R t={t} t0={t0 + 0.4} text={sub} size={36} color={P.ink} weight={600} align="center" /></At>
  </>
);
export const Deploy: React.FC<SP> = ({t}) => {
  let i = 0;
  for (let k = 0; k < 3; k++) if (t >= DP_T[k] - 0.2) i = k;
  const b = DP_T[i];
  const inP = easeOutCubic(ramp(t, b - 0.2, b + 0.35));
  const outP = i < 2 ? easeInOutCubic(ramp(t, DP_T[i + 1] - 0.45, DP_T[i + 1] - 0.15)) : 0;
  const dx = (1 - inP) * 700 - outP * 1400;
  const qs = ['How is it deployed?', 'Who pays?', 'How does it last?'];
  const arrow = (x1: number, x2: number, y: number, t0: number, c: string) => (
    <path d={`M${x1} ${y} L${x2} ${y} M${x2 - 26} ${y - 22} L${x2} ${y} L${x2 - 26} ${y + 22}`} stroke={c} strokeWidth={10} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - easeOutCubic(ramp(t, t0, t0 + 0.4))} />
  );
  let body: React.ReactNode = null;
  if (i === 0) {
    body = (
      <>
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>{arrow(530, 760, 500, b + 1.0, '#C9B79C')}{arrow(1160, 1390, 500, b + 2.0, '#C9B79C')}</svg>
        <Step t={t} t0={b + 0.3} x={330} y={500} k="hospital" c={P.sage} head="One clinic first" sub="a 90-day pilot" />
        <Step t={t} t0={b + 1.3} x={960} y={500} k="people" c={P.marigoldDeep} head="National Cancer Grid" sub="370+ centres" />
        <Step t={t} t0={b + 2.3} x={1590} y={500} k="plug" c={P.indigo} head="Plugs into hospital EHRs" sub="on ABDM health IDs" />
      </>
    );
  } else if (i === 1) {
    body = (
      <>
        <div style={{position: 'absolute', left: 0, top: 0, opacity: clamp(pop(t, b + 0.3) * 2)}}>
          <Person {...(CAST.patient as any)} t={t} x={250} y={640} s={0.58} expr="happy" />
          <Person {...(CAST.daughter as any)} t={t} x={410} y={640} s={0.6} expr="happy" flip />
        </div>
        <PopAt t={t} t0={b + 0.6} x={500} y={300}><div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 70, color: P.sage, transform: 'rotate(-6deg)'}}>Free</div></PopAt>
        <At x={330} y={680} w={520} align="center"><R t={t} t0={b + 0.4} text="Patients and caregivers" size={44} color={P.sage} weight={800} align="center" /></At>
        <At x={330} y={780} w={520} align="center"><R t={t} t0={b + 0.7} text="always free" size={40} color={P.ink} weight={600} align="center" /></At>
        <Step t={t} t0={b + 1.3} x={960} y={500} k="hospital" c={P.indigo} head="Hospitals and palliative clinics" sub="per enrolled patient" />
        <Step t={t} t0={b + 2.3} x={1590} y={500} k="doc" c={P.marigoldDeep} head="States" sub="a yearly subscription" />
      </>
    );
  } else {
    const coin = (k: number) => {
      const ph = ((t - b - 1.0 - k * 0.35) % 1.4) / 1.4;
      if (t < b + 1.0 + k * 0.35) return null;
      return <div key={k} style={{position: 'absolute', left: 860 + k * 70, top: 350 + ph * 220, width: 40, height: 40, borderRadius: '50%', background: P.marigold, border: `5px solid ${P.marigoldDeep}`, boxSizing: 'border-box', opacity: 1 - ph}} />;
    };
    body = (
      <>
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>{arrow(640, 1280, 470, b + 0.9, '#C9B79C')}{arrow(640, 1280, 770, b + 2.2, '#C9B79C')}</svg>
        {[0, 1, 2].map(coin)}
        <PopAt t={t} t0={b + 0.4} x={420} y={470}><Dot k="hospital" d={150} bg={P.indigo} /></PopAt>
        <At x={420} y={560} w={440} align="center"><R t={t} t0={b + 0.55} text="Hospital fees" size={44} color={P.indigo} weight={800} align="center" /></At>
        <PopAt t={t} t0={b + 1.2} x={1500} y={470}><Dot k="code" d={150} bg={P.sage} /></PopAt>
        <At x={1500} y={560} w={560} align="center"><R t={t} t0={b + 1.35} text="cover servers, AI and support" size={40} color={P.ink} weight={700} align="center" /></At>
        <PopAt t={t} t0={b + 1.7} x={420} y={770}><Dot k="heart" d={150} bg={P.rose} /></PopAt>
        <At x={420} y={860} w={440} align="center"><R t={t} t0={b + 1.85} text="Grants and CSR" size={44} color={P.rose} weight={800} align="center" /></At>
        <PopAt t={t} t0={b + 2.5} x={1500} y={770}><Dot k="people" d={150} bg={P.marigoldDeep} /></PopAt>
        <At x={1500} y={860} w={560} align="center"><R t={t} t0={b + 2.65} text="fund new clinics and languages" size={40} color={P.ink} weight={700} align="center" /></At>
      </>
    );
  }
  return (
    <Bg t={t}>
      <div style={{position: 'absolute', left: 0, right: 0, top: 40, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 32, color: P.inkSub, letterSpacing: 3}}>DEPLOYMENT · COST · SUSTAINABILITY</div>
      {[0, 1, 2].map((k) => <div key={k} style={{position: 'absolute', left: 900 + k * 40, top: 92, width: 24, height: 10, borderRadius: 5, background: k === i ? P.sageDark : P.sand}} />)}
      <div style={{position: 'absolute', inset: 0, transform: `translateX(${dx}px)`}}>
        <At x={960} y={130} w={1700} align="center"><R t={t} t0={b} text={qs[i]} size={96} color={P.sageDark} weight={800} align="center" /></At>
        {body}
      </div>
    </Bg>
  );
};
