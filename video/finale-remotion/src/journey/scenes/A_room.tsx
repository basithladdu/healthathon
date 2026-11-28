/* The emergency room at 3 a.m. Shared by scenes A, G and H so the room is identical every time we return. */
import React from 'react';
import {Cam, P, World, FONT_HEAD, FONT_BODY, Person, PersonProps, clamp, back, lerp} from '../kit';
import {CAST} from '../cast';

/* ---------- layout (world units, 1920x1080 room) ---------- */
export const CLOCK = {x: 1000, y: 212};
export const FLOOR_Y = 836;
export const PATIENT = {x: 774, y: 728, s: 0.8};
export const PATIENT_HEAD = {x: 742, y: 534};
export const CHAIR = {x: 410, y: 772}; // seat top
export const STAND = {x: 440, y: 864}; // daughter standing spot (left of the bed)
export const DOC_SPOT = {x: 1440, y: 864}; // doctor standing spot (right of the bed)
export const LAMP = {x: 800, y: 366};

/** world point -> screen point for a camera (no rotation). */
export const toScreen = (cam: Cam, wx: number, wy: number, depth = 1): [number, number] => {
  const z = Math.pow(cam.z, depth);
  const cx = 960 + (cam.x - 960) * depth;
  const cy = 540 + (cam.y - 540) * depth;
  return [960 + (wx - cx) * z, 540 + (wy - cy) * z];
};
/** camera x,y that centres world point (px,py) on a parallax layer of given depth */
export const camFor = (px: number, py: number, depth = 1) => ({x: 960 + (px - 960) / depth, y: 540 + (py - 540) / depth});

/** where a Person's right (front) hand is, in world units. Mirrors kit's arm maths (ignores lean). */
export const handPos = (pp: Partial<PersonProps> & {x: number; y: number}, t: number, a: number, e: number): [number, number] => {
  const s = pp.s ?? 1;
  const legH = pp.sitting ? 0 : 110;
  const bodyTop = -legH - 150;
  const sx = 52;
  const sy = bodyTop + 30;
  const a1 = (a * Math.PI) / 180;
  const ex = sx + Math.sin(a1) * 62;
  const ey = sy + Math.cos(a1) * 62;
  const a2 = a1 + (e * Math.PI) / 180;
  const hx = ex + Math.sin(a2) * 58;
  const hy = ey + Math.cos(a2) * 58;
  const bobv = Math.sin(t * 2.1 + (pp.seed ?? 1)) * 3 * (pp.bob ?? 1);
  return [pp.x + (pp.flip ? -hx : hx) * s, pp.y + (hy + bobv) * s];
};

/* ---------- ECG ---------- */
const g = (x: number, m: number, s: number) => Math.exp(-((x - m) * (x - m)) / (2 * s * s));
const ecg = (p: number) => 0.12 * g(p, 0.12, 0.025) - 0.14 * g(p, 0.29, 0.008) + 1 * g(p, 0.315, 0.009) - 0.3 * g(p, 0.34, 0.01) + 0.24 * g(p, 0.57, 0.045);
const frac = (x: number) => x - Math.floor(x);
export const beepFlash = (t: number, bpm: number) => Math.exp(-frac(t * (bpm / 60) - 0.315) * 9);

const Monitor: React.FC<{t: number; bpm: number}> = ({t, bpm}) => {
  const X0 = 316;
  const W = 228;
  const Y0 = 262;
  const base = Y0 + 52;
  const amp = 40;
  const speed = W / 2.2;
  const sweep = frac(t / 2.2) * W;
  const gap = 14;
  const N = 140;
  const segs: string[] = [];
  let cur = '';
  for (let i = 0; i <= N; i++) {
    const x = (i / N) * W;
    const dx = (((sweep - x) % W) + W) % W;
    if (dx < gap) {
      if (cur) segs.push(cur);
      cur = '';
      continue;
    }
    const st = t - dx / speed;
    const v = ecg(frac((st * bpm) / 60));
    const px = X0 + x;
    const py = base - v * amp;
    cur += (cur ? ' L' : 'M') + px.toFixed(1) + ' ' + py.toFixed(1);
  }
  if (cur) segs.push(cur);
  const headV = ecg(frac((t * bpm) / 60));
  const fl = beepFlash(t, bpm);
  return (
    <g>
      {/* wall arm */}
      <rect x={414} y={196} width={36} height={20} rx={4} fill="#22384F" />
      <rect x={424} y={206} width={16} height={48} fill="#2A425C" />
      <rect x={296} y={240} width={268} height={168} rx={16} fill="#0B1522" />
      <rect x={306} y={250} width={248} height={148} rx={10} fill="#06101A" />
      <g filter="url(#ecgGlow)">
        {segs.map((d, i) => (
          <path key={i} d={d} stroke={P.monitor} strokeWidth={3.2} fill="none" strokeLinejoin="round" strokeLinecap="round" />
        ))}
      </g>
      <circle cx={X0 + sweep} cy={base - headV * amp} r={4.5} fill="#E9FFF8" />
      {/* numbers */}
      <text x={320} y={384} fontFamily={FONT_HEAD} fontSize={26} fill={P.monitor} fontWeight={600}>
        {Math.round(bpm + Math.sin(t * 0.7) * 2)}
      </text>
      <text x={364} y={384} fontFamily={FONT_BODY} fontSize={13} fill="#3E8F7C">
        HR
      </text>
      <text x={408} y={384} fontFamily={FONT_HEAD} fontSize={22} fill={P.sky} fontWeight={600}>
        {bpm > 90 ? 91 : 96}
      </text>
      <text x={442} y={384} fontFamily={FONT_BODY} fontSize={13} fill="#4F7FA3">
        SpO2
      </text>
      <text x={488} y={384} fontFamily={FONT_HEAD} fontSize={19} fill={P.lampWarm} fontWeight={600}>
        {bpm > 90 ? '96/58' : '110/70'}
      </text>
      {/* beep light */}
      <circle cx={538} cy={264} r={5} fill={P.monitor} opacity={0.25 + 0.75 * fl} />
      <circle cx={538} cy={264} r={5 + 10 * fl} fill={P.monitor} opacity={0.25 * fl} />
      <rect x={296} y={408} width={268} height={6} rx={3} fill="#16273A" />
    </g>
  );
};

/* ---------- clock ---------- */
const Clock: React.FC<{t: number; minute?: number}> = ({t, minute = 0}) => {
  const {x, y} = CLOCK;
  const s = Math.floor(t);
  const f = t - s;
  const secA = (s + back(Math.min(1, f / 0.14), 2.2)) * 6;
  const minA = (minute + t / 60) * 6;
  const hrA = 90 + (minute + t / 60) * 0.5;
  const hand = (a: number, len: number, w: number, c: string, tail = 12) => (
    <rect x={-w / 2} y={-len} width={w} height={len + tail} rx={w / 2} fill={c} transform={`translate(${x} ${y}) rotate(${a})`} />
  );
  return (
    <g>
      <circle cx={x + 6} cy={y + 8} r={82} fill="rgba(5,10,20,0.35)" />
      <circle cx={x} cy={y} r={80} fill="#2C4562" />
      <circle cx={x} cy={y} r={68} fill="#EDE7DA" />
      <circle cx={x - 12} cy={y - 14} r={50} fill="rgba(255,255,255,0.22)" />
      {Array.from({length: 12}).map((_, i) => {
        const a = i * 30;
        const big = i % 3 === 0;
        return <rect key={i} x={x - (big ? 2.5 : 1.5)} y={y - 62} width={big ? 5 : 3} height={big ? 12 : 7} rx={1.5} fill="#5E5249" transform={`rotate(${a} ${x} ${y})`} />;
      })}
      {[
        ['12', 0, -40],
        ['3', 42, 0],
        ['6', 0, 42],
        ['9', -42, 0],
      ].map(([n, dx, dy]) => (
        <text key={n as string} x={x + (dx as number)} y={y + (dy as number) + 8} textAnchor="middle" fontFamily={FONT_HEAD} fontSize={22} fontWeight={600} fill="#382E29">
          {n}
        </text>
      ))}
      {hand(hrA, 36, 9, '#382E29')}
      {hand(minA, 52, 6, '#382E29')}
      {hand(secA, 56, 2.6, P.coral, 16)}
      <circle cx={x} cy={y} r={6} fill={P.coral} />
    </g>
  );
};

/* ---------- window with parallax night city ---------- */
const BUILD = [
  [0, 110, 0],
  [60, 170, 1],
  [130, 90, 0],
  [190, 210, 1],
  [270, 140, 0],
  [330, 260, 1],
  [420, 120, 0],
  [480, 190, 1],
  [560, 150, 0],
  [620, 230, 1],
  [700, 100, 0],
];
const NightWindow: React.FC<{t: number; cam: Cam; id: string; warm: number}> = ({t, cam, id, warm}) => {
  const X = 1432;
  const Y = 150;
  const Wd = 400;
  const H = 420;
  const ox = (cam.x - 960) * 0.62;
  const oy = (cam.y - 540) * 0.5;
  const blindBottom = 404;
  return (
    <g>
      <rect x={X - 22} y={Y - 22} width={Wd + 44} height={H + 44} rx={8} fill="#28405C" />
      <defs>
        <clipPath id={'win' + id}>
          <rect x={X} y={Y} width={Wd} height={H} />
        </clipPath>
        <linearGradient id={'sky' + id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#0A1424" />
          <stop offset="1" stopColor={warm > 0 ? '#2A3550' : '#1E3352'} />
        </linearGradient>
        <radialGradient id={'moon' + id}>
          <stop offset="0" stopColor="#F3EAD0" stopOpacity="0.55" />
          <stop offset="1" stopColor="#F3EAD0" stopOpacity="0" />
        </radialGradient>
      </defs>
      <g clipPath={`url(#win${id})`}>
        <rect x={X} y={Y} width={Wd} height={H} fill={`url(#sky${id})`} />
        <g transform={`translate(${ox} ${oy})`}>
          {Array.from({length: 18}).map((_, i) => {
            const sx = X - 120 + ((i * 97) % 640);
            const sy = Y + 20 + ((i * 53) % 170);
            const tw = 0.4 + 0.6 * Math.abs(Math.sin(t * 1.3 + i * 1.7));
            return <circle key={i} cx={sx} cy={sy} r={i % 3 === 0 ? 2.2 : 1.4} fill="#DDE6F5" opacity={tw * 0.8} />;
          })}
          <circle cx={X + 290} cy={Y + 92} r={90} fill={`url(#moon${id})`} />
          <circle cx={X + 290} cy={Y + 92} r={32} fill="#EFE6CC" />
          <circle cx={X + 302} cy={Y + 84} r={28} fill="#1A2C46" opacity={0.0} />
        </g>
        <g transform={`translate(${ox * 0.55 - 120} ${oy * 0.6})`}>
          {BUILD.map(([bx, bh, k], i) => (
            <g key={i}>
              <rect x={X + bx} y={Y + H - bh} width={k ? 56 : 64} height={bh} fill={k ? '#132339' : '#172A43'} />
              {Array.from({length: Math.floor(bh / 26)}).map((_, j) =>
                [0, 1, 2].map((c) => {
                  const on = ((i * 7 + j * 3 + c * 5) % 5) < 2;
                  if (!on) return null;
                  const fl = 0.6 + 0.4 * Math.sin(t * 0.8 + i + j * 2 + c);
                  return <rect key={j + '-' + c} x={X + bx + 8 + c * 16} y={Y + H - bh + 12 + j * 26} width={9} height={11} fill={P.lampWarm} opacity={0.55 * fl} />;
                }),
              )}
            </g>
          ))}
        </g>
        {/* blinds */}
        {Array.from({length: 12}).map((_, i) => {
          const sy = Y + i * 21;
          if (sy > blindBottom) return null;
          return (
            <g key={i}>
              <rect x={X} y={sy} width={Wd} height={13} fill="#3B5A7C" opacity={0.94} />
              <rect x={X} y={sy} width={Wd} height={3} fill="#5A7CA0" opacity={0.9} />
            </g>
          );
        })}
        <rect x={X} y={blindBottom + 4} width={Wd} height={12} rx={3} fill="#4B6B8E" />
        <rect x={X + 96} y={Y} width={3} height={blindBottom - Y + 70} fill="#5A7CA0" opacity={0.7} />
        <rect x={X + 300} y={Y} width={3} height={blindBottom - Y + 40} fill="#5A7CA0" opacity={0.7} />
      </g>
      <rect x={X - 6} y={Y + H} width={Wd + 12} height={10} fill="#3A5778" />
      <rect x={X - 30} y={Y + H + 10} width={Wd + 60} height={18} rx={4} fill="#304D6C" />
      <rect x={X + Wd / 2 - 4} y={Y} width={8} height={H} fill="#28405C" />
    </g>
  );
};

/* ---------- curtain (pleated, gathered on the left) ---------- */
const Curtain: React.FC<{t: number}> = ({t}) => {
  const pleats = 7;
  const x0 = 26;
  const w = 36;
  return (
    <g>
      {Array.from({length: pleats}).map((_, i) => {
        const x = x0 + i * w;
        const sway = Math.sin(t * 0.7 + i * 0.8) * 3;
        const bot = 812 + (i % 2) * 10;
        return (
          <path
            key={i}
            d={`M${x} 76 L${x + w} 76 L${x + w + sway} ${bot - 6} Q${x + w / 2 + sway} ${bot + 12} ${x + sway} ${bot} Z`}
            fill={i % 2 ? '#29525E' : '#30606D'}
          />
        );
      })}
      <path d={`M${x0 + pleats * w - 6} 76 L${x0 + pleats * w + 6} 76 L${x0 + pleats * w + 10} 820 L${x0 + pleats * w - 4} 822 Z`} fill="#22464F" />
      {/* tie-back */}
      <rect x={x0 - 4} y={420} width={pleats * w + 10} height={16} rx={8} fill="#1E3E47" />
    </g>
  );
};

/* ---------- IV stand and lamp ---------- */
const IVStand: React.FC<{t: number}> = ({t}) => {
  const drip = frac(t / 1.6);
  return (
    <g>
      <rect x={604} y={176} width={9} height={668} rx={4} fill="#5D7A98" />
      <rect x={584} y={176} width={48} height={7} rx={3} fill="#5D7A98" />
      {/* base */}
      <path d="M560 846 L656 846 L640 834 L576 834 Z" fill="#3E5A78" />
      <circle cx={566} cy={852} r={8} fill="#1B2B40" />
      <circle cx={650} cy={852} r={8} fill="#1B2B40" />
      {/* bag */}
      <path d="M582 196 L634 196 Q644 250 628 290 L588 290 Q572 250 582 196 Z" fill="#BFE0EC" opacity={0.55} />
      <path d="M586 232 L630 232 Q636 262 626 286 L590 286 Q580 262 586 232 Z" fill="#E8F6FB" opacity={0.5} />
      <rect x={604} y={290} width={8} height={26} rx={3} fill="#A9C6D6" opacity={0.8} />
      <circle cx={608} cy={300 + drip * 14} r={2.4} fill="#E8F6FB" opacity={1 - drip} />
      {/* tube down to the patient's hand */}
      <path d="M608 316 Q596 470 690 560 Q760 640 842 676" stroke="#C9DCE6" strokeWidth={3} fill="none" opacity={0.6} />
    </g>
  );
};

const Lamp: React.FC<{on: number}> = ({on}) => (
  <g>
    <rect x={688} y={286} width={24} height={34} rx={6} fill="#2A425C" />
    <path d="M700 300 Q760 286 800 330" stroke="#3E5A78" strokeWidth={9} fill="none" strokeLinecap="round" />
    <path d="M770 326 L832 326 L846 368 L756 368 Z" fill="#E7D6B8" />
    <path d="M770 326 L800 326 L790 368 L756 368 Z" fill="#CDB896" />
    <ellipse cx={801} cy={368} rx={45} ry={7} fill={on > 0.05 ? '#FFE3A8' : '#8C7E68'} opacity={0.4 + 0.6 * on} />
  </g>
);

/* ---------- chair (for the daughter, left corner) ---------- */
export const Chair: React.FC = () => {
  const {x, y} = CHAIR;
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, overflow: 'visible'}} viewBox="0 0 1920 1080">
      <rect x={x - 76} y={y - 210} width={22} height={300} rx={10} fill="#344E6C" />
      <rect x={x - 82} y={y - 212} width={34} height={120} rx={14} fill="#41608A" />
      <rect x={x - 80} y={y - 4} width={160} height={24} rx={10} fill="#41608A" />
      <rect x={x - 80} y={y + 12} width={160} height={10} rx={4} fill="#2C4562" />
      <rect x={x + 58} y={y + 16} width={14} height={FLOOR_Y + 22 - y - 16} rx={6} fill="#2C4562" />
      <rect x={x - 70} y={y + 16} width={14} height={FLOOR_Y + 22 - y - 16} rx={6} fill="#2C4562" />
    </svg>
  );
};

/** the daughter sitting on the chair, drawing her own legs (kit's sitting mode removes them) */
export const SeatedDaughter: React.FC<{t: number; expr?: PersonProps['expr']; lean?: number; headTilt?: number; look?: number; armL?: number; armR?: number; elbowL?: number; elbowR?: number; opacity?: number}> = ({
  t,
  expr = 'worried',
  lean = 0,
  headTilt = 0,
  look = 1,
  armL = -8,
  armR = -14,
  elbowL = -50,
  elbowR = -40,
}) => {
  const {x, y} = CHAIR;
  const leg = '#3D3550';
  return (
    <>
      <Person {...CAST.daughter} t={t} x={x} y={y + 2} s={1} sitting expr={expr} lean={lean} headTilt={headTilt} look={look} armL={armL} armR={armR} elbowL={elbowL} elbowR={elbowR} />
      <svg style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, overflow: 'visible'}} viewBox="0 0 1920 1080">
        <rect x={x - 34} y={y - 34} width={130} height={34} rx={16} fill={leg} />
        <rect x={x + 66} y={y - 30} width={30} height={FLOOR_Y + 22 - y + 22} rx={14} fill={leg} />
        <rect x={x - 10} y={y - 34} width={106} height={14} rx={7} fill="rgba(0,0,0,0.12)" />
        <ellipse cx={x + 94} cy={FLOOR_Y + 22} rx={28} ry={12} fill="#3A2B26" />
      </svg>
    </>
  );
};

/* ---------- the bed with the patient ---------- */
export const Bed: React.FC<{t: number; expr?: PersonProps['expr']; look?: number; headTilt?: number}> = ({t, expr = 'tired', look = 0.6, headTilt = 0}) => {
  const breath = Math.sin(t * 1.6) * 2;
  return (
    <>
      <svg style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, overflow: 'visible'}} viewBox="0 0 1920 1080">
        {/* shadow on floor */}
        <ellipse cx={1000} cy={FLOOR_Y + 22} rx={400} ry={22} fill="rgba(4,10,20,0.45)" />
        {/* headboard */}
        <rect x={636} y={540} width={40} height={262} rx={18} fill="#3D5878" />
        <rect x={642} y={548} width={12} height={240} rx={6} fill="#4C6A8C" />
        {/* raised backrest */}
        <path d="M676 708 L700 560 Q706 540 726 548 L812 640 L800 708 Z" fill="#2E4866" />
        {/* pillow */}
        <ellipse cx={724} cy={612} rx={52} ry={86} fill="#D2DEE8" transform="rotate(-24 724 612)" />
        <ellipse cx={736} cy={600} rx={30} ry={60} fill="#E3EBF2" transform="rotate(-24 736 600)" />
        {/* mattress */}
        <rect x={662} y={698} width={672} height={70} rx={20} fill="#B6C7D7" />
        {/* frame */}
        <rect x={664} y={766} width={672} height={28} rx={8} fill="#2E4866" />
        <rect x={700} y={792} width={16} height={40} fill="#26405C" />
        <rect x={1286} y={792} width={16} height={40} fill="#26405C" />
        <circle cx={708} cy={842} r={14} fill="#172638" />
        <circle cx={1294} cy={842} r={14} fill="#172638" />
        {/* footboard */}
        <rect x={1320} y={632} width={36} height={172} rx={16} fill="#3D5878" />
        {/* chart clipboard on footboard */}
        <rect x={1328} y={672} width={40} height={54} rx={4} fill="#D9D2C4" />
        <rect x={1336} y={666} width={24} height={9} rx={3} fill="#7C8794" />
        <rect x={1334} y={686} width={28} height={4} rx={2} fill="#9A9184" />
        <rect x={1334} y={696} width={22} height={4} rx={2} fill="#9A9184" />
        <rect x={1334} y={706} width={26} height={4} rx={2} fill="#9A9184" />
      </svg>
      <Person {...CAST.patient} t={t} x={PATIENT.x} y={PATIENT.y} s={PATIENT.s} sitting expr={expr} look={look} lean={-10} headTilt={headTilt - 6} armL={-6} elbowL={-30} armR={34} elbowR={-58} bob={0.5} />
      <svg style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, overflow: 'visible'}} viewBox="0 0 1920 1080">
        {/* blanket */}
        <path d={`M734 ${672 + breath * 0.4} Q770 ${650 + breath} 830 ${652 + breath} L1050 654 Q1138 ${612} 1214 638 Q1296 652 1336 674 L1340 786 Q1034 800 726 788 Z`} fill="#86A2BD" />
        <path d={`M1070 652 Q1138 612 1214 638 Q1250 646 1270 656 Q1190 640 1120 662 Z`} fill="#9DB6CE" />
        <path d={`M734 ${672 + breath * 0.4} Q770 ${650 + breath} 830 ${652 + breath} L960 653 L962 680 Q840 684 736 696 Z`} fill="#D3DDE7" />
        <path d="M740 760 Q1030 776 1338 756 L1340 786 Q1034 800 726 788 Z" fill="#7390AD" />
        {/* patient's hand resting on the blanket, IV line */}
        <ellipse cx={846} cy={672} rx={17} ry={12} fill={P.skin2} />
        <rect x={834} y={666} width={18} height={9} rx={3} fill="#F0F3F6" opacity={0.85} />
        {/* side rail */}
        <rect x={880} y={690} width={300} height={12} rx={6} fill="#6684A3" />
        <rect x={900} y={700} width={9} height={64} rx={4} fill="#6684A3" />
        <rect x={1152} y={700} width={9} height={64} rx={4} fill="#6684A3" />
      </svg>
    </>
  );
};

/* ---------- the room (background layer). Characters go on top in a <World cam> ---------- */
export const RoomBG: React.FC<{t: number; cam: Cam; warm?: number; lamp?: number; sign?: number; bpm?: number; minute?: number; id?: string; chair?: boolean}> = ({
  t,
  cam,
  warm = 0,
  lamp = 1,
  sign = 1,
  bpm = 104,
  minute = 0,
  id = 'a',
  chair = true,
}) => {
  const flick = sign >= 1 ? 1 : sign * (0.6 + 0.4 * Math.sin(t * 60));
  return (
    <>
      <div style={{position: 'absolute', inset: 0, background: P.night0}} />
      <World cam={cam}>
        <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680}} viewBox="-500 -300 2920 1680">
          <defs>
            <linearGradient id={'wall' + id} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#1A2E46" />
              <stop offset="1" stopColor="#21395A" />
            </linearGradient>
            <filter id="ecgGlow" x="-20%" y="-50%" width="140%" height="200%">
              <feGaussianBlur stdDeviation="3" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <filter id={'signGlow' + id} x="-30%" y="-80%" width="160%" height="260%">
              <feGaussianBlur stdDeviation="6" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>
          {/* wall */}
          <rect x={-500} y={-300} width={2920} height={1140} fill={`url(#wall${id})`} />
          {/* ceiling */}
          <rect x={-500} y={-300} width={2920} height={364} fill="#122236" />
          <rect x={-500} y={58} width={2920} height={8} fill="#1C3048" />
          {/* ceiling panel light, off */}
          <rect x={860} y={30} width={280} height={22} rx={6} fill="#1D3149" />
          {/* curtain rail */}
          <rect x={-40} y={66} width={2000} height={9} rx={4} fill="#36536F" />
          {Array.from({length: 26}).map((_, i) => (
            <rect key={i} x={20 + i * 10} y={72} width={4} height={8} fill="#36536F" />
          ))}
          {/* wainscot */}
          <rect x={-500} y={652} width={2920} height={190} fill="#172A41" />
          <rect x={-500} y={644} width={2920} height={10} fill="#2A4562" />
          {/* floor */}
          <rect x={-500} y={FLOOR_Y} width={2920} height={600} fill="#132437" />
          <rect x={-500} y={FLOOR_Y} width={2920} height={12} fill="#0E1C2D" />
          {[900, 990, 1110].map((fy) => (
            <rect key={fy} x={-500} y={fy} width={2920} height={3} fill="#1A2F47" />
          ))}
          {/* whiteboard */}
          <rect x={1146} y={420} width={238} height={150} rx={8} fill="#2B4462" />
          <rect x={1154} y={428} width={222} height={134} rx={4} fill="#B5C2D0" />
          <text x={1170} y={466} fontFamily={FONT_HEAD} fontSize={28} fontWeight={600} fill="#36516F">
            Bed 4
          </text>
          <rect x={1170} y={484} width={150} height={6} rx={3} fill="#7B8FA6" />
          <rect x={1170} y={502} width={110} height={6} rx={3} fill="#7B8FA6" />
          <rect x={1170} y={520} width={170} height={6} rx={3} fill="#7B8FA6" />
          <rect x={1170} y={538} width={80} height={6} rx={3} fill={P.rose} opacity={0.7} />
          <rect x={1300} y={556} width={40} height={10} rx={3} fill="#3B5A7C" />
          {/* EMERGENCY lightbox */}
          <rect x={1146} y={318} width={238} height={64} rx={10} fill="#0E1E30" />
          <rect x={1154} y={326} width={222} height={48} rx={6} fill={flick > 0.5 ? '#0F3A3A' : '#132A3E'} />
          <text x={1265} y={361} textAnchor="middle" fontFamily={FONT_HEAD} fontSize={30} fontWeight={600} letterSpacing={3} fill={flick > 0.5 ? '#A8F5E0' : '#2E4A62'} filter={flick > 0.5 ? `url(#signGlow${id})` : undefined}>
            EMERGENCY
          </text>
          {/* sanitiser */}
          <rect x={1398} y={600} width={26} height={44} rx={6} fill="#5D7A98" />
          <rect x={1404} y={590} width={14} height={12} rx={3} fill="#3E5A78" />
          {/* wall socket strip behind bed */}
          <rect x={880} y={600} width={160} height={20} rx={6} fill="#22394F" />
          <circle cx={910} cy={610} r={4} fill={P.monitor} opacity={0.7} />
          <circle cx={930} cy={610} r={4} fill={P.lampWarm} opacity={0.6} />
          <NightWindow t={t} cam={cam} id={id} warm={warm} />
          <Clock t={t} minute={minute} />
          <Curtain t={t} />
          <Monitor t={t} bpm={bpm} />
          <IVStand t={t} />
          <Lamp on={lamp} />
        </svg>
        {chair && <Chair />}
      </World>
    </>
  );
};

/* ---------- light: lamp cone, monitor glow, moonlight, dust, warm rise ---------- */
export const RoomLight: React.FC<{t: number; cam: Cam; lamp?: number; warm?: number; bpm?: number}> = ({t, cam, lamp = 1, warm = 0, bpm = 104}) => {
  const fl = beepFlash(t, bpm);
  return (
    <World cam={cam} style={{pointerEvents: 'none'}}>
      <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680, mixBlendMode: 'screen'}} viewBox="-500 -300 2920 1680">
        <defs>
          <radialGradient id="lampPool">
            <stop offset="0" stopColor={P.lampWarm} stopOpacity="0.55" />
            <stop offset="0.45" stopColor={P.lampWarm} stopOpacity="0.16" />
            <stop offset="1" stopColor={P.lampWarm} stopOpacity="0" />
          </radialGradient>
          <linearGradient id="lampCone" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FFD38C" stopOpacity="0.42" />
            <stop offset="1" stopColor="#FFD38C" stopOpacity="0" />
          </linearGradient>
          <radialGradient id="monPool">
            <stop offset="0" stopColor={P.monitor} stopOpacity="0.36" />
            <stop offset="1" stopColor={P.monitor} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="warmRise">
            <stop offset="0" stopColor="#F6B868" stopOpacity="0.5" />
            <stop offset="0.6" stopColor="#E0A040" stopOpacity="0.18" />
            <stop offset="1" stopColor="#E0A040" stopOpacity="0" />
          </radialGradient>
          <linearGradient id="moonSlat" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#9CC3E8" stopOpacity="0.0" />
            <stop offset="0.3" stopColor="#9CC3E8" stopOpacity="0.2" />
            <stop offset="1" stopColor="#9CC3E8" stopOpacity="0" />
          </linearGradient>
        </defs>
        <g opacity={lamp}>
          <ellipse cx={LAMP.x} cy={LAMP.y + 40} rx={460} ry={380} fill="url(#lampPool)" />
          <path d={`M${LAMP.x - 40} ${LAMP.y} L${LAMP.x + 42} ${LAMP.y} L${LAMP.x + 300} 800 L${LAMP.x - 260} 800 Z`} fill="url(#lampCone)" />
          <ellipse cx={LAMP.x + 30} cy={800} rx={300} ry={40} fill={P.lampWarm} opacity={0.12} />
        </g>
        <circle cx={430} cy={330} r={300 + fl * 30} fill="url(#monPool)" opacity={0.7 + fl * 0.3} />
        {/* moonlight through the open strip of the window onto the floor */}
        {[0, 1, 2].map((i) => (
          <path key={i} d={`M${1450 + i * 120} 570 L${1530 + i * 120} 570 L${1300 + i * 120} 1080 L${1180 + i * 120} 1080 Z`} fill="url(#moonSlat)" />
        ))}
        {/* dust motes in the lamp cone */}
        <g opacity={lamp}>
          {Array.from({length: 16}).map((_, i) => {
            const px = LAMP.x - 140 + ((i * 61) % 300) + Math.sin(t * 0.5 + i) * 18;
            const py = 420 + ((i * 37 + t * 14 * (1 + (i % 3) * 0.3)) % 340);
            const a = 0.3 + 0.4 * Math.abs(Math.sin(t + i * 2));
            return <circle key={i} cx={px} cy={py} r={i % 4 === 0 ? 3 : 2} fill="#FFE2AA" opacity={a} />;
          })}
        </g>
        {warm > 0 && <ellipse cx={900} cy={520} rx={1400} ry={1000} fill="url(#warmRise)" opacity={warm} />}
      </svg>
    </World>
  );
};

/* ---------- foreground silhouettes (parallax 1.35) ---------- */
export const RoomFG: React.FC<{t: number; cam: Cam}> = ({t, cam}) => (
  <World cam={cam} depth={1.35} style={{pointerEvents: 'none'}}>
    <svg style={{position: 'absolute', left: -500, top: -300, width: 2920, height: 1680, overflow: 'visible'}} viewBox="-500 -300 2920 1680">
      {/* bedside trolley, bottom left */}
      <rect x={-120} y={930} width={330} height={400} rx={18} fill="#0F1C2E" />
      <rect x={-120} y={930} width={330} height={5} rx={2} fill={P.monitor} opacity={0.35} />
      <rect x={-120} y={924} width={340} height={18} rx={8} fill="#13243A" />
      <rect x={60} y={872} width={44} height={58} rx={8} fill="#13243A" />
      <rect x={66} y={880} width={32} height={20} rx={4} fill="#1C3350" />
      <rect x={124} y={896} width={70} height={30} rx={8} fill="#102036" />
      {/* hanging privacy curtain edge, right */}
      <path d={`M1880 -300 L2200 -300 L2200 1400 L1900 1400 Q${1866 + Math.sin(t * 0.6) * 6} 600 1880 -300 Z`} fill="#0A1626" />
      <path d={`M1930 -300 L1950 -300 Q${1928 + Math.sin(t * 0.6) * 6} 600 1946 1400 L1926 1400 Q${1910 + Math.sin(t * 0.6) * 6} 600 1930 -300 Z`} fill="#11223A" />
    </svg>
  </World>
);

/** full-screen darkness with a soft spotlight hole at screen point (sx, sy) */
export const Spotlight: React.FC<{sx: number; sy: number; r: number; dark: number; tint?: string}> = ({sx, sy, r, dark, tint = '4,8,22'}) => (
  <div
    style={{
      position: 'absolute',
      inset: 0,
      pointerEvents: 'none',
      background: `radial-gradient(circle at ${sx}px ${sy}px, rgba(${tint},0) ${r * 0.55}px, rgba(${tint},${dark * 0.6}) ${r}px, rgba(${tint},${dark}) ${r * 1.7}px)`,
    }}
  />
);

export const mix = (a: number, b: number, p: number) => lerp(a, b, clamp(p));
