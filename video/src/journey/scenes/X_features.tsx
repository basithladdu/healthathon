/* Scene X 83.21-104.41: at home, between visits. The living room, then the phone grows into a feature wall that trucks
   through four stations: symptoms + body map + trend, prescription snap + reminders, ask your reports, centres map. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {at, camAt, clamp, easeInCubic, easeInOutCubic, easeOutCubic, easeOutQuint, FONT_BODY, FONT_HEAD, lerp, mv, P, Person, pop, ramp, Rise, spring, useGT, World, zoomLerp} from '../kit';
import {CAST} from '../cast';
import {FgLeaves, Motes, PhoneSeq, Plant, toScreen} from './E_parts';
import {ChairBack, ChairFront, HandPhone, PracticeNote, RoomFloor, RoomWall, SideTable} from './X_room';
import {IndiaMap, makePins} from './X_map';

const START = 83.21;
const END = 104.41;

const T = {
  home: at('s14a', 'home'),
  between: at('s14a', 'between'),
  visits: at('s14a', 'visits'),
  families: at('s14a', 'families'),
  real: at('s14a', 'real'),
  help: at('s14a', 'help'),
  symptoms: at('s14b', 'Symptoms'),
  pain: at('s14b', 'pain'),
  marked: at('s14b', 'marked'),
  body: at('s14b', 'body'),
  map: at('s14b', 'map'),
  trends: at('s14b', 'trends'),
  doctor: at('s14b', 'doctor'),
  snap: at('s14c', 'Snap'),
  prescription: at('s14c', 'prescription'),
  every: at('s14c', 'every'),
  dose: at('s14c', 'dose'),
  reminder: at('s14c', 'reminder'),
  ask: at('s14d', 'Ask'),
  reports: at('s14d', 'reports'),
  question: at('s14d', 'question'),
  line: at('s14d', 'line'),
  sixteen: at('s14e', 'sixteen'),
  centres: at('s14e', 'centres'),
  everyState: at('s14e', 'every'),
  state: at('s14e', 'state'),
  morphine: at('s14e', 'morphine'),
};

/* ---------- the room (part 1) ---------- */
const PAT_X = 760;
const DAU = {x: 1075, y: 935, s: 1.15};
/** right-hand position of a flipped Person (mirrors kit's arm maths) */
const handOf = (x: number, y: number, s: number, a: number, el: number) => {
  const a1 = (a * Math.PI) / 180;
  const ex = 52 + Math.sin(a1) * 62;
  const ey = -230 + Math.cos(a1) * 62;
  const a2 = a1 + (el * Math.PI) / 180;
  const hx = ex + Math.sin(a2) * 58;
  const hy = ey + Math.cos(a2) * 58;
  return [x - hx * s, y + hy * s] as [number, number];
};

/* ---------- the feature wall (part 2) ---------- */
const OX = [0, 1900, 3800, 5700];
const PH = {x: 600, y: 470, h: 800};
const TRUCK = [
  [91.18, 91.7],
  [94.62, 95.12],
  [97.93, 98.42],
];
const DIVE0 = 86.72;
const DIVE1 = 87.42;
const GROW0 = 87.12;
const GROW1 = 87.72;

const PINS = makePins(150, T.sixteen - 0.2, T.centres + 0.15);
const MAP = {x: OX[3] + 1010, y: 130, H: 720};
const nearestPin = (nx: number, ny: number) => {
  let best = 0;
  let bd = 9;
  PINS.forEach((p) => {
    const d = (p.x - nx) ** 2 + (p.y - ny) ** 2;
    if (d < bd) {
      bd = d;
      best = p.k;
    }
  });
  return best;
};
const MORPH_SPOTS: [number, number][] = [[0.66, 0.5], [0.3, 0.9], [0.41, 0.8], [0.35, 0.33], [0.2, 0.6], [0.47, 0.6], [0.84, 0.41]];
const MORPH = MORPH_SPOTS.map(([a, b], i) => ({i: nearestPin(a, b), t: T.morphine - 0.05 + i * 0.07}));
const MAIN_PIN = PINS.find((p) => p.k === MORPH[0].i)!;

const swing = (t: number, t0: number, amp: number) => {
  const x = t - t0;
  if (x < 0 || x > 2) return 0;
  return Math.sin(x * 9) * Math.exp(-x * 3.6) * amp;
};

const Chip: React.FC<{x: number; y: number; a: number; dot?: string; children: React.ReactNode; bg?: string; color?: string; size?: number; origin?: string; right?: boolean}> = ({x, y, a, dot, children, bg = P.white, color = P.ink, size = 26, origin, right = false}) =>
  a <= 0.01 ? null : (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${right ? -100 : 0}%, -50%) scale(${a})`, transformOrigin: origin ?? (right ? 'right center' : 'left center'), display: 'flex', alignItems: 'center', gap: 10, padding: `${size * 0.36}px ${size * 0.8}px`, borderRadius: 999, background: bg, color, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: size, whiteSpace: 'nowrap', boxShadow: '0 8px 20px rgba(44,60,50,0.16)'}}>
      {dot && <div style={{width: size * 0.5, height: size * 0.5, borderRadius: '50%', background: dot}} />}
      {children}
    </div>
  );

/** illustrated body outline, front view; top-centre at (0,0) */
const BodyShape: React.FC<{c: string}> = ({c}) => (
  <g>
    <g stroke={c} strokeLinecap="round" strokeLinejoin="round" fill="none">
      <path d="M-96 166 Q-122 220 -128 300 L-136 420" strokeWidth={42} />
      <path d="M96 166 Q122 220 128 300 L136 420" strokeWidth={42} />
      <path d="M-42 370 L-48 636" strokeWidth={58} />
      <path d="M42 370 L48 636" strokeWidth={58} />
    </g>
    <rect x={-18} y={96} width={36} height={46} rx={12} fill={c} />
    <path d="M-60 132 L60 132 Q104 136 104 180 L92 300 Q90 360 84 392 Q0 410 -84 392 Q-90 360 -92 300 L-104 180 Q-104 136 -60 132 Z" fill={c} />
    <circle cx={0} cy={56} r={56} fill={c} />
  </g>
);
const BodyFig: React.FC = () => (
  <g>
    <defs>
      <clipPath id="xbodyshade">
        <rect x={34} y={-20} width={200} height={700} />
      </clipPath>
    </defs>
    <BodyShape c="#FFFBF3" />
    <g clipPath="url(#xbodyshade)">
      <BodyShape c="#EFE5D6" />
    </g>
    <path d="M-80 250 L80 250 M-84 318 L84 318" stroke="rgba(59,117,98,0.16)" strokeWidth={3} strokeDasharray="8 10" />
  </g>
);

const PainSpot: React.FC<{t: number; t0: number; x: number; y: number; r?: number}> = ({t, t0, x, y, r = 30}) => {
  const a = pop(t, t0, 13, 0.4);
  if (a <= 0.01) return null;
  const ph = ((t - t0) % 1.1) / 1.1;
  return (
    <g transform={`translate(${x} ${y})`}>
      <circle r={r * (1 + ph * 1.6)} fill="none" stroke={P.rose} strokeWidth={5} opacity={0.6 * (1 - ph)} />
      <circle r={r * a * (1 + Math.sin(t * 7) * 0.06)} fill={P.rose} opacity={0.85} />
      <circle r={r * 0.42 * a} fill={P.roseLight} />
    </g>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();

  /* ================= part 1: the living room ================= */
  const raise = mv(t, T.families - 0.25, T.families + 0.35, easeInOutCubic);
  const armA = lerp(22, 62, raise);
  const armE = lerp(80, 58, raise);
  const handW = handOf(DAU.x, DAU.y, DAU.s, armA, armE);
  const phoneW: [number, number] = [handW[0] - 4, handW[1] - 30];
  const roomCam = camAt(
    [
      {t: START, x: 860, y: 520, z: 1.22},
      {t: T.home - 0.1, x: 860, y: 520, z: 1.2},
      {t: T.between - 0.05, x: 900, y: 540, z: 1.0},
      {t: T.visits + 0.45, x: 720, y: 470, z: 1.12},
      {t: T.families - 0.1, x: 760, y: 480, z: 1.1},
      {t: T.real, x: 860, y: 560, z: 1.32},
      {t: DIVE0, x: 860, y: 580, z: 1.36},
      {t: DIVE1, x: phoneW[0], y: phoneW[1], z: 5.2},
    ],
    t,
    0.004,
  );
  const roomOn = t < GROW1 + 0.05;
  const phGlow = easeOutQuint(ramp(t, T.real - 0.1, T.help + 0.2)) * (1 - ramp(t, DIVE0, DIVE0 + 0.3));
  const patHappy = t > T.families + 0.3;

  /* ================= part 2: the feature wall ================= */
  const stage = camAt(
    [
      {t: GROW0, x: 960, y: 540, z: 1.04},
      {t: T.body - 0.1, x: 960, y: 540, z: 1.0},
      {t: T.map + 0.2, x: 1040, y: 520, z: 1.06},
      {t: TRUCK[0][0] - 0.12, x: 1030, y: 524, z: 1.05},
      {t: TRUCK[0][0], x: 1000, y: 530, z: 1.04},
      {t: TRUCK[0][1], x: OX[1] + 960, y: 540, z: 1.0},
      {t: T.dose, x: OX[1] + 980, y: 540, z: 1.03},
      {t: TRUCK[1][0], x: OX[1] + 960, y: 540, z: 1.02},
      {t: TRUCK[1][1], x: OX[2] + 960, y: 540, z: 1.0},
      {t: T.line - 0.2, x: OX[2] + 990, y: 530, z: 1.03},
      {t: T.line + 0.4, x: OX[2] + 1010, y: 520, z: 1.06},
      {t: TRUCK[2][0], x: OX[2] + 990, y: 530, z: 1.04},
      {t: TRUCK[2][1], x: OX[3] + 960, y: 540, z: 1.0},
      {t: T.state, x: OX[3] + 970, y: 540, z: 1.02},
      {t: T.morphine - 0.2, x: OX[3] + 990, y: 530, z: 1.03},
      {t: T.morphine + 0.4, x: OX[3] + 1010, y: 525, z: 1.06},
    ],
    t,
    0.002,
  );
  const stageOn = t > GROW0 - 0.02;
  const reveal = easeInCubic(ramp(t, GROW0, GROW1 - 0.1));
  const revealR = 10 + reveal * 2300;

  // phone grows from the hand phone into the big phone
  const g = easeInOutCubic(ramp(t, GROW0, GROW1));
  const gs = spring(t, GROW0 + 0.15, 11, 0.5);
  const grow = t < GROW0 ? 0 : clamp(lerp(g, gs, 0.5), 0, 1.2);
  const phScale = zoomLerp(0.32, 1, clamp(grow, 0, 1)) * (grow > 1 ? 1 + (grow - 1) * 0.3 : 1);
  const phDX = lerp(960 - PH.x, 0, clamp(grow));
  const phDY = lerp(540 - PH.y, 0, clamp(grow));
  // inertia swing on every truck (the phone lags the camera)
  const rot = TRUCK.reduce((r, [a]) => r + swing(t, a + 0.2, -5) + swing(t, a + 0.55, 3), 0) + Math.sin(t * 0.8) * 0.8 + (1 - clamp(grow)) * -8;
  const truckLag = TRUCK.reduce((r, [a, b]) => r + Math.sin(ramp(t, a, b + 0.2) * Math.PI) * 26, 0);
  const handIn = easeOutQuint(ramp(t, GROW1 - 0.25, GROW1 + 0.4));

  // tiles arriving in the phone make it bump
  const tileT0 = T.prescription + 0.18;
  const bump = [0, 1, 2].reduce((r, i) => r + Math.max(0, Math.sin(clamp((t - (tileT0 + 0.55 + i * 0.2)) / 0.22) * Math.PI)) * 0.025, 0);

  const segs = [
    {clip: 8, at: 87.25, clipFrom: 4.3},
    {clip: 10, at: TRUCK[0][0] + 0.3, clipFrom: 0.3},
    {clip: 11, at: TRUCK[1][0] + 0.25, clipFrom: 4.3},
    {clip: 12, at: TRUCK[2][0] + 0.3, clipFrom: 0.5},
  ];

  // station 1 (prescription) geometry
  const RX = {x: OX[1] + 1350, y: 470};
  const rows = [
    {name: 'Morphine 5 mg', c: P.rose},
    {name: 'Ondansetron 4 mg', c: P.sage},
    {name: 'Dexamethasone 4 mg', c: P.marigold},
  ];
  const rowW = (i: number): [number, number] => [RX.x - 80, RX.y - 113 + i * 92];
  const snapA = ramp(t, T.snap - 0.4, T.snap);
  const flash = t >= T.snap ? Math.exp(-(t - T.snap) * 9) : 0;
  const shutter = t > T.snap ? spring(t, T.snap, 16, 0.35) : 1;

  // station 2 (report)
  const RP = {x: OX[2] + 1510, y: 470};
  const hlRow = 3;
  const rowY2 = (i: number) => RP.y - 170 + i * 58;
  const hl = easeOutCubic(ramp(t, T.line - 0.05, T.line + 0.35));
  const conn = easeInOutCubic(ramp(t, T.line + 0.1, T.line + 0.6));

  // station 3 (map) counter
  const cp = easeOutCubic(ramp(t, T.sixteen - 0.15, T.centres + 0.2));
  const countStr = Math.round(1600 * cp).toLocaleString('en-US') + (cp >= 0.999 ? '+' : '');

  // exit wipe toward scene F (warm cream), from the main morphine pin
  const pinS = toScreen(stage, MAP.x + MAIN_PIN.x * MAP.H, MAP.y + MAIN_PIN.y * MAP.H - 14);
  const exit = easeInCubic(ramp(t, END - 0.45, END - 0.03));
  const irisIn = mv(t, START, START + 0.55);
  const irisR = 20 + irisIn * 1400;

  const sx = (wx: number, wy: number) => toScreen(stage, wx, wy);

  return (
    <AbsoluteFill style={{background: P.cream, overflow: 'hidden'}}>
      {/* ================= ROOM ================= */}
      {roomOn && (
        <AbsoluteFill>
          <World cam={roomCam} depth={0.88}>
            <RoomWall t={t} visitsAt={T.visits} betweenAt={T.between} />
          </World>
          <World cam={roomCam}>
            <RoomFloor t={t} />
            <Plant x={1830} y={900} s={1.3} t={t} pot={P.rose} />
            <SideTable x={470} t={t} />
            <ChairBack x={PAT_X} />
            <Person {...CAST.patient} t={t} x={PAT_X} y={808} s={1.12} sitting look={patHappy ? 0.7 : 0.3} expr={patHappy ? 'happy' : 'neutral'} headTilt={patHappy ? 6 : 0} armR={patHappy ? 30 : 8} elbowR={patHappy ? 70 : 10} bob={0.7} />
            <ChairFront x={PAT_X} t={t} />
            <Person
              {...CAST.daughter}
              t={t}
              x={DAU.x}
              y={DAU.y}
              s={DAU.s}
              flip
              look={0.7}
              lean={-3 * raise}
              bob={1 - raise * 0.9}
              expr={t > T.real ? 'happy' : 'neutral'}
              talk={t > T.families && t < T.help + 0.3}
              armR={armA}
              elbowR={armE}
              armL={6}
            />
            <div style={{position: 'absolute', left: phoneW[0], top: phoneW[1]}}>
              <HandPhone t={t} glow={phGlow} skin={CAST.daughter.skin as string} rot={-10 + raise * 6} />
            </div>
            {/* "real help": three small sparks off the phone */}
            {[0, 1, 2].map((i) => {
              const a = pop(t, T.help - 0.1 + i * 0.08, 14, 0.45);
              if (a <= 0.01) return null;
              const ang = -2.2 + i * 0.55;
              const d = 80 + (t - T.help) * 30;
              return <div key={i} style={{position: 'absolute', left: phoneW[0] + Math.cos(ang) * d - 8, top: phoneW[1] + Math.sin(ang) * d - 8, width: 16, height: 16, borderRadius: 5, background: [P.marigold, P.sage, P.rose][i], transform: `scale(${a}) rotate(45deg)`}} />;
            })}
          </World>
          <World cam={roomCam} depth={0.95}>
            <Motes t={t} n={34} x0={900} y0={80} w={900} h={900} color="rgba(255,236,190,0.9)" seed={21} />
          </World>
          <World cam={roomCam} depth={1.3}>
            <FgLeaves x={-120} y={1240} s={1.15} t={t} />
          </World>
        </AbsoluteFill>
      )}

      {/* ================= FEATURE WALL ================= */}
      {stageOn && (
        <AbsoluteFill style={{clipPath: reveal < 1 ? `circle(${revealR}px at 960px 540px)` : undefined, background: P.cream}}>
          {/* back wall + arches (slight parallax) */}
          <World cam={stage} depth={0.85}>
            <div style={{position: 'absolute', left: -1200, top: -600, width: 9800, height: 1500, background: P.creamWarm}} />
            <svg style={{position: 'absolute', left: -1200, top: -600, overflow: 'visible'}} width={9800} height={1500}>
              {Array.from({length: 80}).map((_, i) =>
                Array.from({length: 15}).map((__, j) => <circle key={i + '-' + j} cx={i * 124 + (j % 2) * 62} cy={j * 100 + 40} r={4} fill="rgba(224,160,64,0.16)" />),
              )}
            </svg>
            {['#B9DAC8', P.roseLight, '#F3E6CF', '#E3EEE6'].map((c, k) => (
              <div key={k} style={{position: 'absolute', left: OX[k] + 1000, top: 60, width: 860, height: 900, borderRadius: '430px 430px 30px 30px', background: c}} />
            ))}
            {/* shelves between stations, seen during the trucks */}
            {[1, 2, 3].map((k) => (
              <svg key={k} style={{position: 'absolute', left: OX[k] - 50, top: 330, overflow: 'visible'}} width={1} height={1}>
                <rect x={-150} y={0} width={300} height={18} rx={6} fill="#C99A6E" />
                <rect x={-120} y={-80} width={56} height={80} rx={10} fill={[P.rose, P.sage, P.plum][k - 1]} />
                <rect x={-50} y={-120} width={36} height={120} rx={8} fill={P.marigold} />
                <circle cx={60} cy={-36} r={36} fill={[P.mint, P.coral, P.sky][k - 1]} />
                <rect x={30} y={-6} width={60} height={8} rx={4} fill="rgba(0,0,0,0.12)" />
              </svg>
            ))}
          </World>
          <World cam={stage} depth={0.93}>
            <Motes t={t} n={60} x0={-300} y0={0} w={8000} h={900} color="rgba(224,160,64,0.28)" seed={5} />
          </World>
          <World cam={stage}>
            {/* table top running the length of the wall */}
            <div style={{position: 'absolute', left: -1200, top: 880, width: 9800, height: 600, background: '#D9B48C'}} />
            <div style={{position: 'absolute', left: -1200, top: 880, width: 9800, height: 14, background: '#C49A70'}} />
            <div style={{position: 'absolute', left: -1200, top: 894, width: 9800, height: 30, background: 'rgba(110,70,40,0.08)'}} />

            {/* ---- station 0: body map + trend ---- */}
            {(() => {
              const bx = OX[0] + 1250;
              const by = 165;
              const bA = spring(t, GROW1 - 0.2, 11, 0.5);
              const mapGlow = easeOutQuint(ramp(t, T.body, T.map + 0.3));
              const tap = t > T.marked - 0.15 && t < T.marked + 0.4;
              const tapP = ramp(t, T.marked - 0.15, T.marked + 0.4);
              const trendA = pop(t, T.trends - 0.15, 13, 0.5);
              const draw = easeInOutCubic(ramp(t, T.trends, T.trends + 0.7));
              const vals = [3, 6, 7, 5, 6, 4, 4, 3];
              const tw = 280;
              const pts = vals.map((v, i) => [20 + (i / (vals.length - 1)) * (tw - 40), 150 - v * 13] as [number, number]);
              const pd = pts.map((p, i) => (i ? 'L' : 'M') + p[0] + ' ' + p[1]).join(' ');
              return (
                <>
                  <svg style={{position: 'absolute', left: bx, top: by, overflow: 'visible'}} width={1} height={1}>
                    <g transform={`translate(0 ${(1 - bA) * 60}) scale(${0.85 + 0.15 * bA})`} opacity={clamp(bA * 1.5)}>
                      {mapGlow > 0 && <ellipse cx={0} cy={330} rx={220 * mapGlow} ry={380 * mapGlow} fill="rgba(159,209,184,0.35)" />}
                      <BodyFig />
                      <PainSpot t={t} t0={T.pain} x={0} y={52} r={24} />
                      <PainSpot t={t} t0={T.marked + 0.08} x={0} y={290} r={32} />
                      {tap && (
                        <g transform={`translate(${70 - tapP * 70} ${360 - tapP * 70})`} opacity={1 - Math.max(0, tapP - 0.7) / 0.3}>
                          <circle r={14} fill={CAST.daughter.skin as string} />
                          <rect x={6} y={6} width={34} height={60} rx={17} fill={CAST.daughter.skin as string} transform="rotate(-40)" />
                        </g>
                      )}
                    </g>
                  </svg>
                  <Chip x={bx + 90} y={by + 52} a={pop(t, T.pain + 0.25, 13, 0.5)} dot={P.rose}>
                    Head 5/10
                  </Chip>
                  <Chip right x={bx - 120} y={by + 290} a={pop(t, T.marked + 0.3, 13, 0.5)} dot={P.rose}>
                    Abdomen 4/10
                  </Chip>
                  {trendA > 0.01 && (
                    <div style={{position: 'absolute', left: OX[0] + 1520, top: 470, width: tw, height: 230, borderRadius: 24, background: P.white, boxShadow: '0 14px 30px rgba(44,60,50,0.16)', transform: `scale(${trendA}) rotate(${2 - trendA * 2 + Math.sin(t) * 0.6}deg)`, transformOrigin: 'left bottom'}}>
                      <div style={{position: 'absolute', left: 20, top: 16, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 24, color: P.ink}}>Pain, last 8 days</div>
                      <svg style={{position: 'absolute', left: 0, top: 40, overflow: 'visible'}} width={tw} height={170}>
                        {[50, 90, 130].map((y) => (
                          <line key={y} x1={20} x2={tw - 20} y1={y} y2={y} stroke="rgba(56,46,41,0.08)" strokeWidth={2} />
                        ))}
                        <path d={pd + ` L${pts[pts.length - 1][0]} 160 L20 160 Z`} fill="rgba(196,106,132,0.12)" opacity={draw} />
                        <path d={pd} stroke={P.rose} strokeWidth={6} fill="none" strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={`${draw} 1`} />
                        {pts.map((p, i) => (draw >= i / (pts.length - 1) ? <circle key={i} cx={p[0]} cy={p[1]} r={7} fill={P.rose} stroke={P.white} strokeWidth={3} /> : null))}
                      </svg>
                    </div>
                  )}
                  <Chip x={OX[0] + 1540} y={740} a={pop(t, T.trends + 0.3, 13, 0.45)} bg={P.sage} color={P.cream} size={24}>
                    <svg width={22} height={26} viewBox="0 0 22 26">
                      <path d="M3 2 L3 10 Q3 17 10 17 Q17 17 17 10 L17 2" stroke={P.cream} strokeWidth={3} fill="none" strokeLinecap="round" />
                      <path d="M10 17 L10 21 Q10 24 15 24" stroke={P.cream} strokeWidth={3} fill="none" strokeLinecap="round" />
                      <circle cx={17} cy={22} r={3.5} fill={P.cream} />
                    </svg>
                    For the doctor
                  </Chip>
                </>
              );
            })()}

            {/* ---- station 1: prescription snap ---- */}
            <svg style={{position: 'absolute', left: OX[1] + 1690, top: 880, overflow: 'visible'}} width={1} height={1}>
              <rect x={-40} y={-150} width={80} height={150} rx={14} fill={P.marigold} />
              <rect x={-14} y={-150} width={54} height={150} rx={14} fill="rgba(176,122,46,0.35)" />
              <rect x={-46} y={-182} width={92} height={40} rx={10} fill={P.cream} />
              <rect x={-32} y={-110} width={64} height={56} rx={8} fill={P.white} />
              <rect x={-22} y={-96} width={44} height={8} rx={4} fill={P.rose} />
              <rect x={-22} y={-80} width={30} height={6} rx={3} fill="rgba(56,46,41,0.2)" />
              {[[-90, -10, 20], [-60, -8, -30], [70, -9, 60]].map(([x, y, r], i) => (
                <g key={i} transform={`translate(${x} ${y}) rotate(${r})`}>
                  <rect x={-16} y={-8} width={32} height={16} rx={8} fill={P.white} />
                  <rect x={0} y={-8} width={16} height={16} rx={8} fill={P.rose} />
                </g>
              ))}
            </svg>
            <div style={{position: 'absolute', left: RX.x - 240, top: RX.y - 310, width: 480, height: 620, transform: `rotate(-3deg) scale(${1 - (1 - shutter) * 0.04})`, borderRadius: 10, background: P.white, boxShadow: '0 16px 34px rgba(120,60,70,0.16)'}}>
              <div style={{position: 'absolute', left: 190, top: -18, width: 100, height: 36, background: 'rgba(224,160,64,0.55)', transform: 'rotate(4deg)'}} />
              <div style={{position: 'absolute', left: 34, top: 30, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 70, color: P.plum}}>Rx</div>
              <div style={{position: 'absolute', left: 150, top: 46, width: 220, height: 14, borderRadius: 7, background: 'rgba(56,46,41,0.2)'}} />
              <div style={{position: 'absolute', left: 150, top: 74, width: 150, height: 12, borderRadius: 6, background: 'rgba(56,46,41,0.12)'}} />
              <div style={{position: 'absolute', left: 30, top: 130, width: 420, height: 4, background: 'rgba(140,90,134,0.25)'}} />
              {rows.map((r, i) => {
                const lifted = t > tileT0 + i * 0.2;
                return (
                  <div key={i} style={{position: 'absolute', left: 30, top: 160 + i * 92, width: 420, height: 74, borderRadius: 14, background: lifted ? 'rgba(56,46,41,0.04)' : 'transparent', display: 'flex', alignItems: 'center', gap: 16, paddingLeft: 12, opacity: lifted ? 0.35 : 1}}>
                    <div style={{width: 44, height: 22, borderRadius: 11, background: `linear-gradient(90deg, ${P.white} 50%, ${r.c} 50%)`, boxShadow: 'inset 0 0 0 2px rgba(56,46,41,0.15)'}} />
                    <div>
                      <div style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 28, color: P.ink}}>{r.name}</div>
                      <div style={{width: 170, height: 10, marginTop: 6, borderRadius: 5, background: 'rgba(56,46,41,0.13)'}} />
                    </div>
                  </div>
                );
              })}
              <svg style={{position: 'absolute', left: 250, top: 480, overflow: 'visible'}} width={180} height={80}>
                <path d="M0 50 Q20 0 40 40 T80 30 Q100 10 110 44 T170 30" stroke={P.indigo} strokeWidth={4} fill="none" strokeLinecap="round" />
              </svg>
              <div style={{position: 'absolute', left: 250, top: 556, width: 180, height: 3, background: 'rgba(56,46,41,0.25)'}} />
            </div>
            {/* viewfinder brackets closing on the paper */}
            {snapA > 0 && t < T.snap + 0.5 && (
              <svg style={{position: 'absolute', left: RX.x, top: RX.y, overflow: 'visible', opacity: 1 - ramp(t, T.snap + 0.15, T.snap + 0.5)}} width={1} height={1}>
                {[[-1, -1], [1, -1], [-1, 1], [1, 1]].map(([dx, dy], i) => {
                  const k = lerp(1.35, 1.0, easeOutCubic(snapA));
                  const x = dx * 275 * k;
                  const y = dy * 345 * k;
                  return <path key={i} d={`M${x} ${y - dy * 60} L${x} ${y} L${x - dx * 60} ${y}`} stroke={P.ink} strokeWidth={9} fill="none" strokeLinecap="round" strokeLinejoin="round" opacity={0.85} />;
                })}
              </svg>
            )}

            {/* ---- station 2: report with a highlighted line ---- */}
            <div style={{position: 'absolute', left: RP.x - 270, top: RP.y - 330, width: 540, height: 660, transform: 'rotate(5deg)', borderRadius: 10, background: '#F4EEE4'}} />
            <div style={{position: 'absolute', left: RP.x - 270, top: RP.y - 320, width: 540, height: 660, transform: 'rotate(-1deg)', borderRadius: 10, background: '#FBF7F0', boxShadow: '0 8px 20px rgba(80,60,30,0.10)'}} />
            <div style={{position: 'absolute', left: RP.x - 280, top: RP.y - 320, width: 560, height: 660, transform: 'rotate(2deg)', borderRadius: 10, background: P.white, boxShadow: '0 16px 34px rgba(80,60,30,0.16)'}}>
              <div style={{position: 'absolute', left: 230, top: -18, width: 100, height: 36, background: 'rgba(59,117,98,0.4)', transform: 'rotate(-3deg)'}} />
              <div style={{position: 'absolute', left: 36, top: 40, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 36, color: P.sageDark}}>Prescription summary</div>
              <div style={{position: 'absolute', left: 36, top: 94, width: 150, height: 12, borderRadius: 6, background: 'rgba(56,46,41,0.16)'}} />
              {Array.from({length: 8}).map((_, i) => {
                const y = rowY2(i) - (RP.y - 320);
                if (i === hlRow)
                  return (
                    <div key={i} style={{position: 'absolute', left: 30, top: y - 10, height: 46, display: 'flex', alignItems: 'center'}}>
                      <div style={{position: 'absolute', left: 0, top: 4, height: 38, width: `${hl * 100}%`, borderRadius: 8, background: 'rgba(224,160,64,0.55)'}} />
                      <div style={{position: 'relative', padding: '0 8px', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 25, color: P.ink, whiteSpace: 'nowrap'}}>Medicines as listed on this prescription</div>
                    </div>
                  );
                return <div key={i} style={{position: 'absolute', left: 38, top: y + 4, width: [430, 380, 450, 0, 400, 300, 440, 260][i], height: 13, borderRadius: 7, background: 'rgba(56,46,41,0.13)'}} />;
              })}
              <div style={{position: 'absolute', right: 30, bottom: 26, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.45)'}}>Page 1</div>
            </div>

            {/* ---- station 3: India, filling with centres ---- */}
            <IndiaMap t={t} x={MAP.x} y={MAP.y} H={MAP.H} pins={PINS} morphine={MORPH} rippleAt={T.everyState - 0.05} />
            <div style={{position: 'absolute', left: OX[3] + 1500, top: 120, width: 420, opacity: clamp(ramp(t, T.sixteen - 0.25, T.sixteen) * 1.5)}}>
              <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 104, color: P.sageDark, lineHeight: 1, fontVariantNumeric: 'tabular-nums', transform: `scale(${1 + Math.max(0, Math.sin(clamp((t - T.centres - 0.2) / 0.3) * Math.PI)) * 0.08})`, transformOrigin: 'left center'}}>{countStr}</div>
              <Rise t={t} t0={T.centres - 0.1} text="palliative care centres" size={32} color={P.ink} weight={600} stagger={0.08} />
            </div>
            <Chip x={OX[3] + 1540} y={690} a={pop(t, T.everyState - 0.05, 13, 0.45)} bg={P.sage} color={P.cream} size={30}>
              36 states and UTs
            </Chip>
            <Chip x={MAP.x + MAIN_PIN.x * MAP.H + 34} y={MAP.y + MAIN_PIN.y * MAP.H - 30} a={pop(t, T.morphine + 0.12, 13, 0.45)} dot={P.marigold} size={26}>
              morphine
            </Chip>
          </World>
          <World cam={stage} depth={1.3}>
            <FgLeaves x={OX[1] - 120} y={1260} s={1.1} t={t} />
            <FgLeaves x={OX[2] + 40} y={1280} s={1.0} t={t} flip color="#7A4C74" color2="#8C5A86" />
            <FgLeaves x={OX[3] - 80} y={1260} s={1.1} t={t} />
            <FgLeaves x={OX[3] + 2180} y={1260} s={1.2} t={t} flip />
          </World>

          {/* ---------- screen space: the big phone held in a hand ---------- */}
          <div style={{position: 'absolute', inset: 0, transform: `translate(${phDX - truckLag}px, ${phDY}px) rotate(${rot}deg) scale(${phScale * (1 + bump)})`, transformOrigin: `${PH.x}px ${PH.y + 300}px`}}>
            {handIn > 0 && (
              <svg style={{position: 'absolute', left: 0, top: (1 - handIn) * 300, overflow: 'visible'}} width={1} height={1}>
                <path d="M330 1200 Q390 1000 470 900" stroke={P.rose} strokeWidth={120} fill="none" strokeLinecap="round" />
                <path d="M330 1200 Q360 1110 400 1050" stroke={P.marigold} strokeWidth={130} fill="none" strokeLinecap="butt" opacity={0.95} />
                <ellipse cx={560} cy={820} rx={120} ry={80} fill={CAST.daughter.skin as string} transform="rotate(-20 560 820)" />
              </svg>
            )}
            <PhoneSeq t={t} segs={segs} x={PH.x} y={PH.y} h={PH.h} note={false} />
            {handIn > 0 && (
              <svg style={{position: 'absolute', left: 0, top: (1 - handIn) * 300, overflow: 'visible'}} width={1} height={1}>
                {[0, 1, 2].map((i) => (
                  <rect key={i} x={772} y={640 + i * 52} width={44} height={36} rx={18} fill={CAST.daughter.skin as string} />
                ))}
                <rect x={772} y={640} width={14} height={140} fill="rgba(60,30,20,0.12)" rx={6} />
                <rect x={384} y={700} width={46} height={120} rx={23} fill={CAST.daughter.skin as string} transform="rotate(18 407 760)" />
              </svg>
            )}
          </div>
          <div style={{opacity: clamp(grow)}}>
            <PracticeNote x={830} y={862} />
          </div>

          {/* station 1 overlays: tiles fly into the phone, dose chips, reminder */}
          {rows.map((r, i) => {
            const t0 = tileT0 + i * 0.2;
            const p = ramp(t, t0, t0 + 0.55);
            if (p <= 0 || p >= 1) return null;
            const [ax, ay] = sx(...rowW(i));
            const bx = PH.x;
            const by = PH.y - 120 + i * 70;
            const e = easeInOutCubic(p);
            const x = lerp(ax, bx, e);
            const y = lerp(ay, by, e) - Math.sin(p * Math.PI) * 170;
            const s = lerp(1, 0.55, easeInCubic(p));
            return (
              <div key={i} style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s}) rotate(${Math.sin(p * Math.PI) * -12}deg)`, opacity: 1 - ramp(p, 0.85, 1), display: 'flex', alignItems: 'center', gap: 14, padding: '14px 26px', borderRadius: 20, background: P.white, boxShadow: '0 16px 30px rgba(80,40,60,0.22)', whiteSpace: 'nowrap'}}>
                <div style={{width: 40, height: 20, borderRadius: 10, background: `linear-gradient(90deg, ${P.roseLight} 50%, ${r.c} 50%)`}} />
                <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 28, color: P.ink}}>{r.name}</div>
              </div>
            );
          })}
          {(() => {
            const out = 1 - easeInCubic(ramp(t, TRUCK[1][0] - 0.1, TRUCK[1][0] + 0.15));
            if (out <= 0 || t < T.every - 0.2) return null;
            const times = ['8:00 am', '2:00 pm', '8:00 pm'];
            return times.map((tm, i) => {
              const a = pop(t, T.every - 0.05 + i * 0.13, 14, 0.45) * out;
              const ring = i === 0 && t > T.reminder ? Math.max(0, Math.sin((t - T.reminder) * 9)) * Math.exp(-(t - T.reminder) * 2) : 0;
              return (
                <Chip key={i} x={830} y={420 + i * 90} a={a * (1 + ring * 0.12)} dot={i === 0 ? P.rose : P.sage} size={28}>
                  {tm}
                </Chip>
              );
            });
          })()}
          {(() => {
            const sIn = spring(t, T.reminder - 0.08, 12, 0.55);
            const sOut = easeInCubic(ramp(t, TRUCK[1][0] - 0.05, TRUCK[1][0] + 0.25));
            if (sIn <= 0.001 || sOut >= 1) return null;
            const y = lerp(-140, 150, sIn) - sOut * 300;
            const dt = t - T.reminder;
            const shake = dt > 0 ? Math.sin(dt * 40) * Math.exp(-dt * 6) * 10 : 0;
            return (
              <div style={{position: 'absolute', left: PH.x - 250, top: y - 52, width: 500, height: 104, borderRadius: 30, background: P.white, boxShadow: '0 18px 40px rgba(40,30,50,0.26)', display: 'flex', alignItems: 'center', gap: 18, padding: '0 22px'}}>
                <div style={{position: 'relative', width: 64, height: 64, borderRadius: '50%', background: P.marigold, flex: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                  {dt > 0 &&
                    dt < 2.2 &&
                    [0, 1].map((k) => {
                      const ph = ((dt + k * 0.35) % 0.9) / 0.9;
                      return <div key={k} style={{position: 'absolute', left: -ph * 26, top: -ph * 26, width: 64 + ph * 52, height: 64 + ph * 52, borderRadius: '50%', border: `4px solid ${P.marigold}`, opacity: 1 - ph, boxSizing: 'border-box'}} />;
                    })}
                  <svg width={34} height={36} viewBox="0 0 34 36" style={{transform: `rotate(${shake}deg)`}}>
                    <path d="M17 3 Q6 3 6 16 L6 24 L2 29 L32 29 L28 24 L28 16 Q28 3 17 3 Z" fill={P.white} />
                    <circle cx={17} cy={32} r={4} fill={P.white} />
                  </svg>
                </div>
                <div>
                  <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 29, color: P.ink, whiteSpace: 'nowrap'}}>8:00 am · Morphine 5 mg</div>
                  <div style={{fontFamily: FONT_BODY, fontSize: 22, color: P.inkSub}}>Time for this dose</div>
                </div>
              </div>
            );
          })()}

          {/* station 2 overlays: the question, and the answer chip joined to its line */}
          {(() => {
            const out = 1 - easeInCubic(ramp(t, TRUCK[2][0] - 0.1, TRUCK[2][0] + 0.15));
            const qa = pop(t, T.ask + 0.05, 12, 0.5) * out;
            if (qa <= 0.01) return null;
            const [lx, ly] = sx(RP.x - 250, rowY2(hlRow) + 14);
            const ax = 1050;
            const ay = 640;
            const ansA = pop(t, T.line + 0.45, 13, 0.45) * out;
            const cp = `M${lx} ${ly} C${lx - 120} ${ly + 40} ${ax + 120} ${ay - 90} ${ax} ${ay - 34}`;
            return (
              <>
                <div style={{position: 'absolute', left: 830, top: 170, transform: `scale(${qa})`, transformOrigin: '0% 80%'}}>
                  <svg style={{position: 'absolute', left: -26, top: 70, overflow: 'visible'}} width={1} height={1}>
                    <path d="M30 0 L30 44 L-4 40 Z" fill="#F6EBC2" />
                  </svg>
                  <div style={{padding: '22px 30px', borderRadius: 28, background: '#F6EBC2', width: 330, boxShadow: '0 12px 26px rgba(80,60,20,0.14)'}}>
                    <div style={{fontFamily: FONT_BODY, fontSize: 21, color: P.inkSub, marginBottom: 4}}>Your question</div>
                    <Rise t={t} t0={T.reports} text="Which medicines are listed?" size={34} color={P.ink} stagger={0.07} />
                  </div>
                </div>
                {conn > 0 && (
                  <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: out}} width={1} height={1}>
                    <defs>
                      <mask id="xconn">
                        <path d={cp} stroke="#fff" strokeWidth={14} fill="none" pathLength={1} strokeDasharray={`${conn} 1`} />
                      </mask>
                    </defs>
                    <path d={cp} stroke={P.marigoldDeep} strokeWidth={6} strokeDasharray="2 13" strokeLinecap="round" fill="none" mask="url(#xconn)" />
                    <circle cx={lx} cy={ly} r={10 * Math.min(1, conn * 3)} fill={P.marigold} />
                  </svg>
                )}
                <Chip x={830} y={ay} a={ansA} bg={P.sage} color={P.cream} size={28}>
                  <svg width={22} height={28} viewBox="0 0 22 28">
                    <path d="M2 2 L14 2 L20 8 L20 26 L2 26 Z" fill={P.cream} />
                    <rect x={6} y={13} width={10} height={3} fill={P.sage} />
                    <rect x={6} y={19} width={8} height={3} fill={P.sage} />
                  </svg>
                  Page 1, line 4
                </Chip>
              </>
            );
          })()}
        </AbsoluteFill>
      )}

      {/* camera flash on "Snap" */}
      {flash > 0.01 && <div style={{position: 'absolute', inset: 0, background: '#FFFDF6', opacity: flash * 0.85}} />}

      {/* entry: the cream from scene E opens as an iris */}
      {irisIn < 1 && <div style={{position: 'absolute', left: 960 - irisR, top: 520 - irisR, width: irisR * 2, height: irisR * 2, borderRadius: '50%', boxShadow: `0 0 0 3000px ${P.cream}`}} />}
      {/* exit: warm cream grows from the morphine pin into scene F */}
      {exit > 0 &&
        [
          [2300 * exit + 46 * (1 - exit * 0.6), P.marigold],
          [2300 * exit, P.creamWarm],
        ].map(([r, c], i) => <div key={i} style={{position: 'absolute', left: pinS[0] - (r as number), top: pinS[1] - (r as number), width: (r as number) * 2, height: (r as number) * 2, borderRadius: '50%', background: c as string}} />)}
    </AbsoluteFill>
  );
};
