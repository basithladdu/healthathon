/* D: Shirin's question. The tower creaks and topples into a heap; the sign flips to "for families".
   Then three switches, one at a time: doctor tools -> patients & families (the heap re-forms into a phone),
   website -> Android app (shortlisted; Basith and Awaiz rebuild it), and the doctors pull everyone back to
   the goals of care talk, which lands centre and fills the frame in sage for scene E. */
import React from 'react';
import {P, FONT_HEAD, FONT_BODY, useGT, Person, PhoneClip, ramp, mv, lerp, pop, spring, kick, back, easeOutCubic, easeInCubic, easeInOutCubic, Expr} from '../kit';
import {CAST} from '../cast';
import {NameTag} from '../team';
import {Studio, W, FLOOR, STU_X, TOWER_X, CARD_W, CARD_H, PHONE, T_FALL, T_CREAK, cardPose, landTimes, roomCam, toScreen, T_GATHER} from './C_room';
import {Stats} from './C_build';
import {Switchboard, SwitchDef, WebPhone, Stamp, GoalsCard, WordsAt} from './D_parts';

/* ---------- Shirin ---------- */
const Shirin: React.FC<{t: number}> = ({t}) => {
  if (t > 47.9) return null;
  const u = ramp(t, W.shirin - 0.12, W.shirin + 0.38);
  const x = lerp(-520, STU_X, back(u, 1.4));
  const hop = -Math.abs(Math.sin(u * Math.PI * 3)) * 22 * (u < 1 ? 1 : 0);
  const settle = t > W.shirin + 0.38 ? -7 * Math.exp(-(t - W.shirin - 0.38) * 6) * Math.sin((t - W.shirin - 0.38) * 14) : 0;
  const lean = 12 * Math.sin(Math.min(1, u) * Math.PI) * (u < 1 ? 1 : 0) + settle;
  const hand = spring(t, W.asked, 12, 0.6) * (1 - mv(t, W.why + 0.4, W.why + 0.75));
  let expr: Expr = 'happy';
  if (t > W.asked) expr = 'think';
  if (t > W.she) expr = 'happy';
  const talk = t > W.why - 0.05 && t < W.this + 0.4;
  const nod = t > W.right && t < W.right + 0.9 ? Math.sin((t - W.right) * 12) * 5 * (1 - ramp(t, W.right + 0.5, W.right + 0.9)) : 0;
  return <Person {...CAST.medStudent} t={t} x={x} y={FLOOR + hop} lean={lean} armR={lerp(6, 160, hand)} elbowR={10 * hand} armL={6} expr={talk ? 'neutral' : expr} talk={talk} look={0.8} headTilt={nod} />;
};

/** "?" over the raised hand */
const Ask: React.FC<{t: number}> = ({t}) => {
  if (t < W.question - 0.1 || t > W.why + 0.4) return null;
  const s = pop(t, W.question - 0.1, 14, 0.4) * (1 - easeInCubic(ramp(t, W.why + 0.1, W.why + 0.35)));
  return (
    <div style={{position: 'absolute', left: STU_X + 90 - 50, top: 380 - 50, width: 100, height: 100, borderRadius: 50, background: P.marigold, boxShadow: `inset 0 -8px 0 ${P.marigoldDeep}`, transform: `scale(${s}) rotate(${Math.sin(t * 8) * 8}deg)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 70, color: P.white}}>
      ?
    </div>
  );
};

/** the big question bubble */
const Question: React.FC<{t: number}> = ({t}) => {
  const t0 = W.why - 0.12;
  if (t < t0 || t > 44.4) return null;
  const s = pop(t, t0, 12, 0.45) * (1 - easeInCubic(ramp(t, 44.05, 44.35)));
  const B = {x: -150, y: -190, w: 980, h: 350};
  return (
    <div style={{position: 'absolute', left: B.x, top: B.y, width: B.w, height: B.h, transform: `scale(${s})`, transformOrigin: '8% 120%'}}>
      <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={B.w} height={B.h}>
        <path d={`M 150 ${B.h - 20} L 300 ${B.h - 20} L ${STU_X - B.x + 50} ${430 - B.y} Z`} fill={P.white} />
      </svg>
      <div style={{position: 'absolute', inset: 0, borderRadius: 60, background: P.white, boxShadow: '0 16px 0 rgba(56,46,41,0.08)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center'}}>
        <WordsAt t={t} words={[['Why', W.why], ['would', W.why + 0.1], ['a', W.why + 0.17], ['family', W.family]]} size={96} color={P.ink} accent={P.rose} accentIdx={3} />
        <WordsAt t={t} words={[['install', W.install], ['this?', W.this]]} size={96} color={P.ink} />
      </div>
    </div>
  );
};

/** the tower groans: vibration arcs either side */
const Creak: React.FC<{t: number}> = ({t}) => {
  if (t < T_CREAK || t > T_FALL + 0.15) return null;
  const k = ramp(t, T_CREAK, T_CREAK + 0.4) * (1 - ramp(t, T_FALL, T_FALL + 0.15));
  const flick = Math.floor(t * 12) % 2;
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {[-1, 1].map((side) =>
        [0, 1, 2].map((j) => {
          const cx = TOWER_X + side * (CARD_W / 2 + 50 + j * 30);
          const cy = 180 + (flick ? 12 : -12) + j * 4;
          const r = 50 + j * 26;
          return <path key={side + '' + j} d={`M${cx} ${cy - r} q ${side * 30} ${r} 0 ${r * 2}`} stroke={P.inkSub} strokeWidth={9} fill="none" strokeLinecap="round" opacity={k * (0.8 - j * 0.2)} />;
        }),
      )}
    </svg>
  );
};

/** dust puffs where cards land */
const Dust: React.FC<{t: number}> = ({t}) => {
  if (t < T_FALL || t > T_FALL + 2.5) return null;
  const L = landTimes();
  const out: React.ReactNode[] = [];
  L.forEach((tl, i) => {
    const d = t - tl;
    if (d < 0 || d > 0.9) return;
    const p = cardPose(i, tl + 0.001);
    for (let j = 0; j < 6; j++) {
      const side = j % 2 ? 1 : -1;
      const u = easeOutCubic(d / 0.9);
      const x = p.x + side * (60 + j * 30) * u + side * 60;
      const y = FLOOR - 16 - (j % 3) * 18 * u - 20 * u;
      const r = (16 + j * 5) * (0.4 + u);
      const o = 0.85 * (1 - ramp(d, 0.35, 0.9));
      out.push(<circle key={i * 10 + j} cx={x} cy={y} r={r} fill={j % 3 === 0 ? '#D9C6A8' : '#F1E6D4'} opacity={o} />);
    }
  });
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {out}
    </svg>
  );
};

/** hanging sign: "for doctors like us" flips to "for families" */
const SIGN_FRONT: [string, number][] = [
  ['for', W.wed + 0.24],
  ['doctors', W.wed + 0.32],
  ['like', W.wed + 0.42],
  ['us', W.us],
];
const SIGN_BACK: [string, number][] = [
  ['for', W.not + 0.28],
  ['families', W.them],
];
const Sign: React.FC<{t: number}> = ({t}) => {
  const T0 = W.wed - 0.15;
  if (t < T0) return null;
  const drop = spring(t, T0, 9, 0.35);
  const y = lerp(-1200, 0, drop);
  const swing = Math.sin((t - T0) * 5) * 4 * Math.exp(-(t - T0) * 1.5) + Math.sin(t * 1.4) * 0.8;
  const p = ramp(t, W.not, W.not + 0.32);
  const sx = Math.abs(Math.cos(p * Math.PI));
  const backSide = p >= 0.5;
  const cx = 600;
  const w = 840;
  const h = 180;
  const top = 40 + y;
  const sq = backSide ? Math.exp(-(t - W.not - 0.16) * 6) * Math.sin((t - W.not - 0.16) * 18) * 0.06 : 0;
  return (
    <div style={{position: 'absolute', left: cx, top: top - 900, width: 0, height: 0, transform: `rotate(${swing}deg)`, transformOrigin: '0 0'}}>
      {[-w / 2 + 60, w / 2 - 66].map((dx) => (
        <div key={dx} style={{position: 'absolute', left: dx, top: 0, width: 6, height: 900, background: '#8A7A6A'}} />
      ))}
      <div
        style={{
          position: 'absolute',
          left: -w / 2,
          top: 900,
          width: w,
          height: h,
          borderRadius: 26,
          background: backSide ? P.sage : P.indigo,
          boxShadow: `inset 0 -12px 0 ${backSide ? P.sageDark : '#46588C'}`,
          transform: `scaleX(${Math.max(0.02, sx)}) scale(${1 + sq})`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          paddingBottom: 10,
          boxSizing: 'border-box',
        }}
      >
        {backSide ? <WordsAt t={t} words={SIGN_BACK} size={92} color={P.white} /> : <WordsAt t={t} words={SIGN_FRONT} size={80} color={P.cream} />}
      </div>
    </div>
  );
};

/* ---------- switches ---------- */
const DEFS: SwitchDef[] = [
  {pop: W.one, flip: W.became, old: 'Doctor tools', neu: 'Patients & families'},
  {pop: W.switch, flip: W.android, old: 'Website', neu: 'Android app'},
  {pop: W.time, flip: W.oneThing, old: 'Everything', neu: 'Goals of care talk', sub: 'at the centre'},
];

/* ---------- the phone: website (s11a), rebuilt as the Android app (s11b) ---------- */
const T_FLIP0 = W.rebuilt + 0.25;
const PhoneStage: React.FC<{t: number}> = ({t}) => {
  if (t < W.became - 0.05) return null;
  const s = pop(t, W.became - 0.05, 12, 0.45);
  const fill = ramp(t, T_GATHER + 0.3, T_GATHER + 0.75);
  const fu = ramp(t, T_FLIP0, T_FLIP0 + 0.36);
  const sx = Math.abs(Math.cos(fu * Math.PI));
  const real = fu >= 0.5;
  const bob = Math.sin(t * 1.6) * 6;
  const recoil = t > W.goals ? -14 * Math.exp(-(t - W.goals - 0.2) * 5) * (t > W.goals + 0.2 ? 1 : 0) : 0;
  const tiles = [W.patients, W.families, W.families + 0.38];
  return (
    <>
      <div style={{position: 'absolute', left: PHONE.x, top: PHONE.y + bob + recoil, width: 0, height: 0, transform: `scale(${s}) scaleX(${Math.max(0.02, sx)})`}}>
        {/* glow behind */}
        <div style={{position: 'absolute', left: -260, top: -300, width: 520, height: 600, borderRadius: '50%', background: 'radial-gradient(circle, rgba(255,226,160,0.55) 0%, rgba(255,226,160,0) 65%)'}} />
        {real ? <PhoneClip clip={13} x={0} y={0} h={PHONE.h} startAt={T_FLIP0 + 0.18} /> : <div style={{position: 'absolute', left: 0, top: 0, transform: `scale(${PHONE.h / 500})`}}>
            <WebPhone t={t} h={500} tiles={tiles} fill={fill} />
          </div>}
      </div>
      {real && (
        <div style={{position: 'absolute', left: PHONE.x - 250, top: PHONE.y + PHONE.h / 2 + 6, width: 500, textAlign: 'center', fontFamily: FONT_BODY, fontSize: 20, color: 'rgba(56,46,41,0.5)', opacity: ramp(t, T_FLIP0 + 0.4, T_FLIP0 + 0.8)}}>
          Practice data. All names are fictional.
        </div>
      )}
    </>
  );
};

/** build sparks around the phone while they rebuild it */
const BuildSparks: React.FC<{t: number}> = ({t}) => {
  if (t < W.rebuilt || t > W.rebuilt + 1.3) return null;
  const out: React.ReactNode[] = [];
  for (let k = 0; k < 3; k++) {
    const t0 = W.rebuilt + k * 0.28;
    const u = ramp(t, t0, t0 + 0.4);
    if (u <= 0 || u >= 1) continue;
    [-1, 1].forEach((side) => {
      const hx = PHONE.x + side * 140;
      const hy = PHONE.y - 40 + k * 70;
      for (let j = 0; j < 4; j++) {
        const a = ((side > 0 ? 0 : 180) + (j - 1.5) * 34) * (Math.PI / 180);
        const r0 = 10 + u * 40;
        const r1 = r0 + 22 * (1 - u) + 8;
        out.push(<line key={`${k}${side}${j}`} x1={hx + Math.cos(a) * r0} y1={hy + Math.sin(a) * r0} x2={hx + Math.cos(a) * r1} y2={hy + Math.sin(a) * r1} stroke={P.marigold} strokeWidth={7} strokeLinecap="round" opacity={1 - u} />);
      }
    });
  }
  return (
    <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
      {out}
    </svg>
  );
};

/* ---------- standing Basith and Awaiz (s11b, s11c) and the doctors with their ropes (s11c) ---------- */
const TUGS = [W.pulling, W.back, W.oneThing];
const pulled = (t: number) => TUGS.reduce((a, tk) => a + easeOutCubic(ramp(t, tk, tk + 0.32)), 0);
const tugPhase = (t: number) => TUGS.reduce((a, tk) => a + Math.sin(Math.PI * ramp(t, tk - 0.05, tk + 0.38)), 0);
const DOC_X = [1555, 2045];
const OUT = 57.05;
const engX = (k: number, t: number) => {
  const home = k === 0 ? 1420 : 2180;
  const t0 = k === 0 ? W.basith - 0.15 : W.awaiz - 0.15;
  const from = k === 0 ? 880 : 2760;
  const enter = lerp(from, home, back(ramp(t, t0, t0 + 0.5), 1.3));
  const away = k === 0 ? 1070 : 2530;
  const step = easeInOutCubic(ramp(t, OUT, OUT + 0.45));
  const base = lerp(enter, away, step);
  return base + (k === 0 ? 1 : -1) * 52 * pulled(t);
};
/** world position of a standing person's hand (matches kit Person geometry at s=1, ignoring idle bob) */
const handAt = (x: number, armDeg: number, elbowDeg: number, side: 1 | -1, flip: boolean, lean: number) => {
  const sx = side * 52;
  const sy = -230;
  const a1 = (side * armDeg * Math.PI) / 180;
  const ex = sx + Math.sin(a1) * 62;
  const ey = sy + Math.cos(a1) * 62;
  const a2 = a1 + (side * elbowDeg * Math.PI) / 180;
  let hx = ex + Math.sin(a2) * 58;
  let hy = ey + Math.cos(a2) * 58;
  const th = (lean * Math.PI) / 180;
  [hx, hy] = [hx * Math.cos(th) - hy * Math.sin(th), hx * Math.sin(th) + hy * Math.cos(th)];
  if (flip) hx = -hx;
  return {x: x + hx, y: FLOOR + hy};
};
const docPose = (t: number) => {
  const ph = tugPhase(t);
  return {arm: 78 - 34 * ph, elbow: 10 + 20 * ph, lean: -5 - 9 * ph};
};

const Builders: React.FC<{t: number}> = ({t}) => {
  if (t < W.basith - 0.2) return null;
  const out: React.ReactNode[] = [];
  const looking = t > W.goals + 0.1;
  ([0, 1] as const).forEach((k) => {
    const x = engX(k, t);
    const who = k === 0 ? CAST.engA : CAST.engB;
    const t0 = k === 0 ? W.basith - 0.15 : W.awaiz - 0.15;
    if (t < t0) return;
    const u = ramp(t, t0, t0 + 0.5);
    const hop = -Math.abs(Math.sin(u * Math.PI * 2)) * 20 * (u < 1 ? 1 : 0);
    // building: hand toward the phone, tapping
    const b = ramp(t, W.rebuilt - 0.1, W.rebuilt + 0.1) * (1 - ramp(t, W.android + 0.3, W.android + 0.6));
    const tap = Math.sin(t * 22 + k) * 12 * b;
    let armIn = lerp(8, 96, b) + tap;
    let elIn = -20 * b;
    let armOut = 8;
    let lean = (k === 0 ? 1 : -1) * 3 * b;
    let expr: Expr = t > W.android ? 'happy' : b > 0.5 ? 'think' : 'happy';
    let look = k === 0 ? 1 : -1;
    // s11c: wander off, then get pulled back on the rope
    if (t > OUT) {
      const w = ramp(t, OUT, OUT + 0.4);
      look = k === 0 ? -1 : 1;
      expr = 'think';
      armIn = 8;
      elIn = 0;
      const ph = tugPhase(t);
      if (t > TUGS[0] - 0.05) {
        look = k === 0 ? 1 : -1;
        expr = 'surprised';
        lean = (k === 0 ? 1 : -1) * (6 + 10 * ph);
        armOut = 30 + 30 * ph;
        armIn = 20 + 20 * ph;
      } else lean = (k === 0 ? -1 : 1) * 3 * w;
      if (t > TUGS[2] + 0.3) expr = 'happy';
    }
    if (looking) {
      expr = 'happy';
      look = k === 0 ? 1 : -1;
    }
    // inward arm is the right arm for Basith (phone on his right), left arm for Awaiz
    const arms = k === 0 ? {armR: armIn, elbowR: elIn, armL: armOut} : {armL: armIn, elbowL: -elIn, armR: armOut};
    out.push(<Person key={'b' + k} {...who} t={t} x={x} y={FLOOR + hop} {...arms} lean={lean} expr={expr} look={look} headTilt={looking ? -6 : 0} />);
  });
  return <>{out}</>;
};

const Doctors: React.FC<{t: number}> = ({t}) => {
  if (t < W.doctors - 0.1) return null;
  const out: React.ReactNode[] = [];
  const ropes: React.ReactNode[] = [];
  const looking = t > W.goals + 0.1;
  ([0, 1] as const).forEach((k) => {
    const t0 = W.doctors - 0.1 + k * 0.16;
    if (t < t0) return;
    const [sx, sy] = [pop(t, t0, 13, 0.42), 1];
    const x = DOC_X[k];
    const flip = k === 0; // Sujay faces left toward Basith; Sharada faces right toward Awaiz
    const d = docPose(t);
    const reach = ramp(t, t0 + 0.15, t0 + 0.45);
    const arm = lerp(10, d.arm, reach);
    const lean = d.lean * reach;
    const who = k === 0 ? CAST.teamDoctor : CAST.sharada;
    // Sujay is flipped, so his "right" arm points to world-left. Sharada's right arm points world-right.
    const hand = handAt(x, arm, d.elbow * reach, 1, flip, k === 0 ? lean : -lean);
    const tgtX = engX(k, t) + (k === 0 ? 30 : -30);
    const tgtY = FLOOR - 175;
    const slack = Math.max(0, 1 - reach) * 120 + 26 * (1 - Math.min(1, tugPhase(t)));
    const midX = (hand.x + tgtX) / 2;
    const midY = (hand.y + tgtY) / 2 + slack;
    const coilX = x + (k === 0 ? 70 : -70);
    if (reach > 0) {
      ropes.push(
        <g key={'r' + k} opacity={Math.min(1, reach * 2)}>
          <path d={`M${hand.x} ${hand.y} Q ${midX} ${midY} ${tgtX} ${tgtY}`} stroke="#B98A4E" strokeWidth={11} fill="none" strokeLinecap="round" />
          <path d={`M${hand.x} ${hand.y - 2} Q ${midX} ${midY - 2} ${tgtX} ${tgtY - 2}`} stroke="#D9AE6E" strokeWidth={4} strokeDasharray="10 12" fill="none" strokeLinecap="round" />
          {/* loop around the waist */}
          <ellipse cx={engX(k, t)} cy={tgtY} rx={74} ry={16} fill="none" stroke="#B98A4E" strokeWidth={10} />
          {/* tail of the rope, coiled at the doctor's feet */}
          <path d={`M${hand.x} ${hand.y} C ${hand.x + (k === 0 ? 60 : -60)} ${hand.y + 120} ${coilX} ${FLOOR - 90} ${coilX} ${FLOOR - 22}`} stroke="#B98A4E" strokeWidth={10} fill="none" strokeLinecap="round" />
          <ellipse cx={coilX} cy={FLOOR - 16} rx={34 + 8 * pulled(t)} ry={12} fill="none" stroke="#9C7140" strokeWidth={9} />
        </g>,
      );
    }
    out.push(
      <div key={'d' + k} style={{position: 'absolute', left: 0, top: 0, transform: `translate(${x}px, ${FLOOR}px) scale(${sx}, ${sy}) translate(${-x}px, ${-FLOOR}px)`}}>
        <Person {...who} t={t} x={x} y={FLOOR} flip={flip} armR={arm} elbowR={d.elbow * reach} armL={lerp(6, -30, reach)} lean={lean} expr={looking ? 'happy' : reach > 0.5 ? 'think' : 'happy'} look={looking ? -1 : 1} headTilt={looking ? 6 : 0} />
      </div>,
    );
  });
  return (
    <>
      {out}
      <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
        {ropes}
      </svg>
    </>
  );
};

/** s11a: a patient and her daughter pop up beside the new app on "patients" and "families" */
const Families: React.FC<{t: number}> = ({t}) => {
  if (t < W.patients - 0.1 || t > W.basith - 0.2) return null;
  const outT = W.shortlisted + 0.15;
  const items = [
    {who: CAST.patient, x: 1330, t0: W.patients - 0.08, look: 1},
    {who: CAST.daughter, x: 2270, t0: W.families - 0.08, look: -1},
  ];
  return (
    <>
      {items.map((it, k) => {
        if (t < it.t0) return null;
        const s = pop(t, it.t0, 13, 0.42) * (1 - easeInCubic(ramp(t, outT + k * 0.08, outT + 0.3 + k * 0.08)));
        const wave = Math.sin((t - it.t0) * 9) * 18 * ramp(t, it.t0 + 0.2, it.t0 + 0.4);
        const arm = k === 0 ? {armL: 140 + wave, elbowL: -20} : {armR: 140 + wave, elbowR: -20};
        return (
          <div key={k} style={{position: 'absolute', left: 0, top: 0, transform: `translate(${it.x}px, ${FLOOR}px) scale(${s}) translate(${-it.x}px, ${-FLOOR}px)`}}>
            <Person {...it.who} t={t} x={it.x} y={FLOOR} {...arm} expr="happy" look={it.look} />
          </div>
        );
      })}
    </>
  );
};

const Tags: React.FC<{t: number}> = ({t}) => {
  const shOut = 1 - easeInCubic(ramp(t, 43.7, 44.0));
  const bOut = 1 - easeInCubic(ramp(t, OUT - 0.1, OUT + 0.15));
  const dOut = 1 - easeInCubic(ramp(t, W.goals - 0.1, W.goals + 0.15));
  return (
    <>
      <NameTag who="basith" x={engX(0, t) - 290} y={590} a={pop(t, W.basith + 0.1, 13, 0.45) * bOut} />
      <NameTag who="awaiz" x={engX(1, t) + 82} y={590} a={pop(t, W.awaiz + 0.1, 13, 0.45) * bOut} />
      <NameTag who="sujay" x={DOC_X[0] - 130} y={418} a={pop(t, W.doctors + 0.1, 13, 0.45) * dOut} />
      <NameTag who="sharada" x={DOC_X[1] - 90} y={418} a={pop(t, W.doctors + 0.26, 13, 0.45) * dOut} />
    </>
  );
};

/* ---------- the goals of care card: drops in, lands centre, then fills the frame ---------- */
const G0 = W.goals - 0.12;
const GLAND = G0 + 0.32;
const GOAL = {x: PHONE.x, y: 560, w: 760, h: 108};
const goalY = (t: number) => {
  const u = ramp(t, G0, GLAND);
  return lerp(-560, GOAL.y, easeInCubic(u));
};
const Goals: React.FC<{t: number}> = ({t}) => {
  if (t < G0 || t > W.talk + 0.1) return null;
  const y = goalY(t);
  const d = t - GLAND;
  const sq = d > 0 && d < 0.5 ? Math.exp(-d * 7) * Math.sin(d * 26) * 0.14 : 0;
  const glow = ramp(t, GLAND, GLAND + 0.25) * (0.85 + 0.15 * Math.sin(t * 6));
  const sparks: React.ReactNode[] = [];
  if (t > GLAND) {
    for (let j = 0; j < 10; j++) {
      const a = (j / 10) * Math.PI * 2 + t * 0.9;
      const R = 470 + Math.sin(t * 2 + j) * 30;
      const s = (7 + (j % 3) * 3) * ramp(t, GLAND + j * 0.03, GLAND + 0.25 + j * 0.03);
      sparks.push(<div key={j} style={{position: 'absolute', left: GOAL.x + Math.cos(a) * R - s, top: GOAL.y + Math.sin(a) * R * 0.38 - s, width: s * 2, height: s * 2, borderRadius: s, background: j % 2 ? P.marigold : '#FFE6B0', transform: 'rotate(45deg)'}} />);
    }
  }
  return (
    <>
      {sparks}
      <div style={{position: 'absolute', left: GOAL.x, top: y, width: 0, height: 0, transform: `scale(${1 + sq}, ${1 - sq})`}}>
        <GoalsCard glow={glow} w={GOAL.w} h={GOAL.h} />
      </div>
    </>
  );
};
const Expand: React.FC<{t: number}> = ({t}) => {
  if (t < W.talk - 0.02) return null;
  const cam = roomCam(t);
  const c = toScreen(cam, GOAL.x, GOAL.y);
  const e = easeInOutCubic(ramp(t, W.talk, W.talk + 0.5));
  const w0 = GOAL.w * cam.z;
  const h0 = GOAL.h * cam.z;
  const L = lerp(c.x - w0 / 2, -30, e);
  const T = lerp(c.y - h0 / 2, -30, e);
  const R = lerp(c.x + w0 / 2, 1950, e);
  const B = lerp(c.y + h0 / 2, 1110, e);
  const k = cam.z * lerp(1, 1.5, e);
  const labelOut = ramp(t, 60.55, 60.92);
  return (
    <div style={{position: 'absolute', left: L, top: T, width: R - L, height: B - T, borderRadius: lerp(GOAL.h * 0.2 * cam.z, 0, e), background: P.sage, overflow: 'hidden', boxShadow: `0 0 ${60 * (1 - e)}px ${26 * (1 - e)}px rgba(246,206,120,0.8)`}}>
      <div style={{position: 'absolute', left: lerp(c.x, 960, e) - L, top: lerp(c.y, 540, e) - T, width: 0, height: 0, transform: `scale(${k})`}}>
        <GoalsCard glow={0} w={GOAL.w} h={GOAL.h} labelOut={labelOut} flat={ramp(t, W.talk, W.talk + 0.25)} />
      </div>
    </div>
  );
};

export const Scene: React.FC = () => {
  const t = useGT();
  let sx = 0;
  let sy = 0;
  const L = [...landTimes()].sort((a, b) => a - b);
  L.slice(0, 12).forEach((tl, j) => {
    const [kx, ky] = kick(t, tl, j < 4 ? 14 : 7);
    sx += kx;
    sy += ky;
  });
  for (const [t0, a] of [
    [W.shortlisted, 12],
    [W.became, 5],
    [W.android, 5],
    [W.oneThing, 5],
    [GLAND, 10],
  ] as [number, number][]) {
    const [kx, ky] = kick(t, t0, a);
    sx += kx;
    sy += ky;
  }
  return (
    <Studio
      t={t}
      back={
        <>
          <Switchboard t={t} drop={W.changed - 0.05} defs={DEFS} />
          <PhoneStage t={t} />
        </>
      }
      people={
        <>
          <Shirin t={t} />
          <Families t={t} />
          <Builders t={t} />
          <Doctors t={t} />
        </>
      }
      mid={<Sign t={t} />}
      front={
        <>
          <Stats t={t} />
          <Creak t={t} />
          <Dust t={t} />
          <BuildSparks t={t} />
          <Ask t={t} />
          <Question t={t} />
          <Stamp t={t} t0={W.shortlisted} x={1210} y={380} out={OUT - 0.05} />
          <Goals t={t} />
          <Tags t={t} />
        </>
      }
      screen={<Expand t={t} />}
      shake={[sx, sy]}
    />
  );
};
