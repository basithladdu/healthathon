/* B part 2: "Back in August". The desk calendar flips to AUG, pull back to the team at a table.
   Dr Sujay stands and talks; Basith, Awaiz, Dr Sharada and Shirin sit across the table.
   "patients say what they want": a note pops and fills with handwriting; "none of it gets written down": the lines blow away. */
import React from 'react';
import {AbsoluteFill} from 'remotion';
import {P, FONT_HEAD, FONT_BODY, at, camAt, World, Person, CamKey, Expr, clamp, lerp, ramp, mv, pop, spring, easeOutCubic, easeInCubic} from '../kit';
import {CAST} from '../cast';
import {NameTag, Who} from '../team';
import {Motes, LaptopBack, Plant, FgLeaves, scribble} from './B_parts';

export const TT = {
  back: at('s05', 'Back'),
  august: at('s05', 'August'),
  dr: at('s05', 'Dr'),
  sujay: at('s05', 'Sujay'),
  told: at('s05', 'told'),
  patients: at('s05', 'patients'),
  say: at('s05', 'say'),
  what: at('s05', 'what'),
  they: at('s05', 'they'),
  want: at('s05', 'want'),
  none: at('s05', 'none'),
  gets: at('s05', 'gets'),
  written: at('s05', 'written'),
  down: at('s05', 'down'),
};

const FLOOR = 905;
const TABLE_TOP = 742;
const TABLE_L = 450;
const TABLE_R = 1500;
const SEAT_Y = 768;
const SEAT_S = 0.92;
const DOC = {x: 296, y: FLOOR};
const CAL = {x: 548, y: TABLE_TOP};
type Seat = {who: Who; cast: string; x: number};
const SEATS: Seat[] = [
  {who: 'basith', cast: 'engA', x: 668},
  {who: 'awaiz', cast: 'engB', x: 900},
  {who: 'sharada', cast: 'sharada', x: 1132},
  {who: 'shirin', cast: 'medStudent', x: 1364},
];
const NOTE = {x: 960, y: 318, w: 400, h: 290};
const T_BLOW = TT.gets - 0.06;

/* ---------- camera ---------- */
const KEYS: CamKey[] = [
  {t: TT.back - 0.1, x: CAL.x, y: CAL.y - 52, z: 3.3},
  {t: TT.august + 0.34, x: CAL.x + 4, y: CAL.y - 50, z: 3.15},
  {t: TT.dr + 0.5, x: 900, y: 560, z: 1.14},
  {t: TT.told, x: 905, y: 556, z: 1.16},
  {t: TT.patients - 0.1, x: 920, y: 560, z: 1.1},
  {t: TT.patients + 0.45, x: 846, y: 470, z: 1.32},
  {t: TT.none - 0.05, x: 850, y: 468, z: 1.37},
  {t: TT.down + 0.1, x: 930, y: 476, z: 1.3},
  {t: 28.42, x: 820, y: 580, z: 1.16},
];
const roomCam = (t: number) => {
  const c = camAt(KEYS, t, 0.004);
  c.x += Math.sin(t * 0.31) * 6;
  c.y += Math.cos(t * 0.27) * 4;
  return c;
};

/* ---------- room ---------- */
const Wall: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -1600, top: -1400, width: 5200, height: 3000, background: P.creamWarm}} />
    <div style={{position: 'absolute', left: -1600, top: 640, width: 5200, height: 900, background: P.sand}} />
    <div style={{position: 'absolute', left: -1600, top: 632, width: 5200, height: 14, background: '#E4D2B7'}} />
    {/* painted arch behind the table */}
    <div style={{position: 'absolute', left: 470, top: 40, width: 1010, height: 600, borderRadius: '505px 505px 0 0', background: P.sageLight}} />
    {/* window */}
    <div style={{position: 'absolute', left: 1600, top: 120, width: 420, height: 470, borderRadius: 24, background: P.white, padding: 16, boxSizing: 'border-box'}}>
      <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: 10, overflow: 'hidden', background: 'linear-gradient(180deg,#BFDDEE 0%,#E3EEF0 62%,#F6E9D4 100%)'}}>
        <svg width={388} height={438} style={{position: 'absolute', left: 0, top: 0}}>
          {[0, 1].map((k) => {
            const x = ((k * 220 + t * (8 + k * 4)) % 520) - 80;
            return (
              <g key={k} transform={`translate(${x} ${70 + k * 90}) scale(${0.8 - k * 0.2})`} fill="#FFFFFF">
                <ellipse cx={0} cy={0} rx={60} ry={24} />
                <ellipse cx={-30} cy={-10} rx={34} ry={26} />
                <ellipse cx={22} cy={-18} rx={36} ry={30} />
              </g>
            );
          })}
          <rect x={30} y={290} width={90} height={160} fill="#E6D3BC" />
          <rect x={250} y={260} width={100} height={190} fill="#DCC3AA" />
          <g transform={`rotate(${Math.sin(t * 1.1) * 1.5} 190 440)`}>
            <rect x={180} y={330} width={20} height={110} fill="#7A5A44" />
            <circle cx={190} cy={300} r={66} fill={P.sage} />
            <circle cx={150} cy={336} r={44} fill={P.sageDark} />
          </g>
        </svg>
        <div style={{position: 'absolute', left: 186, top: 0, width: 16, height: '100%', background: P.white}} />
        <div style={{position: 'absolute', left: 0, top: 210, width: '100%', height: 16, background: P.white}} />
      </div>
    </div>
    {/* shelf with plant on the left */}
    <div style={{position: 'absolute', left: -60, top: 300, width: 380, height: 20, borderRadius: 6, background: '#C99A62'}} />
    {[
      [-30, 54, 130, P.indigo],
      [30, 44, 110, P.rose],
      [80, 56, 146, P.marigold],
    ].map(([x, w, h, c], i) => (
      <div key={i} style={{position: 'absolute', left: x as number, top: 300 - (h as number), width: w as number, height: h as number, borderRadius: 6, background: c as string}} />
    ))}
    <Plant x={250} y={304} s={0.5} t={t} seed={3} />
  </>
);

const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -1600, top: FLOOR - 4, width: 5200, height: 1200, background: '#E2CDB0'}} />
    <div style={{position: 'absolute', left: -1600, top: FLOOR - 14, width: 5200, height: 16, background: '#D8BF9C'}} />
    {[960, 1040, 1160].map((y, i) => (
      <div key={y} style={{position: 'absolute', left: -1600, top: y, width: 5200, height: 3 + i, background: 'rgba(160,120,80,0.16)'}} />
    ))}
    <div style={{position: 'absolute', left: TABLE_L - 30, top: FLOOR - 10, width: TABLE_R - TABLE_L + 60, height: 24, borderRadius: '50%', background: 'rgba(120,80,50,0.16)'}} />
  </>
);

const Lamp: React.FC<{t: number}> = ({t}) => {
  const sw = Math.sin(t * 1.1) * 1.4;
  return (
    <div style={{position: 'absolute', left: 960, top: -900, width: 0, height: 0, transform: `rotate(${sw}deg)`, transformOrigin: '0 0'}}>
      <div style={{position: 'absolute', left: -3, top: 0, width: 6, height: 980, background: '#6B5A4E'}} />
      <svg style={{position: 'absolute', left: -460, top: 980, overflow: 'visible'}} width={920} height={900}>
        <defs>
          <linearGradient id="bcone" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FFE4A8" stopOpacity={0.42} />
            <stop offset="1" stopColor="#FFE4A8" stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d="M400 60 L520 60 L860 820 L60 820 Z" fill="url(#bcone)" />
        <path d="M396 0 L524 0 L590 66 L330 66 Z" fill={P.marigold} />
        <path d="M480 0 L524 0 L590 66 L520 66 Z" fill={P.marigoldDeep} />
        <ellipse cx={460} cy={68} rx={42} ry={13} fill="#FFF3D6" />
      </svg>
    </div>
  );
};

const Table: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: TABLE_L, top: TABLE_TOP, width: TABLE_R - TABLE_L, height: 26, borderRadius: 8, background: '#C98F55'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 24, width: TABLE_R - TABLE_L - 40, height: 84, background: '#B37C46'}} />
    <div style={{position: 'absolute', left: TABLE_L + 20, top: TABLE_TOP + 92, width: TABLE_R - TABLE_L - 40, height: 16, background: '#9C6A3B'}} />
    {[TABLE_L + 34, TABLE_R - 66].map((x) => (
      <div key={x} style={{position: 'absolute', left: x, top: TABLE_TOP + 106, width: 32, height: FLOOR - TABLE_TOP - 106, background: '#9C6A3B'}} />
    ))}
    {/* things on the table */}
    <div style={{position: 'absolute', left: 1180, top: TABLE_TOP - 14, width: 130, height: 16, borderRadius: 4, background: P.white, transform: 'rotate(-3deg)'}} />
    <div style={{position: 'absolute', left: 1200, top: TABLE_TOP - 22, width: 90, height: 10, borderRadius: 3, background: P.mint, transform: 'rotate(-3deg)'}} />
    <div style={{position: 'absolute', left: 1416, top: TABLE_TOP - 46, width: 40, height: 46, borderRadius: '4px 4px 12px 12px', background: P.rose}} />
    <div style={{position: 'absolute', left: 1446, top: TABLE_TOP - 38, width: 22, height: 22, borderRadius: 11, border: `6px solid ${P.rose}`, boxSizing: 'border-box'}} />
    <svg style={{position: 'absolute', left: 1406, top: TABLE_TOP - 130, overflow: 'visible'}} width={60} height={80}>
      {[0, 1].map((k) => {
        const ph = (t * 0.7 + k * 0.5) % 1;
        return <path key={k} d={`M${18 + k * 18} 80 q 10 -20 0 -40 q -10 -20 0 -40`} stroke="rgba(255,255,255,0.85)" strokeWidth={5} fill="none" strokeLinecap="round" opacity={Math.sin(ph * Math.PI)} transform={`translate(0 ${-ph * 20})`} />;
      })}
    </svg>
  </>
);

/** desk calendar: JUL flips over to AUG on "August" */
const DeskCal: React.FC<{t: number}> = ({t}) => {
  const w = 132;
  const h = 108;
  const t0 = TT.august - 0.04;
  const p = ramp(t, t0, t0 + 0.3);
  const land = t > t0 + 0.3 ? Math.exp(-(t - t0 - 0.3) * 8) * Math.sin((t - t0 - 0.3) * 26) * 0.09 : 0;
  const page = (m: string, bg: string) => (
    <div style={{position: 'absolute', inset: 0, borderRadius: 9, background: P.white, overflow: 'hidden'}}>
      <div style={{height: 34, background: bg, color: P.white, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 17, textAlign: 'center', lineHeight: '36px'}}>2026</div>
      <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 48, color: P.ink, textAlign: 'center', lineHeight: '70px'}}>{m}</div>
    </div>
  );
  return (
    <div style={{position: 'absolute', left: CAL.x - w / 2, top: CAL.y - h - 4, width: w, height: h, transform: `scale(${1 + land},${1 - land})`, transformOrigin: '50% 100%'}}>
      <div style={{position: 'absolute', left: -10, top: 12, width: w + 20, height: h - 6, borderRadius: 12, background: '#8A6A50'}} />
      {page('AUG', P.rose)}
      {p < 0.5 && <div style={{position: 'absolute', inset: 0, transform: `scaleY(${Math.cos(p * Math.PI)})`, transformOrigin: '50% 0%'}}>{page('JUL', P.indigo)}</div>}
      {p >= 0.5 && p < 1 && <div style={{position: 'absolute', left: 0, top: -h, width: w, height: h, borderRadius: 9, background: '#E8E2DA', transform: `scaleY(${-Math.cos(p * Math.PI)})`, transformOrigin: '50% 100%'}} />}
      {[26, 61, 96].map((rx) => (
        <div key={rx} style={{position: 'absolute', left: rx, top: -9, width: 11, height: 20, borderRadius: 5, background: '#5E5249'}} />
      ))}
      {/* flip whoosh */}
      {t > t0 && t < t0 + 0.55 && (
        <svg style={{position: 'absolute', left: -60, top: -80, overflow: 'visible'}} width={1} height={1}>
          {[0, 1, 2].map((k) => (
            <path key={k} d={`M ${20 + k * 70} ${40 - k * 6} q 30 -30 60 0`} stroke={P.marigold} strokeWidth={6} fill="none" strokeLinecap="round" opacity={1 - ramp(t, t0 + 0.2, t0 + 0.55)} transform={`translate(0 ${-easeOutCubic(ramp(t, t0, t0 + 0.5)) * 30})`} />
          ))}
        </svg>
      )}
    </div>
  );
};

/* ---------- people ---------- */
const seatPose = (k: number, t: number) => {
  const p: {expr: Expr; look: number; headTilt: number; armL: number; armR: number; elbowL: number; elbowR: number; lean: number} = {expr: 'neutral', look: -1, headTilt: -3, armL: 10, armR: 10, elbowL: -40, elbowR: -40, lean: 0};
  // listening to Dr Sujay (on the left)
  p.lean = Math.sin(t * 1.3 + k) * 1.5;
  if (t > TT.told) p.expr = 'happy';
  if (t > TT.patients + 0.1) {
    // look up at the note
    p.look = (960 - SEATS[k].x) / 300;
    p.headTilt = -6 + k;
    p.expr = 'happy';
  }
  if (t > T_BLOW + 0.12 + k * 0.05) {
    p.look = 1;
    p.expr = 'surprised';
    const w = spring(t, T_BLOW + 0.12 + k * 0.05, 12, 0.45);
    p.lean = 6 * w;
  }
  if (t > TT.down + 0.1 + k * 0.06) {
    p.expr = k % 2 ? 'sad' : 'worried';
    p.look = 0.2;
    p.lean = 2;
    p.headTilt = 6;
  }
  // engineers type a little, then rest their hands on the table
  if (k < 2 && t < TT.patients) {
    const typ = Math.sin(t * 18 + k * 2) * 3 * ramp(t, TT.august, TT.august + 0.5);
    p.armL += typ;
    p.armR -= typ;
  }
  return p;
};
const docPose = (t: number) => {
  let armR = 20 + Math.sin(t * 4.1) * 8;
  let elbowR = -55;
  let armL = 8;
  let elbowL = -10;
  let expr: Expr = 'talk';
  let lean = 0;
  let headTilt = -2;
  const talk = t > TT.told - 0.02 && t < TT.down + 0.3 && !(t > TT.want + 0.35 && t < TT.none - 0.05);
  // "patients say what they want": open palm up to the note
  const pal = spring(t, TT.patients - 0.05, 10, 0.5) * (1 - mv(t, TT.none - 0.1, TT.none + 0.15));
  armR = lerp(armR, 132, pal);
  elbowR = lerp(elbowR, -22, pal);
  // "none of it": both hands out, a shrug
  const sh = spring(t, TT.none, 11, 0.5) * (1 - mv(t, TT.down + 0.2, TT.down + 0.6));
  armR = lerp(armR, 58, sh);
  elbowR = lerp(elbowR, -80, sh);
  armL = lerp(armL, 58, sh);
  elbowL = lerp(elbowL, -80, sh);
  if (t > TT.none) {
    expr = 'sad';
    headTilt = 4 * sh;
  }
  if (t < TT.told) expr = 'neutral';
  if (t > TT.down + 0.3) expr = 'worried';
  lean = sh * -2;
  return {armR, elbowR, armL, elbowL, expr, talk, lean, headTilt};
};

const People: React.FC<{t: number; glow: number}> = ({t, glow}) => {
  const d = docPose(t);
  return (
    <>
      {SEATS.map((s, k) => (
        <div key={'c' + k} style={{position: 'absolute', left: s.x - 96, top: 548, width: 192, height: 200, borderRadius: 32, background: '#5A4F6E', boxShadow: 'inset -18px 0 0 rgba(0,0,0,0.12)'}} />
      ))}
      {SEATS.map((s, k) => (
        <Person key={s.who} {...CAST[s.cast]} t={t} x={s.x} y={SEAT_Y} s={SEAT_S} sitting {...seatPose(k, t)} />
      ))}
      <LaptopBack x={SEATS[0].x} w={150} base={TABLE_TOP + 2} glow={glow} sticker={P.rose} />
      <LaptopBack x={SEATS[1].x} w={150} base={TABLE_TOP + 2} glow={glow} sticker={P.mint} />
      <Table t={t} />
      <DeskCal t={t} />
      <Person {...CAST.teamDoctor} t={t} x={DOC.x} y={DOC.y} s={1.0} look={0.9} {...d} />
    </>
  );
};

/** name tags: Dr Sujay's stays while he talks, the others pop briefly */
const Tags: React.FC<{t: number}> = ({t}) => {
  const sIn = pop(t, TT.sujay - 0.05, 13, 0.45);
  const sOut = 1 - easeInCubic(ramp(t, TT.none + 0.3, TT.none + 0.6));
  const tags: {who: Who; x: number; y: number; a: number}[] = [{who: 'sujay', x: DOC.x + 66, y: 400, a: sIn * sOut}];
  SEATS.forEach((s, k) => {
    const a = pop(t, TT.told - 0.25 + k * 0.12, 13, 0.45) * (1 - easeInCubic(ramp(t, TT.patients - 0.35 + k * 0.04, TT.patients - 0.05 + k * 0.04)));
    tags.push({who: s.who, x: s.x - 98, y: 418, a});
  });
  return (
    <>
      {tags.map((g) => (
        <NameTag key={g.who} who={g.who} x={g.x} y={g.y} a={g.a} size={0.92} />
      ))}
    </>
  );
};

/* ---------- the note ---------- */
const INK = '#33405E';
const LINE_T = [TT.say, TT.what, TT.they, TT.want];
const LINES = [0, 1, 2, 3].map((k) => {
  const x0 = NOTE.x - NOTE.w / 2 + 34;
  const len = [300, 250, 318, 200][k];
  const y0 = NOTE.y - NOTE.h / 2 + 120 + k * 42;
  return {d: scribble(x0, y0, len, k), x0, len, y0};
});
const Note: React.FC<{t: number}> = ({t}) => {
  if (t < TT.patients - 0.05) return null;
  const s = pop(t, TT.patients - 0.05, 13, 0.42);
  // after the blow, the blank note sags a little
  const sag = mv(t, TT.down, TT.down + 0.5);
  const flutter = t > T_BLOW && t < T_BLOW + 1 ? Math.sin((t - T_BLOW) * 30) * 4 * (1 - ramp(t, T_BLOW, T_BLOW + 1)) : 0;
  const rot = -2 + (1 - Math.min(1, s)) * 12 + flutter + sag * 5 + Math.sin(t * 1.4) * 0.8;
  return (
    <div style={{position: 'absolute', left: NOTE.x - NOTE.w / 2, top: NOTE.y - NOTE.h / 2 + sag * 14, width: NOTE.w, height: NOTE.h, transform: `scale(${s}) rotate(${rot}deg)`, transformOrigin: '50% 0%'}}>
      <div style={{position: 'absolute', inset: 0, background: P.white, borderRadius: 8, boxShadow: '0 10px 0 rgba(56,46,41,0.10)'}}>
        <div style={{position: 'absolute', left: 34, top: 22, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 38, color: P.rose}}>What they want</div>
        {[0, 1, 2, 3].map((k) => (
          <div key={k} style={{position: 'absolute', left: 30, right: 30, top: 124 + k * 42, height: 2, background: '#DCE6EE'}} />
        ))}
      </div>
      <div style={{position: 'absolute', left: NOTE.w / 2 - 14, top: -12, width: 28, height: 28, borderRadius: 14, background: P.marigold, boxShadow: 'inset 0 -5px 0 #B07A2E'}} />
    </div>
  );
};
/** the handwriting (in world coords, drawn over the note) and the leaves it turns into */
const Writing: React.FC<{t: number}> = ({t}) => {
  if (t < LINE_T[0] - 0.1 || t > T_BLOW + 2) return null;
  const sag = mv(t, TT.down, TT.down + 0.5);
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      <g transform={`rotate(-2 ${NOTE.x} ${NOTE.y - NOTE.h / 2}) translate(0 ${sag * 14})`}>
        {LINES.map((L, k) => {
          const p = easeOutCubic(ramp(t, LINE_T[k] - 0.04, LINE_T[k] + 0.32));
          const b0 = T_BLOW + k * 0.08;
          const q = ramp(t, b0, b0 + 0.45);
          if (p <= q) return null;
          return <path key={k} d={L.d} pathLength={1} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={`0 ${q} ${p - q} 3`} />;
        })}
      </g>
      {LINES.map((L, k) =>
        Array.from({length: 9}, (_, j) => {
          const u = (j + 0.5) / 9;
          const ts = T_BLOW + k * 0.08 + u * 0.45;
          const dt = t - ts;
          if (dt < 0 || dt > 1.5) return null;
          const x = L.x0 + u * L.len + 620 * dt + 60 * Math.sin(dt * 7 + j) * dt;
          const y = L.y0 - 6 - 230 * dt + 140 * dt * dt + 28 * Math.sin(dt * 9 + j + k);
          const r = dt * 420 * (j % 2 ? 1 : -1) + j * 30;
          const o = 1 - ramp(dt, 0.9, 1.5);
          const sc = 1 + 0.5 * Math.sin(dt * 12 + j);
          return <ellipse key={k * 10 + j} cx={0} cy={0} rx={15} ry={6 * sc} fill={j % 3 === 0 ? P.sage : INK} opacity={o} transform={`translate(${x} ${y}) rotate(${r})`} />;
        }),
      )}
      {[0, 1, 2].map((g) => {
        const u = ramp(t, T_BLOW - 0.15 + g * 0.1, T_BLOW + 0.75 + g * 0.1);
        if (u <= 0 || u >= 1) return null;
        const y = NOTE.y - 70 + g * 70;
        const x = NOTE.x - 520 + u * 1050;
        return (
          <path
            key={'w' + g}
            d={`M${x} ${y} q 60 -30 120 0 t 120 0 q 30 -24 4 -40`}
            stroke="rgba(127,179,217,0.85)"
            strokeWidth={8}
            fill="none"
            strokeLinecap="round"
            opacity={Math.sin(u * Math.PI)}
          />
        );
      })}
    </svg>
  );
};

/* ---------- the room ---------- */
export const TableRoom: React.FC<{t: number}> = ({t}) => {
  const cam = roomCam(t);
  const glow = 0.55 + 0.1 * Math.sin(t * 6);
  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      <World cam={cam} depth={0.9}>
        <Wall t={t} />
      </World>
      <World cam={cam}>
        <Floor />
        <Lamp t={t} />
        <Motes t={t} x0={300} w={1400} y0={120} h={700} n={26} seed="tm" />
        <People t={t} glow={glow} />
        <Plant x={1640} y={FLOOR} s={1.05} t={t} seed={5} pot={P.plum} />
        <Note t={t} />
        <Writing t={t} />
        <Tags t={t} />
      </World>
      <World cam={cam} depth={1.3}>
        <FgLeaves x={-120} y={1180} t={t} s={1.1} />
        <FgLeaves x={2060} y={1200} t={t} flip s={1.0} />
      </World>
    </AbsoluteFill>
  );
};
