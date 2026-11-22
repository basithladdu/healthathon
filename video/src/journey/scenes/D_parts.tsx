/* D props: the three toggle switches, the first (website) phone, the shortlist stamp, the goals card. World units. */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {P, FONT_HEAD, FONT_BODY, ramp, mv, pop, spring, lerp, easeOutQuint, easeInCubic, back} from '../kit';
import {lerpColor} from './C_room';

/* ---------- word-by-word rise for explicit times ---------- */
export const WordsAt: React.FC<{t: number; words: [string, number][]; size: number; color: string; accent?: string; accentIdx?: number; out?: number; weight?: number; align?: 'left' | 'center'}> = ({t, words, size, color, accent, accentIdx = -1, out, weight = 600, align = 'center'}) => {
  const o = out !== undefined ? easeInCubic(ramp(t, out, out + 0.3)) : 0;
  return (
    <div style={{fontFamily: FONT_HEAD, fontWeight: weight, fontSize: size, color, lineHeight: 1.14, textAlign: align, whiteSpace: 'nowrap'}}>
      {words.map(([w, t0], i) => {
        const p = easeOutQuint(ramp(t, t0 - 0.04, t0 + 0.45));
        return (
          <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', paddingBottom: '0.08em', marginRight: i < words.length - 1 ? '0.26em' : 0}}>
            <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130 - o * 130}%)`, color: i === accentIdx ? accent : color}}>{w}</span>
          </span>
        );
      })}
    </div>
  );
};

/* ---------- switchboard ---------- */
export const BOARD = {x: 960, y: 0, w: 1680, h: 250};
export type SwitchDef = {pop: number; flip: number; old: string; neu: string; sub?: string};

const Toggle: React.FC<{t: number; flip: number; x: number; y: number}> = ({t, flip, x, y}) => {
  const ant = mv(t, flip - 0.22, flip - 0.02);
  const go = spring(t, flip, 15, 0.38);
  const ang = lerp(152 + 10 * ant, 28, go); // lever angle: 180 = straight down, 0 = straight up
  const on = ramp(t, flip + 0.05, flip + 0.2);
  const pulse = on > 0 ? 0.75 + 0.25 * Math.sin(t * 5) : 0;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: 140, height: 200}}>
      <div style={{position: 'absolute', inset: 0, borderRadius: 28, background: '#2F2A3D', boxShadow: 'inset 0 -10px 0 #221E2D'}} />
      {/* indicator lamp */}
      <div style={{position: 'absolute', left: 52, top: 14, width: 36, height: 36, borderRadius: 18, background: on > 0 ? lerpColor('#4A4458', '#9FE3BE', on) : '#4A4458', boxShadow: on > 0 ? `0 0 ${34 * pulse}px ${12 * pulse}px rgba(159,227,190,${0.7 * on})` : undefined}} />
      <div style={{position: 'absolute', left: 0, top: 168, width: 140, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 18, letterSpacing: 2, color: on > 0.5 ? '#9FE3BE' : 'rgba(255,255,255,0.4)'}}>{on > 0.5 ? 'ON' : 'OFF'}</div>
      {/* base nut + lever */}
      <svg width={140} height={200} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}}>
        <circle cx={70} cy={110} r={34} fill="#5C566B" />
        <circle cx={70} cy={110} r={24} fill="#8C8698" />
        <g transform={`rotate(${ang} 70 110)`}>
          <rect x={58} y={36} width={24} height={78} rx={12} fill="#D9DEE6" />
          <rect x={70} y={36} width={12} height={78} rx={6} fill="#B9BFC9" />
          <circle cx={70} cy={34} r={22} fill={P.marigold} />
          <circle cx={76} cy={30} r={8} fill="#F6C977" />
        </g>
      </svg>
    </div>
  );
};

export const Switchboard: React.FC<{t: number; drop: number; defs: SwitchDef[]}> = ({t, drop, defs}) => {
  if (t < drop) return null;
  const s = spring(t, drop, 10, 0.42);
  const y = BOARD.y + lerp(-760, 0, s);
  const uw = BOARD.w / 3;
  return (
    <div style={{position: 'absolute', left: BOARD.x, top: y, width: BOARD.w, height: BOARD.h}}>
      {/* hanging wires */}
      {[160, BOARD.w - 160].map((x) => (
        <div key={x} style={{position: 'absolute', left: x, top: -1400, width: 6, height: 1400, background: '#8A7A6A'}} />
      ))}
      <div style={{position: 'absolute', inset: 0, borderRadius: 34, background: '#5A4F6E', boxShadow: 'inset 0 -14px 0 #463D57'}} />
      {[
        [22, 22],
        [BOARD.w - 38, 22],
        [22, BOARD.h - 44],
        [BOARD.w - 38, BOARD.h - 44],
      ].map(([x, y], i) => (
        <div key={i} style={{position: 'absolute', left: x, top: y, width: 16, height: 16, borderRadius: 8, background: '#7C7190'}} />
      ))}
      {defs.map((d, k) => {
        const ps = pop(t, d.pop, 14, 0.42);
        const on = t > d.flip;
        const x0 = k * uw;
        const flipKick = t > d.flip ? Math.exp(-(t - d.flip) * 8) * Math.sin((t - d.flip) * 30) * 0.05 : 0;
        return (
          <div key={k} style={{position: 'absolute', left: x0, top: 0, width: uw, height: BOARD.h, transform: `scale(${ps * (1 + flipKick)})`, transformOrigin: '30% 50%'}}>
            {k > 0 && <div style={{position: 'absolute', left: -2, top: 34, width: 4, height: BOARD.h - 68, borderRadius: 2, background: 'rgba(255,255,255,0.12)'}} />}
            <Toggle t={t} flip={d.flip} x={44} y={24} />
            <div style={{position: 'absolute', left: 206, top: 0, width: uw - 226, height: BOARD.h, display: 'flex', flexDirection: 'column', justifyContent: 'center'}}>
              {/* old value: big before the flip, then a small struck-through line */}
              <div style={{height: 38, position: 'relative'}}>
                {on && (
                  <div style={{position: 'relative', display: 'inline-block'}}>
                    <WordsAt t={t} words={[[d.old, d.flip + 0.08]]} size={28} color="rgba(255,248,238,0.55)" align="left" />
                    <div style={{position: 'absolute', left: -4, top: 18, height: 4, borderRadius: 2, background: P.rose, width: `${lerp(0, 104, mv(t, d.flip + 0.3, d.flip + 0.6))}%`}} />
                  </div>
                )}
              </div>
              <div style={{height: 58, position: 'relative'}}>
                <div style={{position: 'absolute', left: 0, top: 0}}>
                  <WordsAt t={t} words={[[d.old, d.pop + 0.1]]} size={44} color="rgba(255,248,238,0.78)" align="left" out={d.flip - 0.02} />
                </div>
                {on && (
                  <div style={{position: 'absolute', left: 0, top: 0}}>
                    <WordsAt t={t} words={d.neu.split(' ').map((w, i) => [w, d.flip + 0.12 + i * 0.06] as [string, number])} size={44} color={P.white} align="left" />
                  </div>
                )}
              </div>
              {d.sub && (
                <div style={{height: 40}}>
                  {on && <WordsAt t={t} words={d.sub.split(' ').map((w, i) => [w, d.flip + 0.35 + i * 0.06] as [string, number])} size={30} color="#9FE3BE" align="left" />}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/* ---------- the first Sahara: a website on a phone, with three role tiles ---------- */
export const WebPhone: React.FC<{t: number; h: number; tiles: number[]; fill: number}> = ({t, h, tiles, fill}) => {
  const w = h * 0.47;
  const bez = h * 0.018;
  const tileDefs = [
    {label: 'Patient', c: P.sage, d: P.sageDark},
    {label: 'Family', c: P.rose, d: '#A5536C'},
    {label: 'Doctor', c: P.indigo, d: '#46588C'},
  ];
  const flash = fill > 0 && fill < 1 ? 0.5 * Math.sin(fill * Math.PI) : 0;
  return (
    <div style={{position: 'absolute', left: -w / 2, top: -h / 2, width: w, height: h, borderRadius: h * 0.07, background: '#1B1A1F', padding: bez, boxSizing: 'border-box', boxShadow: '0 30px 60px rgba(10,20,30,0.3)'}}>
      <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream}}>
        {/* browser bar */}
        <div style={{position: 'absolute', left: 12, right: 12, top: 26, height: 30, borderRadius: 15, background: '#EDE3D5', display: 'flex', alignItems: 'center', gap: 6, paddingLeft: 10, boxSizing: 'border-box'}}>
          <div style={{width: 10, height: 12, borderRadius: 3, background: P.sage}} />
          <span style={{fontFamily: FONT_BODY, fontSize: 15, color: P.inkSub}}>sahara.wedevit.in</span>
        </div>
        <div style={{position: 'absolute', left: 0, right: 0, top: 74, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, transform: `scale(${pop(t, tiles[0] - 0.3, 13, 0.45)})`}}>
          <Img src={staticFile('brand/sahara-logo.png')} style={{width: 40, height: 40}} />
          <span style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 32, color: P.sageDark}}>Sahara</span>
        </div>
        <div style={{position: 'absolute', left: 0, right: 0, top: 128, textAlign: 'center', fontFamily: FONT_BODY, fontSize: 16, color: P.inkSub, opacity: ramp(t, tiles[0] - 0.2, tiles[0] + 0.1)}}>Who is using Sahara</div>
        {tileDefs.map((d, i) => {
          const s = pop(t, tiles[i], 14, 0.42);
          return (
            <div key={i} style={{position: 'absolute', left: 16, right: 16, top: 160 + i * 84, height: 72, borderRadius: 16, background: d.c, boxShadow: `inset 0 -6px 0 ${d.d}`, display: 'flex', alignItems: 'center', gap: 12, paddingLeft: 12, boxSizing: 'border-box', transform: `scale(${s})`}}>
              <svg width={42} height={42} viewBox="-21 -21 42 42">
                <circle r={20} fill="rgba(255,255,255,0.9)" />
                <circle cy={-5} r={7} fill={d.c} />
                <path d="M-11 12 Q0 -2 11 12 Z" fill={d.c} />
                {i === 1 && <circle cx={9} cy={-1} r={5} fill={d.d} />}
                {i === 2 && <path d="M-2 4 h4 v4 h4 v4 h-4 v4 h-4 v-4 h-4 v-4 h4 Z" fill={P.white} transform="translate(0 -6) scale(0.7)" />}
              </svg>
              <span style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 28, color: P.white}}>{d.label}</span>
            </div>
          );
        })}
        <div style={{position: 'absolute', inset: 0, background: `rgba(255,236,190,${flash})`}} />
      </div>
    </div>
  );
};

/* ---------- shortlist stamp ---------- */
export const Stamp: React.FC<{t: number; t0: number; x: number; y: number; out?: number}> = ({t, t0, x, y, out = 1e9}) => {
  if (t < t0 - 0.14 || t > out + 0.35) return null;
  const fall = ramp(t, t0 - 0.14, t0);
  const sIn = lerp(2.4, 1, Math.pow(fall, 2));
  const land = t > t0 ? Math.exp(-(t - t0) * 9) * Math.sin((t - t0) * 30) * 0.08 : 0;
  const s = sIn * (1 + land) * (1 - easeInCubic(ramp(t, out, out + 0.3)));
  const op = Math.min(1, fall * 2);
  const ink: React.ReactNode[] = [];
  if (t > t0 && t < t0 + 0.6) {
    const u = ramp(t, t0, t0 + 0.5);
    for (let j = 0; j < 10; j++) {
      const a = (j / 10) * Math.PI * 2 + 0.4;
      const d = 250 + u * 90;
      const r = 9 * (1 - u);
      ink.push(<circle key={j} cx={Math.cos(a) * d} cy={Math.sin(a) * d * 0.45} r={r} fill={P.rose} />);
    }
  }
  return (
    <div style={{position: 'absolute', left: x, top: y, width: 0, height: 0}}>
      <svg style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}} width={10} height={10}>
        {ink}
      </svg>
      <div style={{position: 'absolute', left: -230, top: -84, width: 460, height: 168, borderRadius: 22, background: P.rose, transform: `rotate(-7deg) scale(${s})`, opacity: op, boxShadow: '0 10px 0 rgba(120,50,70,0.25)'}}>
        <div style={{position: 'absolute', inset: 10, borderRadius: 14, border: '4px solid rgba(255,248,238,0.85)'}} />
        <div style={{position: 'absolute', left: 0, right: 0, top: 26, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 56, letterSpacing: 4, color: P.cream}}>SHORTLISTED</div>
        <div style={{position: 'absolute', left: 0, right: 0, top: 98, textAlign: 'center', fontFamily: FONT_BODY, fontWeight: 600, fontSize: 32, color: P.cream}}>9 Oct · Top 120</div>
      </div>
    </div>
  );
};

/* ---------- the goals of care card ---------- */
export const GoalsCard: React.FC<{glow: number; w: number; h: number; labelOut?: number; flat?: number}> = ({glow, w, h, labelOut = 0, flat = 0}) => (
  <div
    style={{
      position: 'absolute',
      left: -w / 2,
      top: -h / 2,
      width: w,
      height: h,
      borderRadius: h * 0.2,
      background: flat > 0 ? 'transparent' : P.sage,
      boxShadow: `inset 0 -${h * 0.11 * (1 - flat)}px 0 rgba(44,90,74,${1 - flat}), 0 0 ${60 * glow}px ${26 * glow}px rgba(246,206,120,${0.8 * glow})`,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: h * 0.2,
    }}
  >
    <svg width={h * 0.6} height={h * 0.6} viewBox="-20 -20 40 40" style={{flex: 'none', transform: `scale(${Math.max(0, 1 - labelOut * 1.6)})`}}>
      <circle r={19} fill={P.white} />
      <path d="M0 9 C-14 0 -9 -11 0 -5 C9 -11 14 0 0 9 Z" fill={P.sage} />
    </svg>
    <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: h * 0.42, color: P.white, whiteSpace: 'nowrap', display: 'flex', gap: '0.26em', overflow: 'hidden', paddingTop: h * 0.04}}>
      {'Goals of care talk'.split(' ').map((wd, n) => (
        <span key={n} style={{display: 'inline-block', transform: `translateY(${-Math.max(0, Math.min(1, labelOut * 1.4 - n * 0.12)) * 140}%)`}}>
          {wd}
        </span>
      ))}
    </div>
  </div>
);

export {back};
