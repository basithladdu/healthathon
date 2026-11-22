/* Scene E props: Dr Sujay's 13-topic checklist board and the goals-of-care document (GOCD). */
import React from 'react';
import {clamp, easeOutCubic, easeOutQuint, FONT_BODY, FONT_HEAD, mv, P, pop, ramp} from '../kit';
import {Face} from '../team';
import {IconCheck, Pen} from './E_parts';
import {T, TOPICS, tickTime} from './E_times';

/* ---------- 13-topic checklist ---------- */
export const Checklist: React.FC<{t: number; x: number; y: number}> = ({t, x, y}) => {
  const W = 640;
  const rowH = 47;
  const done = TOPICS.filter((_, i) => t >= tickTime(i) + 0.08).length;
  const cur = TOPICS.findIndex((_, i) => t < tickTime(i) + 0.08);
  const boardIn = pop(t, T.ticks - 0.15, 11, 0.5);
  const cPop = done > 0 ? 1 + 0.18 * Math.exp(-(t - tickTime(done - 1) - 0.08) * 12) : 1;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: W, height: 780, transformOrigin: '50% 100%', transform: `scale(${0.6 + 0.4 * boardIn}) rotate(${(1 - boardIn) * 6 + Math.sin(t * 0.7) * 0.4}deg)`, opacity: clamp(boardIn * 2)}}>
      <div style={{position: 'absolute', inset: 0, borderRadius: 30, background: P.cream, boxShadow: '0 30px 60px rgba(10,30,20,0.3)'}} />
      <div style={{position: 'absolute', left: 0, top: 0, width: W, height: 112, borderRadius: '30px 30px 0 0', background: P.sageLight}} />
      <div style={{position: 'absolute', left: 34, top: 18, fontFamily: FONT_BODY, fontSize: 22, color: P.inkSub, letterSpacing: 1}}>INDIA'S END-OF-LIFE CARE GUIDELINES</div>
      <div style={{position: 'absolute', left: 34, top: 44, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 40, color: P.ink, whiteSpace: 'nowrap'}}>13 topics to cover</div>
      <div style={{position: 'absolute', right: 24, top: 32, padding: '6px 18px', borderRadius: 999, background: done === 13 ? P.sage : P.marigold, color: P.white, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 32, transform: `scale(${cPop})`}}>
        {done}/13
      </div>
      {TOPICS.map((name, i) => {
        const tt = tickTime(i);
        const rIn = easeOutQuint(ramp(t, T.ticks + 0.05 + i * 0.03, T.ticks + 0.5 + i * 0.03));
        const on = pop(t, tt, 16, 0.5);
        const active = i === cur && t > T.dr - 0.2;
        const ry = 128 + i * rowH;
        return (
          <div key={name} style={{position: 'absolute', left: 22, top: ry, width: W - 44, height: rowH - 6, borderRadius: 14, background: active ? 'rgba(224,160,64,0.16)' : on > 0.5 ? 'rgba(59,117,98,0.07)' : 'transparent', transform: `translateX(${(1 - rIn) * 60}px)`, opacity: rIn}}>
            <div style={{position: 'absolute', left: 10, top: 4, width: 34, height: 34, borderRadius: '50%', background: P.sand}} />
            <div style={{position: 'absolute', left: 10, top: 4, width: 34, height: 34, borderRadius: '50%', background: P.sage, transform: `scale(${on})`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
              <IconCheck s={28} color={P.white} p={ramp(t, tt + 0.04, tt + 0.18)} />
            </div>
            <div style={{position: 'absolute', left: 62, top: 3, fontFamily: FONT_BODY, fontSize: 27, fontWeight: on > 0.5 ? 600 : 400, color: on > 0.5 ? P.ink : P.inkSub, whiteSpace: 'nowrap'}}>{name}</div>
            <div style={{position: 'absolute', right: 14, top: 8, fontFamily: FONT_BODY, fontSize: 20, color: on > 0.5 ? P.sage : 'rgba(94,82,73,0.5)', whiteSpace: 'nowrap'}}>{on > 0.5 ? 'Discussed' : active ? 'Listening' : 'Not yet'}</div>
          </div>
        );
      })}
    </div>
  );
};

/* ---------- small graphics ---------- */
const QR: React.FC<{s: number; p: number}> = ({s, p}) => {
  const n = 21;
  const c = s / n;
  const cells: React.ReactNode[] = [];
  const finder = (x: number, y: number) => x >= 0 && x < 7 && y >= 0 && y < 7;
  for (let y = 0; y < n; y++)
    for (let x = 0; x < n; x++) {
      let on: boolean;
      const f = finder(x, y) ? [x, y] : finder(x - 14, y) ? [x - 14, y] : finder(x, y - 14) ? [x, y - 14] : null;
      if (f) {
        const [a, b] = f;
        on = a === 0 || a === 6 || b === 0 || b === 6 || (a >= 2 && a <= 4 && b >= 2 && b <= 4);
      } else if ((x === 7 || y === 7 || x === 13 || y === 13) && (x < 8 || y < 8) && !(x > 13 && y > 13)) on = false;
      else on = Math.abs(Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) % 1 > 0.5;
      const reveal = clamp(p * 1.6 - ((x + y) / (2 * n)) * 0.6);
      if (on && reveal > 0) cells.push(<rect key={x + '_' + y} x={x * c} y={y * c} width={c * reveal + 0.3} height={c * reveal + 0.3} fill={P.ink} />);
    }
  return (
    <svg width={s} height={s} style={{display: 'block'}}>
      {cells}
    </svg>
  );
};

export const Padlock: React.FC<{shut: number; s?: number}> = ({shut, s = 1}) => {
  const lift = (1 - shut) * 22;
  return (
    <svg width={120 * s} height={130 * s} viewBox="0 0 120 130" style={{overflow: 'visible', display: 'block'}}>
      <circle cx={60} cy={72} r={60} fill={P.sage} />
      <path d={`M40 ${66 - lift} L40 ${44 - lift} Q40 ${24 - lift} 60 ${24 - lift} Q80 ${24 - lift} 80 ${44 - lift} L80 ${lift > 4 ? 52 - lift : 66}`} stroke={P.cream} strokeWidth={10} fill="none" strokeLinecap="round" />
      <rect x={30} y={60} width={60} height={46} rx={10} fill={P.cream} />
      <rect x={60} y={60} width={30} height={46} rx={10} fill="rgba(30,60,50,0.12)" />
      <circle cx={60} cy={78} r={6} fill={P.sage} />
      <rect x={57} y={80} width={6} height={14} rx={3} fill={P.sage} />
    </svg>
  );
};

/** text revealed left-to-right like typing, through a clip */
const Typed: React.FC<{p: number; children: React.ReactNode; style?: React.CSSProperties}> = ({p, children, style}) => (
  <div style={{position: 'absolute', whiteSpace: 'nowrap', clipPath: `inset(-10% ${(1 - clamp(p)) * 100}% -10% 0)`, ...style}}>{children}</div>
);

const SIG_DOC = 'M4 46 C16 6 30 4 28 36 C26 62 46 22 58 20 C70 18 60 50 76 46 C92 42 96 16 110 22 C126 30 112 52 132 46 C150 40 160 28 190 34 C204 37 214 30 226 26';
const SIG_FAM = 'M6 40 C10 14 26 12 22 40 C20 56 34 30 44 30 C56 30 48 52 62 46 Q80 36 86 22 Q90 52 104 40 C118 28 126 44 140 38 C160 30 176 44 214 32';

/* ---------- the goals-of-care document ---------- */
export const DOC_W = 700;
export const DOC_H = 830;

type Row = {label: string; value: string};
const SEC1: Row[] = [
  {label: 'Patient', value: 'Mrs Lakshmi, 78'},
  {label: 'Decision maker', value: 'Daughter, named by patient'},
  {label: 'Capacity', value: 'Has capacity'},
];
const SEC3: Row[] = [
  {label: 'Goals of care', value: 'Comfort first'},
  {label: 'Place of care', value: 'Hospital'},
  {label: 'CPR', value: 'Do not attempt'},
  {label: 'ICU care', value: 'Not wanted'},
  {label: 'Ventilatory support', value: 'Not wanted'},
  {label: 'Dialysis', value: 'Not wanted'},
  {label: 'Nutritional support', value: 'By mouth, as she wishes'},
];
const EDIT_ROW = 1;

export const Doc: React.FC<{t: number; x: number; y: number}> = ({t, x, y}) => {
  const docIn = pop(t, T.atFirst - 0.1, 10, 0.5);
  // value modes: app writes (indigo) -> erased on "leads" -> doctor's draft (ink)
  const appStart = T.app - 0.3;
  const appStep = (T.doctors - 0.1 - appStart) / 11;
  const erase = mv(t, T.leads - 0.1, T.leads + 0.35);
  const drStart = T.so + 0.1;
  const drStep = (T.reads - 0.25 - drStart) / 11;
  const valState = (k: number) => {
    if (t < T.leads + 0.35) return {p: ramp(t, appStart + k * appStep, appStart + k * appStep + appStep * 1.4), c: P.indigo, op: 1 - erase};
    return {p: ramp(t, drStart + k * drStep, drStart + k * drStep + 0.3), c: P.ink, op: 1};
  };
  const signed = t >= T.signs + 0.3;
  const locked = t >= T.locks + 0.08;
  const status = locked ? 'Signed and locked' : t >= T.famSigns + 0.5 ? 'Family signed' : signed ? 'Doctor signed' : t >= T.leads ? 'Draft for the doctor' : 'Written by the app';
  const statusC = locked || signed ? P.sage : t >= T.leads ? P.marigoldDeep : P.indigo;
  const statusKey = locked ? 4 : t >= T.famSigns + 0.5 ? 3 : signed ? 2 : t >= T.leads ? 1 : 0;
  const statusT = [T.atFirst, T.leads, T.signs + 0.3, T.famSigns + 0.5, T.locks + 0.08][statusKey];
  const sp = easeOutQuint(ramp(t, statusT, statusT + 0.4));
  const draftOp = ramp(t, T.leads + 0.2, T.leads + 0.6) * (1 - ramp(t, T.signs + 0.1, T.signs + 0.5));
  // reads: highlight sweeps rows; edits: strike + handwritten fix; signs: signatures
  const readP = ramp(t, T.reads - 0.05, T.edits - 0.1);
  const readOp = t > T.reads - 0.05 && t < T.edits + 0.2 ? 1 - ramp(t, T.edits - 0.1, T.edits + 0.2) : 0;
  const strike = mv(t, T.edits, T.edits + 0.25);
  const fix = mv(t, T.edits + 0.25, T.edits + 0.75, easeOutCubic);
  const sigD = mv(t, T.signs, T.signs + 0.75, easeOutCubic);
  const sigF = mv(t, T.famSigns - 0.05, T.famSigns + 0.7, easeOutCubic);
  const qrP = ramp(t, T.locks + 0.1, T.locks + 0.6);
  const lockGlow = locked ? Math.exp(-(t - T.locks - 0.08) * 3) : 0;

  const LX = 40;
  const VX = 290;
  const SIGY = 714;
  const s1y = 200;
  const s3y = 452;
  const rowH3 = 34;
  const readY = s1y - 8 + readP * (s3y + 7 * rowH3 - s1y);
  // pen: writes the correction, then each signature
  let pen: [number, number] | null = null;
  if (t > T.edits + 0.2 && t < T.edits + 0.85) pen = [VX + 130 + 90 * fix, s3y + EDIT_ROW * rowH3 + 26];
  if (t > T.signs - 0.1 && t < T.signs + 0.9) pen = [LX + 6 + 226 * sigD, SIGY + 30 - Math.sin(sigD * 30) * 10];
  if (t > T.famSigns - 0.15 && t < T.famSigns + 0.85) pen = [290 + 6 + 214 * sigF, SIGY + 30 - Math.sin(sigF * 28) * 10];

  const label: React.CSSProperties = {fontFamily: FONT_BODY, fontSize: 22, color: P.inkSub, whiteSpace: 'nowrap'};
  const val = (c: string): React.CSSProperties => ({fontFamily: FONT_BODY, fontSize: 24, fontWeight: 600, color: c});
  const secHead = (n: string, title: string, yy: number) => (
    <div style={{position: 'absolute', left: LX, top: yy, display: 'flex', alignItems: 'center', gap: 10}}>
      <div style={{width: 34, height: 34, borderRadius: 9, background: P.sageLight, color: P.sageDark, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 20, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>{n}</div>
      <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 25, color: P.sageDark, whiteSpace: 'nowrap'}}>{title}</div>
    </div>
  );

  return (
    <div style={{position: 'absolute', left: x, top: y, width: DOC_W, height: DOC_H, transformOrigin: '50% 100%', transform: `scale(${docIn}) rotate(${Math.sin(t * 0.6) * 0.3}deg)`}}>
      {/* paper */}
      <div style={{position: 'absolute', inset: 0, borderRadius: 16, background: '#FFFDF8', boxShadow: `0 40px 80px rgba(10,30,20,0.35), 0 0 0 ${10 * lockGlow}px rgba(159,209,184,${0.8 * lockGlow})`}} />
      <div style={{position: 'absolute', left: DOC_W - 40, top: 0, width: 40, height: DOC_H, borderRadius: '0 16px 16px 0', background: 'rgba(120,90,60,0.06)'}} />
      <div style={{position: 'absolute', left: 0, top: 0, width: DOC_W, height: 10, borderRadius: '16px 16px 0 0', background: P.sage}} />
      {/* header */}
      <div style={{position: 'absolute', left: LX, top: 30, fontFamily: FONT_BODY, fontSize: 16, letterSpacing: 2, color: P.sage, fontWeight: 600}}>SAHARA · GOCD</div>
      <div style={{position: 'absolute', left: LX, top: 58, width: DOC_W - 2 * LX, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 36, lineHeight: 1.12, color: P.ink}}>Emergency care and treatment plan</div>
      <div style={{position: 'absolute', left: LX, top: 104, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 30, color: P.sage}}>Goals of care</div>
      <div style={{position: 'absolute', right: 30, top: 24, height: 38, overflow: 'hidden'}}>
        <div style={{transform: `translateY(${(1 - sp) * 110}%)`, display: 'flex', alignItems: 'center', gap: 8, padding: '5px 16px', borderRadius: 999, background: statusC, color: P.white, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 20, whiteSpace: 'nowrap'}}>{status}</div>
      </div>
      <div style={{position: 'absolute', left: LX, top: 150, width: DOC_W - 2 * LX, height: 3, background: P.sageLight}} />
      {/* reading highlight */}
      <div style={{position: 'absolute', left: LX - 10, top: readY, width: DOC_W - 2 * LX + 20, height: 34, borderRadius: 8, background: 'rgba(224,160,64,0.32)', opacity: readOp}} />
      {/* section I */}
      {secHead('I', 'Patient and family', s1y - 38)}
      {SEC1.map((r, i) => {
        const v = valState(i);
        return (
          <React.Fragment key={r.label}>
            <div style={{...label, position: 'absolute', left: LX, top: s1y + 6 + i * 36}}>{r.label}</div>
            <Typed p={v.p} style={{...val(v.c), left: VX, top: s1y + 4 + i * 36, opacity: v.op}}>
              {r.value}
            </Typed>
          </React.Fragment>
        );
      })}
      {/* section II */}
      {secHead('II', 'In her own words', 322)}
      {(() => {
        const v = valState(3);
        return (
          <Typed p={v.p} style={{left: LX + 4, top: 360, fontFamily: FONT_HEAD, fontStyle: 'italic', fontWeight: 600, fontSize: 34, color: v.c === P.ink ? P.plum : v.c, opacity: v.op}}>
            “I want to stay at home”
          </Typed>
        );
      })()}
      {/* section III */}
      {secHead('III', 'Decisive actions', s3y - 38)}
      {SEC3.map((r, i) => {
        const v = valState(4 + i);
        const yy = s3y + i * rowH3;
        const boxOn = v.p > 0.95 && v.op > 0.5;
        const isEdit = i === EDIT_ROW;
        return (
          <React.Fragment key={r.label}>
            <div style={{position: 'absolute', left: LX, top: yy + 8, width: 22, height: 22, borderRadius: 5, background: boxOn ? (v.c === P.indigo ? P.indigo : P.sage) : P.sand}}>
              {boxOn && (
                <div style={{position: 'absolute', left: 0, top: 0}}>
                  <IconCheck s={22} color={P.white} p={1} />
                </div>
              )}
            </div>
            <div style={{...label, position: 'absolute', left: LX + 32, top: yy + 4}}>{r.label}</div>
            <Typed p={v.p} style={{...val(v.c), left: VX, top: yy + 2, opacity: v.op * (isEdit ? 1 - 0.55 * strike : 1)}}>
              {r.value}
            </Typed>
            {isEdit && strike > 0 && <div style={{position: 'absolute', left: VX - 4, top: yy + 17, width: 112 * strike, height: 5, borderRadius: 3, background: P.rose}} />}
            {isEdit && fix > 0 && (
              <Typed p={fix} style={{left: VX + 126, top: yy - 6, fontFamily: FONT_HEAD, fontStyle: 'italic', fontWeight: 700, fontSize: 34, color: P.marigoldDeep, transform: 'rotate(-3deg)'}}>
                Home
              </Typed>
            )}
          </React.Fragment>
        );
      })}
      <div style={{position: 'absolute', left: LX, top: 698, width: DOC_W - 2 * LX, height: 3, background: P.sageLight}} />
      {/* signatures */}
      {[
        {x: LX, w: 230, lab: 'Doctor', who: 'Dr Sujay', d: SIG_DOC, p: sigD},
        {x: 290, w: 220, lab: 'Family', who: 'Daughter', d: SIG_FAM, p: sigF},
      ].map((g) => (
        <React.Fragment key={g.lab}>
          <svg style={{position: 'absolute', left: g.x + 4, top: SIGY, overflow: 'visible'}} width={g.w} height={70}>
            <path d={g.d} stroke={P.ink} strokeWidth={4.5} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - g.p} opacity={g.p > 0 ? 1 : 0} />
          </svg>
          <div style={{position: 'absolute', left: g.x, top: SIGY + 62, width: g.w, height: 3, background: g.p > 0.98 ? P.sage : 'rgba(94,82,73,0.4)'}} />
          <div style={{position: 'absolute', left: g.x, top: SIGY + 72, fontFamily: FONT_BODY, fontSize: 21, color: P.inkSub, whiteSpace: 'nowrap'}}>
            <span style={{fontWeight: 700, color: P.ink}}>{g.lab}</span> · {g.who}
          </div>
        </React.Fragment>
      ))}
      {/* QR, for the emergency room */}
      {qrP > 0 && (
        <div style={{position: 'absolute', left: DOC_W - 160, top: 710, width: 116, height: 116, padding: 7, borderRadius: 10, background: P.white, boxShadow: '0 4px 12px rgba(10,30,20,0.18)', transform: `scale(${0.6 + 0.4 * easeOutQuint(qrP)})`}}>
          <QR s={102} p={qrP} />
        </div>
      )}
      {/* DRAFT watermark */}
      {draftOp > 0 && (
        <div style={{position: 'absolute', left: 130, top: 400, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 160, color: 'rgba(176,122,46,0.1)', transform: 'rotate(-22deg)', opacity: draftOp, letterSpacing: 10}}>DRAFT</div>
      )}
      {pen && (
        <div style={{position: 'absolute', left: pen[0], top: pen[1], transform: 'rotate(-58deg)', transformOrigin: '0 0'}}>
          <Pen s={1.1} body={P.sageDark} tip={P.marigold} />
        </div>
      )}
    </div>
  );
};
