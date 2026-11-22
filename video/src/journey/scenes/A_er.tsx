/* A: emergency room, 3 a.m. Clock close-up, pull back to the room, the doctor arrives, the daughter stands, the conversation loops. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {P, useGT, at, camAt, World, Person, spring, pop, mv, ramp, clamp, lerp, easeInCubic} from '../kit';
import {CAST} from '../cast';
import {RoomBG, RoomLight, RoomFG, Bed, SeatedDaughter, CLOCK, STAND, DOC_SPOT, toScreen, Chair} from './A_room';

/* an empty speech bubble with ghost lines (the words are not the point, the repetition is) */
export const GhostBubble: React.FC<{x: number; y: number; w: number; s: number; tail: 'left' | 'right'; t: number; seed: number; color?: string; rot?: number}> = ({x, y, w, s, tail, t, seed, color = '#F3EEE6', rot = 0}) => {
  if (s <= 0.001) return null;
  const h = w * 0.5;
  const lines = [0.78, 0.58, 0.68];
  return (
    <div style={{position: 'absolute', left: x - w / 2, top: y - h / 2, width: w, height: h, transform: `rotate(${rot}deg) scale(${s})`, transformOrigin: tail === 'left' ? '15% 120%' : '85% 120%'}}>
      <div style={{position: 'absolute', inset: 0, borderRadius: h * 0.42, background: color, boxShadow: '0 14px 30px rgba(0,0,0,0.35)'}} />
      <svg width={w} height={h + 40} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}}>
        <path d={tail === 'left' ? `M${w * 0.16} ${h - 6} L${w * 0.08} ${h + 34} L${w * 0.34} ${h - 6} Z` : `M${w * 0.66} ${h - 6} L${w * 0.92} ${h + 34} L${w * 0.84} ${h - 6} Z`} fill={color} />
        {lines.map((L, i) => {
          const grow = clamp((t - seed * 0.13 - i * 0.12) * 3);
          return <rect key={i} x={w * 0.14} y={h * (0.26 + i * 0.2)} width={w * 0.72 * L * grow} height={h * 0.09} rx={h * 0.045} fill="#C9C0B4" />;
        })}
      </svg>
    </div>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  const tThree = at('s01', 'Three');
  const tEm = at('s01', 'emergency');
  const tDoc = at('s02', 'doctor');
  const tSick = at('s02', 'very');
  const tFam = at('s03', 'family');
  const tHard = at('s03', 'hardest');
  const tConv = at('s03', 'conversation');
  const tLives = at('s03', 'lives');
  const tAll = at('s03', 'all');
  const tOver = at('s03', 'over');
  const tAgain = at('s03', 'again');

  const cam = camAt(
    [
      {t: 0, x: CLOCK.x, y: CLOCK.y + 16, z: 2.5},
      {t: tThree, x: CLOCK.x, y: CLOCK.y + 14, z: 2.62},
      {t: tEm - 0.12, x: CLOCK.x + 6, y: CLOCK.y + 10, z: 2.7}, // anticipation push before the pull back
      {t: tEm + 1.15, x: 960, y: 560, z: 1.08},
      {t: tSick - 0.15, x: 950, y: 560, z: 1.1},
      {t: tSick + 0.9, x: 870, y: 560, z: 1.3},
      {t: tFam - 0.2, x: 860, y: 560, z: 1.32},
      {t: tFam + 0.7, x: 900, y: 540, z: 1.16},
      {t: tAll - 0.4, x: 920, y: 520, z: 1.18},
      {t: 10.6, x: 700, y: 500, z: 1.5},
    ],
    t,
    0.004,
  );

  // doctor slides in from the right with a spring and leans into the move
  const dP = (tt: number) => spring(tt, tDoc - 0.1, 7.5, 0.62);
  const docX = lerp(2260, DOC_SPOT.x, dP(t));
  const vel = (docX - lerp(2260, DOC_SPOT.x, dP(t - 0.05))) / 0.05;
  const docLean = clamp(-vel * 0.006, -6, 12);
  const walking = clamp(-vel / 600);
  const docY = DOC_SPOT.y - Math.abs(Math.sin(t * 15)) * 12 * walking;

  // daughter: seated, leans in, stands up on "family"
  const standT = tFam;
  const standing = t >= standT;
  const sq = standing ? 1 - (1 - spring(t, standT, 13, 0.4)) * 0.16 : 1;
  const preLean = mv(t, tFam - 0.35, tFam) * 10;

  // bubbles: 4 empty bubbles, first stacked between them, then looping in an orbit
  const C = {x: 935, y: 330};
  const loopP = mv(t, tAll - 0.05, tAll + 0.6);
  const spin = Math.max(0, t - tAll) * (1.2 + 2.2 * ramp(t, tOver, tAgain + 0.4));
  const bubbles = [
    {t0: tHard, side: 'right' as const, sx: 1170, sy: 360},
    {t0: tConv, side: 'left' as const, sx: 680, sy: 380},
    {t0: tLives - 0.12, side: 'right' as const, sx: 1150, sy: 230},
    {t0: tLives + 0.4, side: 'left' as const, sx: 700, sy: 250},
  ];
  const docTalk = (t > tHard && t < tHard + 0.9) || (t > tLives - 0.1 && t < tLives + 0.4);
  const dauTalk = (t > tConv && t < tConv + 0.9) || (t > tLives + 0.4 && t < tAll);

  // circular arrow
  const arrowS = pop(t, tOver - 0.05, 12, 0.5);
  const arrowRot = Math.max(0, t - tOver) * (240 + 480 * ramp(t, tAgain, tAgain + 0.5));

  // iris to cream
  const [isx, isy] = toScreen(cam, C.x, C.y);
  const irisR = easeInCubic(ramp(t, 10.18, 10.6)) * 2400;
  const seedDot = pop(t, 10.05, 16, 0.5) * 14;
  const rr = Math.max(seedDot, irisR);

  const bpm = 104 + 18 * mv(t, tSick, tSick + 1);
  const signP = ramp(t, tEm - 0.05, tEm + 0.35);

  return (
    <AbsoluteFill style={{background: P.night0, overflow: 'hidden'}}>
      <RoomBG t={t} cam={cam} sign={signP} bpm={bpm} chair={false} />
      <World cam={cam}>
        <Chair />
        {standing ? (
          <Person {...CAST.daughter} t={t} x={STAND.x} y={STAND.y} s={1} expr="worried" look={1} squashY={sq} lean={lerp(4, 0, clamp((t - standT) * 2))} armL={-10} elbowL={-55} armR={-16} elbowR={-50} talk={dauTalk} headTilt={t > tAll ? 4 : 0} />
        ) : (
          <SeatedDaughter t={t} expr="worried" lean={preLean} look={1} headTilt={-4} />
        )}
        <Bed t={t} expr="tired" look={t > tFam ? -0.6 : 0.6} />
        <Person {...CAST.erDoctor} t={t} x={docX} y={docY} s={1} flip expr={t > tHard ? 'neutral' : 'think'} look={0.4} lean={docLean} armR={t > tHard ? 22 : 8} elbowR={t > tHard ? 40 : 10} armL={6} talk={docTalk} />
      </World>
      <RoomLight t={t} cam={cam} bpm={bpm} />
      <World cam={cam}>
        {bubbles.map((b, i) => {
          const s0 = pop(t, b.t0, 12, 0.45);
          const ang = (i / bubbles.length) * Math.PI * 2 + spin;
          const ox = C.x + Math.cos(ang) * 270;
          const oy = C.y + Math.sin(ang) * 120;
          const x = lerp(b.sx, ox, loopP);
          const y = lerp(b.sy, oy, loopP);
          const s = s0 * lerp(1, 0.66 + 0.12 * Math.sin(ang), loopP);
          return <GhostBubble key={i} x={x} y={y} w={250} s={s} tail={b.side} t={t - b.t0} seed={i} rot={Math.sin(t * 2 + i) * 2} />;
        })}
        {arrowS > 0.01 && (
          <svg style={{position: 'absolute', left: C.x - 90, top: C.y - 90, width: 180, height: 180, overflow: 'visible', transform: `scale(${arrowS * 1.45}) rotate(${arrowRot}deg)`}} viewBox="-90 -90 180 180">
            <circle cx={0} cy={0} r={88} fill="rgba(14,26,43,0.55)" />
            <path d="M 0 -62 A 62 62 0 1 1 -58 -22" stroke={P.lampWarm} strokeWidth={16} fill="none" strokeLinecap="round" />
            <path d="M -46 -54 L -40 -12 L -78 -28 Z" fill={P.lampWarm} />
          </svg>
        )}
      </World>
      <RoomFG t={t} cam={cam} />
      {rr > 0 && <div style={{position: 'absolute', left: isx - rr, top: isy - rr, width: 2 * rr, height: 2 * rr, borderRadius: '50%', background: P.cream}} />}
    </AbsoluteFill>
  );
};
