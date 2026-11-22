/* H: "Oh, wait." Freeze, the late-night idea, then the spotlight swings from the patient to the daughter. */
import React from 'react';
import {AbsoluteFill, Freeze} from 'remotion';
import {P, useGT, at, camAt, World, PhoneClip, Rise, Pill, FONT_BODY, FONT_HEAD, spring, pop, mv, ramp, clamp, lerp, kick, easeInCubic, easeInOutCubic} from '../kit';
import {RoomBG, RoomLight, RoomFG, Bed, SeatedDaughter, Spotlight, toScreen, PATIENT_HEAD, CHAIR, Chair} from './A_room';
import {GShot} from './G_scan';
import {Office, Bulb, BULB} from './H_office';
import {WIN} from '../windows';

const H0 = WIN.H[0];
const H1 = WIN.H[1];
const FREEZE_T = at('s17', 'Oh');

/* part 1: the G shot, frozen on "Oh, wait" */
const FrozenER: React.FC<{t: number}> = ({t}) => {
  const frozen = t >= FREEZE_T;
  const jolt = frozen ? spring(t, FREEZE_T, 22, 0.32) : 0;
  const [kx, ky] = kick(t, FREEZE_T, 16);
  const de = frozen ? clamp((t - FREEZE_T) / 0.12) : 0;
  const shot = <GShot t={Math.min(t, FREEZE_T)} />;
  return (
    <AbsoluteFill style={{transform: `translate(${kx}px,${ky}px) scale(${1 + 0.07 * jolt}) rotate(${-1.2 * jolt}deg)`, filter: `grayscale(${0.85 * de}) brightness(${1 - 0.18 * de}) contrast(${1 + 0.1 * de})`}}>
      {frozen ? <Freeze frame={Math.round((FREEZE_T - H0) * 30)}>{shot}</Freeze> : shot}
      {frozen && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: 0.5 * (1 - ramp(t, FREEZE_T + 0.05, FREEZE_T + 0.35))}}>
          {[180, 410, 660, 905].map((y, i) => (
            <rect key={i} x={0} y={y + ((t * 900 + i * 77) % 40)} width={1920} height={3 + (i % 2) * 3} fill="#FFFFFF" opacity={0.35} />
          ))}
        </svg>
      )}
    </AbsoluteFill>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  const tLate = at('s17', 'Late');
  const tHit = at('s17', 'hit');
  const tKept = at('s18', 'We');
  const tHow = at('s18', 'how');
  const tFeels = at('s18', 'feels');
  const tNobody = at('s18', 'Nobody');
  const tDaughter = at('s18', 'daughter');
  const tSo = at('s19', 'So');
  const tCheck = at('s19', 'check');
  const tSomeone = at('s19', 'someone');
  const tCall = at('s19', 'call');

  /* ---- whip from the frozen ER to the office ---- */
  const whip = easeInOutCubic(ramp(t, tLate - 0.32, tLate + 0.02));
  const showFrozen = t < tLate + 0.02;
  const showOffice = t > tLate - 0.32 && t < tKept + 0.05;

  /* ---- office ---- */
  const oCam = camAt(
    [
      {t: tLate - 0.3, x: 1060, y: 560, z: 1.1},
      {t: tLate + 0.5, x: 960, y: 560, z: 1.12},
      {t: tHit - 0.15, x: 980, y: 560, z: 1.1},
      {t: tHit + 0.5, x: 1130, y: 470, z: 1.3},
      {t: tKept, x: 1190, y: 440, z: 1.42},
    ],
    t,
    0.01,
  );
  const bulbS = pop(t, tHit - 0.04, 13, 0.4);
  const tags = [0, 1, 2].map((i) => pop(t, tLate + 0.15 + i * 0.16, 13, 0.5));
  const looks = mv(t, tHit + 0.1, tHit + 0.4);
  const aExpr = t > tHit + 0.5 ? 'happy' : t > tHit - 0.05 ? 'surprised' : 'tired';
  const aArm = lerp(18, 150, spring(t, tHit + 0.12, 12, 0.5));
  const [bsx, bsy] = toScreen(oCam, BULB.x, BULB.y);
  const flashR = 160 + easeInCubic(ramp(t, tKept - 0.45, tKept)) * 2600;
  const bloomA = ramp(t, tKept - 0.5, tKept - 0.3);
  const flashOut = 1 - ramp(t, tKept + 0.02, tKept + 0.5);

  /* ---- hospital room, night ---- */
  const rCam = camAt(
    [
      {t: tKept, x: 1320, y: 520, z: 1.32},
      {t: tHow - 0.1, x: 1180, y: 540, z: 1.36},
      {t: tFeels + 0.2, x: 820, y: 560, z: 1.55},
      {t: tNobody - 0.05, x: 800, y: 560, z: 1.58},
      {t: tDaughter, x: 480, y: 630, z: 1.68},
      {t: tSo, x: 470, y: 630, z: 1.72},
      {t: tSo + 0.8, x: 700, y: 630, z: 1.5},
      {t: H1, x: 700, y: 610, z: 1.56},
    ],
    t,
    0.004,
  );
  const dHead = {x: CHAIR.x + 6, y: CHAIR.y - 236};
  const swing = mv(t, tNobody + 0.15, tDaughter + 0.05);
  const spotW = {x: lerp(PATIENT_HEAD.x + 20, dHead.x, swing), y: lerp(PATIENT_HEAD.y + 40, dHead.y + 70, swing) - Math.sin(swing * Math.PI) * 120};
  const [ssx, ssy] = toScreen(rCam, spotW.x, spotW.y);
  const spotOn = mv(t, tHow - 0.1, tFeels);
  const lift = mv(t, tCheck, tCall + 0.3);
  const dark = (0.25 + 0.55 * spotOn) * (1 - 0.75 * lift);
  const spotR = (240 + 70 * pop(t, tDaughter, 10, 0.5) * (1 - swing * 0.2)) * (1 + lift * 0.8) + 30 * Math.sin(t * 2);
  const happyD = t > tSomeone + 0.05;
  const toPhone = t > tSo + 0.7 && !happyD;
  const sigh = 0.5 + 0.5 * Math.sin(t * 1.3);
  const chips = [
    {label: 'Pain', x: PATIENT_HEAD.x - 170, y: 420, d: 0},
    {label: 'Breathing', x: PATIENT_HEAD.x + 220, y: 400, d: 0.12},
    {label: 'Sleep', x: PATIENT_HEAD.x + 280, y: 520, d: 0.24},
  ];
  const chipsOut = ramp(t, tNobody, tNobody + 0.4);

  const phoneP = spring(t, tSo + 0.05, 8, 0.6);
  const callP = spring(t, tSomeone - 0.08, 10, 0.5);
  const sageP = easeInCubic(ramp(t, H1 - 0.38, H1));

  return (
    <AbsoluteFill style={{background: P.night0, overflow: 'hidden'}}>
      {/* the hospital room (from "We kept asking") */}
      {t >= tKept - 0.05 && (
        <AbsoluteFill>
          <RoomBG t={t} cam={rCam} id="h" lamp={0.55 + 0.45 * lift} warm={lift * 0.5} bpm={80} chair={false} />
          <World cam={rCam}>
            <Chair />
            <SeatedDaughter
              t={t}
              expr={happyD ? 'happy' : toPhone ? 'worried' : 'sad'}
              lean={happyD ? 0 : toPhone ? 5 : 11 + sigh * 3}
              headTilt={happyD ? -2 : toPhone ? 4 : 13 + sigh * 3}
              look={happyD ? 0.6 : toPhone ? 1 : -0.5}
            />
            <Bed t={t} expr="tired" look={0.4} />
          </World>
          <RoomLight t={t} cam={rCam} lamp={0.55 + 0.45 * lift} warm={lift * 0.6} bpm={80} />
          <RoomFG t={t} cam={rCam} />
          <Spotlight sx={ssx} sy={ssy} r={spotR} dark={dark} />
          <World cam={rCam}>
            {chips.map((c, i) => {
              const s = pop(t, tFeels - 0.1 + c.d, 13, 0.45) * (1 - chipsOut);
              if (s <= 0.01) return null;
              return (
                <div key={i} style={{position: 'absolute', left: c.x - 100, top: c.y - 26 - chipsOut * 60 + Math.sin(t * 2 + i) * 5, width: 200, display: 'flex', justifyContent: 'center', transform: `scale(${s})`, opacity: 1 - chipsOut}}>
                  <Pill bg={P.sageLight} color={P.sageDark} size={26}>
                    {c.label}
                  </Pill>
                </div>
              );
            })}
          </World>
          {/* caregiver check phone */}
          {phoneP > 0.01 && (
            <>
              <PhoneClip clip={14} x={1430} y={lerp(1500, 560, phoneP)} h={760} rot={lerp(12, 4, phoneP) + Math.sin(t * 1.3) * 0.6} startAt={tSo + 0.2} />
              <div style={{position: 'absolute', left: 1640, top: lerp(1500, 560, phoneP) + 250, width: 230, textAlign: 'left', lineHeight: 1.3, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,255,255,0.45)'}}>Practice data. All names are fictional.</div>
              <div style={{position: 'absolute', left: 1430 - 200, top: lerp(1500, 560, phoneP) - 470, width: 400, display: 'flex', justifyContent: 'center', transform: `scale(${pop(t, tCheck - 0.1, 12, 0.45)})`}}>
                <Pill bg={P.rose} color={P.white} size={30}>
                  A check for her too
                </Pill>
              </div>
            </>
          )}
          {/* someone to call */}
          {callP > 0.01 && (
            <div style={{position: 'absolute', left: 760, top: 150, width: 460, height: 200, transform: `scale(${callP}) rotate(${(1 - callP) * 8 - 2}deg)`, transformOrigin: '20% 100%'}}>
              <div style={{position: 'absolute', inset: 0, borderRadius: 32, background: P.cream, boxShadow: '0 24px 60px rgba(0,0,0,0.45)'}} />
              <svg width={112} height={112} viewBox="0 0 112 112" style={{position: 'absolute', left: 28, top: 44}}>
                <circle cx={56} cy={56} r={56} fill={P.sage} />
                <circle cx={56} cy={56} r={56 + 10 * Math.abs(Math.sin(t * 4))} fill={P.sage} opacity={0.2} />
                <path d="M38 34 Q34 36 34 44 Q38 66 60 80 Q70 84 76 78 L80 72 Q80 68 76 66 L66 62 Q62 62 60 66 Q50 62 46 50 Q50 48 50 44 L46 34 Q44 30 38 34 Z" fill={P.white} transform={`rotate(${Math.sin(t * 18) * 4 * (1 - ramp(t, tCall + 0.3, tCall + 0.6))} 56 56)`} />
              </svg>
              <div style={{position: 'absolute', left: 164, top: 28, right: 18}}>
                <Rise t={t} t0={tSomeone} text="Tele-MANAS" size={40} color={P.sageDark} font={FONT_HEAD} />
                <Rise t={t} t0={tCall - 0.1} text="14416" size={74} color={P.marigoldDeep} font={FONT_HEAD} spacing={3} />
              </div>
            </div>
          )}
          {/* flash from the bulb fading into the room */}
          {flashOut > 0 && <div style={{position: 'absolute', inset: 0, background: '#FFE3A0', opacity: flashOut}} />}
        </AbsoluteFill>
      )}
      {/* office */}
      {showOffice && (
        <AbsoluteFill style={{transform: `translateX(${(1 - whip) * 1920}px)`}}>
          <Office t={t} cam={oCam} looks={looks} aExpr={aExpr} aArm={aArm} tags={tags} />
          {bulbS > 0.01 && (
            <World cam={oCam}>
              <div style={{position: 'absolute', left: 0, top: Math.sin(t * 3) * 6}}>
                <Bulb t={t} t0={tHit} s={bulbS} />
              </div>
            </World>
          )}
          {bloomA > 0 && (
            <>
              <div style={{position: 'absolute', inset: 0, background: `radial-gradient(circle at ${bsx}px ${bsy}px, rgba(255,246,214,${0.95 * bloomA}) 0px, rgba(255,227,160,${0.8 * bloomA}) ${flashR * 0.3}px, rgba(242,179,94,${0.35 * bloomA}) ${flashR * 0.65}px, rgba(242,179,94,0) ${flashR}px)`}} />
              <div style={{position: 'absolute', inset: 0, background: '#FFE3A0', opacity: ramp(t, tKept - 0.14, tKept)}} />
            </>
          )}
        </AbsoluteFill>
      )}
      {/* frozen ER, whipped out to the left */}
      {showFrozen && (
        <AbsoluteFill style={{transform: `translateX(${-whip * 1920}px)`}}>
          <FrozenER t={t} />
        </AbsoluteFill>
      )}
      {/* whip streaks */}
      {whip > 0.02 && whip < 0.98 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: Math.sin(whip * Math.PI) * 0.6}}>
          {Array.from({length: 9}).map((_, i) => (
            <rect key={i} x={((i * 431) % 1600) - 200} y={80 + i * 112} width={600 + (i % 3) * 300} height={6 + (i % 2) * 6} rx={6} fill={i % 2 ? '#8E9BD8' : '#F2B35E'} opacity={0.5} />
          ))}
        </svg>
      )}
      {/* sage wipe into the next scene */}
      {sageP > 0 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
          <path d={`M${1920 - sageP * 2400} 0 Q${1920 - sageP * 2400 - 160} 540 ${1920 - sageP * 2400} 1080 L2400 1080 L2400 0 Z`} fill={P.sage} />
        </svg>
      )}
    </AbsoluteFill>
  );
};
