/* G: back to the same emergency room. One scan of the daughter's QR, and the doctor knows what her mother wanted. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {P, useGT, at, camAt, World, Person, PhoneClip, Rise, FONT_BODY, FONT_HEAD, spring, pop, mv, ramp, clamp, lerp} from '../kit';
import {CAST} from '../cast';
import {RoomBG, RoomLight, RoomFG, Bed, CLOCK, STAND, handPos} from './A_room';

const DOC = {x: 1210, y: 1000, s: 1.1};

/** the whole G shot as a function of film time, so H can freeze on it */
export const GShot: React.FC<{t: number}> = ({t}) => {
  const tBack = at('s16', 'Back');
  const tEm = at('s16', 'emergency');
  const tOne = at('s16', 'One');
  const tScan = at('s16', 'scan');
  const tDoc = at('s16', 'doctor');
  const tKnows = at('s16', 'knows');
  const tWanted = at('s16', 'wanted');

  const cam = camAt(
    [
      {t: 68.1, x: CLOCK.x, y: CLOCK.y + 16, z: 2.45},
      {t: tEm - 0.1, x: CLOCK.x + 4, y: CLOCK.y + 12, z: 2.6},
      {t: tEm + 0.9, x: 900, y: 580, z: 1.1},
      {t: tOne - 0.05, x: 880, y: 590, z: 1.14},
      {t: tScan + 0.35, x: 810, y: 600, z: 1.55},
      {t: tDoc - 0.1, x: 820, y: 590, z: 1.6},
      {t: tKnows + 0.45, x: 890, y: 520, z: 1.14},
      {t: 73.2, x: 890, y: 515, z: 1.16},
    ],
    t,
    0.004,
  );

  // daughter raises her phone (anticipation dip, then up)
  const up = spring(t, tOne - 0.32, 11, 0.5);
  const dip = mv(t, tOne - 0.6, tOne - 0.32) * (1 - up);
  const dArmA = lerp(-14, 116, up) - dip * 10;
  const dElb = lerp(-46, 22, up);
  const dPose = {...CAST.daughter, x: STAND.x, y: STAND.y, s: 1};
  const [hx, hy] = handPos(dPose, t, dArmA, dElb);
  const phoneUp = up > 0.02;

  // doctor raises his phone to scan
  const dUp = spring(t, tScan - 0.3, 11, 0.5);
  const docA = lerp(10, 110, dUp);
  const docE = lerp(10, 20, dUp);
  const docPose = {...CAST.erDoctor, x: DOC.x, y: DOC.y, s: DOC.s, flip: true};
  const [dhx, dhy] = handPos(docPose, t, docA, docE);
  const relaxed = t > tKnows;

  // scan beam
  const beamOn = ramp(t, tScan, tScan + 0.15) * (1 - ramp(t, tDoc + 0.05, tDoc + 0.35));
  const scanY = Math.sin((t - tScan) * 9) * 0.5 + 0.5;
  const ok = pop(t, tDoc - 0.05, 13, 0.45);

  // the wish card floats up from her phone
  const cardP = spring(t, tKnows - 0.15, 7, 0.62);
  const cardX = lerp(hx + 10, 930, cardP);
  const cardY = lerp(hy - 60, 268, cardP) + Math.sin(t * 2) * 4;
  const cardS = clamp(cardP * 1.2) * lerp(0.3, 1, cardP);

  const warm = mv(t, tKnows, tWanted + 0.7) * 0.95;
  const patientExpr = t > tWanted ? 'happy' : 'tired';
  const bpm = lerp(102, 78, mv(t, tKnows, tWanted + 0.6));

  const phoneH = 210;
  const pcx = hx + 4;
  const pcy = hy - 78;

  return (
    <AbsoluteFill style={{background: P.night0, overflow: 'hidden'}}>
      <RoomBG t={t} cam={cam} id="g" warm={warm} bpm={bpm} lamp={1} />
      <World cam={cam}>
        <Person {...CAST.daughter} t={t} x={STAND.x} y={STAND.y} s={1} expr={t > tWanted ? 'happy' : t > tScan ? 'neutral' : 'worried'} look={1} armL={-10} elbowL={-55} armR={dArmA} elbowR={dElb} lean={-dip * 3} headTilt={t > tWanted ? -4 : 2} />
        <Bed t={t} expr={patientExpr} look={t > tKnows ? -0.5 : 0.6} headTilt={t > tWanted ? -4 : 0} />
        <Person
          {...CAST.erDoctor}
          t={t}
          x={DOC.x}
          y={DOC.y}
          s={DOC.s}
          flip
          expr={relaxed ? 'happy' : t > tScan ? 'think' : 'worried'}
          look={0.6}
          armR={docA}
          elbowR={docE}
          armL={4}
          lean={relaxed ? -2 : 2}
          headTilt={relaxed ? 5 : 0}
          hold={
            <g transform={`rotate(${lerp(0, -18, dUp)})`}>
              <rect x={-18} y={-58} width={36} height={66} rx={7} fill="#1B1A1F" />
              <rect x={-14} y={-54} width={28} height={56} rx={4} fill={beamOn > 0.1 ? '#1E5E52' : '#22303F'} />
            </g>
          }
        />
        {/* scan beam from the doctor's phone to the daughter's phone */}
        {beamOn > 0.01 && (
          <svg style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, overflow: 'visible'}} viewBox="0 0 1920 1080">
            <defs>
              <linearGradient id="beamG" x1="1" y1="0" x2="0" y2="0">
                <stop offset="0" stopColor={P.monitor} stopOpacity="0.75" />
                <stop offset="1" stopColor={P.monitor} stopOpacity="0.18" />
              </linearGradient>
            </defs>
            <path d={`M${dhx + 6} ${dhy - 52} L${pcx + 48} ${pcy - 96} L${pcx + 48} ${pcy + 96} Z`} fill="url(#beamG)" opacity={beamOn} style={{mixBlendMode: 'screen'}} />
          </svg>
        )}
        {phoneUp && (
          <div style={{position: 'absolute', left: 0, top: 0}}>
            <PhoneClip clip={7} x={pcx} y={pcy} h={phoneH} rot={-4} startAt={tOne - 0.1} shadow />
          </div>
        )}
        {beamOn > 0.01 && <div style={{position: 'absolute', left: pcx - 44, top: pcy - 96 + scanY * 186, width: 88, height: 5, borderRadius: 3, background: '#B9FFEC', boxShadow: `0 0 18px ${P.monitor}`, opacity: beamOn}} />}
        {ok > 0.01 && t < tKnows + 0.8 && (
          <svg style={{position: 'absolute', left: pcx + 26, top: pcy - 130, width: 64, height: 64, overflow: 'visible', transform: `scale(${ok})`, opacity: 1 - ramp(t, tKnows + 0.5, tKnows + 0.8)}} viewBox="0 0 64 64">
            <circle cx={32} cy={32} r={30} fill={P.sage} />
            <path d="M18 33 L28 43 L47 22" stroke={P.white} strokeWidth={7} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </World>
      <RoomLight t={t} cam={cam} warm={warm} bpm={bpm} />
      <World cam={cam}>
        {cardP > 0.01 && (
          <div style={{position: 'absolute', left: cardX - 370, top: cardY - 95, width: 740, height: 190, transform: `scale(${cardS}) rotate(${(1 - cardP) * -6}deg)`, transformOrigin: '50% 100%'}}>
            <div style={{position: 'absolute', inset: 0, borderRadius: 30, background: P.cream, boxShadow: '0 24px 60px rgba(0,0,0,0.45), 0 0 80px rgba(242,179,94,0.35)'}} />
            <svg width={110} height={110} viewBox="0 0 110 110" style={{position: 'absolute', left: 30, top: 40}}>
              <circle cx={55} cy={55} r={55} fill={P.sageLight} />
              <path d="M28 58 L55 34 L82 58 L82 82 L28 82 Z" fill={P.sage} />
              <path d="M46 82 L46 66 Q55 58 64 66 L64 82 Z" fill={P.marigold} />
            </svg>
            <div style={{position: 'absolute', left: 164, top: 34, right: 24}}>
              <div style={{fontFamily: FONT_BODY, fontSize: 26, fontWeight: 600, color: P.sage, letterSpacing: 1}}>
                <Rise t={t} t0={tKnows + 0.1} text="What she wanted" size={26} color={P.sage} font={FONT_BODY} stagger={0.05} />
              </div>
              <Rise t={t} t0={tWanted - 0.25} text="Comfort care at home" size={50} color={P.ink} font={FONT_HEAD} stagger={0.08} style={{marginTop: 6}} />
            </div>
          </div>
        )}
      </World>
      <RoomFG t={t} cam={cam} />
      {phoneUp && <div style={{position: 'absolute', left: 40, bottom: 34, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,255,255,0.42)'}}>Practice data. All names are fictional.</div>}
    </AbsoluteFill>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  return <GShot t={t} />;
};
