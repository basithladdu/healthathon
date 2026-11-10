/* Scene E 61.06-83.21: the goals-of-care talk. Consent and recording with Dr Sujay, who said what,
   Dr Sujay's 13 topics, the app wrote the note, the doctors said no, the doctor leads, and the
   goals-of-care document read, edited, signed by the doctor and the family, locked with a QR. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {camAt, clamp, easeInCubic, easeOutQuint, FONT_HEAD, kick, lerp, mv, P, Person, pop, ramp, squash, useGT, World} from '../kit';
import {CAST} from '../cast';
import {NameTag} from '../team';
import {FgLeaves, IconCheck, Motes, PhoneSeq, Plant, toScreen} from './E_parts';
import {ChairBack, ChairFront, Floor, Wall} from './E_room';
import {Checklist, Doc, DOC_H, DOC_W, Padlock} from './E_doc';
import {T} from './E_times';

const FLOOR_Y = 935;
const DOC_X = 3580; // world left of the document
const DOC_Y = 70;
const BOARD_X = 1980;

/** phone position in world over time */
const phonePose = (t: number) => {
  // section A/B: in the room beside Dr Sujay
  const inA = easeOutQuint(ramp(t, T.doctor - 0.15, T.doctor + 0.55));
  let x = 1700 + (1 - inA) * 900;
  let y = 500;
  let h = 760;
  let rot = 4 - 7 * mv(t, T.ticks - 0.1, T.ticks + 0.6);
  // travel to the document for "At first, we let the app write the note"
  const tr = mv(t, T.atFirst - 0.35, T.atFirst + 0.55);
  x = lerp(x, 4510, tr);
  y = lerp(y, 520, tr);
  h = lerp(h, 640, tr);
  rot = lerp(rot, 5, tr);
  // flies off as Dr Sharada steps in
  const off = mv(t, T.doctors - 0.4, T.doctors + 0.05, easeInCubic);
  x += off * 900;
  rot += off * 25;
  // returns for the review and the signed document
  const back = easeOutQuint(ramp(t, T.so - 0.1, T.so + 0.6));
  if (t > T.so - 0.1) {
    x = 4600 + (1 - back) * 900;
    y = 492;
    h = 700;
    rot = 3 - 2 * back;
  }
  return {x, y, h, rot};
};

export const Scene: React.FC = () => {
  const t = useGT();
  const camBase = camAt(
    [
      {t: 61.06, x: 900, y: 420, z: 1.5},
      {t: 62.1, x: 960, y: 520, z: 1.0},
      {t: 66.6, x: 990, y: 528, z: 1.03},
      {t: 67.35, x: 1980, y: 520, z: 1.0},
      {t: 70.05, x: 2000, y: 528, z: 1.03},
      {t: 71.0, x: 3920, y: 520, z: 0.85},
      {t: 73.7, x: 3930, y: 515, z: 0.875},
      {t: 75.4, x: 3910, y: 520, z: 0.88},
      {t: 76.2, x: 4090, y: 547, z: 1.05},
      {t: 79.25, x: 4085, y: 552, z: 1.06},
      {t: 79.9, x: 4070, y: 572, z: 1.08},
      {t: 81.9, x: 4070, y: 575, z: 1.09},
      {t: 82.45, x: 4090, y: 547, z: 1.05},
      {t: 83.21, x: 4092, y: 545, z: 1.07},
    ],
    t,
    0,
  );
  const [kx, ky] = kick(t, T.no, 12);
  const [kx2, ky2] = kick(t, T.locks + 0.1, 6);
  const cam = {...camBase, x: camBase.x + kx + kx2 + Math.sin(t * 0.37) * 5, y: camBase.y + ky + ky2 + Math.sin(t * 0.29) * 3};

  /* ---- section A: family + Dr Sujay ---- */
  const heads = [
    {x: 470, y: 330, label: 'Daughter', c: P.rose},
    {x: 760, y: 420, label: 'Patient', c: P.indigo},
    {x: 1130, y: 330, label: 'Dr Sujay', c: P.sageDark},
  ];
  const nod = (i: number) => {
    const x = t - (T.consent + i * 0.12);
    return x > 0 && x < 1.1 ? Math.sin(x * 11) * 5 * (1 - x / 1.1) : 0;
  };
  const recIn = pop(t, T.records - 0.05, 12, 0.5);
  const recOut = mv(t, T.ticks - 0.2, T.ticks + 0.2);
  const ph = phonePose(t);

  /* ---- section C: doctors ---- */
  const shake = (seed: number) => {
    const x = t - (T.said + seed * 0.1);
    return x > 0 && x < 1.0 ? Math.sin(x * 20) * 12 * (1 - x) : 0;
  };
  const step = mv(t, T.leadsThe - 0.05, T.leads + 0.3);
  const antic = t > T.leadsThe - 0.3 && t < T.leadsThe ? -5 * Math.sin(ramp(t, T.leadsThe - 0.3, T.leadsThe) * Math.PI) : 0;
  const giveWay = mv(t, T.then - 0.2, T.then + 0.4);
  const sujX = 3240 + 90 * step - 230 * giveWay;
  const reach = mv(t, T.signs - 0.4, T.signs) * (1 - mv(t, T.signs + 0.8, T.signs + 1.1));
  const shaIn = easeOutQuint(ramp(t, T.doctors - 0.35, T.doctors + 0.2));
  const shaOut = mv(t, T.so - 0.3, T.so + 0.4);
  const shaX = 4560 + (1 - shaIn) * 700 + shaOut * 800;
  const dauIn = easeOutQuint(ramp(t, T.then - 0.1, T.family + 0.25));
  const dauX = 2950 + 420 * dauIn;
  const dauReach = mv(t, T.famSigns - 0.4, T.famSigns) * (1 - mv(t, T.famSigns + 0.9, T.famSigns + 1.2));

  const [ssx, ssy] = squash(t, T.no - 0.05);
  const stampDrop = 1 + 1.4 * (1 - easeOutQuint(ramp(t, T.no - 0.05, T.no + 0.12)));
  const stampOut = mv(t, T.leads - 0.1, T.leads + 0.3);

  const lockIn = pop(t, T.locks - 0.25, 13, 0.45);
  const shut = easeOutQuint(ramp(t, T.locks, T.locks + 0.1));
  const lockW = [DOC_X + DOC_W + 6, DOC_Y + 330] as const;
  const [lsx, lsy] = toScreen(cam, lockW[0], lockW[1]);
  const wipe = easeInCubic(ramp(t, T.end - 0.42, T.end - 0.02));

  const dataFlow = clamp(ramp(t, T.app - 0.4, T.app - 0.1)) * (1 - ramp(t, T.doctors - 0.4, T.doctors - 0.1));

  const phoneSegs = [
    {clip: 1, at: T.doctor, clipFrom: 0.4},
    {clip: 2, at: T.sahara + 0.1, clipFrom: 0.2, rate: 0.6},
    {clip: 3, at: T.ticks - 0.1, clipFrom: 0.3, rate: 0.9},
    {clip: 2, at: T.atFirst - 0.2, clipFrom: 4.2, rate: 0.8},
    {clip: 2, at: T.so, clipFrom: 4.6, rate: 1.0},
    {clip: 5, at: T.signs, clipFrom: 0.2},
  ];

  return (
    <AbsoluteFill style={{background: P.sage, overflow: 'hidden'}}>
      <World cam={cam} depth={0.85}>
        <Wall t={t} />
      </World>
      <World cam={cam} depth={0.92}>
        <Motes t={t} n={40} x0={-200} w={5400} h={900} color="rgba(255,248,238,0.35)" seed={3} />
      </World>
      <World cam={cam}>
        <Floor />
        <Plant x={130} y={920} s={1.1} t={t} />
        <Plant x={2790} y={920} s={0.95} t={t} pot={P.marigold} />
        <Plant x={5150} y={920} s={1.05} t={t} pot={P.plum} />

        {/* ---------- section A: the talk ---------- */}
        <ChairBack />
        <Person {...CAST.patient} t={t} x={760} y={820} s={1.15} sitting expr={t > T.consent ? 'happy' : 'neutral'} look={0.5} headTilt={nod(1) + 3} talk={t > T.who && t < T.what + 0.3} />
        <ChairFront />
        <Person {...CAST.daughter} t={t} x={470} y={FLOOR_Y} s={1.2} look={0.6} expr={t > T.consent ? 'happy' : 'neutral'} headTilt={nod(0) + 4} armR={28} elbowR={-30} talk={t > T.what && t < T.ticks + 0.4} />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={1130}
          y={FLOOR_Y}
          s={1.2}
          flip
          look={0.6}
          expr={t > T.consent + 0.2 ? 'happy' : 'neutral'}
          headTilt={nod(2) - 2}
          talk={(t > 61.1 && t < T.consent - 0.05) || (t > T.sahara && t < T.who) || (t > T.ticks && t < T.topics + 0.8)}
          armR={lerp(10, 48, mv(t, T.doctor, T.records))}
          elbowR={lerp(10, 70, mv(t, T.doctor, T.records))}
          hold={
            <g transform="rotate(-10)">
              <rect x={-18} y={-58} width={36} height={70} rx={7} fill="#1B1A1F" />
              <rect x={-14} y={-54} width={28} height={62} rx={5} fill={P.cream} />
              <circle cx={0} cy={-14} r={7} fill={P.rose} opacity={t > T.records ? 0.6 + 0.4 * Math.sin(t * 8) : 0} />
            </g>
          }
        />
        <NameTag who="sujay" x={1222} y={610} a={pop(t, T.everyone - 0.1, 12, 0.5) * (1 - ramp(t, T.sahara - 0.2, T.sahara))} size={1.15} showRole />
        {heads.map((h, i) => {
          const pIn = pop(t, T.everyone + i * 0.13, 13, 0.45);
          const out = ramp(t, T.records - 0.1, T.records + 0.15);
          const chipIn = pop(t, T.who + i * 0.16, 13, 0.45);
          const fly = mv(t, T.what + 0.15 + i * 0.12, T.what + 0.7 + i * 0.12, easeInCubic);
          const tx = ph.x - h.x;
          const ty = 300 + i * 90 - h.y;
          const arc = Math.sin(fly * Math.PI) * -140;
          return (
            <React.Fragment key={i}>
              {pIn > 0 && out < 1 && (
                <div style={{position: 'absolute', left: h.x - 36, top: h.y - 36, width: 72, height: 72, borderRadius: '50%', background: P.cream, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${pIn * (1 - out)})`, boxShadow: '0 8px 18px rgba(10,30,20,0.25)'}}>
                  <IconCheck s={46} color={P.sage} p={ramp(t, T.everyone + i * 0.13 + 0.1, T.everyone + i * 0.13 + 0.4)} />
                </div>
              )}
              {chipIn > 0 && fly < 1 && (
                <div
                  style={{
                    position: 'absolute',
                    left: h.x,
                    top: h.y,
                    transform: `translate(-50%,-50%) translate(${tx * fly}px, ${ty * fly + arc}px) scale(${chipIn * (1 - 0.6 * fly)})`,
                    padding: '10px 26px',
                    borderRadius: 999,
                    background: h.c,
                    color: P.white,
                    fontFamily: FONT_HEAD,
                    fontWeight: 600,
                    fontSize: 36,
                    whiteSpace: 'nowrap',
                    boxShadow: '0 8px 18px rgba(10,30,20,0.25)',
                  }}
                >
                  {h.label}
                </div>
              )}
            </React.Fragment>
          );
        })}
        {/* recording row */}
        <div style={{position: 'absolute', left: 420, top: 120, width: 760, height: 96, transformOrigin: '0 50%', transform: `scale(${recIn * (1 - recOut)})`}}>
          <div style={{position: 'absolute', left: 0, top: 0, width: 760, height: 96, borderRadius: 48, background: 'rgba(28,62,50,0.6)'}} />
          <div style={{position: 'absolute', left: 24, top: 24, width: 48, height: 48, borderRadius: '50%', background: P.rose, transform: `scale(${1 + 0.12 * Math.sin(t * 7)})`}} />
          <div style={{position: 'absolute', left: 24, top: 24, width: 48, height: 48, borderRadius: '50%', border: `6px solid ${P.rose}`, boxSizing: 'border-box', transform: `scale(${1 + ((t * 1.4) % 1) * 0.9})`, opacity: 1 - ((t * 1.4) % 1)}} />
          <div style={{position: 'absolute', left: 92, top: 24, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 38, color: P.cream}}>REC</div>
          {Array.from({length: 20}).map((_, i) => {
            const hgt = 16 + 46 * Math.abs(Math.sin(t * 6.2 + i * 0.9) * Math.sin(t * 2.3 + i * 0.37));
            const grow = clamp((t - T.records - i * 0.02) / 0.25);
            return <div key={i} style={{position: 'absolute', left: 190 + i * 27, top: 48 - (hgt * grow) / 2, width: 12, height: hgt * grow, borderRadius: 6, background: P.cream}} />;
          })}
        </div>

        {/* ---------- section B: 13 topics ---------- */}
        <Checklist t={t} x={BOARD_X} y={110} />

        {/* ---------- section C: the note, the doctors, the document ---------- */}
        <Doc t={t} x={DOC_X} y={DOC_Y} />
        {dataFlow > 0 && (
          <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: dataFlow}} width={10} height={10}>
            <path d={`M${ph.x - 120} 250 Q${ph.x - 260} 40 ${DOC_X + DOC_W - 150} 240`} stroke={P.cream} strokeWidth={11} strokeDasharray="2 26" strokeLinecap="round" fill="none" strokeDashoffset={-t * 80} />
          </svg>
        )}
        {t >= T.no - 0.05 && stampOut < 1 && (
          <div style={{position: 'absolute', left: DOC_X + DOC_W / 2, top: DOC_Y + DOC_H / 2, transform: `translate(-50%,-50%) translateY(${-stampOut * 80}px) rotate(-12deg) scale(${ssx * stampDrop}, ${ssy * stampDrop})`, opacity: 1 - stampOut}}>
            <div style={{padding: '14px 80px 24px', borderRadius: 34, background: P.rose, color: P.cream, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 170, lineHeight: 1, boxShadow: '0 16px 30px rgba(80,20,40,0.3)'}}>No</div>
          </div>
        )}
        <Person {...CAST.sharada} t={t} x={shaX} y={FLOOR_Y} s={1.2} flip look={0.8} expr={t < T.leads ? 'neutral' : 'happy'} headTilt={shake(1)} armR={t > T.leads + 0.3 ? 18 : 6} />
        <NameTag who="sharada" x={shaX + 95} y={500} a={pop(t, T.doctors - 0.1, 12, 0.5) * (1 - ramp(t, T.so - 0.4, T.so - 0.1))} size={1.2} showRole />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={sujX}
          y={FLOOR_Y}
          s={1.2}
          look={0.8}
          lean={antic + step * 3 - giveWay * 3}
          expr={t < T.doctors ? 'think' : t < T.leadsThe ? 'neutral' : 'happy'}
          headTilt={shake(0)}
          armR={lerp(12, 52, step) + 14 * reach}
          elbowR={lerp(0, 40, step) - 20 * reach}
          hold={
            step > 0.25 && t < T.then && !(t > T.signs - 0.12 && t < T.signs + 0.95) ? (
              <g transform="rotate(-62)">
                <rect x={-30} y={-9} width={120} height={18} rx={9} fill={P.marigold} />
                <rect x={50} y={-9} width={40} height={18} rx={9} fill={P.marigoldDeep} />
                <path d="M-30 -9 L-50 0 L-30 9 Z" fill={P.ink} />
              </g>
            ) : undefined
          }
        />
        <NameTag who="sujay" x={sujX - 130} y={372} a={pop(t, T.atFirst + 0.1, 12, 0.5) * (1 - ramp(t, T.then - 0.3, T.then))} size={1.2} showRole />
        <Person {...CAST.daughter} t={t} x={dauX} y={FLOOR_Y} s={1.2} look={0.8} expr={t > T.locks ? 'happy' : 'neutral'} armR={lerp(10, 52, dauReach)} elbowR={lerp(-10, 24, dauReach)} opacity={dauIn > 0.01 ? 1 : 0} />
        {lockIn > 0 && (
          <div style={{position: 'absolute', left: lockW[0] - 75, top: lockW[1] - 85, transform: `scale(${lockIn}) rotate(${(1 - shut) * -10}deg)`}}>
            <Padlock shut={shut} s={1.25} />
          </div>
        )}

        {/* the phone */}
        <PhoneSeq t={t} segs={phoneSegs} x={ph.x} y={ph.y} h={ph.h} rot={ph.rot} noteColor="rgba(255,248,238,0.7)" />
      </World>
      <World cam={cam} depth={1.25}>
        <FgLeaves x={-120} y={1180} s={1.1} t={t} />
        <FgLeaves x={5300} y={1220} s={1.2} t={t} flip />
      </World>
      {t < T.start + 0.4 && <div style={{position: 'absolute', inset: 0, background: P.sage, opacity: 1 - easeOutQuint(ramp(t, T.start, T.start + 0.38))}} />}
      {wipe > 0 && <div style={{position: 'absolute', left: lsx - 2400 * wipe, top: lsy - 2400 * wipe, width: 4800 * wipe, height: 4800 * wipe, borderRadius: '50%', background: P.cream}} />}
    </AbsoluteFill>
  );
};
