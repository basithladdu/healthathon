/* H: the team's late-night workroom (used only by H_carer). */
import React from 'react';
import {Cam, P, World, Person} from '../kit';
import {CAST} from '../cast';

export const ENG_Y = 768;
export const ENGS = [
  {k: 'engB', x: 610},
  {k: 'engA', x: 960},
  {k: 'engC', x: 1310},
];
export const BULB = {x: 960, y: 330};
const TABLE_Y = 762;

const Laptop: React.FC<{x: number; glow: number}> = ({x, glow}) => (
  <g>
    <path d={`M${x - 112} ${TABLE_Y - 106} L${x + 112} ${TABLE_Y - 106} L${x + 118} ${TABLE_Y - 2} L${x - 118} ${TABLE_Y - 2} Z`} fill="#C7CEDB" />
    <path d={`M${x + 20} ${TABLE_Y - 106} L${x + 112} ${TABLE_Y - 106} L${x + 118} ${TABLE_Y - 2} L${x + 30} ${TABLE_Y - 2} Z`} fill="#B2BACA" />
    <circle cx={x} cy={TABLE_Y - 56} r={11} fill="#DDE3EC" />
    <rect x={x - 130} y={TABLE_Y - 6} width={260} height={10} rx={4} fill="#9AA3B4" />
    {/* screen light spilling over the lid edge */}
    <ellipse cx={x} cy={TABLE_Y - 108} rx={120} ry={10} fill="#BFE9FF" opacity={0.5 * glow} />
  </g>
);

export const Office: React.FC<{t: number; cam: Cam; looks: number; aExpr: 'neutral' | 'surprised' | 'happy' | 'tired' | 'think'; aArm: number}> = ({t, cam, looks, aExpr, aArm}) => {
  const ox = (cam.x - 960) * 0.6;
  const oy = (cam.y - 540) * 0.4;
  return (
    <>
      <div style={{position: 'absolute', inset: 0, background: '#151a3a'}} />
      <World cam={cam}>
        <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680}} viewBox="-500 -300 2920 1680">
          <defs>
            <linearGradient id="offWall" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#1A1F44" />
              <stop offset="1" stopColor="#262C5C" />
            </linearGradient>
            <linearGradient id="offSky" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#0B0F2A" />
              <stop offset="1" stopColor="#27306A" />
            </linearGradient>
            <clipPath id="offWin">
              <rect x={150} y={130} width={640} height={440} rx={6} />
            </clipPath>
            <radialGradient id="deskPool">
              <stop offset="0" stopColor={P.lampWarm} stopOpacity="0.5" />
              <stop offset="1" stopColor={P.lampWarm} stopOpacity="0" />
            </radialGradient>
          </defs>
          <rect x={-500} y={-300} width={2920} height={1680} fill="url(#offWall)" />
          <rect x={-500} y={-300} width={2920} height={340} fill="#141838" />
          {/* window with the city at night */}
          <rect x={130} y={110} width={680} height={480} rx={10} fill="#2D3470" />
          <g clipPath="url(#offWin)">
            <rect x={150} y={130} width={640} height={440} fill="url(#offSky)" />
            <g transform={`translate(${ox} ${oy})`}>
              {Array.from({length: 22}).map((_, i) => (
                <circle key={i} cx={100 + ((i * 113) % 820)} cy={150 + ((i * 47) % 200)} r={i % 4 ? 1.5 : 2.4} fill="#E6E9FF" opacity={0.4 + 0.5 * Math.abs(Math.sin(t * 1.2 + i))} />
              ))}
              <circle cx={640} cy={220} r={40} fill="#F1E6C8" />
              <circle cx={658} cy={208} r={36} fill="#151B44" />
            </g>
            <g transform={`translate(${ox * 0.5} ${oy * 0.5})`}>
              {[
                [120, 160],
                [190, 240],
                [270, 130],
                [330, 300],
                [420, 180],
                [490, 250],
                [570, 150],
                [630, 220],
                [710, 170],
                [770, 260],
              ].map(([bx, bh], i) => (
                <g key={i}>
                  <rect x={bx} y={570 - bh} width={64} height={bh} fill={i % 2 ? '#1B2150' : '#20275C'} />
                  {Array.from({length: Math.floor(bh / 30)}).map((_, j) =>
                    [0, 1].map((c) =>
                      (i + j + c) % 3 === 0 ? <rect key={j + '' + c} x={bx + 12 + c * 24} y={570 - bh + 14 + j * 30} width={12} height={12} fill={P.lampWarm} opacity={0.6} /> : null,
                    ),
                  )}
                </g>
              ))}
            </g>
          </g>
          <rect x={466} y={130} width={8} height={440} fill="#2D3470" />
          <rect x={150} y={344} width={640} height={8} fill="#2D3470" />
          <rect x={110} y={588} width={720} height={18} rx={6} fill="#353D7E" />
          {/* cork board with sticky notes */}
          <rect x={1150} y={150} width={480} height={300} rx={12} fill="#6E5640" />
          <rect x={1162} y={162} width={456} height={276} rx={8} fill="#8A6C4E" />
          {[
            [1190, 190, P.marigold, -4],
            [1300, 200, P.rose, 3],
            [1410, 186, P.mint, -2],
            [1510, 204, P.sky, 4],
            [1220, 312, P.mint, 3],
            [1340, 320, P.marigold, -3],
            [1460, 306, P.roseLight, 2],
          ].map(([nx, ny, c, r], i) => (
            <g key={i} transform={`rotate(${(r as number) + Math.sin(t * 1.4 + i) * 0.6} ${(nx as number) + 45} ${(ny as number) + 45})`}>
              <rect x={nx as number} y={ny as number} width={90} height={90} rx={4} fill={c as string} opacity={0.9} />
              <rect x={(nx as number) + 12} y={(ny as number) + 26} width={60} height={6} rx={3} fill="rgba(40,30,30,0.35)" />
              <rect x={(nx as number) + 12} y={(ny as number) + 42} width={44} height={6} rx={3} fill="rgba(40,30,30,0.35)" />
              <rect x={(nx as number) + 12} y={(ny as number) + 58} width={52} height={6} rx={3} fill="rgba(40,30,30,0.35)" />
            </g>
          ))}
          {/* small wall clock, past one */}
          <circle cx={1760} cy={230} r={54} fill="#2F3672" />
          <circle cx={1760} cy={230} r={44} fill="#E9E3D6" />
          <rect x={1757} y={196} width={6} height={36} rx={3} fill={P.ink} transform={`rotate(${240 + t * 0.1} 1760 230)`} />
          <rect x={1757} y={206} width={7} height={26} rx={3} fill={P.ink} transform={`rotate(${40 + t * 0.008} 1760 230)`} />
          <circle cx={1760} cy={230} r={5} fill={P.coral} />
          {/* plant */}
          <path d="M60 760 L150 760 L136 860 L74 860 Z" fill="#5C4A7A" />
          {[-40, -10, 20, 50].map((a, i) => (
            <ellipse key={i} cx={105 + Math.sin((a * Math.PI) / 180) * 70} cy={700 - Math.cos((a * Math.PI) / 180) * 50} rx={22} ry={60} fill={i % 2 ? '#3B7562' : '#2C5A4A'} transform={`rotate(${a + Math.sin(t + i) * 2} ${105 + Math.sin((a * Math.PI) / 180) * 70} ${700 - Math.cos((a * Math.PI) / 180) * 50})`} />
          ))}
          {/* floor */}
          <rect x={-500} y={860} width={2920} height={600} fill="#12163A" />
        </svg>
        {/* the team behind the table */}
        {ENGS.map((e, i) => {
          const isA = e.k === 'engA';
          const lookDir = isA ? 0 : e.x < 960 ? looks : -looks;
          return (
            <Person
              key={e.k}
              {...CAST[e.k]}
              t={t}
              x={e.x}
              y={ENG_Y}
              s={1}
              sitting
              expr={isA ? aExpr : looks > 0.5 ? 'surprised' : 'tired'}
              look={lookDir}
              headTilt={isA ? 0 : lookDir * 8}
              armR={isA ? aArm : 18}
              elbowR={isA ? 30 : -40}
              armL={18}
              elbowL={-40}
              bob={0.7}
              seed={i * 3 + 2}
            />
          );
        })}
        <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680}} viewBox="-500 -300 2920 1680">
          {/* table */}
          <rect x={240} y={TABLE_Y} width={1460} height={26} rx={8} fill="#4A4479" />
          <rect x={260} y={TABLE_Y + 24} width={1420} height={400} fill="#2A2650" />
          <rect x={260} y={TABLE_Y + 24} width={1420} height={10} fill="#221F44" />
          {ENGS.map((e) => (
            <Laptop key={e.k} x={e.x} glow={1} />
          ))}
          {/* mugs with steam */}
          {[790, 1140].map((mx, i) => (
            <g key={mx}>
              <rect x={mx - 20} y={TABLE_Y - 46} width={40} height={46} rx={8} fill={i ? P.rose : P.sage} />
              <path d={`M${mx + 20} ${TABLE_Y - 36} q16 4 0 22`} stroke={i ? P.rose : P.sage} strokeWidth={6} fill="none" />
              {[0, 1].map((k) => {
                const ph = (t * 0.6 + k * 0.5 + i * 0.3) % 1;
                return <path key={k} d={`M${mx - 6 + k * 12} ${TABLE_Y - 52 - ph * 50} q8 -12 0 -24`} stroke="#C9CDEB" strokeWidth={4} fill="none" strokeLinecap="round" opacity={(1 - ph) * 0.45} />;
              })}
            </g>
          ))}
          {/* desk lamp */}
          <rect x={1520} y={TABLE_Y - 14} width={90} height={14} rx={6} fill="#2F2B55" />
          <path d={`M1566 ${TABLE_Y - 12} L1600 560 L1500 520`} stroke="#3B3668" strokeWidth={12} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M1452 504 L1536 520 L1516 586 L1430 560 Z" fill={P.marigold} />
          <ellipse cx={1474} cy={576} rx={46} ry={10} fill="#FFE4A8" transform="rotate(16 1474 576)" />
          {/* papers */}
          <rect x={360} y={TABLE_Y - 6} width={120} height={8} rx={2} fill="#E6E2F2" transform={`rotate(-3 420 ${TABLE_Y})`} />
          <rect x={1420} y={TABLE_Y - 6} width={90} height={8} rx={2} fill="#E6E2F2" />
        </svg>
      </World>
      {/* light: desk lamp pool + screen glow on faces */}
      <World cam={cam} style={{pointerEvents: 'none'}}>
        <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680, mixBlendMode: 'screen'}} viewBox="-500 -300 2920 1680">
          <defs>
            <radialGradient id="faceGlow">
              <stop offset="0" stopColor="#8FD3FF" stopOpacity="0.35" />
              <stop offset="1" stopColor="#8FD3FF" stopOpacity="0" />
            </radialGradient>
            <linearGradient id="deskCone" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#FFD38C" stopOpacity="0.4" />
              <stop offset="1" stopColor="#FFD38C" stopOpacity="0" />
            </linearGradient>
          </defs>
          {ENGS.map((e) => (
            <ellipse key={e.k} cx={e.x} cy={600} rx={190} ry={170} fill="url(#faceGlow)" />
          ))}
          <path d={`M1440 560 L1530 584 L1640 ${TABLE_Y} L1180 ${TABLE_Y} Z`} fill="url(#deskCone)" />
          <ellipse cx={1420} cy={TABLE_Y} rx={260} ry={40} fill="url(#deskPool)" />
        </svg>
      </World>
    </>
  );
};

/** the idea bulb (local drawing centred at 0,0) */
export const Bulb: React.FC<{t: number; t0: number; s: number}> = ({t, t0, s}) => {
  const k = Math.max(0, t - t0);
  return (
    <svg width={360} height={360} viewBox="-180 -180 360 360" style={{position: 'absolute', left: BULB.x - 180, top: BULB.y - 180, overflow: 'visible', transform: `scale(${s * 0.62})`}}>
      <defs>
        <radialGradient id="bulbGlow">
          <stop offset="0" stopColor="#FFE7A8" stopOpacity="0.9" />
          <stop offset="0.4" stopColor={P.lampWarm} stopOpacity="0.35" />
          <stop offset="1" stopColor={P.lampWarm} stopOpacity="0" />
        </radialGradient>
      </defs>
      <circle cx={0} cy={-10} r={170} fill="url(#bulbGlow)" />
      <g transform={`rotate(${k * 30})`}>
        {Array.from({length: 8}).map((_, i) => (
          <rect key={i} x={-5} y={-150} width={10} height={26 + 8 * Math.sin(t * 8 + i)} rx={5} fill="#FFD27A" transform={`rotate(${i * 45 + 22.5})`} />
        ))}
      </g>
      <path d="M0 -84 C46 -84 72 -50 72 -16 C72 12 52 26 36 46 L36 56 L-36 56 L-36 46 C-52 26 -72 12 -72 -16 C-72 -50 -46 -84 0 -84 Z" fill="#FFE39A" />
      <ellipse cx={-26} cy={-40} rx={16} ry={24} fill="#FFF6D8" transform="rotate(30 -26 -40)" />
      <rect x={-34} y={56} width={68} height={14} rx={5} fill="#8C8FA8" />
      <rect x={-30} y={70} width={60} height={14} rx={5} fill="#6C6F88" />
      <rect x={-18} y={84} width={36} height={12} rx={6} fill="#8C8FA8" />
      <path d="M-14 20 Q0 -10 14 20" stroke={P.marigoldDeep} strokeWidth={6} fill="none" />
    </svg>
  );
};
