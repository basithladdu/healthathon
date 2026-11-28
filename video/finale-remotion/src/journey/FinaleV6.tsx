/* v5 scenes: the ER conversation in words, the pivot explained, and the one consolidated emergency flow. */
import React from 'react';
import {AbsoluteFill, Freeze, Img, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, P, Person, Rise, clamp, easeInOutCubic, easeOutCubic, lerp, pop, ramp} from './kit';
import {CAST} from './cast';
import {Glyph, SP} from './FinaleScenes';
import {Journey} from './Journey';

const At: React.FC<{x: number; y: number; w: number; align?: 'left' | 'center' | 'right'; children: React.ReactNode}> = ({x, y, w, align = 'left', children}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, top: y, width: w}}>{children}</div>
);
const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={0.05} dur={0.7} />
);
const PopAt: React.FC<{t: number; t0: number; x: number; y: number; children: React.ReactNode}> = ({t, t0, x, y, children}) => {
  const s = pop(t, t0, 9, 0.62);
  if (s <= 0.001) return null;
  return <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s})`}}>{children}</div>;
};
const Dot: React.FC<{k: string; d: number; bg: string}> = ({k, d, bg}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)', flexShrink: 0}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);
const Bg: React.FC<{t: number; dark?: boolean; children: React.ReactNode}> = ({t, dark, children}) => (
  <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: dark ? `linear-gradient(160deg, ${P.night1}, ${P.night0})` : `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
    <Blob x={260 + Math.sin(t * 0.4) * 40} y={180} r={340} color={dark ? 'rgba(95,227,192,0.10)' : 'rgba(159,209,184,0.40)'} blur={90} />
    <Blob x={1660 + Math.cos(t * 0.3) * 40} y={880} r={380} color={dark ? 'rgba(242,179,94,0.10)' : 'rgba(224,160,64,0.22)'} blur={100} />
    <div style={{position: 'absolute', inset: 0}}>{children}</div>
  </div>
);
const Phone: React.FC<{h: number; children: React.ReactNode}> = ({h, children}) => (
  <div style={{width: h * 0.47, height: h, borderRadius: h * 0.07, background: '#1B1A1F', padding: h * 0.018, boxSizing: 'border-box', boxShadow: '0 40px 80px rgba(10,20,30,0.35)'}}>
    <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream, position: 'relative'}}>{children}</div>
  </div>
);

/* ---------- the 3 a.m. conversation, in words (over the held ER frame) ---------- */
const Bubble: React.FC<{t: number; t0: number; x: number; y: number; who: string; text: string; tail: 'left' | 'right' | 'up'; c: string}> = ({t, t0, x, y, who, text, tail, c}) => {
  const a = pop(t, t0, 9, 0.62);
  if (a <= 0.01) return null;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${a})`}}>
      <div style={{position: 'relative', padding: '20px 30px', borderRadius: 30, background: P.white, boxShadow: '0 16px 40px rgba(0,0,0,0.35)'}}>
        <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, color: c, marginBottom: 4}}>{who}</div>
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 40, lineHeight: 1.15, color: P.ink, whiteSpace: 'nowrap'}}>{text}</div>
        <div style={{position: 'absolute', width: 34, height: 34, background: P.white, transform: 'rotate(45deg)', ...(tail === 'left' ? {left: 40, bottom: -14} : tail === 'right' ? {right: 40, bottom: -14} : {left: '50%', top: -14, marginLeft: -17})}} />
      </div>
    </div>
  );
};
export const ErTalk: React.FC<SP> = ({t}) => {
  const dim = 0.45 * easeOutCubic(ramp(t, 0, 0.6));
  const end = ramp(t, 8.6, 9.4);
  return (
    <AbsoluteFill>
      <Freeze frame={250}>
        <Journey audio={false} captions={false} />
      </Freeze>
      <div style={{position: 'absolute', inset: 0, background: `rgba(8,14,24,${dim + end * 0.3})`}} />
      <Bubble t={t} t0={0.6} x={1460} y={420} who="Doctor" text="Should we restart her heart?" tail="right" c={P.indigo} />
      <Bubble t={t} t0={2.4} x={470} y={360} who="Daughter" text="Do everything!" tail="left" c={P.rose} />
      <Bubble t={t} t0={4.2} x={640} y={130} who="Son, on the phone" text="She wouldn't want this…" tail="up" c={P.marigoldDeep} />
      <Bubble t={t} t0={6.0} x={1320} y={150} who="Husband" text="I don't know what she wanted" tail="up" c={P.sageDark} />
      <div style={{position: 'absolute', left: 0, right: 0, top: 820, textAlign: 'center', opacity: ramp(t, 8.4, 9.0)}}>
        <R t={t} t0={8.4} text="Nobody knows what she wanted" size={78} color={P.white} weight={800} align="center" />
      </div>
    </AbsoluteFill>
  );
};

/* ---------- the pivot, spelled out ---------- */
export const PivotCard: React.FC<SP> = ({t}) => {
  const steps: [string, string, string, string][] = [
    ['code', P.indigo, 'First, we built tools for doctors', 'calculators, protocols, checklists'],
    ['people', P.marigoldDeep, '"Why would a family install this?"', 'the question that changed it'],
    ['heart', P.rose, 'So we made it for patients and families', 'website first, then the Android app'],
  ];
  return (
    <Bg t={t}>
      <At x={960} y={150} w={1700} align="center"><R t={t} t0={0.1} text="The pivot" size={100} color={P.sageDark} weight={800} align="center" /></At>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        {[0, 1].map((k) => (
          <path key={k} d={`M${560 + k * 520} 560 L${760 + k * 520} 560 M${734 + k * 520} 538 L${760 + k * 520} 560 L${734 + k * 520} 582`} stroke="#C9B79C" strokeWidth={10} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - easeOutCubic(ramp(t, 1.6 + k * 1.8, 2.1 + k * 1.8))} />
        ))}
      </svg>
      {steps.map(([k, c, h, s], i) => {
        const x = 400 + i * 560;
        const t0 = 0.6 + i * 1.8;
        return (
          <React.Fragment key={i}>
            <PopAt t={t} t0={t0} x={x} y={560}><Dot k={k} d={200} bg={c} /></PopAt>
            <At x={x} y={700} w={520} align="center"><R t={t} t0={t0 + 0.2} text={h} size={50} color={c} weight={800} align="center" /></At>
            <At x={x} y={850} w={500} align="center"><R t={t} t0={t0 + 0.6} text={s} size={36} color={P.ink} weight={600} align="center" /></At>
          </React.Fragment>
        );
      })}
    </Bg>
  );
};

/* ---------- one emergency flow: SOS -> QR -> the ER doctor scans -> what appears ---------- */
export const EM_T = [0.4, 3.0, 5.6, 7.0];
export const Emergency: React.FC<SP> = ({t}) => {
  const sos = pop(t, EM_T[0], 9, 0.6);
  const qr = pop(t, EM_T[1], 9, 0.6);
  const beam = ramp(t, EM_T[2], EM_T[2] + 1.0);
  const page = easeOutCubic(ramp(t, EM_T[3], EM_T[3] + 0.7));
  const beat = 1 + 0.04 * Math.sin(t * 5);
  const rows: [string, string, string, string][] = [
    ['heart', P.rose, 'Care preference', 'Comfort care at home'],
    ['people', P.indigo, 'Contacts', 'Priya (daughter) · Dr Anjali Sharma, palliative care'],
    ['pill', P.marigoldDeep, 'Medicines', 'Ondansetron 4 mg · Dexamethasone 4 mg'],
  ];
  return (
    <Bg t={t} dark>
      <At x={960} y={50} w={1700} align="center"><R t={t} t0={0.1} text="In an emergency" size={80} color={P.white} weight={800} align="center" /></At>
      {/* 1 SOS */}
      <div style={{position: 'absolute', left: 250 - 120, top: 380 - 120, width: 240, height: 240, borderRadius: '50%', background: 'radial-gradient(circle at 35% 30%, #F0786F, #C9372F)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 86, color: P.white, transform: `scale(${sos * beat})`, boxShadow: '0 20px 50px rgba(201,55,47,0.45)'}}>SOS</div>
      <At x={250} y={540} w={380} align="center"><R t={t} t0={EM_T[0] + 0.3} text="1  SOS button" size={40} color={P.white} weight={800} align="center" /></At>
      {/* 2 QR */}
      <div style={{position: 'absolute', left: 520, top: 170, transform: `scale(${qr}) rotate(-3deg)`, transformOrigin: '50% 50%'}}>
        <Phone h={560}><Img src={staticFile('stills/f07-emergency-qr.png')} style={{width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top'}} /></Phone>
      </div>
      <At x={652} y={760} w={420} align="center"><R t={t} t0={EM_T[1] + 0.3} text="2  Her QR code" size={40} color={P.white} weight={800} align="center" /></At>
      {/* 3 the ER doctor scans */}
      <div style={{position: 'absolute', left: 0, top: 0, opacity: clamp(pop(t, EM_T[2] - 0.4) * 2)}}>
        <Person {...(CAST.erDoctor as any)} t={t} x={1000} y={980} s={0.9} flip expr={t > EM_T[3] + 0.8 ? 'happy' : 'think'} armR={40} elbowR={-70} />
      </div>
      {beam > 0 && beam < 1 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
          <path d="M1070 640 L790 380 L790 560 Z" fill="rgba(95,227,192,0.30)" />
        </svg>
      )}
      <At x={1000} y={1000} w={500} align="center"><R t={t} t0={EM_T[2] + 0.2} text="3  The ER doctor scans" size={34} color={P.white} weight={800} align="center" /></At>
      {/* 4 what appears */}
      <div style={{position: 'absolute', left: 1240, top: 150 + (1 - page) * 700, opacity: clamp(page * 2)}}>
        <div style={{width: 600, borderRadius: 30, background: P.white, boxShadow: '0 40px 80px rgba(0,0,0,0.4)', overflow: 'hidden'}}>
          <div style={{background: '#B5442B', color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 32, padding: '22px 30px'}}>Emergency information · signed</div>
          {rows.map(([k, c, h, v], i) => (
            <div key={i} style={{display: 'flex', alignItems: 'center', gap: 22, padding: '24px 30px', borderBottom: i < 2 ? `2px solid ${P.well}` : undefined, opacity: ramp(t, EM_T[3] + 0.5 + i * 0.5, EM_T[3] + 0.9 + i * 0.5)}}>
              <Dot k={k} d={72} bg={c} />
              <div>
                <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, color: P.inkSub}}>{h}</div>
                <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: i === 0 ? 40 : 30, color: P.ink, lineHeight: 1.2}}>{v}</div>
              </div>
            </div>
          ))}
        </div>
        <div style={{marginTop: 18, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 36, color: P.white, opacity: ramp(t, EM_T[3] + 2.2, EM_T[3] + 2.7)}}>4  Her wishes, in seconds, no login</div>
      </div>
      <div style={{position: 'absolute', right: 60, bottom: 24, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,255,255,0.5)'}}>Practice data. All names are fictional.</div>
    </Bg>
  );
};
export const _unused = {lerp, easeInOutCubic};
