/* Scene I 83.1-end: three months, six hundred commits, doctors who kept saying no,
   live on Google Play, title card. */
import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';
import {at, camAt, clamp, easeInOutCubic, easeOutCubic, easeOutQuint, FONT_BODY, FONT_HEAD, lerp, mv, P, Person, pop, ramp, spring, TOTAL_SEC, useGT, World} from '../kit';
import {CAST} from '../cast';
import {IconCheck, Motes, PhoneSeq, Plant, toScreen} from './E_parts';

const T = {
  three: at('s20', 'Three'),
  months: at('s20', 'months'),
  six: at('s20', 'Six'),
  commits: at('s20', 'commits'),
  doctors: at('s20', 'Doctors'),
  kept: at('s20', 'kept'),
  telling: at('s20', 'telling'),
  us: at('s20', 'us'),
  no: at('s20', 'no'),
  live: at('s21', "It's"),
  google: at('s21', 'Google'),
  today: at('s21', 'today'),
  sahara: at('s22', 'Sahara'),
  care: at('s22', 'Care'),
};
const TAG = ['Care', 'that', 'follows', 'what', 'matters', 'to', 'the', 'patient'];
const TAG_T = TAG.map((w) => at('s22', w));

const MONTHS = [
  {m: 'AUG', c: P.rose},
  {m: 'SEP', c: P.marigold},
  {m: 'OCT', c: P.plum},
  {m: 'NOV', c: P.coral},
];

const hash = (i: number) => Math.abs(Math.sin(i * 91.17 + 7.3) * 43758.5453) % 1;
const COLS = 40;
const ROWS = 15;
const ORDER = (() => {
  const idx = Array.from({length: COLS * ROWS}).map((_, i) => i);
  // mostly left-to-right (time), with jitter
  const key = (i: number) => Math.floor(i / ROWS) + hash(i) * 6;
  const cells = idx.map((i) => ({i, k: key(i)}));
  cells.sort((a, b) => a.k - b.k);
  const rank: number[] = [];
  cells.forEach((c, r) => (rank[c.i] = r));
  return rank;
})();
const GREENS = [P.mint, '#6FAE92', P.sageLight, '#B8DCC8', P.marigold];

const Thumb: React.FC<{skin: string}> = ({skin}) => <rect x={-8} y={-40} width={16} height={32} rx={8} fill={skin} />;

export const Scene: React.FC = () => {
  const t = useGT();
  const cam = camAt(
    [
      {t: 83.1, x: 960, y: 560, z: 1.12},
      {t: 84.25, x: 960, y: 540, z: 1.0},
      {t: 84.85, x: 960, y: 1640, z: 1.0},
      {t: 85.4, x: 960, y: 1650, z: 1.05},
      {t: 85.95, x: 960, y: 2740, z: 1.0},
      {t: 87.6, x: 960, y: 2740, z: 1.04},
      {t: 88.3, x: 960, y: 2720, z: 1.0},
      {t: 89.6, x: 960, y: 2720, z: 1.03},
    ],
    t,
    0.003,
  );

  /* calendar */
  const calRise = easeOutQuint(ramp(t, T.three - 0.15, T.three + 0.4));

  /* commits */
  const fillP = (r: number) => T.six + 0.05 + (r / (COLS * ROWS)) * 1.3;
  const count = Math.round(600 * easeOutCubic(ramp(t, T.six + 0.05, T.six + 1.35)));

  /* doctors */
  const shake = (seed: number) => {
    const a = T.doctors + 0.35 + seed * 0.1;
    const x = t - a;
    return x > 0 && x < 1.3 ? Math.sin(x * 16) * 10 * (1 - x / 1.3) : 0;
  };
  const thumbs = mv(t, T.no + 0.05, T.no + 0.35, easeOutCubic);
  const apart = mv(t, T.live - 0.1, T.live + 0.5);
  const nos = [
    {t: T.kept, x: 330, y: 2410, r: -8},
    {t: T.telling, x: 640, y: 2330, r: 6},
    {t: T.us, x: 1290, y: 2330, r: -5},
    {t: T.no, x: 1590, y: 2410, r: 8},
  ];
  const flipNo = (i: number) => clamp((t - (T.no + 0.15 + i * 0.07)) / 0.25);

  /* phone + pill */
  const phUp = spring(t, T.live - 0.05, 9, 0.6);
  const phY = 2720 + (1 - phUp) * 1100;
  const pill = pop(t, T.google, 12, 0.45);

  /* title */
  const wipe = easeInOutCubic(ramp(t, T.sahara - 0.5, T.sahara - 0.12));
  const [wx, wy] = toScreen(cam, 960, 2660);
  const logoP = pop(t, T.sahara - 0.2, 10, 0.5);
  const ringP = ramp(t, T.sahara - 0.1, T.sahara + 1.0);
  const creep = 1 + 0.035 * ramp(t, T.sahara, TOTAL_SEC);
  const word = 'Sahara';
  const footT = TAG_T[TAG_T.length - 1] + 0.55;

  return (
    <AbsoluteFill style={{background: P.sage, overflow: 'hidden'}}>
      {/* background parallax shapes */}
      <World cam={cam} depth={0.4}>
        {[
          [200, 300, 260, P.sageDark],
          [1700, 800, 320, P.band],
          [300, 1200, 300, P.band],
          [1650, 1500, 240, P.sageDark],
          [250, 1950, 340, P.sageDark],
          [1700, 2100, 280, P.band],
        ].map(([x, y, r, c], i) => (
          <div key={i} style={{position: 'absolute', left: (x as number) - (r as number), top: (y as number) - (r as number), width: (r as number) * 2, height: (r as number) * 2, borderRadius: '50%', background: c as string, transform: `translateY(${Math.sin(t * 0.5 + i) * 14}px)`}} />
        ))}
      </World>
      <World cam={cam} depth={0.7}>
        <Motes t={t} n={40} x0={-200} y0={-200} w={2300} h={3600} color="rgba(255,248,238,0.4)" seed={11} />
      </World>
      <World cam={cam}>
        {/* ---------- three months ---------- */}
        <div style={{position: 'absolute', left: 0, top: 170, width: 1920, display: 'flex', justifyContent: 'center'}}>
          <Rise2 t={t} t0={83.12} text="Three months" size={84} />
        </div>
        <div style={{position: 'absolute', left: 300, top: 790, width: 1320, height: 10, borderRadius: 5, background: 'rgba(255,248,238,0.2)'}} />
        <div style={{position: 'absolute', left: 300, top: 790, width: 1320 * mv(t, 83.1, T.months + 0.8), height: 10, borderRadius: 5, background: P.marigold}} />
        {MONTHS.map((mo, i) => (
          <div key={'ph' + i} style={{position: 'absolute', left: 450 + i * 340 - 150, top: 360, width: 300, height: 350, borderRadius: 24, background: 'rgba(255,248,238,0.1)'}} />
        ))}
        {MONTHS.map((mo, i) => {
          const t0 = i === 0 ? 82.6 : T.three - 0.08 + i * 0.2;
          const f = i === 0 ? 1 : spring(t, t0, 11, 0.5);
          const x = 450 + i * 340;
          const done = t > t0 + 0.6 && i < 3;
          return (
            <div key={mo.m} style={{position: 'absolute', left: x - 150, top: 360 + (1 - calRise) * 60, width: 300, height: 350, perspective: 900}}>
              <div style={{position: 'absolute', inset: 0, transformOrigin: '50% 0%', transform: `rotateX(${(1 - f) * -95}deg)`, opacity: f > 0.02 ? 1 : 0}}>
                <div style={{position: 'absolute', inset: 0, borderRadius: 24, background: P.cream, boxShadow: '0 20px 40px rgba(10,30,20,0.3)', overflow: 'hidden'}}>
                  <div style={{height: 110, background: mo.c, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: P.white, letterSpacing: 4}}>{mo.m}</div>
                  <div style={{display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 9, padding: 22}}>
                    {Array.from({length: 28}).map((_, k) => (
                      <div key={k} style={{height: 22, borderRadius: 6, background: hash(k + i * 31) > 0.45 ? mo.c : P.sand, opacity: hash(k + i * 31) > 0.45 ? 0.75 : 1}} />
                    ))}
                  </div>
                </div>
                {done && (
                  <div style={{position: 'absolute', right: -16, top: -16, width: 52, height: 52, borderRadius: '50%', background: P.sageDark, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${pop(t, t0 + 0.6, 14, 0.45)})`}}>
                    <IconCheck s={34} color={P.white} />
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {/* ---------- six hundred commits ---------- */}
        <div style={{position: 'absolute', left: 0, top: 1210, width: 1920, display: 'flex', justifyContent: 'center', alignItems: 'baseline', gap: 26, transform: `scale(${pop(t, T.six - 0.1, 12, 0.5)})`}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 150, color: P.cream, fontVariantNumeric: 'tabular-nums', minWidth: 300, textAlign: 'right', lineHeight: 1}}>{count}</div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 72, color: P.mint, lineHeight: 1}}>commits</div>
        </div>
        <div style={{position: 'absolute', left: 316, top: 1420}}>
          {Array.from({length: COLS * ROWS}).map((_, i) => {
            const c = Math.floor(i / ROWS);
            const r = i % ROWS;
            const ft = fillP(ORDER[i]);
            const p = pop(t, ft, 18, 0.5);
            const col = GREENS[Math.floor(hash(i * 3.1) * (hash(i) > 0.93 ? 5 : 4))];
            return (
              <div key={i} style={{position: 'absolute', left: c * 32, top: r * 32, width: 26, height: 26, borderRadius: 6, background: 'rgba(255,248,238,0.1)'}}>
                {p > 0 && <div style={{position: 'absolute', inset: 0, borderRadius: 6, background: col, transform: `scale(${p})`}} />}
              </div>
            );
          })}
        </div>

        {/* ---------- doctors who kept saying no ---------- */}
        <div style={{position: 'absolute', left: 60, top: 3130, width: 1800, height: 140, borderRadius: '50%', background: P.band}} />
        <div style={{position: 'absolute', left: 160, top: 3140, width: 1600, height: 100, borderRadius: '50%', background: '#2F5A4B'}} />
        <Plant x={110} y={3200} s={1.2} t={t} pot={P.marigold} leaf="#1F4A3B" leaf2="#2C5E4B" />
        <Plant x={1820} y={3200} s={1.1} t={t} pot={P.rose} leaf="#1F4A3B" leaf2="#2C5E4B" />
        {nos.map((n, i) => {
          const s = pop(t, n.t, 14, 0.42) * (1 - mv(t, T.live, T.live + 0.3));
          if (s <= 0.01) return null;
          const fl = flipNo(i);
          const showCheck = fl > 0.5;
          const sy = Math.abs(Math.cos(fl * Math.PI));
          return (
            <div key={i} style={{position: 'absolute', left: n.x, top: n.y, transform: `translate(-50%,-50%) rotate(${n.r}deg) scale(${s}) scaleY(${sy})`}}>
              <div style={{width: 170, height: 104, borderRadius: 30, background: showCheck ? P.cream : P.rose, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: P.cream, boxShadow: '0 12px 24px rgba(10,30,20,0.25)'}}>
                {showCheck ? <IconCheck s={64} color={P.sage} /> : 'No'}
              </div>
            </div>
          );
        })}
        <Person
          {...CAST.medStudent}
          t={t}
          x={lerp(720, 430, apart)}
          y={3180}
          s={1.25}
          look={0.6}
          expr={t < T.no + 0.05 ? 'think' : 'happy'}
          headTilt={shake(0)}
          armR={lerp(8, 62, thumbs)}
          elbowR={lerp(0, 112, thumbs)}
          armL={lerp(6, 20, thumbs)}
          hold={thumbs > 0.5 ? <Thumb skin={P.skin3} /> : undefined}
        />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={lerp(1200, 1490, apart)}
          y={3180}
          s={1.25}
          flip
          look={0.6}
          expr={t < T.no + 0.05 ? 'think' : 'happy'}
          headTilt={shake(1)}
          armR={lerp(8, 62, thumbs)}
          elbowR={lerp(0, 112, thumbs)}
          hold={thumbs > 0.5 ? <Thumb skin={P.skin4} /> : undefined}
        />
        {/* the live app */}
        {t > T.live - 0.1 && t < T.sahara - 0.25 && (
          <>
            {Array.from({length: 14}).map((_, i) => {
              const a = (i / 14) * Math.PI * 2;
              const p = easeOutCubic(ramp(t, T.live + 0.2, T.live + 1.1));
              const d = 260 + 220 * p;
              return <div key={i} style={{position: 'absolute', left: 960 + Math.cos(a) * d * 1.2 - 8, top: 2700 + Math.sin(a) * d - 8, width: 16, height: 16, borderRadius: i % 2 ? 4 : 8, background: i % 3 ? P.marigold : P.cream, opacity: p > 0 ? 1 - p : 0, transform: `rotate(${i * 30 + t * 90}deg)`}} />;
            })}
            <PhoneSeq t={t} segs={[{clip: 6, at: T.live, clipFrom: 10.75, rate: 0.8}]} x={960} y={phY} h={820} rot={0} noteColor="rgba(255,248,238,0.6)" />
          </>
        )}
        {pill > 0.01 && (
          <div style={{position: 'absolute', left: 1560, top: 2470, transform: `translate(-50%,-50%) scale(${pill}) rotate(${-3 + Math.sin(t * 1.5) * 1}deg)`}}>
            <div style={{display: 'flex', alignItems: 'center', gap: 16, padding: '18px 34px', borderRadius: 999, background: P.cream, boxShadow: '0 16px 30px rgba(10,30,20,0.3)', whiteSpace: 'nowrap'}}>
              <div style={{width: 22, height: 22, borderRadius: '50%', background: P.sage, boxShadow: `0 0 0 ${6 + 4 * Math.sin(t * 5)}px rgba(59,117,98,0.25)`}} />
              <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 58, color: P.sageDark}}>Live on Google Play</div>
            </div>
          </div>
        )}
      </World>

      {/* ---------- title ---------- */}
      {wipe > 0 && (
        <div style={{position: 'absolute', left: wx - 2300 * wipe, top: wy - 2300 * wipe, width: 4600 * wipe, height: 4600 * wipe, borderRadius: '50%', background: P.cream}} />
      )}
      {t > T.sahara - 0.25 && (
        <AbsoluteFill style={{transform: `scale(${creep})`}}>
          {/* soft ambient shapes */}
          <div style={{position: 'absolute', left: -220 + Math.sin(t * 0.3) * 20, top: -260, width: 760, height: 760, borderRadius: '50%', background: P.sageLight, opacity: 0.7 * ramp(t, T.sahara - 0.25, T.sahara + 0.6)}} />
          <div style={{position: 'absolute', right: -260, bottom: -300 + Math.cos(t * 0.3) * 20, width: 820, height: 820, borderRadius: '50%', background: P.roseLight, opacity: 0.8 * ramp(t, T.sahara - 0.15, T.sahara + 0.8)}} />
          <div style={{position: 'absolute', right: 180, top: 120 + Math.sin(t * 0.6) * 10, width: 70, height: 70, borderRadius: '50%', background: P.marigold, opacity: 0.55 * ramp(t, T.sahara + 0.5, T.sahara + 1.4)}} />
          <div style={{position: 'absolute', left: 230, bottom: 210 + Math.cos(t * 0.5) * 10, width: 40, height: 40, borderRadius: '50%', background: P.mint, opacity: 0.8 * ramp(t, T.sahara + 0.7, T.sahara + 1.6)}} />
          {/* lockup */}
          <div style={{position: 'absolute', left: 0, top: 290, width: 1920, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 36}}>
            <div style={{position: 'relative', width: 250, height: 250}}>
              <div style={{position: 'absolute', inset: 0, borderRadius: '50%', border: `6px solid ${P.sage}`, transform: `scale(${0.6 + ringP * 0.9})`, opacity: (1 - ringP) * 0.5}} />
              <Img src={staticFile('brand/sahara-logo.png')} style={{position: 'absolute', left: -25, top: -25, width: 300, height: 300, transform: `scale(${logoP}) rotate(${(1 - logoP) * -14}deg)`}} />
            </div>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 200, color: P.sageDark, lineHeight: 1, display: 'flex'}}>
              {word.split('').map((ch, i) => {
                const p = easeOutQuint(ramp(t, T.sahara + 0.12 + i * 0.05, T.sahara + 0.7 + i * 0.05));
                return (
                  <span key={i} style={{display: 'inline-block', overflow: 'hidden', paddingBottom: '0.1em'}}>
                    <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 120}%)`}}>{ch}</span>
                  </span>
                );
              })}
            </div>
          </div>
          {/* tagline: each word on its spoken word */}
          <div style={{position: 'absolute', left: 0, top: 630, width: 1920, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: P.ink, lineHeight: 1.2}}>
            {TAG.map((w, i) => {
              const p = easeOutQuint(ramp(t, TAG_T[i] - 0.05, TAG_T[i] + 0.45));
              return (
                <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.1em', marginRight: i < TAG.length - 1 ? '0.26em' : 0}}>
                  <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`, color: w === 'matters' ? P.sage : P.ink}}>{w}</span>
                </span>
              );
            })}
          </div>
          <div style={{position: 'absolute', left: 960 - 60 * easeOutQuint(ramp(t, footT - 0.3, footT + 0.4)), top: 800, width: 120 * easeOutQuint(ramp(t, footT - 0.3, footT + 0.4)), height: 6, borderRadius: 3, background: P.marigold}} />
          {/* footer */}
          <div style={{position: 'absolute', left: 0, top: 890, width: 1920, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 28, lineHeight: 1.3, fontFamily: FONT_BODY, fontSize: 30, color: P.inkSub}}>
            {['sahara.wedevit.in', '·', 'Team Zeros and Ones', '·', 'Health-a-thon 2026'].map((s, i) => {
              const p = easeOutQuint(ramp(t, footT + i * 0.08, footT + 0.5 + i * 0.08));
              return (
                <span key={i} style={{display: 'block', overflow: 'hidden', paddingBottom: '0.1em', lineHeight: 1.3}}>
                  <span style={{display: 'block', transform: `translateY(${(1 - p) * 130}%)`, fontWeight: i === 0 ? 600 : 400, color: i === 0 ? P.sageDark : P.inkSub}}>{s}</span>
                </span>
              );
            })}
          </div>
        </AbsoluteFill>
      )}
    </AbsoluteFill>
  );
};

/** word rise in cream, centred */
const Rise2: React.FC<{t: number; t0: number; text: string; size: number}> = ({t, t0, text, size}) => (
  <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: size, color: P.cream, lineHeight: 1.15}}>
    {text.split(' ').map((w, i) => {
      const p = easeOutQuint(ramp(t, t0 + i * 0.12, t0 + i * 0.12 + 0.55));
      return (
        <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.08em', marginRight: '0.26em'}}>
          <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`}}>{w}</span>
        </span>
      );
    })}
  </div>
);

