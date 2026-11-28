import React from 'react';
import {Img, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, FONT_BODY, FONT_HEAD, MARGIN} from './theme';
import {inOutE, lerp, outE, prog} from './anim';
import {Words} from './common';
import {FEATURES} from './features';
import {Ex1, Ex2, Ex3, Ex4, Ex5, Ex6} from './ExplainersA';
import {Ex7, Ex8, Ex9, Ex10, Ex11, Ex12, Ex13, Ex14, Ex15} from './ExplainersB';
import {EW} from './ui';

const EX: Record<number, React.FC<{f: number}>> = {1: Ex1, 2: Ex2, 3: Ex3, 4: Ex4, 5: Ex5, 6: Ex6, 7: Ex7, 8: Ex8, 9: Ex9, 10: Ex10, 11: Ex11, 12: Ex12, 13: Ex13, 14: Ex14, 15: Ex15};
const EX_START = 50;

const fade = (f: number, frames: number, inD = 12, outD = 14) => prog(f, 0, inD, outE) * (1 - prog(f, frames - outD, frames - 2, inOutE));

/** A feature: text on one side, the persistent phone on the other, an explainer below the words. */
export const FeatureScene: React.FC<{n: number; frames: number}> = ({n, frames}) => {
  const f = useCurrentFrame();
  const d = FEATURES[n];
  const Ex = EX[n];
  const left = d.side === 'R' ? MARGIN : 1800 - EW;
  const out = prog(f, frames - 18, frames - 4, inOutE);
  const cardT = prog(f, EX_START - 6, EX_START + 24, outE);
  return (
    <div style={{position: 'absolute', inset: 0, fontFamily: FONT_BODY}}>
      <div style={{position: 'absolute', left, top: 130, width: EW, height: 170, display: 'flex', alignItems: 'flex-end', opacity: 1 - out, transform: `translateY(${-out * 12}px)`}}>
        <Words text={d.title} start={6} stagger={5} style={{fontFamily: FONT_HEAD, fontSize: 68, lineHeight: 1.1, color: C.ink, fontWeight: 600}} />
      </div>
      <div style={{position: 'absolute', left, top: 322, width: EW, opacity: 1 - out, transform: `translateY(${-out * 12}px)`}}>
        <Words text={d.sub} start={26} stagger={2.2} dur={18} dist={16} style={{fontSize: 34, lineHeight: 1.4, color: C.inkSub}} />
      </div>
      <div style={{position: 'absolute', left, top: 508, opacity: cardT * (1 - out), transform: `translateY(${(1 - cardT) * 26 - out * 12}px) scale(${lerp(0.97, 1, cardT)})`, transformOrigin: '50% 100%'}}>
        <Ex f={Math.max(0, f - EX_START)} />
      </div>
      <div style={{position: 'absolute', left, top: 944, width: EW, fontSize: 28, lineHeight: 1.35, color: C.inkFaint, opacity: prog(f, 20, 40, outE) * (1 - out)}}>
        {d.small && <div style={{color: C.inkSub, fontWeight: 600}}>{d.small}</div>}
        <div>Practice data. All names are fictional.</div>
      </div>
    </div>
  );
};

export const ChapterScene: React.FC<{n: number; name: string; frames: number}> = ({n, name, frames}) => {
  const f = useCurrentFrame();
  const o = fade(f, frames, 14, 14);
  const line = prog(f, 14, 44, inOutE);
  return (
    <div style={{position: 'absolute', inset: 0, opacity: o, color: '#fff', fontFamily: FONT_BODY}}>
      <div style={{position: 'absolute', left: MARGIN, top: 250, fontSize: 32, letterSpacing: 4, textTransform: 'uppercase', color: C.onBandSub, fontWeight: 600, transform: `translateY(${(1 - prog(f, 6, 26, outE)) * 20}px)`, opacity: prog(f, 6, 26, outE)}}>Chapter {n}</div>
      <Words text={name} start={12} stagger={5} style={{position: 'absolute', left: MARGIN, top: 310, width: 1500, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 120, lineHeight: 1.1}} />
      <div style={{position: 'absolute', left: MARGIN, top: 480, width: 220 * line, height: 5, borderRadius: 3, background: '#DCEBE3'}} />
    </div>
  );
};

const Logo: React.FC<{size: number; t?: number; onDark?: boolean}> = ({size, t = 1, onDark}) => (
  <div style={{width: size, height: size, borderRadius: onDark ? size / 2 : 0, background: onDark ? '#FFFDF9' : 'transparent', overflow: 'hidden', opacity: t, transform: `scale(${lerp(0.9, 1, t)})`, clipPath: onDark ? undefined : `circle(${t * 75}% at 50% 50%)`}}>
    <Img src={staticFile('brand/sahara-logo.png')} style={{width: '100%', height: '100%', mixBlendMode: onDark ? 'normal' : 'multiply'}} />
  </div>
);

const Bubble: React.FC<{x: number; y: number; w: number; h: number; t: number; f: number; color: string}> = ({x, y, w, h, t, f, color}) => (
  <g transform={`translate(${x},${y}) scale(${lerp(0.85, 1, t)})`} opacity={t} style={{transformOrigin: `${x}px ${y + h}px`}}>
    <rect width={w} height={h} rx="34" fill="#fff" stroke={color} strokeWidth="5" />
    <path d={`M${w * 0.2} ${h} l-6 44 l44 -44 Z`} fill="#fff" stroke={color} strokeWidth="5" strokeLinejoin="round" />
    <rect x="3" y={h - 8} width={w * 0.25} height="12" fill="#fff" transform={`translate(${w * 0.2 - 3},0)`} />
    {Array.from({length: 11}).map((_, i) => {
      const hh = 14 + 46 * Math.abs(Math.sin(f * 0.2 + i * 0.8)) * Math.sin(((i + 1) / 12) * Math.PI);
      return <rect key={i} x={w * 0.14 + i * (w * 0.072)} y={h / 2 - hh / 2} width="12" height={hh} rx="6" fill={color} />;
    })}
  </g>
);

const Figures: React.FC<{f: number}> = ({f}) => {
  const t = prog(f, 4, 30, outE);
  const t2 = prog(f, 14, 40, outE);
  const bub = prog(f, 40, 62, outE);
  return (
    <svg width="760" height="560" viewBox="0 0 760 560" style={{position: 'absolute', left: 1020, top: 250}}>
      <g opacity={t} transform={`translate(0 ${(1 - t) * 30})`}>
        <circle cx="250" cy="300" r="56" fill={C.band} />
        <path d="M120 520 C 120 400, 170 372, 250 372 S 380 400, 380 520 Z" fill={C.band} />
      </g>
      <g opacity={t2} transform={`translate(0 ${(1 - t2) * 30})`}>
        <circle cx="510" cy="310" r="50" fill="#7FAE97" />
        <path d="M390 520 C 390 410, 440 384, 510 384 S 630 410, 630 520 Z" fill="#7FAE97" />
      </g>
      <Bubble x={90} y={40} w={300} h={170} t={bub} f={f - 40} color={C.primary} />
    </svg>
  );
};

const Archive: React.FC<{f: number}> = ({f}) => {
  const t = prog(f, 20, 120, inOutE);
  const mx = lerp(100, 560, t);
  const my = 250 + Math.sin(t * Math.PI * 2) * 60;
  return (
    <svg width="760" height="560" viewBox="0 0 760 560" style={{position: 'absolute', left: 1020, top: 250}}>
      {Array.from({length: 6}).map((_, i) => {
        const col = i % 3;
        const row = Math.floor(i / 3);
        const a = prog(f, 4 + i * 5, 24 + i * 5, outE);
        const cx = 90 + col * 210;
        const dim = prog(mx, cx + 20, cx + 140, outE) * 0.55;
        return (
          <g key={i} transform={`translate(${cx},${90 + row * 220}) translate(0 ${(1 - a) * 24})`} opacity={a * (1 - dim)}>
            <rect width="170" height="190" rx="18" fill="#fff" stroke={C.edge} strokeWidth="3" />
            <rect x="22" y="28" width="90" height="12" rx="6" fill={C.line} />
            <rect x="22" y="60" width="124" height="10" rx="5" fill={C.line} />
            <rect x="22" y="84" width="104" height="10" rx="5" fill={C.line} />
            <rect x="22" y="108" width="114" height="10" rx="5" fill={C.line} />
          </g>
        );
      })}
      <g transform={`translate(${mx},${my})`} opacity={prog(f, 16, 30, outE) * (1 - prog(f, 126, 142, outE))}>
        <circle r="78" fill="rgba(255,253,249,0.35)" stroke={C.ink} strokeWidth="9" />
        <line x1="56" y1="56" x2="120" y2="120" stroke={C.ink} strokeWidth="14" strokeLinecap="round" />
      </g>
    </svg>
  );
};

export const OpeningScene: React.FC<{id: string; frames: number}> = ({id, frames}) => {
  const f = useCurrentFrame();
  const o = fade(f, frames, 10, 12);
  const head: React.CSSProperties = {fontFamily: FONT_HEAD, fontWeight: 600, color: C.ink, lineHeight: 1.12};
  if (id === 'O1')
    return (
      <div style={{position: 'absolute', inset: 0, opacity: o}}>
        <Words text="A patient tells her doctor what she wants." start={8} stagger={5} style={{...head, position: 'absolute', left: MARGIN, top: 330, width: 840, fontSize: 92}} />
        <Figures f={f} />
      </div>
    );
  if (id === 'O2')
    return (
      <div style={{position: 'absolute', inset: 0, opacity: o}}>
        <Words text="Two months later, in the emergency room, nobody can find what she said." start={8} stagger={4} style={{...head, position: 'absolute', left: MARGIN, top: 290, width: 880, fontSize: 76}} />
        <Archive f={f} />
      </div>
    );
  if (id === 'O3') {
    const count = Math.round(98 * prog(f, 10, 62, inOutE));
    const ring = prog(f, 10, 62, inOutE);
    return (
      <div style={{position: 'absolute', inset: 0, opacity: o, color: '#fff'}}>
        <svg width="520" height="520" viewBox="0 0 520 520" style={{position: 'absolute', left: MARGIN + 20, top: 280}}>
          <circle cx="260" cy="260" r="236" fill="none" stroke="rgba(255,255,255,0.18)" strokeWidth="16" />
          <circle cx="260" cy="260" r="236" fill="none" stroke="#DCEBE3" strokeWidth="16" strokeLinecap="round" pathLength={100} strokeDasharray={`${98 * ring} 100`} transform="rotate(-90 260 260)" />
        </svg>
        <div style={{position: 'absolute', left: MARGIN + 20, top: 410, width: 520, textAlign: 'center', fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 190, lineHeight: 1}}>{count}%</div>
        <Words text="of India’s advanced cancer patients had unmet palliative-care needs" start={30} stagger={4} style={{position: 'absolute', left: 780, top: 360, width: 960, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 70, lineHeight: 1.15}} />
        <div style={{position: 'absolute', left: 780, top: 700, fontFamily: FONT_BODY, fontSize: 30, color: '#DCEBE3', opacity: prog(f, 74, 96, outE)}}>Patil, BMJ Support Palliat Care 2024</div>
      </div>
    );
  }
  if (id === 'O4') {
    const t = prog(f, 4, 40, outE);
    return (
      <div style={{position: 'absolute', inset: 0, opacity: o}}>
        <div style={{position: 'absolute', left: 330, top: 290}}>
          <Logo size={400} t={t} />
        </div>
        <div style={{position: 'absolute', left: 790, top: 360, opacity: prog(f, 22, 46, outE), transform: `translateX(${(1 - prog(f, 22, 46, outE)) * 40}px)`, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 170, color: C.primaryPressed, lineHeight: 1}}>Sahara</div>
        <Words text="A palliative-care companion for patients, families and their doctors." start={40} stagger={3} style={{position: 'absolute', left: 794, top: 560, width: 880, fontFamily: FONT_BODY, fontSize: 44, lineHeight: 1.3, color: C.inkSub}} />
      </div>
    );
  }
  // O5
  const tiles = [
    {n: 'Patient', bg: C.primaryWash, fg: C.primaryPressed, head: '#3B7562'},
    {n: 'Family', bg: C.roseWash, fg: '#8A3F55', head: '#A8586F'},
    {n: 'Doctor', bg: C.infoWash, fg: '#2F5A85', head: '#43709F'},
  ];
  const join = prog(f, 44, 70, inOutE);
  return (
    <div style={{position: 'absolute', inset: 0, opacity: o}}>
      {tiles.map((t, i) => {
        const a = prog(f, 6 + i * 12, 34 + i * 12, outE);
        return (
          <div key={t.n} style={{position: 'absolute', left: MARGIN + 60 + i * 520, top: 200 + (1 - a) * 50, width: 460, height: 380, borderRadius: 40, background: t.bg, opacity: a, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 28}}>
            <svg width="190" height="170" viewBox="0 0 190 170">
              <circle cx="95" cy="52" r="40" fill={t.head} />
              <path d="M20 168 C 20 100, 55 92, 95 92 S 170 100, 170 168 Z" fill={t.head} opacity="0.85" />
            </svg>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 64, color: t.fg}}>{t.n}</div>
          </div>
        );
      })}
      <svg width="1920" height="140" style={{position: 'absolute', left: 0, top: 580}} viewBox="0 0 1920 140">
        {[0, 1, 2].map((i) => (
          <path key={i} d={`M${MARGIN + 60 + 230 + i * 520} 10 V 50 H 960 V 100`} fill="none" stroke={C.edge} strokeWidth="4" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - join} />
        ))}
      </svg>
      <Words text="One care record. Android and web." start={58} stagger={5} style={{position: 'absolute', left: 0, width: 1920, textAlign: 'center', top: 740, ...head, fontSize: 70}} />
    </div>
  );
};

export const CloseScene: React.FC<{id: string; frames: number}> = ({id, frames}) => {
  const f = useCurrentFrame();
  const o = prog(f, 0, 12, outE) * (id === 'C2' ? 1 - prog(f, frames - 20, frames - 2, inOutE) : 1 - prog(f, frames - 10, frames - 1, inOutE));
  if (id === 'C1') {
    const t = prog(f, 4, 40, outE);
    return (
      <div style={{position: 'absolute', inset: 0, opacity: o}}>
        <div style={{position: 'absolute', left: 360, top: 280}}>
          <Logo size={360} t={t} />
        </div>
        <div style={{position: 'absolute', left: 790, top: 330, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 160, color: C.primaryPressed, lineHeight: 1, opacity: prog(f, 20, 44, outE), transform: `translateX(${(1 - prog(f, 20, 44, outE)) * 40}px)`}}>Sahara</div>
        <Words text="Care that follows what matters to the patient." start={38} stagger={4} style={{position: 'absolute', left: 794, top: 520, width: 900, fontSize: 52, lineHeight: 1.25, color: C.inkSub, fontFamily: FONT_BODY}} />
      </div>
    );
  }
  const t = prog(f, 2, 30, outE);
  return (
    <div style={{position: 'absolute', inset: 0, opacity: o, color: '#fff', textAlign: 'center'}}>
      <div style={{position: 'absolute', left: 860, top: 200}}>
        <Logo size={200} t={t} onDark />
      </div>
      <Words text="sahara.wedevit.in" start={14} stagger={0} dist={20} style={{position: 'absolute', left: 0, width: 1920, top: 450, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 124}} />
      <Words text="Team Zeros and Ones. Health-a-thon 2026" start={34} stagger={3} style={{position: 'absolute', left: 0, width: 1920, top: 640, fontFamily: FONT_BODY, fontSize: 48, color: '#DCEBE3'}} />
    </div>
  );
};
