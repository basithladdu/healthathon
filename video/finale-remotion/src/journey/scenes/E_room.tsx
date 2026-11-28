/* Scene E room pieces: sage consultation room, wide enough for the camera to travel to the document. */
import React from 'react';
import {P} from '../kit';

export const RoomWindow: React.FC<{x: number; y: number; w: number; h: number; t: number}> = ({x, y, w, h, t}) => {
  const sway = Math.sin(t * 0.8 + x) * 4;
  return (
    <svg style={{position: 'absolute', left: x, top: y, overflow: 'visible'}} width={w} height={h}>
      <defs>
        <linearGradient id={'esky' + x} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#F9E6C6" />
          <stop offset="1" stopColor="#DCEBDF" />
        </linearGradient>
      </defs>
      <rect x={-16} y={-16} width={w + 32} height={h + 32} rx={10} fill={P.cream} />
      <rect x={0} y={0} width={w} height={h} fill={`url(#esky${x})`} />
      <circle cx={w * 0.7} cy={h * 0.3} r={w * 0.11} fill="#FFF4DD" />
      <ellipse cx={w * 0.25} cy={h * 0.86} rx={w * 0.4} ry={h * 0.2} fill="#A9D2BC" />
      <ellipse cx={w * 0.85} cy={h * 0.9} rx={w * 0.35} ry={h * 0.18} fill="#8CC2A7" />
      <rect x={w / 2 - 6} y={0} width={12} height={h} fill={P.cream} />
      <rect x={0} y={h * 0.48} width={w} height={12} fill={P.cream} />
      <path d={`M-40 -30 L${w * 0.22} -30 Q${w * 0.18 + sway} ${h * 0.5} ${w * 0.24} ${h + 30} L-40 ${h + 30} Z`} fill={P.mint} />
      <path d={`M${w + 40} -30 L${w * 0.78} -30 Q${w * 0.82 - sway} ${h * 0.5} ${w * 0.76} ${h + 30} L${w + 40} ${h + 30} Z`} fill={P.mint} />
      <rect x={-60} y={-44} width={w + 120} height={14} rx={7} fill="#244C3F" />
    </svg>
  );
};

const Lamp: React.FC<{x: number; t: number}> = ({x, t}) => (
  <svg style={{position: 'absolute', left: x, top: 140, overflow: 'visible'}} width={10} height={10}>
    <rect x={-6} y={-200} width={12} height={260} fill="#244C3F" />
    <path d="M-50 120 L-26 56 L26 56 L50 120 Z" fill={P.marigold} />
    <path d="M10 56 L26 56 L50 120 L22 120 Z" fill="rgba(120,70,20,0.18)" />
    <ellipse cx={0} cy={128} rx={150 + Math.sin(t * 1.3) * 6} ry={40} fill="rgba(242,179,94,0.14)" />
  </svg>
);

const Frame: React.FC<{x: number; y: number; w: number; h: number; c: string}> = ({x, y, w, h, c}) => (
  <svg style={{position: 'absolute', left: x, top: y, overflow: 'visible'}} width={w} height={h}>
    <rect x={0} y={0} width={w} height={h} rx={8} fill={P.cream} />
    <rect x={14} y={14} width={w - 28} height={h - 28} rx={4} fill={c} />
    <circle cx={w * 0.35} cy={h * 0.4} r={Math.min(w, h) * 0.14} fill="rgba(255,248,238,0.6)" />
    <path d={`M14 ${h - 14} L${w * 0.45} ${h * 0.5} L${w - 14} ${h - 14} Z`} fill="rgba(30,60,50,0.25)" />
  </svg>
);

export const Wall: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -1400, top: -900, width: 7400, height: 1860, background: P.sage}} />
    <div style={{position: 'absolute', left: -1400, top: 650, width: 7400, height: 300, background: P.band}} />
    <div style={{position: 'absolute', left: -1400, top: 640, width: 7400, height: 16, background: '#4B8672'}} />
    <RoomWindow x={70} y={130} w={330} h={430} t={t} />
    <Lamp x={2380} t={t} />
    <Frame x={2860} y={190} w={200} h={260} c={P.marigold} />
    <Frame x={3090} y={250} w={150} h={150} c={P.plum} />
    <RoomWindow x={4180} y={110} w={420} h={480} t={t} />
  </>
);

export const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -1400, top: 880, width: 7400, height: 700, background: '#E8D6BD'}} />
    <div style={{position: 'absolute', left: -1400, top: 880, width: 7400, height: 14, background: '#D7C2A4'}} />
    {Array.from({length: 44}).map((_, i) => (
      <div key={i} style={{position: 'absolute', left: -1400 + i * 180, top: 900, width: 4, height: 400, background: 'rgba(120,90,60,0.08)', transform: 'skewX(-30deg)'}} />
    ))}
    <div style={{position: 'absolute', left: 260, top: 900, width: 1040, height: 90, borderRadius: '50%', background: '#D88A6E'}} />
    <div style={{position: 'absolute', left: 310, top: 912, width: 940, height: 66, borderRadius: '50%', background: '#E49D80'}} />
  </>
);

export const ChairBack: React.FC = () => (
  <svg style={{position: 'absolute', left: 600, top: 500, overflow: 'visible'}} width={320} height={400}>
    <rect x={10} y={20} width={300} height={330} rx={70} fill={P.plum} />
    <rect x={190} y={20} width={120} height={330} rx={60} fill="rgba(30,20,40,0.14)" />
  </svg>
);
export const ChairFront: React.FC = () => (
  <svg style={{position: 'absolute', left: 600, top: 500, overflow: 'visible'}} width={320} height={400}>
    <path d="M60 268 Q160 238 262 268 L270 340 L52 340 Z" fill={P.marigold} />
    <path d="M60 300 L262 300" stroke="rgba(255,255,255,0.35)" strokeWidth={6} />
    <rect x={20} y={318} width={280} height={62} rx={22} fill="#7A4C75" />
    <rect x={-14} y={200} width={74} height={180} rx={34} fill="#9E6A98" />
    <rect x={260} y={200} width={74} height={180} rx={34} fill="#9E6A98" />
    <rect x={30} y={374} width={18} height={40} rx={6} fill="#5B3956" />
    <rect x={272} y={374} width={18} height={40} rx={6} fill="#5B3956" />
  </svg>
);
