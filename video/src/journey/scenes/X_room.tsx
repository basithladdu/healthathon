/* Scene X helpers: the family's living room in daylight, and the illustrated props used on the feature wall. */
import React from 'react';
import {easeOutQuint, FONT_BODY, FONT_HEAD, P, pop, ramp} from '../kit';

/* ---------- room back wall (world coords, floor line at y=860) ---------- */
export const RoomWall: React.FC<{t: number; visitsAt: number; betweenAt: number}> = ({t, visitsAt, betweenAt}) => {
  const days = Array.from({length: 21});
  const visitDays = [2, 16];
  return (
    <>
      <div style={{position: 'absolute', left: -900, top: -700, width: 3800, height: 1560, background: P.sageLight}} />
      {/* wallpaper sprigs */}
      <svg style={{position: 'absolute', left: -900, top: -700, overflow: 'visible'}} width={3800} height={1300}>
        {Array.from({length: 32}).map((_, i) =>
          Array.from({length: 13}).map((__, j) => {
            const x = i * 120 + (j % 2) * 60;
            const y = j * 100 + 30;
            return <path key={i + '-' + j} d={`M${x} ${y} q8 -16 0 -30 q-8 14 0 30 Z`} fill="rgba(59,117,98,0.10)" />;
          }),
        )}
      </svg>
      {/* wainscot */}
      <div style={{position: 'absolute', left: -900, top: 610, width: 3800, height: 250, background: '#C9DDD1'}} />
      <div style={{position: 'absolute', left: -900, top: 600, width: 3800, height: 16, background: '#B6CFC1'}} />
      {Array.from({length: 30}).map((_, i) => (
        <div key={i} style={{position: 'absolute', left: -900 + i * 130 + 20, top: 645, width: 90, height: 180, borderRadius: 10, background: 'rgba(44,90,74,0.06)'}} />
      ))}
      {/* window with daylight */}
      <svg style={{position: 'absolute', left: 1330, top: 110, overflow: 'visible'}} width={380} height={440}>
        <defs>
          <linearGradient id="xsky" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#A9D2EC" />
            <stop offset="1" stopColor="#EAF4F2" />
          </linearGradient>
        </defs>
        <rect x={-18} y={-18} width={416} height={476} rx={14} fill="#F6EFE3" />
        <rect width={380} height={440} fill="url(#xsky)" />
        <circle cx={290} cy={90} r={44} fill="#FFF6DA" />
        <circle cx={290} cy={90} r={70 + Math.sin(t * 1.5) * 4} fill="rgba(255,246,218,0.35)" />
        {/* clouds drift */}
        {[0, 1].map((k) => {
          const cx = ((t * 14 + k * 210) % 520) - 80;
          return (
            <g key={k} fill="rgba(255,255,255,0.9)">
              <ellipse cx={cx} cy={150 + k * 70} rx={52} ry={18} />
              <ellipse cx={cx + 26} cy={140 + k * 70} rx={30} ry={20} />
            </g>
          );
        })}
        {/* trees and a rooftop */}
        <path d="M0 440 L0 330 Q40 270 90 320 Q120 250 170 310 Q210 280 230 330 L230 440 Z" fill={P.mint} />
        <path d="M200 440 L200 360 L300 300 L400 360 L400 440 Z" fill="#E7B98E" />
        <path d="M300 300 L400 360 L400 440 L330 440 Z" fill="rgba(140,80,40,0.15)" />
        <rect x={183} width={14} height={440} fill="#F6EFE3" />
        <rect y={213} width={380} height={14} fill="#F6EFE3" />
        {/* curtains */}
        <path d={`M-50 -36 L80 -36 Q${66 + Math.sin(t * 0.9) * 6} 220 92 470 L-50 470 Z`} fill={P.marigold} />
        <path d={`M430 -36 L300 -36 Q${314 - Math.sin(t * 0.9) * 6} 220 288 470 L430 470 Z`} fill={P.marigold} />
        <path d={`M30 -36 L80 -36 Q${66 + Math.sin(t * 0.9) * 6} 220 92 470 L50 470 Q40 220 30 -36 Z`} fill="rgba(176,122,46,0.35)" />
        <rect x={-74} y={-52} width={528} height={16} rx={8} fill={P.marigoldDeep} />
      </svg>
      {/* wall clock, daytime (callback to the 3 a.m. clock) */}
      <svg style={{position: 'absolute', left: 1130, top: 190, overflow: 'visible'}} width={1} height={1}>
        <circle r={62} fill={P.cream} />
        <circle r={62} fill="none" stroke={P.sage} strokeWidth={10} />
        {Array.from({length: 12}).map((_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return <circle key={i} cx={Math.sin(a) * 46} cy={-Math.cos(a) * 46} r={i % 3 ? 2.5 : 4.5} fill={P.ink} />;
        })}
        <path d={`M0 0 L${Math.sin((10.2 / 12) * Math.PI * 2) * 26} ${-Math.cos((10.2 / 12) * Math.PI * 2) * 26}`} stroke={P.ink} strokeWidth={6} strokeLinecap="round" />
        <path d={`M0 0 L${Math.sin(0.17 * Math.PI * 2) * 38} ${-Math.cos(0.17 * Math.PI * 2) * 38}`} stroke={P.ink} strokeWidth={4} strokeLinecap="round" />
        <path d={`M0 0 L${Math.sin(t * 1.05) * 42} ${-Math.cos(t * 1.05) * 42}`} stroke={P.rose} strokeWidth={2.5} strokeLinecap="round" />
        <circle r={5} fill={P.rose} />
      </svg>
      {/* wall calendar: two visit days circled, the days between get dots */}
      <div style={{position: 'absolute', left: 300, top: 150, width: 270, height: 250, borderRadius: 14, background: P.white, boxShadow: '0 10px 22px rgba(44,90,74,0.15)', transform: `rotate(-2deg)`}}>
        <div style={{height: 54, borderRadius: '14px 14px 0 0', background: P.rose, color: P.white, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 28, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>Visits</div>
        <svg style={{position: 'absolute', left: 18, top: 70, overflow: 'visible'}} width={234} height={160}>
          {days.map((_, i) => {
            const cx = (i % 7) * 36 + 10;
            const cy = Math.floor(i / 7) * 54 + 20;
            const isVisit = visitDays.includes(i);
            const vA = isVisit ? pop(t, visitsAt + (i === 16 ? 0.16 : 0), 13, 0.45) : 0;
            const between = i > 2 && i < 16;
            const bA = between ? easeOutQuint(ramp(t, betweenAt + (i - 3) * 0.045, betweenAt + (i - 3) * 0.045 + 0.3)) : 0;
            return (
              <g key={i}>
                <rect x={cx - 9} y={cy - 4} width={18} height={8} rx={4} fill="rgba(56,46,41,0.18)" />
                {bA > 0 && <circle cx={cx} cy={cy + 18} r={5 * bA} fill={P.marigold} />}
                {vA > 0 && <circle cx={cx} cy={cy} r={19 * vA} fill="none" stroke={P.sage} strokeWidth={5} />}
              </g>
            );
          })}
        </svg>
      </div>
      {/* framed photo of the family */}
      <svg style={{position: 'absolute', left: 120, top: 250, overflow: 'visible'}} width={1} height={1}>
        <rect x={-6} y={-6} width={132} height={162} rx={8} fill={P.marigoldDeep} />
        <rect x={6} y={6} width={108} height={138} rx={4} fill="#F6E3C6" />
        <circle cx={44} cy={70} r={20} fill={P.skin2} />
        <path d="M18 144 Q44 88 70 144 Z" fill="#9FB8C9" />
        <circle cx={80} cy={78} r={17} fill={P.skin1} />
        <path d="M58 144 Q80 98 102 144 Z" fill={P.rose} />
      </svg>
    </>
  );
};

/* ---------- floor + rug + sunbeam ---------- */
export const RoomFloor: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -900, top: 860, width: 3800, height: 700, background: '#DDBB92'}} />
    <div style={{position: 'absolute', left: -900, top: 860, width: 3800, height: 12, background: '#C9A47A'}} />
    {Array.from({length: 28}).map((_, i) => (
      <div key={i} style={{position: 'absolute', left: -900 + i * 150, top: 872, width: 4, height: 500, background: 'rgba(110,70,40,0.09)', transform: 'skewX(-30deg)'}} />
    ))}
    {/* sunbeam from the window, falling across floor */}
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', mixBlendMode: 'screen'}} width={1} height={1}>
      <path d="M1330 560 L1710 560 L1500 1060 L880 1060 Z" fill={`rgba(255,236,190,${0.28 + Math.sin(t * 0.7) * 0.04})`} />
    </svg>
    <svg style={{position: 'absolute', left: 420, top: 880, overflow: 'visible'}} width={900} height={150}>
      <ellipse cx={450} cy={70} rx={500} ry={66} fill={P.marigoldDeep} opacity={0.9} />
      <ellipse cx={450} cy={66} rx={460} ry={54} fill={P.marigold} />
      <ellipse cx={450} cy={64} rx={360} ry={38} fill="none" stroke={P.cream} strokeWidth={6} strokeDasharray="4 18" opacity={0.7} />
    </svg>
  </>
);

/* ---------- armchair (back drawn behind the patient, front drawn after) ---------- */
export const ChairBack: React.FC<{x: number}> = ({x}) => (
  <svg style={{position: 'absolute', left: x - 200, top: 470, overflow: 'visible'}} width={400} height={420}>
    <path d="M40 330 L40 70 Q40 10 110 10 L290 10 Q360 10 360 70 L360 330 Z" fill={P.plum} />
    <path d="M250 10 L290 10 Q360 10 360 70 L360 330 L270 330 Z" fill="rgba(40,20,40,0.18)" />
    <path d="M120 40 Q200 20 280 40" stroke="rgba(255,255,255,0.12)" strokeWidth={10} fill="none" strokeLinecap="round" />
  </svg>
);
export const ChairFront: React.FC<{x: number; t: number}> = ({x, t}) => (
  <svg style={{position: 'absolute', left: x - 200, top: 470, overflow: 'visible'}} width={400} height={420}>
    {/* knitted throw over the lap */}
    <path d={`M70 300 Q200 ${282 + Math.sin(t * 1.3) * 3} 330 300 L320 380 Q200 396 80 380 Z`} fill={P.marigold} />
    {[0, 1, 2, 3].map((i) => (
      <path key={i} d={`M${86 + i * 2} ${318 + i * 18} Q200 ${306 + i * 18} ${316 - i * 2} ${318 + i * 18}`} stroke="rgba(176,122,46,0.45)" strokeWidth={5} fill="none" strokeDasharray="10 8" />
    ))}
    <rect x={40} y={330} width={320} height={60} rx={22} fill="#7A4C74" />
    <rect x={-6} y={230} width={74} height={170} rx={34} fill="#9C6896" />
    <rect x={332} y={230} width={74} height={170} rx={34} fill="#9C6896" />
    <rect x={332} y={230} width={34} height={170} rx={17} fill="rgba(40,20,40,0.15)" />
    <rect x={40} y={388} width={18} height={34} rx={5} fill="#4E2F4A" />
    <rect x={342} y={388} width={18} height={34} rx={5} fill="#4E2F4A" />
  </svg>
);

/* ---------- side table with water, pill box, small plant ---------- */
export const SideTable: React.FC<{x: number; t: number}> = ({x, t}) => (
  <svg style={{position: 'absolute', left: x, top: 700, overflow: 'visible'}} width={1} height={1}>
    <ellipse cx={0} cy={168} rx={80} ry={12} fill="rgba(30,20,40,0.12)" />
    <rect x={-10} y={20} width={20} height={150} fill="#8A5A3C" />
    <path d="M-60 168 L0 140 L60 168" stroke="#8A5A3C" strokeWidth={12} fill="none" strokeLinecap="round" />
    <ellipse cx={0} cy={20} rx={100} ry={22} fill="#A87150" />
    <ellipse cx={0} cy={14} rx={100} ry={22} fill="#C08A63" />
    {/* glass of water */}
    <path d="M-62 -40 L-34 -40 L-38 12 L-58 12 Z" fill="rgba(170,215,235,0.75)" />
    <path d="M-60 -20 L-36 -20 L-38 12 L-58 12 Z" fill="rgba(127,179,217,0.7)" />
    {/* weekly pill box */}
    <rect x={-20} y={-6} width={84} height={22} rx={6} fill={P.rose} />
    {[0, 1, 2, 3].map((i) => (
      <rect key={i} x={-15 + i * 20} y={-2} width={16} height={8} rx={3} fill={i === 1 ? P.cream : P.roseLight} />
    ))}
    {/* tiny plant */}
    <g transform={`translate(40 -6) rotate(${Math.sin(t * 1.2) * 3})`}>
      <path d="M0 0 Q-24 -40 -10 -70 Q-6 -36 0 0 Z" fill="#4E8B72" />
      <path d="M0 0 Q26 -36 14 -64 Q6 -32 0 0 Z" fill="#2F6B57" />
    </g>
    <rect x={30} y={-14} width={22} height={18} rx={4} fill={P.sage} />
  </svg>
);

/* ---------- small phone held in the daughter's hand (centre at 0,0) ---------- */
export const HandPhone: React.FC<{t: number; glow: number; skin: string; rot?: number}> = ({t, glow, skin, rot = 0}) => (
  <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={1} height={1}>
    <g transform={`rotate(${rot})`}>
      {glow > 0 && <circle r={70 + glow * 30 + Math.sin(t * 6) * 4} fill={`rgba(255,236,190,${0.35 * glow})`} />}
      <rect x={-24} y={-46} width={48} height={92} rx={9} fill="#1B1A1F" />
      <rect x={-20} y={-41} width={40} height={82} rx={6} fill={P.cream} />
      <rect x={-20} y={-41} width={40} height={16} rx={6} fill={P.rose} />
      <rect x={-15} y={-18} width={30} height={8} rx={3} fill={P.sageLight} />
      <rect x={-15} y={-6} width={30} height={8} rx={3} fill={P.sageLight} />
      <rect x={-15} y={6} width={20} height={8} rx={3} fill={P.roseLight} />
      <circle cx={-26} cy={10} r={12} fill={skin} />
      <ellipse cx={-20} cy={-6} rx={6} ry={12} fill={skin} />
    </g>
  </svg>
);

/* ---------- note on the wall: "Practice data" ---------- */
export const PracticeNote: React.FC<{x: number; y: number; color?: string}> = ({x, y, color = 'rgba(56,46,41,0.5)'}) => (
  <div style={{position: 'absolute', left: x, top: y, fontFamily: FONT_BODY, fontSize: 20, color, whiteSpace: 'nowrap'}}>Practice data. All names are fictional.</div>
);
