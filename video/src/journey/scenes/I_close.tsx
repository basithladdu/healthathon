/* Scene I: three months, almost six hundred commits, a survey of doctors and hospitals, doctors who kept
   saying no, live on Google Play today, and the title with the five of us. The camera travels along one
   long sage strip: calendar + commits | survey | Dr Sujay and Dr Sharada | the live app | title. */
import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';
import {at, camAt, clamp, easeInOutCubic, easeOutCubic, easeOutQuint, FONT_BODY, FONT_HEAD, lerp, mv, P, Person, pop, ramp, Rise, spring, TOTAL_SEC, useGT, World} from '../kit';
import {CAST} from '../cast';
import {Face, NameTag, ORDER, PlayBadge, TEAM} from '../team';
import {WIN} from '../windows';
import {IconCheck, Motes, PhoneSeq, Plant, toScreen} from './E_parts';

const I0 = WIN.I[0];
const T = {
  three: at('s20', 'Three'),
  months: at('s20', 'months'),
  almost: at('s20', 'Almost'),
  six: at('s20', 'six'),
  commits: at('s20', 'commits'),
  survey: at('s20', 'survey'),
  docs1: at('s20', 'doctors'),
  hospitals: at('s20', 'hospitals'),
  and2: at('s20', 'And', 1),
  docs2: at('s20', 'doctors', 1),
  kept: at('s20', 'kept'),
  telling: at('s20', 'telling'),
  us: at('s20', 'us'),
  no: at('s20', 'no'),
  live: at('s21', "It's"),
  google: at('s21', 'Google'),
  play: at('s21', 'Play'),
  today: at('s21', 'today'),
  sahara: at('s22', 'Sahara'),
  care: at('s22', 'Care'),
};
const TAG = ['Care', 'that', 'follows', 'what', 'matters', 'to', 'the', 'patient'];
const TAG_T = TAG.map((w) => at('s22', w));

/* station centres along the strip */
const SA = 960;
const SB = 2880;
const SC = 4800;
const SD = 6720;

const MONTHS = [
  {m: 'AUG', c: P.rose},
  {m: 'SEP', c: P.marigold},
  {m: 'OCT', c: P.plum},
  {m: 'NOV', c: P.coral},
];

const hash = (i: number) => Math.abs(Math.sin(i * 91.17 + 7.3) * 43758.5453) % 1;
const COLS = 34;
const ROWS = 7;
const RANK = (() => {
  const idx = Array.from({length: COLS * ROWS}).map((_, i) => i);
  const key = (i: number) => Math.floor(i / ROWS) + hash(i) * 6; // mostly left to right (time), with jitter
  const cells = idx.map((i) => ({i, k: key(i)}));
  cells.sort((a, b) => a.k - b.k);
  const rank: number[] = [];
  cells.forEach((c, r) => (rank[c.i] = r));
  return rank;
})();
const GREENS = [P.mint, '#6FAE92', P.sageLight, '#B8DCC8', P.marigold];

const Thumb: React.FC<{skin: string}> = ({skin}) => <rect x={-8} y={-40} width={16} height={32} rx={8} fill={skin} />;

/* survey rows: bar widths of the question lines */
const QROWS = [
  [300, 180],
  [340, 120],
  [260, 200],
  [320, 150],
  [280, 170],
];

export const Scene: React.FC = () => {
  const t = useGT();
  const cam = camAt(
    [
      {t: I0, x: SA, y: 400, z: 1.24},
      {t: T.three + 0.6, x: SA, y: 410, z: 1.18},
      {t: T.almost - 0.12, x: SA, y: 420, z: 1.16},
      {t: T.almost + 0.55, x: SA, y: 540, z: 1.0},
      {t: T.survey - 0.4, x: SA + 20, y: 540, z: 1.02},
      {t: T.survey + 0.2, x: SB, y: 530, z: 1.0},
      {t: T.and2 - 0.4, x: SB, y: 520, z: 1.05},
      {t: T.and2 + 0.2, x: SC, y: 540, z: 1.0},
      {t: T.live - 0.35, x: SC, y: 530, z: 1.05},
      {t: T.live + 0.2, x: SD, y: 540, z: 1.0},
      {t: T.sahara, x: SD, y: 540, z: 1.05},
    ],
    t,
    0.002,
  );
  const pans = [T.survey - 0.1, T.and2 - 0.1, T.live - 0.075];
  const swish = Math.max(...pans.map((p) => Math.max(0, 1 - Math.abs(t - p) / 0.3)));

  /* ---------- calendar ---------- */
  const calRise = easeOutQuint(ramp(t, T.three - 0.2, T.three + 0.4));

  /* ---------- commits ---------- */
  const fillAt = (r: number) => T.almost + 0.05 + (r / (COLS * ROWS)) * 1.25;
  const count = Math.round(596 * easeOutCubic(ramp(t, T.almost + 0.05, T.commits + 0.35)));

  /* ---------- survey ---------- */
  const boardIn = spring(t, T.survey - 0.45, 9, 0.55);
  const tick0 = T.survey + 0.35;
  const tick1 = T.hospitals + 0.55;
  const tickAt = (i: number) => tick0 + i * ((tick1 - tick0) / (QROWS.length - 1));
  const pen = clamp((t - tick0) / (tick1 - tick0));

  /* ---------- doctors ---------- */
  const shake = (seed: number) => {
    const a = T.docs2 + 0.1 + seed * 0.12;
    const x = t - a;
    const len = T.no - a;
    return x > 0 && x < len + 0.15 ? Math.sin(x * 15) * 11 * (1 - 0.4 * (x / len)) : 0;
  };
  const thumbs = mv(t, T.no + 0.03, T.no + 0.33, easeOutCubic);
  const nos = [
    {t: T.docs2, x: SC - 530, y: 330, r: -8},
    {t: T.kept, x: SC + 530, y: 330, r: 8},
    {t: T.telling, x: SC, y: 230, r: -4},
    {t: T.us, x: SC - 520, y: 770, r: 6},
  ];
  const flipNo = (i: number) => clamp((t - (T.no + 0.12 + i * 0.07)) / 0.25);
  const tagS = [pop(t, T.and2 + 0.1, 13, 0.5), pop(t, T.and2 + 0.26, 13, 0.5)];
  const docHappy = t > T.no + 0.05;

  /* ---------- live ---------- */
  const phUp = spring(t, T.live - 0.25, 9, 0.6);
  const phY = 500 + (1 - phUp) * 1000;
  const badge = pop(t, T.google - 0.05, 12, 0.45);
  const listing = spring(t, T.play - 0.05, 10, 0.55);
  const burst = easeOutCubic(ramp(t, T.today, T.today + 0.9));

  /* ---------- title ---------- */
  const wipe = easeInOutCubic(ramp(t, T.sahara - 0.5, T.sahara - 0.1));
  const [wx, wy] = toScreen(cam, SD - 380, 500);
  const logoP = pop(t, T.sahara - 0.2, 10, 0.5);
  const ringP = ramp(t, T.sahara - 0.1, T.sahara + 1.0);
  const creep = 1 + 0.03 * ramp(t, T.sahara, TOTAL_SEC);
  const faceT = (i: number) => T.care + 0.15 + i * 0.13;
  const footT = TAG_T[TAG_T.length - 1] + 0.45;

  return (
    <AbsoluteFill style={{background: P.sage, overflow: 'hidden'}}>
      {/* background parallax shapes */}
      <World cam={cam} depth={0.4}>
        {Array.from({length: 12}).map((_, i) => {
          const x = 120 + i * 470 + (i % 2) * 120;
          const y = i % 2 ? 860 : 220;
          const r = 200 + (i % 3) * 70;
          return <div key={i} style={{position: 'absolute', left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: '50%', background: i % 3 ? P.sageDark : P.band, transform: `translateY(${Math.sin(t * 0.5 + i) * 14}px)`}} />;
        })}
      </World>
      <World cam={cam} depth={0.7}>
        <Motes t={t} n={60} x0={-200} y0={-100} w={6400} h={1300} color="rgba(255,248,238,0.4)" seed={11} />
      </World>
      <World cam={cam}>
        {/* ======== A: three months + commits ======== */}
        <div style={{position: 'absolute', left: SA - 960, top: 96, width: 1920, display: 'flex', justifyContent: 'center'}}>
          <Rise2 t={t} t0={T.three - 0.05} text="Three months" size={76} />
        </div>
        {MONTHS.map((mo, i) => {
          const x = SA + (i - 1.5) * 270;
          const t0 = T.three - 0.1 + i * 0.17;
          const f = spring(t, t0, 11, 0.5);
          const done = t > t0 + 0.55 && i < 3;
          return (
            <React.Fragment key={mo.m}>
              <div style={{position: 'absolute', left: x - 115, top: 200, width: 230, height: 250, borderRadius: 22, background: 'rgba(255,248,238,0.1)'}} />
              <div style={{position: 'absolute', left: x - 115, top: 200 + (1 - calRise) * 50, width: 230, height: 250, perspective: 900}}>
                <div style={{position: 'absolute', inset: 0, transformOrigin: '50% 0%', transform: `rotateX(${(1 - f) * -95}deg)`, opacity: f > 0.02 ? 1 : 0}}>
                  <div style={{position: 'absolute', inset: 0, borderRadius: 22, background: P.cream, boxShadow: '0 18px 36px rgba(10,30,20,0.3)', overflow: 'hidden'}}>
                    <div style={{height: 80, background: mo.c, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 50, color: P.white, letterSpacing: 4}}>{mo.m}</div>
                    <div style={{display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 7, padding: 18}}>
                      {Array.from({length: 28}).map((_, k) => (
                        <div key={k} style={{height: 17, borderRadius: 5, background: hash(k + i * 31) > 0.45 ? mo.c : P.sand, opacity: hash(k + i * 31) > 0.45 ? 0.75 : 1}} />
                      ))}
                    </div>
                  </div>
                  {done && (
                    <div style={{position: 'absolute', right: -14, top: -14, width: 48, height: 48, borderRadius: '50%', background: P.sageDark, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${pop(t, t0 + 0.55, 14, 0.45)})`}}>
                      <IconCheck s={30} color={P.white} />
                    </div>
                  )}
                </div>
              </div>
            </React.Fragment>
          );
        })}
        {/* time bar under the months */}
        <div style={{position: 'absolute', left: SA - 520, top: 482, width: 1040, height: 8, borderRadius: 4, background: 'rgba(255,248,238,0.2)'}} />
        <div style={{position: 'absolute', left: SA - 520, top: 482, width: 1040 * mv(t, T.three - 0.1, T.months + 0.9), height: 8, borderRadius: 4, background: P.marigold}} />
        {/* commit grid */}
        <div style={{position: 'absolute', left: SA - 710, top: 560}}>
          {Array.from({length: COLS * ROWS}).map((_, i) => {
            const c = Math.floor(i / ROWS);
            const r = i % ROWS;
            const p = pop(t, fillAt(RANK[i]), 18, 0.5);
            const col = GREENS[Math.floor(hash(i * 3.1) * (hash(i) > 0.93 ? 5 : 4))];
            return (
              <div key={i} style={{position: 'absolute', left: c * 30, top: r * 30, width: 24, height: 24, borderRadius: 6, background: 'rgba(255,248,238,0.1)'}}>
                {p > 0 && <div style={{position: 'absolute', inset: 0, borderRadius: 6, background: col, transform: `scale(${p})`}} />}
              </div>
            );
          })}
        </div>
        <div style={{position: 'absolute', left: SA + 340, top: 548, width: 400, transform: `scale(${pop(t, T.almost - 0.08, 12, 0.5)})`, transformOrigin: '0% 50%'}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 150, color: P.cream, fontVariantNumeric: 'tabular-nums', lineHeight: 1}}>{count}</div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 56, color: P.mint, lineHeight: 1.1}}>commits</div>
        </div>

        {/* ======== B: the survey ======== */}
        <IconDisc t={t} t0={T.docs1 - 0.05} x={SB - 560} y={400} kind="doctor" />
        <IconDisc t={t} t0={T.hospitals - 0.05} x={SB + 560} y={400} kind="hospital" />
        <div style={{position: 'absolute', left: SB - 300, top: 140 + (1 - boardIn) * 700, width: 600, height: 730, transform: `rotate(${(1 - boardIn) * 8 - 1.5 + Math.sin(t * 1.1) * 0.5}deg)`}}>
          <div style={{position: 'absolute', inset: 0, borderRadius: 30, background: '#8A6C4E', boxShadow: '0 30px 60px rgba(10,30,20,0.35)'}} />
          <div style={{position: 'absolute', left: 26, top: 40, right: 26, bottom: 26, borderRadius: 14, background: P.cream, padding: '58px 40px 0 40px'}}>
            <Rise t={t} t0={T.survey - 0.05} text="Survey of doctors and hospitals" size={48} color={P.sageDark} stagger={0.07} style={{width: 470}} />
            <div style={{marginTop: 34}}>
              {QROWS.map(([w1, w2], i) => {
                const tp = pop(t, tickAt(i), 14, 0.45);
                return (
                  <div key={i} style={{display: 'flex', alignItems: 'center', gap: 22, height: 74}}>
                    <div style={{width: 46, height: 46, borderRadius: 12, border: `4px solid ${P.sage}`, position: 'relative', flex: 'none', background: tp > 0.5 ? P.sage : 'transparent'}}>
                      {tp > 0 && (
                        <div style={{position: 'absolute', left: 3, top: 3, transform: `scale(${tp})`}}>
                          <IconCheck s={32} color={P.white} />
                        </div>
                      )}
                    </div>
                    <div>
                      <div style={{width: w1 * 0.9, height: 12, borderRadius: 6, background: '#D9CDBD', marginBottom: 10}} />
                      <div style={{width: w2 * 0.9, height: 12, borderRadius: 6, background: '#E7DCCD'}} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {/* the clip */}
          <div style={{position: 'absolute', left: 200, top: -26, width: 200, height: 70, borderRadius: 18, background: P.marigold, boxShadow: '0 8px 16px rgba(10,30,20,0.25)'}} />
          <div style={{position: 'absolute', left: 270, top: -12, width: 60, height: 22, borderRadius: 11, background: P.marigoldDeep}} />
          {/* pencil ticking down the list */}
          {boardIn > 0.3 && (
            <div style={{position: 'absolute', left: 104 + 6 * Math.sin(t * 11), top: 282 + pen * 74 * (QROWS.length - 1) + 4 * Math.cos(t * 13), transform: 'rotate(-38deg)', transformOrigin: '50% 0%'}}>
              <div style={{width: 0, height: 0, borderLeft: '11px solid transparent', borderRight: '11px solid transparent', borderBottom: `26px solid ${P.sand}`}} />
              <div style={{width: 22, height: 150, borderRadius: '2px 2px 8px 8px', background: P.coral}} />
            </div>
          )}
        </div>

        {/* ======== C: doctors who kept telling us no ======== */}
        <div style={{position: 'absolute', left: SC - 900, top: 830, width: 1800, height: 140, borderRadius: '50%', background: P.band}} />
        <div style={{position: 'absolute', left: SC - 760, top: 842, width: 1520, height: 96, borderRadius: '50%', background: '#2F5A4B'}} />
        <Plant x={SC - 820} y={890} s={1.15} t={t} pot={P.marigold} leaf="#1F4A3B" leaf2="#2C5E4B" />
        <Plant x={SC + 820} y={890} s={1.05} t={t} pot={P.rose} leaf="#1F4A3B" leaf2="#2C5E4B" />
        <Person
          {...CAST.teamDoctor}
          t={t}
          x={SC - 240}
          y={880}
          s={1.2}
          look={0.5}
          expr={docHappy ? 'happy' : t > T.docs2 ? 'think' : 'neutral'}
          headTilt={shake(0)}
          armR={lerp(8, 62, thumbs)}
          elbowR={lerp(0, 112, thumbs)}
          hold={thumbs > 0.5 ? <Thumb skin={P.skin4} /> : undefined}
        />
        <Person
          {...CAST.sharada}
          t={t}
          x={SC + 240}
          y={880}
          s={1.2}
          flip
          look={0.5}
          expr={docHappy ? 'happy' : t > T.docs2 ? 'think' : 'neutral'}
          headTilt={shake(1)}
          armR={lerp(8, 62, thumbs)}
          elbowR={lerp(0, 112, thumbs)}
          hold={thumbs > 0.5 ? <Thumb skin={P.skin2} /> : undefined}
        />
        <NameTag who="sujay" x={SC - 240 - 380} y={560 + Math.sin(t * 1.6) * 3} a={tagS[0]} size={1.25} />
        <NameTag who="sharada" x={SC + 240 + 100} y={560 + Math.sin(t * 1.6 + 1) * 3} a={tagS[1]} size={1.25} />
        {nos.map((n, i) => {
          const s = pop(t, n.t, 14, 0.42) * (1 - mv(t, T.live - 0.2, T.live + 0.1));
          if (s <= 0.01) return null;
          const fl = flipNo(i);
          const showCheck = fl > 0.5;
          const sy = Math.abs(Math.cos(fl * Math.PI));
          return (
            <div key={i} style={{position: 'absolute', left: n.x, top: n.y + Math.sin(t * 2 + i) * 5, transform: `translate(-50%,-50%) rotate(${n.r}deg) scale(${s}) scaleY(${sy})`}}>
              <div style={{width: 170, height: 104, borderRadius: 30, background: showCheck ? P.cream : P.rose, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: P.cream, boxShadow: '0 12px 24px rgba(10,30,20,0.25)'}}>
                {showCheck ? <IconCheck s={64} color={P.sage} /> : 'No'}
              </div>
            </div>
          );
        })}

        {/* ======== D: live on Google Play today ======== */}
        {t > T.live - 0.6 && (
          <>
            {Array.from({length: 16}).map((_, i) => {
              const a = (i / 16) * Math.PI * 2;
              const d = 140 + 420 * burst;
              return <div key={i} style={{position: 'absolute', left: SD + 330 + Math.cos(a) * d * 1.3 - 8, top: 470 + Math.sin(a) * d - 8, width: 16, height: 16, borderRadius: i % 2 ? 4 : 8, background: i % 3 ? P.marigold : P.cream, opacity: burst > 0 ? 1 - burst : 0, transform: `rotate(${i * 30 + t * 90}deg)`}} />;
            })}
            <PhoneSeq t={t} segs={[{clip: 6, at: T.live, clipFrom: 10.75, rate: 0.8}]} x={SD - 380} y={phY} h={780} rot={-2 + Math.sin(t * 0.9) * 0.6} note={false} />
            <div style={{position: 'absolute', left: SD - 380 - 183 - 250, top: phY + 300, width: 230, textAlign: 'right', lineHeight: 1.3, fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(255,248,238,0.6)'}}>Practice data. All names are fictional.</div>
            {badge > 0.01 && (
              <div style={{position: 'absolute', left: 0, top: Math.sin(t * 1.5) * 4}}>
                <PlayBadge x={SD + 330} y={300} s={1.35 * badge} />
              </div>
            )}
            {listing > 0.01 && (
              <div style={{position: 'absolute', left: SD + 330 - 480 + (1 - listing) * 900, top: 440, width: 960, padding: 22, borderRadius: 28, background: P.white, boxShadow: '0 24px 50px rgba(10,30,20,0.32)', transform: `rotate(${(1 - listing) * 6 + 1}deg)`, opacity: Math.min(1, listing * 2)}}>
                <Img src={staticFile('brand/play-listing.png')} style={{width: 916, height: 916 * (520 / 2150), display: 'block', borderRadius: 12}} />
              </div>
            )}
          </>
        )}
      </World>

      {/* swish streaks during pans */}
      {swish > 0.02 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: swish * 0.5}}>
          {Array.from({length: 8}).map((_, i) => (
            <rect key={i} x={((i * 431 + t * 4000) % 2400) - 400} y={110 + i * 108} width={500 + (i % 3) * 260} height={5 + (i % 2) * 5} rx={5} fill={i % 2 ? P.mint : P.cream} opacity={0.5} />
          ))}
        </svg>
      )}

      {/* ======== title ======== */}
      {wipe > 0 && <div style={{position: 'absolute', left: wx - 2300 * wipe, top: wy - 2300 * wipe, width: 4600 * wipe, height: 4600 * wipe, borderRadius: '50%', background: P.cream}} />}
      {t > T.sahara - 0.15 && (
        <AbsoluteFill style={{transform: `scale(${creep})`, transformOrigin: '50% 45%'}}>
          <div style={{position: 'absolute', left: -220 + Math.sin(t * 0.3) * 20, top: -300, width: 760, height: 760, borderRadius: '50%', background: P.sageLight, opacity: 0.7 * ramp(t, T.sahara - 0.15, T.sahara + 0.6)}} />
          <div style={{position: 'absolute', right: -280, bottom: -340 + Math.cos(t * 0.3) * 20, width: 820, height: 820, borderRadius: '50%', background: P.roseLight, opacity: 0.8 * ramp(t, T.sahara - 0.1, T.sahara + 0.8)}} />
          <div style={{position: 'absolute', right: 190, top: 130 + Math.sin(t * 0.6) * 10, width: 64, height: 64, borderRadius: '50%', background: P.marigold, opacity: 0.55 * ramp(t, T.sahara + 0.5, T.sahara + 1.4)}} />
          <div style={{position: 'absolute', left: 230, bottom: 300 + Math.cos(t * 0.5) * 10, width: 40, height: 40, borderRadius: '50%', background: P.mint, opacity: 0.8 * ramp(t, T.sahara + 0.7, T.sahara + 1.6)}} />
          {/* lockup */}
          <div style={{position: 'absolute', left: 0, top: 140, width: 1920, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 30}}>
            <div style={{position: 'relative', width: 210, height: 210}}>
              <div style={{position: 'absolute', inset: 0, borderRadius: '50%', border: `6px solid ${P.sage}`, transform: `scale(${0.6 + ringP * 0.9})`, opacity: (1 - ringP) * 0.5}} />
              <Img src={staticFile('brand/sahara-logo.png')} style={{position: 'absolute', left: -20, top: -20, width: 250, height: 250, transform: `scale(${logoP}) rotate(${(1 - logoP) * -14}deg)`}} />
            </div>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 176, color: P.sageDark, lineHeight: 1, display: 'flex'}}>
              {'Sahara'.split('').map((ch, i) => {
                const p = easeOutQuint(ramp(t, T.sahara + 0.1 + i * 0.05, T.sahara + 0.65 + i * 0.05));
                return (
                  <span key={i} style={{display: 'inline-block', overflow: 'hidden', paddingBottom: '0.1em'}}>
                    <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 120}%)`}}>{ch}</span>
                  </span>
                );
              })}
            </div>
          </div>
          {/* tagline: each word on its spoken word */}
          <div style={{position: 'absolute', left: 0, top: 393, width: 1920, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 60, color: P.ink, lineHeight: 1.2}}>
            {TAG.map((w, i) => {
              const p = easeOutQuint(ramp(t, TAG_T[i] - 0.05, TAG_T[i] + 0.45));
              return (
                <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.1em', marginRight: i < TAG.length - 1 ? '0.26em' : 0}}>
                  <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`, color: w === 'matters' ? P.sage : P.ink}}>{w}</span>
                </span>
              );
            })}
          </div>
          {/* the five of us */}
          {ORDER.map((who, i) => {
            const x = 960 + (i - 2) * 230;
            const s = pop(t, faceT(i), 12, 0.5);
            const np = easeOutQuint(ramp(t, faceT(i) + 0.15, faceT(i) + 0.6));
            const bob = Math.sin(t * 1.4 + i * 0.9) * 4;
            return (
              <React.Fragment key={who}>
                {s > 0.01 && <Face who={who} x={x} y={612 + bob} d={156} s={s} />}
                <div style={{position: 'absolute', left: x - 115, top: 702, width: 230, textAlign: 'center', overflow: 'hidden', paddingBottom: 4}}>
                  <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 30, color: P.sageDark, transform: `translateY(${(1 - np) * 130}%)`}}>{TEAM[who].short}</div>
                </div>
              </React.Fragment>
            );
          })}
          <div style={{position: 'absolute', left: 960 - 60 * easeOutQuint(ramp(t, footT - 0.3, footT + 0.4)), top: 790, width: 120 * easeOutQuint(ramp(t, footT - 0.3, footT + 0.4)), height: 6, borderRadius: 3, background: P.marigold}} />
          {/* credit line */}
          <div style={{position: 'absolute', left: 0, top: 814, width: 1920, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 24, fontFamily: FONT_BODY, fontSize: 28, color: P.inkSub}}>
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
    {text.split(' ').map((w, i, a) => {
      const p = easeOutQuint(ramp(t, t0 + i * 0.12, t0 + i * 0.12 + 0.55));
      return (
        <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.08em', marginRight: i < a.length - 1 ? '0.26em' : 0}}>
          <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`}}>{w}</span>
        </span>
      );
    })}
  </div>
);

/** round cream disc with a flat doctor (stethoscope) or hospital icon */
const IconDisc: React.FC<{t: number; t0: number; x: number; y: number; kind: 'doctor' | 'hospital'}> = ({t, t0, x, y, kind}) => {
  const s = pop(t, t0, 12, 0.45);
  if (s <= 0.01) return null;
  return (
    <div style={{position: 'absolute', left: x - 120, top: y - 120 + Math.sin(t * 1.7 + x) * 8, width: 240, height: 240, borderRadius: '50%', background: P.cream, boxShadow: '0 18px 40px rgba(10,30,20,0.3)', transform: `scale(${s}) rotate(${(1 - s) * 20}deg)`}}>
      <svg width={240} height={240} viewBox="0 0 240 240">
        {kind === 'doctor' ? (
          <g>
            <path d="M84 60 Q70 130 120 146 Q170 130 156 60" stroke={P.sageDark} strokeWidth={12} fill="none" strokeLinecap="round" />
            <circle cx={84} cy={58} r={9} fill={P.sageDark} />
            <circle cx={156} cy={58} r={9} fill={P.sageDark} />
            <path d="M120 146 L120 168 Q120 196 150 196 Q176 196 176 170" stroke={P.sageDark} strokeWidth={12} fill="none" strokeLinecap="round" />
            <circle cx={176} cy={160} r={20} fill={P.marigold} />
            <circle cx={176} cy={160} r={8} fill={P.cream} />
          </g>
        ) : (
          <g>
            <rect x={56} y={86} width={128} height={104} rx={8} fill={P.sky} />
            <rect x={88} y={56} width={64} height={134} rx={8} fill={P.indigo} />
            <rect x={110} y={68} width={20} height={44} rx={4} fill={P.cream} />
            <rect x={98} y={80} width={44} height={20} rx={4} fill={P.cream} />
            {[0, 1].map((r) => [0, 1].map((c) => <rect key={r + '' + c} x={c ? 158 : 66} y={104 + r * 34} width={16} height={20} rx={3} fill={P.cream} opacity={0.85} />))}
            <rect x={108} y={154} width={24} height={36} rx={4} fill={P.cream} />
            <rect x={40} y={188} width={160} height={8} rx={4} fill={P.sageDark} />
          </g>
        )}
      </svg>
    </div>
  );
};
