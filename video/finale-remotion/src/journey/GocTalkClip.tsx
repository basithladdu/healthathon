/* 10-second silent clip for the deck: the goals-of-care talk in scene E's room (doctor, patient, family),
   with a waveform over whoever is speaking. Voices are added live by the team; this file has no audio. */
import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {clamp, FONT_HEAD, P, Person, pop, ramp, World} from './kit';
import {CAST} from './cast';
import {ChairBack, ChairFront, Floor, Wall} from './scenes/E_room';
import {Motes, Plant} from './scenes/E_parts';

export const GOC_TALK_SECONDS = 10;
const FLOOR_Y = 935;
type Who = 'doctor' | 'patient' | 'family';
const TURNS: {who: Who; from: number; to: number}[] = [
  {who: 'doctor', from: 0.6, to: 2.9},
  {who: 'patient', from: 3.2, to: 5.4},
  {who: 'family', from: 5.7, to: 7.6},
  {who: 'doctor', from: 7.9, to: 9.7},
];
// head positions in the room, as in scene E (section A)
const SPK: Record<Who, {label: string; c: string; x: number; y: number}> = {
  family: {label: 'Family', c: P.rose, x: 470, y: 330},
  patient: {label: 'Patient', c: P.indigo, x: 760, y: 420},
  doctor: {label: 'Doctor', c: P.sageDark, x: 1130, y: 330},
};
const talking = (who: Who, t: number) => TURNS.some((r) => r.who === who && t >= r.from && t < r.to);

const Wave: React.FC<{who: Who; t: number}> = ({who, t}) => {
  const s = SPK[who];
  const turn = TURNS.filter((r) => r.who === who).find((r) => t >= r.from - 0.2 && t < r.to + 0.35);
  const on = turn ? pop(t, turn.from - 0.15, 13, 0.5) * (1 - ramp(t, turn.to, turn.to + 0.3)) : 0;
  return (
    <>
      {/* name chip, always there */}
      <div style={{position: 'absolute', left: s.x, top: s.y - 78, transform: 'translate(-50%,-50%)', padding: '6px 22px', borderRadius: 999, background: s.c, color: P.white, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 30, whiteSpace: 'nowrap', opacity: 0.55 + 0.45 * clamp(on * 1.5), boxShadow: '0 6px 14px rgba(10,30,20,0.22)'}}>
        {s.label}
      </div>
      {/* waveform bubble over the speaker */}
      {on > 0.01 && (
        <div style={{position: 'absolute', left: s.x, top: s.y - 158, transform: `translate(-50%,-50%) scale(${on})`, transformOrigin: '50% 100%', padding: '14px 22px', borderRadius: 999, background: P.cream, border: `4px solid ${s.c}`, display: 'flex', alignItems: 'center', gap: 6, boxShadow: '0 10px 24px rgba(10,30,20,0.25)'}}>
          {Array.from({length: 18}).map((_, k) => (
            <div key={k} style={{width: 8, borderRadius: 4, background: s.c, height: 10 + 44 * Math.abs(Math.sin(t * 7 + k * 0.75)) * (0.35 + 0.65 * Math.abs(Math.sin(k * 0.41 + t * 1.7))) * Math.sin(((k + 1) / 19) * Math.PI)}} />
          ))}
        </div>
      )}
    </>
  );
};

export const GocTalkClip: React.FC = () => {
  const t = useCurrentFrame() / 30;
  const cam = {x: 880, y: 520, z: 1.0};
  const fadeIn = ramp(t, 0, 0.35);
  return (
    <AbsoluteFill style={{background: P.sage, overflow: 'hidden', opacity: fadeIn}}>
      <World cam={cam} depth={0.85}>
        <Wall t={t} />
      </World>
      <World cam={cam} depth={0.92}>
        <Motes t={t} n={24} x0={-100} w={2200} h={900} color="rgba(255,248,238,0.35)" seed={3} />
      </World>
      <World cam={cam}>
        <Floor />
        <Plant x={130} y={920} s={1.1} t={t} />
        <ChairBack />
        <Person {...CAST.patient} t={t} x={760} y={820} s={1.15} sitting expr="happy" look={0.5} headTilt={3} talk={talking('patient', t)} />
        <ChairFront />
        <Person {...CAST.daughter} t={t} x={470} y={FLOOR_Y} s={1.2} look={0.6} expr="happy" headTilt={4} armR={28} elbowR={-30} talk={talking('family', t)} />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={1130}
          y={FLOOR_Y}
          s={1.2}
          flip
          look={0.6}
          expr="happy"
          headTilt={-2}
          talk={talking('doctor', t)}
          armR={48}
          elbowR={70}
          hold={
            <g transform="rotate(-10)">
              <rect x={-18} y={-58} width={36} height={70} rx={7} fill="#1B1A1F" />
              <rect x={-14} y={-54} width={28} height={62} rx={5} fill={P.cream} />
              <circle cx={0} cy={-14} r={7} fill={P.rose} opacity={0.6 + 0.4 * Math.sin(t * 8)} />
            </g>
          }
        />
        {(['family', 'patient', 'doctor'] as Who[]).map((w) => <Wave key={w} who={w} t={t} />)}
      </World>
      <div style={{position: 'absolute', left: 60, top: 48, display: 'flex', alignItems: 'center', gap: 14, padding: '10px 24px', borderRadius: 40, background: P.ink}}>
        <div style={{width: 20, height: 20, borderRadius: '50%', background: '#E5534B', opacity: 0.6 + 0.4 * Math.abs(Math.sin(t * 3))}} />
        <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 28, color: P.white, letterSpacing: 2}}>REC · goals-of-care talk</div>
      </div>
    </AbsoluteFill>
  );
};
