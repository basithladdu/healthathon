/* v7 fixes: one continuous zoom from the centres map into the morphine pin, and the loop ending with the real QR
   held up large on the daughter's phone before the doctor scans it. */
import React from 'react';
import {AbsoluteFill, Freeze, Img, staticFile} from 'remotion';
import {FONT_BODY, FONT_HEAD, P, Rise, clamp, easeInOutCubic, easeOutCubic, pop, ramp} from './kit';
import {Glyph, SP} from './FinaleScenes';
import {Journey} from './Journey';

const R: React.FC<{t: number; t0: number; text: string; size?: number; color?: string; weight?: number; align?: 'left' | 'center' | 'right'}> = (p) => (
  <Rise t={p.t} t0={p.t0} text={p.text} size={p.size} color={p.color} weight={p.weight ?? 700} align={p.align} stagger={0.05} dur={0.7} />
);
const Dot: React.FC<{k: string; d: number; bg: string}> = ({k, d, bg}) => (
  <div style={{width: d, height: d, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 30px rgba(20,30,40,0.20)', flexShrink: 0}}>
    <Glyph k={k} s={d * 0.52} />
  </div>
);

/* ---------- the centres map, zooming straight into one centre ---------- */
export const MAPZ = {frame: 6018, x: 1220, y: 760, z: 2.8};
export const MapZoom: React.FC<SP> = ({t}) => {
  const z = 1 + (MAPZ.z - 1) * easeInOutCubic(ramp(t, 0.2, 2.0));
  const pin = pop(t, 2.0, 9, 0.5);
  const ring = (t - 2.0) % 1.4;
  // keep the zoom target where it is on screen while scaling around it, then ease it toward the left third
  const ex = easeInOutCubic(ramp(t, 0.2, 2.0));
  const sx = MAPZ.x + (700 - MAPZ.x) * ex;
  const sy = MAPZ.y + (600 - MAPZ.y) * ex;
  return (
    <AbsoluteFill style={{background: P.cream, overflow: 'hidden'}}>
      <div style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, transformOrigin: '0 0', transform: `translate(${sx}px, ${sy}px) scale(${z}) translate(${-MAPZ.x}px, ${-MAPZ.y}px)`}}>
        <Freeze frame={MAPZ.frame}>
          <Journey audio={false} captions={false} />
        </Freeze>
      </div>
      {t > 2.0 && ring > 0 && <div style={{position: 'absolute', left: 700 - 20 - ring * 70, top: 600 - 20 - ring * 70, width: 40 + ring * 140, height: 40 + ring * 140, borderRadius: '50%', border: `5px solid ${P.marigold}`, opacity: 1 - ring / 1.4, boxSizing: 'border-box'}} />}
      {pin > 0.01 && (
        <svg style={{position: 'absolute', left: 700 - 60, top: 600 - 140, overflow: 'visible'}} width={120} height={140}>
          <g transform={`translate(60 140) scale(${pin * 2.2})`}>
            <path d="M0 0 C-14 -18 -22 -30 -22 -42 A22 22 0 1 1 22 -42 C22 -30 14 -18 0 0 Z" fill={P.marigold} />
            <circle cx={0} cy={-42} r={13} fill={P.white} />
            <rect x={-9} y={-46} width={18} height={8} rx={4} fill={P.marigoldDeep} transform="rotate(-35 0 -42)" />
          </g>
        </svg>
      )}
      <div style={{position: 'absolute', left: 0, right: 0, top: 0, height: 240, background: 'linear-gradient(180deg, rgba(255,248,238,0.95), rgba(255,248,238,0))', opacity: ramp(t, 1.6, 2.2)}} />
      <div style={{position: 'absolute', left: 0, right: 0, bottom: 0, height: 300, background: 'linear-gradient(0deg, rgba(255,248,238,1) 55%, rgba(255,248,238,0))', opacity: ramp(t, 0.8, 1.6)}} />
      <div style={{position: 'absolute', left: 1180, top: 0, right: 0, bottom: 0, background: 'linear-gradient(90deg, rgba(255,248,238,0), rgba(255,248,238,1) 45%)', opacity: ramp(t, 1.4, 2.2)}} />
      <div style={{position: 'absolute', left: 0, right: 0, top: 50, textAlign: 'center'}}><R t={t} t0={2.1} text="In India, medical morphine is hard to find" size={58} color={P.ink} weight={800} align="center" /></div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 128, textAlign: 'center'}}><R t={t} t0={2.4} text="most families don't know which centre stocks it" size={40} color={P.inkSub} weight={600} align="center" /></div>
      <div style={{position: 'absolute', left: 980, top: 360, display: 'flex', alignItems: 'center', gap: 26, padding: '20px 30px', borderRadius: 30, background: 'rgba(255,248,238,0.94)', boxShadow: '0 16px 40px rgba(30,40,50,0.18)', opacity: ramp(t, 2.4, 2.8)}}>
        <Dot k="pill" d={120} bg={P.marigold} />
        <div style={{width: 640}}><R t={t} t0={2.5} text="Oral morphine available here" size={58} color={P.marigoldDeep} weight={800} /></div>
      </div>
      <div style={{position: 'absolute', left: 980, top: 600, display: 'flex', alignItems: 'center', gap: 26, padding: '20px 30px', borderRadius: 30, background: 'rgba(255,248,238,0.94)', boxShadow: '0 16px 40px rgba(30,40,50,0.18)', opacity: ramp(t, 4.4, 4.8)}}>
        <Dot k="people" d={120} bg={P.plum} />
        <div style={{width: 640}}>
          <R t={t} t0={4.5} text="Families can also report where they found it" size={46} color={P.plum} weight={800} />
          <div style={{marginTop: 8}}><R t={t} t0={5.0} text="anonymously" size={34} color={P.inkSub} weight={600} /></div>
        </div>
      </div>
    </AbsoluteFill>
  );
};

/* ---------- the loop ending: the real QR held up, the scan, the page ---------- */
const QR_SHOW = 0.1;
const QR_SCAN = 2.9;
const PAGE = 4.1;
export const ErQr: React.FC<SP> = ({t}) => {
  const up = easeOutCubic(ramp(t, QR_SHOW, QR_SHOW + 0.7));
  const away = easeInOutCubic(ramp(t, PAGE, PAGE + 0.6));
  const beam = ramp(t, QR_SCAN, QR_SCAN + 1.0);
  const page = easeOutCubic(ramp(t, PAGE + 0.2, PAGE + 1.0));
  // the phone grows out of the daughter's hand (about 580, 380) to a large, readable size
  const px = 580 + (760 - 580) * up - away * 900;
  const py = 380 + (540 - 380) * up;
  const sc = 0.18 + 0.82 * up;
  const rows: [string, string, string, string][] = [
    ['heart', P.rose, 'What she wanted', 'Comfort care at home'],
    ['people', P.indigo, 'Contacts', 'Priya (daughter) · Dr Anjali Sharma, palliative care'],
    ['pill', P.marigoldDeep, 'Medicines', 'Ondansetron 4 mg · Dexamethasone 4 mg'],
  ];
  return (
    <AbsoluteFill>
      <Freeze frame={6625}>
        <Journey audio={false} captions={false} />
      </Freeze>
      <div style={{position: 'absolute', inset: 0, background: `rgba(8,14,24,${0.35 * up + 0.2 * page})`}} />
      {beam > 0 && beam < 1 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
          <path d="M1140 780 L880 330 L880 760 Z" fill="rgba(95,227,192,0.30)" />
          <rect x={570} y={330 + beam * 400} width={400} height={6} fill={P.monitor} opacity={0.9} />
        </svg>
      )}
      {/* the daughter's phone, large, with the real QR */}
      <div style={{position: 'absolute', left: px, top: py, transform: `translate(-50%,-50%) scale(${sc}) rotate(-3deg)`}}>
        <div style={{width: 400, height: 820, borderRadius: 56, background: '#1B1A1F', padding: 14, boxSizing: 'border-box', boxShadow: '0 40px 90px rgba(0,0,0,0.5)'}}>
          <div style={{width: '100%', height: '100%', borderRadius: 44, overflow: 'hidden', background: P.cream, display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
            <div style={{width: '100%', padding: '26px 24px 14px', display: 'flex', alignItems: 'center', gap: 12, boxSizing: 'border-box'}}>
              <Img src={staticFile('brand/sahara-logo.png')} style={{width: 44, height: 44, objectFit: 'contain'}} />
              <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 28, color: P.sageDark}}>Sahara</div>
            </div>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 34, color: '#B5442B', marginTop: 20}}>Emergency card</div>
            <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 20, color: P.inkSub, marginTop: 6}}>Show this to the doctor</div>
            <div style={{marginTop: 36, padding: 12, background: P.white, borderRadius: 16}}>
              <Img src={staticFile('real/qr_live.png')} style={{width: 320, height: 320, display: 'block', imageRendering: 'pixelated'}} />
            </div>
            <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 20, color: P.inkSub, marginTop: 18}}>Scan: signed wishes, no login</div>
          </div>
        </div>
      </div>
      {/* what the scan opens */}
      <div style={{position: 'absolute', left: 110, top: 60 - (1 - page) * 900, width: 1700, display: 'flex', gap: 36, alignItems: 'flex-start'}}>
        <div style={{width: 720, padding: 14, background: P.white, borderRadius: 18, boxShadow: '0 30px 70px rgba(0,0,0,0.45)'}}>
          <Img src={staticFile('real/plan_left.png')} style={{width: '100%', display: 'block'}} />
        </div>
        <div style={{flex: 1, borderRadius: 26, background: P.white, boxShadow: '0 30px 70px rgba(0,0,0,0.45)', overflow: 'hidden'}}>
          <div style={{background: '#B5442B', color: P.white, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 30, padding: '18px 28px'}}>Opened by the scan · signed plan</div>
          {rows.map(([k, c, h, v], i) => (
            <div key={i} style={{display: 'flex', alignItems: 'center', gap: 22, padding: '22px 28px', borderBottom: i < 2 ? `2px solid ${P.well}` : undefined, opacity: ramp(t, PAGE + 0.9 + i * 0.6, PAGE + 1.4 + i * 0.6)}}>
              <Dot k={k} d={70} bg={c} />
              <div>
                <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 24, color: P.inkSub}}>{h}</div>
                <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: i === 0 ? 44 : 28, color: P.ink, lineHeight: 1.2}}>{v}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div style={{position: 'absolute', right: 60, bottom: 24, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,255,255,0.6)', opacity: clamp(up)}}>Practice data. All names are fictional.</div>
    </AbsoluteFill>
  );
};
