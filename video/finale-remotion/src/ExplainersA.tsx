import React from 'react';
import {staticFile} from 'remotion';
import {C, FONT_HEAD} from './theme';
import {Card, Chip, Check, rise, typed, inOutE, outE, prog} from './ui';
import {lerp} from './anim';

export type EP = {f: number};
const IW = 716;

/** 1. Waveform turning into labelled transcript lines. */
export const Ex1: React.FC<EP> = ({f}) => {
  const N = 38;
  const shrink = prog(f, 62, 92, inOutE);
  const lines: {who: string; text: string; bg: string; fg: string}[] = [
    {who: 'Doctor', text: 'Is everyone okay with recording?', bg: C.primaryWash, fg: C.primaryPressed},
    {who: 'Patient', text: 'Yes, that is fine.', bg: C.infoWash, fg: '#2F5A85'},
    {who: 'Daughter', text: 'Yes, we agree to the recording.', bg: C.roseWash, fg: '#8A3F55'},
  ];
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 0, width: IW, height: 80, display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
        {Array.from({length: N}).map((_, i) => {
          const env = Math.sin((i / (N - 1)) * Math.PI);
          const live = 0.25 + 0.75 * Math.abs(Math.sin(f * 0.22 + i * 0.55) * Math.cos(f * 0.09 + i * 0.21));
          const amp = Math.min(1, prog(f, 0, 14, outE)) * env * live;
          const h = lerp(8 + amp * 70, 6 + amp * 22, shrink);
          return <div key={i} style={{width: 10, height: h, borderRadius: 5, background: C.primary, opacity: lerp(1, 0.55, shrink)}} />;
        })}
      </div>
      {lines.map((l, i) => {
        const d = 78 + i * 30;
        const t = prog(f, d, d + 20, outE);
        return (
          <div key={i} style={{position: 'absolute', left: 0, top: 104 + i * 68, height: 56, display: 'flex', alignItems: 'center', gap: 16, opacity: t, transform: `translateY(${(1 - t) * 18}px)`}}>
            <Chip bg={l.bg} fg={l.fg} style={{width: 168, justifyContent: 'center'}}>{l.who}</Chip>
            <div style={{fontSize: 30, color: C.ink, whiteSpace: 'nowrap', clipPath: `inset(0 ${100 - prog(f, d + 4, d + 30, inOutE) * 100}% 0 0)`}}>{l.text}</div>
          </div>
        );
      })}
      <div style={{position: 'absolute', left: 0, bottom: 0, display: 'flex', alignItems: 'center', gap: 14, ...rise(f, 176)}}>
        <div style={{width: 44, height: 44, borderRadius: 22, background: C.success, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
          <Check size={30} t={prog(f, 186, 204, outE)} />
        </div>
        <div style={{fontSize: 32, fontWeight: 600, color: C.success}}>Form filled</div>
      </div>
    </Card>
  );
};

/** 2. Record, Review, Sign stepper with an edit caret. */
export const Ex2: React.FC<EP> = ({f}) => {
  const steps = ['Record', 'Review', 'Sign'];
  const xs = [60, IW / 2, IW - 60];
  const fill1 = prog(f, 20, 56, inOutE);
  const fill2 = prog(f, 168, 204, inOutE);
  const reached = [1, fill1 > 0.98 ? 1 : 0, fill2 > 0.98 ? 1 : 0];
  const text = typed('Comfort care only.', f, 96, 0.28);
  const caretOn = Math.floor(f / 12) % 2 === 0 || f < 150;
  const edited = prog(f, 150, 166, outE);
  return (
    <Card>
      <div style={{position: 'absolute', left: 60, top: 36, width: IW - 120, height: 6, borderRadius: 3, background: C.line}} />
      <div style={{position: 'absolute', left: 60, top: 36, width: (IW / 2 - 60) * fill1 + (IW / 2 - 60) * fill2, height: 6, borderRadius: 3, background: C.primary}} />
      {steps.map((s, i) => {
        const on = reached[i];
        const active = i === 1 ? prog(f, 50, 62, outE) * (1 - fill2) : i === 2 ? fill2 : 1;
        return (
          <div key={s} style={{position: 'absolute', left: xs[i] - 40, top: 0, width: 80, textAlign: 'center', ...rise(f, 4 + i * 8)}}>
            <div style={{width: 72, height: 72, margin: '3px auto 0', borderRadius: 36, background: on ? C.primary : C.surface, border: `4px solid ${on ? C.primary : C.line}`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', boxShadow: active > 0.5 && i === 1 ? '0 0 0 10px rgba(59,117,98,0.14)' : 'none'}}>
              {on ? <Check size={42} t={1} /> : <span style={{color: C.inkFaint, fontSize: 32, fontWeight: 600}}>{i + 1}</span>}
            </div>
            <div style={{fontSize: 32, fontWeight: 600, marginTop: 14, marginLeft: -20, width: 120, color: on ? C.ink : C.inkFaint}}>{s}</div>
          </div>
        );
      })}
      <div style={{position: 'absolute', left: 0, top: 168, width: IW, height: 130, borderRadius: 22, background: C.groundWarm, border: `2px solid ${C.line}`, ...rise(f, 70)}}>
        <div style={{position: 'absolute', left: 28, top: 26, fontSize: 34, color: C.ink, whiteSpace: 'nowrap'}}>
          <span style={{background: edited > 0 && edited < 1 ? C.primaryWash : 'transparent', borderRadius: 6}}>{text}</span>
          <span style={{display: 'inline-block', width: 3, height: 40, background: C.primary, marginLeft: 3, verticalAlign: 'middle', opacity: caretOn ? 1 : 0}} />
        </div>
        <div style={{position: 'absolute', left: 28, top: 82, width: 300, height: 14, borderRadius: 7, background: C.line}} />
        <div style={{position: 'absolute', right: 24, top: 18, opacity: edited}}>
          <Chip bg={C.primaryWash}>Edited</Chip>
        </div>
      </div>
    </Card>
  );
};

/** 3. 13 topics ticking, 2 flagged. */
export const Ex3: React.FC<EP> = ({f}) => {
  const flagged = [4, 9];
  const per = 11;
  const start = 14;
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 0, width: IW, display: 'grid', gridTemplateColumns: 'repeat(7, 80px)', columnGap: 26.3, rowGap: 22}}>
        {Array.from({length: 13}).map((_, i) => {
          const n = i + 1;
          const t0 = start + i * per;
          const t = prog(f, t0, t0 + 12, outE);
          const flag = flagged.includes(n);
          const pulse = flag ? 0.5 + 0.5 * Math.sin((f - t0) / 7) : 0;
          return (
            <div key={n} style={{width: 80, height: 80, borderRadius: 40, position: 'relative', background: flag ? C.warningWash : t > 0.5 ? C.primary : C.surface, border: `4px solid ${flag ? C.marigold : t > 0.5 ? C.primary : C.line}`, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: flag && t > 0.5 && f > start + 13 * per ? `0 0 0 ${6 + pulse * 8}px rgba(176,122,46,${0.16 - pulse * 0.08})` : 'none'}}>
              {flag ? (
                <span style={{fontSize: 30, fontWeight: 600, color: C.warning, opacity: t}}>{n}</span>
              ) : t > 0.5 ? (
                <Check size={46} t={prog(f, t0 + 4, t0 + 16, outE)} />
              ) : (
                <span style={{fontSize: 30, fontWeight: 600, color: C.inkFaint}}>{n}</span>
              )}
            </div>
          );
        })}
      </div>
      <div style={{position: 'absolute', left: 0, bottom: 0, width: IW, height: 84, borderRadius: 22, background: C.warningWash, border: `2px solid ${C.marigold}`, display: 'flex', alignItems: 'center', padding: '0 28px', gap: 16, ...rise(f, start + 13 * per + 12)}}>
        <div style={{width: 18, height: 18, borderRadius: 9, background: C.marigold}} />
        <div style={{fontSize: 34, fontWeight: 600, color: '#7A4A10'}}>Not discussed: 2</div>
      </div>
    </Card>
  );
};

/** 4. "Yes to CPR" struck through, "No CPR" lands as current. */
export const Ex4: React.FC<EP> = ({f}) => {
  const strike = prog(f, 78, 106, inOutE);
  const landed = prog(f, 44, 72, outE);
  const dim = prog(f, 86, 112, outE);
  return (
    <Card>
      <div style={{position: 'absolute', left: 0, top: 18, width: IW, height: 112, borderRadius: 24, background: C.surface, border: `3px solid ${dim > 0.5 ? C.line : C.primary}`, display: 'flex', alignItems: 'center', padding: '0 32px', justifyContent: 'space-between', opacity: lerp(1, 0.72, dim), ...rise(f, 4)}}>
        <div style={{position: 'relative', fontSize: 52, fontFamily: FONT_HEAD, color: dim > 0.5 ? C.inkFaint : C.ink}}>
          Yes to CPR
          <div style={{position: 'absolute', left: -6, top: '54%', height: 4, width: `calc(${strike * 100}% + 12px)`, background: C.inkSub, borderRadius: 2}} />
        </div>
        <Chip bg={dim > 0.5 ? C.well : C.primaryWash} fg={dim > 0.5 ? C.inkSub : C.primaryPressed}>{dim > 0.5 ? 'Replaced' : 'Earlier'}</Chip>
      </div>
      <div style={{position: 'absolute', left: 0, top: 168 + (1 - landed) * 40, width: IW, height: 130, borderRadius: 24, background: C.primaryWash, border: `4px solid ${C.primary}`, display: 'flex', alignItems: 'center', padding: '0 32px', justifyContent: 'space-between', opacity: landed}}>
        <div style={{fontSize: 62, fontFamily: FONT_HEAD, color: C.primaryPressed}}>No CPR</div>
        <Chip bg={C.primary} fg="#fff">Current</Chip>
      </div>
      <div style={{position: 'absolute', left: 58, top: 134, width: 4, height: 34 * landed, background: C.edge, borderRadius: 2, opacity: 0.6}} />
    </Card>
  );
};

/** 5. Document assembles, signatures draw, lock closes. */
export const Ex5: React.FC<EP> = ({f}) => {
  const lock = prog(f, 150, 176, inOutE);
  const sig1 = prog(f, 96, 124, inOutE);
  const sig2 = prog(f, 118, 146, inOutE);
  const blocks = [
    {x: 28, y: 28, w: 150, h: 18, c: C.primary, from: -60},
    {x: 28, y: 72, w: 244, h: 12, c: C.line, from: 60},
    {x: 28, y: 96, w: 226, h: 12, c: C.line, from: -60},
    {x: 28, y: 120, w: 238, h: 12, c: C.line, from: 60},
    {x: 28, y: 144, w: 180, h: 12, c: C.line, from: -60},
  ];
  return (
    <Card>
      <div style={{position: 'absolute', left: 30, top: -6, width: 300, height: 352, borderRadius: 18, background: '#fff', border: `3px solid ${C.line}`, boxShadow: '0 14px 30px rgba(56,46,41,0.10)', transform: `translateY(${(1 - prog(f, 0, 24, outE)) * 40}px)`, opacity: prog(f, 0, 16, outE)}}>
        {blocks.map((b, i) => {
          const t = prog(f, 14 + i * 9, 40 + i * 9, outE);
          return <div key={i} style={{position: 'absolute', left: b.x + b.from * (1 - t), top: b.y, width: b.w, height: b.h, borderRadius: b.h / 2, background: b.c, opacity: t}} />;
        })}
        {[0, 1].map((k) => {
          const t = k === 0 ? sig1 : sig2;
          const y = 236 + k * 58;
          return (
            <div key={k}>
              <div style={{position: 'absolute', left: 28, top: y + 34, width: 244, height: 3, background: C.edge, opacity: prog(f, 80, 96, outE) * 0.6}} />
              <svg style={{position: 'absolute', left: 38, top: y - 6, width: 200, height: 48}} viewBox="0 0 200 48">
                <path d={k === 0 ? 'M4 34 C 20 4, 34 4, 36 26 S 52 40, 66 14 S 90 30, 104 22 S 130 10, 150 30 L 190 20' : 'M4 28 C 14 8, 30 8, 34 24 S 56 38, 70 20 S 96 8, 110 28 S 140 36, 160 16 L 192 26'} fill="none" stroke={C.band} strokeWidth={3.5} strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - t} />
              </svg>
            </div>
          );
        })}
      </div>
      <div style={{position: 'absolute', left: 392, top: 20, width: 324, textAlign: 'center'}}>
        <svg width="180" height="200" viewBox="0 0 120 134" style={{display: 'block', margin: '0 auto', opacity: prog(f, 140, 156, outE)}}>
          <path d={`M30 62 V ${lerp(34, 46, lock)} a30 30 0 0 1 60 0 V ${lerp(30, 62, lock)}`} transform={`translate(0 ${lerp(-14, 0, lock)})`} fill="none" stroke={C.band} strokeWidth="10" strokeLinecap="round" />
          <rect x="14" y="62" width="92" height="64" rx="16" fill={C.primary} />
          <circle cx="60" cy="90" r="8" fill="#fff" />
          <rect x="57" y="92" width="6" height="16" rx="3" fill="#fff" />
        </svg>
        <div style={{...rise(f, 176), marginTop: 6, display: 'flex', justifyContent: 'center', gap: 12}}>
          <Chip>Version 1</Chip>
          <Chip bg={C.band} fg="#fff">Locked</Chip>
        </div>
      </div>
    </Card>
  );
};

const SENT = [
  {lang: 'English', text: 'I want to stay at home.', font: 'SaharaSans', dir: 'ltr' as const, size: 58, lh: 1.3},
  {lang: 'Hindi', text: 'मुझे घर पर ही रहना है।', font: 'SaharaSans', dir: 'ltr' as const, size: 58, lh: 1.5},
  {lang: 'Telugu', text: 'ఇంట్లోనే ఉండాలి.', font: 'SaharaSans', dir: 'ltr' as const, size: 58, lh: 1.5},
  {lang: 'Urdu', text: 'گھر پر ہی رہنا ہے۔', font: 'NotoUrdu', dir: 'rtl' as const, size: 54, lh: 2.0},
  {lang: 'Kannada', text: 'ಮನೆಯಲ್ಲೇ ಇರಬೇಕು.', font: 'SaharaSans', dir: 'ltr' as const, size: 58, lh: 1.5},
  {lang: 'Mixed', text: 'मुझे home पर ही रहना है।', font: 'SaharaSans', dir: 'ltr' as const, size: 58, lh: 1.5},
];
/** 6. One sentence across scripts, then the speech and translation partner. */
export const Ex6: React.FC<EP> = ({f}) => {
  const per = 33;
  const sarvamAt = 10 + SENT.length * per + 6;
  const sv = prog(f, sarvamAt, sarvamAt + 22, outE);
  const base = 1 - prog(f, sarvamAt - 8, sarvamAt + 6, inOutE);
  const idx = Math.min(SENT.length - 1, Math.floor((f - 10) / per));
  return (
    <Card>
      <div style={{position: 'absolute', inset: 0, ...rise(f, 0), opacity: prog(f, 0, 22, outE) * base}}>
        {SENT.map((s, i) => {
          const a = 10 + i * per;
          const inn = prog(f, a, a + 13, outE);
          const out = prog(f, a + per - 9, a + per + 2, inOutE);
          const op = inn * (1 - out);
          if (op <= 0.001) return null;
          return (
            <div key={i} style={{position: 'absolute', left: 0, right: 0, top: 0, height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: op, transform: `translateY(${(1 - inn) * 18 - out * 14}px)`, filter: `blur(${(1 - inn) * 5 + out * 5}px)`}}>
              <div dir={s.dir} style={{fontFamily: `'${s.font}', 'SaharaSans'`, fontSize: s.size, lineHeight: s.lh, color: C.ink, fontWeight: 400, textAlign: 'center', whiteSpace: 'nowrap'}}>{s.text}</div>
            </div>
          );
        })}
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 52, display: 'flex', justifyContent: 'center'}}>
          <Chip bg={idx === 5 ? C.warningWash : C.primaryWash} fg={idx === 5 ? '#7A4A10' : C.primaryPressed} style={{minWidth: 150, justifyContent: 'center'}}>{SENT[Math.max(0, idx)].lang}</Chip>
        </div>
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 4, display: 'flex', justifyContent: 'center', gap: 14}}>
          {SENT.map((_, i) => (
            <div key={i} style={{width: i === idx ? 36 : 14, height: 14, borderRadius: 7, background: i <= idx ? C.primary : C.line}} />
          ))}
        </div>
      </div>
      {sv > 0 && (
        <div style={{position: 'absolute', inset: 0, opacity: sv, transform: `translateY(${(1 - sv) * 16}px)`}}>
          <div style={{position: 'absolute', left: 0, right: 0, top: 6, textAlign: 'center', fontSize: 28, color: C.inkSub, fontWeight: 600}}>Indian-language speech and translation with</div>
          <div style={{position: 'absolute', left: (716 - 330) / 2, top: 64, width: 330, height: 266, borderRadius: 28, background: '#fff', border: `2px solid ${C.line}`, boxShadow: '0 10px 28px rgba(56,46,41,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
            <img src={staticFile('brand/sarvam.jpeg')} style={{width: 259, height: 194, display: 'block'}} />
          </div>
        </div>
      )}
    </Card>
  );
};
